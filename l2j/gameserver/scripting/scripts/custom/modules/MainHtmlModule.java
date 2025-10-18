package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class MainHtmlModule {
    public static String onFirstTalk(Npc npc, Player player) {
        showMainHtml(npc, player);
        return null;
    }

    public static String onTalk(Npc npc, Player player) {
        showMainHtml(npc, player);
        return null;
    }

    public static String onAdvEvent(String event, Npc npc, Player player) {
        showMainHtml(npc, player);
        return null;
    }

    private static void showMainHtml(Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;
        NpcHtmlMessage html = new NpcHtmlMessage(objectId);
        html.setFile("data/html/scripts/custom/ClanNpc60239/60239.htm");
        player.sendPacket(html);
    }
}