package com.fasterxml.jackson.core.util;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

public class JsonParserSequenceClaudeTest
{
    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable {
        factory = new JsonFactory();
    }

    // createFlattened: neither arg is a sequence -> simple 2-parser sequence
    @Test
    public void testCreateFlattened_neitherIsSequence_createsSequenceWithTwoParsers() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        assertEquals(2, seq.containedParsersCount());
    }

    // createFlattened: first arg is a sequence -> flattens into 3 parsers
    @Test
    public void testCreateFlattened_firstIsSequence_flattensIntoThreeParsers() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParser p3 = factory.createParser("3");
        JsonParserSequence seqA = JsonParserSequence.createFlattened(p1, p2);
        JsonParserSequence combined = JsonParserSequence.createFlattened(seqA, p3);
        assertEquals(3, combined.containedParsersCount());
    }

    // createFlattened: second arg is a sequence -> flattens into 3 parsers
    @Test
    public void testCreateFlattened_secondIsSequence_flattensIntoThreeParsers() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParser p3 = factory.createParser("3");
        JsonParserSequence seqA = JsonParserSequence.createFlattened(p2, p3);
        JsonParserSequence combined = JsonParserSequence.createFlattened(p1, seqA);
        assertEquals(3, combined.containedParsersCount());
    }

    // createFlattened: both args are sequences -> flattens into 4 parsers
    @Test
    public void testCreateFlattened_bothAreSequences_flattensIntoFourParsers() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParser p3 = factory.createParser("3");
        JsonParser p4 = factory.createParser("4");
        JsonParserSequence seqA = JsonParserSequence.createFlattened(p1, p2);
        JsonParserSequence seqB = JsonParserSequence.createFlattened(p3, p4);
        JsonParserSequence combined = JsonParserSequence.createFlattened(seqA, seqB);
        assertEquals(4, combined.containedParsersCount());
    }

    // addFlattenedActiveParsers: fresh sequence -> all parsers added in order
    @Test
    public void testAddFlattenedActiveParsers_freshSequence_addsAllParsersInOrder() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        List<JsonParser> result = new ArrayList<JsonParser>();
        seq.addFlattenedActiveParsers(result);
        assertEquals(2, result.size());
        assertSame(p1, result.get(0));
        assertSame(p2, result.get(1));
    }

    // addFlattenedActiveParsers: after switching to second parser, only remaining active parser is added
    @Test
    public void testAddFlattenedActiveParsers_afterAdvance_addsOnlyRemainingParsers() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        seq.nextToken();
        seq.nextToken();
        List<JsonParser> result = new ArrayList<JsonParser>();
        seq.addFlattenedActiveParsers(result);
        assertEquals(1, result.size());
        assertSame(p2, result.get(0));
    }

    // close(): fresh sequence (no prior nextToken) closes all contained parsers
    @Test
    public void testClose_freshSequence_closesAllParsers() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        seq.close();
        assertTrue(p1.isClosed());
        assertTrue(p2.isClosed());
    }

    // close(): after nextToken() advances past first parser, close() must still close ALL
    // parsers that were part of the sequence, including the already-exhausted first one.
    @Test
    public void testClose_afterAdvancePastFirstParser_closesAllParsersIncludingSkipped() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        seq.nextToken();
        seq.nextToken();
        seq.close();
        assertTrue(p2.isClosed());
        assertTrue(p1.isClosed());
    }

    // nextToken(): first parser's tokens are returned in order without switching
    @Test
    public void testNextToken_firstParserHasTokens_returnsTokensInOrder() throws Throwable {
        JsonParser p1 = factory.createParser("{}");
        JsonParser p2 = factory.createParser("true");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        assertEquals(JsonToken.START_OBJECT, seq.nextToken());
        assertEquals(JsonToken.END_OBJECT, seq.nextToken());
    }

    // nextToken(): once first parser is exhausted, switches to second parser transparently
    @Test
    public void testNextToken_firstParserExhausted_switchesToSecondParser() throws Throwable {
        JsonParser p1 = factory.createParser("{}");
        JsonParser p2 = factory.createParser("true");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        seq.nextToken();
        seq.nextToken();
        JsonToken t = seq.nextToken();
        assertEquals(JsonToken.VALUE_TRUE, t);
    }

    // nextToken(): while-loop iterates multiple times across multiple empty parsers
    @Test
    public void testNextToken_multipleEmptyParsers_loopsMultipleTimesBeforeReturningToken() throws Throwable {
        JsonParser p1 = factory.createParser("");
        JsonParser p2 = factory.createParser("");
        JsonParser p3 = factory.createParser("5");
        JsonParserSequence seqAB = JsonParserSequence.createFlattened(p1, p2);
        JsonParserSequence combined = JsonParserSequence.createFlattened(seqAB, p3);
        JsonToken t = combined.nextToken();
        assertEquals(JsonToken.VALUE_NUMBER_INT, t);
    }

    // nextToken(): once all parsers exhausted, returns null
    @Test
    public void testNextToken_allParsersExhausted_returnsNull() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        seq.nextToken();
        seq.nextToken();
        JsonToken t = seq.nextToken();
        assertNull(t);
    }

    // nextToken(): propagates JsonParseException thrown by an underlying delegate parser
    @Test
    public void testNextToken_invalidJsonInFirstParser_throwsJsonParseException() throws Throwable {
        JsonParser p1 = factory.createParser("{ abc");
        JsonParser p2 = factory.createParser("1");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        seq.nextToken();
        try {
            seq.nextToken();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) {
            // expected
        }
    }

    // containedParsersCount(): simple two-parser sequence
    @Test
    public void testContainedParsersCount_twoParsers_returnsTwo() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        assertEquals(2, seq.containedParsersCount());
    }

    // containedParsersCount(): flattened sequence reports total original parsers, not just active
    @Test
    public void testContainedParsersCount_flattenedThreeParsers_returnsThree() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParser p3 = factory.createParser("3");
        JsonParserSequence seqA = JsonParserSequence.createFlattened(p1, p2);
        JsonParserSequence combined = JsonParserSequence.createFlattened(seqA, p3);
        combined.nextToken();
        combined.nextToken();
        assertEquals(3, combined.containedParsersCount());
    }

    // switchToNext(): true when another parser is available, false once exhausted
    @Test
    public void testSwitchToNext_trueThenFalse_switchesDelegateThenStops() throws Throwable {
        JsonParser p1 = factory.createParser("1");
        JsonParser p2 = factory.createParser("2");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        boolean switched = seq.switchToNext();
        assertTrue(switched);
        assertSame(p2, seq.delegate);
        boolean switchedAgain = seq.switchToNext();
        assertFalse(switchedAgain);
    }

    // nextToken(): empty first parser then value in second parser (boundary of while-loop)
    @Test
    public void testNextToken_emptyFirstParserThenValue_returnsSecondParserToken() throws Throwable {
        JsonParser p1 = factory.createParser("");
        JsonParser p2 = factory.createParser("true");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        JsonToken t = seq.nextToken();
        assertEquals(JsonToken.VALUE_TRUE, t);
    }

    // nextToken(): full sequence of object tokens from first parser, then switch to second
    @Test
    public void testNextToken_objectTokensFromFirstParserThenSwitch_returnsCorrectSequence() throws Throwable {
        JsonParser p1 = factory.createParser("{}");
        JsonParser p2 = factory.createParser("null");
        JsonParserSequence seq = JsonParserSequence.createFlattened(p1, p2);
        assertEquals(JsonToken.START_OBJECT, seq.nextToken());
        assertEquals(JsonToken.END_OBJECT, seq.nextToken());
        assertEquals(JsonToken.VALUE_NULL, seq.nextToken());
        assertNull(seq.nextToken());
    }
}
