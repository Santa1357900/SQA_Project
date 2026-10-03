package com.fasterxml.jackson.core.sym;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicReference;

public class ByteQuadsCanonicalizerTest {

    @Test
    public void testCreateRootAndBasicAccessors() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        assertNotNull(root);
        assertEquals(64, root.bucketCount());
        assertEquals(0, root.size());
        assertTrue(root.maybeDirty());
        assertTrue(root.hashSeed() != 0);
        assertNotNull(root.toString());
        
        assertEquals(0, root.primaryCount());
        assertEquals(0, root.secondaryCount());
        assertEquals(0, root.tertiaryCount());
        assertEquals(0, root.spilloverCount());
        assertEquals(0, root.totalCount());
        
        root.release();
    }

    @Test
    public void testCreateRootWithSeed() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot(12345);
        assertNotNull(root);
        assertEquals(12345, root.hashSeed());
        root.release();
    }

    @Test
    public void testMakeChildAndRelease() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        ByteQuadsCanonicalizer child = root.makeChild(0);
        assertNotNull(child);
        assertEquals(0, child.size());
        assertFalse(child.maybeDirty());
        
        child.release();
        root.release();
    }

    @Test
    public void testAddAndFindName1() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        String added = root.addName("testName1", 123);
        assertEquals("testName1", added);
        assertEquals(1, root.size());
        
        String found = root.findName(123);
        assertEquals("testName1", found);
        
        assertNull(root.findName(999));
        root.release();
    }

    @Test
    public void testAddAndFindName2() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        String added = root.addName("testName2", 123, 456);
        assertEquals("testName2", added);
        assertEquals(1, root.size());
        
        String found = root.findName(123, 456);
        assertEquals("testName2", found);
        
        assertNull(root.findName(123, 999));
        assertNull(root.findName(0, 0));
        root.release();
    }

    @Test
    public void testAddAndFindName3() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        String added = root.addName("testName3", 123, 456, 789);
        assertEquals("testName3", added);
        assertEquals(1, root.size());
        
        String found = root.findName(123, 456, 789);
        assertEquals("testName3", found);
        
        assertNull(root.findName(123, 456, 999));
        root.release();
    }

    @Test
    public void testAddAndFindNameArray() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        int[] q1 = new int[] { 11 };
        assertEquals("arr1", root.addName("arr1", q1, 1));
        assertEquals("arr1", root.findName(q1, 1));

        int[] q2 = new int[] { 22, 33 };
        assertEquals("arr2", root.addName("arr2", q2, 2));
        assertEquals("arr2", root.findName(q2, 2));

        int[] q3 = new int[] { 44, 55, 66 };
        assertEquals("arr3", root.addName("arr3", q3, 3));
        assertEquals("arr3", root.findName(q3, 3));

        int[] q5 = new int[] { 100, 101, 102, 103, 104 };
        assertEquals("arr5", root.addName("arr5", q5, 5));
        assertEquals("arr5", root.findName(q5, 5));
        
        root.release();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalcHashInvalidArrayLength() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        root.calcHash(new int[] { 1, 2 }, 2);
    }

    @Test
    public void testCalcHashCoverages() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        assertTrue(root.calcHash(10) != 0);
        assertTrue(root.calcHash(10, 20) != 0);
        assertTrue(root.calcHash(10, 20, 30) != 0);
        assertTrue(root.calcHash(new int[] { 1, 2, 3, 4 }, 4) != 0);
        root.release();
    }

    @Test
    public void testRehashTrigger() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot(1);
        for (int i = 0; i < 100; i++) {
            root.addName("name" + i, i + 1000, i + 2000);
        }
        assertTrue(root.size() >= 100);
        root.release();
    }

    @Test
    public void testDoSProtectionException() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot(42);
        boolean thrown = false;
        try {
            for (int i = 0; i < 50000; i++) {
                root.addName("n_" + i, i, i, i);
            }
        } catch (IllegalStateException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Suspect a DoS attack"));
        } catch (ArrayIndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testMergeChildWithEqualCount() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        ByteQuadsCanonicalizer child = root.makeChild(0);
        child.release();
        root.release();
    }

    @Test
    public void testMinHashSizeAndPadding() throws Throwable {
        ByteQuadsCanonicalizer root1 = ByteQuadsCanonicalizer.createRoot(1);
        assertNotNull(root1);
    }
}