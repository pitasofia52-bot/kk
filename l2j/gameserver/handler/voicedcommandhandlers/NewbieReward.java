package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.base.ClassId;
import net.sf.l2j.gameserver.model.base.ClassType;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;

import java.util.Arrays;
import java.util.List;

public class NewbieReward implements IVoicedCommandHandler
{
    private static final String[] VOICED_COMMANDS = { "new" };

    // Consumables για όλους (itemId, ποσότητα)
    private static final int[][] COMMON_ITEMS = {
        { 3947, 5000 },
        { 1835, 5000 },
        { 728, 1000 },
        { 17, 5000 }
    };

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String params)
    {
        if (activeChar == null)
            return false;

        // Έλεγχος αν έχει ήδη πάρει reward
        if (activeChar.getMemos().getBool("GotNewbieReward", false))
        {
            activeChar.sendMessage("You have already received your Newbie rewards!");
            return true;
        }

        // Δώσε τα κοινά consumables
        for (int[] entry : COMMON_ITEMS)
        {
            activeChar.getInventory().addItem("NewbieReward", entry[0], entry[1], activeChar, null);
        }

        // Δώσε armor/weapon ανάλογα με starter class (χωρίς equip)
        List<Integer> armorIds = getArmorIds(activeChar.getClassId());
        for (int id : armorIds)
        {
            ItemInstance item = activeChar.getInventory().addItem("NewbieReward", id, 1, activeChar, null);
            if (item != null)
            {
                item.setEnchantLevel(0);
                // ΔΕΝ κάνουμε equip!
            }
        }

        List<Integer> weaponIds = getWeaponIds(activeChar.getClassId());
        for (int id : weaponIds)
        {
            ItemInstance item = activeChar.getInventory().addItem("NewbieReward", id, 1, activeChar, null);
            if (item != null)
            {
                item.setEnchantLevel(15);
                // ΔΕΝ κάνουμε equip!
            }
        }

        activeChar.getInventory().reloadEquippedItems();
        activeChar.broadcastCharInfo();

        // Αποθήκευσε ότι πήρε τα rewards (μόνο μία φορά)
        activeChar.getMemos().set("GotNewbieReward", true);

        activeChar.sendMessage("You have received your Newbie rewards. Good start!");
        return true;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }

    // Armor sets ανάλογα με starter class
    private static List<Integer> getArmorIds(ClassId classId)
    {
        switch (classId)
        {
            // Human Fighters, Elf Fighters, Dark Elf Fighters, Orc Fighters, Dwarf Fighters
            case HUMAN_FIGHTER:
            case WARRIOR:
            case KNIGHT:
            case ROGUE:
            case ELVEN_FIGHTER:
            case ELVEN_KNIGHT:
            case ELVEN_SCOUT:
            case DARK_FIGHTER:
            case PALUS_KNIGHT:
            case ASSASSIN:
            case ORC_FIGHTER:
            case ORC_RAIDER:
            case MONK:
            case DWARVEN_FIGHTER:
            case SCAVENGER:
            case ARTISAN:
                // No-grade fighter armor set (Wooden set, αλλά βάλε τα ids που θέλεις)
                return Arrays.asList(6408); // Πολύ απλό set, άλλαξε τα ids αν θέλεις
            // Mages
            case HUMAN_MYSTIC:
            case HUMAN_WIZARD:
            case CLERIC:
            case ELVEN_MYSTIC:
            case ELVEN_WIZARD:
            case ELVEN_ORACLE:
            case DARK_MYSTIC:
            case DARK_WIZARD:
            case SHILLIEN_ORACLE:
            case ORC_MYSTIC:
            case ORC_SHAMAN:
                // No-grade mage armor set (Devotion set, άλλαξε ids αν θες)
                return Arrays.asList(6408);
            default:
                // fallback: adena
                return Arrays.asList(57);
        }
    }

    // Weapons ανάλογα με starter class
    private static List<Integer> getWeaponIds(ClassId classId)
    {
        switch (classId)
        {
            // Archers
            case HAWKEYE:
            case SILVER_RANGER:
            case PHANTOM_RANGER:
                return Arrays.asList(273); // Wooden Bow, άλλαξε αν θες καλύτερο
            // Daggers
            case TREASURE_HUNTER:
            case PLAINS_WALKER:
            case ABYSS_WALKER:
                return Arrays.asList(219); // Dagger
            // Mages (starter)
            case HUMAN_MYSTIC:
            case HUMAN_WIZARD:
            case CLERIC:
            case ELVEN_MYSTIC:
            case ELVEN_WIZARD:
            case ELVEN_ORACLE:
            case DARK_MYSTIC:
            case DARK_WIZARD:
            case SHILLIEN_ORACLE:
            case ORC_MYSTIC:
            case ORC_SHAMAN:
                return Arrays.asList(155); // Staff
            // Fighters (sword)
            case HUMAN_FIGHTER:
            case WARRIOR:
            case KNIGHT:
            case ROGUE:
            case ELVEN_FIGHTER:
            case ELVEN_KNIGHT:
            case ELVEN_SCOUT:
            case DARK_FIGHTER:
            case PALUS_KNIGHT:
            case ASSASSIN:
            case ORC_FIGHTER:
            case ORC_RAIDER:
            case MONK:
            case DWARVEN_FIGHTER:
            case SCAVENGER:
            case ARTISAN:
                return Arrays.asList(6354); // Sword
            default:
                return Arrays.asList(57);
        }
    }
}