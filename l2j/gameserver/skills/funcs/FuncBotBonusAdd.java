package net.sf.l2j.gameserver.skills.funcs;

import net.sf.l2j.gameserver.skills.Env;
import net.sf.l2j.gameserver.skills.Stats;
import net.sf.l2j.gameserver.skills.basefuncs.Func;

/**
 * Flat additive bonus Func για Bot AI classes.
 * Owner: το ίδιο το FakePlayer instance, ώστε να αφαιρείται εύκολα με removeStatsByOwner(owner).
 */
public final class FuncBotBonusAdd extends Func
{
    private final double bonus;

    /**
     * @param stat  Στο οποίο stat εφαρμόζεται.
     * @param order Προτεραιότητα (μεγαλύτερο = εκτελείται αργότερα).
     * @param owner Owner object (FakePlayer instance).
     * @param bonus Flat ποσό που θα προστεθεί.
     */
    public FuncBotBonusAdd(Stats stat, int order, Object owner, double bonus)
    {
        super(stat, order, owner, null);
        this.bonus = bonus;
    }

    @Override
    public void calc(Env env)
    {
        env.addValue(bonus);
    }
}