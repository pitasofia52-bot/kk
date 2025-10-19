package net.sf.l2j.gameserver.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.olympiad.OlympiadGameManager;
import net.sf.l2j.gameserver.model.olympiad.OlympiadGameTask;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.AbnormalStatusUpdate;
import net.sf.l2j.gameserver.network.serverpackets.ExOlympiadSpelledInfo;
import net.sf.l2j.gameserver.network.serverpackets.PartySpelled;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.templates.skills.L2EffectFlag;
import net.sf.l2j.gameserver.templates.skills.L2EffectType;
import net.sf.l2j.gameserver.templates.skills.L2SkillType;

public class CharEffectList
{
	private static final L2Effect[] EMPTY_EFFECTS = new L2Effect[0];
	
	private List<L2Effect> _buffs;
	private List<L2Effect> _debuffs;
	
	// The table containing the List of all stacked effect in progress for each Stack group Identifier
	private Map<String, List<L2Effect>> _stackedEffects;
	
	private volatile boolean _hasBuffsRemovedOnAnyAction = false;
	private volatile boolean _hasBuffsRemovedOnDamage = false;
	private volatile boolean _hasDebuffsRemovedOnDamage = false;
	
	private boolean _queuesInitialized = false;
	private LinkedBlockingQueue<L2Effect> _addQueue;
	private LinkedBlockingQueue<L2Effect> _removeQueue;
	private final AtomicBoolean queueLock = new AtomicBoolean();
	private int _effectFlags;
	
	// only party icons need to be updated
	private boolean _partyOnly = false;
	
	// Owner of this list
	private final Creature _owner;
	
	private L2Effect[] _effectCache;
	private volatile boolean _rebuildCache = true;
	private final Object _buildEffectLock = new Object();
	
	public CharEffectList(Creature owner)
	{
		_owner = owner;
	}
	
	/**
	 * Helper : detect fake player bot instances without importing every custom class.
	 * Pattern: all bot classes end with "FakePlayer".
	 */
	private boolean isOwnerFakePlayerBot()
	{
		if (!(_owner instanceof Player))
			return false;
		String name = _owner.getClass().getSimpleName();
		// Real Player is "Player", so only treat subclasses that end with FakePlayer
		return name.endsWith("FakePlayer");
	}
	
	/**
	 * Returns all effects affecting stored in this CharEffectList
	 * @return
	 */
	public final L2Effect[] getAllEffects()
	{
		// If no effect is active, return EMPTY_EFFECTS
		if ((_buffs == null || _buffs.isEmpty()) && (_debuffs == null || _debuffs.isEmpty()))
			return EMPTY_EFFECTS;
		
		synchronized (_buildEffectLock)
		{
			// If we dont need to rebuild the cache, just return the current one.
			if (!_rebuildCache)
				return _effectCache;
			
			_rebuildCache = false;
			
			// Create a copy of the effects
			List<L2Effect> temp = new ArrayList<>();
			
			// Add all buffs and all debuffs
			if (_buffs != null && !_buffs.isEmpty())
				temp.addAll(_buffs);
			if (_debuffs != null && !_debuffs.isEmpty())
				temp.addAll(_debuffs);
			
			// Return all effects in an array
			L2Effect[] tempArray = new L2Effect[temp.size()];
			temp.toArray(tempArray);
			return (_effectCache = tempArray);
		}
	}
	
	/**
	 * Returns the first effect matching the given EffectType
	 * @param tp
	 * @return
	 */
	public final L2Effect getFirstEffect(L2EffectType tp)
	{
		L2Effect effectNotInUse = null;
		
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e == null)
					continue;
				
