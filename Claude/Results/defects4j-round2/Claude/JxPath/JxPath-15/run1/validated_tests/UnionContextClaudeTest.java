package org.apache.commons.jxpath.ri.axes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.EvalContext;

public class UnionContextClaudeTest {

    private JXPathContext context;

    @Before
    public void setUp() throws Throwable {
        Map bean = new HashMap();
        List items = new ArrayList();
        items.add("A");
        items.add("B");
        items.add("C");
        bean.put("items", items);

        List nums = new ArrayList();
        nums.add(new Integer(10));
        nums.add(new Integer(20));
        nums.add(new Integer(30));
        bean.put("nums", nums);

        List single = new ArrayList();
        single.add("Only");
        bean.put("single", single);

        context = JXPathContext.newContext(bean);
    }

    // covers constructor + class hierarchy (UnionContext extends NodeSetContext)
    @Test
    public void testConstructor_validArgs_returnsNodeSetContextInstance() throws Throwable {
        EvalContext[] contexts = new EvalContext[] { null, null };
        UnionContext uc = new UnionContext(null, contexts);
        assertTrue(uc instanceof NodeSetContext);
    }

    // covers contexts.length > 1 branch with boundary length == 2
    @Test
    public void testGetDocumentOrder_twoContexts_returnsOne() throws Throwable {
        EvalContext[] contexts = new EvalContext[] { null, null };
        UnionContext uc = new UnionContext(null, contexts);
        assertEquals(1, uc.getDocumentOrder());
    }

    // covers contexts.length > 1 branch with length == 3
    @Test
    public void testGetDocumentOrder_threeContexts_returnsOne() throws Throwable {
        EvalContext[] contexts = new EvalContext[] { null, null, null };
        UnionContext uc = new UnionContext(null, contexts);
        assertEquals(1, uc.getDocumentOrder());
    }

    // covers NPE path when contexts array itself is null
    @Test
    public void testGetDocumentOrder_nullContexts_throwsNullPointerException() throws Throwable {
        UnionContext uc = new UnionContext(null, null);
        try {
            uc.getDocumentOrder();
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    // covers for-loop with 0 iterations (contexts.length == 0) and empty node set
    @Test
    public void testSetPosition_zeroContexts_positionOneReturnsFalse() throws Throwable {
        UnionContext uc = new UnionContext(null, new EvalContext[0]);
        boolean result = uc.setPosition(1);
        assertFalse(result);
    }

    // covers "prepared" flag skipping the build block on the second call
    @Test
    public void testSetPosition_calledTwiceOnEmpty_sameResultEachTime() throws Throwable {
        UnionContext uc = new UnionContext(null, new EvalContext[0]);
        boolean first = uc.setPosition(1);
        boolean second = uc.setPosition(1);
        assertEquals(first, second);
    }

    // covers NPE when contexts array is null while iterating in setPosition
    @Test
    public void testSetPosition_nullContexts_throwsNullPointerException() throws Throwable {
        UnionContext uc = new UnionContext(null, null);
        try {
            uc.setPosition(1);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    // key oracle: XPath union must yield nodes in document order regardless of operand order
    @Test
    public void testUnion_twoIndicesReverseOrder_documentOrderPreserved() throws Throwable {
        Iterator it = context.iterate("items[3] | items[1]");
        assertEquals("A", it.next());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());
    }

    // covers union with three contexts, verifying full document-order sort
    @Test
    public void testUnion_threeIndicesShuffled_documentOrderPreserved() throws Throwable {
        Iterator it = context.iterate("items[3] | items[1] | items[2]");
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());
    }

    // baseline: operands already written in document order
    @Test
    public void testUnion_naturalOrderOperands_baselineOrderCorrect() throws Throwable {
        Iterator it = context.iterate("items[1] | items[2] | items[3]");
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());
    }

    // covers dedup logic: pointers.contains(ptr) must prevent a duplicate pointer
    @Test
    public void testUnion_duplicateSameIndex_dedupToOneResult() throws Throwable {
        Iterator it = context.iterate("items[1] | items[1]");
        assertEquals("A", it.next());
        assertFalse(it.hasNext());
    }

    // covers dedup when one operand repeats among several distinct ones
    @Test
    public void testUnion_repeatedIndexAmongThree_dedupCountTwo() throws Throwable {
        Iterator it = context.iterate("items[1] | items[2] | items[1]");
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertFalse(it.hasNext());
    }

    // covers inner while(nextSet)/while(nextNode) executing 0 times for one operand
    @Test
    public void testUnion_oneOperandOutOfRange_onlyValidOperandReturned() throws Throwable {
        Iterator it = context.iterate("items[10] | items[1]");
        assertEquals("A", it.next());
        assertFalse(it.hasNext());
    }

    // covers both operands contributing zero nodes
    @Test
    public void testUnion_bothOperandsOutOfRange_noResults() throws Throwable {
        Iterator it = context.iterate("items[10] | items[20]");
        assertFalse(it.hasNext());
    }

    // covers getValue entry point relying on the same document-order contract
    @Test
    public void testUnion_viaGetValue_firstResultInDocumentOrder() throws Throwable {
        Object value = context.getValue("items[3] | items[1]");
        assertEquals("A", value);
    }

    // covers iteratePointers entry point and Pointer.getValue()
    @Test
    public void testUnion_viaIteratePointers_firstPointerValueInDocumentOrder() throws Throwable {
        Iterator it = context.iteratePointers("items[3] | items[1]");
        Pointer p = (Pointer) it.next();
        assertEquals("A", p.getValue());
    }

    // covers union over a different data type with reversed operand order
    @Test
    public void testUnion_numericList_reverseOrder_documentOrderPreserved() throws Throwable {
        Iterator it = context.iterate("nums[3] | nums[1]");
        assertEquals(new Integer(10), it.next());
        assertEquals(new Integer(30), it.next());
        assertFalse(it.hasNext());
    }

    // covers union across two different top-level list properties (distinct pointers)
    @Test
    public void testUnion_singleElementListWithOther_bothPresentInDocumentOrder() throws Throwable {
        Iterator it = context.iterate("single[1] | items[1]");
        List results = new ArrayList();
        while (it.hasNext()) {
            results.add(it.next());
        }
        assertEquals(2, results.size());
        assertTrue(results.contains("Only"));
        assertTrue(results.contains("A"));
    }

    // covers a larger contexts array (>2) mixing distinct and duplicate pointers
    @Test
    public void testUnion_fourOperandsWithRepeat_distinctCountAndOrder() throws Throwable {
        Iterator it = context.iterate("items[3] | items[2] | items[1] | items[1]");
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());
    }

    // covers for-loop with length 3 where every sub-context contributes zero nodes
    @Test
    public void testUnion_allIndicesOutOfRangeAcrossThree_noResults() throws Throwable {
        Iterator it = context.iterate("items[10] | items[20] | items[30]");
        assertFalse(it.hasNext());
    }



    // covers dedup via iteratePointers with the exact same index repeated
    @Test
    public void testUnion_iteratePointers_dedupSameIndex_singleGetValueMatches() throws Throwable {
        Iterator it = context.iteratePointers("items[2] | items[2]");
        Pointer p = (Pointer) it.next();
        assertEquals("B", p.getValue());
        assertFalse(it.hasNext());
    }
}
