package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import javafx.util.Pair;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
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
		// Check packet type
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			System.out.println("Not IPv4 packet dropped");
			return;
		}
		// Get IPv4 header
		IPv4 header = (IPv4) etherPacket.getPayload();
		// Check checksum
		short check = getChecksum(header);
		if ((check & 0xffff) != 0x0000) {
			System.out.println("Checksum is incorrect! Not handle. \nSum: " + String.valueOf((check & 0xffff)));
			return;
		}
		// Check TTL
		byte ttl = header.getTtl();
		--ttl;
		if (ttl == 0) {
			System.out.println("TTL is zero! Not handle");
			return;
		}
		// Check destination IP
		int destIP = header.getDestinationAddress();
		for (Map.Entry<String, Iface> entry: super.interfaces.entrySet()) {
			Iface iface = entry.getValue();
			int ifaceIP = iface.getIpAddress();
			if (destIP == ifaceIP) {
				System.out.println("Match interface IP, not handle.");
				return;
			}
		}
		// Look up route entry
		RouteEntry routeEntry = routeTable.lookup(destIP);
		if (routeEntry == null) {
			System.out.println("No matched routeEntry for destIP: " + String.valueOf(destIP));
			return;
		}
		// Update IPv4 packet
		header.setTtl(ttl);
		header.resetChecksum();
		short newChecksum = getChecksum(header);
		header.setChecksum(newChecksum);
		// Look up arp entry
		ArpEntry arpEntry = arpCache.lookup(destIP);
		MACAddress nextHopMAC = arpEntry.getMac();
		// Update ethernet packet
		Iface outIface = routeEntry.getInterface();
		MACAddress sourceMAC = outIface.getMacAddress();
		etherPacket.setDestinationMACAddress(nextHopMAC.toBytes());
		etherPacket.setSourceMACAddress(sourceMAC.toBytes());
		// Send ethernet packet
		System.out.println("Send etherPacket: " + etherPacket.toString().replace("\n", "\n\t"));
		sendPacket(etherPacket, outIface);
		/********************************************************************/
	}
	private short getChecksum(IPv4 header) {
		int headerLength = header.getHeaderLength();
		byte[] data = new byte[headerLength * 4];
		ByteBuffer bb = ByteBuffer.wrap(data);

		bb.put((byte) (((header.getVersion() & 0xf) << 4) | (header.getHeaderLength() & 0xf)));
		bb.put(header.getDiffServ());
		bb.putShort(header.getTotalLength());
		bb.putShort(header.getIdentification());
		bb.putShort((short) (((header.getFlags() & 0x7) << 13) | (header.getFragmentOffset() & 0x1fff)));
		bb.put(header.getTtl());
		bb.put(header.getProtocol());
		bb.putShort(header.getChecksum());
		bb.putInt(header.getSourceAddress());
		bb.putInt(header.getDestinationAddress());
		if (header.getOptions() != null)
			bb.put(header.getOptions());

		bb.rewind();
		int accumulation = 0;
		for (int i = 0; i < headerLength * 2; ++i) {
			accumulation += 0xffff & bb.getShort();
		}
		accumulation = ((accumulation >> 16) & 0xffff)
				+ (accumulation & 0xffff);
		return (short) (~accumulation & 0xffff);
	}
}
