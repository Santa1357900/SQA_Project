package org.mockito.internal.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimerTest {

    @Test
    public void testTimerInitializationAndDuration() throws Throwable {
        Timer timer = new Timer(1000L);
        assertNotNull(timer);
    }

    @Test(expected = AssertionError.class)
    public void testIsCountingWithoutStartThrowsAssertionError() throws Throwable {
        Timer timer = new Timer(1000L);
        timer.isCounting();
    }

    @Test
    public void testTimerCountingActive() throws Throwable {
        Timer timer = new Timer(5000L);
        timer.start();
        assertTrue(timer.isCounting());
    }

    @Test
    public void testTimerCountingExpired() throws Throwable {
        // Create a timer with 0 duration so it expires immediately
        Timer timer = new Timer(0L);
        timer.start();
        
        // Small busy wait or sleep to ensure time advances past 0 ms
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2L) {
            // wait
        }
        
        assertFalse(timer.isCounting());
    }

    @Test
    public void testTimerWithNegativeDuration() throws Throwable {
        Timer timer = new Timer(-100L);
        timer.start();
        assertFalse(timer.isCounting());
    }

    @Test
    public void testRestartTimer() throws Throwable {
        Timer timer = new Timer(100L);
        timer.start();
        assertTrue(timer.isCounting());

        // Restart the timer
        timer.start();
        assertTrue(timer.isCounting());
    }
}