package org.apache.commons.collections.functors;

import junit.framework.TestCase;
import org.apache.commons.collections.Predicate;

public class EqualPredicateTest extends TestCase {

    public EqualPredicateTest(String name) {
        super(name);
    }

    public void testEqualPredicateFactoryNullObject() throws Throwable {
        Predicate<String> predicate = EqualPredicate.equalPredicate(null);
        assertNotNull(predicate);
        assertTrue(predicate instanceof NullPredicate);
    }

    public void testEqualPredicateFactoryWithEquatorNullObject() throws Throwable {
        Equator<String> equator = DefaultEquator.INSTANCE;
        Predicate<String> predicate = EqualPredicate.equalPredicate(null, equator);
        assertNotNull(predicate);
        assertTrue(predicate instanceof NullPredicate);
    }

    public void testEqualPredicateFactoryNonNull() throws Throwable {
        Predicate<String> predicate = EqualPredicate.equalPredicate("testValue");
        assertNotNull(predicate);
        assertTrue(predicate instanceof EqualPredicate);
        assertEquals("testValue", ((EqualPredicate<String>) predicate).getValue());
    }

    public void testEqualPredicateFactoryNonNullWithEquator() throws Throwable {
        Equator<String> equator = DefaultEquator.INSTANCE;
        Predicate<String> predicate = EqualPredicate.equalPredicate("testValue", equator);
        assertNotNull(predicate);
        assertTrue(predicate instanceof EqualPredicate);
        assertEquals("testValue", ((EqualPredicate<String>) predicate).getValue());
    }

    public void testEvaluateMatch() throws Throwable {
        EqualPredicate<String> predicate = new EqualPredicate<String>("hello");
        assertTrue(predicate.evaluate("hello"));
    }

    public void testEvaluateNoMatch() throws Throwable {
        EqualPredicate<String> predicate = new EqualPredicate<String>("hello");
        assertFalse(predicate.evaluate("world"));
    }

    public void testEvaluateWithCustomEquator() throws Throwable {
        Equator<String> customEquator = new Equator<String>() {
            public boolean equate(String o1, String o2) {
                if (o1 == null || o2 == null) {
                    return o1 == o2;
                }
                return o1.equalsIgnoreCase(o2);
            }
            public int hash(String o) {
                return o == null ? 0 : o.toLowerCase().hashCode();
            }
        };

        EqualPredicate<String> predicate = new EqualPredicate<String>("Test", customEquator);
        assertTrue(predicate.evaluate("test"));
        assertTrue(predicate.evaluate("TEST"));
        assertFalse(predicate.evaluate("other"));
    }

    public void testGetValue() throws Throwable {
        Integer value = Integer.valueOf(123);
        EqualPredicate<Integer> predicate = new EqualPredicate<Integer>(value);
        assertEquals(value, predicate.getValue());
    }
}