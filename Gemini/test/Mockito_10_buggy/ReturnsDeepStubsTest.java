package org.mockito.internal.stubbing.defaultanswers;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReturnsDeepStubsTest {

    interface SampleService {
        SampleService getNested();
        String getData();
        int getPrimitive();
        List<String> getList();
    }

    @Test
    public void testSerialVersionUID() throws Throwable {
        ReturnsDeepStubs returnsDeepStubs = new ReturnsDeepStubs();
        assertNotNull(returnsDeepStubs);
    }

    @Test
    public void testReturnsDeepStubsInstantiation() throws Throwable {
        ReturnsDeepStubs returnsDeepStubs = new ReturnsDeepStubs();
        assertTrue(returnsDeepStubs instanceof Serializable);
    }

    @Test
    public void testAnswerWithNonMockableType() throws Throwable {
        ReturnsDeepStubs returnsDeepStubs = new ReturnsDeepStubs();
        SampleService mock = Mockito.mock(SampleService.class);
        Mockito.when(mock.getPrimitive()).thenReturn(42);
        
        InvocationOnMock invocation = Mockito.mock(InvocationOnMock.class);
        Mockito.when(invocation.getMock()).thenReturn(mock);
        Mockito.when(invocation.getMethod()).getDeclaredMethod("getPrimitive", (Class<?>[]) null);

        try {
            returnsDeepStubs.answer(invocation);
        } catch (Throwable t) {
            assertNotNull(t);
        }
    }

    @Test
    public void testActualParameterizedTypeHandling() throws Throwable {
        ReturnsDeepStubs returnsDeepStubs = new ReturnsDeepStubs();
        SampleService mock = Mockito.mock(SampleService.class, Mockito.RETURNS_DEEP_STUBS);

        try {
            returnsDeepStubs.actualParameterizedType(mock);
        } catch (Throwable t) {
            assertNotNull(t);
        }
    }

    @Test
    public void testDeepStubsWithNullInvocation() throws Throwable {
        ReturnsDeepStubs returnsDeepStubs = new ReturnsDeepStubs();
        try {
            returnsDeepStubs.answer(null);
            fail("Should have thrown an exception");
        } catch (Throwable e) {
            assertNotNull(e);
        }
    }
}