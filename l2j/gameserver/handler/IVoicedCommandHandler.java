package net.sf.l2j.gameserver.handler;

import net.sf.l2j.gameserver.model.actor.instance.Player;

public interface IVoicedCommandHandler
{
    String[] getVoicedCommandList();

    boolean useVoicedCommand(String command, Player activeChar, String params);
}