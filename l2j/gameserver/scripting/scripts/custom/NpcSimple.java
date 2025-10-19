package net.sf.l2j.gameserver.scripting.scripts.custom;

import net.sf.l2j.gameserver.custom.tvt.TvTManager;
import net.sf.l2j.gameserver.custom.tvt.TvTState;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.scripting.Quest;

public class NpcSimple extends Quest
{
    private static final int NPC_ID = 60237;

    public NpcSimple()
    {
        super(-1, "custom");
        System.out.println("DEBUG: TvTRegistrationNpc script loaded!");
        addFirstTalkId(NPC_ID);
        addTalkId(NPC_ID);
    }

    @Override
    public String onFirstTalk(Npc npc, Player player)
    {
        System.out.println("DEBUG: onFirstTalk called for player: " + player.getName());
        if (TvTManager.getInstance().getState() == TvTState.REGISTRATION)
        {
            sendNpcHtmlWithCount(npc, player);
            return null;
        }
        else if (player.isInTvT())
        {
            player.sendMessage("TvT: You are already in a running event.");
        }
        else
        {
            player.sendMessage("TvT: The event is not in registration phase.");
        }
        return null;
    }

    @Override
    public String onTalk(Npc npc, Player player)
    {
        System.out.println("DEBUG: onTalk called for player: " + player.getName());
        if (TvTManager.getInstance().getState() == TvTState.REGISTRATION)
        {
            sendNpcHtmlWithCount(npc, player);
            return null;
        }
        return null;
    }

    @Override
    public String onAdvEvent(String event, Npc npc, Player player)
    {
        System.out.println("DEBUG: onAdvEvent called with event: " + event + " by player: " + player.getName());
        if (event.equals("register"))
        {
            if (TvTManager.getInstance().tryRegister(player))
                player.sendMessage("TvT: Registered successfully.");
            else
                player.sendMessage("TvT: Failed to register. Check requirements.");
        }
        else if (event.equals("unregister"))
        {
            if (TvTManager.getInstance().tryUnregister(player))
                player.sendMessage("TvT: Unregistered successfully.");
            else
                player.sendMessage("TvT: You were not registered.");
        }
        // Ξαναστέλνουμε το html ώστε να ανανεώνεται το count.
        sendNpcHtmlWithCount(npc, player);
        return null;
    }

    private void sendNpcHtmlWithCount(Npc npc, Player player)
    {
        int count = TvTManager.getInstance().getRegisteredCount();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        sb.append("<img src=\"L2UI.SquareWhite\" width=\"270\" height=\"1\">");
        sb.append("<table bgcolor=000000 width=270><tr><td width=270 align=center>");
        sb.append("<font color=\"FFFFFF\">TvT Herald</font>");
        sb.append("</td></tr></table>");
        sb.append("<img src=\"L2UI.SquareWhite\" width=\"270\" height=\"1\"><br><br>");
        sb.append("<font color=\"FF0000\">Current Status:</font><br>");
        sb.append("The event is currently in registration phase.<br>");
        sb.append("To register for the event, press the button below.<br><br>");
        sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br>");
        sb.append("<button value=\"Register\" action=\"bypass -h Quest NpcSimple register\" width=\"200\" height=\"32\" back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
        sb.append("<button value=\"Unregister\" action=\"bypass -h Quest NpcSimple unregister\" width=\"200\" height=\"32\" back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
        sb.append("<br><br>");
        sb.append("<font color=\"LEVEL\">Registered players: ").append(count).append("</font>");
        sb.append("<br><img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br>");
        sb.append("</center></body></html>");

        NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        html.setHtml(sb.toString());
        player.sendPacket(html);
    }
}