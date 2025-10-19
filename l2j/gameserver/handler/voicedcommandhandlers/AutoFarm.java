package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.custom.autofarm.AutoFarmManager;
import net.sf.l2j.gameserver.model.zone.ZoneId;

public class AutoFarm implements IVoicedCommandHandler
{
    private static final String[] VOICED_COMMANDS = { "auto", "autofarm" };

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String params)
    {
        if (activeChar == null)
            return false;

        if (activeChar.isAlikeDead() || activeChar.isDead())
        {
            activeChar.sendMessage("Auto-farm is not available right now.");
            return true;
        }

        if (activeChar.isInsideZone(ZoneId.PEACE))
        {
            activeChar.sendMessage("Auto-farm is disabled in peace zones.");
            return true;
        }

        final int objectId = activeChar.getObjectId();
        if (AutoFarmManager.getInstance().isRunning(objectId))
        {
            AutoFarmManager.getInstance().stop(objectId);
            activeChar.sendMessage("Auto-farm stopped.");
        }
        else
        {
            AutoFarmManager.getInstance().start(objectId);
            activeChar.sendMessage("Auto-farm started. Attacking nearby monsters.");
        }
        return true;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }
}