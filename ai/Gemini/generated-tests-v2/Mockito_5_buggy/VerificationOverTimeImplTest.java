package org.mockito.internal.verification;

import org.junit.Test;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.exceptions.verification.junit.ArgumentsAreDifferent;
import org.mockito.internal.util.Timer;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.verification.VerificationMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VerificationOverTimeImplTest {

    private static class DummyVerificationMode implements VerificationMode {
        private int verifyCount = 0;
        private boolean shouldThrow = false;
        private AssertionError errorToThrow = new MockitoAssertionError("fail");

        public DummyVerificationMode(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }

        public DummyVerificationMode(boolean shouldThrow, AssertionError errorToThrow) {
            this.shouldThrow = shouldThrow;
            this.errorToThrow = errorToThrow;
        }

        public void verify(VerificationData data) {
            verifyCount++;
            if (shouldThrow) {
                throw errorToThrow;
            }
        }

        public int getVerifyCount() {
            return verifyCount;
        }
    }

    private static class DummyTimer extends Timer {
        private boolean counting = true;
        private int count = 0;

        public DummyTimer(long durationMillis) {
            super(durationMillis);
        }

        public void start() {
            // do nothing
        }

        public boolean isCounting() {
            if (count++ > 2) {
                counting = false;
            }
            return counting;
        }
    }

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        VerificationMode delegate = new DummyVerificationMode(false);
        Timer timer = new Timer(100);
        VerificationOverTimeImpl impl1 = new VerificationOverTimeImpl(10, 100, delegate, true);
        VerificationOverTimeImpl impl2 = new VerificationOverTimeImpl(10, 100, delegate, true, timer);

        assertEquals(10L, impl1.getPollingPeriod());
        assertEquals(100L, impl1.getDuration());
        assertEquals(delegate, impl1.getDelegate());

        assertEquals(10L, impl2.getPollingPeriod());
        assertEquals(100L, impl2.getDuration());
        assertEquals(delegate, impl2.getDelegate());
    }

    @Test
    public void testVerifySuccessReturnOnSuccessTrue() throws Throwable {
        DummyVerificationMode delegate = new DummyVerificationMode(false);
        VerificationOverTimeImpl impl = new VerificationOverTimeImpl(10, 50, delegate, true);
        
        impl.verify(null);
        assertTrue(delegate.getVerifyCount() > 0);
    }

    @Test
    public void testVerifySuccessReturnOnSuccessFalse() throws Throwable {
        DummyVerificationMode delegate = new DummyVerificationMode(false);
        Timer timer = new DummyTimer(50);
        VerificationOverTimeImpl impl = new VerificationOverTimeImpl(1, 50, delegate, false, timer);

        impl.verify(null);
        assertTrue(delegate.getVerifyCount() > 1);
    }

    @Test
    public void testVerifyFailureThrowsError() throws Throwable {
        MockitoAssertionError expectedError = new MockitoAssertionError("Always fail");
        DummyVerificationMode delegate = new DummyVerificationMode(true, expectedError);
        Timer timer = new DummyTimer(50);
        VerificationOverTimeImpl impl = new VerificationOverTimeImpl(1, 50, delegate, true, timer);

        try {
            impl.verify(null);
            fail("Expected MockitoAssertionError");
        } catch (MockitoAssertionError e) {
            assertEquals("Always fail", e.getMessage());
        }
    }

    @Test
    public void testVerifyArgumentsAreDifferentException() throws Throwable {
        ArgumentsAreDifferent expectedError = new ArgumentsAreDifferent("Args different");
        DummyVerificationMode delegate = new DummyVerificationMode(true, expectedError);
        Timer timer = new DummyTimer(50);
        VerificationOverTimeImpl impl = new VerificationOverTimeImpl(1, 50, delegate, true, timer);

        try {
            impl.verify(null);
            fail("Expected ArgumentsAreDifferent");
        } catch (ArgumentsAreDifferent e) {
            assertTrue(e.getMessage().contains("Args different"));
        }
    }

    @Test
    public void testCanRecoverFromFailureWithAtMost() throws Throwable {
        VerificationMode atMost = new AtMost(1);
        VerificationOverTimeImpl impl = new VerificationOverTimeImpl(10, 50, atMost, true);

        // canRecoverFromFailure is protected, but we can test via reflection or subclassing, 
        // or just test behavior when delegate is AtMost and throws exception.
        DummyVerificationMode delegate = new DummyVerificationMode(true, new MockitoAssertionError("fail")) {
            // override to simulate AtMost behavior for instance check
        };
        
        // Directly test canRecoverFromFailure via anonymous subclass since it's protected
        VerificationOverTimeImpl subclass = new VerificationOverTimeImpl(10, 50, delegate, true) {
            public boolean testCanRecover(VerificationMode mode) {
                return canRecoverFromFailure(mode);
            }
        };

        assertTrue(!subclass.testCanRecover(new AtMost(1)));
        assertTrue(!subclass.testCanRecover(new NoMoreInteractions()));
        assertTrue(subclass.testCanRecover(delegate));
    }

    @Test
    public void testSleepInterruption() throws Throwable {
        // Test that sleep handles InterruptedException gracefully by interrupting the thread
        Thread.currentThread().interrupt();
        DummyVerificationMode delegate = new DummyVerificationMode(true, new MockitoAssertionError("fail"));
        Timer timer = new DummyTimer(10);
        VerificationOverTimeImpl impl = new VerificationOverTimeImpl(1, 10, delegate, true, timer);

        try {
            impl.verify(null);
        } catch (MockitoAssertionError e) {
            // expected
        }
        // Clear interrupted status
        Thread.interrupted();
    }
}