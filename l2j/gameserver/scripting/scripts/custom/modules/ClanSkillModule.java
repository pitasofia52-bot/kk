package net.sf.l2j.gameserver.scripting.scripts.custom.modules;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.data.xml.SkillTreeData;
import net.sf.l2j.gameserver.model.holder.skillnode.ClanSkillNode;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import java.util.*;
import java.util.Map.Entry;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ClanSkillModule {
    private static final String CONFIG_PATH = "config/spesialnpc.properties";

    public static String onAdvEvent(String event, Npc npc, Player player) {
        int objectId = (npc != null) ? npc.getObjectId() : 0;

        if (event.equals("clanskillWindow")) {
            NpcHtmlMessage htmlSkill = new NpcHtmlMessage(objectId);
            htmlSkill.setFile("data/html/scripts/custom/ClanNpc60239/60239-clanskill.htm");
            htmlSkill.replace("%msg%", "");
            htmlSkill.replace("%requirements%", buildRequirementsHtmlTable(getClanSkillRequirements()));
            player.sendPacket(htmlSkill);
            return null;
        }

        if (event.equals("clanskill")) {
            if (player.getClan() == null) {
                sendSkillError(objectId, player, "You do not have a clan.");
                return null;
            }
            if (!player.isClanLeader()) {
                sendSkillError(objectId, player, "Only the clan leader can learn clan skills.");
                return null;
            }

            List<ClanSkillNode> availableSkills = SkillTreeData.getInstance().getClanSkillsFor(player);

            if (availableSkills.isEmpty()) {
                sendSkillError(objectId, player, "No more skills available for your clan's current level.");
                return null;
            }

            StringBuilder skillList = new StringBuilder();
            for (ClanSkillNode node : availableSkills) {
                L2Skill info = SkillTable.getInstance().getInfo(node.getId(), node.getValue());
                if (info == null)
                    continue;
                // Remove "Clan " prefix from skill name
                skillList.append("<button value=\"")
                         .append(removeClanPrefix(info.getName()))
                         .append(" Lv.")
                         .append(node.getValue())
                         .append("\" action=\"bypass -h Quest ClanNpc60239 learnClanSkill ")
                         .append(node.getId())
                         .append(" ")
                         .append(node.getValue())
                         .append("\" width=180 height=21 back=\"L2UI_ch3.BigButton3_over\" fore=\"L2UI_ch3.BigButton3\">")
                         .append("<br>");
            }

            NpcHtmlMessage htmlList = new NpcHtmlMessage(objectId);
            htmlList.setFile("data/html/scripts/custom/ClanNpc60239/60239-clanskill-list.htm");
            htmlList.replace("%skill_list%", skillList.toString());
            player.sendPacket(htmlList);
            return null;
        }

        if (event.startsWith("learnClanSkill ")) {
            if (player.getClan() == null) {
                sendSkillError(objectId, player, "You do not have a clan.");
                return null;
            }
            if (!player.isClanLeader()) {
                sendSkillError(objectId, player, "Only the clan leader can learn clan skills.");
                return null;
            }

            String[] args = event.split(" ");
            if (args.length != 3) {
                sendSkillError(objectId, player, "Bypass error.");
                return null;
            }

            int skillId, skillLevel;
            try {
                skillId = Integer.parseInt(args[1]);
                skillLevel = Integer.parseInt(args[2]);
            } catch (Exception e) {
                sendSkillError(objectId, player, "Bypass error (ID/LVL).");
                return null;
            }

            ClanSkillNode node = SkillTreeData.getInstance().getClanSkillFor(player, skillId, skillLevel);
            if (node == null) {
                sendSkillError(objectId, player, "Skill is not available for your clan.");
                return null;
            }

            L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
            if (skill == null) {
                sendSkillError(objectId, player, "Skill not found.");
                return null;
            }

            Map<Integer, Integer> req = getClanSkillRequirements();
            for (Entry<Integer, Integer> entry : req.entrySet()) {
                int itemId = entry.getKey();
                int count = entry.getValue();
                if (player.getInventory().getItemByItemId(itemId) == null ||
                    player.getInventory().getItemByItemId(itemId).getCount() < count) {
                    sendSkillError(objectId, player, "You need " + count + " x " + getItemName(itemId) + " for clan skill.");
                    return null;
                }
            }
            for (Entry<Integer, Integer> entry : req.entrySet()) {
                int itemId = entry.getKey();
                int count = entry.getValue();
                boolean removed = player.destroyItemByItemId("ClanSkill", itemId, count, player, true);
                if (!removed) {
                    sendSkillError(objectId, player, "Failed to remove required items for clan skill.");
                    return null;
                }
            }

            player.getClan().addNewSkill(skill);

            NpcHtmlMessage htmlSuccess = new NpcHtmlMessage(objectId);
            htmlSuccess.setFile("data/html/scripts/custom/ClanNpc60239/60239-clanskill-success.htm");
            htmlSuccess.replace("%msg%", "Your clan has acquired skill: " + removeClanPrefix(skill.getName()) + " Level " + skill.getLevel());
            player.sendPacket(htmlSuccess);
            return null;
        }

        return null;
    }

    private static void sendSkillError(int objectId, Player player, String msg) {
        NpcHtmlMessage htmlError = new NpcHtmlMessage(objectId);
        htmlError.setFile("data/html/scripts/custom/ClanNpc60239/60239-clanskill-error.htm");
        htmlError.replace("%msg%", msg);
        player.sendPacket(htmlError);
    }

    private static Map<Integer, Integer> loadClanSkillRequirements() {
        Map<Integer, Integer> items = new LinkedHashMap<>();
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_PATH)) {
            prop.load(fis);
            for (String key : prop.stringPropertyNames()) {
                if (key.startsWith("clanskill_item_")) {
                    try {
                        int itemId = Integer.parseInt(key.substring("clanskill_item_".length()));
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

    private static Map<Integer, Integer> getClanSkillRequirements() {
        return loadClanSkillRequirements();
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

    // Utility method to remove "Clan " prefix from skill names
    private static String removeClanPrefix(String name) {
        if (name != null && name.startsWith("Clan ")) {
            return name.substring(5);
        }
        return name;
    }
}