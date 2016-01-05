package org.sjsmp.sample.server;

import com.genes1s.sjmp.server.SjmpProperty;

public interface ISampleObject
{
	//does not work (
    @SjmpProperty("Declared in interface")
    public int getAnswer();
}
