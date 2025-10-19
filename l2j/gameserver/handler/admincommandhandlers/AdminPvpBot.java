package net.sf.l2j.gameserver.handler.admincommandhandlers;

import com.elfocrash.roboto.pvp.PvpBotController;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;

/**
 * Simplified admin handler:
 *   //pvpboton  -> start (manualHold) if none active
 *   //pvpbotoff -> stop all immediately
 */
public class AdminPvpBot implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_pvpboton",
		"admin_pvpbotoff",
		// optional aliases
		"pvpboton",
		"pvpbotoff"
	};

	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (activeChar == null)
			return false;

		if (command.startsWith("admin_pvpboton") || command.equals("pvpboton"))
		{
			boolean ok = PvpBotController.INSTANCE.activateRandomWindowManual();
			activeChar.sendMessage(ok ? "PvP bots started." : "Already running or no windows.");
			return true;
		}

		if (command.startsWith("admin_pvpbotoff") || command.equals("pvpbotoff"))
		{
			boolean ok = PvpBotController.INSTANCE.deactivateAllManual();
			activeChar.sendMessage(ok ? "PvP bots stopped." : "Nothing running.");
			return true;
		}

		return false;
	}

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}