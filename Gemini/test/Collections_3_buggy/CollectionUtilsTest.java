package org.apache.commons.collections;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class CollectionUtilsTest extends TestCase {

    public CollectionUtilsTest(String testName) {
        super(testName);
    }

    public void testConstructor() throws Throwable {
        CollectionUtils utils = new CollectionUtils();
        assertNotNull(utils);
    }

    public void testUnion() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");
        a.add("A");

        List b = new ArrayList();
        b.add("B");
        b.add("C");
        b.add("C");

        Collection union = CollectionUtils.union(a, b);
        assertNotNull(union);
        assertEquals(5, union.size());
        assertEquals(2, CollectionUtils.cardinality("A", union));
        assertEquals(1, CollectionUtils.cardinality("B", union));
        assertEquals(2, CollectionUtils.cardinality("C", union));
    }

    public void testIntersection() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");
        a.add("A");
        a.add("D");

        List b = new ArrayList();
        b.add("A");
        b.add("B");
        b.add("B");
        b.add("C");

        Collection intersection = CollectionUtils.intersection(a, b);
        assertNotNull(intersection);
        assertEquals(2, intersection.size());
        assertEquals(1, CollectionUtils.cardinality("A", intersection));
        assertEquals(1, CollectionUtils.cardinality("B", intersection));
        assertEquals(0, CollectionUtils.cardinality("C", intersection));
        assertEquals(0, CollectionUtils.cardinality("D", intersection));
    }

    public void testDisjunction() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");
        a.add("A");

        List b = new ArrayList();
        b.add("A");
        b.add("B");
        b.add("B");
        b.add("C");

        Collection disj = CollectionUtils.disjunction(a, b);
        assertNotNull(disj);
        assertEquals(3, disj.size());
        assertEquals(1, CollectionUtils.cardinality("A", disj));
        assertEquals(1, CollectionUtils.cardinality("B", disj));
        assertEquals(1, CollectionUtils.cardinality("C", disj));
    }

    public void testSubtract() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");
        a.add("A");
        a.add("C");

        List b = new ArrayList();
        b.add("A");
        b.add("C");

        Collection sub = CollectionUtils.subtract(a, b);
        assertNotNull(sub);
        assertEquals(2, sub.size());
        assertEquals(1, CollectionUtils.cardinality("A", sub));
        assertEquals(1, CollectionUtils.cardinality("B", sub));
    }

    public void testContainsAny() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");

        List b = new ArrayList();
        b.add("B");
        b.add("C");

        List c = new ArrayList();
        c.add("D");
        c.add("E");

        assertTrue(CollectionUtils.containsAny(a, b));
        assertTrue(CollectionUtils.containsAny(b, a));
        assertFalse(CollectionUtils.containsAny(a, c));
        assertFalse(CollectionUtils.containsAny(c, a));
    }

    public void testGetCardinalityMap() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");
        a.add("A");
        a.add(null);
        a.add(null);

        Map map = CollectionUtils.getCardinalityMap(a);
        assertNotNull(map);
        assertEquals(new Integer(2), map.get("A"));
        assertEquals(new Integer(1), map.get("B"));
        assertEquals(new Integer(2), map.get(null));
    }

    public void testIsSubCollection() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("A");

        List b = new ArrayList();
        b.add("A");
        b.add("A");
        b.add("B");

        List c = new ArrayList();
        c.add("A");

        assertTrue(CollectionUtils.isSubCollection(a, b));
        assertTrue(CollectionUtils.isSubCollection(c, a));
        assertFalse(CollectionUtils.isSubCollection(b, a));
    }

    public void testIsProperSubCollection() throws Throwable {
        List a = new ArrayList();
        a.add("A");

        List b = new ArrayList();
        b.add("A");
        b.add("B");

        List c = new ArrayList();
        c.add("A");

        assertTrue(CollectionUtils.isProperSubCollection(a, b));
        assertFalse(CollectionUtils.isProperSubCollection(a, c));
        assertFalse(CollectionUtils.isProperSubCollection(b, a));
    }

    public void testIsEqualCollection() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");

        List b = new ArrayList();
        b.add("B");
        b.add("A");

        List c = new ArrayList();
        c.add("A");
        c.add("C");

        List d = new ArrayList();
        d.add("A");

        assertTrue(CollectionUtils.isEqualCollection(a, b));
        assertFalse(CollectionUtils.isEqualCollection(a, c));
        assertFalse(CollectionUtils.isEqualCollection(a, d));
    }

    public void testCardinality() throws Throwable {
        Set set = new HashSet();
        set.add("A");
        set.add("B");

        assertEquals(1, CollectionUtils.cardinality("A", set));
        assertEquals(0, CollectionUtils.cardinality("C", set));

        List list = new ArrayList();
        list.add("A");
        list.add(null);
        list.add("A");
        list.add("B");

        assertEquals(2, CollectionUtils.cardinality("A", list));
        assertEquals(1, CollectionUtils.cardinality("B", list));
        assertEquals(2, CollectionUtils.cardinality("A", list));
        assertEquals(1, CollectionUtils.cardinality(null, list));
        assertEquals(0, CollectionUtils.cardinality("C", list));
    }

    public void testFind() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        list.add("C");

        Predicate pred = new Predicate() {
            public boolean evaluate(Object object) {
                return "B".equals(object);
            }
        };

        assertEquals("B", CollectionUtils.find(list, pred));
        assertNull(CollectionUtils.find(null, pred));
        assertNull(CollectionUtils.find(list, null));
        assertNull(CollectionUtils.find(list, new Predicate() {
            public boolean evaluate(Object object) {
                return "Z".equals(object);
            }
        }));
    }

    public void testForAllDo() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");

        final List result = new ArrayList();
        Closure closure = new Closure() {
            public void execute(Object input) {
                result.add(input);
            }
        };

        CollectionUtils.forAllDo(list, closure);
        assertEquals(2, result.size());
        assertEquals("A", result.get(0));
        assertEquals("B", result.get(1));

        // Test null safety
        CollectionUtils.forAllDo(null, closure);
        CollectionUtils.forAllDo(list, null);
    }

    public void testFilter() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        list.add("AC");

        Predicate pred = new Predicate() {
            public boolean evaluate(Object object) {
                return ((String) object).startsWith("A");
            }
        };

        CollectionUtils.filter(list, pred);
        assertEquals(2, list.size());
        assertTrue(list.contains("A"));
        assertTrue(list.contains("AC"));
        assertFalse(list.contains("B"));

        CollectionUtils.filter(null, pred);
        CollectionUtils.filter(list, null);
    }

    public void testTransform() throws Throwable {
        List list = new ArrayList();
        list.add("a");
        list.add("b");

        Transformer trans = new Transformer() {
            public Object transform(Object input) {
                return ((String) input).toUpperCase();
            }
        };

        CollectionUtils.transform(list, trans);
        assertEquals("A", list.get(0));
        assertEquals("B", list.get(1));

        Set set = new HashSet();
        set.add("a");
        set.add("b");
        CollectionUtils.transform(set, trans);
        assertTrue(set.contains("A"));
        assertTrue(set.contains("B"));

        CollectionUtils.transform(null, trans);
        CollectionUtils.transform(list, null);
    }

    public void testCountMatches() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        list.add("A");

        Predicate pred = new Predicate() {
            public boolean evaluate(Object object) {
                return "A".equals(object);
            }
        };

        assertEquals(2, CollectionUtils.countMatches(list, pred));
        assertEquals(0, CollectionUtils.countMatches(null, pred));
        assertEquals(0, CollectionUtils.countMatches(list, null));
    }

    public void testExists() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");

        Predicate pred = new Predicate() {
            public boolean evaluate(Object object) {
                return "B".equals(object);
            }
        };

        assertTrue(CollectionUtils.exists(list, pred));
        assertFalse(CollectionUtils.exists(list, new Predicate() {
            public boolean evaluate(Object object) {
                return "Z".equals(object);
            }
        }));
        assertFalse(CollectionUtils.exists(null, pred));
        assertFalse(CollectionUtils.exists(list, null));
    }

    public void testSelect() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        list.add("AA");

        Predicate pred = new Predicate() {
            public boolean evaluate(Object object) {
                return "A".equals(object);
            }
        };

        Collection selected = CollectionUtils.select(list, pred);
        assertEquals(1, selected.size());
        assertTrue(selected.contains("A"));

        List output = new ArrayList();
        CollectionUtils.select(list, pred, output);
        assertEquals(1, output.size());

        CollectionUtils.select(null, pred, output);
        CollectionUtils.select(list, null, output);
    }

    public void testSelectRejected() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");

        Predicate pred = new Predicate() {
            public boolean evaluate(Object object) {
                return "A".equals(object);
            }
        };

        Collection rejected = CollectionUtils.selectRejected(list, pred);
        assertEquals(1, rejected.size());
        assertTrue(rejected.contains("B"));

        List output = new ArrayList();
        CollectionUtils.selectRejected(list, pred, output);
        assertEquals(1, output.size());

        CollectionUtils.selectRejected(null, pred, output);
        CollectionUtils.selectRejected(list, null, output);
    }

    public void testCollect() throws Throwable {
        List list = new ArrayList();
        list.add("a");
        list.add("b");

        Transformer trans = new Transformer() {
            public Object transform(Object input) {
                return ((String) input).toUpperCase();
            }
        };

        Collection collected = CollectionUtils.collect(list, trans);
        assertEquals(2, collected.size());
        assertTrue(collected.contains("A"));

        Iterator it = list.iterator();
        Collection collectedIt = CollectionUtils.collect(it, trans);
        assertEquals(2, collectedIt.size());

        List output = new ArrayList();
        CollectionUtils.collect(list, trans, output);
        assertEquals(2, output.size());

        CollectionUtils.collect((Collection) null, trans, output);
        CollectionUtils.collect((Iterator) null, trans, output);
        CollectionUtils.collect(list, (Transformer) null, output);
    }

    public void testAddIgnoreNull() throws Throwable {
        List list = new ArrayList();
        assertTrue(CollectionUtils.addIgnoreNull(list, "A"));
        assertEquals(1, list.size());
        assertFalse(CollectionUtils.addIgnoreNull(list, null));
        assertEquals(1, list.size());
    }

    public void testAddAllIterator() throws Throwable {
        List list = new ArrayList();
        List source = new ArrayList();
        source.add("A");
        source.add("B");

        CollectionUtils.addAll(list, source.iterator());
        assertEquals(2, list.size());
        assertTrue(list.contains("A"));
    }

    public void testAddAllEnumeration() throws Throwable {
        List list = new ArrayList();
        Vector source = new Vector();
        source.add("A");
        source.add("B");

        CollectionUtils.addAll(list, source.elements());
        assertEquals(2, list.size());
        assertTrue(list.contains("A"));
    }

    public void testAddAllArray() throws Throwable {
        List list = new ArrayList();
        String[] source = new String[]{"A", "B"};

        CollectionUtils.addAll(list, source);
        assertEquals(2, list.size());
        assertTrue(list.contains("A"));
    }

    public void testIndexAndGet() throws Throwable {
        Map map = new HashMap();
        map.put("Key1", "Val1");
        map.put(new Integer(0), "ValZero");

        assertEquals("ValZero", CollectionUtils.index(map, new Integer(0)));
        assertEquals("ValZero", CollectionUtils.get(map, 0));
        assertEquals("Val1", CollectionUtils.index(map, "Key1"));
        assertEquals(map, CollectionUtils.index(map, "NonExistent"));

        List list = new ArrayList();
        list.add("A");
        list.add("B");

        assertEquals("A", CollectionUtils.index(list, 0));
        assertEquals("A", CollectionUtils.index(list, new Integer(0)));
        assertEquals("A", CollectionUtils.get(list, 0));

        String[] array = new String[]{"X", "Y"};
        assertEquals("X", CollectionUtils.index(array, 0));
        assertEquals("X", CollectionUtils.index(array, new Integer(0)));
        assertEquals("X", CollectionUtils.get(array, 0));

        Vector vec = new Vector();
        vec.add("V1");
        vec.add("V2");
        assertEquals("V1", CollectionUtils.index(vec.elements(), 0));
        assertEquals("V1", CollectionUtils.get(vec.elements(), 0));

        assertEquals("V1", CollectionUtils.index(vec.iterator(), 0));
        assertEquals("V1", CollectionUtils.get(vec.iterator(), 0));

        assertEquals("V1", CollectionUtils.index(vec, 0));
        assertEquals("V1", CollectionUtils.get(vec, 0));

        assertNull(CollectionUtils.index(null, 0));
        assertNull(CollectionUtils.index("NotACollection", 0));
        assertEquals("NotACollection", CollectionUtils.index("NotACollection", new Integer(-1)));

        try {
            CollectionUtils.get(null, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            CollectionUtils.get(list, -1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            CollectionUtils.get(list, 10);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            CollectionUtils.get("NotSupported", 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        
        try {
            CollectionUtils.get(vec.iterator(), 5);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            CollectionUtils.get(vec.elements(), 5);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    public void testSize() throws Throwable {
        Map map = new HashMap();
        map.put("A", "1");
        assertEquals(1, CollectionUtils.size(map));

        List list = new ArrayList();
        list.add("A");
        assertEquals(1, CollectionUtils.size(list));

        String[] arr = new String[]{"A"};
        assertEquals(1, CollectionUtils.size(arr));

        assertEquals(1, CollectionUtils.size(list.iterator()));

        Vector vec = new Vector();
        vec.add("A");
        assertEquals(1, CollectionUtils.size(vec.elements()));

        try {
            CollectionUtils.size(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            CollectionUtils.size(new Integer(5));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testSizeIsEmpty() throws Throwable {
        Map map = new HashMap();
        assertTrue(CollectionUtils.sizeIsEmpty(map));
        map.put("A", "1");
        assertFalse(CollectionUtils.sizeIsEmpty(map));

        List list = new ArrayList();
        assertTrue(CollectionUtils.sizeIsEmpty(list));
        list.add("A");
        assertFalse(CollectionUtils.sizeIsEmpty(list));

        String[] arrEmpty = new String[0];
        assertTrue(CollectionUtils.sizeIsEmpty(arrEmpty));
        String[] arrFull = new String[]{"A"};
        assertFalse(CollectionUtils.sizeIsEmpty(arrFull));

        assertTrue(CollectionUtils.sizeIsEmpty(new ArrayList().iterator()));
        assertTrue(CollectionUtils.sizeIsEmpty(new Vector().elements()));

        try {
            CollectionUtils.sizeIsEmpty(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            CollectionUtils.sizeIsEmpty(new Integer(5));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testIsEmptyAndIsNotEmpty() throws Throwable {
        assertTrue(CollectionUtils.isEmpty(null));
        assertTrue(CollectionUtils.isEmpty(new ArrayList()));
        assertFalse(CollectionUtils.isEmpty(EMPTY_COLLECTION));

        assertFalse(CollectionUtils.isNotEmpty(null));
        assertFalse(CollectionUtils.isNotEmpty(new ArrayList()));
        assertTrue(CollectionUtils.isNotEmpty(EMPTY_COLLECTION));
    }

    public void testReverseArray() throws Throwable {
        String[] arr = new String[]{"A", "B", "C"};
        CollectionUtils.reverseArray(arr);
        assertEquals("C", arr[0]);
        assertEquals("B", arr[1]);
        assertEquals("A", arr[2]);

        String[] arrEven = new String[]{"A", "B", "C", "D"};
        CollectionUtils.reverseArray(arrEven);
        assertEquals("D", arrEven[0]);
        assertEquals("C", arrEven[1]);
        assertEquals("B", arrEven[2]);
        assertEquals("A", arrEven[3]);
    }

    public void testIsFullAndMaxSize() throws Throwable {
        List list = new ArrayList();
        assertFalse(CollectionUtils.isFull(list));
        assertEquals(-1, CollectionUtils.maxSize(list));

        try {
            CollectionUtils.isFull(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }

        try {
            CollectionUtils.maxSize(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testRetainAllAndRemoveAll() throws Throwable {
        List a = new ArrayList();
        a.add("A");
        a.add("B");

        List b = new ArrayList();
        b.add("A");

        Collection retained = CollectionUtils.retainAll(a, b);
        assertNotNull(retained);

        Collection removed = CollectionUtils.removeAll(a, b);
        assertNotNull(removed);
    }

    public void testDecorators() throws Throwable {
        List list = new ArrayList();
        assertNotNull(CollectionUtils.synchronizedCollection(list));
        assertNotNull(CollectionUtils.unmodifiableCollection(list));
        assertNotNull(CollectionUtils.predicatedCollection(list, new Predicate() {
            public boolean evaluate(Object object) {
                return true;
            }
        }));
        assertNotNull(CollectionUtils.typedCollection(list, String.class));
        assertNotNull(CollectionUtils.transformedCollection(list, new Transformer() {
            public Object transform(Object input) {
                return input;
            }
        }));
    }
}