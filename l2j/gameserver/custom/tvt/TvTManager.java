package net.sf.l2j.gameserver.custom.tvt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.util.Broadcast;
import net.sf.l2j.gameserver.data.ItemTable;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.data.NpcTable;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.data.xml.MapRegionData.TeleportType;

public final class TvTManager
{
	private static final Logger LOG = Logger.getLogger(TvTManager.class.getName());
	
	private TvTState state = TvTState.IDLE;
	
	private final Set<String> schedules = ConcurrentHashMap.newKeySet();
	private final Set<Integer> registeredPlayers = ConcurrentHashMap.newKeySet();
	
	private final List<TvTPlayerData> participants = new CopyOnWriteArrayList<>();
	private final Map<Integer, TvTPlayerData> participantMap = new ConcurrentHashMap<>();
	
	private final List<ScheduledFuture<?>> dailyScheduleFutures = new CopyOnWriteArrayList<>();
	
	private final Map<String, ScheduledEntry> scheduledEntries = new ConcurrentHashMap<>();
	private ScheduledFuture<?> watchdogTask;
	private static final AtomicInteger SCHEDULE_ID_GEN = new AtomicInteger(1);
	
	private ScheduledFuture<?> registrationCloseTask;
	private ScheduledFuture<?> eventEndTask;
	
	private long registrationEndTime = 0L;
    private long eventEndTime = 0L;
	
	private final Map<String, Integer> ipRegCounts = new ConcurrentHashMap<>();
	private final Set<Integer> spawnProtectedPlayers = ConcurrentHashMap.newKeySet();
	
	private volatile int team1KillsCached = 0;
	private volatile int team2KillsCached = 0;
	
	private L2Spawn regSpawn;
	private final List<L2Spawn> bufferSpawns = new CopyOnWriteArrayList<>();
	
	private TvTManager() {}
	public static TvTManager getInstance() { return SingletonHolder.INSTANCE; }
	
	/* ===================== INIT / SCHEDULING ===================== */
	
