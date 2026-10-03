package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokeniserTest {

    @Test
    public void testInitializationAndGetters() throws Throwable {
        CharacterReader reader = new CharacterReader("<html></html>");
        Tokeniser tokeniser = new Tokeniser(reader);

        assertNotNull(tokeniser.getState());
        assertEquals(TokeniserState.Data, tokeniser.getState());
        assertTrue(tokeniser.isTrackErrors());

        tokeniser.setTrackErrors(false);
        assertFalse(tokeniser.isTrackErrors());

        assertTrue(tokeniser.currentNodeInHtmlNS());
    }

    @Test
    public void testTransitions() throws Throwable {
        CharacterReader reader = new CharacterReader("a");
        Tokeniser tokeniser = new Tokeniser(reader);

        tokeniser.transition(TokeniserState.TagOpen);
        assertEquals(TokeniserState.TagOpen, tokeniser.getState());

        tokeniser.advanceTransition(TokeniserState.Data);
        assertEquals(TokeniserState.Data, tokeniser.getState());
    }

    @Test
    public void testBuffersAndEmits() throws Throwable {
        CharacterReader reader = new CharacterReader("");
        Tokeniser tokeniser = new Tokeniser(reader);

        tokeniser.emit('h');
        tokeniser.emit("ello");

        tokeniser.createTempBuffer();
        assertNotNull(tokeniser.dataBuffer);

        Token.Tag startTag = tokeniser.createTagPending(true);
        assertNotNull(startTag);
        assertTrue(startTag instanceof Token.StartTag);

        Token.Tag endTag = tokeniser.createTagPending(false);
        assertNotNull(endTag);
        assertTrue(endTag instanceof Token.EndTag);

        tokeniser.createCommentPending();
        assertNotNull(tokeniser.commentPending);

        tokeniser.createDoctypePending();
        assertNotNull(tokeniser.doctypePending);
    }

    @Test
    public void testConsumeCharacterReferenceEmpty() throws Throwable {
        CharacterReader reader = new CharacterReader("");
        Tokeniser tokeniser = new Tokeniser(reader);

        Character res = tokeniser.consumeCharacterReference(null, false);
        assertNull(res);
    }

    @Test
    public void testConsumeCharacterReferenceAllowedChar() throws Throwable {
        CharacterReader reader = new CharacterReader("x");
        Tokeniser tokeniser = new Tokeniser(reader);

        Character res = tokeniser.consumeCharacterReference('x', false);
        assertNull(res);
    }

    @Test
    public void testConsumeCharacterReferenceMatchesAny() throws Throwable {
        CharacterReader reader = new CharacterReader("\t");
        Tokeniser tokeniser = new Tokeniser(reader);

        Character res = tokeniser.consumeCharacterReference(null, false);
        assertNull(res);
    }

    @Test
    public void testReadWithSelfClosingFlag() throws Throwable {
        CharacterReader reader = new CharacterReader("");
        Tokeniser tokeniser = new Tokeniser(reader);
        
        tokeniser.acknowledgeSelfClosingFlag();
        Token token = tokeniser.read();
        assertNull(token);
    }
}