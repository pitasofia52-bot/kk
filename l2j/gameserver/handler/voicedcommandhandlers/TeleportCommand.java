package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.commons.concurrent.ThreadPool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportCommand implements IVoicedCommandHandler
{
    private static final String[] VOICED_COMMANDS = { "teleport" };

    private static final int[] LOCATION1 = { 83400, 148195, -3403 };
    private static final int[] LOCATION2 = { 83400, 148195, -3403 };
    private static final int[] LOCATION3 = { 83400, 148195, -3403 };

    private static final long COOLDOWN = 10 * 60 * 1000L; // 10 λεπτά
    private static final long TELEPORT_DELAY = 3 * 1000L; // 5 δευτερόλεπτα

    private static final Map<Integer, Long> _lastTeleportTime = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> _inTeleportProtection = new ConcurrentHashMap<>();

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String params)
    {
        if (activeChar == null)
            return false;

        if (params == null)
        {
            activeChar.sendMessage("Usage: .teleport 1 | 2 | 3");
            return true;
        }

        int loc = 0;
        try {
            loc = Integer.parseInt(params.trim());
        } catch (Exception e) {
            activeChar.sendMessage("Usage: .teleport 1 | 2 | 3");
            return true;
        }

        int[] coords;
        switch (loc) {
            case 1:
                coords = LOCATION1;
                break;
            case 2:
                coords = LOCATION2;
                break;
            case 3:
                coords = LOCATION3;
                break;
            default:
                activeChar.sendMessage("Usage: .teleport 1 | 2 | 3");
                return true;
        }

        long now = System.currentTimeMillis();
        long last = _lastTeleportTime.getOrDefault(activeChar.getObjectId(), 0L);
        if (now - last < COOLDOWN) {
            long left = (COOLDOWN - (now - last)) / 1000;
            activeChar.sendMessage("You must wait " + left + " seconds before teleporting again.");
            return true;
        }

        // Ενεργοποίηση προστασίας
        _inTeleportProtection.put(activeChar.getObjectId(), true);
        activeChar.setIsInvul(true);
        activeChar.setTarget(null);
        activeChar.abortAttack();
        activeChar.abortCast();
        activeChar.broadcastPacket(new net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse(
        activeChar, activeChar, 2036, 1, 5000, 0));
        activeChar.sendMessage("Teleporting in 5 seconds... You are invulnerable!");
        activeChar.sendMessage("Do not move or logout. You are protected from damage.");

        ThreadPool.schedule(() -> {
            // SAFETY: Έλεγχος αν ο παίκτης είναι ακόμα στον κόσμο!
            if (activeChar.isOnline() && activeChar.isVisible())
            {
                _inTeleportProtection.remove(activeChar.getObjectId());
                activeChar.setIsInvul(false);
                activeChar.teleToLocation(coords[0], coords[1], coords[2], 20);
                activeChar.sendMessage("Teleported!");
                _lastTeleportTime.put(activeChar.getObjectId(), System.currentTimeMillis());
            }
        }, TELEPORT_DELAY);

        return true;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }
}