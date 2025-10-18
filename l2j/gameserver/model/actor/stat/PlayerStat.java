package net.sf.l2j.gameserver.model.actor.stat;

import java.util.Map;

import net.sf.l2j.commons.math.MathUtil;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.instancemanager.ZoneManager;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.RewardInfo;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.base.Experience;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.model.zone.type.L2SwampZone;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.StatusUpdate;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.network.serverpackets.UserInfo;
import net.sf.l2j.gameserver.scripting.QuestState;
import net.sf.l2j.gameserver.skills.Stats;

public class PlayerStat extends PlayableStat
{
    private int _oldMaxHp;
    private int _oldMaxMp;
    private int _oldMaxCp;

    // Bonus stat values per item
    private static final int BONUS_P_ATK_3875 = 500;
    private static final int BONUS_M_ATK_3875 = 500;
    private static final int BONUS_P_DEF_3875 = 500;
    private static final int BONUS_M_DEF_3875 = 500;
    private static final int BONUS_ACCURACY_3875 = 50;
    private static final int BONUS_EVASION_3875 = 50;
    private static final int BONUS_CRIT_RATE_3875 = 10;
    private static final int BONUS_SPEED_3875 = 10;
    private static final int BONUS_ATK_SPD_3875 = 10;
    private static final int BONUS_CAST_SPD_3875 = 10;
    private static final int BONUS_HP_3875 = 2000;
    private static final int BONUS_MP_3875 = 2000;
    private static final int BONUS_CP_3875 = 2000;

    private static final int BONUS_P_ATK_3876 = 0;
    private static final int BONUS_M_ATK_3876 = 0;
    private static final int BONUS_P_DEF_3876 = 0;
    private static final int BONUS_M_DEF_3876 = 0;
    private static final int BONUS_ACCURACY_3876 = 0;
    private static final int BONUS_EVASION_3876 = 0;
    private static final int BONUS_CRIT_RATE_3876 = 0;
    private static final int BONUS_SPEED_3876 = 0;
    private static final int BONUS_ATK_SPD_3876 = 0;
    private static final int BONUS_CAST_SPD_3876 = 0;
    private static final int BONUS_HP_3876 = 0;
    private static final int BONUS_MP_3876 = 0;
    private static final int BONUS_CP_3876 = 0;

    private static final int BONUS_P_ATK_3877 = 0;
    private static final int BONUS_M_ATK_3877 = 0;
    private static final int BONUS_P_DEF_3877 = 0;
    private static final int BONUS_M_DEF_3877 = 0;
    private static final int BONUS_ACCURACY_3877 = 0;
    private static final int BONUS_EVASION_3877 = 0;
    private static final int BONUS_CRIT_RATE_3877 = 0;
    private static final int BONUS_SPEED_3877 = 0;
    private static final int BONUS_ATK_SPD_3877 = 0;
    private static final int BONUS_CAST_SPD_3877 = 0;
    private static final int BONUS_HP_3877 = 0;
    private static final int BONUS_MP_3877 = 0;
    private static final int BONUS_CP_3877 = 0;

    private static final int BONUS_P_ATK_3878 = 0;
    private static final int BONUS_M_ATK_3878 = 0;
    private static final int BONUS_P_DEF_3878 = 0;
    private static final int BONUS_M_DEF_3878 = 0;
    private static final int BONUS_ACCURACY_3878 = 0;
    private static final int BONUS_EVASION_3878 = 0;
    private static final int BONUS_CRIT_RATE_3878 = 0;
    private static final int BONUS_SPEED_3878 = 0;
    private static final int BONUS_ATK_SPD_3878 = 0;
    private static final int BONUS_CAST_SPD_3878 = 0;
    private static final int BONUS_HP_3878 = 0;
    private static final int BONUS_MP_3878 = 0;
    private static final int BONUS_CP_3878 = 0;

    private static final int BONUS_P_ATK_3879 = 0;
    private static final int BONUS_M_ATK_3879 = 0;
    private static final int BONUS_P_DEF_3879 = 0;
    private static final int BONUS_M_DEF_3879 = 0;
    private static final int BONUS_ACCURACY_3879 = 0;
    private static final int BONUS_EVASION_3879 = 0;
    private static final int BONUS_CRIT_RATE_3879 = 0;
    private static final int BONUS_SPEED_3879 = 0;
    private static final int BONUS_ATK_SPD_3879 = 0;
    private static final int BONUS_CAST_SPD_3879 = 0;
    private static final int BONUS_HP_3879 = 0;
    private static final int BONUS_MP_3879 = 0;
    private static final int BONUS_CP_3879 = 0;

    private boolean hasItem(int itemId) {
        return getActiveChar().getInventory().getItemByItemId(itemId) != null;
    }

