package org.sjsmp.sample.server;

import com.genes1s.sjmp.server.SjmpAction;
import com.genes1s.sjmp.server.SjmpActionParameter;
import com.genes1s.sjmp.server.SjmpProperty;
import com.genes1s.sjmp.server.SjmpPropertyLimits;

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

    @SjmpProperty("timer interval")
    @SjmpPropertyLimits(min = 1, max = 1000)
    int getIntervalSeconds() { return m_intervalSeconds; }
    void setIntervalSeconds(int value) { m_intervalSeconds = value; }
    
    
    @SjmpProperty(value="value being changed by timer", showGraph = true)
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

    @SjmpAction(value = "Starts timer", requireConfirm = true)
    public void startTimer()
    {
        synchronized (m_lock)
        {
            if (m_thread == null)
            {
                m_thread = new Thread(() -> threadFunc());
                m_thread.start();
            }
        }
    }

    @SjmpAction("Stops timer")
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

    @SjmpAction("Returns true if the timer is currently running")
    public boolean isTimerRunning()
    {
        return m_thread != null;
    }

    @SjmpAction("Returns same value that is passed as paramerter")
    public String returnSame(@SjmpActionParameter("parameter to be returned") String param)
    {
        return param;
    }

	@Override
	public int getAnswer()
	{
		return 42;
	}

}
