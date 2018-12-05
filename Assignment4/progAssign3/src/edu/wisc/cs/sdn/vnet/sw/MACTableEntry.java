package edu.wisc.cs.sdn.vnet.sw;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

/**
 * An entry in a MAC learning table.
 * @author Aaron Gember-Jacobson
 */
public class MACTableEntry 
{
	/** MAC address */
	private MACAddress macAddress;

	/** Switch interface out which packets should be sent to reach the MAC */
	private Iface iface;
	
	/** Time (in milliseconds since the epoch) the entry was updated */
	private long timeUpdated;
	
	/**
	 * Create a new MAC table entry.
	 * @param macAddress MAC addresses
	 * @param ifaceName name of the switch interface out which packets should 
	 *        be sent to reach the MAC address
	 */
	public MACTableEntry(MACAddress macAddress, Iface iface)
	{
		this.macAddress = macAddress;
		this.iface = iface;
		this.timeUpdated = System.currentTimeMillis();
	}
	
	public void update(Iface iface)
	{
		this.iface = iface;
		this.timeUpdated = System.currentTimeMillis();
	}
	
	public MACAddress getMACAddress() 
	{ return this.macAddress; }

	public Iface getInterface()
	{ return this.iface; }
	
	/**
	 * @return time (in milliseconds since the epoch) the entry was updated
	 */
	public long getTimeUpdated()
	{ return this.timeUpdated; }
}
