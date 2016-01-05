/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sjsmp.server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author barg_ma
 */
public final class NamedThreadFactory implements ThreadFactory
{
    private final String m_namePrefix;
    private final AtomicInteger m_sequence = new AtomicInteger(0);

    public NamedThreadFactory(String namePrefix)
    {
        this.m_namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r)
    {
        Thread t = new Thread(r);
        t.setName(m_namePrefix + "-" + m_sequence.getAndIncrement());
        return t;
    }
}
