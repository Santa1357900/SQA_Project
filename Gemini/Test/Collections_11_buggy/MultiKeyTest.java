package org.apache.commons.collections.keyvalue;

import junit.framework.TestCase;
import java.util.Arrays;

public class MultiKeyTest extends TestCase {

    public void testTwoKeysConstructor() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        assertEquals(2, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        
        Object[] keys = mk.getKeys();
        assertEquals(2, keys.length);
        assertEquals("A", keys[0]);
        assertEquals("B", keys[1]);
    }

    public void testThreeKeysConstructor() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C");
        assertEquals(3, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        assertEquals("C", mk.getKey(2));
    }

    public void testFourKeysConstructor() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C", "D");
        assertEquals(4, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        assertEquals("C", mk.getKey(2));
        assertEquals("D", mk.getKey(3));
    }

    public void testFiveKeysConstructor() throws Throwable {
        MultiKey mk = new MultiKey("A", "B", "C", "D", "E");
        assertEquals(5, mk.size());
        assertEquals("A", mk.getKey(0));
        assertEquals("B", mk.getKey(1));
        assertEquals("C", mk.getKey(2));
        assertEquals("D", mk.getKey(3));
        assertEquals("E", mk.getKey(4));
    }

    public void testArrayConstructorNull() throws Throwable {
        try {
            new MultiKey((Object[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }

        try {
            new MultiKey(null, true);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    public void testArrayConstructorClone() throws Throwable {
        String[] baseKeys = new String[] { "X", "Y" };
        MultiKey mk = new MultiKey(baseKeys, true);
        assertEquals(2, mk.size());
        assertEquals("X", mk.getKey(0));
        assertEquals("Y", mk.getKey(1));
        
        // Mutate original array to ensure cloning worked
        baseKeys[0] = "Z";
        assertEquals("X", mk.getKey(0));
    }

    public void testArrayConstructorNoClone() throws Throwable {
        String[] baseKeys = new String[] { "X", "Y" };
        MultiKey mk = new MultiKey(baseKeys, false);
        assertEquals(2, mk.size());
        assertEquals("X", mk.getKey(0));
        assertEquals("Y", mk.getKey(1));
    }

    public void testEqualsAndHashCode() throws Throwable {
        MultiKey mk1 = new MultiKey("A", "B");
        MultiKey mk2 = new MultiKey("A", "B");
        MultiKey mk3 = new MultiKey("A", "C");
        MultiKey mk4 = new MultiKey("A", "B", "C");

        assertTrue(mk1.equals(mk1));
        assertTrue(mk1.equals(mk2));
        assertEquals(mk1.hashCode(), mk2.hashCode());

        assertFalse(mk1.equals(mk3));
        assertFalse(mk1.equals(mk4));
        assertFalse(mk1.equals("Not a MultiKey"));
        assertFalse(mk1.equals(null));
    }

    public void testNullKeysInCalculation() throws Throwable {
        MultiKey mk1 = new MultiKey(null, "B");
        MultiKey mk2 = new MultiKey("A", null);
        MultiKey mk3 = new MultiKey(null, null);

        assertEquals(2, mk1.size());
        assertNull(mk1.getKey(0));
        assertEquals("B", mk1.getKey(1));

        assertEquals(2, mk2.size());
        assertEquals("A", mk2.getKey(0));
        assertNull(mk2.getKey(1));

        assertEquals(2, mk3.size());
        assertNull(mk3.getKey(0));
        assertNull(mk3.getKey(1));

        // Verify hashcode and equals work with nulls
        MultiKey mk1Clone = new MultiKey(null, "B");
        assertTrue(mk1.equals(mk1Clone));
        assertEquals(mk1.hashCode(), mk1Clone.hashCode());
    }

    public void testToString() throws Throwable {
        MultiKey mk = new MultiKey("A", "B");
        String str = mk.toString();
        assertNotNull(str);
        assertTrue(str.startsWith("MultiKey"));
        assertTrue(str.contains("A"));
        assertTrue(str.contains("B"));
    }

    public void testGettersAndIndexOutOfBounds() throws Throwable {
        MultiKey mk = new MultiKey(new Object[] { "One" }, false);
        assertEquals(1, mk.size());
        assertEquals("One", mk.getKey(0));

        try {
            mk.getKey(1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }
    }
}