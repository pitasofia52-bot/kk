package net.sf.l2j.gameserver.skills.effects;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.L2Effect;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.skills.Env;
import net.sf.l2j.gameserver.skills.Formulas;
import net.sf.l2j.gameserver.templates.skills.L2EffectType;

/**
 * Generic cancel effect (cancels dances first, then other buffs).
 * Προσθήκη: Προστατεύουμε θετικά μη‑toggle buffs των FakePlayer (class simpleName endsWith("FakePlayer")).
 * Debuffs εξακολουθούν να μπορούν να ακυρωθούν.
 */
public class EffectCancel extends L2Effect
{
	public EffectCancel(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.CANCEL;
	}
	
	@Override
	public boolean onStart()
	{
		return cancel(getEffector(), getEffected(), this);
	}
	
	@Override
	public boolean onActionTime()
	{
		return false;
	}
	
	private static boolean cancel(Creature caster, Creature target, L2Effect effect)
	{
		if (!(target instanceof Player) || target.isDead())
			return false;
		
		final boolean targetIsFakeBot = target.getClass().getSimpleName().endsWith("FakePlayer");
		
		final int cancelLvl = effect.getSkill().getMagicLevel();
		int count = effect.getSkill().getMaxNegatedEffects();
		
		double rate = effect.getEffectPower();
		
		// Resistance/vulnerability
		final double res = Formulas.calcSkillVulnerability(caster, target, effect.getSkill(), effect.getSkillType());
		rate *= res;
		
		L2Effect eff;
		int lastCanceledSkillId = 0;
		final L2Effect[] effects = target.getAllEffects();
		
		// First pass : dances / songs
		for (int i = effects.length; --i >= 0;)
		{
			eff = effects[i];
			if (eff == null)
				continue;
			
			if (!eff.getSkill().isDance())
				continue;
			
			// Προστασία θετικών μη‑toggle buffs (συμπεριλαμβάνει dances) για FakePlayer
			if (targetIsFakeBot && !eff.getSkill().isDebuff() && !eff.getSkill().isToggle())
				continue;
			
			if (eff.getSkill().getId() == lastCanceledSkillId)
			{
				eff.exit(); // ήδη ακυρωμένο ίδιο skill id επόμενου stack
				continue;
			}
			
			if (!calcCancelSuccess(eff, cancelLvl, (int) rate))
				continue;
			
			lastCanceledSkillId = eff.getSkill().getId();
			eff.exit();
			count--;
			
			if (count == 0)
				break;
		}
		
		// Second pass : όλα τα υπόλοιπα μη dance
		if (count != 0)
		{
			lastCanceledSkillId = 0;
			for (int i = effects.length; --i >= 0;)
			{
				eff = effects[i];
				if (eff == null)
					continue;
				
				if (eff.getSkill().isDance())
					continue;
				
				// Προστασία θετικών μη‑toggle buffs για FakePlayer
				if (targetIsFakeBot && !eff.getSkill().isDebuff() && !eff.getSkill().isToggle())
					continue;
				
				if (eff.getSkill().getId() == lastCanceledSkillId)
				{
					eff.exit();
					continue;
				}
				
				if (!calcCancelSuccess(eff, cancelLvl, (int) rate))
					continue;
				
				lastCanceledSkillId = eff.getSkill().getId();
				eff.exit();
				count--;
				
				if (count == 0)
					break;
			}
		}
		return true;
	}
	
	private static boolean calcCancelSuccess(L2Effect effect, int cancelLvl, int baseRate)
	{
		int rate = 2 * (cancelLvl - effect.getSkill().getMagicLevel());
		rate += effect.getPeriod() / 120;
		rate += baseRate;
		
		if (rate < 25)
			rate = 25;
		else if (rate > 75)
			rate = 75;
		
		return Rnd.get(100) < rate;
	}
}