package net.sf.l2j.gameserver.management;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.gameserver.network.L2GameClient;

/**
 * Scheduled monitor that runs every 5 minutes and enforces a maximum of 2 distinct accounts per IP.
 * - Reads whitelist from config/ip.txt (lines: # comment, one account name per line).
 * - Supports an ENABLED flag in the same file: ENABLED=true|false (case-insensitive).
 * - Uses ActiveClients snapshot to group by IP and decide which accounts to kick.
 *
 * NOTE: This class calls client.closeNow() to perform safe disconnects (lets existing cleanup run).
 */
public final class ConnectionMonitor implements Runnable
{
    private static final Logger _log = Logger.getLogger(ConnectionMonitor.class.getName());
    private static final Path CONFIG_PATH = Paths.get("config", "ip.txt");
    private static final long DEFAULT_INTERVAL_MS = 300_000L; // 5 minutes

    public ConnectionMonitor()
    {
    }

    /**
     * Schedule the monitor on the ThreadPool.
     * Call from server bootstrap: ThreadPool.scheduleAtFixedRate(new ConnectionMonitor(), delay, DEFAULT_INTERVAL_MS);
     */
    public static void scheduleAtFixedRate(long initialDelay)
    {
        ThreadPool.scheduleAtFixedRate(new ConnectionMonitor(), initialDelay, DEFAULT_INTERVAL_MS);
    }

