package org.mockito;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hamcrest.Matcher;
import org.mockito.internal.matchers.Equals;
import org.mockito.internal.matchers.NotNull;
import org.mockito.internal.matchers.Null;

public class MatchersTest {

    @Test
    public void testAnyPrimitives() throws Throwable {
        assertFalse(Matchers.anyBoolean());
        assertEquals(0, Matchers.anyByte());
        assertEquals(0, Matchers.anyChar());
        assertEquals(0, Matchers.anyInt());
        assertEquals(0L, Matchers.anyLong());
        assertEquals(0.0f, Matchers.anyFloat(), 0.0f);
        assertEquals(0.0, Matchers.anyDouble(), 0.0);
        assertEquals(0, Matchers.anyShort());
    }

    @Test
    public void testAnyObjectsAndCollections() throws Throwable {
        assertNull(Matchers.anyObject());
        assertNull(Matchers.anyVararg());
        assertNull(Matchers.any(String.class));
        assertNull(Matchers.any());
        assertEquals("", Matchers.anyString());
        assertNotNull(Matchers.anyList());
        assertNotNull(Matchers.anyListOf(String.class));
        assertNotNull(Matchers.anySet());
        assertNotNull(Matchers.anySetOf(String.class));
        assertNotNull(Matchers.anyMap());
        assertNotNull(Matchers.anyCollection());
        assertNotNull(Matchers.anyCollectionOf(String.class));
    }

    @Test
    public void testIsA() throws Throwable {
        assertNull(Matchers.isA(String.class));
    }

    @Test
    public void testEqPrimitives() throws Throwable {
        assertFalse(Matchers.eq(false));
        assertTrue(Matchers.eq(true));
        assertEquals((byte) 1, Matchers.eq((byte) 1));
        assertEquals('a', Matchers.eq('a'));
        assertEquals(1.0, Matchers.eq(1.0), 0.0);
        assertEquals(1.0f, Matchers.eq(1.0f), 0.0f);
        assertEquals(42, Matchers.eq(42));
        assertEquals(42L, Matchers.eq(42L));
        assertEquals((short) 5, Matchers.eq((short) 5));
    }

    @Test
    public void testEqObject() throws Throwable {
        assertNull(Matchers.eq("test"));
        assertNull(Matchers.eq((String) null));
    }

    @Test
    public void testRefEqAndSame() throws Throwable {
        assertNull(Matchers.refEq("test", "field1"));
        assertNull(Matchers.same("test"));
    }

    @Test
    public void testNullAndNotNull() throws Throwable {
        assertNull(Matchers.isNull());
        assertNull(Matchers.notNull());
        assertNull(Matchers.isNotNull());
    }

    @Test
    public void testStringMatchers() throws Throwable {
        assertEquals("", Matchers.contains("sub"));
        assertEquals("", Matchers.matches(".*"));
        assertEquals("", Matchers.endsWith("suf"));
        assertEquals("", Matchers.startsWith("pre"));
    }

    @Test
    public void testCustomMatchersThat() throws Throwable {
        Matcher<Object> dummyMatcher = new Equals("test");
        Matcher<Character> charMatcher = new Equals('c');
        Matcher<Boolean> boolMatcher = new Equals(true);
        Matcher<Byte> byteMatcher = new Equals((byte) 1);
        Matcher<Short> shortMatcher = new Equals((short) 1);
        Matcher<Integer> intMatcher = new Equals(1);
        Matcher<Long> longMatcher = new Equals(1L);
        Matcher<Float> floatMatcher = new Equals(1.0f);
        Matcher<Double> doubleMatcher = new Equals(1.0);

        assertNull(Matchers.argThat(dummyMatcher));
        assertEquals('c', Matchers.charThat(charMatcher));
        assertTrue(Matchers.booleanThat(boolMatcher));
        assertEquals((byte) 1, Matchers.byteThat(byteMatcher));
        assertEquals((short) 1, Matchers.shortThat(shortMatcher));
        assertEquals(1, Matchers.intThat(intMatcher));
        assertEquals(1L, Matchers.longThat(longMatcher));
        assertEquals(1.0f, Matchers.floatThat(floatMatcher), 0.0f);
        assertEquals(1.0, Matchers.doubleThat(doubleMatcher), 0.0);
    }
}