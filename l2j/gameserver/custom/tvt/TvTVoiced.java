package net.sf.l2j.gameserver.custom.tvt;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;

public final class TvTVoiced implements IVoicedCommandHandler
{
	private static final String[] COMMANDS = { "tvtjoin", "tvtleave", "tvtstatus" };
	
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if (activeChar == null)
			return false;
		
		// Global block inside event
		if (activeChar.isInTvT() && TvTConfig.BLOCK_ALL_VOICE_IN_EVENT)
		{
			if (!("tvtstatus".equals(command) && TvTConfig.ALLOW_TVT_STATUS_VOICE))
			{
				activeChar.sendMessage("TvT: Voice commands blocked during event.");
				return true;
			}
		}
		
		switch (command)
		{
			case "tvtjoin":
				TvTManager.getInstance().tryRegister(activeChar);
				break;
			case "tvtleave":
				TvTManager.getInstance().tryUnregister(activeChar);
				break;
			case "tvtstatus":
				activeChar.sendMessage(TvTManager.getInstance().buildStatus(activeChar));
				break;
		}
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return COMMANDS;
	}
}