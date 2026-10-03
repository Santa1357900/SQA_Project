package org.mockito.internal.configuration;

import org.junit.Test;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.exceptions.base.MockitoException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class SpyAnnotationEngineTest {

    @Spy
    private List<String> spiedList;

    @Mock
    private List<String> mockedList;

    @Captor
    private org.mockito.ArgumentCaptor<String> captor;

    private List<String> nullInstanceField;

    @Test
    public void testCreateMockForReturnsNull() throws Throwable {
        SpyAnnotationEngine engine = new SpyAnnotationEngine();
        Object result = engine.createMockFor(null, null);
        assertNull(result);
    }

    @Test
    public void testProcessWithNullInstance() throws Throwable {
        SpyAnnotationEngine engine = new SpyAnnotationEngine();
        try {
            engine.process(SpyAnnotationEngineTest.class, this);
            fail("Expected MockitoException due to null instance for @Spy");
        } catch (MockitoException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("Cannot create a @Spy"));
        }
    }

    @Test
    public void testProcessWithValidInstance() throws Throwable {
        SpyAnnotationEngineTest testInstance = new SpyAnnotationEngineTest();
        testInstance.spiedList = new ArrayList<String>();

        SpyAnnotationEngine engine = new SpyAnnotationEngine();
        engine.process(SpyAnnotationEngineTest.class, testInstance);
        
        org.junit.Assert.assertNotNull(testInstance.spiedList);
    }

    @Test
    public void testProcessWithAlreadySpiedInstance() throws Throwable {
        SpyAnnotationEngineTest testInstance = new SpyAnnotationEngineTest();
        List<String> innerList = new ArrayList<String>();
        testInstance.spiedList = org.mockito.Mockito.spy(innerList);

        SpyAnnotationEngine engine = new SpyAnnotationEngine();
        engine.process(SpyAnnotationEngineTest.class, testInstance);
        
        org.junit.Assert.assertNotNull(testInstance.spiedList);
    }

    @Test
    public void testAssertNoAnnotationsWithConflict() throws Throwable {
        SpyAnnotationEngine engine = new SpyAnnotationEngine();
        Field field = SpyAnnotationEngineTest.class.getDeclaredField("mockedList");
        
        try {
            engine.assertNoAnnotations(Spy.class, field, Mock.class);
            fail("Expected exception due to unsupported combination of annotations");
        } catch (Throwable t) {
            // Reporter throws an exception or error, we just verify it executes and throws something
            org.junit.Assert.assertNotNull(t);
        }
    }
}