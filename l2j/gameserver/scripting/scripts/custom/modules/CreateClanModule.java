package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class CreateClanModule {
    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("createClan")) {
            NpcHtmlMessage htmlCreate = new NpcHtmlMessage(objectId);
            htmlCreate.setFile("data/html/scripts/custom/ClanNpc60239/60239-clancreate.htm");
            htmlCreate.replace("%msg%", "");
            player.sendPacket(htmlCreate);
            return null;
        }

        if (event.startsWith("createClanWithName ")) {
            if (player.getClan() != null) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-clancreate-error.htm");
                htmlError.replace("%msg%", "You are already in a clan and cannot create a new one.");
                player.sendPacket(htmlError);
                return null;
            }
            String clanName = event.substring("createClanWithName ".length()).trim();
            if (clanName.isEmpty()) {
                NpcHtmlMessage htmlCreate = new NpcHtmlMessage(objectId);
                htmlCreate.setFile("data/html/scripts/custom/ClanNpc60239/60239-clancreate.htm");
                htmlCreate.replace("%msg%", "Clan name cannot be empty.");
                player.sendPacket(htmlCreate);
                return null;
            }
            ClanTable.getInstance().createClan(player, clanName);
            if (player.getClan() == null) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-clancreate-error.htm");
                htmlError.replace("%msg%", "Clan creation failed. Please choose another name.");
                player.sendPacket(htmlError);
                return null;
            }
            NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
            htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-clancreate-success.htm");
            player.sendPacket(htmlSuccess);
            return null;
        }

        return null;
    }
}