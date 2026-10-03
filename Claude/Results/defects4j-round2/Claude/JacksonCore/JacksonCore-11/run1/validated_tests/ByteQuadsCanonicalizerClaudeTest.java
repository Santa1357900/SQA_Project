package com.fasterxml.jackson.core.sym;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ByteQuadsCanonicalizerClaudeTest {

    private ByteQuadsCanonicalizer root;
    private ByteQuadsCanonicalizer child;

    @Before
    public void setUp() throws Throwable {
        root = ByteQuadsCanonicalizer.createRoot(12345);
        child = root.makeChild(0);
    }

    // createRoot(seed): freshly created root table has zero entries
    @Test
    public void testCreateRootSeed_initialState_sizeZero() throws Throwable {
        assertEquals(0, root.size());
    }

    // createRoot(seed): hashSeed() returns exactly the seed supplied
    @Test
    public void testCreateRootSeed_hashSeedMatchesInput() throws Throwable {
        ByteQuadsCanonicalizer r = ByteQuadsCanonicalizer.createRoot(999);
        assertEquals(999, r.hashSeed());
    }

    // makeChild(flags): freshly created child has zero entries (nothing added yet)
    @Test
    public void testMakeChild_initialSizeZero() throws Throwable {
        assertEquals(0, child.size());
    }

    // addName(String,int) + findName(int): round trip for a single-quad name
    @Test
    public void testAddNameSingleQuad_findNameReturnsSameName() throws Throwable {
        String added = child.addName("abcd", 0x61626364);
        assertEquals("abcd", added);
        assertEquals("abcd", child.findName(0x61626364));
    }

    // findName(int): quad never added returns null
    @Test
    public void testFindNameSingleQuad_missing_returnsNull() throws Throwable {
        assertNull(child.findName(0x12345678));
    }

    // addName(String,int,int) with non-zero q2: round trip via findName(int,int)
    @Test
    public void testAddNameTwoQuad_nonZeroQ2_findNameReturnsSameName() throws Throwable {
        child.addName("longname", 111, 222);
        assertEquals("longname", child.findName(111, 222));
    }



    // findName(int,int): missing pair returns null
    @Test
    public void testFindNameTwoQuad_missing_returnsNull() throws Throwable {
        assertNull(child.findName(1, 2));
    }

    // addName(String,int,int,int) + findName(int,int,int): round trip for a three-quad name
    @Test
    public void testAddNameThreeQuad_findNameReturnsSameName() throws Throwable {
        child.addName("threequad", 1, 2, 3);
        assertEquals("threequad", child.findName(1, 2, 3));
    }

    // findName(int,int,int): missing triple returns null
    @Test
    public void testFindNameThreeQuad_missing_returnsNull() throws Throwable {
        assertNull(child.findName(9, 9, 9));
    }

    // addName(String,int[],int) qlen=1 delegates to single-quad storage; findable via findName(int[],1)
    @Test
    public void testAddNameIntArrayQlen1_findNameIntArrayQlen1() throws Throwable {
        int[] q = new int[] {555};
        child.addName("one", q, 1);
        assertEquals("one", child.findName(new int[] {555}, 1));
    }

    // addName(String,int[],int) qlen=2 delegates to two-quad storage; findable via findName(int[],2)
    @Test
    public void testAddNameIntArrayQlen2_findNameIntArrayQlen2() throws Throwable {
        int[] q = new int[] {10, 20};
        child.addName("two", q, 2);
        assertEquals("two", child.findName(new int[] {10, 20}, 2));
    }

    // addName(String,int[],int) qlen=3 delegates to three-quad storage; findable via findName(int[],3)
    @Test
    public void testAddNameIntArrayQlen3_findNameIntArrayQlen3() throws Throwable {
        int[] q = new int[] {1, 2, 3};
        child.addName("three", q, 3);
        assertEquals("three", child.findName(new int[] {1, 2, 3}, 3));
    }

    // addName qlen=4: exercises the "long name" storage/verify path (case 4 fall-through)
    @Test
    public void testAddNameIntArrayQlen4_longName_findNameIntArrayQlen4() throws Throwable {
        int[] q = new int[] {1, 2, 3, 4};
        child.addName("four", q, 4);
        assertEquals("four", child.findName(new int[] {1, 2, 3, 4}, 4));
    }

    // addName qlen=8: exercises _verifyLongName case 8 full fall-through chain
    @Test
    public void testAddNameIntArrayQlen8_longName_findNameIntArrayQlen8() throws Throwable {
        int[] q = new int[] {1, 2, 3, 4, 5, 6, 7, 8};
        child.addName("eight", q, 8);
        assertEquals("eight", child.findName(new int[] {1, 2, 3, 4, 5, 6, 7, 8}, 8));
    }

    // addName qlen=9: exercises _verifyLongName2 (default branch for qlen > 8)
    @Test
    public void testAddNameIntArrayQlen9_longName_usesVerifyLongName2() throws Throwable {
        int[] q = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
        child.addName("nine", q, 9);
        assertEquals("nine", child.findName(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, 9));
    }

    // findName(int[],int) qlen>=4: name never added returns null
    @Test
    public void testFindNameIntArray_qlen4_missing_returnsNull() throws Throwable {
        assertNull(child.findName(new int[] {100, 200, 300, 400}, 4));
    }

    // calcHash(int): deterministic - same input on same instance yields same hash
    @Test
    public void testCalcHashSingleQuad_isDeterministic() throws Throwable {
        int h1 = child.calcHash(42);
        int h2 = child.calcHash(42);
        assertEquals(h1, h2);
    }

    // calcHash(int,int): deterministic - same inputs yield same hash
    @Test
    public void testCalcHashTwoQuad_isDeterministic() throws Throwable {
        int h1 = child.calcHash(1, 2);
        int h2 = child.calcHash(1, 2);
        assertEquals(h1, h2);
    }

    // calcHash(int,int,int): deterministic - same inputs yield same hash
    @Test
    public void testCalcHashThreeQuad_isDeterministic() throws Throwable {
        int h1 = child.calcHash(1, 2, 3);
        int h2 = child.calcHash(1, 2, 3);
        assertEquals(h1, h2);
    }

    // calcHash(int[],int) with qlen<4 must throw IllegalArgumentException per explicit sanity check
    @Test
    public void testCalcHashIntArray_qlenLessThan4_throwsIllegalArgumentException() throws Throwable {
        try {
            child.calcHash(new int[] {1, 2, 3}, 3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // calcHash(int[],int) with qlen>=4: deterministic for same inputs
    @Test
    public void testCalcHashIntArray_qlen4_isDeterministic() throws Throwable {
        int[] q = new int[] {1, 2, 3, 4};
        int h1 = child.calcHash(q, 4);
        int h2 = child.calcHash(q, 4);
        assertEquals(h1, h2);
    }

    // primaryCount(): a single add into a fresh table always lands in a primary slot
    @Test
    public void testPrimaryCount_afterOneAdd_isOne() throws Throwable {
        child.addName("solo", 1234);
        assertEquals(1, child.primaryCount());
    }

    // Invariant: primary+secondary+tertiary+spillover counts must sum to totalCount()
    @Test
    public void testTotalCount_equalsSumOfCategoryCounts_afterMultipleAdds() throws Throwable {
        child.addName("a", 1);
        child.addName("b", 2, 3);
        child.addName("c", 4, 5, 6);
        int sum = child.primaryCount() + child.secondaryCount() + child.tertiaryCount() + child.spilloverCount();
        assertEquals(sum, child.totalCount());
    }

    // size(): child table's size reflects number of distinct names actually added
    @Test
    public void testSize_child_reflectsAddedCount() throws Throwable {
        child.addName("x", 1);
        child.addName("y", 2);
        assertEquals(2, child.size());
    }

    // size(): a freshly created root table starts empty
    @Test
    public void testSize_root_initiallyZero() throws Throwable {
        assertEquals(0, root.size());
    }

    // bucketCount(): freshly created child uses the documented default primary hash size (64)
    @Test
    public void testBucketCount_freshChild_defaultSize() throws Throwable {
        assertEquals(64, child.bucketCount());
    }

    // maybeDirty(): a freshly made child (still sharing parent arrays) is not dirty
    @Test
    public void testMaybeDirty_freshChild_isFalse() throws Throwable {
        assertFalse(child.maybeDirty());
    }

    // maybeDirty(): after adding a name the child unshares its arrays and becomes dirty
    @Test
    public void testMaybeDirty_afterAdd_isTrue() throws Throwable {
        child.addName("dirty", 1);
        assertTrue(child.maybeDirty());
    }

    // release(): merges child's new entries back into parent, visible to a subsequently created child
    @Test
    public void testRelease_mergesIntoParent_visibleInNewChild() throws Throwable {
        child.addName("merged", 42);
        child.release();
        ByteQuadsCanonicalizer child2 = root.makeChild(0);
        assertEquals("merged", child2.findName(42));
    }

    // toString(): produces a human readable summary that identifies the class
    @Test
    public void testToString_containsClassNameAndCounts() throws Throwable {
        child.addName("s", 1);
        String s = child.toString();
        assertTrue(s.contains("ByteQuadsCanonicalizer"));
    }

    // Adding enough distinct entries beyond the 80% load factor forces an internal rehash, doubling bucketCount()
    @Test
    public void testAddManyEntries_triggersRehash_bucketCountGrows() throws Throwable {
        for (int i = 0; i < 60; ++i) {
            child.addName("name" + i, 1000 + i);
        }
        assertTrue(child.bucketCount() > 64);
    }

    // After an internal rehash, all previously added entries must remain findable (no data loss on growth)
    @Test
    public void testAfterRehash_allNamesStillFindable() throws Throwable {
        for (int i = 0; i < 60; ++i) {
            child.addName("name" + i, 1000 + i);
        }
        for (int i = 0; i < 60; ++i) {
            assertEquals("name" + i, child.findName(1000 + i));
        }
    }

    // _calcTertiaryShift: small primary size yields the smallest bucket shift (4)
    @Test
    public void testCalcTertiaryShift_smallSize_returns4() throws Throwable {
        assertEquals(4, ByteQuadsCanonicalizer._calcTertiaryShift(16));
    }

    // _calcTertiaryShift: boundary where tertiary slot count == 256 yields shift 5
    @Test
    public void testCalcTertiaryShift_boundary256_returns5() throws Throwable {
        assertEquals(5, ByteQuadsCanonicalizer._calcTertiaryShift(1024));
    }

    // _calcTertiaryShift: boundary where tertiary slot count == 1024 yields shift 6
    @Test
    public void testCalcTertiaryShift_boundary1024_returns6() throws Throwable {
        assertEquals(6, ByteQuadsCanonicalizer._calcTertiaryShift(4096));
    }

    // _calcTertiaryShift: tertiary slot count beyond 1024 yields the largest bucket shift (7)
    @Test
    public void testCalcTertiaryShift_largeSize_returns7() throws Throwable {
        assertEquals(7, ByteQuadsCanonicalizer._calcTertiaryShift(8192));
    }
}
