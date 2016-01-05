package org.sjsmp.server;

/**
 *
 * @author kondrashin_aa
 */
public final class SjmlArgumentException extends Exception 
{
    private static final long serialVersionUID = 1L;
    
    public SjmlArgumentException(final String message)
    {
        super(message);
    }

    public SjmlArgumentException(final String message, final Exception e)
    {
        super(message, e);
    }
}
