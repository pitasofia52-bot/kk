package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.commons.math.MathUtil;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.commons.util.ArraysUtil;

import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.ai.CtrlIntention;
import net.sf.l2j.gameserver.model.actor.ai.type.SummonAI;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.actor.instance.Folk;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.actor.instance.Servitor;
import net.sf.l2j.gameserver.model.actor.instance.SiegeSummon;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.NpcSay;
import net.sf.l2j.gameserver.custom.tvt.TvTManager;

public final class RequestActionUse extends L2GameClientPacket
{
	private static final int[] PASSIVE_SUMMONS =
	{
		12564,
		12621,
		14702,
		14703,
		14704,
		14705,
		14706,
		14707,
		14708,
		14709,
		14710,
		14711,
		14712,
		14713,
		14714,
		14715,
		14716,
		14717,
		14718,
		14719,
		14720,
		14721,
		14722,
		14723,
		14724,
		14725,
		14726,
		14727,
		14728,
		14729,
		14730,
		14731,
		14732,
		14733,
		14734,
		14735,
		14736
	};
	
	private static final int SIN_EATER_ID = 12564;
	private static final String[] SIN_EATER_ACTIONS_STRINGS =
	{
		"special skill? Abuses in this kind of place, can turn blood Knots...!",
		"Hey! Brother! What do you anticipate to me?",
		"shouts ha! Flap! Flap! Response?",
		", has not hit...!"
	};
	
	private int _actionId;
	private boolean _ctrlPressed;
	private boolean _shiftPressed;
	
	@Override
	protected void readImpl()
	{
		_actionId = readD();
		_ctrlPressed = (readD() == 1);
		_shiftPressed = (readC() == 1);
	}
	
	@Override
	protected void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		// Dont do anything if player is dead, or use fakedeath using another action than sit.
		if ((activeChar.isFakeDeath() && _actionId != 0) || activeChar.isDead() || activeChar.isOutOfControl())
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final Summon pet = activeChar.getPet();
		final WorldObject target = activeChar.getTarget();
		final TvTManager tvt = TvTManager.getInstance();
		
		switch (_actionId)
		{
			case 0:
				activeChar.tryToSitOrStand(target, activeChar.isSitting());
				break;
			
			case 1:
				if (activeChar.isMounted())
					return;
				
				if (activeChar.isRunning())
					activeChar.setWalking();
				else
					activeChar.setRunning();
				break;
			
			case 10:
				activeChar.tryOpenPrivateSellStore(false);
				break;
			
			case 28:
				activeChar.tryOpenPrivateBuyStore();
				break;
			
			case 15:
			case 21:
				if (pet == null)
					return;
				
				if (pet.getFollowStatus() && MathUtil.calculateDistance(activeChar, pet, true) > 2000)
					return;
				
				if (pet.isOutOfControl())
				{
					activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
					return;
				}
				
				((SummonAI) pet.getAI()).notifyFollowStatusChange();
				break;
			
			case 16:
			case 22: // Pet attack
				if (!(target instanceof Creature) || pet == null || pet == target || activeChar == target)
					return;
				
				if (ArraysUtil.contains(PASSIVE_SUMMONS, pet.getNpcId()))
					return;
				
				if (pet.isOutOfControl())
				{
					activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
					return;
				}
				
				if (pet.isAttackingDisabled())
				{
					if (pet.getAttackEndTime() <= System.currentTimeMillis())
						return;
					
					pet.getAI().setIntention(CtrlIntention.ATTACK, target);
				}
				
				if (pet instanceof Pet && (pet.getLevel() - activeChar.getLevel() > 20))
				{
					activeChar.sendPacket(SystemMessageId.PET_TOO_HIGH_TO_CONTROL);
					return;
				}
				
				if (activeChar.isInOlympiadMode() && !activeChar.isOlympiadStart())
					return;
				
				// TvT pet attack rule
				if (target instanceof Player && tvt.isRunning() && !tvt.isTvTCombatAllowed(activeChar, (Player) target))
					return; // silent block
				
				pet.setTarget(target);
				
				if (!target.isAutoAttackable(activeChar) && !_ctrlPressed && (!(target instanceof Folk)))
				{
					pet.setFollowStatus(false);
					pet.getAI().setIntention(CtrlIntention.FOLLOW, target);
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
					return;
				}
				
				if (target instanceof Door)
				{
					if (((Door) target).isAutoAttackable(activeChar) && pet.getNpcId() != SiegeSummon.SWOOP_CANNON_ID)
						pet.getAI().setIntention(CtrlIntention.ATTACK, target);
				}
				else if (pet.getNpcId() != SiegeSummon.SIEGE_GOLEM_ID)
				{
					if (Creature.isInsidePeaceZone(pet, target))
					{
						pet.setFollowStatus(false);
						pet.getAI().setIntention(CtrlIntention.FOLLOW, target);
					}
					else
						pet.getAI().setIntention(CtrlIntention.ATTACK, target);
				}
				break;
			
			case 17:
			case 23:
				if (pet == null)
					return;
				
				if (pet.isOutOfControl())
				{
					activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
					return;
				}
				
				pet.getAI().setIntention(CtrlIntention.ACTIVE, null);
				break;
			
			case 19:
				if (pet == null || !(pet instanceof Pet))
					return;
				
				if (pet.isDead())
					activeChar.sendPacket(SystemMessageId.DEAD_PET_CANNOT_BE_RETURNED);
				else if (pet.isOutOfControl())
					activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
				else if (pet.isAttackingNow() || pet.isInCombat())
					activeChar.sendPacket(SystemMessageId.PET_CANNOT_SENT_BACK_DURING_BATTLE);
				else if (((Pet) pet).checkUnsummonState())
					activeChar.sendPacket(SystemMessageId.YOU_CANNOT_RESTORE_HUNGRY_PETS);
				else
					pet.unSummon(activeChar);
				break;
			
			case 38:
				activeChar.mountPlayer(pet);
				break;
			
			case 32:
				break;
			
			case 36:
				useSkill(4259, target);
				break;
			
			case 37:
				activeChar.tryOpenWorkshop(true);
				break;
			
			case 39:
				useSkill(4138, target);
				break;
			
			case 41:
				if (!(target instanceof Door))
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
					return;
				}
				
