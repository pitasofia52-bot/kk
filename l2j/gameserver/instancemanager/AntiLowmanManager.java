package net.sf.l2j.gameserver.instancemanager;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;

/**
 * Anti-lowman protection for specific Raid Boss IDs.
 * - Invisible: applies only as extra HP regen multiplier (no visible buff).
 * - Lightweight: per-instance state updated on hits, sliding window cleanup.
 * - Separate handling per boss ID.
 */
public final class AntiLowmanManager {

    // Group A: limit 5, gate 60 minutes
    private static final int[] GROUP_A_IDS = {
        25293, 25450, 25050, 25163, 60228, 60229, 60230, 60231, 60232
    };

    // Group B: limit 10, gate 90 minutes
    private static final int[] GROUP_B_IDS = {
        60233, 60234, 60235, 60236
    };

    // Limits and time gates
    private static final int LIMIT_A = 5;
    private static final int LIMIT_B = 10;

    private static final long GATE_A_MS = 60L * 60L * 1000L;  // 60 minutes
    private static final long GATE_B_MS = 90L * 60L * 1000L;  // 90 minutes

    // Activity and reset windows
    private static final long ACTIVITY_WINDOW_MS = 60L * 1000L;   // 60 seconds
    private static final long COMBAT_RESET_MS   = 10L * 60L * 1000L; // 10 minutes without damage

    // Regen multipliers (invisible)
    private static final double MULT_EARLY = 12.0; // 0%..50% of time gate
    private static final double MULT_LATE  = 20.0; // 50%..100% of time gate

    // Fast lookup sets
    private static final Set<Integer> GROUP_A_SET = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Integer> GROUP_B_SET = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static {
        for (int id : GROUP_A_IDS) GROUP_A_SET.add(id);
        for (int id : GROUP_B_IDS) GROUP_B_SET.add(id);
    }

    // Per-instance state (keyed by boss objectId)
    private static final class BossState {
        final int npcId;
        volatile long firstHitAt;   // when fight started
        volatile long lastDamageAt; // last time we saw damage
        final Map<Integer, Long> attackers = new ConcurrentHashMap<>(); // playerObjectId -> lastHitAt

        BossState(int npcId) {
            this.npcId = npcId;
            this.firstHitAt = 0L;
            this.lastDamageAt = 0L;
        }

        void onHit(Player p, long now) {
            if (firstHitAt == 0L) {
                firstHitAt = now;
            }
            lastDamageAt = now;

            // record/update attacker timestamp
            attackers.put(p.getObjectId(), now);

            // sliding window cleanup
            final long threshold = now - ACTIVITY_WINDOW_MS;
            attackers.entrySet().removeIf(e -> e.getValue() < threshold);
        }

        void reset() {
            firstHitAt = 0L;
            lastDamageAt = 0L;
            attackers.clear();
        }

        int activeAttackersCount(long now) {
            final long threshold = now - ACTIVITY_WINDOW_MS;
            // prune and count
            attackers.entrySet().removeIf(e -> e.getValue() < threshold);
            return attackers.size();
        }
    }

    private final Map<Integer, BossState> states = new ConcurrentHashMap<>();

    private AntiLowmanManager() {}

    private static final class Holder {
        private static final AntiLowmanManager INSTANCE = new AntiLowmanManager();
    }

    public static AntiLowmanManager getInstance() {
        return Holder.INSTANCE;
    }

    private boolean isManagedId(int npcId) {
        return GROUP_A_SET.contains(npcId) || GROUP_B_SET.contains(npcId);
    }

    private int limitFor(int npcId) {
        return GROUP_A_SET.contains(npcId) ? LIMIT_A : LIMIT_B;
    }

    private long gateFor(int npcId) {
        return GROUP_A_SET.contains(npcId) ? GATE_A_MS : GATE_B_MS;
    }

    /**
     * Record a hit from a player to a boss. Very lightweight, called only when isRaid() and the ID is managed.
     */
    public void onHit(Npc boss, Player p) {
        if (boss == null || p == null) return;
        final int npcId = boss.getNpcId();
        if (!isManagedId(npcId)) return;

        final long now = System.currentTimeMillis();
        final int key = boss.getObjectId();

        final BossState st = states.computeIfAbsent(key, k -> new BossState(npcId));
        // If this state belongs to a different template instance (edge case), refresh npcId
        if (st.npcId != npcId) {
            // replace with a fresh state
            final BossState ns = new BossState(npcId);
            states.put(key, ns);
            ns.onHit(p, now);
            return;
        }
        st.onHit(p, now);
    }

    /**
     * Return extra regen multiplier for a boss instance. 1.0 when disabled.
     * Invisible to players (only affects Formulas.calcHpRegen).
     */
    public double getExtraRegenMultiplier(Npc boss) {
        if (boss == null) return 1.0;
        final int npcId = boss.getNpcId();
        if (!isManagedId(npcId)) return 1.0;

        final int key = boss.getObjectId();
        final BossState st = states.get(key);
        if (st == null) return 1.0;

        final long now = System.currentTimeMillis();

        // Reset if idle for too long
        if (st.lastDamageAt > 0 && (now - st.lastDamageAt) > COMBAT_RESET_MS) {
            st.reset();
            return 1.0;
        }

        // Not started yet
        if (st.firstHitAt == 0L) return 1.0;

        final long elapsed = now - st.firstHitAt;
        final long gate = gateFor(npcId);
        if (elapsed >= gate) {
            return 1.0; // gate expired
        }

        final int active = st.activeAttackersCount(now);
        final int limit = limitFor(npcId);
        if (active > limit) {
            return 1.0; // too many attackers -> disable
        }

        // Two-stage multiplier for stronger anti-lowman the longer it lasts
        final double fraction = (gate > 0) ? (double) elapsed / (double) gate : 1.0;
        return (fraction < 0.5) ? MULT_EARLY : MULT_LATE;
    }

    /**
     * Clear state for a boss instance (optional utility).
     */
    public void clear(Npc boss) {
        if (boss == null) return;
        states.remove(boss.getObjectId());
    }
}