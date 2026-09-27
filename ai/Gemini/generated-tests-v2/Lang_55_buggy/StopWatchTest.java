package org.apache.commons.lang.time;

import junit.framework.TestCase;

public class StopWatchTest extends TestCase {

    public StopWatchTest(String name) {
        super(name);
    }

    public void testConstructor() throws Throwable {
        StopWatch watch = new StopWatch();
        assertNotNull(watch);
        assertEquals(0, watch.getTime());
    }

    public void testStartAndStop() throws Throwable {
        StopWatch watch = new StopWatch();
        watch.start();
        Thread.sleep(50);
        watch.stop();
        long time = watch.getTime();
        assertTrue(time >= 0);
        
        // Test calling start without reset after stop
        boolean exceptionThrown = false;
        try {
            watch.start();
        } catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    public void testStartAlreadyStarted() throws Throwable {
        StopWatch watch = new StopWatch();
        watch.start();
        boolean exceptionThrown = false;
        try {
            watch.start();
        } catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        watch.stop();
    }

    public void testStopNotRunning() throws Throwable {
        StopWatch watch = new StopWatch();
        boolean exceptionThrown = false;
        try {
            watch.stop();
        } catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    public void testReset() throws Throwable {
        StopWatch watch = new StopWatch();
        watch.start();
        Thread.sleep(10);
        watch.stop();
        watch.reset();
        assertEquals(0, watch.getTime());
        
        // Should be able to start again after reset
        watch.start();
        watch.stop();
        assertTrue(watch.getTime() >= 0);
    }

    public void testSplitAndUnsplit() throws Throwable {
        StopWatch watch = new StopWatch();
        
        // Cannot split when unstarted
        boolean ex1 = false;
        try {
            watch.split();
        } catch (IllegalStateException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        watch.start();
        Thread.sleep(20);
        watch.split();
        long splitTime = watch.getSplitTime();
        assertTrue(splitTime >= 0);
        assertNotNull(watch.toSplitString());

        Thread.sleep(20);
        watch.unsplit();

        // Cannot unsplit when not split
        boolean ex2 = false;
        try {
            watch.unsplit();
        } catch (IllegalStateException e) {
            ex2 = true;
        }
        assertTrue(ex2);

        watch.stop();
    }

    public void testGetSplitTimeNotSplit() throws Throwable {
        StopWatch watch = new StopWatch();
        watch.start();
        boolean exceptionThrown = false;
        try {
            watch.getSplitTime();
        } catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        watch.stop();
    }

    public void testSuspendAndResume() throws Throwable {
        StopWatch watch = new StopWatch();

        // Cannot suspend when unstarted
        boolean ex1 = false;
        try {
            watch.suspend();
        } catch (IllegalStateException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        watch.start();
        Thread.sleep(20);
        watch.suspend();
        long suspendedTime = watch.getTime();

        // Cannot suspend when already suspended
        boolean ex2 = false;
        try {
            watch.suspend();
        } catch (IllegalStateException e) {
            ex2 = true;
        }
        assertTrue(ex2);

        // Cannot resume when running (well, if it was running, but here it's suspended, let's test resume first)
        Thread.sleep(20);
        watch.resume();

        // Cannot resume when not suspended
        boolean ex3 = false;
        try {
            watch.resume();
        } catch (IllegalStateException e) {
            ex3 = true;
        }
        assertTrue(ex3);

        watch.stop();
        assertTrue(watch.getTime() >= suspendedTime);
    }

    public void testGetTimeStates() throws Throwable {
        StopWatch watch = new StopWatch();
        // Unstarted state getTime()
        assertEquals(0, watch.getTime());

        watch.start();
        // Running state getTime()
        long runningTime = watch.getTime();
        assertTrue(runningTime >= 0);

        watch.suspend();
        // Suspended state getTime()
        long suspendedTime = watch.getTime();
        assertTrue(suspendedTime >= 0);

        watch.resume();
        watch.stop();
        // Stopped state getTime()
        long stoppedTime = watch.getTime();
        assertTrue(stoppedTime >= 0);
        
        assertNotNull(watch.toString());
    }
}