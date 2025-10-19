package net.sf.l2j.gameserver.handler.admincommandhandlers;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.custom.tvt.TvTManager;

public class AdminTvT implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = {
		"admin_tvt",
		"admin_tvt_force_reg",
		"admin_tvt_force_start",
		"admin_tvt_force_end",
		"admin_tvt_status",
		"admin_tvt_spawn_regnpc",
		"admin_tvt_despawn_regnpc",
		"admin_tvt_test_in"
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (activeChar == null)
			return false;
		
		try
		{
			if (command.equalsIgnoreCase("admin_tvt_force_reg"))
			{
				TvTManager.getInstance().forceOpen();
				activeChar.sendMessage("TvT: force registration attempted.");
			}
			else if (command.equalsIgnoreCase("admin_tvt_force_start"))
			{
				TvTManager.getInstance().forceStart();
				activeChar.sendMessage("TvT: force start attempted.");
			}
			else if (command.equalsIgnoreCase("admin_tvt_force_end"))
			{
				TvTManager.getInstance().forceEnd();
				activeChar.sendMessage("TvT: force end attempted.");
			}
			else if (command.equalsIgnoreCase("admin_tvt_status") || command.equalsIgnoreCase("admin_tvt"))
			{
				activeChar.sendMessage(TvTManager.getInstance().buildStatus(activeChar));
			}
			else if (command.equalsIgnoreCase("admin_tvt_spawn_regnpc"))
			{
				TvTManager.getInstance().forceSpawnRegNpc();
				activeChar.sendMessage("TvT: reg NPC spawn attempted.");
			}
			else if (command.equalsIgnoreCase("admin_tvt_despawn_regnpc"))
			{
				TvTManager.getInstance().forceDespawnRegNpc();
				activeChar.sendMessage("TvT: reg NPC despawn attempted.");
			}
			else if (command.startsWith("admin_tvt_test_in"))
			{
				String[] parts = command.split(" ");
				int sec = (parts.length > 1) ? Integer.parseInt(parts[1]) : 30;
				TvTManager.getInstance().scheduleTestRegistrationIn(sec);
				activeChar.sendMessage("TvT: test registration in " + sec + "s scheduled.");
			}
		}
		catch (Exception e)
		{
			activeChar.sendMessage("TvT admin cmd error: " + e.getMessage());
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}