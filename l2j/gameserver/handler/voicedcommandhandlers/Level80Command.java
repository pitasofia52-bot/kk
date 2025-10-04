package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.data.ItemTable;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class Level80Command implements IVoicedCommandHandler
{
    private static final String[] VOICED_COMMANDS = { "80lvl" };

    // Requirements: { itemId, count }
    private static final int[][] REQUIREMENTS = {
        { 10639, 200 },    // Gold Bar x200
        { 10637, 10000 },  // Iron Bar x10000
        { 10638, 10000 },  // Silver Bar x10000
        { 10640, 10000 },  // Chrysolite Bar x10000
        { 10641, 10000 },  // Damascus Bar x10000
        { 10642, 10000 },  // Mithril Bar x10000
        { 10643, 10000 },  // Adamantium Bar x10000
        { 10644, 10000 }   // Titanium Bar x10000
    };

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String params)
    {
        if (activeChar == null)
            return false;

        if (!"80lvl".equalsIgnoreCase(command))
            return false;

        // If already 80+, do nothing.
        if (activeChar.getLevel() >= 80)
        {
            activeChar.sendMessage("You are already level 80.");
            return true;
        }

        // If no "confirm" param, show the requirements window.
        if (params == null || params.trim().isEmpty() || !"confirm".equalsIgnoreCase(params.trim()))
        {
            showRequirementsWindow(activeChar);
            return true;
        }

        // On confirmation, run anti-exploit checks and perform the operation.
        if (!canUseNow(activeChar))
            return true;

        // Check that all items exist.
        List<int[]> missing = new ArrayList<>();
        for (int[] req : REQUIREMENTS)
        {
            int itemId = req[0];
            int need = req[1];
            long have = getItemCount(activeChar, itemId);
            if (have < need)
                missing.add(new int[] { itemId, need, (int) have });
        }

        if (!missing.isEmpty())
        {
            activeChar.sendMessage("You don't have all required items.");
            showRequirementsWindow(activeChar);
            return true;
        }

        // Remove items with rollback safety.
        List<int[]> removed = new ArrayList<>();
        for (int[] req : REQUIREMENTS)
        {
            int itemId = req[0];
            int count = req[1];

            boolean ok = activeChar.destroyItemByItemId("Level80Command", itemId, count, null, false);
            if (!ok)
            {
                // Rollback any items already removed.
                for (int[] r : removed)
                    activeChar.addItem("Level80Rollback", r[0], r[1], activeChar, false);

                activeChar.sendMessage("Failed to remove items. Any removed items were returned.");
                return true;
            }
            removed.add(new int[] { itemId, count });
        }

        // Level up to 80 by adding EXP up to the target threshold.
        long targetExp = activeChar.getStat().getExpForLevel(80);
        long curExp = activeChar.getExp();
        long toAdd = targetExp - curExp;
        if (toAdd > 0)
            activeChar.addExpAndSp(toAdd, 0);

        activeChar.broadcastUserInfo();
        activeChar.sendMessage("Congratulations! You are now Level 80.");

        return true;
    }

    private void showRequirementsWindow(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<center><font color=\"LEVEL\">Level 80 Upgrade</font></center><br>");
        sb.append("To become level 80, you need the following items:<br><br>");

        for (int[] req : REQUIREMENTS)
        {
            int itemId = req[0];
            int need = req[1];

            Item item = ItemTable.getInstance().getTemplate(itemId);
            String name = (item != null) ? item.getName() : ("Item " + itemId);

            long have = getItemCount(player, itemId);
            boolean ok = have >= need;

            sb.append((ok ? "<font color=\"00CC00\">" : "<font color=\"FF3333\">"));
            sb.append("- ").append(name).append(" x").append(need);
            sb.append("</font>");
            sb.append(" (You have: ").append(have).append(")<br>");
        }

        sb.append("<br>");
        sb.append("If you are level 1 to 79, you can proceed.<br>");
        sb.append("If you are already 80+, nothing will be removed and you cannot use this command.<br><br>");

        sb.append("To confirm, type: <font color=\"LEVEL\">.80lvl confirm</font><br>");
        // Optional button (only if your system supports bypass to voiced):
        // sb.append("<button value=\"Confirm\" action=\"bypass -h voice .80lvl confirm\" width=120 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");

        sb.append("</body></html>");

        NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setHtml(sb.toString());
        player.sendPacket(html);
    }

    private boolean canUseNow(Player pc)
    {
        // Safety checks
        if (pc.isAlikeDead() || pc.isDead())
        {
            pc.sendMessage("You cannot use this now.");
            return false;
        }
        if (pc.isInCombat())
        {
            pc.sendMessage("You cannot use this while in combat.");
            return false;
        }
        if (pc.isInDuel())
        {
            pc.sendMessage("You cannot use this during a duel.");
            return false;
        }
        if (pc.isInOlympiadMode())
        {
            pc.sendMessage("You cannot use this in Olympiad.");
            return false;
        }
        if (pc.isInStoreMode() || pc.getActiveTradeList() != null || pc.isProcessingTransaction())
        {
            pc.sendMessage("Close your trade/store before continuing.");
            return false;
        }
        if (pc.isInJail())
        {
            pc.sendMessage("You cannot use this from jail.");
            return false;
        }
        // Internal events such as TvT – block if needed.
        try
        {
            if (pc.isInTvT())
            {
                pc.sendMessage("You cannot use this during an event.");
                return false;
            }
        }
        catch (Throwable ignored) {}

        if (pc.getLevel() >= 80)
        {
            pc.sendMessage("You are already level 80.");
            return false;
        }
        if (pc.getLevel() < 1 || pc.getLevel() > 79)
        {
            pc.sendMessage("This command can be used from level 1 to 79.");
            return false;
        }
        return true;
    }

    private long getItemCount(Player pc, int itemId)
    {
        long total = 0L;
        ItemInstance[] items = pc.getInventory().getAllItemsByItemId(itemId, true);
        if (items != null)
        {
            for (ItemInstance it : items)
            {
                if (it != null)
                    total += it.getCount();
            }
        }
        return total;
    }
}