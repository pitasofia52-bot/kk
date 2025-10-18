package net.sf.l2j.gameserver.handler.admincommandhandlers;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.ScriptManager;
import net.sf.l2j.gameserver.scripting.scripts.events.EventRaidBoss;

public class AdminEventRaidBoss implements IAdminCommandHandler
{
    private static final String[] ADMIN_COMMANDS =
    {
        "admin_evenrraidon" // usage: //evenrraidon
    };

    @Override
    public boolean useAdminCommand(String command, Player activeChar)
    {
        if (activeChar == null || !activeChar.isGM())
            return false;

        if ("admin_evenrraidon".equals(command))
        {
            final Quest q = ScriptManager.getInstance().getQuest("EventRaidBoss");
            if (q == null || !(q instanceof EventRaidBoss))
            {
                activeChar.sendMessage("EventRaidBoss script is not loaded.");
                return false;
            }

            final EventRaidBoss erb = (EventRaidBoss) q;
            final boolean started = erb.startNow();
            if (started)
                activeChar.sendMessage("Event Raid Boss started.");
            else
                activeChar.sendMessage("Event Raid Boss is already active or blocked by an active Siege.");
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