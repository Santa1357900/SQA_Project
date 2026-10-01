package org.mockito.internal.configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.util.MockUtil;

public class SpyAnnotationEngineClaudeTest {

    private SpyAnnotationEngine engine;

    @Before
    public void setUp() throws Throwable {
        engine = new SpyAnnotationEngine();
    }

    static class NoAnnotationHolder {
        List plainField = new LinkedList();
    }

    static class EmptyListSpyHolder {
        @Spy
        List spyField = new LinkedList();
    }

    static class PopulatedListSpyHolder {
        @Spy
        List spyField = new ArrayList(Arrays.asList("a", "b"));
    }

    static class NullInstanceSpyHolder {
        @Spy
        List spyField;
    }

    static class AlreadyMockSpyHolder {
        @Spy
        List spyField = Mockito.mock(List.class);
    }

    static class AlreadySpiedSpyHolder {
        @Spy
        List spyField = Mockito.spy(new LinkedList());
    }

    static class TwoSpyFieldsHolder {
        @Spy
        List spyField1 = new LinkedList();
        @Spy
        List spyField2 = new ArrayList();
    }

    static class MixedFieldsHolder {
        List plainField = new LinkedList();
        @Spy
        List spyField = new LinkedList();
    }

    static class PrivateSpyHolder {
        @Spy
        private List spyField = new LinkedList();
    }

    static class SpyAndMockHolder {
        @Spy
        @Mock
        List spyField = new LinkedList();
    }

    static class SpyAndCaptorHolder {
        @Spy
        @Captor
        List spyField = new LinkedList();
    }

    static class SpyAndDeprecatedMockHolder {
        @Spy
        @MockitoAnnotations.Mock
        List spyField = new LinkedList();
    }

    static class NoFieldsHolder {
    }

    // Covers createMockFor which ignores its inputs and always returns null
    @Test
    public void testCreateMockFor_validAnnotationAndField_returnsNull() throws Throwable {
        Field field = EmptyListSpyHolder.class.getDeclaredField("spyField");
        Annotation annotation = field.getAnnotation(Spy.class);
        Object result = engine.createMockFor(annotation, field);
        assertNull(result);
    }

    // Covers createMockFor called with null arguments (body never dereferences them)
    @Test
    public void testCreateMockFor_nullAnnotationAndField_returnsNull() throws Throwable {
        Object result = engine.createMockFor(null, null);
        assertNull(result);
    }

    // Covers for-loop iteration where field.isAnnotationPresent(Spy.class) is false -> no-op
    @Test
    public void testProcess_noSpyAnnotatedField_fieldValueUnchanged() throws Throwable {
        NoAnnotationHolder holder = new NoAnnotationHolder();
        List original = holder.plainField;
        engine.process(NoAnnotationHolder.class, holder);
        assertSame(original, holder.plainField);
    }

    // Covers instance != null, MockUtil.isMock(instance) false -> field.set(testClass, Mockito.spy(instance))
    @Test
    public void testProcess_spyFieldWithValidInstance_fieldBecomesSpyMock() throws Throwable {
        EmptyListSpyHolder holder = new EmptyListSpyHolder();
        engine.process(EmptyListSpyHolder.class, holder);
        assertTrue(new MockUtil().isMock(holder.spyField));
    }

    // Covers that Mockito.spy wraps the real instance and preserves its real state/behavior
    @Test
    public void testProcess_spyFieldWithPopulatedInstance_spyDelegatesRealBehavior() throws Throwable {
        PopulatedListSpyHolder holder = new PopulatedListSpyHolder();
        engine.process(PopulatedListSpyHolder.class, holder);
        assertEquals(2, holder.spyField.size());
    }