	public void init()
	{
		try
		{
			cleanup();
			cancelDailySchedules();
			schedules.clear();
			schedules.addAll(TvTConfig.SCHEDULES);
			LOG.info("TvTManager: Init schedules = " + schedules);
			for (String s : schedules)
				LOG.info("TvTManager: Schedule to be programmed: " + s);
			if (!schedules.isEmpty())
				scheduleDailyTasks();
			else
				LOG.info("TvTManager: No schedules defined (IDLE).");
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "TvTManager init error: " + e.getMessage(), e);
		}
	}
	
	private void scheduleDailyTasks()
	{
		LOG.info("TvTManager: scheduleDailyTasks() START (will cancel old & reschedule new).");
		cancelDailySchedules();
		scheduledEntries.clear();
		for (String s : schedules)
		{
			long delay = computeDelayMillis(s);
			if (delay <= 0)
			{
				LOG.warning("TvTManager: Invalid schedule '" + s + "', skipped.");
				continue;
			}
			long fireAt = System.currentTimeMillis() + delay;
			int id = SCHEDULE_ID_GEN.getAndIncrement();
			ScheduledEntry entry = new ScheduledEntry(id, s, fireAt, System.currentTimeMillis());
			scheduledEntries.put(s, entry);
			ScheduledFuture<?> f = ThreadPool.schedule(() -> {
				entry.firedAt = System.currentTimeMillis();
				entry.fired = true;
				try { openRegistration(s); }
				catch (Throwable t) { LOG.log(Level.WARNING, "openRegistration task error", t); }
			}, delay);
			entry.future = f;
			dailyScheduleFutures.add(f);
		}
		ScheduledFuture<?> cycle = ThreadPool.schedule(this::scheduleDailyTasks, 24L * 60 * 60 * 1000L);
		dailyScheduleFutures.add(cycle);
		startWatchdog();
	}
	
	private long computeDelayMillis(String hhmm)
	{
		try
		{
			StringTokenizer st = new StringTokenizer(hhmm, ":");
			int h = Integer.parseInt(st.nextToken());
			int m = Integer.parseInt(st.nextToken());
			if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
			Calendar now = Calendar.getInstance();
			Calendar target = (Calendar) now.clone();
			target.set(Calendar.HOUR_OF_DAY, h);
			target.set(Calendar.MINUTE, m);
			target.set(Calendar.SECOND, 0);
			target.set(Calendar.MILLISECOND, 0);
			if (target.getTimeInMillis() <= now.getTimeInMillis())
				target.add(Calendar.DAY_OF_MONTH, 1);
			return target.getTimeInMillis() - now.getTimeInMillis();
		}
		catch (Exception e)
		{
			return -1;
		}
	}
	
	private void cancelDailySchedules()
	{
		for (ScheduledFuture<?> f : dailyScheduleFutures)
			if (f != null) f.cancel(false);
		dailyScheduleFutures.clear();
	}
	
	/* ===================== REGISTRATION ===================== */
	
	private synchronized void openRegistration(String label)
	{
		if (!TvTConfig.ENABLED) return;
		if (state != TvTState.IDLE) return;
		if (!spawnsConfigured())
		{
			Broadcast.announceToOnlinePlayers("[TvT] Cannot open registration: spawn points invalid.");
			return;
		}
		
		state = TvTState.REGISTRATION;
		registeredPlayers.clear();
		participants.clear();
		participantMap.clear();
		ipRegCounts.clear();
		registrationEndTime = System.currentTimeMillis() + TvTConfig.REGISTRATION_TIME * 1000L;
		
		spawnRegistrationNpc();
		
		Broadcast.announceToOnlinePlayers("[TvT] Registration OPEN (" + label + ") for " + TvTConfig.REGISTRATION_TIME + "s. Use .tvtjoin");
		
		registrationCloseTask = ThreadPool.schedule(() -> {
			try { closeRegistration(); } catch (Throwable t) { LOG.log(Level.WARNING, "closeRegistration task error", t); }
		}, TvTConfig.REGISTRATION_TIME * 1000L);
	}
	
	private boolean spawnsConfigured()
	{
		if (TvTConfig.TEAM1_SPAWN_X == 0 && TvTConfig.TEAM1_SPAWN_Y == 0 && TvTConfig.TEAM1_SPAWN_Z == 0) return false;
		if (TvTConfig.TEAM2_SPAWN_X == 0 && TvTConfig.TEAM2_SPAWN_Y == 0 && TvTConfig.TEAM2_SPAWN_Z == 0) return false;
		return true;
	}
	
	private synchronized void closeRegistration()
	{
		if (state != TvTState.REGISTRATION)
			return;
		int count = registeredPlayers.size();
		if (count < TvTConfig.MIN_PLAYERS)
		{
			Broadcast.announceToOnlinePlayers("[TvT] Registration CLOSED. Not enough players (" + count + "/" + TvTConfig.MIN_PLAYERS + ").");
			cleanup();
			return;
		}
		startEvent();
	}
	
	public boolean tryRegister(Player player)
	{
		if (player == null) return false;
		if (state != TvTState.REGISTRATION) { player.sendMessage("TvT: Not in registration phase."); return false; }
		if (!checkLevelRange(player)) { player.sendMessage("TvT: Level not allowed."); return false; }
		if (!TvTConfig.ALLOW_KARMA_PLAYERS && player.getKarma() > 0) { player.sendMessage("Because you have karma you are not allowed to enter TvT."); return false; }
		if (TvTConfig.MAX_PLAYERS > 0 && registeredPlayers.size() >= TvTConfig.MAX_PLAYERS) { player.sendMessage("TvT: Max players reached."); return false; }
		String ip = player.getIP(); if (ip == null) ip = "0.0.0.0";
		if (TvTConfig.MAX_WINDOWS_PER_IP > 0)
		{
			int already = ipRegCounts.getOrDefault(ip, 0);
			if (already >= TvTConfig.MAX_WINDOWS_PER_IP) { player.sendMessage("TvT: IP registration limit reached."); return false; }
		}
		if (!registeredPlayers.add(player.getObjectId())) { player.sendMessage("TvT: Already registered."); return false; }
		ipRegCounts.merge(ip, 1, Integer::sum);
		player.sendMessage("TvT: Registered. Total=" + registeredPlayers.size());
		return true;
	}
	
	public boolean tryUnregister(Player player)
	{
		if (player == null) return false;
		if (state != TvTState.REGISTRATION) { player.sendMessage("TvT: Not in registration phase."); return false; }
		if (registeredPlayers.remove(player.getObjectId()))
		{
			decrementIpReg(player.getIP());
			player.sendMessage("TvT: Unregistered. Total=" + registeredPlayers.size());
			return true;
		}
		player.sendMessage("TvT: You were not registered.");
		return false;
	}
	
	private void decrementIpReg(String ip)
	{
		if (ip == null) return;
		ipRegCounts.computeIfPresent(ip, (k, v) -> v <= 1 ? null : v - 1);
	}
	
	private boolean checkLevelRange(Player p)
	{
		if (TvTConfig.MIN_LEVEL > 0 && p.getLevel() < TvTConfig.MIN_LEVEL) return false;
		if (TvTConfig.MAX_LEVEL > 0 && p.getLevel() > TvTConfig.MAX_LEVEL) return false;
		return true;
	}
	
	/* ===================== EVENT START ===================== */
	
	private void startEvent()
	{
		despawnRegistrationNpc();
		
		if (isAnySiegeInProgress())
		{
			Broadcast.announceToOnlinePlayers("[TvT] A siege is currently in progress. Event aborted.");
			cleanup();
			state = TvTState.IDLE;
			return;
		}
		
		state = TvTState.RUNNING;
		registrationEndTime = 0L;
		team1KillsCached = 0;
		team2KillsCached = 0;
		
		List<Player> list = new ArrayList<>();
		for (int objId : registeredPlayers)
		{
			Player p = World.getInstance().getPlayer(objId);
			if (p != null) list.add(p);
		}
		if (list.size() < TvTConfig.MIN_PLAYERS)
		{
			Broadcast.announceToOnlinePlayers("[TvT] Not enough players present anymore. Canceled.");
			cleanup();
			return;
		}
		
		list.sort(Comparator.comparingInt(Player::getObjectId));
		
		int team = 1;
		for (Player p : list)
		{
			try
			{
				Location originalLoc = new Location(p.getX(), p.getY(), p.getZ());
				TvTPlayerData data = new TvTPlayerData(
					p.getObjectId(),
					p.getName(),
					team,
					originalLoc,
					p.getTitle(),
					p.getAppearance().getTitleColor(),
					p.getAppearance().getNameColor(),
					p.getIP()
				);
				participants.add(data);
				participantMap.put(p.getObjectId(), data);
				p.setTvTData(data);
				
				applyTeamVisuals(p, data);
				teleportToTeamSpawn(p, team);
				
				if (TvTConfig.SHOW_PERSONAL_STATUS_ON_JOIN)
					p.sendMessage("[TvT] Joined Team " + team + ". Good luck!");
				
				team = (team == 1) ? 2 : 1;
			}
			catch (Exception ex)
			{
				LOG.log(Level.WARNING, "Prepare participant fail objId=" + p.getObjectId(), ex);
			}
		}
		
		Broadcast.announceToOnlinePlayers("[TvT] Event STARTED. Participants=" + participants.size());
		
		spawnBuffers();
		
		eventEndTime = System.currentTimeMillis() + TvTConfig.EVENT_TIME * 1000L;
		eventEndTask = ThreadPool.schedule(() -> {
			try { endEvent(); } catch (Throwable t) { LOG.log(Level.WARNING, "endEvent task error", t); }
		}, TvTConfig.EVENT_TIME * 1000L);
	}
	
	private void applyTeamVisuals(Player p, TvTPlayerData data)
	{
		try
		{
			// 1) Εφαρμογή χρώματος ονόματος ανά ομάδα.
			if (data.getTeam() == 1)
				p.getAppearance().setNameColor(TvTConfig.NAME_COLOR_TEAM1);
			else
				p.getAppearance().setNameColor(TvTConfig.NAME_COLOR_TEAM2);
			
			// Δεν αλλάζουμε τίτλο / title color σύμφωνα με την απαίτηση.
			
			// 2) (Προαιρετικά) Auras μόνο αν έχουν μείνει ενεργά στο config.
			if (TvTConfig.USE_TEAM_AURAS)
			{
				int mask = (data.getTeam() == 1) ? TvTConfig.TEAM_AURA_RED_MASK : TvTConfig.TEAM_AURA_BLUE_MASK;
				if (mask != 0)
					p.startAbnormalEffect(mask);
				else
				{
					if (data.getTeam() == 1 && TvTConfig.TEAM_AURA_RED_EFFECT != null)
						p.startAbnormalEffect(TvTConfig.TEAM_AURA_RED_EFFECT.getMask());
					else if (data.getTeam() == 2 && TvTConfig.TEAM_AURA_BLUE_EFFECT != null)
						p.startAbnormalEffect(TvTConfig.TEAM_AURA_BLUE_EFFECT.getMask());
				}
			}
			
			p.broadcastUserInfo();
		}
		catch (Exception e)
		{
			LOG.warning("applyTeamVisuals fail for " + p.getName() + ": " + e.getMessage());
		}
	}
	
	private void teleportToTeamSpawn(Player p, int team)
	{
		if (team == 1)
			p.teleToLocation(TvTConfig.TEAM1_SPAWN_X, TvTConfig.TEAM1_SPAWN_Y, TvTConfig.TEAM1_SPAWN_Z, TvTConfig.TEAM1_SPAWN_H);
		else
			p.teleToLocation(TvTConfig.TEAM2_SPAWN_X, TvTConfig.TEAM2_SPAWN_Y, TvTConfig.TEAM2_SPAWN_Z, TvTConfig.TEAM2_SPAWN_H);
	}
	
	/* ===================== KILL / DEATH ===================== */
	
	public void onKill(Player killer, Player victim)
	{
		if (killer == null || victim == null) return;
		if (state != TvTState.RUNNING) return;
		if (killer == victim) return;
		
		TvTPlayerData kd = participantMap.get(killer.getObjectId());
		TvTPlayerData vd = participantMap.get(victim.getObjectId());
		if (kd == null || vd == null) return;
		if (kd.getTeam() == vd.getTeam()) return;
		
		kd.addKill();
		vd.addDeath();
		if (kd.getTeam() == 1) team1KillsCached++; else team2KillsCached++;
		
		checkEarlyEnd();
		
		ThreadPool.schedule(() -> {
			try
			{
				if (state != TvTState.RUNNING) return;
				if (victim.isInTvT() && victim.isDead())
				{
					victim.doRevive();
					victim.setCurrentHp(victim.getMaxHp());
					victim.setCurrentMp(victim.getMaxMp());
					victim.setCurrentCp(victim.getMaxCp());
					teleportToTeamSpawn(victim, vd.getTeam());
					applySpawnProtection(victim);
					victim.broadcastUserInfo();
				}
			}
			catch (Exception ex)
			{
				LOG.log(Level.WARNING, "Revive task error: " + ex.getMessage(), ex);
			}
		}, TvTConfig.RESPAWN_DELAY * 1000L);
	}
	
	public void onPlayerDeath(Player victim, Player killer)
	{
		if (state != TvTState.RUNNING) return;
		if (victim == null) return;
		TvTPlayerData data = participantMap.get(victim.getObjectId());
		if (data == null) return;
		
		ThreadPool.schedule(() -> {
			try
			{
				if (state != TvTState.RUNNING) return;
				if (victim.isInTvT() && victim.isDead())
				{
					victim.doRevive();
					victim.setCurrentHp(victim.getMaxHp());
					victim.setCurrentMp(victim.getMaxMp());
					victim.setCurrentCp(victim.getMaxCp());
					teleportToTeamSpawn(victim, data.getTeam());
					applySpawnProtection(victim);
					victim.broadcastUserInfo();
				}
			}
			catch (Exception ex)
			{
				LOG.log(Level.WARNING, "Scheduled revive error: " + ex.getMessage(), ex);
			}
		}, TvTConfig.RESPAWN_DELAY * 1000L);
	}
	
	private void applySpawnProtection(Player p)
	{
		if (TvTConfig.SPAWN_PROTECTION_MS <= 0) return;
		spawnProtectedPlayers.add(p.getObjectId());
		try { p.setIsInvul(true); } catch (Exception ignored) {}
		ThreadPool.schedule(() -> {
			spawnProtectedPlayers.remove(p.getObjectId());
			try { if (p != null && p.isOnline()) p.setIsInvul(false); } catch (Exception ignored) {}
		}, TvTConfig.SPAWN_PROTECTION_MS);
	}
	
	private void checkEarlyEnd()
	{
		if (state != TvTState.RUNNING) return;
		boolean t1 = false, t2 = false;
		for (TvTPlayerData d : participants)
		{
			if (d.getTeam() == 1) t1 = true; else t2 = true;
			if (t1 && t2) break;
		}
		if (!t1 || !t2)
		{
			Broadcast.announceToOnlinePlayers("[TvT] One team empty. Ending early.");
			endEvent();
		}
	}
	
	public void onLogout(Player p)
	{
		if (p == null) return;
		if (state == TvTState.REGISTRATION)
		{
			if (registeredPlayers.remove(p.getObjectId()))
				decrementIpReg(p.getIP());
			return;
		}
		if (state == TvTState.RUNNING)
		{
			TvTPlayerData d = participantMap.get(p.getObjectId());
			if (d != null)
			{
				// Restore original visuals BEFORE removing data.
				restoreVisuals(p, d);
				
				d.addDeath();
				removeParticipant(p.getObjectId());
				clearAuras(p);
				p.setTvTData(null);
				try { p.teleToLocation(TeleportType.TOWN); } catch (Exception ignored) {}
				checkEarlyEnd();
			}
		}
	}
	
	private void removeParticipant(int objectId)
	{
		TvTPlayerData d = participantMap.remove(objectId);
		if (d != null) participants.remove(d);
	}
	
	/* ===================== END EVENT / REWARD ===================== */
	
	private synchronized void endEvent()
	{
		if (state != TvTState.RUNNING && state != TvTState.ENDING)
			return;
		state = TvTState.ENDING;
		
		if (eventEndTask != null)
		{
			eventEndTask.cancel(false);
			eventEndTask = null;
		}
		
		int team1Kills = team1KillsCached;
		int team2Kills = team2KillsCached;
		
		int winningTeam = 0;
		String msg;
		if (team1Kills > team2Kills) { winningTeam = 1; msg = "[TvT] Team 1 wins (" + team1Kills + " vs " + team2Kills + ")."; }
		else if (team2Kills > team1Kills) { winningTeam = 2; msg = "[TvT] Team 2 wins (" + team2Kills + " vs " + team1Kills + ")."; }
		else { msg = "[TvT] Tie (" + team1Kills + " - " + team2Kills + ")."; }
		Broadcast.announceToOnlinePlayers(msg);
		
		if (!participants.isEmpty())
		{
			StringBuilder sb = new StringBuilder("[TvT] Top5: ");
			List<TvTPlayerData> sorted = new ArrayList<>(participants);
			sorted.sort(Comparator.comparingInt(TvTPlayerData::getKills).reversed().thenComparingInt(TvTPlayerData::getDeaths));
			int c = 0;
			for (TvTPlayerData d : sorted)
			{
				if (c >= 5) break;
				sb.append(d.getName()).append("(T").append(d.getTeam())
				  .append(" K:").append(d.getKills())
				  .append(" D:").append(d.getDeaths()).append(") ");
				c++;
			}
			Broadcast.announceToOnlinePlayers(sb.toString());
		}
		
		announceMvp(team1Kills, team2Kills);
		rewardParticipants(winningTeam);
		
		for (TvTPlayerData d : participants)
		{
			Player p = World.getInstance().getPlayer(d.getObjectId());
			if (p != null) restorePlayer(p, d);
		}
		
		cleanup();
		state = TvTState.IDLE;
	}
	
	private void rewardParticipants(int winningTeam)
	{
		try
		{
			Map<String, TvTPlayerData> rewardedByIp = new ConcurrentHashMap<>();
			int winners = 0, losers = 0, ties = 0, deniedIp = 0;
			
			for (TvTPlayerData d : participants)
			{
				if (d.getKills() < TvTConfig.KILL_REWARD_MIN_KILLS)
					continue;
				Player p = World.getInstance().getPlayer(d.getObjectId());
				if (p == null) continue;
				
				boolean tie = (winningTeam == 0);
				boolean isWinner = !tie && d.getTeam() == winningTeam;
				boolean isLoser = !tie && !isWinner;
				
				boolean give = tie || isWinner || (isLoser && TvTConfig.REWARD_LOSER_ITEM > 0 && TvTConfig.REWARD_LOSER_AMOUNT > 0);
				if (!give) continue;
				
				String ip = d.getIp();
				if (ip == null) ip = "0.0.0.0";
				
				if (TvTConfig.OVER_IP_LIMIT_NO_REWARD && TvTConfig.MAX_WINDOWS_PER_IP > 0)
				{
					int same = 0;
					for (TvTPlayerData all : participants)
						if (ip.equals(all.getIp())) same++;
					if (same > TvTConfig.MAX_WINDOWS_PER_IP)
					{
						p.sendMessage("[TvT] Reward denied: IP over limit.");
						deniedIp++;
						continue;
					}
				}
				
				if (rewardedByIp.containsKey(ip))
				{
					p.sendMessage("[TvT] Another character on your IP already got reward.");
					continue;
				}
				
				int itemId;
				int amount;
				if (tie) { itemId = TvTConfig.REWARD_TIE_ITEM; amount = TvTConfig.REWARD_TIE_AMOUNT; ties++; }
				else if (isWinner) { itemId = TvTConfig.REWARD_WINNER_ITEM; amount = TvTConfig.REWARD_WINNER_AMOUNT; winners++; }
				else { itemId = TvTConfig.REWARD_LOSER_ITEM; amount = TvTConfig.REWARD_LOSER_AMOUNT; losers++; }
				
				if (itemId <= 0 || amount <= 0) continue;
				
				if (!TvTConfig.INVENTORY_FULL_DROPS_REWARD)
				{
					try
					{
						ItemInstance proto = ItemTable.getInstance().createDummyItem(itemId);
						if (proto != null && !canReceiveItem(p, proto, amount))
						{
							p.sendMessage("[TvT] Not enough capacity for reward.");
							continue;
						}
					}
					catch (Throwable ignored) {}
				}
				
				try
				{
					p.addItem("TvTReward", itemId, amount, p, true);
					rewardedByIp.put(ip, d);
				}
				catch (Exception addEx)
				{
					LOG.log(Level.WARNING, "Reward fail " + p.getName() + ": " + addEx.getMessage(), addEx);
				}
			}
			
			Broadcast.announceToOnlinePlayers("[TvT] Rewards: winners=" + winners + " losers=" + losers + " ties=" + ties + " deniedIp=" + deniedIp + " uniqueIp=" + rewardedByIp.size());
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "rewardParticipants error: " + e.getMessage(), e);
		}
	}
	
	private void restorePlayer(Player p, TvTPlayerData data)
	{
		try
		{
			if (p.isDead()) p.doRevive();
			// Restore original name/title colors & title.
			restoreVisuals(p, data);
			clearAuras(p);
			Location loc = data.getOriginalLoc();
			p.teleToLocation(loc.getX(), loc.getY(), loc.getZ(), 0);
			p.setTvTData(null);
			p.broadcastUserInfo();
		}
		catch (Exception e)
		{
			LOG.warning("restorePlayer fail " + p.getName() + ": " + e.getMessage());
		}
	}
	
	private void restoreVisuals(Player p, TvTPlayerData data)
	{
		try
		{
			p.getAppearance().setNameColor(data.getOriginalNameColor());
			p.getAppearance().setTitleColor(data.getOriginalTitleColor());
			p.setTitle(data.getOriginalTitle());
		}
		catch (Exception e)
		{
			LOG.warning("restoreVisuals fail " + p.getName() + ": " + e.getMessage());
		}
	}
	
	/* ===================== STATUS HELPERS ===================== */
	
	public String buildStatus(Player requester)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("=== TvT Status ===\n");
		sb.append("State: ").append(state).append('\n');
		switch (state)
		{
			case REGISTRATION:
				sb.append("Time left: ").append(getRegistrationRemainingSeconds()).append("s\n");
				sb.append("Registered: ").append(getRegisteredCount()).append('\n');
				break;
			case RUNNING:
				sb.append("Time left: ").append(getEventRemainingSeconds()).append("s\n");
				sb.append("Team1 Kills: ").append(team1KillsCached).append(" | Team2 Kills: ").append(team2KillsCached).append('\n');
				if (requester != null && requester.isInTvT())
				{
					TvTPlayerData pd = getPlayerData(requester);
					if (pd != null)
						sb.append("You: Team ").append(pd.getTeam()).append(" K:").append(pd.getKills()).append(" D:").append(pd.getDeaths()).append('\n');
				}
				break;
			default:
				sb.append("Next schedules: ").append(TvTConfig.SCHEDULES).append('\n');
		}
		return sb.toString();
	}
	
	public int getRegisteredCount() { return registeredPlayers.size(); }
	public boolean isRegistered(Player p) { return p != null && registeredPlayers.contains(p.getObjectId()); }
	public TvTPlayerData getPlayerData(Player p) { return (p == null) ? null : participantMap.get(p.getObjectId()); }
	public boolean isRunning() { return state == TvTState.RUNNING; }
	public TvTState getState() { return state; }
	public boolean isParticipant(Player p) { return p != null && participantMap.containsKey(p.getObjectId()); }
	
	public long getRegistrationRemainingSeconds()
	{
		if (state != TvTState.REGISTRATION || registrationEndTime == 0L) return 0;
		long diff = registrationEndTime - System.currentTimeMillis();
		return diff <= 0 ? 0 : diff / 1000L;
	}
	
	public long getEventRemainingSeconds()
	{
		if (state != TvTState.RUNNING || eventEndTime == 0L) return 0;
		long diff = eventEndTime - System.currentTimeMillis();
		return diff <= 0 ? 0 : diff / 1000L;
	}
	
	/* ===================== CLEANUP ===================== */
	
	private void cleanup()
	{
		try
		{
			if (registrationCloseTask != null) { registrationCloseTask.cancel(false); registrationCloseTask = null; }
			if (eventEndTask != null) { eventEndTask.cancel(false); eventEndTask = null; }
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "Task cancel error: " + e.getMessage(), e);
		}
		despawnRegistrationNpc();
		despawnBuffers();
		state = TvTState.IDLE;
		registeredPlayers.clear();
		participants.clear();
		participantMap.clear();
		ipRegCounts.clear();
		spawnProtectedPlayers.clear();
		registrationEndTime = 0L;
		eventEndTime = 0L;
		team1KillsCached = 0;
		team2KillsCached = 0;
		if (watchdogTask != null)
		{
			try { watchdogTask.cancel(false); } catch (Exception ignored) {}
			watchdogTask = null;
		}
	}
	
	private void clearAuras(Player p)
	{
		if (p == null) return;
		try
		{
			if (TvTConfig.TEAM_AURA_RED_MASK != 0)
				p.stopAbnormalEffect(TvTConfig.TEAM_AURA_RED_MASK);
			if (TvTConfig.TEAM_AURA_BLUE_MASK != 0)
				p.stopAbnormalEffect(TvTConfig.TEAM_AURA_BLUE_MASK);
			else
			{
				if (TvTConfig.TEAM_AURA_RED_EFFECT != null)
					p.stopAbnormalEffect(TvTConfig.TEAM_AURA_RED_EFFECT.getMask());
				if (TvTConfig.TEAM_AURA_BLUE_EFFECT != null)
					p.stopAbnormalEffect(TvTConfig.TEAM_AURA_BLUE_EFFECT.getMask());
			}
		}
		catch (Exception ignored) {}
	}
	
	private void announceMvp(int team1Kills, int team2Kills)
	{
		if (!TvTConfig.BROADCAST_MVP) return;
		if (participants.isEmpty()) return;
		int max = 0;
		for (TvTPlayerData d : participants)
			if (d.getKills() > max) max = d.getKills();
		if (max == 0) return;
		List<TvTPlayerData> tops = new ArrayList<>();
		for (TvTPlayerData d : participants)
			if (d.getKills() == max) tops.add(d);
		if (tops.size() > 1 && !TvTConfig.MVP_TIE_ENABLED) return;
		StringBuilder names = new StringBuilder();
		for (int i = 0; i < tops.size(); i++)
		{
			if (i > 0) names.append(", ");
			names.append(tops.get(i).getName());
		}
		Broadcast.announceToOnlinePlayers(String.format(TvTConfig.MVP_MESSAGE_FORMAT, names.toString(), max));
	}
	
	private boolean canReceiveItem(Player p, ItemInstance proto, int amount)
	{
		try
		{
			if (proto.isStackable())
			{
				try { if (!p.getInventory().validateWeight(proto.getItem().getWeight() * amount)) return false; } catch (Throwable ignored) {}
				return true;
			}
			try { if (!p.getInventory().validateCapacity(1)) return false; } catch (Throwable ignored) {}
			try {
				int totalWeight = proto.getItem().getWeight() * amount;
				if (!p.getInventory().validateWeight(totalWeight)) return false;
			} catch (Throwable ignored) {}
			return true;
		}
		catch (Throwable t)
		{
			return true;
		}
	}
	
	/* ===================== REG NPC ===================== */
	
	private void spawnRegistrationNpc()
	{
		if (TvTConfig.REGISTRATION_NPC_ID <= 0 || TvTConfig.REGISTRATION_NPC_LOC == null || TvTConfig.REGISTRATION_NPC_LOC.isEmpty())
			return;
		try
		{
			String[] parts = TvTConfig.REGISTRATION_NPC_LOC.split(",");
			if (parts.length < 3) return;
			int x = Integer.parseInt(parts[0].trim());
			int y = Integer.parseInt(parts[1].trim());
			int z = Integer.parseInt(parts[2].trim());
			int h = (parts.length > 3) ? Integer.parseInt(parts[3].trim()) : 0;
			
			if (regSpawn != null)
				despawnRegistrationNpc();
			
			NpcTemplate tmpl = NpcTable.getInstance().getTemplate(TvTConfig.REGISTRATION_NPC_ID);
			if (tmpl == null) return;
			
			regSpawn = new L2Spawn(tmpl);
			regSpawn.setLoc(x, y, z, h);
			regSpawn.doSpawn(false);
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "Spawn registration NPC error", e);
		}
	}
	
	private void despawnRegistrationNpc()
	{
		try
		{
			if (regSpawn != null)
			{
				if (regSpawn.getNpc() != null)
					regSpawn.getNpc().deleteMe();
				regSpawn = null;
			}
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "despawnRegistrationNpc error", e);
		}
	}
	
	private void spawnBuffers()
	{
		if (TvTConfig.BUFFER_NPC_ID <= 0)
			return;
		
		NpcTemplate tmpl = NpcTable.getInstance().getTemplate(TvTConfig.BUFFER_NPC_ID);
		if (tmpl == null)
			return;
		
		try {
			if (!(TvTConfig.TEAM1_BUFFER_LOC_X == 0 && TvTConfig.TEAM1_BUFFER_LOC_Y == 0 && TvTConfig.TEAM1_BUFFER_LOC_Z == 0)) {
				L2Spawn spawn1 = new L2Spawn(tmpl);
				spawn1.setLoc(TvTConfig.TEAM1_BUFFER_LOC_X, TvTConfig.TEAM1_BUFFER_LOC_Y, TvTConfig.TEAM1_BUFFER_LOC_Z, TvTConfig.TEAM1_BUFFER_LOC_H);
				spawn1.doSpawn(false);
				bufferSpawns.add(spawn1);
			}
			if (!(TvTConfig.TEAM2_BUFFER_LOC_X == 0 && TvTConfig.TEAM2_BUFFER_LOC_Y == 0 && TvTConfig.TEAM2_BUFFER_LOC_Z == 0)) {
				L2Spawn spawn2 = new L2Spawn(tmpl);
				spawn2.setLoc(TvTConfig.TEAM2_BUFFER_LOC_X, TvTConfig.TEAM2_BUFFER_LOC_Y, TvTConfig.TEAM2_BUFFER_LOC_Z, TvTConfig.TEAM2_BUFFER_LOC_H);
				spawn2.doSpawn(false);
				bufferSpawns.add(spawn2);
			}
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Could not spawn buffers", e);
		}
	}

	private void despawnBuffers()
	{
		for (L2Spawn spawn : bufferSpawns)
		{
			if (spawn.getNpc() != null)
				spawn.getNpc().deleteMe();
		}
		bufferSpawns.clear();
	}
	
	/* ===================== FORCE / DEBUG PUBLIC API ===================== */
	
	public void forceOpen()
	{
		if (state == TvTState.IDLE)
			openRegistration("FORCE");
	}
	
	public void forceStart()
	{
		if (state == TvTState.REGISTRATION)
		{
			if (registrationCloseTask != null)
			{
				registrationCloseTask.cancel(false);
				registrationCloseTask = null;
			}
			startEvent();
		}
	}
	
	public void forceEnd()
	{
		if (state == TvTState.RUNNING || state == TvTState.ENDING)
			endEvent();
	}
	
	public void forceSpawnRegNpc()
	{
		spawnRegistrationNpc();
	}
	
	public void forceDespawnRegNpc()
	{
		despawnRegistrationNpc();
	}
	
	public void scheduleTestRegistrationIn(int seconds)
	{
		if (seconds <= 0) return;
		ThreadPool.schedule(() -> {
			try { openRegistration("TEST"); } catch (Exception e) { LOG.log(Level.WARNING, "Test registration task error", e); }
		}, seconds * 1000L);
	}
	
	/* ===================== WATCHDOG ===================== */
	
	private void startWatchdog()
	{
		if (watchdogTask != null && !watchdogTask.isCancelled()) return;
		watchdogTask = ThreadPool.scheduleAtFixedRate(() -> {
			try
			{
				long now = System.currentTimeMillis();
				for (ScheduledEntry se : scheduledEntries.values())
				{
					long remain = se.fireAtMillis - now;
					if (!se.fired && remain < 31000 && remain >= 0)
					{
						LOG.fine("TvT WATCHDOG: pending id=" + se.id + " remain=" + remain + "ms");
					}
				}
			}
			catch (Throwable t)
			{
				LOG.log(Level.WARNING, "TvTManager watchdog error", t);
			}
		}, 30000L, 30000L);
	}
	
	private static final class ScheduledEntry
	{
		final int id;
		final String hour;
		final long fireAtMillis;
		final long createdAt;
		volatile boolean fired = false;
		volatile long firedAt = 0L;
		volatile ScheduledFuture<?> future;
		ScheduledEntry(int id, String hour, long fireAtMillis, long createdAt)
		{
			this.id = id;
			this.hour = hour;
			this.fireAtMillis = fireAtMillis;
			this.createdAt = createdAt;
		}
	}
	
	private String futureState(ScheduledFuture<?> f)
	{
		if (f == null) return "null";
		try
		{
			if (f.isCancelled()) return "CANCELLED";
			if (f.isDone()) return "DONE";
			return "PENDING";
		}
		catch (Throwable t)
		{
			return "ERR";
		}
	}
	
	private static class SingletonHolder
	{
		private static final TvTManager INSTANCE = new TvTManager();
	}
	
	private boolean isAnySiegeInProgress()
	{
		try
		{
			for (Castle c : CastleManager.getInstance().getCastles())
				if (c.getSiege() != null && c.getSiege().isInProgress())
					return true;
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "isAnySiegeInProgress error", e);
		}
		return false;
	}
	
	public boolean isTvTCombatAllowed(Player a, Player b)
	{
		if (a == null || b == null) return true;
		if (state != TvTState.RUNNING) return true;
		TvTPlayerData da = participantMap.get(a.getObjectId());
		TvTPlayerData db = participantMap.get(b.getObjectId());
		boolean ain = da != null;
		boolean bin = db != null;
		if (ain && bin)
			return da.getTeam() != db.getTeam();
		if (ain ^ bin)
			return false;
		return true;
	}
	
	public boolean isFriendlyFire(Player a, Player b)
	{
		if (a == null || b == null) return false;
		if (state != TvTState.RUNNING) return false;
		TvTPlayerData da = participantMap.get(a.getObjectId());
		TvTPlayerData db = participantMap.get(b.getObjectId());
		if (da == null || db == null) return false;
		return da.getTeam() == db.getTeam();
	}
	
	public List<String> getSchedules()
	{
		return new ArrayList<>(schedules);
	}
}