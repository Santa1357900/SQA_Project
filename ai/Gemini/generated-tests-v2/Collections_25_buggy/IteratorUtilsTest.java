package org.apache.commons.collections4;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Vector;

import org.junit.Test;

public class IteratorUtilsTest {

    @Test
    public void testConstantsAndEmptyIterators() throws Throwable {
        assertNotNull(IteratorUtils.EMPTY_ITERATOR);
        assertNotNull(IteratorUtils.EMPTY_LIST_ITERATOR);
        assertNotNull(IteratorUtils.EMPTY_ORDERED_ITERATOR);
        assertNotNull(IteratorUtils.EMPTY_MAP_ITERATOR);
        assertNotNull(IteratorUtils.EMPTY_ORDERED_MAP_ITERATOR);

        assertNotNull(IteratorUtils.emptyIterator());
        assertNotNull(IteratorUtils.emptyListIterator());
        assertNotNull(IteratorUtils.emptyOrderedIterator());
        assertNotNull(IteratorUtils.emptyMapIterator());
        assertNotNull(IteratorUtils.emptyOrderedMapIterator());

        assertFalse(IteratorUtils.emptyIterator().hasNext());
        assertFalse(IteratorUtils.emptyListIterator().hasNext());
    }

    @Test
    public void testSingletonIterators() throws Throwable {
        Iterator<String> it = IteratorUtils.singletonIterator("test");
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("test", it.next());
        assertFalse(it.hasNext());

        ListIterator<String> lit = IteratorUtils.singletonListIterator("testList");
        assertNotNull(lit);
        assertTrue(lit.hasNext());
        assertEquals("testList", lit.next());
        assertFalse(lit.hasNext());
    }

    @Test
    public void testArrayIterators() throws Throwable {
        String[] arr = new String[] { "a", "b", "c" };
        
        Iterator<String> it1 = IteratorUtils.arrayIterator(arr);
        assertNotNull(it1);
        assertTrue(it1.hasNext());
        assertEquals("a", it1.next());

        Iterator<String> it2 = IteratorUtils.arrayIterator((Object) arr);
        assertNotNull(it2);
        assertTrue(it2.hasNext());

        Iterator<String> it3 = IteratorUtils.arrayIterator(arr, 1);
        assertNotNull(it3);
        assertEquals("b", it3.next());

        Iterator<String> it4 = IteratorUtils.arrayIterator((Object) arr, 1);
        assertNotNull(it4);

        Iterator<String> it5 = IteratorUtils.arrayIterator(arr, 1, 3);
        assertNotNull(it5);
        assertEquals("b", it5.next());

        Iterator<String> it6 = IteratorUtils.arrayIterator((Object) arr, 1, 3);
        assertNotNull(it6);

        ListIterator<String> lit1 = IteratorUtils.arrayListIterator(arr);
        assertNotNull(lit1);

        ListIterator<String> lit2 = IteratorUtils.arrayListIterator((Object) arr);
        assertNotNull(lit2);

        ListIterator<String> lit3 = IteratorUtils.arrayListIterator(arr, 1);
        assertNotNull(lit3);

        ListIterator<String> lit4 = IteratorUtils.arrayListIterator((Object) arr, 1);
        assertNotNull(lit4);

        ListIterator<String> lit5 = IteratorUtils.arrayListIterator(arr, 1, 2);
        assertNotNull(lit5);

        ListIterator<String> lit6 = IteratorUtils.arrayListIterator((Object) arr, 1, 2);
        assertNotNull(lit6);
    }

    @Test
    public void testBoundedIterator() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("x");
        list.add("y");
        list.add("z");

        Iterator<String> bounded1 = IteratorUtils.boundedIterator(list.iterator(), 2);
        assertNotNull(bounded1);
        assertEquals("x", bounded1.next());
        assertEquals("y", bounded1.next());
        assertFalse(bounded1.hasNext());

