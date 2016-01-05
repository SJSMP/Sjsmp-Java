package org.sjsmp.server;

/**
 *
 * @author kondrashin_aa
 */
public final class SjmpServerException extends Exception 
{
	private static final long serialVersionUID = 1L;

	public SjmpServerException(final String message)
    {
        super(message);
    }

    public SjmpServerException(final String message, final Exception e)
    {
        super(message, e);
    }
}
