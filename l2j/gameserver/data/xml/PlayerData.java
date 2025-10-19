package net.sf.l2j.gameserver.data.xml;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.commons.data.xml.XMLDocument;

import net.sf.l2j.gameserver.model.actor.template.PlayerTemplate;
import net.sf.l2j.gameserver.model.base.ClassId;
import net.sf.l2j.gameserver.model.holder.skillnode.GeneralSkillNode;
import net.sf.l2j.gameserver.templates.StatsSet;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * This class loads and stores {@link PlayerTemplate}s. It also feed their skill trees.
 */
public class PlayerData extends XMLDocument
{
	private final Map<Integer, PlayerTemplate> _templates = new HashMap<>();
	
	protected PlayerData()
	{
		load();
	}
	
	@Override
	protected void load()
	{
		loadDocument("./data/xml/classes");
		LOG.info("Loaded " + _templates.size() + " player classes templates.");
		
		// We add parent skills, if existing.
		for (PlayerTemplate template : _templates.values())
		{
			final ClassId parentClassId = template.getClassId().getParent();
			if (parentClassId != null)
				template.getSkills().addAll(_templates.get(parentClassId.getId()).getSkills());
		}
	}
	
	@Override
	protected void parseDocument(Document doc, File f)
	{
		// StatsSet used to feed informations. Cleaned on every entry.
		final StatsSet set = new StatsSet();
		final StatsSet skillSet = new StatsSet();
		
		// First element is never read.
		final Node n = doc.getFirstChild();
		
		for (Node o = n.getFirstChild(); o != null; o = o.getNextSibling())
		{
			if (!"class".equalsIgnoreCase(o.getNodeName()))
				continue;
			
			for (Node d = o.getFirstChild(); d != null; d = d.getNextSibling())
			{
				if ("set".equalsIgnoreCase(d.getNodeName()))
				{
					// Parse and feed content.
					parseAndFeed(d.getAttributes(), set);
				}
				else if ("skills".equalsIgnoreCase(d.getNodeName()))
				{
					// The list used to feed skills tree of this player template class.
					final List<GeneralSkillNode> skills = new ArrayList<>();
					
					for (Node e = d.getFirstChild(); e != null; e = e.getNextSibling())
					{
						if (!"skill".equalsIgnoreCase(e.getNodeName()))
							continue;
						
						// Parse and feed content.
						parseAndFeed(e.getAttributes(), skillSet);
						
						// Feed the map with new data.
						skills.add(new GeneralSkillNode(skillSet));
						
						// Clear the StatsSet.
						skillSet.clear();
					}
					
					// Feed the global StatsSet with skills list.
					set.set("skills", skills);
				}
			}
			
			// Feed the map with new data.
			_templates.put(set.getInteger("id"), new PlayerTemplate(set));
			
			// Clear the StatsSet.
			set.clear();
		}
	}
	
	public PlayerTemplate getTemplate(ClassId classId)
	{
		return _templates.get(classId.getId());
	}
	
	public PlayerTemplate getTemplate(int classId)
	{
		return _templates.get(classId);
	}
	
	public final String getClassNameById(int classId)
	{
		final PlayerTemplate template = _templates.get(classId);
		return (template != null) ? template.getClassName() : "Invalid class";
	}
	
	public static PlayerData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final PlayerData INSTANCE = new PlayerData();
	}
}
