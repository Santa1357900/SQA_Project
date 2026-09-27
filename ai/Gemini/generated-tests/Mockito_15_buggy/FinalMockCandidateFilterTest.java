package org.mockito.internal.configuration.injection;

import org.junit.Test;
import org.mockito.exceptions.base.MockitoException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.*;

public class FinalMockCandidateFilterTest {

    private static class SampleTestClass {
        public Object sampleField;
    }

    @Test
    public void testFilterCandidateWithSingleMock() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();
        Object mock = new Object();
        mocks.add(mock);

        Field field = SampleTestClass.class.getDeclaredField("sampleField");
        SampleTestClass instance = new SampleTestClass();

        OngoingInjecter injecter = filter.filterCandidate(mocks, field, instance);
        assertNotNull(injecter);

        boolean result = injecter.thenInject();
        assertTrue(result);
        assertEquals(mock, instance.sampleField);
    }

    @Test
    public void testFilterCandidateWithZeroMocks() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();

        Field field = SampleTestClass.class.getDeclaredField("sampleField");
        SampleTestClass instance = new SampleTestClass();

        OngoingInjecter injecter = filter.filterCandidate(mocks, field, instance);
        assertNotNull(injecter);

        boolean result = injecter.thenInject();
        assertFalse(result);
        assertNull(instance.sampleField);
    }

    @Test
    public void testFilterCandidateWithMultipleMocks() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add(new Object());
        mocks.add(new Object());

        Field field = SampleTestClass.class.getDeclaredField("sampleField");
        SampleTestClass instance = new SampleTestClass();

        OngoingInjecter injecter = filter.filterCandidate(mocks, field, instance);
        assertNotNull(injecter);

        boolean result = injecter.thenInject();
        assertFalse(result);
        assertNull(instance.sampleField);
    }

    @Test
    public void testFilterCandidateInjectionFailureThrowsMockitoException() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add(new Object());

        // Pass a field and instance that are mismatched to force FieldSetter to throw an exception
        Field field = SampleTestClass.class.getDeclaredField("sampleField");
        Object invalidInstance = new Object(); // Not an instance of SampleTestClass

        OngoingInjecter injecter = filter.filterCandidate(mocks, field, invalidInstance);
        assertNotNull(injecter);

        try {
            injecter.thenInject();
            fail("Expected MockitoException due to invalid injection target");
        } catch (MockitoException e) {
            assertTrue(e.getMessage() != null);
        }
    }
}