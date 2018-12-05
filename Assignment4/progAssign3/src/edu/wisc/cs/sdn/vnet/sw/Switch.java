package edu.wisc.cs.sdn.vnet.sw;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {
    private class ForwardingEntry {
        private final long period = 15;
        private MACAddress mac;
        private String iface;
        private long timeout;

        ForwardingEntry(MACAddress mac, String iface) {
            this.mac = mac;
            this.iface = iface;
            this.timeout = getTimeout();
        }

        private long getTimeout() {
            return System.currentTimeMillis() / 1000 + period;
        }

        String getIface() {
            return iface;
        }

        boolean isTimeout() {
            return System.currentTimeMillis() / 1000 >= this.timeout;
        }

        void refresh(String iface) {
            this.iface = iface;
            this.timeout = getTimeout();
        }

        MACAddress getMac() {
            return mac;
        }

        public String toString() {
            return String.join(" ", mac.toString(), iface, String.valueOf(timeout));
        }
    }

    private LinkedList<ForwardingEntry> forwardingTable = new LinkedList<>();

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Switch(String host, DumpFile logfile) {
        super(host, logfile);
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

        /********************************************************************/
        /* TODO: Handle packets                                             */
        MACAddress sourceMac = etherPacket.getSourceMAC();
        MACAddress destMac = etherPacket.getDestinationMAC();

        String inIfaceName = inIface.getName();
        boolean sourceMatch = false;
        boolean destMatch = false;
        Iterator<ForwardingEntry> forwardingEntryIterator = forwardingTable.iterator();
        // Update forwarding table and send packet
        while (forwardingEntryIterator.hasNext()) {
            ForwardingEntry forwardingEntry = forwardingEntryIterator.next();
            if (forwardingEntry.isTimeout()) forwardingEntryIterator.remove();
            else if (forwardingEntry.getMac().equals(destMac)) {
                sendPacket(etherPacket, super.interfaces.get(forwardingEntry.getIface()));
                destMatch = true;
            } else if (forwardingEntry.getMac().equals(sourceMac)) {
                forwardingEntry.refresh(inIfaceName);
                sourceMatch = true;
            }
        }
        if (!destMatch) broadCast(etherPacket, inIfaceName);
        if (!sourceMatch) forwardingTable.addLast(new ForwardingEntry(sourceMac, inIfaceName));

        forwardingTable.forEach(System.err::println);
        /********************************************************************/
    }
    private void broadCast(Ethernet etherPacket, String inIfaceName) {
        super.interfaces.forEach((name, iface) -> {
            if (!name.equals(inIfaceName)) sendPacket(etherPacket, iface);
        });
    }
}
