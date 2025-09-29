package net.sf.l2j.gameserver.scripting.scripts.events;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.location.SpawnLocation;
import net.sf.l2j.gameserver.scripting.ScheduledQuest;
import net.sf.l2j.gameserver.util.Broadcast;

public class EventRaidBoss extends ScheduledQuest
{
    // Config
    private static final int[] RAID_IDS = { 60233, 60234, 60235, 60236 };
    private static final SpawnLocation RB_LOC = new SpawnLocation(174239, -88025, -5117, 0);
    private static final int TELEPORTER_NPC = 50011;
    private static final SpawnLocation TP_LOC = new SpawnLocation(83511, 148641, -3408, 0);

    private static final long DESPAWN_RB_MS = 6L * 60 * 60 * 1000; // 6 hours

    // Shared state (γιατί υπάρχουν 2 instances από το schedule)
    private static volatile boolean ACTIVE = false;
    private static volatile EventRaidBoss OWNER = null;
    private static Npc s_raid;
    private static Npc s_teleporter;

    // Optional: για debug/diagnostics ποιος το ξεκίνησε, πότε κ.λπ.
    private static final ConcurrentMap<String, Long> META = new ConcurrentHashMap<>();

    public EventRaidBoss()
    {
        super(-1, "events");

        addKillId(RAID_IDS);
        addFirstTalkId(TELEPORTER_NPC);
        addTalkId(TELEPORTER_NPC);
        // No item hooks πλέον (χωρίς scrolls).
    }

    // Manual trigger από admin handler.
    public boolean startNow()
    {
        return doStart();
    }

    @Override
    protected void onStart()
    {
        doStart();
    }

    private boolean doStart()
    {
        synchronized (EventRaidBoss.class)
        {
            // Μην ξεκινήσεις αν υπάρχει ενεργό siege.
            for (Castle c : CastleManager.getInstance().getCastles())
            {
                if (c.getSiege().isInProgress())
                    return false;
            }

            // Μην διπλοξεκινήσεις.
            if (ACTIVE)
                return false;

            ACTIVE = true;
            OWNER = this;
            META.put("startedAt", System.currentTimeMillis());

            // Spawn RB για 6 ώρες (auto-despawn).
            final int rbId = RAID_IDS[Rnd.get(RAID_IDS.length)];
            s_raid = addSpawn(rbId, RB_LOC, false, DESPAWN_RB_MS, false);

            // Spawn Teleporter χωρίς despawn (μένει μέχρι kill/hard-end).
            s_teleporter = addSpawn(TELEPORTER_NPC, TP_LOC, false, 0, false);

            // Ανακοίνωση
            Broadcast.announceToOnlinePlayers("Event Raid Boss: teleport from Giran is now open.");

            // Hard end safety μετά από 6 ώρες (αν δεν πεθάνει ο RB).
            startQuestTimer("ERB_HARD_END", DESPAWN_RB_MS, null, null, false);

            return true;
        }
    }

    // Δεν κλείνουμε το event στα 20' του schedule.
    @Override
    protected void onEnd()
    {
        // no-op by design
    }

    private void endEventCleanup(String reason)
    {
        synchronized (EventRaidBoss.class)
        {
            if (!ACTIVE)
                return;

            META.put("endedAt", System.currentTimeMillis());
            META.put("reason:" + reason, System.currentTimeMillis());

            if (s_teleporter != null)
            {
                s_teleporter.deleteMe();
                s_teleporter = null;
            }

            if (s_raid != null)
            {
                s_raid.deleteMe(); // σε περίπτωση που δεν έχει auto-despawn ακόμη.
                s_raid = null;
            }

            ACTIVE = false;
            OWNER = null;
        }
    }

    @Override
    public String onKill(Npc npc, Player killer, boolean isPet)
    {
        synchronized (EventRaidBoss.class)
        {
            if (!ACTIVE || s_raid == null)
                return null;

            if (npc.getObjectId() == s_raid.getObjectId())
            {
                // RB πέθανε -> σβήσε teleporter αμέσως και κλείσε event.
                endEventCleanup("KILL");
            }
        }
        return null;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player)
    {
        StringBuilder sb = new StringBuilder(256);
        sb.append("<html><body>");
        sb.append("<center><br><font color=\"LEVEL\">Event Raid Boss</font><br><br>");
        sb.append("Teleport from Giran to the Raid Boss area.<br><br>");
        if (ACTIVE)
            sb.append("<button value=\"Teleport\" action=\"bypass -h Quest ").append(getName()).append(" teleport\" width=120 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        else
            sb.append("<font color=\"FF0000\">The event is not active.</font>");
        sb.append("</center></body></html>");
        return sb.toString();
    }

    @Override
    public String onTalk(Npc npc, Player player)
    {
        return onFirstTalk(npc, player);
    }

    @Override
    public String onAdvEvent(String event, Npc npc, Player player)
    {
        if ("teleport".equalsIgnoreCase(event))
        {
            if (ACTIVE)
                player.teleToLocation(RB_LOC, 0);
            else
                player.sendMessage("Event Raid Boss is not active.");
        }
        else if ("ERB_HARD_END".equalsIgnoreCase(event))
        {
            // Μόνο ο OWNER instance κλείνει (ώστε να μη "διασταυρωθούν" τα δύο schedules).
            if (OWNER == this && ACTIVE)
                endEventCleanup("HARD_END");
        }
        return null;
    }

    @Override
    public String onItemUse(net.sf.l2j.gameserver.model.item.instance.ItemInstance item, Player player, WorldObject target)
    {
        // Δεν υπάρχει πια λογική scrolls — επιστρέφουμε null.
        return null;
    }
}