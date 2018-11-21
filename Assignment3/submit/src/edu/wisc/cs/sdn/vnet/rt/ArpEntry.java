package edu.wisc.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * An entry in ARP table that maps an IP address to a MAC address.
 * @author Aaron Gember-Jacobson
 */
public class ArpEntry 
{
	/** MAC address corresponding to IP address */
	private MACAddress mac;
	
	/** IP address corresponding to MAC address */
	private int ip;
	
	/** Time (in milliseconds since the epoch) the mapping was created */
	private long timeAdded;
	
	/**
	 * Create an ARP table entry that maps an IP address to a MAC address.
	 * @param mac MAC address corresponding to IP address
	 * @param ip IP address corresponding to MAC address
	 */
	public ArpEntry(MACAddress mac, int ip)
	{
		this.mac = mac;
		this.ip = ip;
		this.timeAdded = System.currentTimeMillis();
	}
	
	/**
	 * @return MAC address corresponding to IP address
	 */
	public MACAddress getMac()
	{ return this.mac; }
	
	/**
	 * @return IP address corresponding to MAC address
	 */
	public int getIp()
	{ return this.ip; }
	
	/**
	 * @return time (in milliseconds since the epoch) the mapping was created
	 */
	public long getTimeAdded()
	{ return this.timeAdded; }
	
	public String toString()
	{
		return String.format("%s \t%s", IPv4.fromIPv4Address(this.ip),
				this.mac.toString());
	}
}
