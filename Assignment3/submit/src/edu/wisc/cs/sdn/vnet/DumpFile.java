package edu.wisc.cs.sdn.vnet;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import net.floodlightcontroller.packet.Ethernet;

public class DumpFile
{
	private static final int TCPDUMP_MAGIC = 0xa1b2c3d4;
	private static final short PCAP_VERSION_MAJOR = 2;
	private static final short PCAP_VERSION_MINOR = 4;
	private static final int THIS_ZONE = 0;
	private static final int SIG_FIGS = 0;
	private static final int SNAP_LEN = 65535;
	private static final int LINKTYPE_ETHERNET = 1;
	
	private FileOutputStream fileStream;
	DataOutputStream outStream;
	
	private DumpFile(FileOutputStream fileStream)
	{
		this.fileStream = fileStream;
		outStream = new DataOutputStream(fileStream);
	}
	
	private DumpFile()
	{
		this.fileStream = null;
		outStream = new DataOutputStream(System.out);
	}
	
	public static DumpFile open(String filename)
	{
		DumpFile dumpFile = null;
		if (filename.equals("-"))
		{ dumpFile = new DumpFile(); }
		else
		{
			try 
			{ dumpFile = new DumpFile(new FileOutputStream(filename)); } 
			catch (FileNotFoundException e) 
			{
				System.err.println("Cannot open " + filename);
				return null;
			}
		}
		
		if (!dumpFile.writeHeader())
		{ return null; }
		return dumpFile;
	}
	
	private boolean writeHeader()
	{        
		try 
		{
			this.outStream.writeInt(TCPDUMP_MAGIC);
			this.outStream.writeShort(PCAP_VERSION_MAJOR);
			this.outStream.writeShort(PCAP_VERSION_MINOR);
			this.outStream.writeInt(THIS_ZONE);
			this.outStream.writeInt(SIG_FIGS);
			this.outStream.writeInt(SNAP_LEN);
			this.outStream.writeInt(LINKTYPE_ETHERNET);
	        this.outStream.flush();
	        return true;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}

	}
	
	public void dump(Ethernet etherPacket)
	{
		byte[] buf = etherPacket.serialize();
		
		int sec = (int)(System.currentTimeMillis()/1000);
		int usec = (int)((System.currentTimeMillis() % 1000)*1000);
		try
		{
			this.outStream.writeInt(sec);
			this.outStream.writeInt(usec);
			this.outStream.writeInt(buf.length);
			this.outStream.writeInt(buf.length);
			this.outStream.write(buf);
			this.outStream.flush();
		}
		catch (IOException e)
		{ e.printStackTrace(); }
	}
	
	public void close()
	{
		try
		{
			this.outStream.flush();
			if (this.fileStream != null)
			{ this.outStream.close(); }
		}
		catch(IOException e) { }
	}
}
