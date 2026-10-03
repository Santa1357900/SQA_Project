package org.apache.commons.jxpath.ri.axes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.jxpath.JXPathContext;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Black-box tests for AttributeContext exercised through JXPathContext,
 * because AttributeContext cannot be instantiated directly without
 * undocumented internal collaborators (EvalContext subclasses, QName,
 * NodeNameTest constructors) that are not part of the provided source.
 */
public class AttributeContextClaudeTest {

    public static class SimpleBean {
        private String name;
        private int age;
        private boolean active;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static class Address {
        private String city;
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    public static class Person {
        private String name;
        private Address address;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    // nextNode(): NodeNameTest branch finds an existing bean property, returns its String value
    @Test
    public void testGetValue_existingStringAttributeOnBean_returnsValue() throws Throwable {
        SimpleBean bean = new SimpleBean();
        bean.setName("Alice");
        JXPathContext context = JXPathContext.newContext(bean);
        assertEquals("Alice", context.getValue("@name"));
    }

    // nextNode(): existing int bean property returned as boxed numeric value
    @Test
    public void testGetValue_existingIntAttributeOnBean_returnsBoxedValue() throws Throwable {
        SimpleBean bean = new SimpleBean();
        bean.setAge(30);
        JXPathContext context = JXPathContext.newContext(bean);
        Object result = context.getValue("@age");
        assertEquals(30, ((Number) result).intValue());
    }

    // nextNode(): existing boolean bean property returned as Boolean value
    @Test
    public void testGetValue_existingBooleanAttributeOnBean_returnsBoxedValue() throws Throwable {
        SimpleBean bean = new SimpleBean();
        bean.setActive(true);
        JXPathContext context = JXPathContext.newContext(bean);
        assertEquals(Boolean.TRUE, context.getValue("@active"));
    }

    // parentContext chaining: attribute axis applied after a child step reaches nested bean's property
    @Test
    public void testGetValue_nestedChildThenAttribute_returnsNestedAttributeValue() throws Throwable {
        Person person = new Person();
        Address addr = new Address();
        addr.setCity("Chiang Mai");
        person.setAddress(addr);
        JXPathContext context = JXPathContext.newContext(person);
        assertEquals("Chiang Mai", context.getValue("address/@city"));
    }

    // nextNode(): wildcard NodeNameTest on a nested single-property bean yields that one value
    @Test
    public void testGetValue_nestedWildcardAttribute_returnsSinglePropertyOfNestedBean() throws Throwable {
        Person person = new Person();
        Address addr = new Address();
        addr.setCity("Phuket");
        person.setAddress(addr);
        JXPathContext context = JXPathContext.newContext(person);
        assertEquals("Phuket", context.getValue("address/@*"));
    }

    // nextNode() loop: multiple iterations over a wildcard test collect all map entries in order
    @Test
    public void testIterate_wildcardAttributeOnMapWithMultipleEntries_returnsAllValuesInOrder() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("city", "Bangkok");
        map.put("zip", "10110");
        JXPathContext context = JXPathContext.newContext(map);
        Iterator it = context.iterate("@*");
        List<Object> values = new ArrayList<Object>();
        while (it.hasNext()) { values.add(it.next()); }
        assertEquals(2, values.size());
        assertEquals("Bangkok", values.get(0));
        assertEquals("10110", values.get(1));
    }

    // nextNode(): zero-iteration loop when iterator has no elements (empty map)
    @Test
    public void testIterate_wildcardAttributeOnEmptyMap_returnsNoElements() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        JXPathContext context = JXPathContext.newContext(map);
        Iterator it = context.iterate("@*");
        assertFalse(it.hasNext());
    }

    // nextNode(): single-iteration loop for a one-entry map
    @Test
    public void testIterate_wildcardAttributeOnMapWithSingleEntry_returnsSingleElement() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("onlyKey", "onlyValue");
        JXPathContext context = JXPathContext.newContext(map);
        Iterator it = context.iterate("@*");
        assertEquals("onlyValue", it.next());
        assertFalse(it.hasNext());
    }

