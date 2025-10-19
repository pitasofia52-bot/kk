package net.sf.l2j.gameserver.custom.autofarm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import net.sf.l2j.commons.concurrent.ThreadPool;

public final class AutoFarmManager {
    private static final AutoFarmManager INSTANCE = new AutoFarmManager();
    public static AutoFarmManager getInstance() { return INSTANCE; }

    private final Map<Integer, Future<?>> tasks = new ConcurrentHashMap<>();

    public boolean isRunning(int objectId) {
        return tasks.containsKey(objectId);
    }

    public synchronized void start(int objectId) {
        stop(objectId);
        final AutoFarmTask task = new AutoFarmTask(objectId);
        Future<?> f = ThreadPool.scheduleAtFixedRate(task, 1000, 1000); // tick κάθε 1s
        tasks.put(objectId, f);
    }

    public synchronized void stop(int objectId) {
        final Future<?> f = tasks.remove(objectId);
        if (f != null) {
            f.cancel(true);
        }
    }

    // Χρησιμοποιείται από το task για να αυτο-τερματίσει καθαρά
    void stopInternal(int objectId) {
        stop(objectId);
    }
}