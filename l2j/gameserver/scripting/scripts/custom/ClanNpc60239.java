package net.sf.l2j.gameserver.scripting.scripts.custom;

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.scripting.Quest;

// Import all modules
import net.sf.l2j.gameserver.scripting.scripts.custom.modules.*;

public class ClanNpc60239 extends Quest {
    private static final int NPC_ID = 60239;

    public ClanNpc60239() {
        super(-1, "custom");
        addFirstTalkId(NPC_ID);
        addTalkId(NPC_ID);
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        return MainHtmlModule.onFirstTalk(npc, player);
    }

    @Override
    public String onTalk(Npc npc, Player player) {
        return MainHtmlModule.onTalk(npc, player);
    }

    @Override
    public String onAdvEvent(String event, Npc npc, Player player) {
        // Route each event to the correct module
        if (event.equals("main")) {
            return MainHtmlModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("noblesse")) {
            return NoblesseModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("clanlevel")) {
            return ClanLevelUpModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("clanskill")) {
            return ClanSkillModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("learnClanSkill")) { // FIX: Handle learnClanSkill events!
            return ClanSkillModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("createClan")) {
            return CreateClanModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("deleteClan") || event.equals("confirmDeleteClan")) {
            return DeleteClanModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("changeClanLeader") || event.equals("changeLeaderWindow")) {
            return ChangeLeaderModule.onAdvEvent(event, npc, player);              
        } else if (event.startsWith("createAlliance")) {
            return AllianceCreateModule.onAdvEvent(event, npc, player);
        } else if (event.startsWith("dissolveAlliance")) {
            return AllianceDissolveModule.onAdvEvent(event, npc, player);
        }
        // fallback for unknown events
        return MainHtmlModule.onAdvEvent(event, npc, player);
    }
}