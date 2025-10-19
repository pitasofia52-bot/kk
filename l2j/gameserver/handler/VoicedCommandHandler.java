package net.sf.l2j.gameserver.handler;

import java.util.HashMap;
import java.util.Map;

public final class VoicedCommandHandler
{
    private static final VoicedCommandHandler INSTANCE = new VoicedCommandHandler();
    public static VoicedCommandHandler getInstance() { return INSTANCE; }

    private final Map<String, IVoicedCommandHandler> _datatable = new HashMap<>();

    public void registerHandler(IVoicedCommandHandler handler)
    {
        for (String cmd : handler.getVoicedCommandList())
            _datatable.put(cmd.toLowerCase(), handler);
    }

    public IVoicedCommandHandler getHandler(String voicedCommand)
    {
        if (voicedCommand == null)
            return null;
        return _datatable.get(voicedCommand.toLowerCase());
    }

    public int size()
    {
        return _datatable.size();
    }
}