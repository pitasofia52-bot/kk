package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import java.util.*;
import java.util.Map.Entry;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ClanLevelUpModule {
    private static final String CONFIG_PATH = "config/spesialnpc.properties";
    private static final int MAX_CLAN_LEVEL = 11; // acis default

    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("clanlevelWindow")) {
            NpcHtmlMessage htmlClanLevel = new NpcHtmlMessage(objectId);
            htmlClanLevel.setFile("data/html/scripts/custom/ClanNpc60239/60239-clanlevel.htm");
            htmlClanLevel.replace("%msg%", "");
            htmlClanLevel.replace("%requirements%", buildRequirementsHtmlTable(getClanLevelRequirements()));
            player.sendPacket(htmlClanLevel);
            return null;
        }

        if (event.equals("clanlevel")) {
            String clanLvlMsg = handleClanLevelUp(player);
            if (clanLvlMsg.startsWith("success")) {
                NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
                htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-clanlevel-success.htm");
                player.sendPacket(htmlSuccess);
            } else {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-clanlevel-error.htm");
                htmlError.replace("%msg%", clanLvlMsg);
                player.sendPacket(htmlError);
            }
            return null;
        }

        return null;
    }

    private static Map<Integer, Integer> loadClanLevelRequirements() {
        Map<Integer, Integer> items = new LinkedHashMap<>();
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_PATH)) {
            prop.load(fis);
            for (String key : prop.stringPropertyNames()) {
                if (key.startsWith("clanlevel_item_")) {
                    try {
                        int itemId = Integer.parseInt(key.substring("clanlevel_item_".length()));
                        int count = Integer.parseInt(prop.getProperty(key));
                        items.put(itemId, count);
                    } catch (NumberFormatException e) {
                        // ignore bad lines
                    }
                }
            }
        } catch (IOException e) {
            // log error if needed
        }
        return items;
    }

    private static Map<Integer, Integer> getClanLevelRequirements() {
        return loadClanLevelRequirements();
    }

    private static String getItemName(int itemId) {
        try {
            return net.sf.l2j.gameserver.data.ItemTable.getInstance().getTemplate(itemId).getName();
        } catch (Exception e) {
            return "Item " + itemId;
        }
    }

    private static String buildRequirementsHtmlTable(Map<Integer, Integer> req) {
        StringBuilder sb = new StringBuilder();
        for (Entry<Integer, Integer> entry : req.entrySet()) {
            sb.append("<tr><td align=\"left\">")
              .append(getItemName(entry.getKey()))
              .append("</td><td align=\"center\">")
              .append(entry.getValue())
              .append("</td></tr>");
        }
        return sb.toString();
    }

    // Custom: Only item check, direct level up (no SP/reputation/penalty checks)
    private static String handleClanLevelUp(Player player) {
        Clan clan = player.getClan();
        if (clan == null)
            return "You do not have a clan.";
        if (!player.isClanLeader())
            return "Only the clan leader can level up the clan.";
        if (clan.getLevel() >= MAX_CLAN_LEVEL)
            return "Your clan is already at maximum level.";

        Map<Integer, Integer> req = getClanLevelRequirements();
        for (Entry<Integer, Integer> entry : req.entrySet()) {
            int itemId = entry.getKey();
            int count = entry.getValue();
            if (player.getInventory().getItemByItemId(itemId) == null ||
                player.getInventory().getItemByItemId(itemId).getCount() < count) {
                return "You need " + count + " x " + getItemName(itemId) + ".";
            }
        }

        boolean allRemoved = true;
        for (Entry<Integer, Integer> entry : req.entrySet()) {
            int itemId = entry.getKey();
            int count = entry.getValue();
            boolean removed = player.destroyItemByItemId("ClanLevelUp", itemId, count, player, true);
            if (!removed) {
                allRemoved = false;
                break;
            }
        }
        if (!allRemoved)
            return "Failed to remove required items. Please try again.";

        // CORRECT: Use changeLevel to update clan level and save to DB (not setLevel/updateClanInDB)
        int newLevel = clan.getLevel() + 1;
        clan.changeLevel(newLevel);

        return "success";
    }
}