    private int sumBonus(int b3875, int b3876, int b3877, int b3878, int b3879) {
        int total = 0;
        if (hasItem(3875)) total += b3875;
        if (hasItem(3876)) total += b3876;
        if (hasItem(3877)) total += b3877;
        if (hasItem(3878)) total += b3878;
        if (hasItem(3879)) total += b3879;
        return total;
    }

    public PlayerStat(Player activeChar) { super(activeChar); }

    @Override
    public boolean addExp(long value) {
        if (!getActiveChar().getAccessLevel().canGainExp()) return false;
        if (!super.addExp(value)) return false;
        getActiveChar().sendPacket(new UserInfo(getActiveChar()));
        return true;
    }

    @Override
    public boolean addExpAndSp(long addToExp, int addToSp) {
        if (!super.addExpAndSp(addToExp, addToSp)) return false;
        SystemMessage sm;
        if (addToExp == 0 && addToSp > 0)
            sm = SystemMessage.getSystemMessage(SystemMessageId.ACQUIRED_S1_SP).addNumber(addToSp);
        else if (addToExp > 0 && addToSp == 0)
            sm = SystemMessage.getSystemMessage(SystemMessageId.EARNED_S1_EXPERIENCE).addNumber((int) addToExp);
        else
            sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_EARNED_S1_EXP_AND_S2_SP).addNumber((int) addToExp).addNumber(addToSp);
        getActiveChar().sendPacket(sm);
        return true;
    }

    public boolean addExpAndSp(long addToExp, int addToSp, Map<Creature, RewardInfo> rewards) {
        if (!getActiveChar().getAccessLevel().canGainExp()) return false;
        if (getActiveChar().hasPet()) {
            final Pet pet = (Pet) getActiveChar().getPet();
            if (pet.getStat().getExp() <= (pet.getTemplate().getPetDataEntry(81).getMaxExp() + 10000) && !pet.isDead()) {
                if (MathUtil.checkIfInShortRadius(Config.PARTY_RANGE, pet, getActiveChar(), true)) {
                    int ratio = pet.getPetData().getExpType();
                    long petExp = 0; int petSp = 0;
                    if (ratio == -1) {
                        RewardInfo r = rewards.get(pet);
                        RewardInfo reward = rewards.get(getActiveChar());
                        if (r != null && reward != null) {
                            double damageDoneByPet = ((double) (r.getDamage())) / reward.getDamage();
                            petExp = (long) (addToExp * damageDoneByPet);
                            petSp = (int) (addToSp * damageDoneByPet);
                        }
                    } else {
                        if (ratio > 100) ratio = 100;
                        petExp = Math.round(addToExp * (1 - (ratio / 100.0)));
                        petSp = (int) Math.round(addToSp * (1 - (ratio / 100.0)));
                    }
                    addToExp -= petExp;
                    addToSp -= petSp;
                    pet.addExpAndSp(petExp, petSp);
                }
            }
        }
        return addExpAndSp(addToExp, addToSp);
    }

    @Override
    public boolean removeExpAndSp(long removeExp, int removeSp) { return removeExpAndSp(removeExp, removeSp, true); }

    public boolean removeExpAndSp(long removeExp, int removeSp, boolean sendMessage) {
        final int oldLevel = getLevel();
        if (!super.removeExpAndSp(removeExp, removeSp)) return false;
        if (sendMessage) {
            if (removeExp > 0) getActiveChar().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EXP_DECREASED_BY_S1).addNumber((int) removeExp));
            if (removeSp > 0) getActiveChar().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SP_DECREASED_S1).addNumber(removeSp));
            if (getLevel() < oldLevel) getActiveChar().broadcastStatusUpdate();
        }
        return true;
    }

    @Override
    public final boolean addLevel(byte value) {
        if (getLevel() + value > Experience.MAX_LEVEL - 1) return false;
        boolean levelIncreased = super.addLevel(value);
        if (levelIncreased) {
            if (!Config.DISABLE_TUTORIAL) {
                QuestState qs = getActiveChar().getQuestState("Tutorial");
                if (qs != null) qs.getQuest().notifyEvent("CE40", null, getActiveChar());
            }
            getActiveChar().setCurrentCp(getMaxCp());
            getActiveChar().broadcastPacket(new SocialAction(getActiveChar(), 15));
            getActiveChar().sendPacket(SystemMessageId.YOU_INCREASED_YOUR_LEVEL);
        }
        getActiveChar().giveSkills();
        final Clan clan = getActiveChar().getClan();
        if (clan != null) {
            clan.updateClanMember(getActiveChar());
            clan.broadcastToOnlineMembers(new PledgeShowMemberListUpdate(getActiveChar()));
        }
        final Party party = getActiveChar().getParty();
        if (party != null) party.recalculateLevel();
        getActiveChar().refreshOverloaded();
        getActiveChar().refreshExpertisePenalty();
        getActiveChar().sendPacket(new UserInfo(getActiveChar()));
        return levelIncreased;
    }

    @Override
    public final long getExpForLevel(int level) { return Experience.LEVEL[level]; }
    @Override
    public final Player getActiveChar() { return (Player) super.getActiveChar(); }

    @Override
    public final long getExp() {
        if (getActiveChar().isSubClassActive())
            return getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).getExp();
        return super.getExp();
    }

    @Override
    public final void setExp(long value) {
        if (getActiveChar().isSubClassActive())
            getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).setExp(value);
        else super.setExp(value);
    }

    @Override
    public final byte getLevel() {
        if (getActiveChar().isSubClassActive())
            return getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).getLevel();
        return super.getLevel();
    }

    @Override
    public final void setLevel(byte value) {
        if (value > Experience.MAX_LEVEL - 1) value = Experience.MAX_LEVEL - 1;
        if (getActiveChar().isSubClassActive())
            getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).setLevel(value);
        else super.setLevel(value);
    }

    @Override
    public final int getMaxCp() {
        int val = (int) calcStat(Stats.MAX_CP, getActiveChar().getTemplate().getBaseCpMax(getActiveChar().getLevel()), null, null);
        val += sumBonus(BONUS_CP_3875, BONUS_CP_3876, BONUS_CP_3877, BONUS_CP_3878, BONUS_CP_3879);
        if (val != _oldMaxCp) {
            _oldMaxCp = val;
            if (getActiveChar().getStatus().getCurrentCp() != val)
                getActiveChar().getStatus().setCurrentCp(getActiveChar().getStatus().getCurrentCp());
        }
        return val;
    }

    @Override
    public final int getMaxHp() {
        int val = super.getMaxHp();
        val += sumBonus(BONUS_HP_3875, BONUS_HP_3876, BONUS_HP_3877, BONUS_HP_3878, BONUS_HP_3879);
        if (val != _oldMaxHp) {
            _oldMaxHp = val;
            if (getActiveChar().getStatus().getCurrentHp() != val)
                getActiveChar().getStatus().setCurrentHp(getActiveChar().getStatus().getCurrentHp());
        }
        return val;
    }

    @Override
    public final int getMaxMp() {
        int val = super.getMaxMp();
        val += sumBonus(BONUS_MP_3875, BONUS_MP_3876, BONUS_MP_3877, BONUS_MP_3878, BONUS_MP_3879);
        if (val != _oldMaxMp) {
            _oldMaxMp = val;
            if (getActiveChar().getStatus().getCurrentMp() != val)
                getActiveChar().getStatus().setCurrentMp(getActiveChar().getStatus().getCurrentMp());
        }
        return val;
    }

    @Override
    public final int getSp() {
        if (getActiveChar().isSubClassActive())
            return getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).getSp();
        return super.getSp();
    }

    @Override
    public final void setSp(int value) {
        if (getActiveChar().isSubClassActive())
            getActiveChar().getSubClasses().get(getActiveChar().getClassIndex()).setSp(value);
        else super.setSp(value);
        StatusUpdate su = new StatusUpdate(getActiveChar());
        su.addAttribute(StatusUpdate.SP, getSp());
        getActiveChar().sendPacket(su);
    }

    @Override
    public int getBaseRunSpeed() {
        if (getActiveChar().isMounted()) {
            int base = (getActiveChar().isFlying()) ? getActiveChar().getPetDataEntry().getMountFlySpeed() : getActiveChar().getPetDataEntry().getMountBaseSpeed();
            if (getActiveChar().getLevel() < getActiveChar().getMountLevel()) base /= 2;
            if (getActiveChar().checkFoodState(getActiveChar().getPetTemplate().getHungryLimit())) base /= 2;
            return base;
        }
        return super.getBaseRunSpeed();
    }

    public int getBaseSwimSpeed() {
        if (getActiveChar().isMounted()) {
            int base = getActiveChar().getPetDataEntry().getMountSwimSpeed();
            if (getActiveChar().getLevel() < getActiveChar().getMountLevel()) base /= 2;
            if (getActiveChar().checkFoodState(getActiveChar().getPetTemplate().getHungryLimit())) base /= 2;
            return base;
        }
        return getActiveChar().getTemplate().getBaseSwimSpeed();
    }

    @Override
    public float getMoveSpeed() {
        float baseValue = getActiveChar().isInsideZone(ZoneId.WATER) ? getBaseSwimSpeed() : getBaseMoveSpeed();
        if (getActiveChar().isInsideZone(ZoneId.SWAMP)) {
            final L2SwampZone zone = ZoneManager.getInstance().getZone(getActiveChar(), L2SwampZone.class);
            if (zone != null)
                baseValue *= (100 + zone.getMoveBonus()) / 100.0;
        }
        final int penalty = getActiveChar().getExpertiseArmorPenalty();
        if (penalty > 0) baseValue *= Math.pow(0.84, penalty);
        baseValue += sumBonus(BONUS_SPEED_3875, BONUS_SPEED_3876, BONUS_SPEED_3877, BONUS_SPEED_3878, BONUS_SPEED_3879);
        return (float) calcStat(Stats.RUN_SPEED, baseValue, null, null);
    }

    @Override
    public int getMAtk(Creature target, L2Skill skill) {
        if (getActiveChar().isMounted()) {
            double base = getActiveChar().getPetDataEntry().getMountMAtk();
            if (getActiveChar().getLevel() < getActiveChar().getMountLevel()) base /= 2;
            return (int) calcStat(Stats.MAGIC_ATTACK, base, null, null);
        }
        int val = super.getMAtk(target, skill);
        val += sumBonus(BONUS_M_ATK_3875, BONUS_M_ATK_3876, BONUS_M_ATK_3877, BONUS_M_ATK_3878, BONUS_M_ATK_3879);
        return val;
    }

    @Override
    public int getMAtkSpd() {
        double base = 333;
        if (getActiveChar().isMounted()) {
            if (getActiveChar().checkFoodState(getActiveChar().getPetTemplate().getHungryLimit())) base /= 2;
        }
        final int penalty = getActiveChar().getExpertiseArmorPenalty();
        if (penalty > 0) base *= Math.pow(0.84, penalty);
        int val = (int) calcStat(Stats.MAGIC_ATTACK_SPEED, base, null, null);
        val += sumBonus(BONUS_CAST_SPD_3875, BONUS_CAST_SPD_3876, BONUS_CAST_SPD_3877, BONUS_CAST_SPD_3878, BONUS_CAST_SPD_3879);
        return val;
    }

    @Override
    public int getPAtk(Creature target) {
        if (getActiveChar().isMounted()) {
            double base = getActiveChar().getPetDataEntry().getMountPAtk();
            if (getActiveChar().getLevel() < getActiveChar().getMountLevel()) base /= 2;
            return (int) calcStat(Stats.POWER_ATTACK, base, null, null);
        }
        int val = super.getPAtk(target);
        val += sumBonus(BONUS_P_ATK_3875, BONUS_P_ATK_3876, BONUS_P_ATK_3877, BONUS_P_ATK_3878, BONUS_P_ATK_3879);
        return val;
    }

    @Override
    public int getPAtkSpd() {
        if (getActiveChar().isMounted()) {
            int base = getActiveChar().getPetDataEntry().getMountAtkSpd();
            if (getActiveChar().checkFoodState(getActiveChar().getPetTemplate().getHungryLimit())) base /= 2;
            return (int) calcStat(Stats.POWER_ATTACK_SPEED, base, null, null);
        }
        int val = super.getPAtkSpd();
        val += sumBonus(BONUS_ATK_SPD_3875, BONUS_ATK_SPD_3876, BONUS_ATK_SPD_3877, BONUS_ATK_SPD_3878, BONUS_ATK_SPD_3879);
        return val;
    }

    @Override
    public int getEvasionRate(Creature target) {
        int val = super.getEvasionRate(target);
        val += sumBonus(BONUS_EVASION_3875, BONUS_EVASION_3876, BONUS_EVASION_3877, BONUS_EVASION_3878, BONUS_EVASION_3879);
        final int penalty = getActiveChar().getExpertiseArmorPenalty();
        if (penalty > 0) val -= (2 * penalty);
        return val;
    }

    @Override
    public int getAccuracy() {
        int val = super.getAccuracy();
        val += sumBonus(BONUS_ACCURACY_3875, BONUS_ACCURACY_3876, BONUS_ACCURACY_3877, BONUS_ACCURACY_3878, BONUS_ACCURACY_3879);
        if (getActiveChar().getExpertiseWeaponPenalty()) val -= 20;
        return val;
    }

    @Override
    public int getCriticalHit(Creature target, L2Skill skill) {
        int val = super.getCriticalHit(target, skill);
        val += sumBonus(BONUS_CRIT_RATE_3875, BONUS_CRIT_RATE_3876, BONUS_CRIT_RATE_3877, BONUS_CRIT_RATE_3878, BONUS_CRIT_RATE_3879);
        return val;
    }

    @Override
    public int getPhysicalAttackRange() {
        return (int) calcStat(Stats.POWER_ATTACK_RANGE, getActiveChar().getAttackType().getRange(), null, null);
    }
}