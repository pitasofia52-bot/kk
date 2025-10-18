package net.sf.l2j.gameserver.management;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.gameserver.network.L2GameClient;

/**
 * Registry of active L2GameClient instances.
 * - Thread-safe.
 * - Provides snapshot views and grouping helpers by IP/account.
 *
 * NOTE: This class only stores references; clients must register/unregister themselves
 *       at appropriate lifecycle points (constructor / cleanup).
 */
public final class ActiveClients
{
    // Using ConcurrentHashMap as a ConcurrentSet (value is Boolean.TRUE)
    private static final ConcurrentHashMap<L2GameClient, Boolean> CLIENTS = new ConcurrentHashMap<>();

    private ActiveClients() {}

    /**
     * Register a client as active.
     */
    public static void register(final L2GameClient client)
    {
        if (client == null)
            return;
        CLIENTS.put(client, Boolean.TRUE);
    }

    /**
     * Unregister a client.
     */
    public static void unregister(final L2GameClient client)
    {
        if (client == null)
            return;
        CLIENTS.remove(client);
    }

    /**
     * Return a snapshot (copy) of active clients.
     * The returned set is independent of the internal registry.
     */
    public static Set<L2GameClient> getAllClientsSnapshot()
    {
        return new HashSet<>(CLIENTS.keySet());
    }

    /**
     * Group snapshot of clients by IP address (string).
     * Clients without an InetAddress are ignored.
     * The returned map and sets are independent copies.
     */
    public static Map<String, Set<L2GameClient>> getSnapshotGroupedByIp()
    {
        final Map<String, Set<L2GameClient>> map = new HashMap<>();
        for (L2GameClient client : CLIENTS.keySet())
        {
            try
            {
                final InetAddress addr = client.getConnection().getInetAddress();
                if (addr == null)
                    continue;
                final String ip = addr.getHostAddress();
                map.computeIfAbsent(ip, k -> new HashSet<>()).add(client);
            }
            catch (Exception e)
            {
                // Defensive: ignore clients with issues retrieving address
            }
        }
        return map;
    }

    /**
     * Return distinct account names connected from the given IP (snapshot).
     */
    public static Set<String> getDistinctAccountNamesForIp(final String ip)
    {
        if (ip == null)
            return Collections.emptySet();

        return CLIENTS.keySet().stream()
            .filter(c -> {
                try {
                    final InetAddress a = c.getConnection().getInetAddress();
                    return a != null && ip.equals(a.getHostAddress());
                } catch (Exception e) {
                    return false;
                }
            })
            .map(c -> {
                try { return c.getAccountName(); } catch (Exception e) { return null; }
            })
            .filter(s -> s != null && !s.isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * Return clients for a given IP (snapshot).
     */
    public static Set<L2GameClient> getClientsForIp(final String ip)
    {
        if (ip == null)
            return Collections.emptySet();

        final Set<L2GameClient> result = new HashSet<>();
        for (L2GameClient client : CLIENTS.keySet())
        {
            try
            {
                final InetAddress addr = client.getConnection().getInetAddress();
                if (addr == null)
                    continue;
                if (ip.equals(addr.getHostAddress()))
                    result.add(client);
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        return result;
    }

    /**
     * Return number of active clients (snapshot size).
     */
    public static int size()
    {
        return CLIENTS.size();
    }
}