    @Override
    public void run()
    {
        try
        {
            // Read enabled flag first: if monitoring is disabled in config, skip full run.
            if (!isMonitorEnabled())
            {
                _log.fine("ConnectionMonitor is disabled via config/ip.txt (ENABLED flag). Skipping run().");
                return;
            }

            final Set<String> whitelistAccounts = new HashSet<>();
            readWhitelist(whitelistAccounts);

            final Map<String, Set<L2GameClient>> grouped = ActiveClients.getSnapshotGroupedByIp();

            for (Map.Entry<String, Set<L2GameClient>> entry : grouped.entrySet())
            {
                final String ip = entry.getKey();
                final Set<L2GameClient> clients = entry.getValue();

                if (ip == null || clients == null || clients.isEmpty())
                    continue;

                // IP whitelist removed: only account-based whitelist is used.

                // Group clients by account
                final Map<String, List<L2GameClient>> byAccount = new HashMap<>();
                for (L2GameClient client : clients)
                {
                    try
                    {
                        final String acct = client.getAccountName() == null ? "" : client.getAccountName();
                        byAccount.computeIfAbsent(acct, k -> new ArrayList<>()).add(client);
                    }
                    catch (Exception e)
                    {
                        // defensive: ignore problematic client
                    }
                }

                // Remove or mark whitelisted accounts: they are always kept
                final Set<String> whitelistedPresentAccounts = byAccount.keySet().stream()
                    .filter(whitelistAccounts::contains)
                    .collect(Collectors.toSet());

                // Count distinct accounts excluding whitelisted ones
                final Set<String> nonWhitelistedAccounts = byAccount.keySet().stream()
                    .filter(acc -> !whitelistAccounts.contains(acc))
                    .collect(Collectors.toSet());

                if (nonWhitelistedAccounts.size() <= 2)
                    continue; // within limit

                // Prepare account infos for ranking (prefer accounts with IN_GAME, then older connectionStartTime)
                class AccInfo
                {
                    final String acc;
                    final boolean hasInGame;
                    final long earliestConnection;

                    AccInfo(String acc, boolean hasInGame, long earliestConnection)
                    {
                        this.acc = acc;
                        this.hasInGame = hasInGame;
                        this.earliestConnection = earliestConnection;
                    }
                }

                final List<AccInfo> infos = new ArrayList<>();
                for (String acc : nonWhitelistedAccounts)
                {
                    boolean hasInGame = false;
                    long earliest = Long.MAX_VALUE;
                    final List<L2GameClient> list = byAccount.get(acc);
                    if (list != null)
                    {
                        for (L2GameClient c : list)
                        {
                            try
                            {
                                if (c.getState() != null && c.getState().toString().equals("IN_GAME"))
                                    hasInGame = true;
                                final long cs = c.getConnectionStartTime();
                                if (cs > 0 && cs < earliest)
                                    earliest = cs;
                            }
                            catch (Exception e)
                            {
                                // ignore
                            }
                        }
                    }
                    if (earliest == Long.MAX_VALUE)
                        earliest = System.currentTimeMillis();
                    infos.add(new AccInfo(acc, hasInGame, earliest));
                }

                // Sort: accounts with IN_GAME first (desc), then by earliestConnection asc (older connections first)
                infos.sort(Comparator.<AccInfo>comparingInt(i -> i.hasInGame ? 0 : 1)
                    .thenComparingLong(i -> i.earliestConnection));

                // Build keep set: always add whitelisted present accounts first, then fill up to 2 with top-ranked non-whitelisted
                final Set<String> keepAccounts = new HashSet<>(whitelistedPresentAccounts);
                for (AccInfo info : infos)
                {
                    if (keepAccounts.size() >= 2)
                        break;
                    keepAccounts.add(info.acc);
                }

                // Kick all clients whose account is not in keepAccounts (and not whitelisted)
                for (Map.Entry<String, List<L2GameClient>> accEntry : byAccount.entrySet())
                {
                    final String acc = accEntry.getKey();
                    if (keepAccounts.contains(acc) || whitelistAccounts.contains(acc))
                        continue;

                    final List<L2GameClient> toKick = accEntry.getValue();
                    for (L2GameClient client : toKick)
                    {
                        try
                        {
                            _log.info("ConnectionMonitor: Kicking account '" + acc + "' from IP " + ip + " (client=" + client.toString() + ")");
                            client.closeNow();
                        }
                        catch (Exception e)
                        {
                            _log.log(Level.WARNING, "ConnectionMonitor: Failed to kick client for account " + acc + " at IP " + ip, e);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            _log.log(Level.WARNING, "ConnectionMonitor: unexpected error during run()", e);
        }
    }

    /**
     * Reads whitelist accounts: every non-comment non-empty line is treated as account name.
     * Lines starting with "ENABLED=" are ignored here (handled by isMonitorEnabled()).
     */
    private void readWhitelist(Set<String> accounts)
    {
        if (!Files.exists(CONFIG_PATH))
            return;

        try
        {
            final List<String> lines = Files.readAllLines(CONFIG_PATH);
            for (String raw : lines)
            {
                if (raw == null)
                    continue;
                final String s = raw.trim();
                if (s.isEmpty() || s.startsWith("#"))
                    continue;
                if (s.toUpperCase().startsWith("ENABLED="))
                    continue; // skip flag line here
                // Treat every other non-comment line as an account name to whitelist.
                accounts.add(s);
            }
        }
        catch (IOException e)
        {
            _log.log(Level.WARNING, "ConnectionMonitor: failed to read whitelist file: " + CONFIG_PATH, e);
        }
    }

    /**
     * Read ENABLED flag from config file.
     * Returns true if monitoring is enabled (default true).
     * Accepted false values: "0", "false", "off" (case-insensitive).
     * Accepted true values: "1", "true", "on" (case-insensitive) or absence of the flag.
     */
    private boolean isMonitorEnabled()
    {
        if (!Files.exists(CONFIG_PATH))
            return true; // default: enabled

        try
        {
            final List<String> lines = Files.readAllLines(CONFIG_PATH);
            for (String raw : lines)
            {
                if (raw == null)
                    continue;
                final String s = raw.trim();
                if (s.isEmpty() || s.startsWith("#"))
                    continue;
                if (s.toUpperCase().startsWith("ENABLED="))
                {
                    final String val = s.substring("ENABLED=".length()).trim().toLowerCase();
                    if (val.equals("0") || val.equals("false") || val.equals("off"))
                        return false;
                    if (val.equals("1") || val.equals("true") || val.equals("on"))
                        return true;
                    // Unrecognized value: default to enabled
                    return true;
                }
            }
        }
        catch (IOException e)
        {
            _log.log(Level.WARNING, "ConnectionMonitor: failed to read enabled flag from: " + CONFIG_PATH, e);
        }
        return true; // default
    }
}