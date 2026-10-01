package org.mockito.internal.verification;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.internal.util.Timer;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.verification.VerificationMode;

public class VerificationOverTimeImplClaudeTest {

    private static class AlwaysPassingMode implements VerificationMode {
        public int callCount = 0;
        public void verify(VerificationData data) {
            callCount++;
        }
    }

    private static class AlwaysFailingMode implements VerificationMode {
        public int callCount = 0;
        public void verify(VerificationData data) {
            callCount++;
            throw new MockitoAssertionError("always fails");
        }
    }

    private static class FailThenPassMode implements VerificationMode {
        public int callCount = 0;
        private final int failUntil;
        FailThenPassMode(int failUntil) {
            this.failUntil = failUntil;
        }
        public void verify(VerificationData data) {
            callCount++;
            if (callCount <= failUntil) {
                throw new MockitoAssertionError("not yet satisfied");
            }
        }
    }

    // Constructor (4-arg): pollingPeriodMillis must be stored and returned by getter
    @Test
    public void testConstructorFourArgs_storesPollingPeriod() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(7L, 100L, delegate, true);
        assertEquals(7L, v.getPollingPeriod());
    }

    // Constructor (4-arg): durationMillis must be stored and returned by getter
    @Test
    public void testConstructorFourArgs_storesDuration() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(7L, 123L, delegate, true);
        assertEquals(123L, v.getDuration());
    }

    // Constructor (4-arg): delegate reference must be stored and returned by getter
    @Test
    public void testConstructorFourArgs_storesDelegate() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(7L, 123L, delegate, true);
        assertSame(delegate, v.getDelegate());
    }

    // Constructor (5-arg, explicit Timer): all fields stored correctly
    @Test
    public void testConstructorFiveArgs_storesPollingPeriodDurationDelegate() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        Timer timer = new Timer(50L);
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 50L, delegate, false, timer);
        assertEquals(5L, v.getPollingPeriod());
        assertEquals(50L, v.getDuration());
        assertSame(delegate, v.getDelegate());
    }

    // getPollingPeriod: returns exact value given at construction, including zero
    @Test
    public void testGetPollingPeriod_returnsValueGivenInConstructor_zero() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(0L, 10L, delegate, true);
        assertEquals(0L, v.getPollingPeriod());
    }

    // getDuration: returns exact value given at construction, including zero
    @Test
    public void testGetDuration_returnsValueGivenInConstructor_zero() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(10L, 0L, delegate, true);
        assertEquals(0L, v.getDuration());
    }

    // getDelegate: returns the same instance passed in, not a copy
    @Test
    public void testGetDelegate_returnsSameDelegateInstance() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(10L, 10L, delegate, true);
        assertSame(delegate, v.getDelegate());
    }

    // verify(): returnOnSuccess=true, delegate succeeds on first call -> returns without exception
    @Test
    public void testVerify_delegateSucceedsImmediately_returnOnSuccessTrue_returnsWithoutException() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(1000L, 10000L, delegate, true);
        v.verify(null);
        assertTrue(delegate.callCount >= 1);
    }

    // verify(): returnOnSuccess=true must return as soon as delegate is satisfied (single call, no polling loop)
    @Test
    public void testVerify_delegateSucceedsImmediately_returnOnSuccessTrue_callsDelegateExactlyOnce() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(1000L, 10000L, delegate, true);
        v.verify(null);
        assertEquals(1, delegate.callCount);
    }

    // verify(): returnOnSuccess=false, delegate always succeeds -> waits full duration but still succeeds overall
    @Test
    public void testVerify_delegateSucceedsImmediately_returnOnSuccessFalse_returnsWithoutException() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 30L, delegate, false);
        v.verify(null);
        assertTrue(delegate.callCount >= 1);
    }

    // verify(): delegate never satisfied, returnOnSuccess=true -> throws MockitoAssertionError after timeout
    @Test
    public void testVerify_delegateAlwaysFails_returnOnSuccessTrue_throwsMockitoAssertionErrorAfterTimeout() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 40L, delegate, true);
        try {
            v.verify(null);
            fail("expected MockitoAssertionError");
        } catch (MockitoAssertionError expected) {
            assertTrue(expected.getMessage().contains("always fails"));
        }
    }

    // verify(): delegate never satisfied, returnOnSuccess=false -> also throws after timeout
    @Test
    public void testVerify_delegateAlwaysFails_returnOnSuccessFalse_throwsMockitoAssertionErrorAfterTimeout() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 40L, delegate, false);
        try {
            v.verify(null);
            fail("expected MockitoAssertionError");
        } catch (MockitoAssertionError expected) {
            assertTrue(delegate.callCount >= 1);
        }
    }

    // verify(): recoverable delegate failure is retried (polled) multiple times before giving up
    @Test
    public void testVerify_delegateAlwaysFails_pollsDelegateMultipleTimesBeforeThrowing() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 60L, delegate, true);
        try {
            v.verify(null);
            fail("expected MockitoAssertionError");
        } catch (MockitoAssertionError expected) {
            assertTrue("expected delegate to be polled more than once, was " + delegate.callCount,
                    delegate.callCount > 1);
        }
    }

    // verify(): delegate fails then later succeeds, returnOnSuccess=true -> returns once satisfied, no exception
    @Test
    public void testVerify_delegateFailsThenSucceeds_returnOnSuccessTrue_returnsOnceSatisfied() throws Throwable {
        FailThenPassMode delegate = new FailThenPassMode(2);
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 5000L, delegate, true);
        v.verify(null);
        assertTrue(delegate.callCount >= 3);
    }

    // verify(): delegate fails then succeeds, returnOnSuccess=false -> overall success once final state is success
    @Test
    public void testVerify_delegateFailsThenSucceeds_returnOnSuccessFalse_returnsWithoutExceptionAfterDuration() throws Throwable {
        FailThenPassMode delegate = new FailThenPassMode(1);
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 40L, delegate, false);
        v.verify(null);
        assertTrue(delegate.callCount > 1);
    }

    // verify(): the error ultimately thrown must be the same type/message as the delegate's last failure
    @Test
    public void testVerify_exceptionMessagePropagatedWhenDelegateNeverSucceeds() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 30L, delegate, true);
        try {
            v.verify(null);
            fail("expected MockitoAssertionError");
        } catch (MockitoAssertionError expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // verify(): boundary case duration=0 -- contract says delegate is polled until satisfied; a delegate
    // that is never satisfied must still result in a thrown error, not a silent success.
    @Test
    public void testVerify_zeroDurationDelegateAlwaysFails_stillThrows() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(1L, 0L, delegate, true);
        try {
            v.verify(null);
            fail("expected MockitoAssertionError because delegate never succeeded");
        } catch (MockitoAssertionError expected) {
            assertTrue(expected.getMessage().contains("always fails"));
        }
    }

    // verify(): small positive duration, delegate always passes -> must not throw
    @Test
    public void testVerify_smallPositiveDuration_delegateAlwaysPasses_noException() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(1L, 5L, delegate, true);
        v.verify(null);
        assertTrue(delegate.callCount >= 1);
    }

    // canRecoverFromFailure: a VerificationMode that is neither AtMost nor NoMoreInteractions is recoverable
    @Test
    public void testCanRecoverFromFailure_customVerificationMode_returnsTrue() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 30L, delegate, true);
        assertTrue(v.canRecoverFromFailure(delegate));
    }

    // canRecoverFromFailure: null is not an instance of AtMost/NoMoreInteractions -> recoverable (true)
    @Test
    public void testCanRecoverFromFailure_null_returnsTrue() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 30L, delegate, true);
        assertTrue(v.canRecoverFromFailure(null));
    }

    // verify(): using explicit Timer (5-arg constructor), success with returnOnSuccess=true returns immediately
    @Test
    public void testVerify_fiveArgConstructorWithRealTimer_delegateSucceeds_returnOnSuccessTrue() throws Throwable {
        AlwaysPassingMode delegate = new AlwaysPassingMode();
        Timer timer = new Timer(10000L);
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(100L, 10000L, delegate, true, timer);
        v.verify(null);
        assertEquals(1, delegate.callCount);
    }

    // verify(): using explicit Timer (5-arg constructor), delegate never satisfied -> throws after timeout
    @Test
    public void testVerify_fiveArgConstructorWithRealTimer_delegateAlwaysFails_throwsAfterTimeout() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        Timer timer = new Timer(30L);
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(5L, 30L, delegate, false, timer);
        try {
            v.verify(null);
            fail("expected MockitoAssertionError");
        } catch (MockitoAssertionError expected) {
            assertTrue(delegate.callCount >= 1);
        }
    }

    // verify(): delegate fails on every call but recoverable -> ensures sleeping/retrying happens before
    // the final failure is thrown (duration long enough to guarantee multiple attempts)
    @Test
    public void testVerify_delegateAlwaysFails_longerDuration_retriesSeveralTimesThenThrows() throws Throwable {
        AlwaysFailingMode delegate = new AlwaysFailingMode();
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(10L, 100L, delegate, false);
        try {
            v.verify(null);
            fail("expected MockitoAssertionError");
        } catch (MockitoAssertionError expected) {
            assertTrue("expected several retries, was " + delegate.callCount, delegate.callCount >= 3);
        }
    }

    // verify(): FailThenPassMode with failUntil=0 behaves exactly like AlwaysPassingMode on first call
    @Test
    public void testVerify_failThenPassWithZeroFailures_behavesLikeImmediateSuccess() throws Throwable {
        FailThenPassMode delegate = new FailThenPassMode(0);
        VerificationOverTimeImpl v = new VerificationOverTimeImpl(1000L, 10000L, delegate, true);
        v.verify(null);
        assertEquals(1, delegate.callCount);
    }
}
