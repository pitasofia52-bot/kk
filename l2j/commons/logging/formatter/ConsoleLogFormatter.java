package net.sf.l2j.commons.logging.formatter;

import java.util.logging.LogRecord;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.logging.MasterFormatter;

public class ConsoleLogFormatter extends MasterFormatter
{
	@Override
	public String format(LogRecord record)
	{
		final StringBuilder sb = new StringBuilder(500);
		
		StringUtil.append(sb, record.getMessage(), CRLF);
		
		if (record.getThrown() != null)
		{
			try
			{
				StringUtil.append(sb, record.getThrown().getMessage(), CRLF);
			}
			catch (Exception ex)
			{
			}
		}
		return sb.toString();
	}
}
