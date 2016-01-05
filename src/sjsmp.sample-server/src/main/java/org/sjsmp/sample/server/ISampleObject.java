package org.sjsmp.sample.server;

import org.sjsmp.server.SjsmpProperty;

public interface ISampleObject
{
	//does not work (
    @SjsmpProperty("Declared in interface")
    public int getAnswer();
}
