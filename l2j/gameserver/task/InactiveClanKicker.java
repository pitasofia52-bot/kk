package net.sf.l2j.gameserver.task;

import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.model.pledge.ClanMember;
import net.sf.l2j.gameserver.model.actor.instance.Player;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.Calendar;

public class InactiveClanKicker implements Runnable
{
    private static final Logger _log = Logger.getLogger(InactiveClanKicker.class.getName());
    private static final int CLAN_ID = 268813597; // L2lineage1
    private static final long INACTIVE_LIMIT_MILLIS = 4L * 24L * 60L * 60L * 1000L; // 4 days

    public static void start()
    {
        // Υπολογισμός χρόνου μέχρι τις 01:00
        Calendar now = Calendar.getInstance();
        Calendar nextRun = (Calendar) now.clone();
        nextRun.set(Calendar.HOUR_OF_DAY, 1);
        nextRun.set(Calendar.MINUTE, 0);
        nextRun.set(Calendar.SECOND, 0);
        nextRun.set(Calendar.MILLISECOND, 0);
        if (now.after(nextRun)) {
            // Αν η ώρα είναι μετά τη 01:00, πήγαινε στην επόμενη μέρα
            nextRun.add(Calendar.DATE, 1);
        }
        long initialDelay = nextRun.getTimeInMillis() - now.getTimeInMillis();

        // Εκτέλεση κάθε μέρα στις 01:00
        net.sf.l2j.commons.concurrent.ThreadPool.scheduleAtFixedRate(
            new InactiveClanKicker(),
            initialDelay,
            TimeUnit.DAYS.toMillis(1)
        );
    }

    @Override
    public void run()
    {
        Clan clan = ClanTable.getInstance().getClan(CLAN_ID);
        if (clan == null)
        {
            _log.warning("InactiveClanKicker: Clan with id " + CLAN_ID + " not found.");
            return;
        }

        long now = System.currentTimeMillis();

        for (ClanMember member : clan.getMembers())
        {
            // Leader is never kicked automatically for inactivity
            if (clan.getLeader() == member)
                continue;

            Player player = member.getPlayerInstance();

            // Αν είναι online, δεν τον κλωτσάς
            if (player != null && player.isOnline())
                continue;

            long lastAccess = 0;

            // Αν είναι offline, παίρνουμε lastAccess από DB (μέσω ClanMember)
            if (player != null)
                lastAccess = player.getLastAccess();
            else
            	 continue; 

            if (lastAccess == 0)
                continue; // Δεν μπορείς να ξέρεις ποτέ μπήκε, δεν τον κλωτσάς

            if ((now - lastAccess) >= INACTIVE_LIMIT_MILLIS)
            {
                clan.removeClanMember(member.getObjectId(), 0); // 0 για no penalty
                _log.info("InactiveClanKicker: Removed inactive member: " + member.getName() + " from clan " + clan.getName());
            }
        }
    }
}