				if (e.getEffectType() == tp)
				{
					if (e.getInUse())
						return e;
					
					effectNotInUse = e;
				}
			}
		}
		
		if (effectNotInUse == null && _debuffs != null && !_debuffs.isEmpty())
		{
			for (L2Effect e : _debuffs)
			{
				if (e == null)
					continue;
				
				if (e.getEffectType() == tp)
				{
					if (e.getInUse())
						return e;
					
					effectNotInUse = e;
				}
			}
		}
		return effectNotInUse;
	}
	
	/**
	 * Returns the first effect matching the given L2Skill
	 * @param skill
	 * @return
	 */
	public final L2Effect getFirstEffect(L2Skill skill)
	{
		L2Effect effectNotInUse = null;
		
		if (skill.isDebuff())
		{
			if (_debuffs != null && !_debuffs.isEmpty())
			{
				for (L2Effect e : _debuffs)
				{
					if (e == null)
						continue;
					
					if (e.getSkill() == skill)
					{
						if (e.getInUse())
							return e;
						
						effectNotInUse = e;
					}
				}
			}
		}
		else
		{
			if (_buffs != null && !_buffs.isEmpty())
			{
				for (L2Effect e : _buffs)
				{
					if (e == null)
						continue;
					
					if (e.getSkill() == skill)
					{
						if (e.getInUse())
							return e;
						
						effectNotInUse = e;
					}
				}
			}
		}
		return effectNotInUse;
	}
	
	/**
	 * @param skillId The skill id to check.
	 * @return the first effect matching the given skillId.
	 */
	public final L2Effect getFirstEffect(int skillId)
	{
		L2Effect effectNotInUse = null;
		
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e == null)
					continue;
				
				if (e.getSkill().getId() == skillId)
				{
					if (e.getInUse())
						return e;
					
					effectNotInUse = e;
				}
			}
		}
		
		if (effectNotInUse == null && _debuffs != null && !_debuffs.isEmpty())
		{
			for (L2Effect e : _debuffs)
			{
				if (e == null)
					continue;
				if (e.getSkill().getId() == skillId)
				{
					if (e.getInUse())
						return e;
					
					effectNotInUse = e;
				}
			}
		}
		return effectNotInUse;
	}
	
	/**
	 * Checks if the given skill stacks with an existing one.
	 * @param checkSkill the skill to be checked
	 * @return Returns whether or not this skill will stack
	 */
	private boolean doesStack(L2Skill checkSkill)
	{
		if (_buffs == null || _buffs.isEmpty())
			return false;
		
		if (checkSkill._effectTemplates == null || checkSkill._effectTemplates.isEmpty())
			return false;
		
		final String stackType = checkSkill._effectTemplates.get(0).stackType;
		if (stackType == null || "none".equals(stackType))
			return false;
		
		for (L2Effect e : _buffs)
		{
			if (e.getStackType() != null && e.getStackType().equals(stackType))
				return true;
		}
		return false;
	}
	
	/**
	 * Return the number of buffs in this CharEffectList not counting Songs/Dances
	 * @return
	 */
	public int getBuffCount()
	{
		if (_buffs == null || _buffs.isEmpty())
			return 0;
		
		int buffCount = 0;
		for (L2Effect e : _buffs)
		{
			if (e != null && e.getShowIcon() && !e.getSkill().is7Signs())
			{
				switch (e.getSkill().getSkillType())
				{
					case BUFF:
					case COMBATPOINTHEAL:
					case REFLECT:
					case HEAL_PERCENT:
					case MANAHEAL_PERCENT:
						buffCount++;
				}
			}
		}
		return buffCount;
	}
	
	/**
	 * Return the number of Songs/Dances in this CharEffectList
	 * @return
	 */
	public int getDanceCount()
	{
		if (_buffs == null || _buffs.isEmpty())
			return 0;
		
		int danceCount = 0;
		for (L2Effect e : _buffs)
		{
			if (e != null && e.getSkill().isDance() && e.getInUse())
				danceCount++;
		}
		return danceCount;
	}
	
	/**
	 * Exits all effects in this CharEffectList
	 */
	public final void stopAllEffects()
	{
		L2Effect[] effects = getAllEffects();
		for (L2Effect e : effects)
		{
			if (e != null)
				e.exit(true);
		}
	}
	
	/**
	 * Exits all effects except those that last through death. For FakePlayer bots we preserve positive non-toggle buffs.
	 */
	public final void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		final boolean keepPositivesForBots = isOwnerFakePlayerBot();
		
		L2Effect[] effects = getAllEffects();
		for (L2Effect e : effects)
		{
			if (e == null)
				continue;
			
			// Skip effects that are flagged to stay after death (normal behaviour)
			if (e.getSkill().isStayAfterDeath())
				continue;
			
			// For fake player bots: keep positive (non-debuff) non-toggle buffs.
			if (keepPositivesForBots && !e.getSkill().isDebuff() && !e.getSkill().isToggle())
				continue;
			
			e.exit(true);
		}
	}
	
	/**
	 * Exit all toggle-type effects
	 */
	public void stopAllToggles()
	{
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e != null && e.getSkill().isToggle())
					e.exit();
			}
		}
	}
	
	/**
	 * Exit all effects having a specified type
	 * @param type
	 */
	public final void stopEffects(L2EffectType type)
	{
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e != null && e.getEffectType() == type)
					e.exit();
			}
		}
		
		if (_debuffs != null && !_debuffs.isEmpty())
		{
			for (L2Effect e : _debuffs)
			{
				if (e != null && e.getEffectType() == type)
					e.exit();
			}
		}
	}
	
	/**
	 * Exits all effects created by a specific skillId
	 * @param skillId
	 */
	public final void stopSkillEffects(int skillId)
	{
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e != null && e.getSkill().getId() == skillId)
					e.exit();
			}
		}
		
		if (_debuffs != null && !_debuffs.isEmpty())
		{
			for (L2Effect e : _debuffs)
			{
				if (e != null && e.getSkill().getId() == skillId)
					e.exit();
			}
		}
	}
	
	/**
	 * Exits all effects created by a specific skill type
	 * @param skillType skill type
	 * @param negateLvl
	 */
	public final void stopSkillEffects(L2SkillType skillType, int negateLvl)
	{
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e != null && (e.getSkill().getSkillType() == skillType || (e.getSkill().getEffectType() != null && e.getSkill().getEffectType() == skillType)) && (negateLvl == -1 || (e.getSkill().getEffectType() != null && e.getSkill().getEffectAbnormalLvl() >= 0 && e.getSkill().getEffectAbnormalLvl() <= negateLvl) || (e.getSkill().getAbnormalLvl() >= 0 && e.getSkill().getAbnormalLvl() <= negateLvl)))
					e.exit();
			}
		}
		
		if (_debuffs != null && !_debuffs.isEmpty())
		{
			for (L2Effect e : _debuffs)
			{
				if (e != null && (e.getSkill().getSkillType() == skillType || (e.getSkill().getEffectType() != null && e.getSkill().getEffectType() == skillType)) && (negateLvl == -1 || (e.getSkill().getEffectType() != null && e.getSkill().getEffectAbnormalLvl() >= 0 && e.getSkill().getEffectAbnormalLvl() <= negateLvl) || (e.getSkill().getAbnormalLvl() >= 0 && e.getSkill().getAbnormalLvl() <= negateLvl)))
					e.exit();
			}
		}
	}
	
	/**
	 * Exits all buffs effects of the skills with "removedOnAnyAction" set. Called on any action except movement (attack, cast).
	 */
	public void stopEffectsOnAction()
	{
		if (_hasBuffsRemovedOnAnyAction)
		{
			if (_buffs != null && !_buffs.isEmpty())
			{
				for (L2Effect e : _buffs)
				{
					if (e != null && e.getSkill().isRemovedOnAnyActionExceptMove())
						e.exit(true);
				}
			}
		}
	}
	
	public void stopEffectsOnDamage(boolean awake)
	{
		if (_hasBuffsRemovedOnDamage)
		{
			if (_buffs != null && !_buffs.isEmpty())
			{
				for (L2Effect e : _buffs)
				{
					if (e != null && e.getSkill().isRemovedOnDamage() && (awake || e.getSkill().getSkillType() != L2SkillType.SLEEP))
						e.exit(true);
				}
			}
		}
		
		if (_hasDebuffsRemovedOnDamage)
		{
			if (_debuffs != null && !_debuffs.isEmpty())
			{
				for (L2Effect e : _debuffs)
				{
					if (e != null && e.getSkill().isRemovedOnDamage() && (awake || e.getSkill().getSkillType() != L2SkillType.SLEEP))
						e.exit(true);
				}
			}
		}
	}
	
	public void updateEffectIcons(boolean partyOnly)
	{
		if (_buffs == null && _debuffs == null)
			return;
		
		if (partyOnly)
			_partyOnly = true;
		
		queueRunner();
	}
	
	public void queueEffect(L2Effect effect, boolean remove)
	{
		if (effect == null)
			return;
		
		if (!_queuesInitialized)
			init();
		
		if (remove)
			_removeQueue.offer(effect);
		else
			_addQueue.offer(effect);
		
		queueRunner();
	}
	
	private synchronized void init()
	{
		if (_queuesInitialized)
			return;
		
		_addQueue = new LinkedBlockingQueue<>();
		_removeQueue = new LinkedBlockingQueue<>();
		_queuesInitialized = true;
	}
	
	private void queueRunner()
	{
		if (!queueLock.compareAndSet(false, true))
			return;
		
		try
		{
			L2Effect effect;
			do
			{
				while ((effect = _removeQueue.poll()) != null)
				{
					removeEffectFromQueue(effect);
					_partyOnly = false;
				}
				
				if ((effect = _addQueue.poll()) != null)
				{
					addEffectFromQueue(effect);
					_partyOnly = false;
				}
			}
			while (!_addQueue.isEmpty() || !_removeQueue.isEmpty());
			
			computeEffectFlags();
			updateEffectIcons();
		}
		finally
		{
			queueLock.set(false);
		}
	}
	
	protected void removeEffectFromQueue(L2Effect effect)
	{
		if (effect == null)
			return;
		
		List<L2Effect> effectList;
		
		_rebuildCache = true;
		
		if (effect.getSkill().isDebuff())
		{
			if (_debuffs == null)
				return;
			
			effectList = _debuffs;
		}
		else
		{
			if (_buffs == null)
				return;
			
			effectList = _buffs;
		}
		
		if ("none".equals(effect.getStackType()))
		{
			_owner.removeStatsByOwner(effect);
		}
		else
		{
			if (_stackedEffects == null)
				return;
			
			List<L2Effect> stackQueue = _stackedEffects.get(effect.getStackType());
			
			if (stackQueue == null || stackQueue.isEmpty())
				return;
			
			int index = stackQueue.indexOf(effect);
			
			if (index >= 0)
			{
				stackQueue.remove(effect);
				if (index == 0)
				{
					_owner.removeStatsByOwner(effect);
					
					if (!stackQueue.isEmpty())
					{
						L2Effect newStackedEffect = listsContains(stackQueue.get(0));
						if (newStackedEffect != null)
						{
							if (newStackedEffect.setInUse(true))
								_owner.addStatFuncs(newStackedEffect.getStatFuncs());
						}
					}
				}
				
				if (stackQueue.isEmpty())
					_stackedEffects.remove(effect.getStackType());
				else
					_stackedEffects.put(effect.getStackType(), stackQueue);
			}
		}
		
		if (effectList.remove(effect) && _owner instanceof Player && effect.getShowIcon())
		{
			SystemMessage sm;
			if (effect.getSkill().isToggle())
				sm = SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_BEEN_ABORTED);
			else
				sm = SystemMessage.getSystemMessage(SystemMessageId.EFFECT_S1_DISAPPEARED);
			
			sm.addSkillName(effect);
			_owner.sendPacket(sm);
		}
	}
	
	protected void addEffectFromQueue(L2Effect newEffect)
	{
		if (newEffect == null)
			return;
		
		L2Skill newSkill = newEffect.getSkill();
		
		_rebuildCache = true;
		
		if (isAffected(newEffect.getEffectFlags()) && !newEffect.onSameEffect(null))
		{
			newEffect.stopEffectTask();
			return;
		}
		
		if (newSkill.isDebuff())
		{
			if (_debuffs == null)
				_debuffs = new CopyOnWriteArrayList<>();
			
			for (L2Effect e : _debuffs)
			{
				if (e != null && e.getSkill().getId() == newEffect.getSkill().getId() && e.getEffectType() == newEffect.getEffectType() && e.getStackOrder() == newEffect.getStackOrder() && e.getStackType().equals(newEffect.getStackType()))
				{
					newEffect.stopEffectTask();
					return;
				}
			}
			_debuffs.add(newEffect);
		}
		else
		{
			if (_buffs == null)
				_buffs = new CopyOnWriteArrayList<>();
			
			for (L2Effect e : _buffs)
			{
				if (e != null && e.getSkill().getId() == newEffect.getSkill().getId() && e.getEffectType() == newEffect.getEffectType() && e.getStackOrder() == newEffect.getStackOrder() && e.getStackType().equals(newEffect.getStackType()))
				{
					e.exit();
				}
			}
			
			if (newEffect.isHerbEffect() && getBuffCount() >= _owner.getMaxBuffCount())
			{
				newEffect.stopEffectTask();
				return;
			}
			
			if (!doesStack(newSkill) && !newSkill.is7Signs())
			{
				int effectsToRemove = getBuffCount() - _owner.getMaxBuffCount();
				if (effectsToRemove >= 0)
				{
					switch (newSkill.getSkillType())
					{
						case BUFF:
						case REFLECT:
						case HEAL_PERCENT:
						case MANAHEAL_PERCENT:
						case COMBATPOINTHEAL:
							for (L2Effect e : _buffs)
							{
								if (e == null)
									continue;
								
								switch (e.getSkill().getSkillType())
								{
									case BUFF:
									case REFLECT:
									case HEAL_PERCENT:
									case MANAHEAL_PERCENT:
									case COMBATPOINTHEAL:
										e.exit();
										effectsToRemove--;
										break;
									default:
										continue;
								}
								if (effectsToRemove < 0)
									break;
							}
					}
				}
			}
			
			if (newSkill.isToggle())
				_buffs.add(newEffect);
			else
			{
				int pos = 0;
				for (L2Effect e : _buffs)
				{
					if (e == null || e.getSkill().isToggle() || e.getSkill().is7Signs())
						continue;
					
					pos++;
				}
				_buffs.add(pos, newEffect);
			}
		}
		
		if ("none".equals(newEffect.getStackType()))
		{
			if (newEffect.setInUse(true))
				_owner.addStatFuncs(newEffect.getStatFuncs());
			
			return;
		}
		
		List<L2Effect> stackQueue;
		L2Effect effectToAdd = null;
		L2Effect effectToRemove = null;
		if (_stackedEffects == null)
			_stackedEffects = new HashMap<>();
		
		stackQueue = _stackedEffects.get(newEffect.getStackType());
		
		if (stackQueue != null)
		{
			int pos = 0;
			if (!stackQueue.isEmpty())
			{
				effectToRemove = listsContains(stackQueue.get(0));
				
				Iterator<L2Effect> queueIterator = stackQueue.iterator();
				
				while (queueIterator.hasNext())
				{
					if (newEffect.getStackOrder() < queueIterator.next().getStackOrder())
						pos++;
					else
						break;
				}
				stackQueue.add(pos, newEffect);
				
				if (Config.EFFECT_CANCELING && !newEffect.isHerbEffect() && stackQueue.size() > 1)
				{
					if (newSkill.isDebuff())
						_debuffs.remove(stackQueue.remove(1));
					else
						_buffs.remove(stackQueue.remove(1));
				}
			}
			else
				stackQueue.add(0, newEffect);
		}
		else
		{
			stackQueue = new ArrayList<>();
			stackQueue.add(0, newEffect);
		}
		
		_stackedEffects.put(newEffect.getStackType(), stackQueue);
		
		if (!stackQueue.isEmpty())
			effectToAdd = listsContains(stackQueue.get(0));
		
		if (effectToRemove != effectToAdd)
		{
			if (effectToRemove != null)
			{
				_owner.removeStatsByOwner(effectToRemove);
				effectToRemove.setInUse(false);
			}
			
			if (effectToAdd != null)
			{
				if (effectToAdd.setInUse(true))
					_owner.addStatFuncs(effectToAdd.getStatFuncs());
			}
		}
	}
	
	protected void updateEffectIcons()
	{
		if (_owner == null)
			return;
		
		if (!(_owner instanceof Playable))
		{
			updateEffectFlags();
			return;
		}
		
		AbnormalStatusUpdate mi = null;
		PartySpelled ps = null;
		ExOlympiadSpelledInfo os = null;
		
		if (_owner instanceof Player)
		{
			if (_partyOnly)
				_partyOnly = false;
			else
				mi = new AbnormalStatusUpdate();
			
			if (_owner.isInParty())
				ps = new PartySpelled(_owner);
			
			if (((Player) _owner).isInOlympiadMode() && ((Player) _owner).isOlympiadStart())
				os = new ExOlympiadSpelledInfo((Player) _owner);
		}
		else if (_owner instanceof Summon)
			ps = new PartySpelled(_owner);
		
		boolean foundRemovedOnAction = false;
		boolean foundRemovedOnDamage = false;
		
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e == null)
					continue;
				
				if (e.getSkill().isRemovedOnAnyActionExceptMove())
					foundRemovedOnAction = true;
				if (e.getSkill().isRemovedOnDamage())
					foundRemovedOnDamage = true;
				
				if (!e.getShowIcon())
					continue;
				
				switch (e.getEffectType())
				{
					case SIGNET_GROUND:
						continue;
				}
				
				if (e.getInUse())
				{
					if (mi != null)
						e.addIcon(mi);
					
					if (ps != null)
						e.addPartySpelledIcon(ps);
					
					if (os != null)
						e.addOlympiadSpelledIcon(os);
				}
			}
		}
		
		_hasBuffsRemovedOnAnyAction = foundRemovedOnAction;
		_hasBuffsRemovedOnDamage = foundRemovedOnDamage;
		foundRemovedOnDamage = false;
		
		if (_debuffs != null && !_debuffs.isEmpty())
		{
			for (L2Effect e : _debuffs)
			{
				if (e == null)
					continue;
				
				if (e.getSkill().isRemovedOnAnyActionExceptMove())
					foundRemovedOnAction = true;
				if (e.getSkill().isRemovedOnDamage())
					foundRemovedOnDamage = true;
				
				if (!e.getShowIcon())
					continue;
				
				switch (e.getEffectType())
				{
					case SIGNET_GROUND:
						continue;
				}
				
				if (e.getInUse())
				{
					if (mi != null)
						e.addIcon(mi);
					
					if (ps != null)
						e.addPartySpelledIcon(ps);
					
					if (os != null)
						e.addOlympiadSpelledIcon(os);
				}
			}
		}
		
		_hasDebuffsRemovedOnDamage = foundRemovedOnDamage;
		
		if (mi != null)
			_owner.sendPacket(mi);
		
		if (ps != null)
		{
			if (_owner instanceof Summon)
			{
				final Player summonOwner = ((Summon) _owner).getOwner();
				if (summonOwner != null)
				{
					final Party party = summonOwner.getParty();
					if (party != null)
						party.broadcastPacket(ps);
					else
						summonOwner.sendPacket(ps);
				}
			}
			else if (_owner instanceof Player && _owner.isInParty())
				_owner.getParty().broadcastPacket(ps);
		}
		
		if (os != null)
		{
			final OlympiadGameTask game = OlympiadGameManager.getInstance().getOlympiadTask(((Player) _owner).getOlympiadGameId());
			if (game != null && game.isBattleStarted())
				game.getZone().broadcastPacketToObservers(os);
		}
	}
	
	protected void updateEffectFlags()
	{
		boolean foundRemovedOnAction = false;
		boolean foundRemovedOnDamage = false;
		
		if (_buffs != null && !_buffs.isEmpty())
		{
			for (L2Effect e : _buffs)
			{
				if (e == null)
					continue;
				
				if (e.getSkill().isRemovedOnAnyActionExceptMove())
					foundRemovedOnAction = true;
				if (e.getSkill().isRemovedOnDamage())
					foundRemovedOnDamage = true;
			}
		}
		_hasBuffsRemovedOnAnyAction = foundRemovedOnAction;
		_hasBuffsRemovedOnDamage = foundRemovedOnDamage;
		foundRemovedOnDamage = false;
		
		if (_debuffs != null && !_debuffs.isEmpty())
		{
			for (L2Effect e : _debuffs)
			{
				if (e == null)
					continue;
				
				if (e.getSkill().isRemovedOnDamage())
					foundRemovedOnDamage = true;
			}
		}
		_hasDebuffsRemovedOnDamage = foundRemovedOnDamage;
	}
	
	private L2Effect listsContains(L2Effect effect)
	{
		if (_buffs != null && !_buffs.isEmpty() && _buffs.contains(effect))
			return effect;
		if (_debuffs != null && !_debuffs.isEmpty() && _debuffs.contains(effect))
			return effect;
		
		return null;
	}
	
	private final void computeEffectFlags()
	{
		int flags = 0;
		
		if (_buffs != null)
		{
			for (L2Effect e : _buffs)
			{
				if (e == null)
					continue;
				
				flags |= e.getEffectFlags();
			}
		}
		
		if (_debuffs != null)
		{
			for (L2Effect e : _debuffs)
			{
				if (e == null)
					continue;
				
				flags |= e.getEffectFlags();
			}
		}
		
		_effectFlags = flags;
	}
	
	public boolean isAffected(L2EffectFlag flag)
	{
		return isAffected(flag.getMask());
	}
	
	public boolean isAffected(int mask)
	{
		return (_effectFlags & mask) != 0;
	}
	
	public void clear()
	{
		_addQueue = null;
		_removeQueue = null;
		_buffs = null;
		_debuffs = null;
		_stackedEffects = null;
		_queuesInitialized = false;
	}
}