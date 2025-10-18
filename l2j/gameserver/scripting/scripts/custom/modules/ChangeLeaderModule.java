package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.model.pledge.ClanMember;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class ChangeLeaderModule {
    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("changeLeaderWindow")) {
            NpcHtmlMessage htmlChangeLeader = new NpcHtmlMessage(objectId);
            htmlChangeLeader.setFile("data/html/scripts/custom/ClanNpc60239/60239-changeleader.htm");
            player.sendPacket(htmlChangeLeader);
            return null;
        }

        if (event.startsWith("changeClanLeader ")) {
            if (player.getClan() == null) {
                sendChangeLeaderError(objectId, player, "You do not have a clan.");
                return null;
            }
            if (!player.isClanLeader()) {
                sendChangeLeaderError(objectId, player, "Only the clan leader can change the leader.");
                return null;
            }

            String memberName = event.substring("changeClanLeader ".length()).trim();
            if (memberName.isEmpty()) {
                sendChangeLeaderError(objectId, player, "You need to enter the member's name.");
                return null;
            }

            if (player.getName().equalsIgnoreCase(memberName)) {
                sendChangeLeaderError(objectId, player, "You cannot select yourself!");
                return null;
            }

            Clan clan = player.getClan();
            ClanMember member = clan.getClanMember(memberName);

            if (member == null) {
                sendChangeLeaderError(objectId, player, "Member not found in your clan.");
                return null;
            }
            if (!member.isOnline()) {
                sendChangeLeaderError(objectId, player, "The member must be online.");
                return null;
            }
            if (player.isFlying()) {
                sendChangeLeaderError(objectId, player, "You must dismount from Wyvern!");
                return null;
            }

            clan.setNewLeader(member);

            NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
            htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-changeleader-success.htm");
            htmlSuccess.replace("%msg%", "New leader is: " + memberName);
            player.sendPacket(htmlSuccess);
            return null;
        }

        return null;
    }

    private static void sendChangeLeaderError(int objectId, Player player, String msg) {
        NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
        htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-changeleader-error.htm");
        htmlError.replace("%msg%", msg);
        player.sendPacket(htmlError);
    }
}