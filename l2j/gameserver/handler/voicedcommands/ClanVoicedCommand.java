package net.sf.l2j.gameserver.handler.voicedcommands;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.network.SystemMessageId;

public class ClanVoicedCommand implements IVoicedCommandHandler
{
    private static final String[] VOICED_COMMANDS = { "clan" };
    private static final int TARGET_CLAN_ID = 268813597;

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String target)
    {
        if (activeChar == null)
            return false;

        if (command.equalsIgnoreCase("clan"))
        {
            Clan targetClan = ClanTable.getInstance().getClan(TARGET_CLAN_ID);

            if (targetClan == null)
            {
                activeChar.sendMessage("Clan is not available at the moment. Try again later.");
                return false;
            }

            if (activeChar.getClan() != null)
            {
                if (activeChar.getClan().getClanId() == TARGET_CLAN_ID)
                {
                    activeChar.sendMessage("You are already a member of this clan.");
                }
                else
                {
                    activeChar.sendMessage("You are already in a different clan. Leave your current clan first.");
                }
                return false;
            }

            if (activeChar.getClanJoinExpiryTime() > System.currentTimeMillis())
            {
                activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_MUST_WAIT_BEFORE_JOINING_ANOTHER_CLAN).addCharName(activeChar));
                return false;
            }

            targetClan.addClanMember(activeChar);

            activeChar.sendMessage("You have successfully joined the clan restart to get the clan skills. [" + targetClan.getName() + "].");
            activeChar.sendMessage("ATTENTION: If you do not log in to play for 4 days you will be automatically removed from the L2lineage1 clan.");

            // Broadcast join message to online clan members.
            SystemMessage joinMsg = new SystemMessage(SystemMessageId.S1_HAS_JOINED_CLAN);
            joinMsg.addString(activeChar.getName() + " has joined the clan.");
            targetClan.broadcastToOnlineMembers(joinMsg);

            return true;
        }

        return false;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }
}