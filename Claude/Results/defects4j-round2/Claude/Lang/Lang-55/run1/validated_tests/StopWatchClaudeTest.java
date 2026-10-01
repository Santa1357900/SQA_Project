package org.apache.commons.lang.time;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StopWatchClaudeTest {

    private StopWatch watch;

    @Before
    public void setUp() throws Throwable {
        watch = new StopWatch();
    }

    // constructor produces an unstarted watch where getTime() is 0
    @Test
    public void testConstructor_createsUnstartedWatch() throws Throwable {
        assertEquals(0L, watch.getTime());
    }

    // start() from UNSTARTED succeeds, watch becomes RUNNING
    @Test
    public void testStart_fromUnstarted_succeeds() throws Throwable {
        watch.start();
        assertTrue(watch.getTime() >= 0);
    }

    // start() called twice without reset throws "already started"
    @Test
    public void testStart_calledTwice_throwsIllegalStateException() throws Throwable {
        watch.start();
        try {
            watch.start();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // start() after stop() without reset() throws "must be reset" branch
    @Test
    public void testStart_afterStopWithoutReset_throwsIllegalStateException() throws Throwable {
        watch.start();
        watch.stop();
        try {
            watch.start();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // start() after reset() succeeds again
    @Test
    public void testStart_afterReset_succeeds() throws Throwable {
        watch.start();
        watch.stop();
        watch.reset();
        watch.start();
        assertTrue(watch.getTime() >= 0);
    }

    // stop() without start() throws "not running"
    @Test
    public void testStop_withoutStart_throwsIllegalStateException() throws Throwable {
        try {
            watch.stop();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // stop() after start() succeeds, RUNNING -> STOPPED branch
    @Test
    public void testStop_afterStart_succeeds() throws Throwable {
        watch.start();
        watch.stop();
        assertTrue(watch.getTime() >= 0);
    }

    // stop() after suspend() succeeds, SUSPENDED -> STOPPED branch
    @Test
    public void testStop_afterSuspend_succeeds() throws Throwable {
        watch.start();
        watch.suspend();
        watch.stop();
        assertTrue(watch.getTime() >= 0);
    }

    // stop() called twice throws on the second call
    @Test
    public void testStop_calledTwice_throwsIllegalStateException() throws Throwable {
        watch.start();
        watch.stop();
        try {
            watch.stop();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // reset() clears state so getTime() reports 0 again
    @Test
    public void testReset_afterStopAndStart_clearsTime() throws Throwable {
        watch.start();
        watch.stop();
        watch.reset();
        assertEquals(0L, watch.getTime());
    }

    // reset() on an already UNSTARTED watch does not throw
    @Test
    public void testReset_whenUnstarted_doesNotThrow() throws Throwable {
        watch.reset();
        assertEquals(0L, watch.getTime());
    }

    // split() without start() throws "not running"
    @Test
    public void testSplit_withoutStart_throwsIllegalStateException() throws Throwable {
        try {
            watch.split();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // split() after start() succeeds and getSplitTime() becomes available
    @Test
    public void testSplit_afterStart_succeeds() throws Throwable {
        watch.start();
        watch.split();
        assertTrue(watch.getSplitTime() >= 0);
    }



    // unsplit() without a prior split() throws "has not been split"
    @Test
    public void testUnsplit_withoutSplit_throwsIllegalStateException() throws Throwable {
        try {
            watch.unsplit();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // unsplit() after split() succeeds and allows a fresh split() afterwards
    @Test
    public void testUnsplit_afterSplit_allowsSplitAgain() throws Throwable {
        watch.start();
        watch.split();
        watch.unsplit();
        watch.split();
        assertTrue(watch.getSplitTime() >= 0);
    }

    // getSplitTime() after unsplit() throws since splitState reverted to UNSPLIT
    @Test
    public void testGetSplitTime_afterUnsplit_throwsIllegalStateException() throws Throwable {
        watch.start();
        watch.split();
        watch.unsplit();
        try {
            watch.getSplitTime();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // suspend() without start() throws "must be running to suspend"
    @Test
    public void testSuspend_withoutStart_throwsIllegalStateException() throws Throwable {
        try {
            watch.suspend();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // suspend() after start() succeeds, RUNNING -> SUSPENDED
    @Test
    public void testSuspend_afterStart_succeeds() throws Throwable {
        watch.start();
        watch.suspend();
        assertTrue(watch.getTime() >= 0);
    }

    // suspend() called twice throws on second call since state is no longer RUNNING
    @Test
    public void testSuspend_calledTwice_throwsIllegalStateException() throws Throwable {
        watch.start();
        watch.suspend();
        try {
            watch.suspend();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // resume() without suspend() throws "must be suspended to resume"
    @Test
    public void testResume_withoutSuspend_throwsIllegalStateException() throws Throwable {
        try {
            watch.resume();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // resume() after start() but without suspend() throws as well
    @Test
    public void testResume_afterStartWithoutSuspend_throwsIllegalStateException() throws Throwable {
        watch.start();
        try {
            watch.resume();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // resume() after suspend() succeeds, SUSPENDED -> RUNNING, and stop afterwards works
    @Test
    public void testResume_afterSuspend_succeedsAndAllowsStop() throws Throwable {
        watch.start();
        watch.suspend();
        watch.resume();
        watch.stop();
        assertTrue(watch.getTime() >= 0);
    }

    // getTime() on UNSTARTED watch returns exactly 0
    @Test
    public void testGetTime_unstarted_returnsZero() throws Throwable {
        assertEquals(0L, watch.getTime());
    }

    // getTime() while RUNNING returns a non-negative elapsed value
    @Test
    public void testGetTime_running_returnsNonNegative() throws Throwable {
        watch.start();
        assertTrue(watch.getTime() >= 0);
    }

    // getTime() while STOPPED returns stopTime - startTime, non-negative
    @Test
    public void testGetTime_stopped_returnsNonNegative() throws Throwable {
        watch.start();
        watch.stop();
        assertTrue(watch.getTime() >= 0);
    }

    // getTime() while SUSPENDED returns stopTime - startTime, non-negative
    @Test
    public void testGetTime_suspended_returnsNonNegative() throws Throwable {
        watch.start();
        watch.suspend();
        assertTrue(watch.getTime() >= 0);
    }

    // getSplitTime() without any split() throws "must be split"
    @Test
    public void testGetSplitTime_withoutSplit_throwsIllegalStateException() throws Throwable {
        try {
            watch.getSplitTime();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // getSplitTime() after split() returns a non-negative value
    @Test
    public void testGetSplitTime_afterSplit_returnsNonNegative() throws Throwable {
        watch.start();
        watch.split();
        assertTrue(watch.getSplitTime() >= 0);
    }

    // getSplitTime() after unsplit() then split() again returns non-negative
    @Test
    public void testGetSplitTime_afterUnsplitThenSplitAgain_returnsNonNegative() throws Throwable {
        watch.start();
        watch.split();
        watch.unsplit();
        watch.split();
        assertTrue(watch.getSplitTime() >= 0);
    }

    // toString() on unstarted watch returns an ISO8601-like HMS formatted, non-empty String
    @Test
    public void testToString_unstarted_returnsFormattedString() throws Throwable {
        String s = watch.toString();
        assertNotNull(s);
        assertTrue(s.length() > 0);
        assertTrue(s.indexOf(':') >= 0);
    }

    // toString() after start()/stop() returns a formatted String containing hours:minutes:seconds.millis separators
    @Test
    public void testToString_afterStop_returnsFormattedString() throws Throwable {
        watch.start();
        watch.stop();
        String s = watch.toString();
        assertNotNull(s);
        assertTrue(s.indexOf(':') >= 0);
        assertTrue(s.indexOf('.') >= 0);
    }

    // toSplitString() after split() returns a formatted String containing separators
    @Test
    public void testToSplitString_afterSplit_returnsFormattedString() throws Throwable {
        watch.start();
        watch.split();
        String s = watch.toSplitString();
        assertNotNull(s);
        assertTrue(s.indexOf(':') >= 0);
        assertTrue(s.indexOf('.') >= 0);
    }
}
