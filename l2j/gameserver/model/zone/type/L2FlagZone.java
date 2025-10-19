package net.sf.l2j.gameserver.model.zone.type;

import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.zone.L2ZoneType;

/**
 * Zone where restart is not allowed.<BR>
 * This zone will be rworked later (need to burn GrandBossManager), atm it's a simple zone.
 * @author Tryskell
 */
public class L2FlagZone extends L2ZoneType
{
	public L2FlagZone(final int id)
	{
		super(id);
	}
	
	@Override
	protected void onEnter(final Creature character)
	{
		if (character instanceof Player)		
		 character.setCurrentCp(character.getMaxCp());
		 ((Player) character).setPvpFlag(1);
		 ((Player) character).setTitle("Lets Fight");	
		 ((Player) character).broadcastUserInfo();	 
	}
	
	@Override
	protected void onExit(final Creature character)
	{
		if (character instanceof Player)
		 ((Player) character).setPvpFlag(0);
		 ((Player) character).setTitle("");	
		 ((Player) character).broadcastUserInfo();		 
	}
	
	@Override
	public void onDieInside(final Creature character)
	{
	}
	
	@Override
	public void onReviveInside(final Creature character)
	{
	}
}
