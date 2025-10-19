package net.sf.l2j.gameserver.custom.autofarm;

import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.ai.CtrlIntention;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.actor.instance.RaidBoss;
import net.sf.l2j.gameserver.model.actor.instance.GrandBoss;
import net.sf.l2j.gameserver.model.zone.ZoneId;

public final class AutoFarmTask implements Runnable {
    private static final int RADIUS = 2000;

    private final int objectId;

    public AutoFarmTask(int objectId) {
        this.objectId = objectId;
    }

    @Override
    public void run() {
        final Player player = resolvePlayer(objectId);
        if (player == null) {
            AutoFarmManager.getInstance().stopInternal(objectId);
            return;
        }

        if (player.isAlikeDead() || player.isDead()) {
            player.sendMessage("Auto-farm stopped: you are dead.");
            AutoFarmManager.getInstance().stopInternal(objectId);
            return;
        }

        if (player.isInsideZone(ZoneId.PEACE)) {
            player.sendMessage("Auto-farm stopped in peace zone.");
            AutoFarmManager.getInstance().stopInternal(objectId);
            return;
        }

        // Αν ήδη επιτίθεται/κάνει cast, άφησέ τον
        if (player.isAttackingNow() || player.isCastingNow()) {
            return;
        }

        final Monster target = pickNearestVisibleMonster(player, RADIUS);
        if (target != null) {
            if (player.getTarget() != target) {
                player.setTarget(target);
            }
            player.getAI().setIntention(CtrlIntention.ATTACK, target);
        }
        // Αν δεν υπάρχει στόχος στο radius, περιμένει το επόμενο tick.
    }

    private Monster pickNearestVisibleMonster(Player player, int radius) {
        Monster closest = null;
        double bestDist2 = Double.MAX_VALUE;

        for (Monster m : player.getKnownTypeInRadius(Monster.class, radius)) {
            if (m == null || m.isDead()) continue;
            if (Creature.isInsidePeaceZone(player, m)) continue;
            if (!GeoEngine.getInstance().canSeeTarget(player, m)) continue;

            // ΜΗ χτυπάς bosses ή raid minions
            if (m instanceof RaidBoss || m instanceof GrandBoss) continue;
            if (m.isRaid() || m.isRaidMinion()) continue;

            // Απόφυγε mobs που ήδη τα χτυπάει/στοχεύει άλλος παίκτης
            if (isContestedByOtherPlayer(player, m, radius)) continue;

            double dx = m.getX() - player.getX();
            double dy = m.getY() - player.getY();
            double dz = m.getZ() - player.getZ();
            double d2 = dx * dx + dy * dy + dz * dz;

            if (d2 < bestDist2) {
                bestDist2 = d2;
                closest = m;
            }
        }
        return closest;
    }

    private boolean isContestedByOtherPlayer(Player me, Monster mob, int checkRadius) {
        // Αν το mob έχει ήδη target άλλον παίκτη, θεωρείται contested
        if (mob.getTarget() instanceof Player && mob.getTarget() != me) {
            return true;
        }

        // Αν άλλος παίκτης κοντά το στοχεύει και είναι σε επίθεση/μάχη, απόφυγέ το
        for (Player other : me.getKnownTypeInRadius(Player.class, checkRadius)) {
            if (other == null || other == me) continue;
            if (other.isAlikeDead() || other.isDead()) continue;

            if (other.getTarget() == mob && (other.isAttackingNow() || other.isInCombat())) {
                return true;
            }
        }

        return false;
    }

    private Player resolvePlayer(int objId) {
        try {
            Player p = World.getInstance().getPlayer(objId);
            if (p != null) return p;
        } catch (Throwable ignore) {
        }
        for (Player p : World.getInstance().getPlayers()) {
            if (p.getObjectId() == objId) return p;
        }
        return null;
    }
}