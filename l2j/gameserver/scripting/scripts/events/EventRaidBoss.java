package net.sf.l2j.gameserver.scripting.scripts.events;

import java.util.Collection;

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
    private static final int[] RAID_IDS = { 60233, 60234, 60235, 60236 };
    private static final SpawnLocation RB_LOC = new SpawnLocation(174239, -88025, -5117, 0);
    private static final int TELEPORTER_NPC = 50011;
    private static final SpawnLocation TP_LOC = new SpawnLocation(83511, 148641, -3408, 0);

    private static final int ITEM_SCROLL = 7125;
    private static final int GIVE_SCROLLS = 10;

    private static final long DESPAWN_RB_MS = 6L * 60 * 60 * 1000;     // 6 hours
    private static final long DESPAWN_TP_MS = 20L * 60 * 1000;         // 20 minutes

    private volatile boolean _activeWindow = false;
    private Npc _raid;
    private Npc _teleporter;

    public EventRaidBoss()
    {
        super(-1, "events");
        addKillId(RAID_IDS);
        addFirstTalkId(TELEPORTER_NPC);
        addTalkId(TELEPORTER_NPC);
        addItemUse(ITEM_SCROLL);
    }

    // Manual trigger from admin handler. Returns true if started now, false if already active or blocked by siege.
    public synchronized boolean startNow()
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
        // Block if siege active
        for (Castle c : CastleManager.getInstance().getCastles())
        {
            if (c.getSiege().isInProgress())
                return false;
        }

        // Prevent duplicate spawns if this instance is already active or spawned.
        if (_activeWindow || _raid != null || _teleporter != null)
            return false;

        _activeWindow = true;

        // Spawn random RB
        final int rbId = RAID_IDS[Rnd.get(RAID_IDS.length)];
        _raid = addSpawn(rbId, RB_LOC, false, DESPAWN_RB_MS, false);

        // Spawn teleporter (20 minutes)
        _teleporter = addSpawn(TELEPORTER_NPC, TP_LOC, false, DESPAWN_TP_MS, false);

        // Announce
        Broadcast.announceToOnlinePlayers("Event Raid Boss: teleport from Giran is now open.");

        // Give 10x 7125 to all online players
        for (Player p : World.getInstance().getPlayers())
        {
            if (p != null && p.isOnline())
                p.addItem("EventRaidBoss", ITEM_SCROLL, GIVE_SCROLLS, null, true);
        }
        return true;
    }

    @Override
    protected void onEnd()
    {
        if (!_activeWindow)
            return;

        if (_teleporter != null)
        {
            _teleporter.deleteMe();
            _teleporter = null;
        }

        for (Player p : World.getInstance().getPlayers())
        {
            if (p == null || !p.isOnline())
                continue;

            final ItemInstance it = p.getInventory().getItemByItemId(ITEM_SCROLL);
            if (it != null && it.getCount() > 0)
                p.destroyItemByItemId("EventRaidBossEnd", ITEM_SCROLL, it.getCount(), null, true);
        }

        _activeWindow = false;
    }

    @Override
    public String onKill(Npc npc, Player killer, boolean isPet)
    {
        if (_activeWindow && _raid != null && npc.getObjectId() == _raid.getObjectId())
        {
            _raid = null;

            if (_teleporter != null)
            {
                _teleporter.deleteMe();
                _teleporter = null;
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
        if (_activeWindow && _raid != null)
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
            if (_activeWindow && _raid != null)
                player.teleToLocation(RB_LOC, 0);
            else
                player.sendMessage("Event Raid Boss is not active.");
        }
        return null;
    }

    @Override
    public String onItemUse(ItemInstance item, Player player, WorldObject target)
    {
        if (item.getItemId() == ITEM_SCROLL && !_activeWindow)
            return "This item can only be used during the Event Raid Boss.";
        return null;
    }
}