package edu.wisc.cs.sdn.vnet.rt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * A cache of MAC address to IP address mappings.
 * @author Aaron Gember-Jacobson
 */
public class ArpCache
{		
	/** Entries in the cache; maps an IP address to an entry */
	private Map<Integer,ArpEntry> entries;
	
	/**
	 * Initializes an empty ARP cache for a router.
	 */
	public ArpCache()
	{ this.entries = new ConcurrentHashMap<Integer,ArpEntry>(); }
	
	/**
	 * Insert an entry in the ARP cache for a specific IP address, MAC address
	 * pair.
	 * @param mac MAC address corresponding to IP address
	 * @param ip IP address corresponding to MAC address
	 */
	public void insert(MACAddress mac, int ip)
	{ this.entries.put(ip, new ArpEntry(mac, ip)); }
	
	/**
	 * Checks if an IP->MAC mapping is the in the cache.
	 * @param ip IP address whose MAC address is desired
	 * @return the IP->MAC mapping from the cache; null if none exists 
	 */
	public ArpEntry lookup(int ip)
	{ return this.entries.get(ip); }
	
	/**
	 * Populate the ARP cache from a file.
	 * @param filename name of the file containing the static route table
	 * @param router the route table is associated with
	 * @return true if route table was successfully loaded, otherwise false
	 */
	public boolean load(String filename)
	{
		// Open the file
		BufferedReader reader;
		try 
		{
			FileReader fileReader = new FileReader(filename);
			reader = new BufferedReader(fileReader);
		}
		catch (FileNotFoundException e) 
		{
			System.err.println(e.toString());
			return false;
		}
		
		while (true)
		{
			// Read an ARP entry from the file
			String line = null;
			try 
			{ line = reader.readLine(); }
			catch (IOException e) 
			{
				System.err.println(e.toString());
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			// Stop if we have reached the end of the file
			if (null == line)
			{ break; }
			
			// Parse fields for ARP entry
			String ipPattern = "(\\d+\\.\\d+\\.\\d+\\.\\d+)";
			String macByte = "[a-fA-F0-9]{2}";
			String macPattern = "("+macByte+":"+macByte+":"+macByte
					+":"+macByte+":"+macByte+":"+macByte+")";
			Pattern pattern = Pattern.compile(String.format(
                        "%s\\s+%s", ipPattern, macPattern));
			Matcher matcher = pattern.matcher(line);
			if (!matcher.matches() || matcher.groupCount() != 2)
			{
				System.err.println("Invalid entry in ARP cache file");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}

			int ip = IPv4.toIPv4Address(matcher.group(1));
			if (0 == ip)
			{
				System.err.println("Error loading ARP cache, cannot convert "
						+ matcher.group(1) + " to valid IP");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			MACAddress mac = null;
			try
			{ mac = MACAddress.valueOf(matcher.group(2)); }
			catch(IllegalArgumentException iae)
			{
				System.err.println("Error loading ARP cache, cannot convert " 
						+ matcher.group(3) + " to valid MAC");
				try { reader.close(); } catch (IOException f) {};
				return false;
			}
			
			// Add an entry to the ACP cache
			this.insert(mac, ip);
		}
	
		// Close the file
		try { reader.close(); } catch (IOException f) {};
		return true;
	}
	
	public String toString()
	{
        String result = "IP\t\tMAC\n";
        for (ArpEntry entry : this.entries.values())
        { result += entry.toString()+"\n"; }
	    return result;
	}
}
