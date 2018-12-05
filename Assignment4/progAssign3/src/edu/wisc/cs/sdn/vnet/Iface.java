package edu.wisc.cs.sdn.vnet;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * An interface on a router.
 * @author Aaron Gember-Jacobson
 */
public class Iface 
{
	private String name;
	private MACAddress macAddress;
	private int ipAddress;
    private int subnetMask;
	
	public Iface(String name)
	{
		this.name = name;
		this.macAddress = null;
		this.ipAddress = 0;
	}
	
	public String getName()
	{ return this.name; }
	
	public void setMacAddress(MACAddress mac)
	{ this.macAddress = mac; }
	
	public MACAddress getMacAddress()
	{ return this.macAddress; }

	public void setIpAddress(int ip)
	{ this.ipAddress = ip; }
	
	public int getIpAddress()
	{ return this.ipAddress; }
	
    public void setSubnetMask(int subnetMask)
	{ this.subnetMask = subnetMask; }
	
	public int getSubnetMask()
	{ return this.subnetMask; }

	public String toString()
	{
		if ((null == this.macAddress) || (0 == this.ipAddress)
				|| (0 == this.subnetMask))
		{ return this.name; }
		else
		{
			return String.format("%s\tHWaddr %s\n\tinet addr:%s Mask:%s",
					this.name, this.macAddress.toString(), 
					IPv4.fromIPv4Address(this.ipAddress),
	                IPv4.fromIPv4Address(this.subnetMask));
		}
	}
}