    // setPosition(1): positional predicate selects the first matching attribute
    @Test
    public void testGetValue_positionalPredicateFirstAttribute_returnsFirstMapValue() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "v1");
        map.put("b", "v2");
        JXPathContext context = JXPathContext.newContext(map);
        assertEquals("v1", context.getValue("@*[1]"));
    }

    // setPosition(2): loop advances nextNode() twice to reach the second attribute
    @Test
    public void testGetValue_positionalPredicateSecondAttribute_returnsSecondMapValue() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "v1");
        map.put("b", "v2");
        JXPathContext context = JXPathContext.newContext(map);
        assertEquals("v2", context.getValue("@*[2]"));
    }

    // setPosition(): requested position beyond available attributes causes nextNode() to fail, no match
    @Test
    public void testIterate_positionalPredicateOutOfRange_returnsNoElements() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "v1");
        map.put("b", "v2");
        JXPathContext context = JXPathContext.newContext(map);
        Iterator it = context.iterate("@*[3]");
        assertFalse(it.hasNext());
    }

    // nextNode(): dynamic property pointer on a Map for an existing key returns its value
    @Test
    public void testGetValue_mapAttributeExistingKey_returnsValue() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("color", "red");
        JXPathContext context = JXPathContext.newContext(map);
        assertEquals("red", context.getValue("@color"));
    }

    // nextNode(): per Map contract, an undefined key is a valid dynamic attribute with null value
    @Test
    public void testGetValue_mapAttributeMissingKey_returnsNull() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("color", "red");
        JXPathContext context = JXPathContext.newContext(map);
        assertNull(context.getValue("@missingKey"));
    }

    // nextNode(): boolean() function confirms an attribute node actually exists for a bean property
    @Test
    public void testGetValue_booleanFunctionOnExistingBeanAttribute_returnsTrue() throws Throwable {
        SimpleBean bean = new SimpleBean();
        bean.setName("Somchai");
        JXPathContext context = JXPathContext.newContext(bean);
        assertEquals(Boolean.TRUE, context.getValue("boolean(@name)"));
    }

    // nextNode() loop count: count() over wildcard attributes matches number of map entries
    @Test
    public void testGetValue_countFunctionOnWildcardAttributes_returnsCorrectCount() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "1");
        map.put("b", "2");
        JXPathContext context = JXPathContext.newContext(map);
        Object result = context.getValue("count(@*)");
        assertEquals(2.0, ((Number) result).doubleValue(), 1e-9);
    }

    // nextNode() full loop: last() predicate requires iterating through all attributes to find the final one
    @Test
    public void testGetValue_lastFunctionPredicateOnWildcardAttributes_returnsLastValue() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "v1");
        map.put("b", "v2");
        map.put("c", "v3");
        JXPathContext context = JXPathContext.newContext(map);
        assertEquals("v3", context.getValue("@*[last()]"));
    }

    // reset()/fresh evaluation: repeated iteration of the same expression yields consistent results each time
    @Test
    public void testIterate_calledTwiceOnSameExpression_producesConsistentResults() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "v1");
        map.put("b", "v2");
        JXPathContext context = JXPathContext.newContext(map);
        Iterator first = context.iterate("@*");
        Iterator second = context.iterate("@*");
        assertEquals(first.next(), second.next());
        assertEquals(first.next(), second.next());
    }

    // nextNode(): wildcard attribute on a single-entry map returns exactly that value via getValue
    @Test
    public void testGetValue_singleEntryMapWildcard_returnsThatSingleValue() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("k", "onlyVal");
        JXPathContext context = JXPathContext.newContext(map);
        assertEquals("onlyVal", context.getValue("@*"));
    }

    // setPosition(3): loop advances nextNode() three times to reach the third attribute in order
    @Test
    public void testGetValue_threeEntryMapPositionThree_returnsThirdValue() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "v1");
        map.put("b", "v2");
        map.put("c", "v3");
        JXPathContext context = JXPathContext.newContext(map);
        assertEquals("v3", context.getValue("@*[3]"));
    }

    // nextNode() loop over three entries: wildcard iteration visits every attribute in insertion order
    @Test
    public void testIterate_threeEntryMapWildcard_returnsAllThreeInOrder() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("a", "v1");
        map.put("b", "v2");
        map.put("c", "v3");
        JXPathContext context = JXPathContext.newContext(map);
        Iterator it = context.iterate("@*");
        List<Object> values = new ArrayList<Object>();
        while (it.hasNext()) { values.add(it.next()); }
        assertEquals("v1", values.get(0));
        assertEquals("v2", values.get(1));
        assertEquals("v3", values.get(2));
    }

    // nextNode(): an existing key mapped to an empty string is still a valid attribute value, not null
    @Test
    public void testGetValue_mapAttributeKeyWithEmptyStringValue_returnsEmptyString() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("note", "");
        JXPathContext context = JXPathContext.newContext(map);
        assertEquals("", context.getValue("@note"));
    }

    // getCurrentNodePointer()/nextNode(): attribute access reflects the bean's current property state on each call
    @Test
    public void testGetValue_beanAttributeAfterModifyingPropertyBetweenCalls_reflectsUpdatedValue() throws Throwable {
        SimpleBean bean = new SimpleBean();
        bean.setName("First");
        JXPathContext context = JXPathContext.newContext(bean);
        assertEquals("First", context.getValue("@name"));
        bean.setName("Second");
        assertEquals("Second", context.getValue("@name"));
    }

    // nextNode(): distinct simple attributes on the same bean are independently resolvable in one context
    @Test
    public void testGetValue_multipleDistinctAttributesOnSameBean_eachResolvesIndependently() throws Throwable {
        SimpleBean bean = new SimpleBean();
        bean.setName("Nok");
        bean.setAge(25);
        JXPathContext context = JXPathContext.newContext(bean);
        assertEquals("Nok", context.getValue("@name"));
        assertEquals(25, ((Number) context.getValue("@age")).intValue());
    }


}
