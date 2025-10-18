package net.sf.l2j.gameserver.handler.skillhandlers;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.model.L2Effect;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.ShotType;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.skills.Formulas;
import net.sf.l2j.gameserver.templates.skills.L2SkillType;

/**
 * Cancel / Mage Bane / Warrior Bane
 * Modification: Skip removal of positive non-toggle buffs from FakePlayer bot instances.
 * Detection uses class simpleName endsWith("FakePlayer") to avoid tight coupling.
 */
public class Cancel implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS =
	{
		L2SkillType.CANCEL,
		L2SkillType.MAGE_BANE,
		L2SkillType.WARRIOR_BANE
	};
	
	@Override
	public void useSkill(Creature activeChar, L2Skill skill, WorldObject[] targets)
	{
		final int minRate = (skill.getSkillType() == L2SkillType.CANCEL) ? 25 : 40;
		final int maxRate = (skill.getSkillType() == L2SkillType.CANCEL) ? 75 : 95;
		
		final double skillPower = skill.getPower();
		
		for (WorldObject obj : targets)
		{
			if (!(obj instanceof Creature))
				continue;
			
			final Creature target = (Creature) obj;
			if (target.isDead())
				continue;
			
			final boolean targetIsFakeBot = (target instanceof Player) && target.getClass().getSimpleName().endsWith("FakePlayer");
			
			int lastCanceledSkillId = 0;
			int count = skill.getMaxNegatedEffects();
			
			final int diffLevel = skill.getMagicLevel() - target.getLevel();
			final double skillVuln = Formulas.calcSkillVulnerability(activeChar, target, skill, skill.getSkillType());
			
			for (L2Effect effect : target.getAllEffects())
			{
				if (effect == null || effect.getSkill().isToggle())
					continue;
				
				// Protect positive (non debuff) non-toggle buffs for fake player bots.
				if (targetIsFakeBot && !effect.getSkill().isDebuff())
					continue;
				
				switch (skill.getSkillType())
				{
					case MAGE_BANE:
						if ("casting_time_down".equalsIgnoreCase(effect.getStackType()))
							break;
						if ("ma_up".equalsIgnoreCase(effect.getStackType()))
							break;
						continue;
					
					case WARRIOR_BANE:
						if ("attack_time_down".equalsIgnoreCase(effect.getStackType()))
							break;
						if ("speed_up".equalsIgnoreCase(effect.getStackType()))
							break;
						continue;
					default:
						break;
				}
				
				if (effect.getSkill().getId() == lastCanceledSkillId)
					continue;
				
				if (calcCancelSuccess(effect.getPeriod(), diffLevel, skillPower, skillVuln, minRate, maxRate))
				{
					lastCanceledSkillId = effect.getSkill().getId();
					effect.exit();
				}
				
				count--;
				if (count == 0)
					break;
			}
			
			Formulas.calcLethalHit(activeChar, target, skill);
		}
		
		if (skill.hasSelfEffects())
		{
			final L2Effect effect = activeChar.getFirstEffect(skill.getId());
			if (effect != null && effect.isSelfEffect())
				effect.exit();
			
			skill.getEffectsSelf(activeChar);
		}
		activeChar.setChargedShot(activeChar.isChargedShot(ShotType.BLESSED_SPIRITSHOT) ? ShotType.BLESSED_SPIRITSHOT : ShotType.SPIRITSHOT, skill.isStaticReuse());
	}
	
	private static boolean calcCancelSuccess(int effectPeriod, int diffLevel, double baseRate, double vuln, int minRate, int maxRate)
	{
		double rate = (2 * diffLevel + baseRate + effectPeriod / 120) * vuln;
		
		if (Config.DEVELOPER)
			_log.info("calcCancelSuccess(): diffLevel:" + diffLevel + ", baseRate:" + baseRate + ", vuln:" + vuln + ", total:" + rate);
		
		if (rate < minRate)
			rate = minRate;
		else if (rate > maxRate)
			rate = maxRate;
		
		return Rnd.get(100) < rate;
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}