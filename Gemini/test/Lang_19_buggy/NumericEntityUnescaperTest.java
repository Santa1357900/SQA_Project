package org.apache.commons.lang3.text.translate;

import org.junit.Test;
import java.io.StringWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NumericEntityUnescaperTest {

    @Test
    public void testBasicDecimalUnescape() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&#65;";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(5, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testBasicHexUnescape() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&#x41;";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(6, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testUppercaseHexUnescape() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&#X41;";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(6, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testUnescapeWithoutSemicolon() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&#65XYZ";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(4, consumed);
        assertEquals("A", writer.toString());
    }

    @Test
    public void testSupplementaryCharUnescape() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        // Codepoint 1F000 (supplementary plane, > 0xFFFF)
        String input = "&#128000;";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(10, consumed);
        assertEquals(Character.toChars(128000), writer.toString().toCharArray());
    }

    @Test
    public void testInvalidNumberFormatReturnsZero() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        // Not a valid number between &# and ;
        String input = "&#notanumber;";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testNonMatchingInputReturnsZero() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "ABC";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testIncompleteEntityReturnsZero() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "&";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    @Test
    public void testHashWithoutAmpersandReturnsZero() throws Throwable {
        NumericEntityUnescaper unescaper = new NumericEntityUnescaper();
        StringWriter writer = new StringWriter();
        String input = "#65;";
        
        int consumed = unescaper.translate(input, 0, writer);
        
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }
}