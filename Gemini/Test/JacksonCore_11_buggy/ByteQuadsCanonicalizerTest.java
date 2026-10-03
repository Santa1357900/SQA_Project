package com.fasterxml.jackson.core.sym;

import org.junit.Test;
import static org.junit.Assert.*;

public class ByteQuadsCanonicalizerTest {

    @Test
    public void testCreateRootAndBasicProperties() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        assertNotNull(canonicalizer);
        assertTrue(canonicalizer.size() == 0);
        assertTrue(canonicalizer.bucketCount() > 0);
        assertFalse(canonicalizer.maybeDirty());
        assertTrue(canonicalizer.hashSeed() != 0);
        assertNotNull(canonicalizer.toString());
    }

    @Test
    public void testCreateRootWithSeed() throws Throwable {
        int seed = 12345;
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot(seed);
        assertNotNull(canonicalizer);
        assertTrue(canonicalizer.hashSeed() == seed);
    }

    @Test
    public void testMakeChildAndRelease() throws Throwable {
        ByteQuadsCanonicalizer root = ByteQuadsCanonicalizer.createRoot();
        ByteQuadsCanonicalizer child = root.makeChild(0);
        assertNotNull(child);
        assertTrue(child.size() == 0);
        
        // Add name to child to make it dirty
        child.addName("testField", 12345);
        assertTrue(child.maybeDirty());
        
        child.release();
    }

    @Test
    public void testAddAndFindSingleQuad() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        String name = "key1";
        int q1 = 1111;

        String added = canonicalizer.addName(name, q1);
        assertEquals(name, added);
        assertTrue(canonicalizer.size() == 1);

        String found = canonicalizer.findName(q1);
        assertEquals(name, found);

        // Test non-existent find
        String notFound = canonicalizer.findName(9999);
        assertNull(notFound);
    }

    @Test
    public void testAddAndFindDoubleQuad() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        String name = "keyTwo";
        int q1 = 111;
        int q2 = 222;

        String added = canonicalizer.addName(name, q1, q2);
        assertEquals(name, added);
        assertTrue(canonicalizer.size() == 1);

        String found = canonicalizer.findName(q1, q2);
        assertEquals(name, found);

        // Test q2 == 0 overload
        String nameZero = "keyZero";
        int q1_zero = 333;
        canonicalizer.addName(nameZero, q1_zero, 0);
        assertEquals(nameZero, canonicalizer.findName(q1_zero));
    }

    @Test
    public void testAddAndFindTripleQuad() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        String name = "keyThree";
        int q1 = 10;
        int q2 = 20;
        int q3 = 30;

        String added = canonicalizer.addName(name, q1, q2, q3);
        assertEquals(name, added);
        assertTrue(canonicalizer.size() == 1);

        String found = canonicalizer.findName(q1, q2, q3);
        assertEquals(name, found);
    }

    @Test
    public void testAddAndFindArrayQuads() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        
        // qlen = 1
        int[] q1 = new int[] { 100 };
        canonicalizer.addName("n1", q1, 1);
        assertEquals("n1", canonicalizer.findName(q1, 1));

        // qlen = 2
        int[] q2 = new int[] { 200, 201 };
        canonicalizer.addName("n2", q2, 2);
        assertEquals("n2", canonicalizer.findName(q2, 2));

        // qlen = 3
        int[] q3 = new int[] { 300, 301, 302 };
        canonicalizer.addName("n3", q3, 3);
        assertEquals("n3", canonicalizer.findName(q3, 3));

        // qlen >= 4 (Long name)
        int[] q4 = new int[] { 400, 401, 402, 403, 404 };
        canonicalizer.addName("n4", q4, 5);
        assertEquals("n4", canonicalizer.findName(q4, 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalcHashInvalidLength() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        int[] q = new int[] { 1, 2 };
        canonicalizer.calcHash(q, 2);
    }

    @Test
    public void testCalcHashOverloads() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        int h1 = canonicalizer.calcHash(123);
        int h2 = canonicalizer.calcHash(123, 456);
        int h3 = canonicalizer.calcHash(123, 456, 789);
        int[] q = new int[] { 1, 2, 3, 4 };
        int h4 = canonicalizer.calcHash(q, 4);

        assertTrue(h1 != 0);
        assertTrue(h2 != 0);
        assertTrue(h3 != 0);
        assertTrue(h4 != 0);
    }

    @Test
    public void testCountsAndStats() throws Throwable {
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        canonicalizer.addName("abc", 999);
        
        assertTrue(canonicalizer.primaryCount() >= 0);
        assertTrue(canonicalizer.secondaryCount() >= 0);
        assertTrue(canonicalizer.tertiaryCount() >= 0);
        assertTrue(canonicalizer.spilloverCount() >= 0);
        assertTrue(canonicalizer.totalCount() >= 0);
    }

    @Test
    public void testRehashTrigger() throws Throwable {
        // Create root with small size to trigger rehash easily
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot(1);
        // Add many entries to force rehash and spillover
        for (int i = 0; i < 200; i++) {
            canonicalizer.addName("name" + i, i + 1000, i + 2000);
        }
        assertTrue(canonicalizer.size() == 200);
    }

    @Test
    public void testCollisionAndFailOnDoS() throws Throwable {
        // Test with failOnDoS = false or via feature flags
        ByteQuadsCanonicalizer canonicalizer = ByteQuadsCanonicalizer.createRoot();
        // Force many collisions by putting same hash/slots if possible, or exercising _reportTooManyCollisions
        try {
            canonicalizer._reportTooManyCollisions();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Spill-over slots"));
        }
    }
}