        Iterator<String> bounded2 = IteratorUtils.boundedIterator(list.iterator(), 1, 1);
        assertNotNull(bounded2);
        assertEquals("y", bounded2.next());
        assertFalse(bounded2.hasNext());
    }

    @Test
    public void testUnmodifiableIterators() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");

        Iterator<String> unmodIt = IteratorUtils.unmodifiableIterator(list.iterator());
        assertNotNull(unmodIt);

        ListIterator<String> unmodListIt = IteratorUtils.unmodifiableListIterator(list.listIterator());
        assertNotNull(unmodListIt);

        MapIterator<String, String> mapIt = new MapIterator<String, String>() {
            public boolean hasNext() { return false; }
            public String next() { return null; }
            public void remove() {}
            public String getKey() { return null; }
            public String getValue() { return null; }
            public String setValue(String value) { return null; }
            public boolean hasPrevious() { return false; }
            public String previous() { return null; }
            public void reset() {}
        };
        MapIterator<String, String> unmodMapIt = IteratorUtils.unmodifiableMapIterator(mapIt);
        assertNotNull(unmodMapIt);
    }

    @Test
    public void testChainedIterators() throws Throwable {
        List<String> list1 = new ArrayList<String>();
        list1.add("1");
        List<String> list2 = new ArrayList<String>();
        list2.add("2");

        Iterator<String> chained1 = IteratorUtils.chainedIterator(list1.iterator(), list2.iterator());
        assertNotNull(chained1);
        assertEquals("1", chained1.next());
        assertEquals("2", chained1.next());

        @SuppressWarnings("unchecked")
        Iterator<String>[] arr = new Iterator[] { list1.iterator(), list2.iterator() };
        Iterator<String> chained2 = IteratorUtils.chainedIterator(arr);
        assertNotNull(chained2);

        Collection<Iterator<String>> col = new ArrayList<Iterator<String>>();
        col.add(list1.iterator());
        col.add(list2.iterator());
        Iterator<String> chained3 = IteratorUtils.chainedIterator(col);
        assertNotNull(chained3);
    }

    @Test
    public void testCollatedIterators() throws Throwable {
        List<String> list1 = new ArrayList<String>();
        list1.add("a");
        List<String> list2 = new ArrayList<String>();
        list2.add("b");

        Comparator<String> comp = new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        Iterator<String> coll1 = IteratorUtils.collatedIterator(comp, list1.iterator(), list2.iterator());
        assertNotNull(coll1);

        @SuppressWarnings("unchecked")
        Iterator<String>[] arr = new Iterator[] { list1.iterator(), list2.iterator() };
        Iterator<String> coll2 = IteratorUtils.collatedIterator(comp, arr);
        assertNotNull(coll2);

        Collection<Iterator<String>> col = new ArrayList<Iterator<String>>();
        col.add(list1.iterator());
        col.add(list2.iterator());
        Iterator<String> coll3 = IteratorUtils.collatedIterator(comp, col);
        assertNotNull(coll3);
    }

    @Test
    public void testObjectGraphIterator() throws Throwable {
        Transformer<String, String> tx = new Transformer<String, String>() {
            public String transform(String input) {
                return null;
            }
        };
        Iterator<String> ogIt = IteratorUtils.objectGraphIterator("root", tx);
        assertNotNull(ogIt);
    }

    @Test
    public void testTransformedAndFilteredIterators() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("test");

        Transformer<String, Integer> tx = new Transformer<String, Integer>() {
            public Integer transform(String input) {
                return Integer.valueOf(input.length());
            }
        };
        Iterator<Integer> transIt = IteratorUtils.transformedIterator(list.iterator(), tx);
        assertNotNull(transIt);
        assertEquals(Integer.valueOf(4), transIt.next());

        Predicate<String> pred = new Predicate<String>() {
            public boolean evaluate(String object) {
                return true;
            }
        };
        Iterator<String> filtIt = IteratorUtils.filteredIterator(list.iterator(), pred);
        assertNotNull(filtIt);

        ListIterator<String> filtListIt = IteratorUtils.filteredListIterator(list.listIterator(), pred);
        assertNotNull(filtListIt);
    }

    @Test(expected = NullPointerException.class)
    public void testTransformedIteratorNullIterator() throws Throwable {
        Transformer<String, String> tx = new Transformer<String, String>() {
            public String transform(String input) { return input; }
        };
        IteratorUtils.transformedIterator(null, tx);
    }

    @Test(expected = NullPointerException.class)
    public void testTransformedIteratorNullTransformer() throws Throwable {
        List<String> list = new ArrayList<String>();
        IteratorUtils.transformedIterator(list.iterator(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testFilteredIteratorNullIterator() throws Throwable {
        Predicate<String> pred = new Predicate<String>() {
            public boolean evaluate(String object) { return true; }
        };
        IteratorUtils.filteredIterator(null, pred);
    }

    @Test(expected = NullPointerException.class)
    public void testFilteredIteratorNullPredicate() throws Throwable {
        List<String> list = new ArrayList<String>();
        IteratorUtils.filteredIterator(list.iterator(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testFilteredListIteratorNull() throws Throwable {
        IteratorUtils.filteredListIterator(null, new Predicate<String>() {
            public boolean evaluate(String object) { return true; }
        });
    }

    @Test(expected = NullPointerException.class)
    public void testFilteredListIteratorNullPredicate() throws Throwable {
        List<String> list = new ArrayList<String>();
        IteratorUtils.filteredListIterator(list.listIterator(), null);
    }

    @Test
    public void testLoopingIterators() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");

        ResettableIterator<String> loopIt = IteratorUtils.loopingIterator(list);
        assertNotNull(loopIt);

        ResettableListIterator<String> loopListIt = IteratorUtils.loopingListIterator(list);
        assertNotNull(loopListIt);
    }

    @Test(expected = NullPointerException.class)
    public void testLoopingIteratorNull() throws Throwable {
        IteratorUtils.loopingIterator(null);
    }

    @Test(expected = NullPointerException.class)
    public void testLoopingListIteratorNull() throws Throwable {
        IteratorUtils.loopingListIterator(null);
    }

    @Test
    public void testPeekingAndPushbackIterators() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");

        Iterator<String> peekIt = IteratorUtils.peekingIterator(list.iterator());
        assertNotNull(peekIt);

        Iterator<String> pushIt = IteratorUtils.pushbackIterator(list.iterator());
        assertNotNull(pushIt);
    }

    @Test
    public void testSkippingAndZippingIterators() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("a");

        SkippingIterator<String> skipIt = IteratorUtils.skippingIterator(list.iterator(), 0);
        assertNotNull(skipIt);

        ZippingIterator<String> zip1 = IteratorUtils.zippingIterator(list.iterator(), list.iterator());
        assertNotNull(zip1);

        ZippingIterator<String> zip2 = IteratorUtils.zippingIterator(list.iterator(), list.iterator(), list.iterator());
        assertNotNull(zip2);

        @SuppressWarnings("unchecked")
        Iterator<String>[] arr = new Iterator[] { list.iterator() };
        ZippingIterator<String> zip3 = IteratorUtils.zippingIterator(arr);
        assertNotNull(zip3);
    }

    @Test
    public void testViewsAndConversions() throws Throwable {
        Vector<String> vec = new Vector<String>();
        vec.add("v");

        Enumeration<String> en = vec.elements();
        Iterator<String> itFromEn = IteratorUtils.asIterator(en);
        assertNotNull(itFromEn);

        Iterator<String> itFromEnColl = IteratorUtils.asIterator(vec.elements(), vec);
        assertNotNull(itFromEnColl);

        List<String> list = new ArrayList<String>();
        list.add("l");
        Enumeration<String> enFromIt = IteratorUtils.asEnumeration(list.iterator());
        assertNotNull(enFromIt);

        Iterable<String> iterable = IteratorUtils.asIterable(list.iterator());
        assertNotNull(iterable);

        Iterable<String> multiIterable = IteratorUtils.asMultipleUseIterable(list.iterator());
        assertNotNull(multiIterable);

        ListIterator<String> listIt = IteratorUtils.toListIterator(list.iterator());
        assertNotNull(listIt);

        Object[] arrObj = IteratorUtils.toArray(list.iterator());
        assertNotNull(arrObj);

        String[] arrTyped = IteratorUtils.toArray(list.iterator(), String.class);
        assertNotNull(arrTyped);

        List<String> convertedList1 = IteratorUtils.toList(list.iterator());
        assertNotNull(convertedList1);

        List<String> convertedList2 = IteratorUtils.toList(list.iterator(), 5);
        assertNotNull(convertedList2);
    }

    @Test(expected = NullPointerException.class)
    public void testAsIteratorNull() throws Throwable {
        IteratorUtils.asIterator((Enumeration<String>) null);
    }

    @Test(expected = NullPointerException.class)
    public void testAsIteratorNullCollection() throws Throwable {
        Vector<String> vec = new Vector<String>();
        IteratorUtils.asIterator(vec.elements(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testAsEnumerationNull() throws Throwable {
        IteratorUtils.asEnumeration(null);
    }

    @Test(expected = NullPointerException.class)
    public void testAsIterableNull() throws Throwable {
        IteratorUtils.asIterable(null);
    }

    @Test(expected = NullPointerException.class)
    public void testAsMultipleUseIterableNull() throws Throwable {
        IteratorUtils.asMultipleUseIterable(null);
    }

    @Test(expected = NullPointerException.class)
    public void testToListIteratorNull() throws Throwable {
        IteratorUtils.toListIterator(null);
    }

    @Test(expected = NullPointerException.class)
    public void testToArrayNull() throws Throwable {
        IteratorUtils.toArray(null);
    }

    @Test(expected = NullPointerException.class)
    public void testToArrayNullClass() throws Throwable {
        List<String> list = new ArrayList<String>();
        IteratorUtils.toArray(list.iterator(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testToListNull() throws Throwable {
        IteratorUtils.toList(null, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToListInvalidSize() throws Throwable {
        List<String> list = new ArrayList<String>();
        IteratorUtils.toList(list.iterator(), 0);
    }

    @Test
    public void testGetIteratorCases() throws Throwable {
        assertNotNull(IteratorUtils.getIterator(null));

        List<String> list = new ArrayList<String>();
        list.add("a");
        assertNotNull(IteratorUtils.getIterator(list.iterator()));
        assertNotNull(IteratorUtils.getIterator(list));

        String[] arr = new String[] { "a" };
        assertNotNull(IteratorUtils.getIterator(arr));

        Vector<String> vec = new Vector<String>();
        assertNotNull(IteratorUtils.getIterator(vec.elements()));

        Map<String, String> map = new HashMap<String, String>();
        map.put("k", "v");
        assertNotNull(IteratorUtils.getIterator(map));

        Dictionary<String, String> dict = new Hashtable<String, String>();
        dict.put("k", "v");
        assertNotNull(IteratorUtils.getIterator(dict));

        int[] primArr = new int[] { 1 };
        assertNotNull(IteratorUtils.getIterator(primArr));

        // Object with iterator() method via reflection
        Object customObj = new Object() {
            public Iterator<String> iterator() {
                List<String> l = new ArrayList<String>();
                l.add("custom");
                return l.iterator();
            }
        };
        assertNotNull(IteratorUtils.getIterator(customObj));

        // Fallback singleton
        assertNotNull(IteratorUtils.getIterator(new Object()));
    }

    @Test
    public void testUtilityMethods() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("hello");

        Closure<String> closure = new Closure<String>() {
            public void execute(String input) {
                // no-op
            }
        };
        IteratorUtils.apply(list.iterator(), closure);
        IteratorUtils.apply(null, closure);

        Predicate<String> pred = new Predicate<String>() {
            public boolean evaluate(String object) {
                return "hello".equals(object);
            }
        };
        assertEquals("hello", IteratorUtils.find(list.iterator(), pred));
        assertNull(IteratorUtils.find(null, pred));

        assertTrue(IteratorUtils.matchesAny(list.iterator(), pred));
        assertFalse(IteratorUtils.matchesAny(null, pred));

        assertTrue(IteratorUtils.matchesAll(list.iterator(), pred));
        assertTrue(IteratorUtils.matchesAll(null, pred));

        assertFalse(IteratorUtils.isEmpty(list.iterator()));
        assertTrue(IteratorUtils.isEmpty(null));
        assertTrue(IteratorUtils.isEmpty(Collections.emptyIterator()));

        assertTrue(IteratorUtils.contains(list.iterator(), "hello"));
        assertFalse(IteratorUtils.contains(null, "hello"));

        assertEquals("hello", IteratorUtils.get(list.iterator(), 0));

        assertEquals(1, IteratorUtils.size(list.iterator()));
        assertEquals(0, IteratorUtils.size(null));

        assertEquals("[hello]", IteratorUtils.toString(list.iterator()));
        
        Transformer<String, String> stringTx = new Transformer<String, String>() {
            public String transform(String input) {
                return input;
            }
        };
        assertEquals("[hello]", IteratorUtils.toString(list.iterator(), stringTx));
        assertEquals("[hello]", IteratorUtils.toString(list.iterator(), stringTx, ", ", "[", "]"));
        assertEquals("hello", IteratorUtils.toString(null, stringTx, ", ", "", ""));
    }

    @Test(expected = NullPointerException.class)
    public void testApplyNullClosure() throws Throwable {
        List<String> list = new ArrayList<String>();
        IteratorUtils.apply(list.iterator(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testFindNullPredicate() throws Throwable {
        IteratorUtils.find(new ArrayList<String>().iterator(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testMatchesAnyNullPredicate() throws Throwable {
        IteratorUtils.matchesAny(new ArrayList<String>().iterator(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testMatchesAllNullPredicate() throws Throwable {
        IteratorUtils.matchesAll(new ArrayList<String>().iterator(), null);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetOutOfBounds() throws Throwable {
        List<String> list = new ArrayList<String>();
        IteratorUtils.get(list.iterator(), 5);
    }

    @Test(expected = NullPointerException.class)
    public void testToStringNullTransformer() throws Throwable {
        IteratorUtils.toString(new ArrayList<String>().iterator(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testToStringNullDelimiter() throws Throwable {
        Transformer<String, String> tx = new Transformer<String, String>() {
            public String transform(String input) { return input; }
        };
        IteratorUtils.toString(new ArrayList<String>().iterator(), tx, null, "[", "]");
    }

    @Test(expected = NullPointerException.class)
    public void testToStringNullPrefix() throws Throwable {
        Transformer<String, String> tx = new Transformer<String, String>() {
            public String transform(String input) { return input; }
        };
        IteratorUtils.toString(new ArrayList<String>().iterator(), tx, ", ", null, "]");
    }

    @Test(expected = NullPointerException.class)
    public void testToStringNullSuffix() throws Throwable {
        Transformer<String, String> tx = new Transformer<String, String>() {
            public String transform(String input) { return input; }
        };
        IteratorUtils.toString(new ArrayList<String>().iterator(), tx, ", ", "
[", null);
    }
}