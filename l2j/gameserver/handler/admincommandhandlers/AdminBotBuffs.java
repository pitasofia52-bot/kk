package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.elfocrash.roboto.FakePlayer;
import com.elfocrash.roboto.helpers.FakeHelpers;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.L2Effect;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.templates.skills.L2EffectType;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.model.L2Skill;

/**
 * Admin command: //botbuffs [name|objectId]
 * Internal engine στέλνει συνήθως "admin_botbuffs", γι' αυτό κρατάμε και τα 2 ids.
 */
public class AdminBotBuffs implements IAdminCommandHandler {

    // Βάζουμε και τις δύο μορφές για σιγουριά.
    private static final String[] ADMIN_COMMANDS = { "admin_botbuffs", "botbuffs" };

    @Override
    public boolean useAdminCommand(String command, Player activeChar) {
        if (activeChar == null || !activeChar.isGM()) {
            return false;
        }

        // Ο parser του aCis περνάει π.χ. "admin_botbuffs Xeltharia"
        String base = command;
        int idx = command.indexOf(' ');
        String arg = null;
        if (idx != -1) {
            base = command.substring(0, idx);
            arg = command.substring(idx + 1).trim();
        }

        // Κανονικοποίηση: αν είναι admin_botbuffs ή botbuffs δεν μας νοιάζει.
        if (!base.equalsIgnoreCase("admin_botbuffs") && !base.equalsIgnoreCase("botbuffs")) {
            return false;
        }

        FakePlayer targetBot = null;

        if (arg != null && !arg.isEmpty()) {
            String ref = arg;
            // Δοκίμασε objectId
            if (ref.matches("\\d+")) {
                int objId = Integer.parseInt(ref);
                Player p = World.getInstance().getPlayer(objId);
                if (p instanceof FakePlayer) {
                    targetBot = (FakePlayer) p;
                }
            }
            // Δοκίμασε όνομα
            if (targetBot == null) {
                Player p = World.getInstance().getPlayer(ref);
                if (p instanceof FakePlayer) {
                    targetBot = (FakePlayer) p;
                }
            }
        } else {
            if (activeChar.getTarget() instanceof FakePlayer) {
                targetBot = (FakePlayer) activeChar.getTarget();
            }
        }

        if (targetBot == null) {
            activeChar.sendMessage("Χρήση: //botbuffs [name|objectId] (ή κάνε target ένα bot).");
            return true;
        }

        dumpBuffs(activeChar, targetBot);
        return true;
    }

    private void dumpBuffs(Player gm, FakePlayer bot) {
        L2Effect[] effects = bot.getAllEffects();

        Set<Integer> activeBuffIds = Arrays.stream(effects)
                .filter(e -> e.getEffectType() == L2EffectType.BUFF)
                .map(e -> e.getSkill().getId())
                .collect(Collectors.toSet());

        int[][] template = resolveTemplate(bot);

        Set<Integer> templateIds = Arrays.stream(template)
                .map(arr -> arr[0])
                .collect(Collectors.toCollection(HashSet::new));

        gm.sendMessage("=== BOT BUFFS: " + bot.getName() + " (objId=" + bot.getObjectId() + ") ===");

        if (activeBuffIds.isEmpty()) {
            gm.sendMessage("Δεν έχει ενεργά buffs.");
        } else {
            for (L2Effect ef : effects) {
                if (ef.getEffectType() != L2EffectType.BUFF)
                    continue;
                final int id = ef.getSkill().getId();
                final int lvl = ef.getSkill().getLevel();
                final int remain = Math.max(0, ef.getPeriod() - ef.getTime()); // seconds remaining
                final int total = ef.getPeriod();
                final boolean inTemplate = templateIds.contains(id);

                L2Skill skl = SkillTable.getInstance().getInfo(id, ef.getSkill().getLevel());
                String name = (skl != null ? skl.getName() : "Unknown");

                gm.sendMessage(String.format("%s %4d (%s) lvl=%d remain=%ds/%ds",
                        inTemplate ? "*" : " ",
                        id,
                        name,
                        lvl,
                        remain,
                        total));
            }
        }

        Set<Integer> missing = templateIds.stream()
                .filter(id -> !activeBuffIds.contains(id))
                .collect(Collectors.toSet());

        if (missing.isEmpty()) {
            gm.sendMessage("Template: OK (κανένα missing).");
        } else {
            gm.sendMessage("Missing (" + missing.size() + "): " + missing.stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }

        gm.sendMessage("* = στο template (" + templateIds.size() + " συνολικά).");
        gm.sendMessage("========================================");
    }

    /**
     * Προς το παρόν fighter/mage templates ίδια. Επιστρέφουμε ένα από τα δύο.
     */
    private int[][] resolveTemplate(FakePlayer bot) {
        return FakeHelpers.getFighterBuffs();
    }

    @Override
    public String[] getAdminCommandList() {
        return ADMIN_COMMANDS;
    }
}