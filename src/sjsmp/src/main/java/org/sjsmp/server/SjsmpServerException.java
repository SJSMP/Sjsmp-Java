package org.sjsmp.server;

/**
 *
 * @author kondrashin_aa
 */
public final class SjsmpServerException extends Exception 
{
	private static final long serialVersionUID = 1L;

	public SjsmpServerException(final String message)
    {
        super(message);
    }

    public SjsmpServerException(final String message, final Exception e)
    {
        super(message, e);
    }
}
