package edu.wisc.cs.sdn.vnet.vns;

import java.nio.ByteBuffer;

public class CommandHwEntry
{
	public static final int HW_INTERFACE = 1;
	public static final int HW_ETHER = 2;
	public static final int HW_ETH_IP = 4;
	public static final int HW_MASK = 8;
	
	protected int mKey;
	protected byte [] value;
	
	protected CommandHwEntry deserialize(ByteBuffer buf)
	{
		this.mKey = buf.getInt();
		
		this.value = new byte[32];
		buf.get(this.value);
		
		return this;
	}
}
