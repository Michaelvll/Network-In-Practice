package edu.wisc.cs.sdn.vnet.vns;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.Iface;

public class VNSComm 
{
	private Socket socket;
	private Device device;
	
	public VNSComm(Device device)
	{ 
		this.device = device;
		this.device.setVNSComm(this);
	}
	
	public boolean connectToServer(short port, String server)
	{
		// Grab server address from name
		InetAddress addr;
		try 
		{ addr = InetAddress.getByName(server); }
		catch (UnknownHostException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		// Create socket and attempt to connect to the server
		try 
		{ socket = new Socket(addr, port); }
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		// Send VNS_OPEN message to server
		CommandOpen cmdOpen = new CommandOpen();
		cmdOpen.mVirtualHostId = this.device.getHost();
		byte[] buf = cmdOpen.serialize();
		
		try
		{
			OutputStream outStream = socket.getOutputStream();
			outStream.write(buf);
            outStream.flush();
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		return true; 
	}
	
	private boolean handleHwInfo(CommandHwInfo cmdHwInfo)
	{
		Iface lastIface = null;
		for (CommandHwEntry hwEntry : cmdHwInfo.mHwInfo)
		{
			switch(hwEntry.mKey)
			{
			case CommandHwEntry.HW_INTERFACE:
				lastIface = this.device.addInterface(
                        new String(hwEntry.value).trim());
				break;
			case CommandHwEntry.HW_MASK:
				lastIface.setSubnetMask(ByteBuffer.wrap(hwEntry.value).getInt());
				break;
			case CommandHwEntry.HW_ETH_IP:
				lastIface.setIpAddress(ByteBuffer.wrap(hwEntry.value).getInt());
				break;
			case CommandHwEntry.HW_ETHER:
				lastIface.setMacAddress(new MACAddress(hwEntry.value));
				break;
			default:
				System.out.println(String.format(" %d", hwEntry.mKey));
			}
		}
		
		System.out.println("Device interfaces:");
		if (0 == this.device.getInterfaces().size())
		{ System.out.println(" Interface list empty"); }
		else
		{
			for (Iface iface : this.device.getInterfaces().values())
			{ System.out.println(iface.toString()); }
		}
		
		return true;
	}
	
	public boolean readFromServer()
	{ return this.readFromServerExpect(0); }
	
	public boolean readFromServerExpect(int expectedCmd)
	{
		int bytesRead = 0;
		InputStream inStream = null;
		
		// Get input stream
		try 
		{ inStream = this.socket.getInputStream(); } 
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		// Attempt to read the size of the incoming packet
		byte[] lenBytes = new byte[4];
		while (bytesRead < 4)
		{
			try 
			{
				int ret = inStream.read(lenBytes, bytesRead, 4 - bytesRead);
				if (ret < 0)
				{ throw new Exception(); }
				bytesRead += ret;
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
				return false;
			}
		}
		
		int len = ByteBuffer.wrap(lenBytes).getInt();
		
		if (len > 10000 || len < 0)
		{
			System.err.println(String.format(
					"Error: comamnd length too large %d", len));
			try { socket.close(); } catch (IOException e) { }
			return false;
		}
		
		// Allocate buffer
		ByteBuffer buf = ByteBuffer.allocate(len);
		
		// Set first field of command since we've already read it
		buf.putInt(len);
		
		// Read the rest of the command
		while (bytesRead < len)
		{
			try 
			{
				int ret = inStream.read(buf.array(), bytesRead, len - bytesRead);
				if (ret < 0)
				{ throw new Exception(); }
				bytesRead += ret;
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
				System.err.println("Error: failed reading command body");
				try { socket.close(); } catch (IOException e2) { }
				return false;
			}
		}
		
		// Make sure the command is what we expected if we were expecting something
		int command = buf.getInt();
		if (expectedCmd != 0 && command != expectedCmd)
		{
			if (command != Command.VNS_CLOSE) // VNS_CLOSE is always ok
			{
				System.err.println(String.format(
						"Error: expected command %d but got %d", expectedCmd,
						command));
				return false;
			}
		}
		
		buf.position(0);
		switch(command)
		{
		case Command.VNS_PACKET:
			CommandPacket cmdPkt = new CommandPacket();
			cmdPkt.deserialize(buf);
			
			// Log packet
            if (this.device.getLogFile() != null)
            { this.device.getLogFile().dump(cmdPkt.etherPacket); }
			
			// Pass to device, student's code should take over here
			this.device.handlePacket(cmdPkt.etherPacket, 
					this.device.getInterface(cmdPkt.mInterfaceName));
			break;
			
		case Command.VNS_CLOSE:
			System.err.println("VNS server closed session.");
			CommandClose cmdClose = new CommandClose();
			cmdClose.deserialize(buf);
			System.err.println("Reason: " + new String(cmdClose.mErrorMessage));
			return true;
			
		case Command.VNS_HW_INFO:
			CommandHwInfo cmdHwInfo = new CommandHwInfo();
			cmdHwInfo.deserialize(buf);
			this.handleHwInfo(cmdHwInfo);
			break;
		
		default:
			System.err.println(String.format("unknown command: %d", command));
			break;
		}

		return true;
	}
	
	public boolean etherAddrsMatchInterface(Ethernet etherPacket, 
			String ifaceName)
	{
		Iface iface = this.device.getInterface(ifaceName);
		if (null == iface)
		{
			System.err.println("** Error, interface " + ifaceName 
					+ ", does not exist");
			return false;
		}
		if (!iface.getMacAddress().equals(etherPacket.getSourceMAC()))
		{
			System.err.println("** Error, source address does not match interface"); 
			return false;
		}
		return true;
	}
	
	// sr_send_packet
	public boolean sendPacket(Ethernet etherPacket, String ifaceName)
	{
		CommandPacket cmdPacket = new CommandPacket();
		cmdPacket.mInterfaceName = ifaceName;
		cmdPacket.etherPacket = etherPacket;
		
		byte[] buf = cmdPacket.serialize();
		
		/*if (!etherAddrsMatchInterface(etherPacket, ifaceName))
		{
			System.err.println("*** Error: problem with ethernet header, check log");
			return false;
		}*/
		
		// Log packet
        if (this.device.getLogFile() != null)
        { this.device.getLogFile().dump(etherPacket); }
		
	    try
		{
			OutputStream outStream = socket.getOutputStream();
			outStream.write(buf);
            outStream.flush();
		}
		catch(IOException e)
		{
			System.err.println("Error writing packet");
			return false;
		}
		return true;
	}
}
