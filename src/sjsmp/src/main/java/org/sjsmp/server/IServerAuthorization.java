package org.sjsmp.server;

public interface IServerAuthorization
{
    boolean CheckAccess(String username, String password);
}
