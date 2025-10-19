package net.sf.l2j.gameserver.util;

import java.util.logging.Logger;
import net.sf.l2j.commons.concurrent.ThreadPool;

public class MemoryMonitor {

    private static final Logger _log = Logger.getLogger(MemoryMonitor.class.getName());

    public static void init() {
        ThreadPool.scheduleAtFixedRate(() -> {
            logMemoryUsage();
        }, 0, 5 * 60 * 60 * 1000L); // κάθε 5 ώρες
    }

    private static void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        _log.info(String.format(
            "[Memory Monitor] Used: %.2f MB | Free: %.2f MB | Total: %.2f MB | Max: %.2f MB",
            usedMemory / 1024.0 / 1024.0,
            freeMemory / 1024.0 / 1024.0,
            totalMemory / 1024.0 / 1024.0,
            maxMemory / 1024.0 / 1024.0
        ));
    }
}
