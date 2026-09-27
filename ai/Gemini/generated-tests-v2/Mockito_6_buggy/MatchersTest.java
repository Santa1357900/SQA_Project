package org.mockito;

import org.junit.Test;
import static org.junit.Assert.*;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsEqual;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;

public class MatchersTest {

    @Test
    public void testAnyPrimitives() throws Throwable {
        assertFalse(Matchers.anyBoolean());
        assertEquals((byte) 0, Matchers.anyByte());
        assertEquals((char) 0, Matchers.anyChar());
        assertEquals(0, Matchers.anyInt());
        assertEquals(0L, Matchers.anyLong());
        assertEquals(0.0f, Matchers.anyFloat(), 0.0f);
        assertEquals(0.0, Matchers.anyDouble(), 0.0);
        assertEquals((short) 0, Matchers.anyShort());
    }

    @Test
    public void testAnyObjectsAndAliases() throws Throwable {
        assertNull(Matchers.anyObject());
        assertNull(Matchers.anyVararg());
        assertNull(Matchers.any(String.class));
        assertNull(Matchers.any());
        
        assertEquals("", Matchers.anyString());
        
        List list = Matchers.anyList();
        assertNotNull(list);
        
        List<String> typedList = Matchers.anyListOf(String.class);
        assertNotNull(typedList);
        
        Set set = Matchers.anySet();
        assertNotNull(set);
        
        Set<String> typedSet = Matchers.anySetOf(String.class);
        assertNotNull(typedSet);
        
        Map map = Matchers.anyMap();
        assertNotNull(map);
        
        Map<String, String> typedMap = Matchers.anyMapOf(String.class, String.class);
        assertNotNull(typedMap);
        
        Collection col = Matchers.anyCollection();
        assertNotNull(col);
        
        Collection<String> typedCol = Matchers.anyCollectionOf(String.class);
        assertNotNull(typedCol);
    }

    @Test
    public void testIsA() throws Throwable {
        assertNull(Matchers.isA(String.class));
    }

    @Test
    public void testEqPrimitives() throws Throwable {
        assertFalse(Matchers.eq(false));
        assertEquals((byte) 5, Matchers.eq((byte) 5));
        assertEquals('a', Matchers.eq('a'));
        assertEquals(10.0, Matchers.eq(10.0), 0.0);
        assertEquals(5.0f, Matchers.eq(5.0f), 0.0f);
        assertEquals(42, Matchers.eq(42));
        assertEquals(100L, Matchers.eq(100L));
        assertEquals((short) 3, Matchers.eq((short) 3));
    }

    @Test
    public void testEqObjects() throws Throwable {
        String testVal = "test";
        assertEquals(testVal, Matchers.eq(testVal));
    }

    @Test
    public void testRefEq() throws Throwable {
        String testVal = "testRef";
        assertNull(Matchers.refEq(testVal, "someField"));
    }

    @Test
    public void testSame() throws Throwable {
        String testVal = "testSame";
        assertEquals(testVal, Matchers.same(testVal));
    }

    @Test
    public void testNullAndNotNull() throws Throwable {
        assertNull(Matchers.isNull());
        assertNull(Matchers.isNull(String.class));
        assertNull(Matchers.notNull());
        assertNull(Matchers.notNull(String.class));
        assertNull(Matchers.isNotNull());
        assertNull(Matchers.isNotNull(String.class));
    }

    @Test
    public void testStringMatchers() throws Throwable {
        assertEquals("", Matchers.contains("sub"));
        assertEquals("", Matchers.matches("regex"));
        assertEquals("", Matchers.endsWith("suf"));
        assertEquals("", Matchers.startsWith("pre"));
    }

    @Test
    public void testThatMatchers() throws Throwable {
        Matcher<Integer> matcher = new IsEqual<Integer>(Integer.valueOf(1));
        
        assertNull(Matchers.argThat(matcher));
        assertEquals((char) 0, Matchers.charThat(new IsEqual<Character>(Character.valueOf('a'))));
        assertFalse(Matchers.booleanThat(new IsEqual<Boolean>(Boolean.valueOf(true))));
        assertEquals((byte) 0, Matchers.byteThat(new IsEqual<Byte>((byte) 1)));
        assertEquals((short) 0, Matchers.shortThat(new IsEqual<Short>((short) 1)));
        assertEquals(0, Matchers.intThat(matcher));
        assertEquals(0L, Matchers.longThat(new IsEqual<Long>(1L)));
        assertEquals(0.0f, Matchers.floatThat(new IsEqual<Float>(1.0f)), 0.0f);
        assertEquals(0.0, Matchers.doubleThat(new IsEqual<Double>(1.0)), 0.0);
    }
}