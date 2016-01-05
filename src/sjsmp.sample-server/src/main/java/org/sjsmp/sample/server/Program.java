package org.sjsmp.sample.server;

import java.io.IOException;

import com.genes1s.sjmp.server.SjmpServerException;
import com.genes1s.sjmp.server.SjmpServer;

public class Program
{
	
	public static void main(String[] args)
	{
		try (SjmpServer server = new SjmpServer("SchemaName", "Schema description", "Sample service group", 12345, 12345, null, "http://portal.activebc.ru/sjmp/register"))
		{
			SampleObject obj1 = new SampleObject();
            SampleObject obj2 = new SampleObject();
            server.RegisterObject(obj1, "SampleObjectName1", "First SampleObject Description", "SampleObject Group", false);
            server.RegisterObject(obj2, "SampleObjectName2", "Second SampleObject Description", "SampleObject Group", true);

            System.out.println("Press any key to stop");
            System.out.flush();
	    	System.in.read();

            obj1.stopTimer();
            obj2.stopTimer();
            server.UnRegisterObject(obj1);
            server.UnRegisterObject(obj2);
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (SjmpServerException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
}