				useSkill(4230, target);
				break;
			
			case 42:
				useSkill(4378, activeChar);
				break;
			
			case 43:
				useSkill(4137, target);
				break;
			
			case 44:
				useSkill(4139, target);
				break;
			
			case 45:
				useSkill(4025, activeChar);
				break;
			
			case 46:
				useSkill(4261, target);
				break;
			
			case 47:
				useSkill(4260, target);
				break;
			
			case 48:
				useSkill(4068, target);
				break;
			
			case 51:
				activeChar.tryOpenWorkshop(false);
				break;
			
			case 52:
				if (pet == null || !(pet instanceof Servitor))
					return;
				
				if (pet.isDead())
					activeChar.sendPacket(SystemMessageId.DEAD_PET_CANNOT_BE_RETURNED);
				else if (pet.isOutOfControl())
					activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
				else if (pet.isAttackingNow() || pet.isInCombat())
					activeChar.sendPacket(SystemMessageId.PET_CANNOT_SENT_BACK_DURING_BATTLE);
				else
					pet.unSummon(activeChar);
				break;
			
			case 53:
			case 54:
				if (target == null || pet == null || pet == target)
					return;
				
				if (pet.isOutOfControl())
				{
					activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
					return;
				}
				
				pet.setFollowStatus(false);
				pet.getAI().setIntention(CtrlIntention.MOVE_TO, new Location(target.getX(), target.getY(), target.getZ()));
				break;
			
			case 61:
				activeChar.tryOpenPrivateSellStore(true);
				break;
			
			case 1000:
				if (!(target instanceof Door))
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
					return;
				}
				
				useSkill(4079, target);
				break;
			
			case 1001:
				if (useSkill(4139, pet) && pet.getNpcId() == SIN_EATER_ID && Rnd.get(100) < 10)
					pet.broadcastPacket(new NpcSay(pet.getObjectId(), Say2.ALL, pet.getNpcId(), SIN_EATER_ACTIONS_STRINGS[Rnd.get(SIN_EATER_ACTIONS_STRINGS.length)]));
				break;
			
			case 1003:
				useSkill(4710, target);
				break;
			
			case 1004:
				useSkill(4711, activeChar);
				break;
			
			case 1005:
				useSkill(4712, target);
				break;
			
			case 1006:
				useSkill(4713, activeChar);
				break;
			
			case 1007:
				useSkill(4699, activeChar);
				break;
			
			case 1008:
				useSkill(4700, activeChar);
				break;
			
			case 1009:
				useSkill(4701, target);
				break;
			
			case 1010:
				useSkill(4702, activeChar);
				break;
			
			case 1011:
				useSkill(4703, activeChar);
				break;
			
			case 1012:
				useSkill(4704, target);
				break;
			
			case 1013:
				useSkill(4705, target);
				break;
			
			case 1014:
				useSkill(4706, activeChar);
				break;
			
			case 1015:
				useSkill(4707, target);
				break;
			
			case 1016:
				useSkill(4709, target);
				break;
			
			case 1017:
				useSkill(4708, target);
				break;
			
			case 1031:
				useSkill(5135, target);
				break;
			
			case 1032:
				useSkill(5136, target);
				break;
			
			case 1033:
				useSkill(5137, target);
				break;
			
			case 1034:
				useSkill(5138, target);
				break;
			
			case 1035:
				useSkill(5139, target);
				break;
			
			case 1036:
				useSkill(5142, target);
				break;
			
			case 1037:
				useSkill(5141, target);
				break;
			
			case 1038:
				useSkill(5140, target);
				break;
			
			case 1039:
				if (target instanceof Door)
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
					return;
				}
				
				useSkill(5110, target);
				break;
			
			case 1040:
				if (target instanceof Door)
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
					return;
				}
				
				useSkill(5111, target);
				break;
			
			default:
				_log.warning(activeChar.getName() + ": unhandled action type " + _actionId);
		}
	}
	
	private boolean useSkill(int skillId, WorldObject target)
	{
		final Player activeChar = getClient().getActiveChar();
		
		if (activeChar == null || activeChar.isInStoreMode())
			return false;
		
		final Summon activeSummon = activeChar.getPet();
		if (activeSummon == null)
			return false;
		
		if (activeSummon instanceof Pet && activeSummon.getLevel() - activeChar.getLevel() > 20)
		{
			activeChar.sendPacket(SystemMessageId.PET_TOO_HIGH_TO_CONTROL);
			return false;
		}
		
		if (activeSummon.isOutOfControl())
		{
			activeChar.sendPacket(SystemMessageId.PET_REFUSING_ORDER);
			return false;
		}
		
		final L2Skill skill = activeSummon.getSkill(skillId);
		if (skill == null)
			return false;
		
		if (skill.isOffensive() && activeChar == target)
			return false;
		
		// TvT summon offensive skill guard
		if (skill.isOffensive() && target instanceof Player)
		{
			final TvTManager tvt = TvTManager.getInstance();
			if (tvt.isRunning() && !tvt.isTvTCombatAllowed(activeChar, (Player) target))
				return false;
		}
		
		activeSummon.setTarget(target);
		return activeSummon.useMagic(skill, _ctrlPressed, _shiftPressed);
	}
}