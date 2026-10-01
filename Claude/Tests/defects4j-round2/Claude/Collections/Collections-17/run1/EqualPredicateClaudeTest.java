package org.apache.commons.collections.functors;

import static org.junit.Assert.*;

import org.junit.Test;

import org.apache.commons.collections.Predicate;

public class EqualPredicateClaudeTest {

    // helper whose equals() always returns false, even for itself, to probe reference-equality shortcutting
    private static class NeverEqual {
        public boolean equals(Object o) {
            return false;
        }
        public int hashCode() {
            return 1;
        }
    }

    // helper whose equals() always returns true, regardless of reference identity
    private static class AlwaysEqual {
        public boolean equals(Object o) {
            return true;
        }
        public int hashCode() {
            return 2;
        }
    }

    // evaluate(): single-arg constructor, equal non-null values branch
    @Test
    public void testEvaluate_singleArgConstructor_equalValues_returnsTrue() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>("alpha");
        assertTrue(p.evaluate("alpha"));
    }

    // evaluate(): single-arg constructor, non-equal non-null values branch
    @Test
    public void testEvaluate_singleArgConstructor_differentValues_returnsFalse() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>("alpha");
        assertFalse(p.evaluate("beta"));
    }

    // evaluate(): non-null stored value vs null input branch
    @Test
    public void testEvaluate_nonNullStoredValue_nullInput_returnsFalse() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>("alpha");
        assertFalse(p.evaluate(null));
    }

    // evaluate(): null stored value vs null input branch, both nulls considered equal
    @Test
    public void testEvaluate_nullStoredValue_nullInput_returnsTrue() throws Throwable {
        EqualPredicate<Object> p = new EqualPredicate<Object>(null);
        assertTrue(p.evaluate(null));
    }

    // evaluate(): null stored value vs non-null input branch
    @Test
    public void testEvaluate_nullStoredValue_nonNullInput_returnsFalse() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>(null);
        assertFalse(p.evaluate("gamma"));
    }

    // evaluate(): equal-by-value but distinct-reference objects must be true (equals-based, not identity-based)
    @Test
    public void testEvaluate_equalButDifferentReferenceObjects_returnsTrue() throws Throwable {
        String stored = new String("delta");
        String input = new String("delta");
        EqualPredicate<String> p = new EqualPredicate<String>(stored);
        assertTrue(stored != input);
        assertTrue(p.evaluate(input));
    }

    // class contract says "the same object ... by equals": self-reference whose equals() is false must yield false
    @Test
    public void testEvaluate_selfReferenceWithFalseEquals_mustHonorEqualsContract() throws Throwable {
        NeverEqual obj = new NeverEqual();
        EqualPredicate<NeverEqual> p = new EqualPredicate<NeverEqual>(obj);
        assertFalse(p.evaluate(obj));
    }

    // complementary: object whose equals() always returns true must evaluate true even for a different reference
    @Test
    public void testEvaluate_alwaysEqualObject_returnsTrue() throws Throwable {
        AlwaysEqual stored = new AlwaysEqual();
        AlwaysEqual input = new AlwaysEqual();
        EqualPredicate<AlwaysEqual> p = new EqualPredicate<AlwaysEqual>(stored);
        assertTrue(p.evaluate(input));
    }

    // evaluate(): two-arg constructor with explicit DefaultEquator, equal values branch
    @Test
    public void testEvaluate_twoArgConstructorWithDefaultEquator_equalValues_returnsTrue() throws Throwable {
        EqualPredicate<Integer> p = new EqualPredicate<Integer>(Integer.valueOf(5), new DefaultEquator<Integer>());
        assertTrue(p.evaluate(Integer.valueOf(5)));
    }

    // evaluate(): two-arg constructor with explicit DefaultEquator, different values branch
    @Test
    public void testEvaluate_twoArgConstructorWithDefaultEquator_differentValues_returnsFalse() throws Throwable {
        EqualPredicate<Integer> p = new EqualPredicate<Integer>(Integer.valueOf(5), new DefaultEquator<Integer>());
        assertFalse(p.evaluate(Integer.valueOf(6)));
    }

    // boundary value: Integer.MIN_VALUE equal case
    @Test
    public void testEvaluate_integerMinValue_equalValue_returnsTrue() throws Throwable {
        EqualPredicate<Integer> p = new EqualPredicate<Integer>(Integer.valueOf(Integer.MIN_VALUE));
        assertTrue(p.evaluate(Integer.valueOf(Integer.MIN_VALUE)));
    }

    // boundary value: Integer.MAX_VALUE vs MAX_VALUE-1, different branch
    @Test
    public void testEvaluate_integerMaxValue_differentValue_returnsFalse() throws Throwable {
        EqualPredicate<Integer> p = new EqualPredicate<Integer>(Integer.valueOf(Integer.MAX_VALUE));
        assertFalse(p.evaluate(Integer.valueOf(Integer.MAX_VALUE - 1)));
    }

    // edge strings: empty string vs single-space string are not equal
    @Test
    public void testEvaluate_emptyString_vsSpaceString_returnsFalse() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>("");
        assertFalse(p.evaluate(" "));
    }

    // edge strings: empty string equal to a distinct empty string instance
    @Test
    public void testEvaluate_emptyString_equalEmptyString_returnsTrue() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>("");
        assertTrue(p.evaluate(new String("")));
    }

    // getValue(): single-arg constructor, non-null branch returns exact stored reference
    @Test
    public void testGetValue_nonNullValue_returnsStoredReference() throws Throwable {
        String value = "stored";
        EqualPredicate<String> p = new EqualPredicate<String>(value);
        assertSame(value, p.getValue());
    }

    // getValue(): single-arg constructor, null branch returns null
    @Test
    public void testGetValue_nullValue_returnsNull() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>(null);
        assertNull(p.getValue());
    }

    // getValue(): two-arg constructor returns exact stored reference
    @Test
    public void testGetValue_twoArgConstructor_returnsStoredReference() throws Throwable {
        Integer value = Integer.valueOf(42);
        EqualPredicate<Integer> p = new EqualPredicate<Integer>(value, new DefaultEquator<Integer>());
        assertSame(value, p.getValue());
    }

    // equalPredicate(T): non-null branch creates a working EqualPredicate, equal case
    @Test
    public void testEqualPredicateFactory_nonNullObject_evaluatesEqualValueTrue() throws Throwable {
        Predicate<String> p = EqualPredicate.equalPredicate("echo");
        assertTrue(p.evaluate("echo"));
    }

    // equalPredicate(T): non-null branch, different value returns false
    @Test
    public void testEqualPredicateFactory_nonNullObject_evaluatesDifferentValueFalse() throws Throwable {
        Predicate<String> p = EqualPredicate.equalPredicate("echo");
        assertFalse(p.evaluate("foxtrot"));
    }

    // equalPredicate(T): null branch, returned predicate evaluates true only for null input
    @Test
    public void testEqualPredicateFactory_nullObject_evaluatesNullInputTrue() throws Throwable {
        Predicate<Object> p = EqualPredicate.equalPredicate((Object) null);
        assertTrue(p.evaluate(null));
    }

    // equalPredicate(T): null branch, returned predicate is false for non-null input
    @Test
    public void testEqualPredicateFactory_nullObject_evaluatesNonNullInputFalse() throws Throwable {
        Predicate<Object> p = EqualPredicate.equalPredicate((Object) null);
        assertFalse(p.evaluate("golf"));
    }

    // equalPredicate(T, Equator): non-null branch with explicit DefaultEquator, equal case
    @Test
    public void testEqualPredicateFactoryWithEquator_nonNullObject_equalValueTrue() throws Throwable {
        Predicate<String> p = EqualPredicate.equalPredicate("hotel", new DefaultEquator<String>());
        assertTrue(p.evaluate("hotel"));
    }

    // equalPredicate(T, Equator): non-null branch, different value returns false
    @Test
    public void testEqualPredicateFactoryWithEquator_nonNullObject_differentValueFalse() throws Throwable {
        Predicate<String> p = EqualPredicate.equalPredicate("hotel", new DefaultEquator<String>());
        assertFalse(p.evaluate("india"));
    }

    // equalPredicate(T, Equator): null short-circuit branch ignores equator, true for null input
    @Test
    public void testEqualPredicateFactoryWithEquator_nullObject_evaluatesNullInputTrue() throws Throwable {
        Predicate<Object> p = EqualPredicate.equalPredicate((Object) null, new DefaultEquator<Object>());
        assertTrue(p.evaluate(null));
    }

    // equalPredicate(T, Equator): null short-circuit branch, false for non-null input
    @Test
    public void testEqualPredicateFactoryWithEquator_nullObject_evaluatesNonNullInputFalse() throws Throwable {
        Predicate<Object> p = EqualPredicate.equalPredicate((Object) null, new DefaultEquator<Object>());
        assertFalse(p.evaluate("juliet"));
    }

    // evaluate() is stable and side-effect free across repeated calls
    @Test
    public void testEvaluate_calledRepeatedly_isStableAndSideEffectFree() throws Throwable {
        EqualPredicate<String> p = new EqualPredicate<String>("kilo");
        assertTrue(p.evaluate("kilo"));
        assertTrue(p.evaluate("kilo"));
        assertFalse(p.evaluate("lima"));
    }

    // Boolean values: equal TRUE branch
    @Test
    public void testEvaluate_booleanValues_equalTrueBranch() throws Throwable {
        EqualPredicate<Boolean> p = new EqualPredicate<Boolean>(Boolean.TRUE);
        assertTrue(p.evaluate(Boolean.TRUE));
    }

    // Boolean values: different values branch
    @Test
    public void testEvaluate_booleanValues_differentFalseBranch() throws Throwable {
        EqualPredicate<Boolean> p = new EqualPredicate<Boolean>(Boolean.TRUE);
        assertFalse(p.evaluate(Boolean.FALSE));
    }

    // Long boundary: zero equal case
    @Test
    public void testEvaluate_longZeroValue_equalTrue() throws Throwable {
        EqualPredicate<Long> p = new EqualPredicate<Long>(Long.valueOf(0L));
        assertTrue(p.evaluate(Long.valueOf(0L)));
    }

    // Long boundary: -1 vs 1, different values branch
    @Test
    public void testEvaluate_longNegativeOne_vsPositiveOne_returnsFalse() throws Throwable {
        EqualPredicate<Long> p = new EqualPredicate<Long>(Long.valueOf(-1L));
        assertFalse(p.evaluate(Long.valueOf(1L)));
    }
}
