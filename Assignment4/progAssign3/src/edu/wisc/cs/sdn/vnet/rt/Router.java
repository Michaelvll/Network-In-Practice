package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
    /**
     * Routing table for the router
     */
    private RouteTable routeTable;

    /**
     * ARP cache for the router
     */
    private AtomicReference<ArpCache> arpCache;

    // Timer for RIP
    private Timer ripTimer;
    private final static int ripIP = IPv4.toIPv4Address("224.0.0.9");

    private class PackIface {
        BasePacket packet;
        Iface inIface;
        Iface outIface;

        PackIface(BasePacket packet, Iface inIface, Iface outIface) {
            this.packet = packet;
            this.inIface = inIface;
            this.outIface = outIface;
        }
    }

    private AtomicReference<Map<Integer, TimerQueue<PackIface>>> packetQueues;

    private class TimerQueue<T> {
        Queue<T> queue;
        Timer timer;

        TimerQueue(Queue<T> queue, Ethernet etherPacket, Iface outIface, int targetIP) {
            this.queue = queue;
            this.timer = new Timer();
            this.timer.scheduleAtFixedRate(new WaitARP(etherPacket, outIface, targetIP), 0, 1000);
        }

        int size() {
            return queue.size();
        }

        Queue<T> get() {
            return queue;
        }

        void cancel() {
            timer.cancel();
            timer.purge();
        }

        void add(T item) {
            queue.add(item);
        }
    }

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile) {
        super(host, logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new AtomicReference<>(new ArpCache());
        this.packetQueues = new AtomicReference<>(new HashMap<>());
    }

    /**
     * @return routing table for the router
     */
    public RouteTable getRouteTable() {
        return this.routeTable;
    }

    /**
     * Load a new routing table from a file.
     *
     * @param routeTableFile the name of the file containing the routing table
     */
    public void loadRouteTable(String routeTableFile) {
        if (!routeTable.load(routeTableFile, this)) {
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
     *
     * @param arpCacheFile the name of the file containing the ARP cache
     */
    public void loadArpCache(String arpCacheFile) {
        if (!arpCache.get().load(arpCacheFile)) {
            System.err.println("Error setting up ARP cache from file "
                    + arpCacheFile);
            System.exit(1);
        }

        System.out.println("Loaded static ARP cache");
        System.out.println("----------------------------------");
        System.out.print(this.arpCache.toString());
        System.out.println("----------------------------------");
    }

    public void startRIP() {
        // Add entries of directly reachable subnets
        for (Iface iface : interfaces.values()) {
            int ip = iface.getIpAddress();
            int mask = iface.getSubnetMask();
            int subnet = ip & mask;
            routeTable.insert(subnet, 0, mask, iface, 1, false);
        }

        // Send RIP initial request
        for (Iface iface : interfaces.values()) {
            sendRIP(iface, true, false, null);
        }
        ripTimer = new Timer();
        ripTimer.scheduleAtFixedRate(new ScheduledRIPResponse(), 10000, 10000);
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
//		System.out.println("*** -> Received packet: " +
//                etherPacket.toString().replace("\n", "\n\t"));

        /********************************************************************/
        /* TODO: Handle packets                                             */
        // Check packet type
        switch (etherPacket.getEtherType()) {
            case Ethernet.TYPE_IPv4:
                handleIPPacket(etherPacket, inIface);
                break;
            case Ethernet.TYPE_ARP:
                handleARPPacket(etherPacket, inIface);
                break;
            default:
                System.out.println("Unknown packet (not IPv4 or ARP) dropped");
                break;
        }
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
            return;
        }

        /********************************************************************/
    }

    private void handleARPPacket(Ethernet etherPacket, Iface inIface) {
        ARP arpPacket = (ARP) etherPacket.getPayload();

        if (arpPacket.getOpCode() != ARP.OP_REQUEST) {
            if (arpPacket.getOpCode() == ARP.OP_REPLY) {
                int senderIP = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
                MACAddress senderMAC = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
                arpCache.get().insert(senderMAC, senderIP);

                TimerQueue<PackIface> queue = packetQueues.get().get(senderIP);
                queue.cancel();
                packetQueues.get().remove(senderIP);
//                System.out.println("Get ARP reply for IP: " + IPv4.fromIPv4Address(senderIP) + "! Send " +
//                        queue.size() + " packets in the queue");
                for (PackIface packIface : queue.get()) {
                    Ethernet ethernet = (Ethernet) packIface.packet;
                    ethernet.setDestinationMACAddress(senderMAC.toBytes());
                    sendPacket(ethernet, inIface);
//                    System.out.println("Send etherPacket in queue: " + ethernet.toString().replace("\n", "\n\t"));
                }
            }
            return;
        }

        int targetIP = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();
        int srcIP = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
        if (targetIP != inIface.getIpAddress()) {
            System.out.println("ARP target IP not match interface IP! Drop packet");
            return;
        }

        sendARP(etherPacket, inIface, srcIP);
    }

    private void handleRIPPacket(Ethernet etherPacket, Iface inIface) {
        IPv4 ip = (IPv4) etherPacket.getPayload();
        UDP udp = (UDP) ip.getPayload();
        if (!(udp.getPayload() instanceof RIPv2)) {
            System.out.println("Get non RIP packet from the reserved port");
        }
        RIPv2 rip = (RIPv2) udp.getPayload();
        // Check UDP checksum
        short originCheck = udp.getChecksum();
        udp.resetChecksum();
        byte[] serialized = udp.serialize();
        udp.deserialize(serialized, 0, serialized.length);
        if (udp.getChecksum() != originCheck) {
            System.out.println("UDP Checksum is incorrect! Not handle. \nSum: " + String.valueOf((udp.getChecksum() & 0xffff)));
            return;
        }
//        System.out.println("Get RIP Packet: " + rip.toString());

        // Update route table according to the rip
        for (RIPv2Entry entry : rip.getEntries()) {
            int dstIP = entry.getAddress();
            int nextHopIP = entry.getNextHopAddress();
            int mask = entry.getSubnetMask();
            int metric = entry.getMetric();
            if ((dstIP & mask) == (inIface.getIpAddress() & inIface.getSubnetMask())) continue;

            RouteEntry dstEntry = routeTable.lookup(dstIP);
            RouteEntry nextEntry = routeTable.lookup(nextHopIP);
            int nextHopMetric = nextEntry.getMetric();
            int cost = nextHopMetric + metric;

            if (dstEntry == null) routeTable.insert(dstIP, nextHopIP, mask, inIface, cost, true);
            else if (cost <= dstEntry.getMetric())
                routeTable.update(dstIP, mask, nextHopIP, inIface, cost);
        }
        System.out.println("Route table updated: \n" + routeTable.toString());
        if (rip.getCommand() == RIPv2.COMMAND_REQUEST) {
            sendRIP(inIface, false, true, etherPacket);
        }
    }

    private void handleIPPacket(Ethernet etherPacket, Iface inIface) {
        // Get IPv4 header
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        // Check checksum
        short originCheck = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        if (ipPacket.getChecksum() != originCheck) {
            System.out.println("IPv4 Checksum is incorrect! Not handle. \nSum: " + String.valueOf((ipPacket.getChecksum() & 0xffff)));
            return;
        }
        // Check TTL
        byte ttl = ipPacket.getTtl();
        --ttl;
        if (ttl == 0) {
            System.out.println("TTL is zero! Send back ICMP (Type 11, Code 0)");
            sendICMP(inIface, ipPacket, 11, 0);
            return;
        }

        // Check destination IP
        int dstIP = ipPacket.getDestinationAddress();
        if (dstIP == ripIP) {
            handleRIPPacket(etherPacket, inIface);
            return;
        }
        for (Iface iface : super.interfaces.values()) {
            int ifaceIP = iface.getIpAddress();
            if (dstIP == ifaceIP) {
                switch (ipPacket.getProtocol()) {
                    case IPv4.PROTOCOL_UDP:
                        if (((UDP) ipPacket.getPayload()).getDestinationPort() == UDP.RIP_PORT) {
                            handleRIPPacket(etherPacket, inIface);
                            return;
                        }
                    case IPv4.PROTOCOL_TCP:
                        System.out.println(IPv4.protocolClassMap.get(ipPacket.getProtocol())
                                + ", match interface IP! Send back ICMP (Type 3, Code 3)");
                        sendICMP(inIface, ipPacket, 3, 3);
                        return;
                    case IPv4.PROTOCOL_ICMP:
                        if (((ICMP) ipPacket.getPayload()).getIcmpType() != 8) {
                            System.out.println("ICMP non-echo, match interface IP! Drop packet");
                            return;
                        }
                        System.out.println("ICMP echo, match interface IP! " +
                                "Send back ICMP echo reply (Type 3, Code 3)");
                        sendICMP(inIface, ipPacket, 0, 0, true);
                }
                return;
            }
        }
        // Look up route entry
        RouteEntry routeEntry = routeTable.lookup(dstIP);
        if (routeEntry == null) {
            System.out.println("No matched route entry for dstIP: " + IPv4.fromIPv4Address(dstIP)
                    + "! Send back ICMP (Type 3, Code 0)");
            sendICMP(inIface, ipPacket, 3, 0);

            return;
        }
        // Update IPv4 packet
        ipPacket.setTtl(ttl);
        ipPacket.resetChecksum();
        serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);

        // Update ethernet packet
        Iface outIface = routeEntry.getInterface();
        MACAddress sourceMAC = outIface.getMacAddress();
        etherPacket.setSourceMACAddress(sourceMAC.toBytes());

        // Look up arp entry
        arpAndResponse(etherPacket, inIface, dstIP, routeEntry, outIface);
    }

    private void arpAndResponse(Ethernet etherPacket, Iface inIface, int dstIP, RouteEntry routeEntry, Iface outIface) {
        int nextHopIP = routeEntry.getGatewayAddress();
        if (nextHopIP == 0) nextHopIP = dstIP;
        ArpEntry arpEntry = arpCache.get().lookup(nextHopIP);
        if (arpEntry == null) {
            System.out.println("No matched ARP entry for the next hop IP: " + IPv4.fromIPv4Address(nextHopIP) + "!");
            if (!packetQueues.get().containsKey(nextHopIP)) {
                packetQueues.get().put(nextHopIP, new TimerQueue<>(new LinkedList<>(), etherPacket, outIface, nextHopIP));
            }
            packetQueues.get().get(nextHopIP).add(new PackIface(etherPacket, inIface, outIface));
            return;
        }
        MACAddress nextHopMAC = arpEntry.getMac();
        // Update the destination MAC address
        etherPacket.setDestinationMACAddress(nextHopMAC.toBytes());
        // Send ethernet packet
        sendPacket(etherPacket, outIface);
//		System.out.println("Send etherPacket: " + etherPacket.toString().replace("\n", "\n\t"));

    }

    private class WaitARP extends TimerTask {
        Ethernet etherPacket;
        Iface outIface;
        int targetIP;
        int count = 0;

        WaitARP(Ethernet etherPacket, Iface outIface, int targetIP) {
            this.etherPacket = etherPacket;
            this.outIface = outIface;
            this.targetIP = targetIP;
        }

        @Override
        public void run() {
            ++count;
            ArpEntry arpEntry = arpCache.get().lookup(targetIP);
            if (count == 4 && arpEntry == null) {
                TimerQueue<PackIface> queue = packetQueues.get().get(targetIP);
                packetQueues.get().remove(targetIP);
                if (queue == null) return;
                System.out.println("Do not get ARP reply for IP: " + IPv4.fromIPv4Address(targetIP) + ", after trying 3 times! Drop " +
                        queue.size() + " packets in the queue");
                for (PackIface packIface : queue.get()) {
                    Iface inIface = packIface.inIface;
                    Ethernet ether = (Ethernet) packIface.packet;
                    // Send destination host unreachable message ICMP
                    sendICMP(inIface, (IPv4) ether.getPayload(), 3, 1);
                }
            }
            if (count == 4 || arpEntry != null) {
                this.cancel();
                return;
            }

            System.out.println("Send ARPPacket " + String.valueOf(count) + "th!");
            sendARP(etherPacket, outIface, targetIP, true);
        }
    }

    private void sendARP(Ethernet etherPacket, Iface outIface, int targetIP) {
        sendARP(etherPacket, outIface, targetIP, false);
    }

    private void sendARP(Ethernet etherPacket, Iface outIface, int targetIP, boolean request) {
        Ethernet ether = new Ethernet();
        ARP arp = new ARP();
        ether.setPayload(arp);

        // Set ether header
        ether.setEtherType(Ethernet.TYPE_ARP);
        ether.setSourceMACAddress(outIface.getMacAddress().toBytes());
        ether.setDestinationMACAddress((request ? MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toBytes() :
                etherPacket.getSourceMACAddress()));

        // Set ARP header
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        arp.setProtocolType(ARP.PROTO_TYPE_IP);
        arp.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
        arp.setProtocolAddressLength((byte) 4);
        arp.setOpCode(request ? ARP.OP_REQUEST : ARP.OP_REPLY);
        arp.setSenderHardwareAddress(outIface.getMacAddress().toBytes());
        arp.setSenderProtocolAddress(outIface.getIpAddress());
        arp.setTargetHardwareAddress(request ? MACAddress.valueOf(0).toBytes() : etherPacket.getSourceMACAddress());
        arp.setTargetProtocolAddress(targetIP);

//        ether.serialize();
        sendPacket(ether, outIface);
//        System.out.println("Send ARP packet: " + (request ? "request" : "reply") + ether.toString().replace("\n", "\n\t"));
    }

    private void sendICMP(Iface inIface, IPv4 ipPacket, int type, int code) {
        sendICMP(inIface, ipPacket, type, code, false);
    }

    private void sendICMP(Iface inIface, IPv4 ipPacket, int type, int code, boolean echo) {
        Ethernet ether = new Ethernet();
        IPv4 ip = new IPv4();
        ICMP icmp = new ICMP();
        Data data = new Data();
        ether.setPayload(ip);
        ip.setPayload(icmp);
        icmp.setPayload(data);

        int dstIP = ipPacket.getSourceAddress();
        // Set header of ether packet
        ether.setEtherType(Ethernet.TYPE_IPv4);
        ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
//			System.out.println("SourceIP: " + IPv4.fromIPv4Address(dstIP));
        RouteEntry dstRouteEntry = routeTable.lookup(dstIP);
        assert dstRouteEntry != null;
        // Set header of IPv4
        Iface outIface = dstRouteEntry.getInterface();
        ip.setTtl((byte) 64);
        ip.setProtocol(IPv4.PROTOCOL_ICMP);
        ip.setSourceAddress(echo ? ipPacket.getDestinationAddress() : inIface.getIpAddress());
        ip.setDestinationAddress(dstIP);
        // Set header of ICMP
        icmp.setIcmpType((byte) type);
        icmp.setIcmpCode((byte) code);
        // Set Data payload
        if (!echo) {
            int headerLength = ipPacket.getHeaderLength() * 4;
            int packetLength = 4 + headerLength + 8;
            byte[] dataBytes = new byte[packetLength];
            ByteBuffer bb = ByteBuffer.wrap(dataBytes);
            bb.putInt(0);
            byte[] ipBytes = ipPacket.serialize();
            bb.put(Arrays.copyOfRange(ipBytes, 0, headerLength));
            bb.put(Arrays.copyOfRange(ipBytes, headerLength, headerLength + 8));
            bb.rewind();
            data.setData(dataBytes);
//		    System.out.println(Arrays.toString(dataBytes));
//		    System.out.println(Arrays.toString(ipBytes));
        } else data.setData(ipPacket.getPayload().getPayload().serialize());

        arpAndResponse(ether, inIface, dstIP, dstRouteEntry, outIface);
//		System.out.println("Send etherPacket (ICMP): " + ether.toString().replace("\n", "\n\t"));
    }

    private void sendRIP(Iface iface, boolean broadcast, boolean reply, Ethernet originEther) {
        Ethernet ether = new Ethernet();
        IPv4 ip = new IPv4();
        UDP udp = new UDP();
        RIPv2 rip = new RIPv2();
        ether.setPayload(ip);
        ip.setPayload(udp);
        udp.setPayload(rip);
        // Set ethernet header
        ether.setEtherType(Ethernet.TYPE_IPv4);
        ether.setSourceMACAddress(iface.getMacAddress().toBytes());
        ether.setDestinationMACAddress((broadcast ? MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toBytes()
                : originEther.getSourceMACAddress()));
        // Set ip header
        ip.setProtocol(IPv4.PROTOCOL_UDP);
        ip.setTtl((byte) 64);
        ip.setSourceAddress(iface.getIpAddress());
        ip.setDestinationAddress(reply ? ((IPv4) originEther.getPayload()).getSourceAddress() : ripIP);
        // Set udp header
        udp.setSourcePort(UDP.RIP_PORT);
        udp.setDestinationPort(UDP.RIP_PORT);
        // Set RIP header
        rip.setCommand((reply || broadcast) ? RIPv2.COMMAND_RESPONSE : RIPv2.COMMAND_REQUEST);
        for (RouteEntry routeEntry : routeTable.getEntries()) {
            int dstIP = routeEntry.getDestinationAddress();
            int mask = routeEntry.getMaskAddress();
            int metric = routeEntry.getMetric();

            RIPv2Entry ripEntry = new RIPv2Entry(dstIP, mask, metric);
            ripEntry.setNextHopAddress(iface.getIpAddress());
            rip.addEntry(ripEntry);
        }

        sendPacket(ether, iface);
//        System.out.println("Send RIP packet: " + ((reply || broadcast) ? "COMMAND_RESPONSE" : "COMMAND_REQUEST")
//                + ether.toString().replace("\n", "\n\t"));
    }

    private class ScheduledRIPResponse extends TimerTask {
        @Override
        public void run() {
            for (Iface iface : interfaces.values()) {
                sendRIP(iface, true, false, null);
            }
        }
    }


}
