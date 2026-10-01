package org.apache.commons.collections4;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.junit.Test;
import static org.junit.Assert.*;

public class IteratorUtilsClaudeTest {

    private List<String> strList(String[] values) {
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < values.length; i++) {
            list.add(values[i]);
        }
        return list;
    }

    private List<Integer> intList(int[] values) {
        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < values.length; i++) {
            list.add(Integer.valueOf(values[i]));
        }
        return list;
    }

    private List<Object> collect(Iterator<?> it) {
        List<Object> result = new ArrayList<Object>();
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    // emptyIterator(): hasNext false
    @Test
    public void testEmptyIterator_hasNextFalse() throws Throwable {
        Iterator<Object> it = IteratorUtils.emptyIterator();
        assertFalse(it.hasNext());
    }

    // emptyListIterator(): hasNext/hasPrevious both false
    @Test
    public void testEmptyListIterator_hasNextAndHasPreviousFalse() throws Throwable {
        ListIterator<Object> li = IteratorUtils.emptyListIterator();
        assertFalse(li.hasNext());
        assertFalse(li.hasPrevious());
    }

    // emptyOrderedIterator(): hasNext false
    @Test
    public void testEmptyOrderedIterator_hasNextFalse() throws Throwable {
        Iterator<Object> it = IteratorUtils.emptyOrderedIterator();
        assertFalse(it.hasNext());
    }

    // emptyMapIterator()/emptyOrderedMapIterator(): never null
    @Test
    public void testEmptyMapIterators_returnNonNull() throws Throwable {
        Object mi = IteratorUtils.emptyMapIterator();
        Object omi = IteratorUtils.emptyOrderedMapIterator();
        assertNotNull(mi);
        assertNotNull(omi);
    }

    // singletonIterator(): one element then exhausted
    @Test
    public void testSingletonIterator_iteratesExactlyOneElement() throws Throwable {
        Iterator<String> it = IteratorUtils.singletonIterator("only");
        assertTrue(it.hasNext());
        assertEquals("only", it.next());
        assertFalse(it.hasNext());
    }

    // singletonListIterator(): hasPrevious flips after next()
    @Test
    public void testSingletonListIterator_hasPreviousAfterNext() throws Throwable {
        ListIterator<String> li = IteratorUtils.singletonListIterator("only");
        assertFalse(li.hasPrevious());
        assertEquals("only", li.next());
        assertTrue(li.hasPrevious());
        assertFalse(li.hasNext());
    }

    // arrayIterator(E...): iterates in order
    @Test
    public void testArrayIteratorVarargs_iteratesInOrder() throws Throwable {
        Iterator<String> it = IteratorUtils.arrayIterator("a", "b", "c");
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
        assertFalse(it.hasNext());
    }

    // arrayIterator(Object): wraps primitive array elements
    @Test
    public void testArrayIteratorObject_wrapsPrimitiveArray() throws Throwable {
        int[] prim = {10, 20, 30};
        Iterator<Integer> it = IteratorUtils.<Integer>arrayIterator((Object) prim);
        assertEquals(Integer.valueOf(10), it.next());
        assertEquals(Integer.valueOf(20), it.next());
        assertEquals(Integer.valueOf(30), it.next());
        assertFalse(it.hasNext());
    }

    // arrayIterator(E[], start): negative start -> IndexOutOfBoundsException
    @Test
    public void testArrayIteratorStart_negativeStart_throwsIOOBE() throws Throwable {
        String[] arr = {"a", "b", "c"};
        try {
            IteratorUtils.arrayIterator(arr, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // arrayIterator(E[], start, end): end before start -> IllegalArgumentException
    @Test
    public void testArrayIteratorStartEnd_endBeforeStart_throwsIAE() throws Throwable {
        String[] arr = {"a", "b", "c"};
        try {
            IteratorUtils.arrayIterator(arr, 2, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // arrayListIterator(E...): basic forward iteration
    @Test
    public void testArrayListIterator_iteratesInOrder() throws Throwable {
        ListIterator<String> li = IteratorUtils.arrayListIterator("p", "q");
        assertEquals("p", li.next());
        assertEquals("q", li.next());
        assertFalse(li.hasNext());
    }

    // boundedIterator(iterator, max): limits returned element count
    @Test
    public void testBoundedIteratorMax_limitsElements() throws Throwable {
        List<Integer> src = intList(new int[]{1, 2, 3, 4, 5});
        Iterator<Integer> it = IteratorUtils.boundedIterator(src.iterator(), 3);
        List<Object> result = collect(it);
        assertEquals(intList(new int[]{1, 2, 3}), result);
    }

    // boundedIterator(iterator, offset, max): skips offset then limits
    @Test
    public void testBoundedIteratorOffsetMax_skipsAndLimits() throws Throwable {
        List<Integer> src = intList(new int[]{1, 2, 3, 4, 5});
        Iterator<Integer> it = IteratorUtils.boundedIterator(src.iterator(), 1, 2);
        List<Object> result = collect(it);
        assertEquals(intList(new int[]{2, 3}), result);
    }

    // boundedIterator: negative max -> IllegalArgumentException
    @Test
    public void testBoundedIterator_negativeMax_throwsIAE() throws Throwable {
        List<Integer> src = intList(new int[]{1, 2, 3});
        try {
            IteratorUtils.boundedIterator(src.iterator(), -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // unmodifiableIterator: remove() throws UnsupportedOperationException
    @Test
    public void testUnmodifiableIterator_removeThrowsUOE() throws Throwable {
        List<String> src = strList(new String[]{"a"});
        Iterator<String> it = IteratorUtils.unmodifiableIterator(src.iterator());
        it.next();
        try {
            it.remove();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // unmodifiableListIterator: add() throws UnsupportedOperationException
    @Test
    public void testUnmodifiableListIterator_addThrowsUOE() throws Throwable {
        List<String> src = strList(new String[]{"a"});
        ListIterator<String> li = IteratorUtils.unmodifiableListIterator(src.listIterator());
        try {
            li.add("b");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // chainedIterator(two): combines elements in order
    @Test
    public void testChainedIteratorTwo_combinesInOrder() throws Throwable {
        List<Integer> a = intList(new int[]{1, 2});
        List<Integer> b = intList(new int[]{3, 4});
        Iterator<Integer> it = IteratorUtils.chainedIterator(a.iterator(), b.iterator());
        assertEquals(intList(new int[]{1, 2, 3, 4}), collect(it));
    }

    // chainedIterator(varargs): combines three iterators
    @Test
    public void testChainedIteratorVarargs_combinesInOrder() throws Throwable {
        List<Integer> a = intList(new int[]{1});
        List<Integer> b = intList(new int[]{2});
        List<Integer> c = intList(new int[]{3});
        Iterator<Integer> it = IteratorUtils.chainedIterator(a.iterator(), b.iterator(), c.iterator());
        assertEquals(intList(new int[]{1, 2, 3}), collect(it));
    }

    // collatedIterator: null comparator -> natural order merge
    @Test
    public void testCollatedIterator_mergesInNaturalOrder() throws Throwable {
        List<Integer> a = intList(new int[]{1, 3, 5});
        List<Integer> b = intList(new int[]{2, 4, 6});
        Iterator<Integer> it = IteratorUtils.<Integer>collatedIterator(null, a.iterator(), b.iterator());
        assertEquals(intList(new int[]{1, 2, 3, 4, 5, 6}), collect(it));
    }

    // objectGraphIterator: null transformer returns root itself once
    @Test
    public void testObjectGraphIterator_nullTransformer_returnsRootOnce() throws Throwable {
        Iterator<String> it = IteratorUtils.objectGraphIterator("X", null);
        assertTrue(it.hasNext());
        assertEquals("X", it.next());
        assertFalse(it.hasNext());
    }

    // objectGraphIterator: null root -> empty iterator
    @Test
    public void testObjectGraphIterator_nullRoot_emptyIterator() throws Throwable {
        Iterator<String> it = IteratorUtils.<String>objectGraphIterator(null, null);
        assertFalse(it.hasNext());
    }

    // transformedIterator: transforms each element
    @Test
    public void testTransformedIterator_transformsElements() throws Throwable {
        List<String> src = strList(new String[]{"ab", "abc"});
        Transformer<String, Integer> transformer = new Transformer<String, Integer>() {
            public Integer transform(String input) {
                return Integer.valueOf(input.length());
            }
        };
        Iterator<Integer> it = IteratorUtils.transformedIterator(src.iterator(), transformer);
        assertEquals(Integer.valueOf(2), it.next());
        assertEquals(Integer.valueOf(3), it.next());
        assertFalse(it.hasNext());
    }

    // transformedIterator: null iterator -> NullPointerException
    @Test
    public void testTransformedIterator_nullIterator_throwsNPE() throws Throwable {
        Transformer<String, String> transformer = new Transformer<String, String>() {
            public String transform(String input) {
                return input;
            }
        };
        try {
            IteratorUtils.transformedIterator((Iterator<String>) null, transformer);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // filteredIterator: only matching elements returned
    @Test
    public void testFilteredIterator_filtersElements() throws Throwable {
        List<String> src = strList(new String[]{"apple", "banana", "avocado"});
        Predicate<String> predicate = new Predicate<String>() {
            public boolean evaluate(String object) {
                return object.startsWith("a");
            }
        };
        Iterator<String> it = IteratorUtils.filteredIterator(src.iterator(), predicate);
        assertEquals(strList(new String[]{"apple", "avocado"}), collect(it));
    }

    // loopingIterator: wraps around after reaching end
    @Test
    public void testLoopingIterator_loopsContinuously() throws Throwable {
        List<Integer> coll = intList(new int[]{1, 2});
        Iterator<Integer> it = IteratorUtils.loopingIterator(coll);
        assertEquals(Integer.valueOf(1), it.next());
        assertEquals(Integer.valueOf(2), it.next());
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(1), it.next());
    }

    // loopingIterator: null collection -> NullPointerException
    @Test
    public void testLoopingIterator_nullCollection_throwsNPE() throws Throwable {
        try {
            IteratorUtils.loopingIterator(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // skippingIterator: skips first offset elements
    @Test
    public void testSkippingIterator_skipsOffset() throws Throwable {
        List<Integer> src = intList(new int[]{10, 20, 30, 40});
        Iterator<Integer> it = IteratorUtils.skippingIterator(src.iterator(), 2);
        assertEquals(intList(new int[]{30, 40}), collect(it));
    }

    // skippingIterator: negative offset -> IllegalArgumentException
    @Test
    public void testSkippingIterator_negativeOffset_throwsIAE() throws Throwable {
        List<Integer> src = intList(new int[]{1, 2});
        try {
            IteratorUtils.skippingIterator(src.iterator(), -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // zippingIterator: interleaves elements from both iterators
    @Test
    public void testZippingIterator_interleavesElements() throws Throwable {
        List<Integer> a = intList(new int[]{1, 3, 5});
        List<Integer> b = intList(new int[]{2, 4});
        Iterator<Integer> it = IteratorUtils.zippingIterator(a.iterator(), b.iterator());
        assertEquals(intList(new int[]{1, 2, 3, 4, 5}), collect(it));
    }

    // asIterator: wraps an Enumeration
    @Test
    public void testAsIterator_wrapsEnumeration() throws Throwable {
        final List<String> data = strList(new String[]{"a", "b"});
        Enumeration<String> en = new Enumeration<String>() {
            private int idx = 0;
            public boolean hasMoreElements() {
                return idx < data.size();
            }
            public String nextElement() {
                return data.get(idx++);
            }
        };
        Iterator<String> it = IteratorUtils.asIterator(en);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertFalse(it.hasNext());
    }

    // asEnumeration: wraps an Iterator
    @Test
    public void testAsEnumeration_wrapsIterator() throws Throwable {
        List<String> src = strList(new String[]{"x", "y"});
        Enumeration<String> en = IteratorUtils.asEnumeration(src.iterator());
        assertTrue(en.hasMoreElements());
        assertEquals("x", en.nextElement());
        assertEquals("y", en.nextElement());
        assertFalse(en.hasMoreElements());
    }

    // asMultipleUseIterable: iterable usable more than once
    @Test
    public void testAsMultipleUseIterable_multipleUse() throws Throwable {
        List<String> src = strList(new String[]{"x", "y"});
        Iterable<String> iterable = IteratorUtils.asMultipleUseIterable(src.iterator());
        int count1 = 0;
        for (Iterator<String> i1 = iterable.iterator(); i1.hasNext();) {
            i1.next();
            count1++;
        }
        int count2 = 0;
        for (Iterator<String> i2 = iterable.iterator(); i2.hasNext();) {
            i2.next();
            count2++;
        }
        assertEquals(2, count1);
        assertEquals(2, count2);
    }

    // toListIterator: converts simple Iterator into ListIterator
    @Test
    public void testToListIterator_convertsIterator() throws Throwable {
        List<String> src = strList(new String[]{"a", "b"});
        ListIterator<String> li = IteratorUtils.toListIterator(src.iterator());
        assertEquals("a", li.next());
        assertTrue(li.hasPrevious());
    }

    // toArray(iterator): produces Object array of contents
    @Test
    public void testToArray_fromIterator() throws Throwable {
        List<String> src = strList(new String[]{"a", "b", "c"});
        Object[] arr = IteratorUtils.toArray(src.iterator());
        assertEquals(3, arr.length);
        assertEquals("a", arr[0]);
        assertEquals("c", arr[2]);
    }

    // toList(iterator, estimatedSize): size < 1 -> IllegalArgumentException
    @Test
    public void testToListEstimatedSize_invalidSize_throwsIAE() throws Throwable {
        List<String> src = strList(new String[]{"a"});
        try {
            IteratorUtils.toList(src.iterator(), 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // nodeListIterator(NodeList): null -> NullPointerException
    @Test
    public void testNodeListIterator_nullNodeList_throwsNPE() throws Throwable {
        try {
            IteratorUtils.nodeListIterator((NodeList) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // nodeListIterator(Node): null -> NullPointerException
    @Test
    public void testNodeListIterator_nullNode_throwsNPE() throws Throwable {
        try {
            IteratorUtils.nodeListIterator((Node) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // getIterator: null object -> empty iterator
    @Test
    public void testGetIterator_null_returnsEmptyIterator() throws Throwable {
        Iterator<?> it = IteratorUtils.getIterator(null);
        assertFalse(it.hasNext());
    }

    // getIterator: Iterable (List) -> its own iterator
    @Test
    public void testGetIterator_withIterable_returnsItsIterator() throws Throwable {
        List<String> list = strList(new String[]{"x", "y"});
        Iterator<?> it = IteratorUtils.getIterator(list);
        assertEquals("x", it.next());
        assertEquals("y", it.next());
    }

    // getIterator: Object[] array -> iterates elements
    @Test
    public void testGetIterator_withObjectArray_iteratesElements() throws Throwable {
        String[] arr = {"p", "q"};
        Iterator<?> it = IteratorUtils.getIterator(arr);
        assertEquals("p", it.next());
        assertEquals("q", it.next());
    }

    // getIterator: Map -> values iterator (single entry, deterministic)
    @Test
    public void testGetIterator_withMap_returnsValuesIterator() throws Throwable {
        Map<String, String> map = new HashMap<String, String>();
        map.put("k", "v");
        Iterator<?> it = IteratorUtils.getIterator(map);
        assertTrue(it.hasNext());
        assertEquals("v", it.next());
        assertFalse(it.hasNext());
    }

    // getIterator: plain object with no iterator() method -> singleton iterator
    @Test
    public void testGetIterator_withPlainObject_returnsSingletonIterator() throws Throwable {
        Iterator<?> it = IteratorUtils.getIterator(Integer.valueOf(5));
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(5), it.next());
        assertFalse(it.hasNext());
    }

    // apply: closure executed for every element in order
    @Test
    public void testApply_appliesClosureToEachElement() throws Throwable {
        final StringBuilder sb = new StringBuilder();
        List<String> src = strList(new String[]{"a", "b", "c"});
        Closure<String> closure = new Closure<String>() {
            public void execute(String input) {
                sb.append(input);
            }
        };
        IteratorUtils.apply(src.iterator(), closure);
        assertEquals("abc", sb.toString());
    }

    // find: returns first matching element
    @Test
    public void testFind_findsFirstMatchingElement() throws Throwable {
        List<Integer> src = intList(new int[]{1, 2, 3, 4});
        Predicate<Integer> predicate = new Predicate<Integer>() {
            public boolean evaluate(Integer object) {
                return object.intValue() > 2;
            }
        };
        Integer found = IteratorUtils.find(src.iterator(), predicate);
        assertEquals(Integer.valueOf(3), found);
    }

    // find: no element matches -> null
    @Test
    public void testFind_noMatch_returnsNull() throws Throwable {
        List<Integer> src = intList(new int[]{1, 2});
        Predicate<Integer> predicate = new Predicate<Integer>() {
            public boolean evaluate(Integer object) {
                return object.intValue() > 100;
            }
        };
        assertNull(IteratorUtils.find(src.iterator(), predicate));
    }

    // matchesAny: null iterator -> false
    @Test
    public void testMatchesAny_falseForNullIterator() throws Throwable {
        Predicate<Object> predicate = new Predicate<Object>() {
            public boolean evaluate(Object object) {
                return true;
            }
        };
        assertFalse(IteratorUtils.matchesAny((Iterator<Object>) null, predicate));
    }

    // matchesAll: empty iterator -> true regardless of predicate
    @Test
    public void testMatchesAll_trueForEmptyIterator() throws Throwable {
        List<Object> empty = new ArrayList<Object>();
        Predicate<Object> alwaysFalse = new Predicate<Object>() {
            public boolean evaluate(Object object) {
                return false;
            }
        };
        assertTrue(IteratorUtils.matchesAll(empty.iterator(), alwaysFalse));
    }

    // matchesAll: one non-matching element -> false
    @Test
    public void testMatchesAll_falseWhenOneElementFails() throws Throwable {
        List<Integer> src = intList(new int[]{2, 4, 5});
        Predicate<Integer> isEven = new Predicate<Integer>() {
            public boolean evaluate(Integer object) {
                return object.intValue() % 2 == 0;
            }
        };
        assertFalse(IteratorUtils.matchesAll(src.iterator(), isEven));
    }

    // isEmpty: null, empty, and non-empty iterator cases
    @Test
    public void testIsEmpty_nullEmptyAndNonEmptyCases() throws Throwable {
        List<String> empty = new ArrayList<String>();
        List<String> nonEmpty = strList(new String[]{"x"});
        assertTrue(IteratorUtils.isEmpty(null));
        assertTrue(IteratorUtils.isEmpty(empty.iterator()));
        assertFalse(IteratorUtils.isEmpty(nonEmpty.iterator()));
    }

    // contains: true when present, false when absent
    @Test
    public void testContains_trueAndFalseCases() throws Throwable {
        List<String> src1 = strList(new String[]{"a", "b", "c"});
        List<String> src2 = strList(new String[]{"a", "b", "c"});
        assertTrue(IteratorUtils.contains(src1.iterator(), "b"));
        assertFalse(IteratorUtils.contains(src2.iterator(), "z"));
    }

    // get: returns element at first and last valid index
    @Test
    public void testGet_returnsCorrectIndexElement() throws Throwable {
        List<String> src1 = strList(new String[]{"a", "b", "c"});
        List<String> src2 = strList(new String[]{"a", "b", "c"});
        assertEquals("a", IteratorUtils.get(src1.iterator(), 0));
        assertEquals("c", IteratorUtils.get(src2.iterator(), 2));
    }

    // get: index beyond available elements -> IndexOutOfBoundsException
    @Test
    public void testGet_indexBeyondSize_throwsIOOBE() throws Throwable {
        List<String> src = strList(new String[]{"a", "b"});
        try {
            IteratorUtils.get(src.iterator(), 5);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // get: negative index -> IndexOutOfBoundsException
    @Test
    public void testGet_negativeIndex_throwsIOOBE() throws Throwable {
        List<String> src = strList(new String[]{"a"});
        try {
            IteratorUtils.get(src.iterator(), -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // size: counts elements, 0 for null
    @Test
    public void testSize_countsElementsAndZeroForNull() throws Throwable {
        List<String> src = strList(new String[]{"a", "b", "c"});
        assertEquals(3, IteratorUtils.size(src.iterator()));
        assertEquals(0, IteratorUtils.size(null));
    }

    // toString(iterator): default prefix/suffix/delimiter format
    @Test
    public void testToString_defaultFormat() throws Throwable {
        List<String> src = strList(new String[]{"a", "b"});
        assertEquals("[a, b]", IteratorUtils.toString(src.iterator()));
    }

    // toString(iterator): empty iterator yields just brackets
    @Test
    public void testToString_emptyIterator_bracketsOnly() throws Throwable {
        List<String> empty = new ArrayList<String>();
        assertEquals("[]", IteratorUtils.toString(empty.iterator()));
    }

    // toString(iterator, transformer): null transformer -> NullPointerException
    @Test
    public void testToString_nullTransformer_throwsNPE() throws Throwable {
        List<String> src = strList(new String[]{"a"});
        try {
            IteratorUtils.toString(src.iterator(), null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // toString(iterator, transformer, delim, prefix, suffix): custom formatting
    @Test
    public void testToString_customDelimiterPrefixSuffix() throws Throwable {
        List<String> src = strList(new String[]{"x", "y", "z"});
        String result = IteratorUtils.toString(src.iterator(),
                TransformerUtils.stringValueTransformer(), "-", "<", ">");
        assertEquals("<x-y-z>", result);
    }
}
