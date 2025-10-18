package net.sf.l2j.gameserver.custom.tvt;

import net.sf.l2j.gameserver.model.location.Location;

/**
 * Per-player TvT data (extended with IP).
 */
public final class TvTPlayerData
{
	private final int objectId;
	private final String name;
	private final int team;
	
	private int kills;
	private int deaths;
	
	private final Location originalLoc;
	private final String originalTitle;
	private final int originalTitleColor;
	private final int originalNameColor;
	private final String ip;
	
	public TvTPlayerData(int objectId, String name, int team,
	                     Location originalLoc, String originalTitle,
	                     int originalTitleColor, int originalNameColor,
	                     String ip)
	{
		this.objectId = objectId;
		this.name = name;
		this.team = team;
		this.originalLoc = originalLoc;
		this.originalTitle = originalTitle;
		this.originalTitleColor = originalTitleColor;
		this.originalNameColor = originalNameColor;
		this.ip = ip;
	}
	
	public int getObjectId() { return objectId; }
	public String getName() { return name; }
	public int getTeam() { return team; }
	
	public int getKills() { return kills; }
	public int getDeaths() { return deaths; }
	public void addKill() { kills++; }
	public void addDeath() { deaths++; }
	
	public Location getOriginalLoc() { return originalLoc; }
	public String getOriginalTitle() { return originalTitle; }
	public int getOriginalTitleColor() { return originalTitleColor; }
	public int getOriginalNameColor() { return originalNameColor; }
	public String getIp() { return ip; }
}