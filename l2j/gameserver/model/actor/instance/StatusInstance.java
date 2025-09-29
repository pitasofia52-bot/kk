package net.sf.l2j.gameserver.model.actor.instance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.StringTokenizer;
import java.util.logging.Level;

import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.gameserver.cache.HtmCache;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

// Integration: import botkills storage
import com.elfocrash.roboto.pvp.BotKillsStorage;

public class StatusInstance extends Folk
{
    private static class PlayerInfo
    {
        public PlayerInfo(int pos, String n, int pvps, int pks)
        {
            position = pos;
            Nick = n;
            pvpCount = pvps;
            pkCount = pks;
        }

        public int position;
        public String Nick;
        public int pvpCount;
        public int pkCount;
    }

    // delay interval (in minutes):
    private final int delayForCheck = 10;

    // number of players to be listed
    private int pvpListCount = 20;
    private int pkListCount = 20;

    private PlayerInfo[] topPvPList = new PlayerInfo[pvpListCount];
    private PlayerInfo[] topPkList = new PlayerInfo[pkListCount];

    @SuppressWarnings("synthetic-access")
    public StatusInstance(int objectId, NpcTemplate template)
    {
        super(objectId, template);
        ThreadPool.scheduleAtFixedRate(new RefreshAllLists(), 10000, delayForCheck * 60000);
    }

    private class RefreshAllLists implements Runnable
    {
        @Override
        public void run()
        {
            ReloadData();
        }
    }

