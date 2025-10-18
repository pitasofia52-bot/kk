package net.sf.l2j.gameserver.custom.tvt;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.skills.AbnormalEffect;

public final class TvTConfig
{
	private static final Logger LOG = Logger.getLogger(TvTConfig.class.getName());
	private static final String FILE = "./config/tvt.txt";
	
	public static boolean ENABLED;
	public static int REGISTRATION_TIME;
	public static int EVENT_TIME;
	public static int MIN_PLAYERS;
	public static int MAX_PLAYERS;
	public static int RESPAWN_DELAY;
	
	public static final List<String> SCHEDULES = new ArrayList<>();
	
	public static int TEAM1_SPAWN_X, TEAM1_SPAWN_Y, TEAM1_SPAWN_Z, TEAM1_SPAWN_H;
	public static int TEAM2_SPAWN_X, TEAM2_SPAWN_Y, TEAM2_SPAWN_Z, TEAM2_SPAWN_H;
	
	// (Συμβατότητα config – δεν αλλάζουμε πραγματικά τα colors πλέον)
	public static int TITLE_COLOR_TEAM1, TITLE_COLOR_TEAM2;
	public static int NAME_COLOR_TEAM1, NAME_COLOR_TEAM2;
	
	public static int REWARD_WINNER_ITEM, REWARD_WINNER_AMOUNT;
	public static int REWARD_LOSER_ITEM, REWARD_LOSER_AMOUNT;
	public static int REWARD_TIE_ITEM, REWARD_TIE_AMOUNT;
	
	public static boolean SHOW_PERSONAL_STATUS_ON_JOIN;
	public static int MIN_LEVEL, MAX_LEVEL;
	
	public static int MAX_WINDOWS_PER_IP;
	public static boolean OVER_IP_LIMIT_NO_REWARD;
	public static int KILL_REWARD_MIN_KILLS;
	public static boolean INVENTORY_FULL_DROPS_REWARD;
	
	public static boolean USE_TEAM_AURAS;
	public static String TEAM_AURA_RED;
	public static String TEAM_AURA_BLUE;
	public static AbnormalEffect TEAM_AURA_RED_EFFECT;
	public static AbnormalEffect TEAM_AURA_BLUE_EFFECT;
	
	// NEW: Combined masks (υποστήριξη πολλαπλών effects με '|')
	public static int TEAM_AURA_RED_MASK;
	public static int TEAM_AURA_BLUE_MASK;
	
	public static int REGISTRATION_NPC_ID;
	public static String REGISTRATION_NPC_LOC;
	public static int BUFFER_NPC_ID;
	public static boolean SPAWN_BUFFER_AT_TEAMS;
	
	// Buffer locations
	public static int TEAM1_BUFFER_LOC_X, TEAM1_BUFFER_LOC_Y, TEAM1_BUFFER_LOC_Z, TEAM1_BUFFER_LOC_H;
	public static int TEAM2_BUFFER_LOC_X, TEAM2_BUFFER_LOC_Y, TEAM2_BUFFER_LOC_Z, TEAM2_BUFFER_LOC_H;
	
	// Phase 2 / extensions
	public static boolean BROADCAST_MVP;
	public static boolean BLOCK_EXTERNAL_COMBAT;
	public static boolean BLOCK_ALL_VOICE_IN_EVENT;
	public static boolean ALLOW_TVT_STATUS_VOICE;
	public static int BUFFER_COPIES_PER_TEAM;
	public static String BUFFER_OFFSETS;
	public static boolean DISQUALIFY_ON_TELEPORT_OUT;
	public static int APPROVED_TELEPORT_RADIUS;
	public static String SCROLL_TELEPORT_ACTION;
	public static String DISQUALIFY_MESSAGE;
	public static boolean MVP_TIE_ENABLED;
	
	public static int SPAWN_PROTECTION_MS;
	
	public static String MVP_MESSAGE_FORMAT;
	public static boolean ALLOW_KARMA_PLAYERS;
	
