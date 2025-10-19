package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class DeleteClanModule {
    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("deleteClan")) {
            NpcHtmlMessage htmlDeleteConfirm = new NpcHtmlMessage(objectId);
            htmlDeleteConfirm.setFile("data/html/scripts/custom/ClanNpc60239/60239-deleteconfirm.htm");
            player.sendPacket(htmlDeleteConfirm);
            return null;
        }

        if (event.equals("confirmDeleteClan")) {
            Clan clan = player.getClan();
            if (clan == null) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-delete-error.htm");
                htmlError.replace("%msg%", "You are not in a clan.");
                player.sendPacket(htmlError);
                return null;
            }
            if (!player.isClanLeader()) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-delete-error.htm");
                htmlError.replace("%msg%", "Only the clan leader can delete the clan.");
                player.sendPacket(htmlError);
                return null;
            }
            if (clan.getAllyId() > 0) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-delete-error.htm");
                htmlError.replace("%msg%", "You must first dissolve your alliance before deleting the clan.");
                player.sendPacket(htmlError);
                return null;
            }
            if (clan.getMembersCount() > 1) {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-delete-error.htm");
                htmlError.replace("%msg%", "You must remove all clan members before deleting the clan.");
                player.sendPacket(htmlError);
                return null;
            }
            ClanTable.getInstance().destroyClan(clan);
            NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
            htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-delete-success.htm");
            player.sendPacket(htmlSuccess);
            return null;
        }

        return null;
    }
}