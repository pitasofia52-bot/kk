package net.sf.l2j.gameserver.handler.chathandlers;

import com.elfocrash.roboto.FakePlayer;
import com.elfocrash.roboto.helpers.BotPvpPmMemory;
import com.elfocrash.roboto.helpers.BotPvpPmResponseHelper;
import compvp.elfocrash.roboto.FFakePlayer;
import dre.elfocrash.roboto.FFFFakePlayer;
import dre.elfocrash.roboto.helpers.BotPmMemory;
import dre.elfocrash.roboto.helpers.BotPmResponseHelper;
import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.handler.IChatHandler;
import net.sf.l2j.gameserver.model.BlockList;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public class ChatTell implements IChatHandler
{
    private static final int[] COMMAND_IDS =
    {
        2
    };

    @Override
    public void handleChat(int type, Player activeChar, String target, String text)
    {
        if (target == null)
            return;

        final Player receiver = World.getInstance().getPlayer(target);
        if (receiver == null)
        {
            activeChar.sendPacket(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
            return;
        }
        if (receiver.getClient() != null && receiver.getClient().isDetached()
                && !(receiver instanceof FakePlayer)
                && !(receiver instanceof FFFFakePlayer)
                && !(receiver instanceof FFakePlayer))
        {
            activeChar.sendPacket(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
            return;
        }

        if (activeChar.equals(receiver))
        {
            activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
            return;
        }

        if (receiver.isInJail() || receiver.isChatBanned())
        {
            activeChar.sendPacket(SystemMessageId.TARGET_IS_CHAT_BANNED);
            return;
        }

        if (!activeChar.isGM() && (receiver.isInRefusalMode() || BlockList.isBlocked(receiver, activeChar)))
        {
            activeChar.sendPacket(SystemMessageId.THE_PERSON_IS_IN_MESSAGE_REFUSAL_MODE);
            return;
        }

        // Deliver the actual tell
        receiver.sendPacket(new CreatureSay(activeChar.getObjectId(), type, activeChar.getName(), text));
        activeChar.sendPacket(new CreatureSay(activeChar.getObjectId(), type, "->" + receiver.getName(), text));

        // Auto-reply για bots: τώρα και FFakePlayer και FFFFakePlayer.
        if (receiver instanceof FFFFakePlayer || receiver instanceof FFakePlayer)
        {
            // Global rate-limit per sender->bot
            if (BotPmMemory.rateLimited(activeChar.getObjectId(), receiver.getObjectId()))
                return;

            final String keyword = extractKeyword(text, BotPmResponseHelper.getKeywords());
            if (keyword != null && BotPmResponseHelper.hasKeyword(keyword))
            {
                // Ignore repeated same message (normalized) within 30 minutes
                if (!BotPmMemory.shouldIgnoreMessage(activeChar.getObjectId(), receiver.getObjectId(), text))
                {
                    final String lastResp = BotPmMemory.getLastResponse(activeChar.getObjectId(), receiver.getObjectId(), keyword);
                    final String response = BotPmResponseHelper.getRandomResponseExcept(keyword, lastResp);
                    if (response != null)
                    {
                        // Προετοιμασία delayed απάντησης 5-20s
                        final int delayMs = Rnd.get(5000, 20000);

                        // Καταγραφή anti-spam & rate-limit άμεσα ώστε να μη σωρεύονται απαντήσεις
                        BotPmMemory.recordMessage(activeChar.getObjectId(), receiver.getObjectId(), text);
                        BotPmMemory.markReplied(activeChar.getObjectId(), receiver.getObjectId());
                        BotPmMemory.setLastResponse(activeChar.getObjectId(), receiver.getObjectId(), keyword, response);

                        ThreadPool.schedule(() ->
                        {
                            // Ασφαλής αποστολή (αν ο παίκτης αποσυνδέθηκε/αποκόπηκε, παράλειψε)
                            if (activeChar == null || activeChar.getClient() == null || activeChar.getClient().isDetached())
                                return;

                            // Στείλε την απάντηση ως να έρχεται από το bot (receiver)
                            activeChar.sendPacket(new CreatureSay(receiver.getObjectId(), type, receiver.getName(), response));
                        }, delayMs);
                    }
                }
            }
        }

        // ΝΕΟ: Auto-reply για ΟΛΑ τα FakePlayer (μόνο αυτά, όχι τα υπόλοιπα bot!)
        if (receiver instanceof FakePlayer)
        {
            // Rate-limit για PvP bot PM
            if (BotPvpPmMemory.rateLimited(activeChar.getObjectId(), receiver.getObjectId()))
                return;

            final String keyword = extractKeyword(text, BotPvpPmResponseHelper.getKeywords());
            if (keyword != null && BotPvpPmResponseHelper.hasKeyword(keyword))
            {
                // Ignore repeated same message (normalized) within 30 minutes
                if (!BotPvpPmMemory.shouldIgnoreMessage(activeChar.getObjectId(), receiver.getObjectId(), text))
                {
                    final String lastResp = BotPvpPmMemory.getLastResponse(activeChar.getObjectId(), receiver.getObjectId(), keyword);
                    final String response = BotPvpPmResponseHelper.getRandomResponseExcept(keyword, lastResp);
                    if (response != null)
                    {
                        // Προετοιμασία delayed απάντησης 5-20s
                        final int delayMs = Rnd.get(5000, 20000);

                        // Καταγραφή anti-spam & rate-limit άμεσα ώστε να μη σωρεύονται απαντήσεις
                        BotPvpPmMemory.recordMessage(activeChar.getObjectId(), receiver.getObjectId(), text);
                        BotPvpPmMemory.markReplied(activeChar.getObjectId(), receiver.getObjectId());
                        BotPvpPmMemory.setLastResponse(activeChar.getObjectId(), receiver.getObjectId(), keyword, response);

                        ThreadPool.schedule(() ->
                        {
                            // Ασφαλής αποστολή (αν ο παίκτης αποσυνδέθηκε/αποκόπηκε, παράλειψε)
                            if (activeChar == null || activeChar.getClient() == null || activeChar.getClient().isDetached())
                                return;

                            // Στείλε την απάντηση ως να έρχεται από το bot (receiver)
                            activeChar.sendPacket(new CreatureSay(receiver.getObjectId(), type, receiver.getName(), response));
                        }, delayMs);
                    }
                }
            }
        }
    }

    // Για να μπορεί να δουλεύει και για τα δύο helpers (FFake/FFF και PvP)
    private String extractKeyword(String message, java.util.Set<String> keywords)
    {
        final String lower = message.toLowerCase();
        for (String keyword : keywords)
        {
            if (lower.contains(keyword))
                return keyword;
        }
        return null;
    }

    @Override
    public int[] getChatTypeList()
    {
        return COMMAND_IDS;
    }
}