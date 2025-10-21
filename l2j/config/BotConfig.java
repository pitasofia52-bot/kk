package net.sf.l2j.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * BotConfig
 * - Διαβάζει config/bot.properties
 * - Υποστηρίζει mode=single ή mode=multi
 *   * single : κρατάει ΜΟΝΟ το πρώτο ενεργό σύμφωνα με προτεραιότητα (dre, alt, com, compvp, dyn)
 *   * multi  : εκκινεί ΟΛΑ τα enabled subsystems
 * - Παρέχει reload() (ώστε μελλοντικά να γίνει admin command).
 * - Δεν ρίχνει exception αν λείπει το αρχείο – δουλεύει με defaults (όλα off).
 */
public final class BotConfig {
    private static final Logger LOG = Logger.getLogger(BotConfig.class.getName());
    private static boolean LOADED = false;

    public static boolean BOTS_ENABLED = false;

    public static boolean ENABLE_ALT = false;
    public static boolean ENABLE_COM = false;
    public static boolean ENABLE_COMPVP = false;
    public static boolean ENABLE_DRE = false;
    public static boolean ENABLE_DYN = false;

    // mode = "single" (default) ή "multi"
    private static String MODE = "single";

    private BotConfig() {}

    private static File resolveConfigFile() {
        return new File("config/bot.properties");
    }

    public static synchronized void load() {
        if (LOADED) return;

        final File file = resolveConfigFile();
        final Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            p.load(fis);
        } catch (Exception e) {
            LOG.warning("BotConfig: bot.properties not found or unreadable (" + file.getAbsolutePath() + "). Using defaults. Error: " + e.getMessage());
        }

        // Global switch
        BOTS_ENABLED = getBool(p, "enabled", false);

        // Individual subsystems
        ENABLE_ALT    = getBool(p, "enable_alt",    false);
        ENABLE_COM    = getBool(p, "enable_com",    false);
        ENABLE_COMPVP = getBool(p, "enable_compvp", false);
        ENABLE_DRE    = getBool(p, "enable_dre",    false);
        ENABLE_DYN    = getBool(p, "enable_dyn",    false);

        MODE = p.getProperty("mode", "single").trim().toLowerCase();
        if (!MODE.equals("single") && !MODE.equals("multi")) {
            LOG.warning("BotConfig: invalid mode='" + MODE + "', falling back to 'single'.");
            MODE = "single";
        }

        if (MODE.equals("single")) {
            enforceSingleSystemPriority();
        }

        LOADED = true;

        LOG.info("BotConfig loaded from: " + file.getAbsolutePath()
                + " mode=" + MODE
                + " globalEnabled=" + BOTS_ENABLED);
        LOG.info("BotConfig flags: alt=" + ENABLE_ALT
                + " com=" + ENABLE_COM
                + " compvp=" + ENABLE_COMPVP
                + " dre=" + ENABLE_DRE
                + " dyn=" + ENABLE_DYN);

        if (MODE.equals("multi")) {
            LOG.info("BotConfig: MULTI mode -> all enabled subsystems will start.");
        } else {
            LOG.info("BotConfig: SINGLE mode -> only first enabled (priority order) started: "
                    + getEnabledSystemsList());
        }
    }

    public static synchronized void reload() {
        LOADED = false;
        load();
    }

    private static boolean getBool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        return Boolean.parseBoolean(v.trim());
    }

    /**
     * SINGLE mode: κρατάει ΜΟΝΟ το πρώτο true με σειρά προτεραιότητας.
     */
    private static void enforceSingleSystemPriority() {
        // σειρά προτεραιότητας (μπορείς να την αλλάξεις)
        if (ENABLE_DRE) {
            ENABLE_ALT = ENABLE_COM = ENABLE_COMPVP = ENABLE_DYN = false;
            return;
        }
        if (ENABLE_ALT) {
            ENABLE_COM = ENABLE_COMPVP = ENABLE_DRE = ENABLE_DYN = false;
            return;
        }
        if (ENABLE_COM) {
            ENABLE_ALT = ENABLE_COMPVP = ENABLE_DRE = ENABLE_DYN = false;
            return;
        }
        if (ENABLE_COMPVP) {
            ENABLE_ALT = ENABLE_COM = ENABLE_DRE = ENABLE_DYN = false;
            return;
        }
        if (ENABLE_DYN) {
            ENABLE_ALT = ENABLE_COM = ENABLE_COMPVP = ENABLE_DRE = false;
        }
    }

    public static boolean isMultiMode() {
        return MODE.equals("multi");
    }

    /**
     * Λίστα ενεργών (μετά το trimming αν single).
     */
    public static List<String> getEnabledSystemsList() {
        List<String> l = new ArrayList<>();
        if (ENABLE_ALT)    l.add("alt");
        if (ENABLE_COM)    l.add("com");
        if (ENABLE_COMPVP) l.add("compvp");
        if (ENABLE_DRE)    l.add("dre");
        if (ENABLE_DYN)    l.add("dyn");
        return l;
    }

    /**
     * Για legacy χρήση που παίρνει “ένα”.
     */
    public static String pickSelectedSystem() {
        List<String> list = getEnabledSystemsList();
        return list.isEmpty() ? "" : list.get(0);
    }
}