package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.manager.BufferManager;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.ai.CtrlIntention;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.ItemList;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.MoveToPawn;
import net.sf.l2j.gameserver.network.serverpackets.MyTargetSelected;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.ValidateLocation;

/**
 * @author Baggos
 */
public final class PlayerBuffer extends Npc
{
	public PlayerBuffer(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onAction(Player player)
	{
		if (this != player.getTarget())
		{
			player.setTarget(this);
			player.sendPacket(new MyTargetSelected(getObjectId(), 0));
			player.sendPacket(new ValidateLocation(this));
		}
		else
		{
			if (!canInteract(player))
				player.getAI().setIntention(CtrlIntention.INTERACT, this);
			else
			{
				// Rotate the player to face the instance
				player.sendPacket(new MoveToPawn(player, this, Npc.INTERACTION_DISTANCE));
				
				if (hasRandomAnimation())
					onRandomAnimation(Rnd.get(8));
				
				showMainWindow(player);
				
				// Send ActionFailed to the player in order to avoid he stucks
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	private void showMainWindow(Player activeChar)
	{		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/mods/buffer/index.htm");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%name%", activeChar.getName());
		html.replace("%buffcount%", "You have " + activeChar.getBuffCount() + "/" + activeChar.getMaxBuffCount() + " buffs.");
		
		activeChar.sendPacket(html);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (player.getPvpFlag() > 0 && Config.PRESTRICT_USE_BUFFER_ON_PVPFLAG)
		{
			player.sendMessage("You can't use buffer when you are pvp flagged.");
			return;
		}
		
		if (player.isInCombat() && Config.PRESTRICT_USE_BUFFER_IN_COMBAT)
		{
			player.sendMessage("You can't use buffer when you are in combat.");
			return;
		}
		
		if (player.isDead())
			return;
		
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken();
		
		if (actualCommand.equalsIgnoreCase("bufflist"))
		{
			autoBuffFunction(player, st.nextToken());
		}
		else if (actualCommand.equalsIgnoreCase("restore"))
		{
			String noble = st.nextToken();
			player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
			player.setCurrentCp(player.getMaxCp());
			
			if (noble.equals("true"))
			{
				SkillTable.getInstance().getInfo(1323, 1).getEffects(player, player);
				player.broadcastPacket(new MagicSkillUse(this, player, 1323, 1, 850, 0));
			}
			
			final Summon summon = player.getPet();
			if (summon != null)
				summon.setCurrentHpMp(summon.getMaxHp(), summon.getMaxMp());
			
			showMainWindow(player);
		}
		else if (actualCommand.equalsIgnoreCase("cancellation"))
		{
			L2Skill buff;
			buff = SkillTable.getInstance().getInfo(1056, 1);
			buff.getEffects(this, player);
			player.stopAllEffectsExceptThoseThatLastThroughDeath();
			player.broadcastPacket(new MagicSkillUse(this, player, 1056, 1, 850, 0));
			player.stopAllEffects();
			
			final Summon summon = player.getPet();
			if (summon != null)
				summon.stopAllEffects();
			
			showMainWindow(player);
		}
		else if (actualCommand.equalsIgnoreCase("openlist"))
		{
			String category = st.nextToken();
			String htmfile = st.nextToken();
			
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			
			if (category.equalsIgnoreCase("null"))
			{
				html.setFile("data/html/mods/buffer/" + htmfile + ".htm");
				
				// First Page
				if (htmfile.equals("index"))
				{
					html.replace("%name%", player.getName());
					html.replace("%buffcount%", "You have " + player.getBuffCount() + "/" + player.getMaxBuffCount() + " buffs.");
				}
			}
			else
				html.setFile("data/html/mods/buffer/" + category + "/" + htmfile + ".htm");
			
			html.replace("%objectId%", String.valueOf(getObjectId()));
			player.sendPacket(html);
		}
		
		else if (actualCommand.equalsIgnoreCase("dobuff"))
		{
			int buffid = Integer.valueOf(st.nextToken());
			int bufflevel = Integer.valueOf(st.nextToken());
			String category = st.nextToken();
			String windowhtml = st.nextToken();
			String votebuff = null;
			
			if (st.hasMoreTokens())
				votebuff = st.nextToken();
			
			if (windowhtml.equals("malaria"))
			{
				if (player.getInventory().getInventoryItemCount(Config.PVOTE_BUFF_ITEM_ID, 0) >= 1)
				{
					player.getInventory().destroyItemByItemId("VoteCoins", Config.PVOTE_BUFF_ITEM_ID, 1, player, null);
					player.getInventory().updateDatabase();
					player.sendPacket(new ItemList(player, true));
					player.sendMessage(1 + " Vote eye destroyed.");
				}
				else
				{
					player.sendMessage("You dont have enough (" + 1 + ") vote item for buff.");
					return;
				}
			}
			
			if (votebuff != null)
			{
				if (player.getInventory().getInventoryItemCount(Config.PVOTE_BUFF_ITEM_ID, 0) >= Config.PVOTE_BUFF_ITEM_COUNT)
				{
					player.getInventory().destroyItemByItemId("VoteCoins", Config.PVOTE_BUFF_ITEM_ID, Config.PVOTE_BUFF_ITEM_COUNT, player, null);
					player.getInventory().updateDatabase();
					player.sendPacket(new ItemList(player, true));
					player.sendMessage(Config.PVOTE_BUFF_ITEM_COUNT + " vote stone destroyed.");
				}
				else
				{
					player.sendMessage("You dont have enough (" + Config.PVOTE_BUFF_ITEM_COUNT + ") vote item for buff.");
					return;
				}
			}
			
			Creature target = player;
			if (category.equalsIgnoreCase("pet"))
			{
				if (player.getPet() == null)
				{
					player.sendMessage("Incorrect Pet");
					showMainWindow(player);
					return;
				}
				target = player.getPet();
			}
			
			MagicSkillUse mgc = new MagicSkillUse(this, target, buffid, bufflevel, 1150, 0);
			player.sendPacket(mgc);
			player.broadcastPacket(mgc);
			SkillTable.getInstance().getInfo(buffid, bufflevel).getEffects(this, target);
			
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile("data/html/mods/buffer/" + category + "/" + windowhtml + ".htm");
			html.replace("%objectId%", String.valueOf(getObjectId()));
			html.replace("%name%", player.getName());
			player.sendPacket(html);
		}
		else if (actualCommand.equalsIgnoreCase("getbuff"))
		{
			int buffid = Integer.valueOf(st.nextToken());
			int bufflevel = Integer.valueOf(st.nextToken());
			if (buffid != 0)
			{
				SkillTable.getInstance().getInfo(buffid, bufflevel).getEffects(this, player);
				broadcastPacket(new MagicSkillUse(this, player, buffid, bufflevel, 450, 0));
				showMainWindow(player);
			}
		}
		else if (actualCommand.startsWith("support"))
		{
			showGiveBuffsWindow(player, st.nextToken());
		}
		else if (actualCommand.startsWith("givebuffs"))
		{
			final String targetType = st.nextToken();
			final String schemeName = st.nextToken();
			final int cost = Integer.parseInt(st.nextToken());
			
			final Creature target = (targetType.equalsIgnoreCase("pet")) ? player.getPet() : player;
			if (target == null)
				player.sendMessage("You don't have a pet.");
			else if (cost == 0 || player.reduceAdena("NPC Buffer", cost, this, true))
			{
				for (int skillId : BufferManager.getInstance().getScheme(player.getObjectId(), schemeName))
					SkillTable.getInstance().getInfo(skillId, SkillTable.getInstance().getMaxLevel(skillId)).getEffects(this, target);
			}
			showGiveBuffsWindow(player, targetType);
		}
		else if (actualCommand.startsWith("editschemes"))
		{
			if (st.countTokens() == 2)
				showEditSchemeWindow(player, st.nextToken(), st.nextToken());
			else
				player.sendMessage("Something wrong with your scheme. Please contact with Admin");
		}
		else if (actualCommand.startsWith("skill"))
		{
			final String groupType = st.nextToken();
			final String schemeName = st.nextToken();
			
			final int skillId = Integer.parseInt(st.nextToken());
			
			final List<Integer> skills = BufferManager.getInstance().getScheme(player.getObjectId(), schemeName);
			
			if (actualCommand.startsWith("skillselect") && !schemeName.equalsIgnoreCase("none"))
			{
				if (skills.size() < Config.PBUFFER_MAX_SKILLS)
					skills.add(skillId);
				else
					player.sendMessage("This scheme has reached the maximum amount of buffs.");
			}
			else if (actualCommand.startsWith("skillunselect"))
				skills.remove(Integer.valueOf(skillId));
			
			showEditSchemeWindow(player, groupType, schemeName);
		}
		else if (actualCommand.startsWith("manageschemes"))
		{
			showManageSchemeWindow(player);
		}
		else if (actualCommand.startsWith("createscheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				if (schemeName.length() > 14)
				{
					player.sendMessage("Scheme's name must contain up to 14 chars. Spaces are trimmed.");
					showManageSchemeWindow(player);
					return;
				}
				
				final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
				if (schemes != null)
				{
					if (schemes.size() == Config.PBUFFER_MAX_SCHEMES)
					{
						player.sendMessage("Maximum schemes amount is already reached.");
						showManageSchemeWindow(player);
						return;
					}
					
					if (schemes.containsKey(schemeName))
					{
						player.sendMessage("The scheme name already exists.");
						showManageSchemeWindow(player);
						return;
					}
				}
				
				BufferManager.getInstance().setScheme(player.getObjectId(), schemeName.trim(), new ArrayList<Integer>());
				showManageSchemeWindow(player);
			}
			catch (Exception e)
			{
				player.sendMessage("Scheme's name must contain up to 14 chars. Spaces are trimmed.");
				showManageSchemeWindow(player);
			}
		}
		else if (actualCommand.startsWith("deletescheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
				
				if (schemes != null && schemes.containsKey(schemeName))
					schemes.remove(schemeName);
			}
			catch (Exception e)
			{
				player.sendMessage("This scheme name is invalid.");
			}
			showManageSchemeWindow(player);
		}
		else if (actualCommand.startsWith("clearscheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
				
				if (schemes != null && schemes.containsKey(schemeName))
					schemes.get(schemeName).clear();
			}
			catch (Exception e)
			{
				player.sendMessage("This scheme name is invalid.");
			}
			showManageSchemeWindow(player);
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	/**
	 * Sends an html packet to player with Give Buffs menu info for player and pet, depending on targetType parameter {player, pet}
	 * @param player : The player to make checks on.
	 * @param targetType : a String used to define if the player or his pet must be used as target.
	 */
	private void showGiveBuffsWindow(Player player, String targetType)
	{
		final StringBuilder sb = new StringBuilder(200);
		
		final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
		if (schemes == null || schemes.isEmpty())
			sb.append("<font color=\"LEVEL\">You haven't defined any scheme, please go to 'Manage my schemes' and create at least one valid scheme.</font>");
		else
		{
			for (Map.Entry<String, ArrayList<Integer>> scheme : schemes.entrySet())
			{
				final int cost = getFee(scheme.getValue());
				StringUtil.append(sb, "<font color=\"LEVEL\"><a action=\"bypass -h npc_%objectId%_givebuffs ", targetType, " ", scheme.getKey(), " ", cost, "\">", scheme.getKey(), " (", scheme.getValue().size(), " skill(s))</a>", ((cost > 0) ? " - Adena cost: " + cost : ""), "</font><br1>");
			}
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/buffer/schememanager/index-1.htm");
		html.replace("%schemes%", sb.toString());
		html.replace("%targettype%", (targetType.equalsIgnoreCase("pet") ? "&nbsp;<a action=\"bypass -h npc_%objectId%_support player\">yourself</a>&nbsp;|&nbsp;your pet" : "yourself&nbsp;|&nbsp;<a action=\"bypass -h npc_%objectId%_support pet\">your pet</a>"));
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
	
	/**
	 * Sends an html packet to player with Manage scheme menu info. This allows player to create/delete/clear schemes
	 * @param player : The player to make checks on.
	 */
	private void showManageSchemeWindow(Player player)
	{
		final StringBuilder sb = new StringBuilder(200);
		
		final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
		if (schemes == null || schemes.isEmpty())
			sb.append("<font color=\"LEVEL\">You haven't created any scheme.</font>");
		else
		{
			sb.append("<table>");
			for (Map.Entry<String, ArrayList<Integer>> scheme : schemes.entrySet())
				StringUtil.append(sb, "<tr><td width=140>", scheme.getKey(), " (", scheme.getValue().size(), " skill(s))</td><td width=60><button value=\"Clear\" action=\"bypass -h npc_%objectId%_clearscheme ", scheme.getKey(), "\" width=55 height=15 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td><td width=60><button value=\"Drop\" action=\"bypass -h npc_%objectId%_deletescheme ", scheme.getKey(), "\" width=55 height=15 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td></tr>");
			
			sb.append("</table>");
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/buffer/schememanager/index-2.htm");
		html.replace("%schemes%", sb.toString());
		html.replace("%max_schemes%", Config.PBUFFER_MAX_SCHEMES);
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
	
	/**
	 * This sends an html packet to player with Edit Scheme Menu info. This allows player to edit each created scheme (add/delete skills)
	 * @param player : The player to make checks on.
	 * @param groupType : The group of skills to select.
	 * @param schemeName : The scheme to make check.
	 */
	private void showEditSchemeWindow(Player player, String groupType, String schemeName)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		
		if (schemeName.equalsIgnoreCase("none"))
			html.setFile("data/html/mods/buffer/schememanager/index-3.htm");
		else
		{
			if (groupType.equalsIgnoreCase("none"))
				html.setFile("data/html/mods/buffer/schememanager/index-4.htm");
			else
			{
				html.setFile("data/html/mods/buffer/schememanager/index-5.htm");
				html.replace("%skilllistframe%", getGroupSkillList(player, groupType, schemeName));
			}
			html.replace("%schemename%", schemeName);
			html.replace("%myschemeframe%", getPlayerSchemeSkillList(player, groupType, schemeName));
			html.replace("%typesframe%", getTypesFrame(groupType, schemeName));
		}
		html.replace("%schemes%", getPlayerSchemes(player, schemeName));
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
	
	/**
	 * @param player : The player to make checks on.
	 * @param schemeName : The name to don't link (previously clicked).
	 * @return a String listing player's schemes. The scheme currently on selection isn't linkable.
	 */
	private static String getPlayerSchemes(Player player, String schemeName)
	{
		final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
		if (schemes == null || schemes.isEmpty())
			return "Please create at least one scheme.";
		
		final StringBuilder sb = new StringBuilder(200);
		sb.append("<table>");
		
		for (Map.Entry<String, ArrayList<Integer>> scheme : schemes.entrySet())
		{
			if (schemeName.equalsIgnoreCase(scheme.getKey()))
				StringUtil.append(sb, "<tr><td width=200>", scheme.getKey(), " (<font color=\"LEVEL\">", scheme.getValue().size(), "</font> / ", Config.PBUFFER_MAX_SKILLS, " skill(s))</td></tr>");
			else
				StringUtil.append(sb, "<tr><td width=200><a action=\"bypass -h npc_%objectId%_editschemes none ", scheme.getKey(), "\">", scheme.getKey(), " (", scheme.getValue().size(), " / ", Config.PBUFFER_MAX_SKILLS, " skill(s))</a></td></tr>");
		}
		
		sb.append("</table>");
		
		return sb.toString();
	}
	
	/**
	 * @param player : The player to make checks on.
	 * @param groupType : The group of skills to select.
	 * @param schemeName : The scheme to make check.
	 * @return a String representing skills available to selection for a given groupType.
	 */
	private static String getGroupSkillList(Player player, String groupType, String schemeName)
	{
		final List<Integer> skills = new ArrayList<>();
		for (int skillId : BufferManager.getInstance().getSkillsIdsByType(groupType))
		{
			if (BufferManager.getInstance().getSchemeContainsSkill(player.getObjectId(), schemeName, skillId))
				continue;
			
			skills.add(skillId);
		}
		
		if (skills.isEmpty())
			return "That group doesn't contain any skills.";
		
		final StringBuilder sb = new StringBuilder(500);
		
		sb.append("<table>");
		int count = 0;
		for (int skillId : skills)
		{
			if (BufferManager.getInstance().getSchemeContainsSkill(player.getObjectId(), schemeName, skillId))
				continue;
			
			if (count == 0)
				sb.append("<tr>");
			
			if (skillId < 100)
				sb.append("<td width=180><font color=\"949490\"><a action=\"bypass -h npc_%objectId%_skillselect " + groupType + " " + schemeName + " " + skillId + "\">" + SkillTable.getInstance().getInfo(skillId, 1).getName() + "</a></font></td>");
			else if (skillId < 1000)
				sb.append("<td width=180><font color=\"949490\"><a action=\"bypass -h npc_%objectId%_skillselect " + groupType + " " + schemeName + " " + skillId + "\">" + SkillTable.getInstance().getInfo(skillId, 1).getName() + "</a></font></td>");
			else
				sb.append("<td width=180><font color=\"949490\"><a action=\"bypass -h npc_%objectId%_skillselect " + groupType + " " + schemeName + " " + skillId + "\">" + SkillTable.getInstance().getInfo(skillId, 1).getName() + "</a></font></td>");
			
			count++;
			if (count == 2)
			{
				sb.append("</tr><tr><td></td></tr>");
				count = 0;
			}
		}
		
		if (!sb.toString().endsWith("</tr>"))
			sb.append("</tr>");
		
		sb.append("</table>");
		
		return sb.toString();
	}
	
	/**
	 * @param player : The player to make checks on.
	 * @param groupType : The group of skills to select.
	 * @param schemeName : The scheme to make check.
	 * @return a String representing a given scheme's content.
	 */
	private static String getPlayerSchemeSkillList(Player player, String groupType, String schemeName)
	{
		final List<Integer> skills = BufferManager.getInstance().getScheme(player.getObjectId(), schemeName);
		if (skills.isEmpty())
			return "That scheme is empty.";
		
		final StringBuilder sb = new StringBuilder(500);
		sb.append("<table>");
		int count = 0;
		
		for (int sk : skills)
		{
			if (count == 0)
				sb.append("<tr>");
			
			if (sk < 100)
				sb.append("<td width=180><font color=\"6e6e6a\"><a action=\"bypass -h npc_%objectId%_skillunselect " + groupType + " " + schemeName + " " + sk + "\">" + SkillTable.getInstance().getInfo(sk, 1).getName() + "</a></font></td>");
			else if (sk < 1000)
				sb.append("<td width=180><font color=\"6e6e6a\"><a action=\"bypass -h npc_%objectId%_skillunselect " + groupType + " " + schemeName + " " + sk + "\">" + SkillTable.getInstance().getInfo(sk, 1).getName() + "</a></font></td>");
			else
				sb.append("<td width=180><font color=\"6e6e6a\"><a action=\"bypass -h npc_%objectId%_skillunselect " + groupType + " " + schemeName + " " + sk + "\">" + SkillTable.getInstance().getInfo(sk, 1).getName() + "</a></font></td>");
			
			count++;
			if (count == 2)
			{
				sb.append("</tr><tr><td></td></tr>");
				count = 0;
			}
		}
		
		if (!sb.toString().endsWith("<tr>"))
			sb.append("<tr>");
		
		sb.append("</table>");
		
		return sb.toString();
	}
	
	/**
	 * @param groupType : The group of skills to select.
	 * @param schemeName : The scheme to make check.
	 * @return a string representing all groupTypes availables. The group currently on selection isn't linkable.
	 */
	private static String getTypesFrame(String groupType, String schemeName)
	{
		final StringBuilder sb = new StringBuilder(500);
		sb.append("<table>");
		
		int count = 0;
		for (String s : BufferManager.getInstance().getSkillTypes())
		{
			if (count == 0)
				sb.append("<tr>");
			
			if (groupType.equalsIgnoreCase(s))
				StringUtil.append(sb, "<td width=65>", s, "</td>");
			else
				StringUtil.append(sb, "<td width=65><a action=\"bypass -h npc_%objectId%_editschemes ", s, " ", schemeName, "\">", s, "</a></td>");
			
			count++;
			if (count == 4)
			{
				sb.append("</tr>");
				count = 0;
			}
		}
		
		if (!sb.toString().endsWith("</tr>"))
			sb.append("</tr>");
		
		sb.append("</table>");
		
		return sb.toString();
	}
	
	/**
	 * @param list : A list of skill ids.
	 * @return a global fee for all skills contained in list.
	 */
	private static int getFee(ArrayList<Integer> list)
	{
		if (Config.PBUFFER_STATIC_BUFF_COST >= 0)
			return (list.size() * Config.PBUFFER_STATIC_BUFF_COST);
		
		int fee = 0;
		for (int sk : list)
		{
			if (Config.PBUFFER_BUFFLIST.get(sk) == null)
				continue;
			
			fee += Config.PBUFFER_BUFFLIST.get(sk).getValue();
		}
		
		return fee;
	}
	
	private void autoBuffFunction(Player player, String bufflist)
	{
		ArrayList<L2Skill> skills_to_buff = new ArrayList<>();
		List<Integer> list = null;
		
		if (bufflist.equalsIgnoreCase("fighter"))
			list = Config.PFIGHTER_SKILL_LIST;
		else if (bufflist.equalsIgnoreCase("mage"))
			list = Config.PMAGE_SKILL_LIST;
		
		if (list != null)
		{
			for (int skillId : list)
			{
				L2Skill skill = SkillTable.getInstance().getInfo(skillId, SkillTable.getInstance().getMaxLevel(skillId));
				if (skill != null)
					skills_to_buff.add(skill);
			}
			
			for (L2Skill sk : skills_to_buff)
				sk.getEffects(player, player);
			
			player.updateEffectIcons();
			
			list = null;
		}
		
		skills_to_buff.clear();
		
		showMainWindow(player);
	}
}
