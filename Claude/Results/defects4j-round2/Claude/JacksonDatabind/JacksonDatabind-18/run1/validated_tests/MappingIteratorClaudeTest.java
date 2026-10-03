package com.fasterxml.jackson.databind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

public class MappingIteratorClaudeTest {

    private ObjectMapper mapper;

    public static class SimplePojo {
        private int value;
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // hasNext(): parser with no tokens at all -> immediately exhausted
    @Test
    public void testHasNext_emptyInput_returnsFalse() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertFalse(it.hasNext());
    }

    // hasNext()/next(): single root value then exhausted
    @Test
    public void testHasNext_singleValue_trueThenFalse() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("42");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(42), it.next());
        assertFalse(it.hasNext());
    }

    // hasNext() idempotent when called repeatedly without consuming via next()
    @Test
    public void testHasNext_calledTwiceWithoutNext_doesNotSkipValue() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("5 6");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(5), it.next());
        assertEquals(Integer.valueOf(6), it.next());
        assertFalse(it.hasNext());
    }

    // hasNext()/next(): multiple root-level values iterate in order
    @Test
    public void testHasNext_multipleRootValues_iteratesInOrder() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1 2 3");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        List<Integer> list = new ArrayList<Integer>();
        while (it.hasNext()) {
            list.add(it.next());
        }
        assertEquals(Arrays.asList(1, 2, 3), list);
    }



    // next(): NoSuchElementException once exhausted
    @Test
    public void testNext_afterExhausted_throwsNoSuchElementException() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        it.next();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // next(): invalid coercion (non-numeric string into Integer) wraps JsonMappingException as RuntimeJsonMappingException
    @Test
    public void testNext_invalidTypeCoercion_throwsRuntimeJsonMappingException() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("\"abc\"");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertTrue(it.hasNext());
        try {
            it.next();
            fail("expected RuntimeJsonMappingException");
        } catch (RuntimeJsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // remove(): always unsupported regardless of state
    @Test
    public void testRemove_alwaysThrowsUnsupportedOperationException() throws Throwable {
        MappingIterator<Object> it = MappingIterator.emptyIterator();
        try {
            it.remove();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // close(): closes the underlying parser
    @Test
    public void testClose_closesUnderlyingParser() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertFalse(p.isClosed());
        it.close();
        assertTrue(p.isClosed());
    }



    // hasNextValue(): checked equivalent of hasNext detects first available token
    @Test
    public void testHasNextValue_freshParser_detectsFirstToken() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("7");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertTrue(it.hasNextValue());
        assertEquals(Integer.valueOf(7), it.nextValue());
        assertFalse(it.hasNextValue());
    }

    // nextValue(): auto-checks hasNextValue when not previously called
    @Test
    public void testNextValue_withoutPriorHasNextCheck_autoChecks() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("99");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertEquals(Integer.valueOf(99), it.nextValue());
    }

    // nextValue(): throws NoSuchElementException when exhausted
    @Test
    public void testNextValue_afterExhausted_throwsNoSuchElementException() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertFalse(it.hasNextValue());
        try {
            it.nextValue();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }



    // nextValue(): invalid coercion propagates checked JsonMappingException directly (not wrapped)
    @Test
    public void testNextValue_invalidTypeCoercion_throwsJsonMappingExceptionDirectly() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("\"abc\"");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertTrue(it.hasNextValue());
        try {
            it.nextValue();
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // readAll(): no-arg convenience returns ArrayList with all elements in order
    @Test
    public void testReadAll_noArg_returnsArrayListWithAllElements() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1 2 3");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        List<Integer> result = it.readAll();
        assertTrue(result instanceof ArrayList);
        assertEquals(Arrays.asList(1, 2, 3), result);
    }

    // readAll(): empty source yields empty list, not null
    @Test
    public void testReadAll_noArg_emptyInput_returnsEmptyList() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        List<Integer> result = it.readAll();
        assertTrue(result.isEmpty());
    }

    // readAll(List): appends into provided list and returns same instance
    @Test
    public void testReadAll_listArg_appendsAndReturnsSameInstance() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1 2");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        List<Integer> existing = new ArrayList<Integer>();
        existing.add(100);
        List<Integer> result = it.readAll(existing);
        assertSame(existing, result);
        assertEquals(Arrays.asList(100, 1, 2), result);
    }

    // readAll(Collection): populates provided collection (non-List) fully
    @Test
    public void testReadAll_collectionArg_populatesProvidedSet() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("7 8");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        Collection<Integer> coll = new HashSet<Integer>();
        Collection<Integer> result = it.readAll(coll);
        assertSame(coll, result);
        assertEquals(2, result.size());
        assertTrue(result.contains(7));
        assertTrue(result.contains(8));
    }

    // getParser(): returns exact underlying parser instance
    @Test
    public void testGetParser_returnsUnderlyingParserInstance() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertSame(p, it.getParser());
    }



    // getParserSchema(): plain JSON parser has no schema
    @Test
    public void testGetParserSchema_forJsonInput_returnsNull() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertNull(it.getParserSchema());
    }

    // getCurrentLocation(): delegates to parser and yields non-null location
    @Test
    public void testGetCurrentLocation_returnsNonNullLocation() throws Throwable {
        JsonParser p = mapper.getFactory().createParser("1");
        MappingIterator<Integer> it = mapper.readValues(p, Integer.class);
        assertNotNull(it.getCurrentLocation());
    }

    // emptyIterator(): hasNext is always false
    @Test
    public void testEmptyIterator_hasNextReturnsFalse() throws Throwable {
        MappingIterator<Object> it = MappingIterator.emptyIterator();
        assertFalse(it.hasNext());
    }

    // emptyIterator(): next() throws NoSuchElementException
    @Test
    public void testEmptyIterator_nextThrowsNoSuchElementException() throws Throwable {
        MappingIterator<Object> it = MappingIterator.emptyIterator();
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // emptyIterator(): close() is a safe no-op since parser is null
    @Test
    public void testEmptyIterator_closeDoesNotThrow() throws Throwable {
        MappingIterator<Object> it = MappingIterator.emptyIterator();
        it.close();
        assertNull(it.getParser());
    }

    // readValues on POJO sequence deserializes each element's fields correctly
    @Test
    public void testReadValues_pojoSequence_deserializesFieldsCorrectly() throws Throwable {
        String json = "{\"value\":1} {\"value\":2}";
        JsonParser p = mapper.getFactory().createParser(json);
        MappingIterator<SimplePojo> it = mapper.readValues(p, SimplePojo.class);
        List<SimplePojo> list = it.readAll();
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).getValue());
        assertEquals(2, list.get(1).getValue());
    }
}
