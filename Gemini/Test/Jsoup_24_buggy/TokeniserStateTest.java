package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokeniserStateTest {

    @Test
    public void testTokeniserStateConstants() throws Throwable {
        TokeniserState[] states = TokeniserState.values();
        assertTrue(states.length > 0);
        
        TokeniserState dataState = TokeniserState.valueOf("Data");
        assertNotNull(dataState);
    }

    @Test
    public void testDataStateReadAmpersand() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("&");
        TokeniserState.Data.read(t, r);
        assertEquals(TokeniserState.CharacterReferenceInData, t.nextState);
    }

    @Test
    public void testDataStateReadTagOpen() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("<");
        TokeniserState.Data.read(t, r);
        assertEquals(TokeniserState.TagOpen, t.nextState);
    }

    @Test
    public void testDataStateReadNullChar() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("\u0000");
        TokeniserState.Data.read(t, r);
        assertTrue(t.errorCalled);
        assertEquals("\u0000", t.emittedString);
    }

    @Test
    public void testDataStateReadEof() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("");
        TokeniserState.Data.read(t, r);
        assertNotNull(t.emittedToken);
        assertTrue(t.emittedToken instanceof Token.EOF);
    }

    @Test
    public void testDataStateReadDefault() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("abc&");
        TokeniserState.Data.read(t, r);
        assertEquals("abc", t.emittedString);
    }

    @Test
    public void testCharacterReferenceInDataReadNull() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        t.charRefToReturn = null;
        CharacterReader r = new CharacterReader("");
        TokeniserState.CharacterReferenceInData.read(t, r);
        assertEquals("&", t.emittedString);
        assertEquals(TokeniserState.Data, t.nextState);
    }

    @Test
    public void testCharacterReferenceInDataReadChar() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        t.charRefToReturn = Character.valueOf('X');
        CharacterReader r = new CharacterReader("");
        TokeniserState.CharacterReferenceInData.read(t, r);
        assertEquals("X", t.emittedString);
        assertEquals(TokeniserState.Data, t.nextState);
    }

    @Test
    public void testRcdataStateRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("&");
        TokeniserState.Rcdata.read(t, r);
        assertEquals(TokeniserState.CharacterReferenceInRcdata, t.nextState);
    }

    @Test
    public void testRawtextStateRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("<");
        TokeniserState.Rawtext.read(t, r);
        assertEquals(TokeniserState.RawtextLessthanSign, t.nextState);
    }

    @Test
    public void testScriptDataStateRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("<");
        TokeniserState.ScriptData.read(t, r);
        assertEquals(TokeniserState.ScriptDataLessthanSign, t.nextState);
    }

    @Test
    public void testPlaintextStateRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("\u0000");
        TokeniserState.PLAINTEXT.read(t, r);
        assertTrue(t.errorCalled);
    }

    @Test
    public void testTagOpenStateRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("!");
        TokeniserState.TagOpen.read(t, r);
        assertEquals(TokeniserState.MarkupDeclarationOpen, t.nextState);
    }

    @Test
    public void testEndTagOpenStateRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("");
        TokeniserState.EndTagOpen.read(t, r);
        assertTrue(t.eofErrorCalled);
        assertEquals("</", t.emittedString);
    }

    @Test
    public void testTagNameStateRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("div>");
        t.tagPending = new Token.StartTag();
        TokeniserState.TagName.read(t, r);
        assertTrue(t.tagEmitted);
    }

    @Test
    public void testCdataSectionRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("some data]]>remainder");
        TokeniserState.CdataSection.read(t, r);
        assertEquals("some data", t.emittedString);
        assertEquals(TokeniserState.Data, t.nextState);
    }

    @Test
    public void testBogusCommentRead() throws Throwable {
        TokeniseriserMock t = new TokeniseriserMock();
        CharacterReader r = new CharacterReader("comment>remainder");
        TokeniserState.BogusComment.read(t, r);
        assertNotNull(t.emittedComment);
        assertEquals(TokeniserState.Data, t.nextState);
    }

    private static class TokeniseriserMock extends Tokeniser {
        TokeniserState nextState;
        boolean errorCalled = false;
        boolean eofErrorCalled = false;
        String emittedString = null;
        Token emittedToken = null;
        Token.Comment emittedComment = null;
        Character charRefToReturn = null;
        boolean tagEmitted = false;

        TokeniseriserMock() {
            super(new CharacterReader(""), new ParseErrorList(1));
        }

        public void advanceTransition(TokeniserState state) {
            this.nextState = state;
        }

        public void transition(TokeniserState state) {
            this.nextState = state;
        }

        public void error(TokeniserState state) {
            this.errorCalled = true;
        }

        public void eofError(TokeniserState state) {
            this.eofErrorCalled = true;
        }

        public void emit(String str) {
            this.emittedString = str;
        }

        public void emit(char c) {
            this.emittedString = String.valueOf(c);
        }

        public void emit(Token token) {
            this.emittedToken = token;
        }

        public void emit(Token.Comment comment) {
            this.emittedComment = comment;
        }

        public Character consumeCharacterReference(Character additionalAllowedCharacter, boolean inAttribute) {
            return charRefToReturn;
        }

        public void emitTagPending() {
            this.tagEmitted = true;
        }

        public void createTagPending(boolean start) {
            this.tagPending = new Token.StartTag();
        }

        public void createCommentPending() {
            this.commentPending = new Token.Comment();
        }

        public void emitCommentPending() {
            this.emittedComment = this.commentPending;
        }

        public void createDoctypePending() {
            this.doctypePending = new Token.Doctype();
        }

        public void emitDoctypePending() {
            // no-op
        }

        public boolean isAppropriateEndTagToken() {
            return true;
        }

        public String appropriateEndTagName() {
            return "title";
        }
    }
}