	public static void load()
	{
		Properties p = new Properties();
		try (FileInputStream fis = new FileInputStream(new File(FILE)))
		{
			p.load(fis);
			
			ENABLED = getBoolean(p, "Enabled", false);
			REGISTRATION_TIME = getInt(p, "RegistrationTime", 60);
			EVENT_TIME = getInt(p, "EventTime", 120);
			MIN_PLAYERS = getInt(p, "MinPlayers", 2);
			MAX_PLAYERS = getInt(p, "MaxPlayers", 0);
			RESPAWN_DELAY = getInt(p, "RespawnDelay", 7);
			
			SCHEDULES.clear();
			String raw = p.getProperty("Schedules", "").trim();
			LOG.info("TvTConfig: Raw Schedules string = [" + raw + "]");
			if (!raw.isEmpty())
			{
				for (String token : raw.split(","))
				{
					String t = token.trim();
					if (t.matches("\\d{2}:\\d{2}"))
					{
						SCHEDULES.add(t);
					}
					else
						LOG.warning("TvTConfig: Invalid schedule ignored: " + t);
				}
			}
			
			parseSpawn(p.getProperty("Team1Spawn", "0,0,0,0"), true, false, false);
			parseSpawn(p.getProperty("Team2Spawn", "0,0,0,0"), false, false, false);
			
			TITLE_COLOR_TEAM1 = parseColor(p.getProperty("TitleColorTeam1", "FF4444"));
			TITLE_COLOR_TEAM2 = parseColor(p.getProperty("TitleColorTeam2", "4488FF"));
			NAME_COLOR_TEAM1 = parseColor(p.getProperty("NameColorTeam1", "FFFFFF"));
			NAME_COLOR_TEAM2 = parseColor(p.getProperty("NameColorTeam2", "FFFFFF"));
			
			// Rewards
			REWARD_WINNER_ITEM = getInt(p, "RewardWinnerItemId", 57);
			REWARD_WINNER_AMOUNT = getInt(p, "RewardWinnerAmount", 200);
			REWARD_LOSER_ITEM = getInt(p, "RewardLoserItemId", 222);
			REWARD_LOSER_AMOUNT = getInt(p, "RewardLoserAmount", 100);
			REWARD_TIE_ITEM = getInt(p, "RewardTieItemId", REWARD_LOSER_ITEM);
			REWARD_TIE_AMOUNT = getInt(p, "RewardTieAmount", REWARD_LOSER_AMOUNT);
			
			SHOW_PERSONAL_STATUS_ON_JOIN = getBoolean(p, "ShowPersonalStatusOnJoin", true);
			MIN_LEVEL = getInt(p, "MinLevel", 0);
			MAX_LEVEL = getInt(p, "MaxLevel", 0);
			
			MAX_WINDOWS_PER_IP = getInt(p, "MaxWindowsPerIP", 2);
			OVER_IP_LIMIT_NO_REWARD = getBoolean(p, "OverIpLimitNoReward", false);
			KILL_REWARD_MIN_KILLS = getInt(p, "KillRewardMinKills", 10);
			INVENTORY_FULL_DROPS_REWARD = getBoolean(p, "InventoryFullDropsReward", false);
			
			USE_TEAM_AURAS = getBoolean(p, "UseTeamAuras", true);
			TEAM_AURA_RED = p.getProperty("TeamAuraRed", "FLAME").trim();
			TEAM_AURA_BLUE = p.getProperty("TeamAuraBlue", "ICE").trim();
			resolveAuras();
			
			REGISTRATION_NPC_ID = getInt(p, "RegistrationNpcId", 0);
			REGISTRATION_NPC_LOC = p.getProperty("RegistrationNpcLoc", "").trim();
			BUFFER_NPC_ID = getInt(p, "BufferNpcId", 0);
			SPAWN_BUFFER_AT_TEAMS = getBoolean(p, "SpawnBufferAtTeams", false);
			
			parseSpawn(p.getProperty("BufferTeam1Loc", "0,0,0,0"), false, true, false);
			parseSpawn(p.getProperty("BufferTeam2Loc", "0,0,0,0"), false, false, true);
			
			BROADCAST_MVP = getBoolean(p, "BroadcastMvp", true);
			BLOCK_EXTERNAL_COMBAT = getBoolean(p, "BlockExternalCombat", true);
			BLOCK_ALL_VOICE_IN_EVENT = getBoolean(p, "BlockAllVoiceInEvent", true);
			ALLOW_TVT_STATUS_VOICE = getBoolean(p, "AllowTvTStatusVoice", true);
			BUFFER_COPIES_PER_TEAM = getInt(p, "BufferCopiesPerTeam", 2);
			BUFFER_OFFSETS = p.getProperty("BufferOffsets", "0:0;80:0").trim();
			DISQUALIFY_ON_TELEPORT_OUT = getBoolean(p, "DisqualifyOnTeleportOut", true);
			APPROVED_TELEPORT_RADIUS = getInt(p, "ApprovedTeleportRadius", 50);
			SCROLL_TELEPORT_ACTION = p.getProperty("ScrollTeleportAction", "BLOCK").trim().toUpperCase();
			DISQUALIFY_MESSAGE = p.getProperty("DisqualifyMessage", "TvT: Disqualified due to leaving the arena").trim();
			MVP_TIE_ENABLED = getBoolean(p, "MvpTieEnabled", true);
			
			SPAWN_PROTECTION_MS = getInt(p, "SpawnProtectionMs", 2000);
			MVP_MESSAGE_FORMAT = p.getProperty("MvpMessageFormat", "[TvT] MVP: %s (%d kills)");
			
			ALLOW_KARMA_PLAYERS = getBoolean(p, "AllowKarmaPlayers", false);
			
			LOG.info("TvTConfig: Loaded (Enabled=" + ENABLED + " Respawn=" + RESPAWN_DELAY + " Auras=" + USE_TEAM_AURAS + " AllowKarmaPlayers=" + ALLOW_KARMA_PLAYERS + ")");
		}
		catch (Exception e)
		{
			LOG.warning("TvTConfig: Failed to load " + FILE + " : " + e.getMessage());
		}
	}
	
