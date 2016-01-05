package org.sjsmp.sample.server;

import org.sjsmp.server.SjsmpAction;
import org.sjsmp.server.SjsmpActionParameter;
import org.sjsmp.server.SjsmpProperty;
import org.sjsmp.server.SjsmpPropertyLimits;

final class SampleObject implements ISampleObject
{
    private final Object m_lock = new Object();
    private volatile Thread m_thread;

    private int m_intervalSeconds;
    private volatile int m_timedValue;

    public SampleObject()
    {
    	m_intervalSeconds = 1;
    }

    @SjsmpProperty("timer interval")
    @SjsmpPropertyLimits(min = 1, max = 1000)
    int getIntervalSeconds() { return m_intervalSeconds; }
    void setIntervalSeconds(int value) { m_intervalSeconds = value; }


    @SjsmpProperty(value="value being changed by timer", showGraph = true)
    int getTimedValue() { return m_timedValue; }

    private void threadFunc()
    {
        while (true)
        {
            ++m_timedValue;
            try
			{
				Thread.sleep(m_intervalSeconds * 1000);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
        }
    }

    @SjsmpAction(value = "Starts timer", requireConfirm = true)
    public void startTimer()
    {
        synchronized (m_lock)
        {
            if (m_thread == null)
            {
                m_thread = new Thread(new Runnable()
					{
						@Override
						public void run()
						{
							threadFunc();
						}
					}
				);
                m_thread.start();
            }
        }
    }

    @SjsmpAction("Stops timer")
    public void stopTimer()
    {
        synchronized (m_lock)
        {
            if (m_thread != null)
            {
                m_thread.interrupt();
                m_thread = null;
            }
        }
    }

    @SjsmpAction("Returns true if the timer is currently running")
    public boolean isTimerRunning()
    {
        return m_thread != null;
    }

    @SjsmpAction("Returns same value that is passed as paramerter")
    public String returnSame(@SjsmpActionParameter("parameter to be returned") String param)
    {
        return param;
    }

	@Override
	public int getAnswer()
	{
		return 42;
	}

}
