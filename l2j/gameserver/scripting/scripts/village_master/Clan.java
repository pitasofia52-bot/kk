package net.sf.l2j.gameserver.scripting.scripts.village_master;

import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.scripting.Quest;

public class Clan extends Quest
{
    public Clan()
    {
        super(-1, "village_master");

        addStartNpc(30026, 30031, 30037, 30066, 30070, 30109, 30115, 30120, 30154, 30174, 30175, 30176, 30187, 30191, 30195, 30288, 30289, 30290, 30297, 30358, 30373, 30462, 30474, 30498, 30499, 30500, 30503, 30504, 30505, 30508, 30511, 30512, 30513, 30520, 30525, 30565, 30594, 30595, 30676, 30677, 30681, 30685, 30687, 30689, 30694, 30699, 30704, 30845, 30847, 30849, 30854, 30857, 30862, 30865, 30894, 30897, 30900, 30905, 30910, 30913, 31269, 31272, 31276, 31279, 31285, 31288, 31314, 31317, 31321, 31324, 31326, 31328, 31331, 31334, 31336, 31755, 31958, 31961, 31965, 31968, 31974, 31977, 31996, 32092, 32093, 32094, 32095, 32096, 32097, 32098);
        addTalkId(30026, 30031, 30037, 30066, 30070, 30109, 30115, 30120, 30154, 30174, 30175, 30176, 30187, 30191, 30195, 30288, 30289, 30290, 30297, 30358, 30373, 30462, 30474, 30498, 30499, 30500, 30503, 30504, 30505, 30508, 30511, 30512, 30513, 30520, 30525, 30565, 30594, 30595, 30676, 30677, 30681, 30685, 30687, 30689, 30694, 30699, 30704, 30845, 30847, 30849, 30854, 30857, 30862, 30865, 30894, 30897, 30900, 30905, 30910, 30913, 31269, 31272, 31276, 31279, 31285, 31288, 31314, 31317, 31321, 31324, 31326, 31328, 31331, 31334, 31336, 31755, 31958, 31961, 31965, 31968, 31974, 31977, 31996, 32092, 32093, 32094, 32095, 32096, 32097, 32098);
    }

    @Override
    public String onAdvEvent(String event, Npc npc, Player player)
    {
        System.out.println("[DEBUG] Clan.java - onAdvEvent: event=" + event + ", player=" + (player != null ? player.getName() : "null"));

        if (event.startsWith("create_clan "))
        {
            String clanName = event.substring("create_clan ".length()).trim();
            System.out.println("[DEBUG] Clan.java - create_clan received: clanName=" + clanName);

            if (clanName.isEmpty()) {
                System.out.println("[DEBUG] Clan.java - Empty clan name");
                return "9000-02.htm"; // Fade back to form if no name
            }

            System.out.println("[DEBUG] Clan.java - Calling ClanTable.createClan");
            ClanTable.getInstance().createClan(player, clanName);

            if (player.getClan() == null) {
                System.out.println("[DEBUG] Clan.java - Clan creation failed for player: " + player.getName());
                return "9000-02.htm";
            }
            System.out.println("[DEBUG] Clan.java - Clan creation success for player: " + player.getName());
            return "9000-01.htm";
        }

        switch (event)
        {
            case "9000-03.htm":
                if (!player.isClanLeader())
                    return "9000-03-no.htm";
                break;
            case "9000-04.htm":
                if (!player.isClanLeader())
                    return "9000-04-no.htm";
                break;
            case "9000-05.htm":
                if (!player.isClanLeader())
                    return "9000-05-no.htm";
                break;
            case "9000-06a.htm":
            case "9000-07.htm":
            case "9000-12a.htm":
            case "9000-13a.htm":
            case "9000-13b.htm":
            case "9000-14a.htm":
            case "9000-15.htm":
                if (!player.isClanLeader())
                    return "9000-07-no.htm";
                break;
        }

        return event;
    }

    @Override
    public String onTalk(Npc npc, Player player)
    {
        System.out.println("[DEBUG] Clan.java - onTalk: npc=" + (npc != null ? npc.getNpcId() : "null") + ", player=" + (player != null ? player.getName() : "null"));
        return "9000-01.htm";
    }
}