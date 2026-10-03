package org.mockito.internal.stubbing.defaultanswers;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReturnsDeepStubsTest {

    @Test
    public void testInstantiation() throws Throwable {
        ReturnsDeepStubs deepStubs = new ReturnsDeepStubs();
        assertNotNull(deepStubs);
    }

    @Test(expected = NullPointerException.class)
    public void testAnswerWithNullInvocation() throws Throwable {
        ReturnsDeepStubs deepStubs = new ReturnsDeepStubs();
        deepStubs.answer(null);
    }
}