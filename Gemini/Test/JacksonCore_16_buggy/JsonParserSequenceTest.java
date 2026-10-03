package com.fasterxml.jackson.core.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class JsonParserSequenceTest {

    @Test
    public void testCreateFlattenedSimple() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p1 = f.createParser(new StringReader("{\"a\":1}"));
        JsonParser p2 = f.createParser(new StringReader("{\"b\":2}"));

        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        assertNotNull(seq);
        assertEquals(2, seq.containedParsersCount());

        assertEquals(JsonToken.START_OBJECT, seq.nextToken());
        assertEquals(JsonToken.FIELD_NAME, seq.nextToken());
        assertEquals("a", seq.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seq.nextToken());
        assertEquals(1, seq.getIntValue());

        // Switch to next parser via nextToken
        assertEquals(JsonToken.START_OBJECT, seq.nextToken());
        assertEquals(JsonToken.FIELD_NAME, seq.nextToken());
        assertEquals("b", seq.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seq.nextToken());
        assertEquals(2, seq.getIntValue());

        assertNull(seq.nextToken());
        seq.close();
    }

    @Test
    public void testCreateFlattenedNestedFirst() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p1 = f.createParser(new StringReader("1"));
        JsonParser p2 = f.createParser(new StringReader("2"));
        JsonParser p3 = f.createParser(new StringReader("3"));

        JsonParserSequence seq1 = JsonParserSequence.createFlattened(p1, p2);
        JsonParserSequence seq2 = JsonParserSequence.createFlattened(seq1, p3);

        assertEquals(3, seq2.containedParsersCount());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seq2.nextToken());
        assertEquals(1, seq2.getIntValue());

        assertEquals(JsonToken.VALUE_NUMBER_INT, seq2.nextToken());
        assertEquals(2, seq2.getIntValue());

        assertEquals(JsonToken.VALUE_NUMBER_INT, seq2.nextToken());
        assertEquals(3, seq2.getIntValue());

        assertNull(seq2.nextToken());
        seq2.close();
    }

    @Test
    public void testCreateFlattenedNestedSecond() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p1 = f.createParser(new StringReader("1"));
        JsonParser p2 = f.createParser(new StringReader("2"));
        JsonParser p3 = f.createParser(new StringReader("3"));

        JsonParserSequence seq1 = JsonParserSequence.createFlattened(p2, p3);
        JsonParserSequence seq2 = JsonParserSequence.createFlattened(p1, seq1);

        assertEquals(3, seq2.containedParsersCount());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seq2.nextToken());
        assertEquals(1, seq2.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seq2.nextToken());
        assertEquals(2, seq2.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seq2.nextToken());
        assertEquals(3, seq2.getIntValue());
        assertNull(seq2.nextToken());
        seq2.close();
    }

    @Test
    public void testCreateFlattenedBothSequences() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p1 = f.createParser(new StringReader("1"));
        JsonParser p2 = f.createParser(new StringReader("2"));
        JsonParser p3 = f.createParser(new StringReader("3"));
        JsonParser p4 = f.createParser(new StringReader("4"));

        JsonParserSequence seqA = JsonParserSequence.createFlattened(p1, p2);
        JsonParserSequence seqB = JsonParserSequence.createFlattened(p3, p4);
        JsonParserSequence seqCombined = JsonParserSequence.createFlattened(seqA, seqB);

        assertEquals(4, seqCombined.containedParsersCount());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seqCombined.nextToken());
        assertEquals(1, seqCombined.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seqCombined.nextToken());
        assertEquals(2, seqCombined.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seqCombined.nextToken());
        assertEquals(3, seqCombined.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, seqCombined.nextToken());
        assertEquals(4, seqCombined.getIntValue());
        assertNull(seqCombined.nextToken());
        seqCombined.close();
    }

    @Test
    public void testCloseSequence() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p1 = f.createParser(new StringReader("1"));
        JsonParser p2 = f.createParser(new StringReader("2"));

        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        // Switch next parser first to test closing remaining active parsers
        seq.nextToken(); // reads 1 from p1
        // now delegate is p1, but _nextParser is 1 pointing to p2
        seq.close();
        
        assertTrue(p1.isClosed());
        assertTrue(p2.isClosed());
    }

    @Test
    public void testAddFlattenedActiveParsersCustomList() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p1 = f.createParser(new StringReader("1"));
        JsonParser p2 = f.createParser(new StringReader("2"));
        JsonParser p3 = f.createParser(new StringReader("3"));

        JsonParserSequence seq1 = JsonParserSequence.createFlattened(p1, p2);
        // Consume p1 so _nextParser becomes 1 (which references p2)
        assertEquals(JsonToken.VALUE_NUMBER_INT, seq1.nextToken());

        JsonParserSequence seq2 = JsonParserSequence.createFlattened(seq1, p3);
        assertEquals(2, seq2.containedParsersCount());
        
        List<JsonParser> list = new ArrayList<JsonParser>();
        seq2.addFlattenedActiveParsers(list);
        
        // Should contain p2 and p3 (since p1 was already past active)
        assertTrue(list.contains(p2));
        assertTrue(list.contains(p3));
        
        seq2.close();
    }
}