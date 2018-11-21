package edu.wisc.cs.sdn.vnet;

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.packet.Ethernet;

import edu.wisc.cs.sdn.vnet.vns.VNSComm;

/**
 * @author Aaron Gember-Jacobson
 */
public abstract class Device 
{
	/** Hostname for the device */
	private String host;
	
	/** List of the device's interfaces; maps interface name's to interfaces */
	protected Map<String,Iface> interfaces;
	
	/** PCAP dump file for logging all packets sent/received by the device;
	 *  null if packets should not be logged */
	private DumpFile logfile;
	
	/** Virtual Network Simulator communication manager for the device */
	private VNSComm vnsComm;
	
	/**
	 * Creates a device.
	 * @param host hostname for the device
	 * @param logfile PCAP dump file for logging all packets sent/received by the device
	 */
	public Device(String host, DumpFile logfile)
	{
		this.host = host;
		this.logfile = logfile;
		this.interfaces = new HashMap<String,Iface>();
		this.vnsComm = null;
	}
	
	/**
	 * @param logfile PCAP dump file for logging all packets sent/received by 
	 * 		  the router; null if packets should not be logged
	 */
	public void setLogFile(DumpFile logfile)
	{ this.logfile = logfile; }
	
	/**
	 * @return PCAP dump file for logging all packets sent/received by the
	 *         device; null if packets should not be logged
	 */
	public DumpFile getLogFile()
	{ return this.logfile; }
	
	/**
	 * @return hostname for the device
	 */
	public String getHost()
	{ return this.host; }
	
	/**
	 * @return list of the router's interfaces; maps interface name's to
	 * 	       interfaces
	 */
	public Map<String,Iface> getInterfaces()
	{ return this.interfaces; }
	
	/**
	 * @param vnsComm Virtual Network System communication manager for the router
	 */
	public void setVNSComm(VNSComm vnsComm)
	{ this.vnsComm = vnsComm; }
	
	/**
	 * Close the PCAP dump file for the router, if logging is enabled.
	 */
	public void destroy()
	{
		if (logfile != null)
		{ this.logfile.close(); }
	}
	
	/**
	 * Add an interface to the device.
	 * @param ifaceName the name of the interface
	 */
	public Iface addInterface(String ifaceName)
	{
		Iface iface = new Iface(ifaceName);
		this.interfaces.put(ifaceName, iface);
		return iface;
	}
	
	/**
	 * Gets an interface on the device by the interface's name.
	 * @param ifaceName name of the desired interface
	 * @return requested interface; null if no interface with the given name 
	 * 		   exists
	 */
	public Iface getInterface(String ifaceName)
	{ return this.interfaces.get(ifaceName); }
	
	/**
	 * Send an Ethernet packet out a specific interface.
	 * @param etherPacket an Ethernet packet with all fields, encapsulated
	 * 		  headers, and payloads completed
	 * @param iface interface on which to send the packet
	 * @return true if the packet was sent successfully, otherwise false
	 */
	public boolean sendPacket(Ethernet etherPacket, Iface iface)
	{ return this.vnsComm.sendPacket(etherPacket, iface.getName()); }
	
	public abstract void handlePacket(Ethernet etherPacket, Iface inIface);
}
