package com.fasterxml.jackson.databind.introspect;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;

public class AnnotationMapTest {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface DummyAnnotation1 {
        String value() default "default1";
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface DummyAnnotation2 {
        int value() default 42;
    }

    @DummyAnnotation1(value = "test1")
    private static class DummyClass1 {
    }

    @DummyAnnotation2(value = 100)
    private static class DummyClass2 {
    }

    @Test
    public void testEmptyAnnotationMap() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        assertEquals(0, map.size());
        assertNull(map.get(DummyAnnotation1.class));
        assertNotNull(map.annotations());
        assertFalse(map.annotations().iterator().hasNext());
        assertNotNull(map.toString());
    }

    @Test
    public void testAddAnnotation() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        DummyAnnotation1 ann = DummyClass1.class.getAnnotation(DummyAnnotation1.class);

        boolean changed = map.add(ann);
        assertTrue(changed);
        assertEquals(1, map.size());
        assertEquals(ann, map.get(DummyAnnotation1.class));

        // Add the same annotation instance again
        boolean changedAgain = map.add(ann);
        assertTrue(changedAgain); // _add returns (previous != null) && previous.equals(ann)
        assertEquals(1, map.size());
    }

    @Test
    public void testAddIfNotPresent() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        DummyAnnotation1 ann = DummyClass1.class.getAnnotation(DummyAnnotation1.class);

        boolean addedFirst = map.addIfNotPresent(ann);
        assertTrue(addedFirst);
        assertEquals(1, map.size());

        // Try adding same type again without present
        boolean addedSecond = map.addIfNotPresent(ann);
        assertFalse(addedSecond);
        assertEquals(1, map.size());
    }

    @Test
    public void testMergeWithNulls() throws Throwable {
        AnnotationMap map1 = new AnnotationMap();
        DummyAnnotation1 ann1 = DummyClass1.class.getAnnotation(DummyAnnotation1.class);
        map1.add(ann1);

        // merge(primary, null) -> primary
        AnnotationMap merged1 = AnnotationMap.merge(map1, null);
        assertSame(map1, merged1);

        // merge(null, secondary) -> secondary
        AnnotationMap merged2 = AnnotationMap.merge(null, map1);
        assertSame(map1, merged2);

        // merge(empty, secondary) -> secondary
        AnnotationMap emptyMap = new AnnotationMap();
        AnnotationMap merged3 = AnnotationMap.merge(emptyMap, map1);
        assertSame(map1, merged3);

        // merge(primary, empty) -> primary
        AnnotationMap merged4 = AnnotationMap.merge(map1, emptyMap);
        assertSame(map1, merged4);
    }

    @Test
    public void testMergeOverlappingAndDistinct() throws Throwable {
        AnnotationMap primary = new AnnotationMap();
        DummyAnnotation1 ann1 = DummyClass1.class.getAnnotation(DummyAnnotation1.class);
        primary.add(ann1);

        AnnotationMap secondary = new AnnotationMap();
        DummyAnnotation2 ann2 = DummyClass2.class.getAnnotation(DummyAnnotation2.class);
        secondary.add(ann2);

        // Add another DummyAnnotation1 to secondary with different value if possible, 
        // or just rely on overriding behavior. Let's create an anonymous or separate class for secondary's ann1 override.
        // Actually, let's test overriding: secondary has DummyAnnotation1, primary overrides it.
        AnnotationMap secondaryWithAnn1 = new AnnotationMap();
        DummyAnnotation1 ann1Secondary = DummyClass2.class.getAnnotation(DummyAnnotation1.class); // null, but let's use a dynamic approach or just another class
        
        // Let's define another annotated class for overriding test
        @DummyAnnotation1(value = "secondaryVal")
        class SecondaryDummy {
        }
        DummyAnnotation1 ann1Sec = SecondaryDummy.class.getAnnotation(DummyAnnotation1.class);
        secondary.add(ann1Sec);

        AnnotationMap merged = AnnotationMap.merge(primary, secondary);
        assertEquals(2, merged.size());
        assertEquals(ann1, merged.get(DummyAnnotation1.class)); // primary overrides secondary
        assertEquals(ann2, merged.get(DummyAnnotation2.class));
    }

    @Test
    public void testAnnotationsIterable() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        DummyAnnotation1 ann1 = DummyClass1.class.getAnnotation(DummyAnnotation1.class);
        DummyAnnotation2 ann2 = DummyClass2.class.getAnnotation(DummyAnnotation2.class);
        map.add(ann1);
        map.add(ann2);

        Iterable<Annotation> iterable = map.annotations();
        assertNotNull(iterable);
        Iterator<Annotation> it = iterable.iterator();
        int count = 0;
        while (it.hasNext()) {
            assertNotNull(it.next());
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testToStringBehavior() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        assertTrue(map.toString().contains("null"));

        DummyAnnotation1 ann1 = DummyClass1.class.getAnnotation(DummyAnnotation1.class);
        map.add(ann1);
        assertNotNull(map.toString());
        assertTrue(map.toString().length() > 0);
    }
}