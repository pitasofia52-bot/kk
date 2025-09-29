package net.sf.l2j.gameserver.network.clientpackets;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.L2FriendSay;
import dre.elfocrash.roboto.FFFFakePlayer;
import dre.elfocrash.roboto.helpers.BotPmMemory;
import dre.elfocrash.roboto.helpers.BotPmResponseHelper;

/**
 * Recieve Private (Friend) Message - 0xCC Format: c SS S: Message S: Receiving Player
 * @author Tempy
 */
public final class RequestSendFriendMsg extends L2GameClientPacket
{
    private static final Logger CHAT_LOG = Logger.getLogger("chat");
    
    private String _message;
    private String _reciever;
    
    @Override
    protected void readImpl()
    {
        _message = readS();
        _reciever = readS();
    }
    
    @Override
    protected void runImpl()
    {
        if (_message == null || _message.isEmpty() || _message.length() > 300)
            return;
        
        final Player activeChar = getClient().getActiveChar();
        if (activeChar == null)
            return;
        
        final Player targetPlayer = World.getInstance().getPlayer(_reciever);
        
        if (targetPlayer == null)
        {
            activeChar.sendPacket(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
            return;
        }
            
        if (targetPlayer instanceof FFFFakePlayer)
        {
            // Rate limit per sender->bot
            if (BotPmMemory.rateLimited(activeChar.getObjectId(), targetPlayer.getObjectId()))
                return;

            String keyword = extractKeyword(_message);
            if (keyword != null && BotPmResponseHelper.hasKeyword(keyword))
            {
                if (!BotPmMemory.shouldIgnoreMessage(activeChar.getObjectId(), targetPlayer.getObjectId(), _message))
                {
                    final String lastResp = BotPmMemory.getLastResponse(activeChar.getObjectId(), targetPlayer.getObjectId(), keyword);
                    final String response = BotPmResponseHelper.getRandomResponseExcept(keyword, lastResp);
                    if (response != null)
                    {
                        final int delayMs = Rnd.get(5000, 20000);

                        // Καταγραφή anti-spam & rate-limit άμεσα
                        BotPmMemory.recordMessage(activeChar.getObjectId(), targetPlayer.getObjectId(), _message);
                        BotPmMemory.markReplied(activeChar.getObjectId(), targetPlayer.getObjectId());
                        BotPmMemory.setLastResponse(activeChar.getObjectId(), targetPlayer.getObjectId(), keyword, response);

                        ThreadPool.schedule(() ->
                        {
                            if (activeChar == null || activeChar.getClient() == null || activeChar.getClient().isDetached())
                                return;

                            // Reply to the player as if it comes from the bot (friend PM channel).
                            activeChar.sendPacket(new L2FriendSay(targetPlayer.getName(), activeChar.getName(), response));
                        }, delayMs);
                    }
                }
            }
            return;
        }
        
        if (Config.LOG_CHAT)
        {
            LogRecord record = new LogRecord(Level.INFO, _message);
            record.setLoggerName("chat");
            record.setParameters(new Object[]
            {
                "PRIV_MSG",
                "[" + activeChar.getName() + " to " + _reciever + "]"
            });
            
            CHAT_LOG.log(record);
        }
        
        targetPlayer.sendPacket(new L2FriendSay(activeChar.getName(), _reciever, _message));
    }

    private String extractKeyword(String message)
    {
        String lower = message.toLowerCase();
        for (String keyword : BotPmResponseHelper.getKeywords())
        {
            if (lower.contains(keyword))
                return keyword;
        }
        return null;
    }
}