package org.mockito;

import org.junit.Test;
import static org.junit.Assert.*;
import org.hamcrest.core.IsNull;

public class MatchersClaudeTest {

    // anyBoolean(): no type checks, must return primitive default false
    @Test
    public void testAnyBoolean_returnsFalse() throws Throwable {
        assertFalse(Matchers.anyBoolean());
    }

    // anyByte/anyChar/anyInt/anyLong/anyFloat/anyDouble/anyShort: each returns its zero default
    @Test
    public void testAnyNumericPrimitives_returnZeroDefaults() throws Throwable {
        assertEquals(0, Matchers.anyByte());
        assertEquals('\0', Matchers.anyChar());
        assertEquals(0, Matchers.anyInt());
        assertEquals(0L, Matchers.anyLong());
        assertEquals(0f, Matchers.anyFloat(), 0.0f);
        assertEquals(0d, Matchers.anyDouble(), 0.0);
        assertEquals((short) 0, Matchers.anyShort());
    }

    // anyObject()/anyVararg()/any(): all documented to return null
    @Test
    public void testAnyObjectAnyVarargAnyNoArg_returnNull() throws Throwable {
        assertNull(Matchers.anyObject());
        assertNull(Matchers.anyVararg());
        assertNull(Matchers.any());
    }

    // any(Class): for a plain reference type, documented return is null
    @Test
    public void testAnyWithClass_returnsNullForReferenceType() throws Throwable {
        assertNull(Matchers.any(String.class));
    }

    // anyString(): documented to return empty String, not null
    @Test
    public void testAnyString_returnsEmptyString() throws Throwable {
        assertEquals("", Matchers.anyString());
    }

    // anyList()/anyListOf(Class): both documented to return an empty List
    @Test
    public void testAnyListAndAnyListOf_returnEmptyList() throws Throwable {
        assertTrue(Matchers.anyList().isEmpty());
        assertTrue(Matchers.anyListOf(String.class).isEmpty());
    }

    // anySet()/anySetOf(Class): both documented to return an empty Set
    @Test
    public void testAnySetAndAnySetOf_returnEmptySet() throws Throwable {
        assertTrue(Matchers.anySet().isEmpty());
        assertTrue(Matchers.anySetOf(String.class).isEmpty());
    }

    // anyMap()/anyMapOf(Class,Class): both documented to return an empty Map
    @Test
    public void testAnyMapAndAnyMapOf_returnEmptyMap() throws Throwable {
        assertTrue(Matchers.anyMap().isEmpty());
        assertTrue(Matchers.anyMapOf(String.class, Integer.class).isEmpty());
    }

    // anyCollection()/anyCollectionOf(Class): both documented to return an empty Collection
    @Test
    public void testAnyCollectionAndAnyCollectionOf_returnEmptyCollection() throws Throwable {
        assertTrue(Matchers.anyCollection().isEmpty());
        assertTrue(Matchers.anyCollectionOf(String.class).isEmpty());
    }

    // isA(Class): documented to return null for a reference type
    @Test
    public void testIsA_withReferenceType_returnsNull() throws Throwable {
        assertNull(Matchers.isA(String.class));
    }

    // eq(boolean): dummy value must be primitive default false
    @Test
    public void testEqBoolean_returnsFalse() throws Throwable {
        assertFalse(Matchers.eq(true));
    }

    // eq(byte)/eq(char)/eq(double)/eq(float)/eq(int)/eq(long)/eq(short): all return zero default
    @Test
    public void testEqNumericPrimitives_returnZeroDefaults() throws Throwable {
        assertEquals(0, Matchers.eq((byte) 5));
        assertEquals('\0', Matchers.eq('x'));
        assertEquals(0d, Matchers.eq(3.14), 0.0);
        assertEquals(0f, Matchers.eq(2.5f), 0.0f);
        assertEquals(0, Matchers.eq(42));
        assertEquals(0L, Matchers.eq(100L));
        assertEquals((short) 0, Matchers.eq((short) 7));
    }

    // eq(int) boundary values: MIN_VALUE and MAX_VALUE must still yield the zero dummy
    @Test
    public void testEqInt_withBoundaryValues_returnsZero() throws Throwable {
        assertEquals(0, Matchers.eq(Integer.MIN_VALUE));
        assertEquals(0, Matchers.eq(Integer.MAX_VALUE));
    }

