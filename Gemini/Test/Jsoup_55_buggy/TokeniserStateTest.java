package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokeniserStateTest {

    @Test
    public void testTokeniserStatesCoverage() throws Throwable {
        TokeniserState[] states = TokeniserState.values();
        assertTrue(states.length > 0);
        
        for (TokeniserState state : states) {
            assertNotNull(state.name());
        }
    }

    @Test
    public void testDataState() throws Throwable {
        TokeniserState state = TokeniserState.Data;
        assertNotNull(state);
    }

    @Test
    public void testCharacterReferenceInDataState() throws Throwable {
        TokeniserState state = TokeniserState.CharacterReferenceInData;
        assertNotNull(state);
    }

    @Test
    public void testRcdataState() throws Throwable {
        TokeniserState state = TokeniserState.Rcdata;
        assertNotNull(state);
    }

    @Test
    public void testRawtextState() throws Throwable {
        TokeniserState state = TokeniserState.Rawtext;
        assertNotNull(state);
    }

    @Test
    public void testScriptDataState() throws Throwable {
        TokeniserState state = TokeniserState.ScriptData;
        assertNotNull(state);
    }

    @Test
    public void testPlaintextState() throws Throwable {
        TokeniserState state = TokeniserState.PLAINTEXT;
        assertNotNull(state);
    }

    @Test
    public void testTagOpenState() throws Throwable {
        TokeniserState state = TokeniserState.TagOpen;
        assertNotNull(state);
    }

    @Test
    public void testEndTagOpenState() throws Throwable {
        TokeniserState state = TokeniserState.EndTagOpen;
        assertNotNull(state);
    }

    @Test
    public void testTagNameState() throws Throwable {
        TokeniserState state = TokeniserState.TagName;
        assertNotNull(state);
    }

    @Test
    public void testBogusCommentState() throws Throwable {
        TokeniserState state = TokeniserState.BogusComment;
        assertNotNull(state);
    }

    @Test
    public void testCdataSectionState() throws Throwable {
        TokeniserState state = TokeniserState.CdataSection;
        assertNotNull(state);
    }
}