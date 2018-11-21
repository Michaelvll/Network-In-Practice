package edu.wisc.cs.sdn.vnet.vns;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CommandHwInfo extends Command
{
	public static final int MAX_HW_ENTRIES = 256;
	
	protected List<CommandHwEntry> mHwInfo;
	
	public CommandHwInfo()
	{ super(Command.VNS_HW_INFO); }
	
	protected CommandHwInfo deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		this.mHwInfo = new ArrayList<CommandHwEntry>(); 
		while (buf.hasRemaining() && mHwInfo.size() < MAX_HW_ENTRIES)
		{
			CommandHwEntry hwEntry = new CommandHwEntry();
			hwEntry.deserialize(buf);
			this.mHwInfo.add(hwEntry);
		}
						
		return this;
	}
}
