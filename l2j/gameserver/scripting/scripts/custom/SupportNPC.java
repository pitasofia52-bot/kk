package net.sf.l2j.gameserver.scripting.scripts.custom;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.QuestState;

public class SupportNPC extends Quest
{
    private static final String qn = "SupportNPC";
    private static final int NPC_ID = 60238;

    private static final int LIMIT_OTHER = 2;
    private static final int LIMIT_DONATE = 5;

    private static final Map<String, DailyCounter> LIMITS = new ConcurrentHashMap<>();

    private static final String SUPPORT_DIR = "./SupportServer";
    private static final String SUPPORT_FILE = SUPPORT_DIR + "/support.txt";
    private static final Object FILE_LOCK = new Object();

    private static final int MAX_MESSAGE_LENGTH = 1000;

    // Debug
    private static final boolean DEBUG = false;
    private static final String DEBUG_FILE = SUPPORT_DIR + "/debug.log";

    public SupportNPC()
    {
        super(99999, "custom");
        addStartNpc(NPC_ID);
        addTalkId(NPC_ID);
        addFirstTalkId(NPC_ID);
    }

    @Override
    public String onFirstTalk(Npc npc, Player player)
    {
        QuestState st = player.getQuestState(qn);
        if (st == null)
            st = newQuestState(player);
        return "start.htm";
    }

    @Override
    public String onTalk(Npc npc, Player player)
    {
        QuestState st = player.getQuestState(qn);
        if (st == null)
            st = newQuestState(player);
        return "start.htm";
    }

    @Override
    public String onAdvEvent(String event, Npc npc, Player player)
    {
        QuestState st = player.getQuestState(qn);
        if (st == null)
            st = newQuestState(player);

        if (event == null || event.isEmpty())
            return null;

        if (DEBUG)
            debug("RAW event='" + event + "' player=" + (player != null ? player.getName() : "null"));

        if (event.equals("main"))
            return "start.htm";

        if (event.equals("bug") || event.equals("donate") || event.equals("other"))
        {
            st.set("option", event);
            return "message.htm";
        }

        // MESSAGE PATTERNS :
        if (event.startsWith("message_") || event.startsWith("message$"))
        {
            String raw = event.substring(8);
            return handleMessageFinalize(st, player, raw, "underscore/dollar");
        }
        else if (event.startsWith("message "))
        {
            String raw = event.substring(8);
            return handleMessageFinalize(st, player, raw, "space");
        }

        return null;
    }

    private String handleMessageFinalize(QuestState st, Player player, String raw, String src)
    {
        String before = raw;
        String message = normalizeInput(raw);
        if (isPlaceholder(message, "message"))
            message = "";
        message = sanitizeMessage(message);

        if (DEBUG)
            debug("MESSAGE via " + src + ": before='" + before + "' normalized='" + message + "'");

        String option = st.get("option");
        if (option == null)
        {
            if (DEBUG)
                debug("No option in state; returning to start.");
            cleanupState(st);
            return "start.htm";
        }

        String account = player.getAccountName();
        String charName = player.getName();

        DailyCounter counter = getCounter(account);
        int limit = option.equals("donate") ? LIMIT_DONATE : LIMIT_OTHER;
        int used = counter.get(option);
        if (used >= limit)
        {
            long hours = counter.hoursUntilReset();
            if (DEBUG)
                debug("Daily limit reached for option=" + option + " account=" + account);
            cleanupState(st);
            return getLimitHtml(option, hours);
        }

        if (message.isEmpty())
        {
            if (DEBUG)
                debug("Message empty -> need content.");
            return getNeedContentHtml();
        }

        String line = formatLine(account, charName, option, message);
        boolean ok = appendLine(line);

        if (DEBUG)
            debug("APPEND: ok=" + ok + " line=" + line);

        counter.inc(option);
        cleanupState(st);
        return ok ? "thankyou.htm" : "error.htm";
    }

