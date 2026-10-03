package org.mockito.internal.configuration.injection.filter;

import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import static org.junit.Assert.*;

public class FinalMockCandidateFilterTest {

    static class SampleClass {
        private Object myField;
        public Object getMyField() {
            return myField;
        }
    }

    @Test
    public void testFilterCandidateSizeNotOne() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();
        
        Field field = SampleClass.class.getDeclaredField("myField");
        SampleClass instance = new SampleClass();

        OngoingInjecter injecter = filter.filterCandidate(mocks, field, instance);
        assertNotNull(injecter);
        
        Object result = injecter.thenInject();
        assertNull(result);
    }

    @Test
    public void testFilterCandidateMultipleMocks() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("mock1");
        mocks.add("mock2");
        
        Field field = SampleClass.class.getDeclaredField("myField");
        SampleClass instance = new SampleClass();

        OngoingInjecter injecter = filter.filterCandidate(mocks, field, instance);
        assertNotNull(injecter);
        
        Object result = injecter.thenInject();
        assertNull(result);
    }

    @Test
    public void testFilterCandidateSizeOne() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();
        Object mockValue = "theMock";
        mocks.add(mockValue);
        
        Field field = SampleClass.class.getDeclaredField("myField");
        SampleClass instance = new SampleClass();

        OngoingInjecter injecter = filter.filterCandidate(mocks, field, instance);
        assertNotNull(injecter);
        
        Object result = injecter.thenInject();
        assertEquals(mockValue, result);
        assertEquals(mockValue, instance.getMyField());
    }

    @Test
    public void testFilterCandidateInjectionFailureHandling() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Collection<Object> mocks = new ArrayList<Object>();
        Object mockValue = "theMock";
        mocks.add(mockValue);

        // Pass an incompatible field instance or invalid field to trigger RuntimeException in setter
        Field field = SampleClass.class.getDeclaredField("myField");
        // Passing null instance will cause NullPointerException inside BeanPropertySetter or FieldSetter
        OngoingInjecter injecter = filter.filterCandidate(mocks, field, null);
        assertNotNull(injecter);

        try {
            injecter.thenInject();
            fail("Expected Reporter to handle exception or exception to be thrown");
        } catch (Throwable t) {
            // Expected either NullPointerException or wrapped/reported exception from Reporter
            assertTrue(t instanceof RuntimeException || t instanceof NullPointerException);
        }
    }
}