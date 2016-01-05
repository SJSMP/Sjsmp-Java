package org.sjsmp.server;

/**
 *
 * @author kondrashin_aa
 */
public final class SjsmpArgumentException extends Exception 
{
    private static final long serialVersionUID = 1L;
    
    public SjsmpArgumentException(final String message)
    {
        super(message);
    }

    public SjsmpArgumentException(final String message, final Exception e)
    {
        super(message, e);
    }
}
