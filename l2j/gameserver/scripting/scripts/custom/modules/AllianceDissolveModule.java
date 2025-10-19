package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AllianceDissolveModule {
    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("dissolveAllianceWindow")) {
            NpcHtmlMessage dissolveHtml = new NpcHtmlMessage(objectId);
            dissolveHtml.setFile("data/html/scripts/custom/ClanNpc60239/60239-dissolvealliance.htm");
            dissolveHtml.replace("%msg%", "");
            player.sendPacket(dissolveHtml);
            return null;
        }

        if (event.equals("dissolveAlliance")) {
            Clan clan = player.getClan();
            String dissolveMsg = validateDissolveAlliance(player);
            if (dissolveMsg != null) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-dissolvealliance-error.htm");
                htmlError.replace("%msg%", dissolveMsg);
                player.sendPacket(htmlError);
                return null;
            }

            clan.setAllyId(0);
            clan.setAllyName(null);
            clan.changeAllyCrest(0, true);
            clan.updateClanInDB();

            NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
            htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-dissolvealliance-success.htm");
            player.sendPacket(htmlSuccess);
            return null;
        }

        return null;
    }

    private static String validateDissolveAlliance(Player player) {
        Clan clan = player.getClan();
        if (clan == null)
            return "You are not in a clan.";
        if (!player.isClanLeader())
            return "Only the clan leader can dissolve an alliance.";
        if (clan.getAllyId() == 0)
            return "Your clan is not in an alliance.";
        if (clan.getAllyId() != clan.getClanId())
            return "Only the alliance leader can dissolve the alliance.";
        return null;
    }
}