    private void cleanupState(QuestState st)
    {
        st.exitQuest(true);
        st.unset("option");
    }

    private String normalizeInput(String s)
    {
        if (s == null)
            return "";
        if (s.startsWith("$"))
            s = s.substring(1);
        return s.trim();
    }

    private boolean isPlaceholder(String value, String placeholder)
    {
        if (value == null || value.isEmpty())
            return true;
        String v = value.toLowerCase(Locale.ENGLISH);
        return v.equals(placeholder) || v.equals("$" + placeholder);
    }

    private String sanitizeMessage(String message)
    {
        if (message == null)
            return "";
        if (message.length() > MAX_MESSAGE_LENGTH)
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        message = message.replace('\r', ' ').replace('\n', ' ');
        message = message.replaceAll("<.*?>", "");
        return message.trim();
    }

    private String getNeedContentHtml()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        sb.append("<font color=\"LEVEL\">You must enter a message.</font><br><br>");
        sb.append("<a action=\"bypass -h Quest SupportNPC main\">Back</a>");
        sb.append("</center></body></html>");
        return sb.toString();
    }

    private String getLimitHtml(String option, long hours)
    {
        String html = "<html><body><br><center>";
        html += "<font color=\"LEVEL\">You have reached your daily limit for this option.</font><br>";
        html += "You can send another message in <font color=\"LEVEL\">" + hours + "</font> hour(s).<br>";
        html += "<br><a action=\"bypass -h Quest SupportNPC main\">Back to Menu</a>";
        html += "</center></body></html>";
        return html;
    }

    private DailyCounter getCounter(String account)
    {
        String today = getToday();
        DailyCounter c = LIMITS.get(account);
        if (c == null || !c.date.equals(today))
        {
            c = new DailyCounter(today);
            LIMITS.put(account, c);
        }
        return c;
    }

    private String getToday()
    {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private String formatLine(String account, String charName, String option, String message)
    {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(date).append("]");
        sb.append(" | Account: ").append(account);
        sb.append(" | Char: ").append(charName);
        sb.append(" | Option: ").append(option);
        sb.append(" | Message: ").append(message.isEmpty() ? "N/A" : message);
        return sb.toString();
    }

    private boolean appendLine(String line)
    {
        synchronized (FILE_LOCK)
        {
            try
            {
                File dir = new File(SUPPORT_DIR);
                if (!dir.exists())
                    dir.mkdirs();
                try (PrintWriter out = new PrintWriter(new FileWriter(SUPPORT_FILE, true)))
                {
                    out.println(line);
                }
                return true;
            }
            catch (Exception e)
            {
                if (DEBUG)
                    debug("AppendLine error: " + e.getMessage());
                return false;
            }
        }
    }

    private void debug(String s)
    {
        if (!DEBUG) return;
        synchronized (FILE_LOCK)
        {
            try
            {
                File dir = new File(SUPPORT_DIR);
                if (!dir.exists())
                    dir.mkdirs();
                try (PrintWriter out = new PrintWriter(new FileWriter(DEBUG_FILE, true)))
                {
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    out.println("[" + date + "] " + s);
                }
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private static class DailyCounter
    {
        public final String date;
        private int bug, donate, other;

        public DailyCounter(String date) { this.date = date; }

        public int get(String option)
        {
            switch (option)
            {
                case "bug": return bug;
                case "donate": return donate;
                case "other": return other;
                default: return 0;
            }
        }

        public void inc(String option)
        {
            switch (option)
            {
                case "bug": bug++; break;
                case "donate": donate++; break;
                case "other": other++; break;
            }
        }

        public long hoursUntilReset()
        {
            try
            {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date d = sdf.parse(date);
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                cal.add(Calendar.DATE, 1);
                long millis = cal.getTimeInMillis() - System.currentTimeMillis();
                return Math.max(1, millis / (1000 * 60 * 60));
            }
            catch (Exception e)
            {
                return 24;
            }
        }
    }
}