    // eq(T) generic overload: documented to return null for a reference type value
    @Test
    public void testEqGeneric_withStringValue_returnsNull() throws Throwable {
        assertNull(Matchers.eq("hello"));
    }

    // eq(T) generic overload: null value is a valid argument and still yields null
    @Test
    public void testEqGeneric_withNullValue_returnsNull() throws Throwable {
        assertNull(Matchers.eq((Object) null));
    }

    // refEq(T, String...): documented to return null, no excludeFields given
    @Test
    public void testRefEq_returnsNull() throws Throwable {
        assertNull(Matchers.refEq("abc"));
    }

    // refEq(T, String...): documented to return null when excludeFields are provided
    @Test
    public void testRefEq_withExcludeFields_returnsNull() throws Throwable {
        assertNull(Matchers.refEq("abc", "field1", "field2"));
    }

    // same(T): documented to return null for a reference type value
    @Test
    public void testSame_returnsNullForObjectValue() throws Throwable {
        assertNull(Matchers.same("abc"));
    }

    // same(T): null is a valid argument and still yields null
    @Test
    public void testSame_withNullValue_returnsNull() throws Throwable {
        assertNull(Matchers.same((Object) null));
    }

    // isNull()/isNull(Class): both documented to return null
    @Test
    public void testIsNull_noArgAndWithClass_returnNull() throws Throwable {
        assertNull(Matchers.isNull());
        assertNull(Matchers.isNull(String.class));
    }

    // notNull()/notNull(Class): both documented to return null (the dummy, not the matched value)
    @Test
    public void testNotNull_noArgAndWithClass_returnNull() throws Throwable {
        assertNull(Matchers.notNull());
        assertNull(Matchers.notNull(String.class));
    }

    // isNotNull()/isNotNull(Class): aliases of notNull(), also return null
    @Test
    public void testIsNotNull_noArgAndWithClass_returnNull() throws Throwable {
        assertNull(Matchers.isNotNull());
        assertNull(Matchers.isNotNull(String.class));
    }

    // contains(String): documented to return empty String
    @Test
    public void testContains_returnsEmptyString() throws Throwable {
        assertEquals("", Matchers.contains("sub"));
    }

    // contains(String): empty substring argument is still valid, returns empty String
    @Test
    public void testContains_withEmptySubstring_returnsEmptyString() throws Throwable {
        assertEquals("", Matchers.contains(""));
    }

    // matches(String): documented to return empty String
    @Test
    public void testMatches_returnsEmptyString() throws Throwable {
        assertEquals("", Matchers.matches(".*"));
    }

    // endsWith(String): documented to return empty String
    @Test
    public void testEndsWith_returnsEmptyString() throws Throwable {
        assertEquals("", Matchers.endsWith("suffix"));
    }

    // startsWith(String): documented to return empty String
    @Test
    public void testStartsWith_returnsEmptyString() throws Throwable {
        assertEquals("", Matchers.startsWith("prefix"));
    }

    // argThat(Matcher): documented to return null regardless of the matcher passed
    @Test
    public void testArgThat_returnsNull() throws Throwable {
        assertNull(Matchers.argThat(new IsNull<Object>()));
    }

    // charThat/booleanThat/byteThat/shortThat: each returns its respective zero/false default
    @Test
    public void testCharThatBooleanThatByteThatShortThat_returnDefaults() throws Throwable {
        assertEquals('\0', Matchers.charThat(new IsNull<Character>()));
        assertFalse(Matchers.booleanThat(new IsNull<Boolean>()));
        assertEquals(0, Matchers.byteThat(new IsNull<Byte>()));
        assertEquals((short) 0, Matchers.shortThat(new IsNull<Short>()));
    }

    // intThat/longThat/floatThat/doubleThat: each returns its respective zero default
    @Test
    public void testIntThatLongThatFloatThatDoubleThat_returnDefaults() throws Throwable {
        assertEquals(0, Matchers.intThat(new IsNull<Integer>()));
        assertEquals(0L, Matchers.longThat(new IsNull<Long>()));
        assertEquals(0f, Matchers.floatThat(new IsNull<Float>()), 0.0f);
        assertEquals(0d, Matchers.doubleThat(new IsNull<Double>()), 0.0);
    }
}
