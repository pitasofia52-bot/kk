package net.sf.l2j.gameserver.model.actor.instance;

import java.util.Calendar;
import net.sf.l2j.gameserver.cache.HtmCache;
import net.sf.l2j.gameserver.data.NpcTable;
import net.sf.l2j.gameserver.instancemanager.RaidBossSpawnManager;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class RaidBossStatusInstance extends Folk
{ 
	private static final int[] RBOSSES = {25293,25450,25050,25163,60228,60229,60230,60231,60232};
	private static int MBOSS = 25293;

	public RaidBossStatusInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void showChatWindow(Player player)
	 {
		generateFirstWindow(player);
	 }
	
	private void generateFirstWindow(Player activeChar)
	{
		final StringBuilder sb = new StringBuilder();
	
		for(int rboss : RBOSSES)
		{
			
			long delay = RaidBossSpawnManager.getInstance().getRespawntime(rboss);
			String name = NpcTable.getInstance().getTemplate(rboss).getName().toUpperCase();
			
			if (delay==0)
			{
				sb.append("<font color=\"b09979\">"+name +" IS ALIVE!</font><br1>");
			}
			else if (delay<0)
			{
				sb.append("<font color=\"FF0000\"> "+name +" IS DEAD.</font><br1>");
			}
			else
			{
				delay =  RaidBossSpawnManager.getInstance().getRespawntime(rboss) - Calendar.getInstance().getTimeInMillis();
				sb.append("<font color=\"b09979\">"+name+"</font> "+ConverTime(delay)+" <font color=\"b09979\">TO RESPAWN.</font><br1>");
			}
		}
		
		long m_delay = RaidBossSpawnManager.getInstance().getRespawntime(MBOSS);
		String m_name = NpcTable.getInstance().getTemplate(MBOSS).getName().toUpperCase();
		
		String mainBossInfo ="";
		
		if (m_delay==0)
		{
			mainBossInfo = "WE SHOULD HAVE ACTED<br1><font color=\"b09979\">"+m_name+" IS ALIVE!</font><br1>";
		}
		else if (m_delay<0)
		{
			mainBossInfo = "IT'S ALL OVER<br1><font color=\"FF0000\"> "+m_name+" IS DEAD.</font><br1>";
		}
		else
		{
			m_delay =  m_delay - Calendar.getInstance().getTimeInMillis();
			mainBossInfo = "<font color=\"b09979\">"+ConverTime(m_delay)+"</font><br1>UNTIL OBLIVION OPEN!";
		}
		
	    NpcHtmlMessage html = new NpcHtmlMessage(1);
	    html.setFile(getHtmlPath(getNpcId(), 0));
	    html.replace("%objectId%", getObjectId());
	    html.replace("%bosslist%", sb.toString());
	    html.replace("%mboss%", mainBossInfo);
	    activeChar.sendPacket(html);
	}
	
	private static String ConverTime(long mseconds)
	{ 
		long remainder = mseconds;
		
		long hours = (long)Math.ceil((mseconds/(60*60*1000)));
		remainder = mseconds - (hours*60*60*1000);
		
		long minutes = (long)Math.ceil((remainder / (60*1000)));
		remainder = remainder -(minutes *(60*1000));
 
		long seconds = (long)Math.ceil((remainder / 1000));
	 
		return hours+":"+minutes+":"+seconds;
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String filename;
		
		if (val == 0)
			filename = "data/html/mods/RaidBossStatus/" + npcId + ".htm";
		else
			filename = "data/html/mods/RaidBossStatus/" + npcId + "-" + val + ".htm";
		
		if (HtmCache.getInstance().isLoadable(filename))
			return filename;
		
		return "data/html/mods/RaidBossStatus/" + npcId + ".htm";
	}
}