	private static void resolveAuras()
	{
		TEAM_AURA_RED_EFFECT = null;
		TEAM_AURA_BLUE_EFFECT = null;
		TEAM_AURA_RED_MASK = 0;
		TEAM_AURA_BLUE_MASK = 0;
		
		if (!USE_TEAM_AURAS)
			return;
		
		parseAuraString(TEAM_AURA_RED, true);
		parseAuraString(TEAM_AURA_BLUE, false);
		
		// Αν δεν δόθηκε valid token κρατάμε fallback (FLAME / ICE) ώστε να μην μείνει χωρίς οπτικό
		if (TEAM_AURA_RED_MASK == 0)
		{
			TEAM_AURA_RED_EFFECT = safeAura(TEAM_AURA_RED, AbnormalEffect.FLAME);
			TEAM_AURA_RED_MASK = (TEAM_AURA_RED_EFFECT != null) ? TEAM_AURA_RED_EFFECT.getMask() : 0;
		}
		if (TEAM_AURA_BLUE_MASK == 0)
		{
			TEAM_AURA_BLUE_EFFECT = safeAura(TEAM_AURA_BLUE, AbnormalEffect.ICE);
			TEAM_AURA_BLUE_MASK = (TEAM_AURA_BLUE_EFFECT != null) ? TEAM_AURA_BLUE_EFFECT.getMask() : 0;
		}
		
		LOG.info("TvTConfig: TeamAuraRed mask=0x" + Integer.toHexString(TEAM_AURA_RED_MASK) + " raw='" + TEAM_AURA_RED + "'");
		LOG.info("TvTConfig: TeamAuraBlue mask=0x" + Integer.toHexString(TEAM_AURA_BLUE_MASK) + " raw='" + TEAM_AURA_BLUE + "'");
	}
	
	private static void parseAuraString(String raw, boolean red)
	{
		if (raw == null || raw.isEmpty())
			return;
		
		String[] tokens = raw.toLowerCase().split("\\|");
		int mask = 0;
		AbnormalEffect first = null;
		
		for (String t : tokens)
		{
			String token = t.trim();
			if (token.isEmpty())
				continue;
			try
			{
				AbnormalEffect eff = AbnormalEffect.getByName(token);
				if (first == null)
					first = eff;
				mask |= eff.getMask();
			}
			catch (Exception ex)
			{
				LOG.warning("TvTConfig: Invalid aura token '" + token + "' in " + (red ? "TeamAuraRed" : "TeamAuraBlue"));
			}
		}
		
		if (red)
		{
			TEAM_AURA_RED_EFFECT = first;
			TEAM_AURA_RED_MASK = mask;
		}
		else
		{
			TEAM_AURA_BLUE_EFFECT = first;
			TEAM_AURA_BLUE_MASK = mask;
		}
	}
	
	private static AbnormalEffect safeAura(String name, AbnormalEffect def)
	{
		if (name == null || name.isEmpty()) return def;
		try
		{
			return AbnormalEffect.getByName(name.toLowerCase());
		}
		catch (Exception e)
		{
			LOG.warning("TvTConfig: Invalid aura name '" + name + "', fallback to " + def.getName());
			return def;
		}
	}
	
	private static void parseSpawn(String s, boolean team1, boolean isTeam1Buffer, boolean isTeam2Buffer)
	{
		try
		{
			String[] parts = s.split(",");
			int x = Integer.parseInt(parts[0].trim());
			int y = Integer.parseInt(parts[1].trim());
			int z = Integer.parseInt(parts[2].trim());
			int h = (parts.length > 3) ? Integer.parseInt(parts[3].trim()) : 0;
			
			if (isTeam1Buffer) {
				TEAM1_BUFFER_LOC_X = x; TEAM1_BUFFER_LOC_Y = y; TEAM1_BUFFER_LOC_Z = z; TEAM1_BUFFER_LOC_H = h;
			}
			else if (isTeam2Buffer) {
				TEAM2_BUFFER_LOC_X = x; TEAM2_BUFFER_LOC_Y = y; TEAM2_BUFFER_LOC_Z = z; TEAM2_BUFFER_LOC_H = h;
			}
			else if (team1) {
				TEAM1_SPAWN_X = x; TEAM1_SPAWN_Y = y; TEAM1_SPAWN_Z = z; TEAM1_SPAWN_H = h;
			}
			else {
				TEAM2_SPAWN_X = x; TEAM2_SPAWN_Y = y; TEAM2_SPAWN_Z = z; TEAM2_SPAWN_H = h;
			}
		}
		catch (Exception e)
		{
			LOG.warning("TvTConfig: Invalid spawn format for [" + s + "].");
		}
	}
	
	private static int parseColor(String hex)
	{
		hex = hex.trim().replace("0x", "").replace("#", "");
		try { return Integer.parseInt(hex, 16); } catch (Exception e) { return 0xFFFFFF; }
	}
	
	private static int getInt(Properties p, String key, int def)
	{
		try { return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim()); } catch (Exception e) { return def; }
	}
	private static boolean getBoolean(Properties p, String key, boolean def)
	{
		try { return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)).trim()); } catch (Exception e) { return def; }
	}
	
	private TvTConfig() {}
}