package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AllianceCreateModule {
    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("createAlliance")) {
            NpcHtmlMessage allianceHtml = new NpcHtmlMessage(objectId);
            allianceHtml.setFile("data/html/scripts/custom/ClanNpc60239/60239-createalliance.htm");
            allianceHtml.replace("%msg%", "");
            player.sendPacket(allianceHtml);
            return null;
        }

        if (event.startsWith("createAllianceWithName ")) {
            String allianceName = event.substring("createAllianceWithName ".length()).trim();

            String msg = validateCreateAlliance(player, allianceName);
            if (msg != null) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-createalliance-error.htm");
                htmlError.replace("%msg%", msg);
                player.sendPacket(htmlError);
                return null;
            }

            Clan clan = player.getClan();
            clan.setAllyId(clan.getClanId());
            clan.setAllyName(allianceName);
            clan.changeAllyCrest(0, true);
            clan.updateClanInDB();

            NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
            htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-createalliance-success.htm");
            htmlSuccess.replace("%alliance_name%", allianceName);
            player.sendPacket(htmlSuccess);
            return null;
        }

        return null;
    }

    private static String validateCreateAlliance(Player player, String name) {
        if (player.getClan() == null)
            return "You must be in a clan to create an alliance.";
        if (!player.isClanLeader())
            return "Only the clan leader can create an alliance.";
        if (player.getClan().getAllyId() > 0)
            return "Your clan is already in an alliance.";
        if (name.isEmpty())
            return "Alliance name cannot be empty.";
        if (name.length() < 2 || name.length() > 16)
            return "Alliance name must be 2-16 characters long.";
        if (!name.matches("^[A-Za-z0-9]+$"))
            return "Alliance name must be alphanumeric only.";
        if (ClanTable.getInstance().isAllyExists(name))
            return "Alliance name already exists.";
        return null;
    }
}