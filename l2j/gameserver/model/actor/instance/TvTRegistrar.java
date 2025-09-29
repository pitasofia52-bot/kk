package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import net.sf.l2j.gameserver.custom.tvt.TvTManager;
import net.sf.l2j.gameserver.custom.tvt.TvTConfig;
import net.sf.l2j.gameserver.custom.tvt.TvTPlayerData;

/**
 * TvT Registrar NPC (type="TvTRegistrar").
 * Αν ΔΕΝ υπάρχει κλάση Folk στο project σου, άλλαξε:
 *   public class TvTRegistrar extends Folk
 * σε
 *   public class TvTRegistrar extends Npc
 * και φρόντισε το import για Npc.
 */
public class TvTRegistrar extends Folk
{
	public TvTRegistrar(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (player == null)
			return;
		
		switch (command)
		{
			case "tvt_npc_join":
				TvTManager.getInstance().tryRegister(player);
				showChatWindow(player);
				break;
			case "tvt_npc_leave":
				TvTManager.getInstance().tryUnregister(player);
				showChatWindow(player);
				break;
			case "tvt_npc_status":
				player.sendMessage(TvTManager.getInstance().buildStatus(player));
				showChatWindow(player);
				break;
			default:
				super.onBypassFeedback(player, command);
		}
	}
	
	@Override
	public void showChatWindow(Player player)
	{
		if (player == null) return;
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/mods/tvt/60237.htm");
		
		String statusLine;
		switch (TvTManager.getInstance().getState())
		{
			case REGISTRATION:
				statusLine = "Registration: <font color=00FF00>OPEN</font> (" + TvTManager.getInstance().getRegisteredCount() + " players)";
				break;
			case RUNNING:
				statusLine = "Status: <font color=FF9900>RUNNING</font> (" + TvTManager.getInstance().getEventRemainingSeconds() + "s left)";
				break;
			default:
				statusLine = "Status: <font color=AAAAAA>Idle</font>";
		}
		
		String actionBlock;
		switch (TvTManager.getInstance().getState())
		{
			case REGISTRATION:
				actionBlock = TvTManager.getInstance().isRegistered(player)
					? "<button value=\"Leave\" action=\"bypass -h tvt_npc_leave\" width=80 height=21 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"/>"
					: "<button value=\"Join\" action=\"bypass -h tvt_npc_join\" width=80 height=21 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"/>";
				break;
			case RUNNING:
				if (player.isInTvT())
				{
					TvTPlayerData d = TvTManager.getInstance().getPlayerData(player);
					actionBlock = (d != null)
						? "You are in Team " + d.getTeam() + " (K:" + d.getKills() + " D:" + d.getDeaths() + ")"
						: "You participate.";
				}
				else
					actionBlock = "Event in progress.";
				break;
			default:
				actionBlock = "Waiting for next schedule...";
		}
		
		String schedules = TvTConfig.SCHEDULES.isEmpty() ? "-" : String.join(",", TvTConfig.SCHEDULES);
		
		html.replace("%STATUS_LINE%", statusLine);
		html.replace("%ACTION_BLOCK%", actionBlock);
		html.replace("%NEXT_SCHEDULES%", schedules);
		player.sendPacket(html);
	}
}