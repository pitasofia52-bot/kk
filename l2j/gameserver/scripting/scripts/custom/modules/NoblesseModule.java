package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import java.util.*;
import java.util.Map.Entry;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class NoblesseModule {
    private static final String CONFIG_PATH = "config/spesialnpc.properties";

    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("noblesseWindow")) {
            NpcHtmlMessage htmlNoble = new NpcHtmlMessage(objectId);
            htmlNoble.setFile("data/html/scripts/custom/ClanNpc60239/60239-noblesse.htm");
            htmlNoble.replace("%msg%", "");
            htmlNoble.replace("%requirements%", buildRequirementsHtmlTable(getNoblesseRequirements()));
            player.sendPacket(htmlNoble);
            return null;
        }

        if (event.equals("noblesse")) {
            String noblesseMsg = handleNoblesse(player);
            if (noblesseMsg.equals("success")) {
                NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
                htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-noblesse-success.htm");
                player.sendPacket(htmlSuccess);
            } else {
                NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
                htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-noblesse.htm");
                htmlError.replace("%msg%", noblesseMsg);
                htmlError.replace("%requirements%", buildRequirementsHtmlTable(getNoblesseRequirements()));
                player.sendPacket(htmlError);
            }
            return null;
        }

        return null;
    }

    private static Map<Integer, Integer> loadNoblesseRequirements() {
        Map<Integer, Integer> items = new LinkedHashMap<>();
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_PATH)) {
            prop.load(fis);
            for (String key : prop.stringPropertyNames()) {
                if (key.startsWith("noblesse_item_")) {
                    try {
                        int itemId = Integer.parseInt(key.substring("noblesse_item_".length()));
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

    private static Map<Integer, Integer> getNoblesseRequirements() {
        return loadNoblesseRequirements();
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

    private static String handleNoblesse(Player player) {
        if (player.isNoble())
            return "You are already Noblesse.";

        Map<Integer, Integer> req = getNoblesseRequirements();

        // Check if player has all items
        for (Entry<Integer, Integer> entry : req.entrySet()) {
            int itemId = entry.getKey();
            int count = entry.getValue();
            if (player.getInventory().getItemByItemId(itemId) == null ||
                player.getInventory().getItemByItemId(itemId).getCount() < count) {
                return "You need " + count + " x " + getItemName(itemId) + ".";
            }
        }

        // Remove all items
        boolean allRemoved = true;
        for (Entry<Integer, Integer> entry : req.entrySet()) {
            int itemId = entry.getKey();
            int count = entry.getValue();
            boolean removed = player.destroyItemByItemId("Noblesse", itemId, count, player, true);
            if (!removed) {
                allRemoved = false;
                break;
            }
        }
        if (!allRemoved) {
            return "Failed to remove required items. Please try again.";
        }

        player.setNoble(true, true);
        player.sendSkillList();
        return "success";
    }
}