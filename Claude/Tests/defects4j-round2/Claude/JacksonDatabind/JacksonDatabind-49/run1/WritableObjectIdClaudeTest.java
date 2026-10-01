package com.fasterxml.jackson.databind.ser.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WritableObjectIdClaudeTest
{
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
    public static class Node
    {
        private int value;
        private Node next;

        public Node() { }
        public Node(int value) { this.value = value; }

        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public Node getNext() { return next; }
        public void setNext(Node next) { this.next = next; }
    }

    public static class Pair
    {
        private Node first;
        private Node second;

        public Node getFirst() { return first; }
        public void setFirst(Node first) { this.first = first; }
        public Node getSecond() { return second; }
        public void setSecond(Node second) { this.second = second; }
    }

    private String extractIdValue(String json)
    {
        String marker = "\"@id\":";
        int start = json.indexOf(marker) + marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return json.substring(start, end);
    }

    // constructor: generator field must reference the same instance passed in
    @Test
    public void testConstructor_storesGeneratorReference() throws Throwable {
        ObjectIdGenerator<?> gen = new ObjectIdGenerators.IntSequenceGenerator();
        WritableObjectId wid = new WritableObjectId(gen);
        assertSame(gen, wid.generator);
    }

    // constructor: id field must be null before any generation/assignment
    @Test
    public void testConstructor_idIsNullInitially() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        assertNull(wid.id);
    }

    // constructor: idWritten defaults to false before any writeAsField call
    @Test
    public void testConstructor_idWrittenIsFalseInitially() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        assertFalse(wid.idWritten);
    }

    // generateId: first call must produce a non-null id
    @Test
    public void testGenerateId_firstCall_returnsNonNull() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        Object result = wid.generateId(new Object());
        assertNotNull(result);
    }

    // generateId: first call must set the id field to the returned value
    @Test
    public void testGenerateId_firstCall_setsIdFieldToReturnedValue() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        Object result = wid.generateId(new Object());
        assertEquals(result, wid.id);
    }

    // generateId: forPojo may be null, generator must still be able to run
    @Test
    public void testGenerateId_withNullForPojo_stillGeneratesId() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        Object result = wid.generateId(null);
        assertNotNull(result);
    }

    // generateId: returned value equals current id field right after the call
    @Test
    public void testGenerateId_returnedValueEqualsIdFieldAfterCall() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        Object first = wid.generateId(new Object());
        assertEquals(first, wid.id);
    }

    // BUG TEST: javadoc states "need to use existing id if there is one";
    // if id already present, generateId must NOT overwrite it with a new one.
    @Test
    public void testGenerateId_whenIdManuallyPreset_reusesExistingIdPerContract() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        wid.id = Integer.valueOf(999);
        Object result = wid.generateId(new Object());
        assertEquals(Integer.valueOf(999), result);
    }

    // BUG TEST variant: existing id preserved even with null forPojo argument
    @Test
    public void testGenerateId_whenIdManuallyPresetWithNullForPojo_stillReusesExistingId() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        wid.id = Integer.valueOf(777);
        Object result = wid.generateId(null);
        assertEquals(Integer.valueOf(777), result);
        assertEquals(Integer.valueOf(777), wid.id);
    }

    // BUG TEST with a different generator implementation to confirm contract is generator-agnostic
    @Test
    public void testGenerateId_withStringIdGenerator_whenIdPreset_reusesExistingId() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.StringIdGenerator());
        wid.id = "preset-id";
        Object result = wid.generateId(new Object());
        assertEquals("preset-id", result);
    }

    // generateId with StringIdGenerator (no preset id) must produce a non-null String
    @Test
    public void testGenerateId_withStringIdGenerator_returnsNonNullStringId() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.StringIdGenerator());
        Object result = wid.generateId(new Object());
        assertNotNull(result);
        assertTrue(result instanceof String);
    }

    // two distinct instances must not share the same generator object reference
    @Test
    public void testTwoInstances_haveIndependentGeneratorFields() throws Throwable {
        ObjectIdGenerator<?> gen1 = new ObjectIdGenerators.IntSequenceGenerator();
        ObjectIdGenerator<?> gen2 = new ObjectIdGenerators.IntSequenceGenerator();
        WritableObjectId wid1 = new WritableObjectId(gen1);
        WritableObjectId wid2 = new WritableObjectId(gen2);
        assertNotSame(wid1.generator, wid2.generator);
    }

    // generator field must equal exactly the constructor argument (identity)
    @Test
    public void testGeneratorField_isSameInstanceAsConstructorArg() throws Throwable {
        ObjectIdGenerator<?> gen = new ObjectIdGenerators.IntSequenceGenerator();
        WritableObjectId wid = new WritableObjectId(gen);
        assertSame(gen, wid.generator);
    }

    // id field is a plain public Object field, directly assignable
    @Test
    public void testIdField_canBeAssignedDirectly() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        wid.id = "custom-id";
        assertEquals("custom-id", wid.id);
    }

    // idWritten defaults false and can be toggled directly (package-visible field)
    @Test
    public void testIdWrittenField_defaultFalseAndCanBeSetTrueDirectly() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        assertFalse(wid.idWritten);
        wid.idWritten = true;
        assertTrue(wid.idWritten);
    }

    // generateId must not alter the idWritten flag; it only concerns the id value
    @Test
    public void testIdWrittenField_unaffectedByGenerateId() throws Throwable {
        WritableObjectId wid = new WritableObjectId(new ObjectIdGenerators.IntSequenceGenerator());
        wid.idWritten = true;
        wid.generateId(new Object());
        assertTrue(wid.idWritten);
    }

    // writeAsField branch: first occurrence of an identity-info object must emit an "@id" property
    @Test
    public void testSerialization_firstOccurrence_writesIdField() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Node node = new Node(10);
        String json = mapper.writeValueAsString(node);
        assertTrue(json.contains("\"@id\""));
    }

    // writeAsId branch: a self-reference must be serialized as a bare id reference, not a full object again
    @Test
    public void testSerialization_selfReference_writesBackReferenceAsIdOnce() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Node node = new Node(5);
        node.setNext(node);
        String json = mapper.writeValueAsString(node);
        int firstIdIndex = json.indexOf("\"@id\"");
        int secondIdIndex = json.indexOf("\"@id\"", firstIdIndex + 1);
        assertEquals(-1, secondIdIndex);
    }

    // writeAsId branch: a shared reference between two properties must be serialized once, referenced afterward
    @Test
    public void testSerialization_sharedReference_serializedOnceWithIdReuse() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Node shared = new Node(1);
        Pair pair = new Pair();
        pair.setFirst(shared);
        pair.setSecond(shared);
        String json = mapper.writeValueAsString(pair);
        int firstIdIndex = json.indexOf("\"@id\"");
        int secondIdIndex = json.indexOf("\"@id\"", firstIdIndex + 1);
        assertEquals(-1, secondIdIndex);
    }

    // independent serialization calls must each start their own id sequence from the same initial value
    @Test
    public void testSerialization_independentCalls_idSequenceRestartsSame() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Node n1 = new Node(1);
        Node n2 = new Node(2);
        String json1 = mapper.writeValueAsString(n1);
        String json2 = mapper.writeValueAsString(n2);
        assertEquals(extractIdValue(json1), extractIdValue(json2));
    }

    // writeAsField loop: distinct unrelated objects must each get their own "@id" entry, not be deduplicated
    @Test
    public void testSerialization_multipleDistinctNodes_eachGetsOwnId() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Node[] arr = new Node[] { new Node(1), new Node(2) };
        String json = mapper.writeValueAsString(arr);
        int first = json.indexOf("\"@id\"");
        int second = json.indexOf("\"@id\"", first + 1);
        assertTrue(second > first);
    }
}