    // Covers instance == null branch throwing MockitoException
    @Test
    public void testProcess_spyFieldNullInstance_throwsMockitoException() throws Throwable {
        NullInstanceSpyHolder holder = new NullInstanceSpyHolder();
        try {
            engine.process(NullInstanceSpyHolder.class, holder);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("missing"));
        }
    }

    // Covers message construction for the missing-instance exception, must include field name
    @Test
    public void testProcess_spyFieldNullInstance_messageContainsFieldName() throws Throwable {
        NullInstanceSpyHolder holder = new NullInstanceSpyHolder();
        try {
            engine.process(NullInstanceSpyHolder.class, holder);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("spyField"));
        }
    }

    // Covers MockUtil.isMock(instance) true branch -> Mockito.reset(instance) instead of re-spying
    @Test
    public void testProcess_spyFieldAlreadyMock_resetsInsteadOfReSpying() throws Throwable {
        AlreadyMockSpyHolder holder = new AlreadyMockSpyHolder();
        Mockito.when(holder.spyField.size()).thenReturn(99);
        engine.process(AlreadyMockSpyHolder.class, holder);
        assertEquals(0, holder.spyField.size());
    }

    // Covers MockUtil.isMock(instance) true branch when instance is an existing spy: reset keeps identity
    @Test
    public void testProcess_spyFieldAlreadySpied_resetsSpyWithoutError() throws Throwable {
        AlreadySpiedSpyHolder holder = new AlreadySpiedSpyHolder();
        List before = holder.spyField;
        engine.process(AlreadySpiedSpyHolder.class, holder);
        assertSame(before, holder.spyField);
    }

    // Covers for-loop processing more than one annotated field (multiple iterations)
    @Test
    public void testProcess_multipleSpyFields_bothFieldsBecomeSpies() throws Throwable {
        TwoSpyFieldsHolder holder = new TwoSpyFieldsHolder();
        engine.process(TwoSpyFieldsHolder.class, holder);
        assertTrue(new MockUtil().isMock(holder.spyField1));
        assertTrue(new MockUtil().isMock(holder.spyField2));
    }

    // Covers loop skipping a non-annotated field while still processing the annotated one
    @Test
    public void testProcess_mixedFields_onlyAnnotatedFieldModified() throws Throwable {
        MixedFieldsHolder holder = new MixedFieldsHolder();
        List originalPlain = holder.plainField;
        engine.process(MixedFieldsHolder.class, holder);
        assertSame(originalPlain, holder.plainField);
        assertTrue(new MockUtil().isMock(holder.spyField));
    }

    // Covers finally block restoring field.setAccessible(wasAccessible) to its original false value
    @Test
    public void testProcess_privateSpyField_accessibilityRestoredAfterProcessing() throws Throwable {
        PrivateSpyHolder holder = new PrivateSpyHolder();
        Field field = PrivateSpyHolder.class.getDeclaredField("spyField");
        assertFalse(field.isAccessible());
        engine.process(PrivateSpyHolder.class, holder);
        assertFalse(field.isAccessible());
    }

    // Covers assertNoAnnotations detecting undesired @Mock annotation alongside @Spy via process()
    @Test
    public void testProcess_spyAndMockAnnotationsConflict_throwsMockitoException() throws Throwable {
        SpyAndMockHolder holder = new SpyAndMockHolder();
        try {
            engine.process(SpyAndMockHolder.class, holder);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Covers assertNoAnnotations detecting undesired @Captor annotation alongside @Spy via process()
    @Test
    public void testProcess_spyAndCaptorAnnotationsConflict_throwsMockitoException() throws Throwable {
        SpyAndCaptorHolder holder = new SpyAndCaptorHolder();
        try {
            engine.process(SpyAndCaptorHolder.class, holder);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Covers assertNoAnnotations detecting undesired deprecated MockitoAnnotations.Mock alongside @Spy
    @Test
    public void testProcess_spyAndDeprecatedMockAnnotationsConflict_throwsMockitoException() throws Throwable {
        SpyAndDeprecatedMockHolder holder = new SpyAndDeprecatedMockHolder();
        try {
            engine.process(SpyAndDeprecatedMockHolder.class, holder);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Covers for-loop with zero iterations when context has no declared fields
    @Test
    public void testProcess_classWithNoDeclaredFields_fieldsArrayEmpty() throws Throwable {
        assertEquals(0, NoFieldsHolder.class.getDeclaredFields().length);
        NoFieldsHolder holder = new NoFieldsHolder();
        engine.process(NoFieldsHolder.class, holder);
        assertEquals(0, NoFieldsHolder.class.getDeclaredFields().length);
    }

    // Covers inner for-loop where the undesired annotations array has no match -> completes without throwing
    @Test
    public void testAssertNoAnnotations_noUndesiredAnnotationPresent_noExceptionThrown() throws Throwable {
        Field field = EmptyListSpyHolder.class.getDeclaredField("spyField");
        engine.assertNoAnnotations(Spy.class, field, Mock.class, Captor.class);
        assertTrue(field.isAnnotationPresent(Spy.class));
    }

    // Bug hunt: the thrown message must name the actual conflicting annotation (Mock), not
    // annotation.getClass().getSimpleName() which would wrongly read "Class"
    @Test
    public void testAssertNoAnnotations_undesiredMockAnnotationPresent_messageNamesMockNotClass() throws Throwable {
        Field field = SpyAndMockHolder.class.getDeclaredField("spyField");
        try {
            engine.assertNoAnnotations(Spy.class, field, Mock.class);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Mock"));
        }
    }

    // Bug hunt: ensures the thrown message identifies the actual conflicting annotation (Captor)
    @Test
    public void testAssertNoAnnotations_undesiredCaptorAnnotationPresent_messageNamesCaptor() throws Throwable {
        Field field = SpyAndCaptorHolder.class.getDeclaredField("spyField");
        try {
            engine.assertNoAnnotations(Spy.class, field, Captor.class);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Captor"));
        }
    }

    // Covers varargs with zero undesired-annotation elements -> loop body never executes
    @Test
    public void testAssertNoAnnotations_emptyUndesiredArray_noExceptionThrown() throws Throwable {
        Field field = EmptyListSpyHolder.class.getDeclaredField("spyField");
        engine.assertNoAnnotations(Spy.class, field);
        assertEquals("spyField", field.getName());
    }

    // Covers loop iterating multiple undesired annotation array elements, throws once a match is found
    @Test
    public void testAssertNoAnnotations_fieldWithMultipleUndesiredAnnotations_throwsOnFirstMatch() throws Throwable {
        Field field = SpyAndMockHolder.class.getDeclaredField("spyField");
        try {
            engine.assertNoAnnotations(Spy.class, field, Mock.class, Captor.class);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }
}
