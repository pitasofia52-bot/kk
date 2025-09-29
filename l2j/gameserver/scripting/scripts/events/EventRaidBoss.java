package net.sf.l2j.gameserver.scripting.scripts.events;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
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

    private static final int ITEM_SCROLL = 7125;
    private static final int GIVE_SCROLLS = 10;

    private static final long DESPAWN_RB_MS = 6L * 60 * 60 * 1000;     // 6 hours
    private static final long DESPAWN_TP_MS = 20L * 60 * 1000;         // 20 minutes

    // Shared (singleton-like) state across all instances (because scripts.xml spawns two instances per day)
    private static volatile boolean ACTIVE = false;
    private static volatile EventRaidBoss OWNER = null;
    private static Npc s_raid;
    private static Npc s_teleporter;
    private static final Set<Integer> GRANTED_THIS_WINDOW = ConcurrentHashMap.newKeySet();

    public EventRaidBoss()
    {
        super(-1, "events");

        addKillId(RAID_IDS);
        addFirstTalkId(TELEPORTER_NPC);
        addTalkId(TELEPORTER_NPC);
        addItemUse(ITEM_SCROLL);
    }

    // Manual trigger from admin handler. Returns true if started now, false if already active or blocked by siege.
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
            // Siege guard: do not start while any siege is active.
            for (Castle c : CastleManager.getInstance().getCastles())
            {
                if (c.getSiege().isInProgress())
                    return false;
            }

            // Prevent duplicate windows across multiple script instances.
            if (ACTIVE)
                return false;

            ACTIVE = true;
            OWNER = this;
            GRANTED_THIS_WINDOW.clear();

            // Spawn random RB
            final int rbId = RAID_IDS[Rnd.get(RAID_IDS.length)];
            s_raid = addSpawn(rbId, RB_LOC, false, DESPAWN_RB_MS, false);

            // Spawn Teleporter (20 minutes)
            s_teleporter = addSpawn(TELEPORTER_NPC, TP_LOC, false, DESPAWN_TP_MS, false);

            // Announce
            Broadcast.announceToOnlinePlayers("Event Raid Boss: teleport from Giran is now open.");

            // Note: Scrolls are now granted on teleport, not here.
            return true;
        }
    }

    @Override
    protected void onEnd()
    {
        synchronized (EventRaidBoss.class)
        {
            // Only the instance that started the current window should end it.
            if (!ACTIVE || OWNER != this)
                return;

            if (s_teleporter != null)
            {
                s_teleporter.deleteMe();
                s_teleporter = null;
            }

            // Remove remaining event scrolls from online players at window end.
            for (Player p : World.getInstance().getPlayers())
            {
                if (p == null || !p.isOnline())
                    continue;

                final ItemInstance it = p.getInventory().getItemByItemId(ITEM_SCROLL);
                if (it != null && it.getCount() > 0)
                    p.destroyItemByItemId("EventRaidBossEnd", ITEM_SCROLL, it.getCount(), null, true);
            }

            ACTIVE = false;
            OWNER = null;
            GRANTED_THIS_WINDOW.clear();
        }
    }

    @Override
    public String onKill(Npc npc, Player killer, boolean isPet)
    {
        synchronized (EventRaidBoss.class)
        {
            if (!ACTIVE)
                return null;

            if (s_raid != null && npc.getObjectId() == s_raid.getObjectId())
            {
                s_raid = null;

                if (s_teleporter != null)
                {
                    s_teleporter.deleteMe();
                    s_teleporter = null;
                }
            }
        }
        return null;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player)
    {
        StringBuilder sb = new StringBuilder(200);
        sb.append("<html><body>");
        sb.append("<center><br><font color=\"LEVEL\">Event Raid Boss</font><br><br>");
        sb.append("Teleport from Giran to the Raid Boss area.<br><br>");
        if (ACTIVE)
            sb.append("<button value=\"Teleport\" action=\"bypass -h Quest ").append(getName()).append(" teleport\" width=100 height=24 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
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
            {
                // Give scrolls once per player per event window.
                if (GRANTED_THIS_WINDOW.add(player.getObjectId()))
                    player.addItem("EventRaidBoss", ITEM_SCROLL, GIVE_SCROLLS, null, true);

                player.teleToLocation(RB_LOC, 0);
            }
            else
                player.sendMessage("Event Raid Boss is not active.");
        }
        return null;
    }

    @Override
    public String onItemUse(ItemInstance item, Player player, WorldObject target)
    {
        if (item.getItemId() == ITEM_SCROLL && !ACTIVE)
            return "This item can only be used during the Event Raid Boss.";
        return null;
    }
}