    private void ReloadData()
    {
        // Integration: Use a map to merge player DB and bot storage for both PvP/PK
        Map<String, Integer> pvpMap = new HashMap<>();
        Map<String, Integer> pkMap = new HashMap<>();

        // --- Step 1: Load real players from DB ---
        try (Connection con = L2DatabaseFactory.getInstance().getConnection())
        {
            // PvP
            PreparedStatement statement = con.prepareStatement("SELECT char_name, pvpkills FROM characters");
            ResultSet result = statement.executeQuery();
            while (result.next())
            {
                String name = result.getString("char_name");
                int pvp = result.getInt("pvpkills");
                if (pvp > 0)
                    pvpMap.put(name, pvp);
            }
            result.close();
            statement.close();

            // PK
            statement = con.prepareStatement("SELECT char_name, pkkills FROM characters");
            result = statement.executeQuery();
            while (result.next())
            {
                String name = result.getString("char_name");
                int pk = result.getInt("pkkills");
                if (pk > 0)
                    pkMap.put(name, pk);
            }
            result.close();
            statement.close();
        }
        catch (SQLException e)
        {
            _log.log(Level.WARNING, "ranking (status): could not load statistics informations" + e.getMessage(), e);
        }

        // --- Step 2: Load bots from BotKillsStorage ---
        try {
            List<Map.Entry<String, BotKillsStorage.BotKills>> bots = BotKillsStorage.getInstance().getTopBots(0); // 0 = all
            for (Map.Entry<String, BotKillsStorage.BotKills> entry : bots) {
                String name = entry.getKey();
                BotKillsStorage.BotKills kills = entry.getValue();
                if (kills.pvp > 0)
                    pvpMap.put(name, Math.max(pvpMap.getOrDefault(name, 0), kills.pvp)); // If same name, keep max
                if (kills.pk > 0)
                    pkMap.put(name, Math.max(pkMap.getOrDefault(name, 0), kills.pk));
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "ranking (status): could not load botkills storage: " + e.getMessage(), e);
        }

        // --- Step 3: Sort PvP/PK and fill arrays ---
        // PvP
        List<Map.Entry<String, Integer>> pvpSorted = new ArrayList<>(pvpMap.entrySet());
        pvpSorted.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            if (cmp != 0) return cmp;
            return a.getKey().compareToIgnoreCase(b.getKey());
        });

        for (int i = 0; i < pvpListCount; i++) {
            if (i < pvpSorted.size()) {
                Map.Entry<String, Integer> e = pvpSorted.get(i);
                topPvPList[i] = new PlayerInfo(i + 1, e.getKey(), e.getValue(), 0);
            } else {
                topPvPList[i] = null;
            }
        }

        // PK
        List<Map.Entry<String, Integer>> pkSorted = new ArrayList<>(pkMap.entrySet());
        pkSorted.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            if (cmp != 0) return cmp;
            return a.getKey().compareToIgnoreCase(b.getKey());
        });

        for (int i = 0; i < pkListCount; i++) {
            if (i < pkSorted.size()) {
                Map.Entry<String, Integer> e = pkSorted.get(i);
                topPkList[i] = new PlayerInfo(i + 1, e.getKey(), 0, e.getValue());
            } else {
                topPkList[i] = null;
            }
        }
    }

    @Override
    public void onSpawn()
    {
        ReloadData();
    }

    @Override
    public void showChatWindow(Player player)
    {
        GeneratePvPList(player);
    }

    @Override
    public void onBypassFeedback(Player player, String command)
    {
        StringTokenizer st = new StringTokenizer(command, " ");
        String currentCommand = st.nextToken();

        if (currentCommand.startsWith("pvplist"))
        {
            GeneratePvPList(player);
        }
        else if (currentCommand.startsWith("pklist"))
        {
            GeneratePKList(player);
        }

        super.onBypassFeedback(player, command);
    }

    private void GeneratePvPList(Player p)
    {
        StringBuilder _PVPranking = new StringBuilder();
        for (PlayerInfo player : topPvPList)
        {
            if (player == null) break;

            _PVPranking.append("<table width=\"290\"><tr>");
            _PVPranking.append("<td FIXWIDTH=\"2\" align=\"center\"></td>");
            _PVPranking.append("<td FIXWIDTH=\"17\" align=\"center\">" + player.position + "</td>");
            _PVPranking.append("<td FIXWIDTH=\"158\" align=\"center\">" + player.Nick + "</td>");
            _PVPranking.append("<td FIXWIDTH=\"90\" align=\"center\">" + player.pvpCount + "</td>");
            _PVPranking.append("<td FIXWIDTH=\"2\" align=\"center\"></td>");
            _PVPranking.append("</tr></table>");
            _PVPranking.append("<img src=\"L2UI.Squaregray\" width=\"300\" height=\"1\">");
        }

        NpcHtmlMessage html = new NpcHtmlMessage(1);
        html.setFile(getHtmlPath(getNpcId(), 0));
        html.replace("%objectId%", getObjectId());
        html.replace("%pvplist%", _PVPranking.toString());
        p.sendPacket(html);
    }

    private void GeneratePKList(Player p)
    {
        StringBuilder _PKranking = new StringBuilder();
        for (PlayerInfo player : topPkList)
        {
            if (player == null) break;

            _PKranking.append("<table width=\"290\"><tr>");
            _PKranking.append("<td FIXWIDTH=\"2\" align=\"center\"></td>");
            _PKranking.append("<td FIXWIDTH=\"17\" align=\"center\">" + player.position + "</td>");
            _PKranking.append("<td FIXWIDTH=\"158\" align=\"center\">" + player.Nick + "</td>");
            _PKranking.append("<td FIXWIDTH=\"90\" align=\"center\">" + player.pkCount + "</td>");
            _PKranking.append("<td FIXWIDTH=\"2\" align=\"center\"></td>");
            _PKranking.append("</tr></table>");
            _PKranking.append("<img src=\"L2UI.Squaregray\" width=\"300\" height=\"1\">");
        }

        NpcHtmlMessage html = new NpcHtmlMessage(1);
        html.setFile(getHtmlPath(getNpcId(), 2));
        html.replace("%objectId%", getObjectId());
        html.replace("%pklist%", _PKranking.toString());
        p.sendPacket(html);
    }

    @Override
    public String getHtmlPath(int npcId, int val)
    {
        String filename;

        if (val == 0)
            filename = "data/html/Status/" + npcId + ".htm";
        else
            filename = "data/html/Status/" + npcId + "-" + val + ".htm";

        if (HtmCache.getInstance().isLoadable(filename))
            return filename;

        return "data/html/Status/" + npcId + ".htm";
    }
}