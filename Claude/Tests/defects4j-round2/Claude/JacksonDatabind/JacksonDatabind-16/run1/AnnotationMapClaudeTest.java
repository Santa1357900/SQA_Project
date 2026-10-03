package com.fasterxml.jackson.databind.introspect;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnnotationMapClaudeTest
{
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface AnnoA { }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface AnnoB { }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface AnnoC {
        String value();
    }

    @AnnoA
    static class WithA { }

    @AnnoB
    static class WithB { }

    @AnnoC("x")
    static class WithCx { }

    @AnnoC("y")
    static class WithCy { }

    private Annotation annoA() {
        return WithA.class.getAnnotation(AnnoA.class);
    }

    private Annotation annoB() {
        return WithB.class.getAnnotation(AnnoB.class);
    }

    private Annotation annoCx() {
        return WithCx.class.getAnnotation(AnnoC.class);
    }

    private Annotation annoCy() {
        return WithCy.class.getAnnotation(AnnoC.class);
    }

    // get(): _annotations null -> returns null
    @Test
    public void testGet_nullAnnotations_returnsNull() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        assertNull(map.get(AnnoA.class));
    }

    // get(): after add, returns the exact stored instance
    @Test
    public void testGet_afterAdd_returnsSameInstance() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        Annotation a = annoA();
        map.add(a);
        assertSame(a, map.get(AnnoA.class));
    }

    // get(): map has entries but not requested type -> null
    @Test
    public void testGet_wrongType_returnsNull() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoA());
        assertNull(map.get(AnnoB.class));
    }

    // annotations(): _annotations null -> empty iterable branch
    @Test
    public void testAnnotations_emptyMap_returnsEmptyIterable() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        Iterator<Annotation> it = map.annotations().iterator();
        assertFalse(it.hasNext());
    }

    // annotations(): single entry -> contains exactly that annotation
    @Test
    public void testAnnotations_afterSingleAdd_containsThatAnnotation() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoA());
        Iterator<Annotation> it = map.annotations().iterator();
        assertTrue(it.hasNext());
        Annotation found = it.next();
        assertEquals(AnnoA.class, found.annotationType());
        assertFalse(it.hasNext());
    }

    // annotations(): multiple entries -> contains all added types
    @Test
    public void testAnnotations_afterMultipleAdds_containsAll() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoA());
        map.add(annoB());
        int count = 0;
        boolean foundA = false;
        boolean foundB = false;
        Iterator<Annotation> it = map.annotations().iterator();
        while (it.hasNext()) {
            Annotation ann = it.next();
            count++;
            if (ann.annotationType().equals(AnnoA.class)) {
                foundA = true;
            }
            if (ann.annotationType().equals(AnnoB.class)) {
                foundB = true;
            }
        }
        assertEquals(2, count);
        assertTrue(foundA);
        assertTrue(foundB);
    }

    // size(): _annotations null -> zero
    @Test
    public void testSize_emptyMap_returnsZero() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        assertEquals(0, map.size());
    }

    // size(): one entry added -> one
    @Test
    public void testSize_afterAdd_returnsOne() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoA());
        assertEquals(1, map.size());
    }

    // size(): same annotation type added twice -> stays one (map keyed by type)
    @Test
    public void testSize_duplicateTypeAdd_staysOne() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoCx());
        map.add(annoCy());
        assertEquals(1, map.size());
    }

    // size(): two distinct annotation types -> two
    @Test
    public void testSize_twoDifferentTypes_returnsTwo() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoA());
        map.add(annoB());
        assertEquals(2, map.size());
    }

    // addIfNotPresent(): key absent (map null) -> adds and returns true
    @Test
    public void testAddIfNotPresent_newAnnotation_returnsTrue() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        boolean added = map.addIfNotPresent(annoA());
        assertTrue(added);
        assertEquals(1, map.size());
    }

    // addIfNotPresent(): key already present -> returns false, original kept
    @Test
    public void testAddIfNotPresent_existingType_returnsFalseAndKeepsOriginal() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        Annotation cx = annoCx();
        map.add(cx);
        boolean added = map.addIfNotPresent(annoCy());
        assertFalse(added);
        assertSame(cx, map.get(AnnoC.class));
    }

    // add(): first time new type (previous==null) -> per contract map changed -> true
    @Test
    public void testAdd_firstTimeNewType_returnsTrue() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        boolean changed = map.add(annoA());
        assertTrue(changed);
    }

    // add(): re-adding an equal annotation of same type -> no content change -> false
    @Test
    public void testAdd_sameAnnotationAgain_returnsFalse() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        Annotation a = annoA();
        map.add(a);
        boolean changed = map.add(a);
        assertFalse(changed);
    }

    // add(): replacing with a different annotation of same type -> content changed -> true
    @Test
    public void testAdd_differentAnnotationSameType_returnsTrue() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoCx());
        boolean changed = map.add(annoCy());
        assertTrue(changed);
    }

    // add(): stored value is always overwritten by the latest addition regardless of return flag
    @Test
    public void testAdd_replacesValueInMap() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoCx());
        map.add(annoCy());
        AnnoC stored = (AnnoC) map.get(AnnoC.class);
        assertEquals("y", stored.value());
    }

    // merge(): both null -> returns secondary (null)
    @Test
    public void testMerge_bothNull_returnsNull() throws Throwable {
        AnnotationMap result = AnnotationMap.merge(null, null);
        assertNull(result);
    }

    // merge(): primary null, secondary non-empty -> returns secondary reference
    @Test
    public void testMerge_primaryNull_secondaryNonNull_returnsSecondaryRef() throws Throwable {
        AnnotationMap secondary = new AnnotationMap();
        secondary.add(annoA());
        AnnotationMap result = AnnotationMap.merge(null, secondary);
        assertSame(secondary, result);
    }

    // merge(): primary empty (no annotations) -> returns secondary reference
    @Test
    public void testMerge_primaryEmpty_secondaryNonNull_returnsSecondaryRef() throws Throwable {
        AnnotationMap primary = new AnnotationMap();
        AnnotationMap secondary = new AnnotationMap();
        secondary.add(annoA());
        AnnotationMap result = AnnotationMap.merge(primary, secondary);
        assertSame(secondary, result);
    }

    // merge(): secondary null, primary non-empty -> returns primary reference
    @Test
    public void testMerge_secondaryNull_primaryNonNull_returnsPrimaryRef() throws Throwable {
        AnnotationMap primary = new AnnotationMap();
        primary.add(annoA());
        AnnotationMap result = AnnotationMap.merge(primary, null);
        assertSame(primary, result);
    }

    // merge(): secondary empty -> returns primary reference
    @Test
    public void testMerge_secondaryEmpty_primaryNonNull_returnsPrimaryRef() throws Throwable {
        AnnotationMap primary = new AnnotationMap();
        primary.add(annoA());
        AnnotationMap secondary = new AnnotationMap();
        AnnotationMap result = AnnotationMap.merge(primary, secondary);
        assertSame(primary, result);
    }

    // merge(): both non-empty with disjoint types -> combined map has both
    @Test
    public void testMerge_bothNonNull_disjointTypes_sizeAndValues() throws Throwable {
        AnnotationMap primary = new AnnotationMap();
        primary.add(annoA());
        AnnotationMap secondary = new AnnotationMap();
        secondary.add(annoB());
        AnnotationMap merged = AnnotationMap.merge(primary, secondary);
        assertEquals(2, merged.size());
        assertNotNull(merged.get(AnnoA.class));
        assertNotNull(merged.get(AnnoB.class));
    }

    // merge(): both non-empty with overlapping type -> primary's value wins
    @Test
    public void testMerge_bothNonNull_overlappingType_primaryWins() throws Throwable {
        AnnotationMap primary = new AnnotationMap();
        primary.add(annoCx());
        AnnotationMap secondary = new AnnotationMap();
        secondary.add(annoCy());
        AnnotationMap merged = AnnotationMap.merge(primary, secondary);
        assertEquals(1, merged.size());
        AnnoC result = (AnnoC) merged.get(AnnoC.class);
        assertEquals("x", result.value());
    }

    // toString(): _annotations null -> literal "[null]"
    @Test
    public void testToString_emptyMap_returnsNullBracket() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        assertEquals("[null]", map.toString());
    }

    // toString(): after add -> delegates to underlying map's toString containing the type
    @Test
    public void testToString_afterAdd_containsAnnotationRepresentation() throws Throwable {
        AnnotationMap map = new AnnotationMap();
        map.add(annoA());
        String str = map.toString();
        assertTrue(str.indexOf("AnnoA") >= 0);
    }
}
