package org.mockito;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MatchersClaudeTest {

    private static class AnyMatcher extends ArgumentMatcher {
        public boolean matches(Object item) {
            return true;
        }
    }

    // ครอบคลุม anyBoolean(): ต้องคืนค่า false ตาม javadoc
    @Test
    public void testAnyBoolean_returnsFalse() throws Throwable {
        boolean result = Matchers.anyBoolean();
        assertFalse(result);
    }

    // ครอบคลุม anyByte(): ต้องคืนค่า 0 ตาม javadoc
    @Test
    public void testAnyByte_returnsZero() throws Throwable {
        byte result = Matchers.anyByte();
        assertEquals(0, result);
    }

    // ครอบคลุม anyChar(): ต้องคืนค่า 0 (char ศูนย์) ตาม javadoc
    @Test
    public void testAnyChar_returnsZeroChar() throws Throwable {
        char result = Matchers.anyChar();
        assertEquals(0, result);
    }

    // ครอบคลุม anyInt(): ต้องคืนค่า 0 ตาม javadoc
    @Test
    public void testAnyInt_returnsZero() throws Throwable {
        int result = Matchers.anyInt();
        assertEquals(0, result);
    }

    // ครอบคลุม anyLong(): ต้องคืนค่า 0 ตาม javadoc
    @Test
    public void testAnyLong_returnsZero() throws Throwable {
        long result = Matchers.anyLong();
        assertEquals(0L, result);
    }

    // ครอบคลุม anyFloat(): ต้องคืนค่า 0 ตาม javadoc
    @Test
    public void testAnyFloat_returnsZero() throws Throwable {
        float result = Matchers.anyFloat();
        assertEquals(0f, result, 0.0f);
    }

    // ครอบคลุม anyDouble(): ต้องคืนค่า 0 ตาม javadoc
    @Test
    public void testAnyDouble_returnsZero() throws Throwable {
        double result = Matchers.anyDouble();
        assertEquals(0.0, result, 0.0);
    }

    // ครอบคลุม anyShort(): ต้องคืนค่า 0 ตาม javadoc
    @Test
    public void testAnyShort_returnsZero() throws Throwable {
        short result = Matchers.anyShort();
        assertEquals(0, result);
    }

    // ครอบคลุม anyObject(): ต้องคืนค่า null ตาม javadoc
    @Test
    public void testAnyObject_returnsNull() throws Throwable {
        Object result = Matchers.anyObject();
        assertNull(result);
    }

    // ครอบคลุม anyVararg(): ต้องคืนค่า null ตาม javadoc
    @Test
    public void testAnyVararg_returnsNull() throws Throwable {
        Object result = Matchers.anyVararg();
        assertNull(result);
    }

    // ครอบคลุม any(Class): เป็น alias ของ anyObject() ต้องคืนค่า null
    @Test
    public void testAny_withClass_returnsNull() throws Throwable {
        String result = Matchers.any(String.class);
        assertNull(result);
    }

    // ครอบคลุม any(): เป็น alias ของ anyObject() ต้องคืนค่า null
    @Test
    public void testAny_noArg_returnsNull() throws Throwable {
        Object result = Matchers.any();
        assertNull(result);
    }

    // ครอบคลุม anyString(): ต้องคืนค่า String ว่าง ("") ตาม javadoc
    @Test
    public void testAnyString_returnsEmptyString() throws Throwable {
        String result = Matchers.anyString();
        assertEquals("", result);
    }

    // ครอบคลุม anyList(): ต้องคืนค่า List ว่าง (ไม่ null, ไม่มีสมาชิก)
    @Test
    public void testAnyList_returnsEmptyList() throws Throwable {
        List result = Matchers.anyList();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ครอบคลุม anyListOf(Class): ต้องคืนค่า List ว่าง
    @Test
    public void testAnyListOf_returnsEmptyList() throws Throwable {
        List<String> result = Matchers.anyListOf(String.class);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ครอบคลุม anySet(): ต้องคืนค่า Set ว่าง
    @Test
    public void testAnySet_returnsEmptySet() throws Throwable {
        Set result = Matchers.anySet();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ครอบคลุม anySetOf(Class): ต้องคืนค่า Set ว่าง
    @Test
    public void testAnySetOf_returnsEmptySet() throws Throwable {
        Set<Integer> result = Matchers.anySetOf(Integer.class);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ครอบคลุม anyMap(): ต้องคืนค่า Map ว่าง
    @Test
    public void testAnyMap_returnsEmptyMap() throws Throwable {
        Map result = Matchers.anyMap();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ครอบคลุม anyCollection(): ต้องคืนค่า Collection ว่าง
    @Test
    public void testAnyCollection_returnsEmptyCollection() throws Throwable {
        Collection result = Matchers.anyCollection();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ครอบคลุม anyCollectionOf(Class): ต้องคืนค่า Collection ว่าง
    @Test
    public void testAnyCollectionOf_returnsEmptyCollection() throws Throwable {
        Collection<String> result = Matchers.anyCollectionOf(String.class);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ครอบคลุม isA(Class): ต้องคืนค่า null ตาม javadoc
    @Test
    public void testIsA_returnsNull() throws Throwable {
        String result = Matchers.isA(String.class);
        assertNull(result);
    }

    // ครอบคลุม eq(boolean): ใช้ returnFalse() ภายใน ต้องคืนค่า false
    @Test
    public void testEq_boolean_returnsFalse() throws Throwable {
        boolean result = Matchers.eq(true);
        assertFalse(result);
    }

    // ครอบคลุม eq(byte), eq(char), eq(short): ทุก overload ต้องคืนค่า 0
    @Test
    public void testEq_byteCharShort_returnsZero() throws Throwable {
        assertEquals(0, Matchers.eq((byte) 9));
        assertEquals(0, Matchers.eq('x'));
        assertEquals(0, Matchers.eq((short) 3));
    }

    // ครอบคลุม eq(double): ต้องคืนค่า 0
    @Test
    public void testEq_double_returnsZero() throws Throwable {
        double result = Matchers.eq(3.14);
        assertEquals(0.0, result, 0.0);
    }

    // ครอบคลุม eq(float): ต้องคืนค่า 0
    @Test
    public void testEq_float_returnsZero() throws Throwable {
        float result = Matchers.eq(1.5f);
        assertEquals(0f, result, 0.0f);
    }

    // ครอบคลุม eq(int): ต้องคืนค่า 0
    @Test
    public void testEq_int_returnsZero() throws Throwable {
        int result = Matchers.eq(42);
        assertEquals(0, result);
    }

    // ครอบคลุม eq(long): ต้องคืนค่า 0
    @Test
    public void testEq_long_returnsZero() throws Throwable {
        long result = Matchers.eq(100L);
        assertEquals(0L, result);
    }

    // ครอบคลุม eq(T) object overload: ต้องคืนค่า null
    @Test
    public void testEq_object_returnsNull() throws Throwable {
        String result = Matchers.eq("hello");
        assertNull(result);
    }

    // ครอบคลุม refEq(T, excludeFields...) ทั้งกรณีมี varargs และไม่มี (0 รอบ)
    @Test
    public void testRefEq_withAndWithoutExcludeFields_returnsNull() throws Throwable {
        String withExclude = Matchers.refEq("value", "field1", "field2");
        Integer withoutExclude = Matchers.refEq(Integer.valueOf(5));
        assertNull(withExclude);
        assertNull(withoutExclude);
    }

    // ครอบคลุม same(T): ต้องคืนค่า null
    @Test
    public void testSame_returnsNull() throws Throwable {
        Object value = new Object();
        Object result = Matchers.same(value);
        assertNull(result);
    }

    // ครอบคลุม isNull(), notNull(), isNotNull() (isNotNull เป็น alias เรียก notNull())
    @Test
    public void testNullMatchers_isNull_notNull_isNotNull_returnNull() throws Throwable {
        assertNull(Matchers.isNull());
        assertNull(Matchers.notNull());
        assertNull(Matchers.isNotNull());
    }

    // ครอบคลุม contains, matches, endsWith, startsWith: ทุกตัวต้องคืนค่า String ว่าง
    @Test
    public void testStringPatternMatchers_returnEmptyString() throws Throwable {
        assertEquals("", Matchers.contains("sub"));
        assertEquals("", Matchers.matches(".*"));
        assertEquals("", Matchers.endsWith("fix"));
        assertEquals("", Matchers.startsWith("pre"));
    }

    // ครอบคลุม argThat(Matcher): ต้องคืนค่า null
    @Test
    public void testArgThat_returnsNull() throws Throwable {
        Object result = Matchers.argThat(new AnyMatcher());
        assertNull(result);
    }

    // ครอบคลุม charThat(Matcher): ต้องคืนค่า 0
    @Test
    public void testCharThat_returnsZero() throws Throwable {
        char result = Matchers.charThat(new AnyMatcher());
        assertEquals(0, result);
    }

    // ครอบคลุม booleanThat(Matcher): ต้องคืนค่า false
    @Test
    public void testBooleanThat_returnsFalse() throws Throwable {
        boolean result = Matchers.booleanThat(new AnyMatcher());
        assertFalse(result);
    }

    // ครอบคลุม byteThat(Matcher): ต้องคืนค่า 0
    @Test
    public void testByteThat_returnsZero() throws Throwable {
        byte result = Matchers.byteThat(new AnyMatcher());
        assertEquals(0, result);
    }

    // ครอบคลุม shortThat(Matcher): ต้องคืนค่า 0
    @Test
    public void testShortThat_returnsZero() throws Throwable {
        short result = Matchers.shortThat(new AnyMatcher());
        assertEquals(0, result);
    }

    // ครอบคลุม intThat(Matcher): ต้องคืนค่า 0
    @Test
    public void testIntThat_returnsZero() throws Throwable {
        int result = Matchers.intThat(new AnyMatcher());
        assertEquals(0, result);
    }

    // ครอบคลุม longThat(Matcher): ต้องคืนค่า 0
    @Test
    public void testLongThat_returnsZero() throws Throwable {
        long result = Matchers.longThat(new AnyMatcher());
        assertEquals(0L, result);
    }

    // ครอบคลุม floatThat(Matcher): ต้องคืนค่า 0
    @Test
    public void testFloatThat_returnsZero() throws Throwable {
        float result = Matchers.floatThat(new AnyMatcher());
        assertEquals(0f, result, 0.0f);
    }

    // ครอบคลุม doubleThat(Matcher): ต้องคืนค่า 0
    @Test
    public void testDoubleThat_returnsZero() throws Throwable {
        double result = Matchers.doubleThat(new AnyMatcher());
        assertEquals(0.0, result, 0.0);
    }
}
