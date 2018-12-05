package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	private MACTable macTable;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.macTable = new MACTable();
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		this.macTable.insert(etherPacket.getSourceMAC(), inIface);
		
		MACTableEntry entry = this.macTable.lookup(etherPacket.getDestinationMAC());
		if (entry != null)
		{ this.sendPacket(etherPacket, entry.getInterface()); }
		else
		{
			for (Iface iface : this.interfaces.values()) 
			{
				if (iface != inIface)
				{
					this.sendPacket(etherPacket, iface);
					System.out.println("Send packet out interface "+iface);
				}
			}
		}
		
		/********************************************************************/
	}
}
