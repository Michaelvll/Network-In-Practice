package edu.wisc.cs.sdn.vnet.sw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.MACAddress;

/**
 * A MAC learning table.
 * @author Aaron Gember-Jacobson
 */
public class MACTable implements Runnable
{
	/** Timeout (in milliseconds) for entries in the MAC table */
	public static final int TIMEOUT = 15 * 1000;
	
	/** Entries in the MAC table */
	private Map<MACAddress,MACTableEntry> entries;
	
	/** Thread for timing out requests and entries in the cache */
	private Thread timeoutThread;

	/**
	 * Initializes an empty MAC learning table for a switch.
	 * @param sw switch to which this table belongs
	 */
	public MACTable()
	{
		this.entries = new ConcurrentHashMap<MACAddress, MACTableEntry>();
		timeoutThread = new Thread(this);
		timeoutThread.start();
	}
	
	public void insert(MACAddress macAddress, Iface iface)
	{
		MACTableEntry entry = this.lookup(macAddress);
		if(entry != null)
		{ entry.update(iface); }
		else
		{ 
			entry = new MACTableEntry(macAddress, iface);
			this.entries.put(macAddress, entry); 
		}
	}
	
	public MACTableEntry lookup(MACAddress macAddress) 
	{
		if (this.entries.containsKey(macAddress))
		{ return this.entries.get(macAddress); }
		return null;
	}
	
	/**
	 * Every second: timeout MAC table entries.
	 */
	public void run()
	{
		while (true)
		{
			// Run every second
			try 
			{ Thread.sleep(1000); }
			catch (InterruptedException e) 
			{ break; }
			
			// Timeout entries
			for (MACTableEntry entry : this.entries.values())
			{
				if ((System.currentTimeMillis() - entry.getTimeUpdated()) 
						> TIMEOUT)
				{ this.entries.remove(entry.getMACAddress()); }
			}
		}
	}
}
