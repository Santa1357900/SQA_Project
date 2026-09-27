package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.javascript.rhino.Node;

public class CodeConsumerTest {

    private static class ConcreteCodeConsumer extends CodeConsumer {
        private StringBuilder sb = new StringBuilder();
        private char lastChar = '\0';

        @Override
        char getLastChar() {
            return lastChar;
        }

        @Override
        void append(String str) {
            sb.append(str);
            if (str.length() > 0) {
                lastChar = str.charAt(str.length() - 1);
            }
        }

        public String getOutput() {
            return sb.toString();
        }
    }

    @Test
    public void testInitialState() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        assertFalse(consumer.statementNeedsEnded);
        assertFalse(consumer.statementStarted);
        assertFalse(consumer.sawFunction);
        assertTrue(consumer.continueProcessing());
        assertFalse(consumer.shouldPreserveExtraBlocks());
        assertFalse(consumer.breakAfterBlockFor(null, false));
        assertTrue(consumer.breakAfterBlockFor(null, true));
    }

    @Test
    public void testSourceMappingAndLines() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        Node node = new Node(Token.BLOCK);
        consumer.startSourceMapping(node);
        consumer.endSourceMapping(node);
        consumer.startNewLine();
        consumer.maybeLineBreak();
        consumer.maybeCutLine();
        consumer.endLine();
        consumer.notePreferredLineBreak();
        consumer.endFile();
        assertEquals("", consumer.getOutput());
    }

    @Test
    public void testBlocks() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.appendBlockStart();
        consumer.appendBlockEnd();
        assertEquals("{}", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.statementNeedsEnded = true;
        consumer.beginBlock();
        assertEquals("{", consumer.getOutput());
        assertFalse(consumer.statementNeedsEnded);

        consumer = new ConcreteCodeConsumer();
        consumer.endBlock();
        assertEquals("}", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.endBlock(true);
        assertEquals("}", consumer.getOutput());
    }

    @Test
    public void testListSeparator() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.listSeparator();
        assertEquals(",", consumer.getOutput());
    }

    @Test
    public void testStatements() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.endStatement();
        
        consumer = new ConcreteCodeConsumer();
        consumer.endStatement(true);
        assertEquals(";", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.statementStarted = true;
        consumer.endStatement(false);
        assertTrue(consumer.statementNeedsEnded);

        consumer = new ConcreteCodeConsumer();
        consumer.statementNeedsEnded = true;
        consumer.maybeEndStatement();
        assertEquals(";", consumer.getOutput());
        assertTrue(consumer.statementStarted);
        assertFalse(consumer.statementNeedsEnded);
    }

    @Test
    public void testFunctions() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.endFunction();
        assertTrue(consumer.sawFunction);

        consumer = new ConcreteCodeConsumer();
        consumer.endFunction(true);
        assertTrue(consumer.sawFunction);
    }

    @Test
    public void testCaseBody() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.beginCaseBody();
        consumer.endCaseBody();
        assertEquals(":", consumer.getOutput());
    }

    @Test
    public void testAddAndWordChars() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.add("");
        assertEquals("", consumer.getOutput());

        consumer.add("foo");
        assertEquals("foo", consumer.getOutput());

        consumer.add("bar");
        assertEquals("foo bar", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.addIdentifier("myId");
        assertEquals("myId", consumer.getOutput());

        assertTrue(CodeConsumer.isWordChar('_'));
        assertTrue(CodeConsumer.isWordChar('$'));
        assertTrue(CodeConsumer.isWordChar('a'));
        assertTrue(CodeConsumer.isWordChar('9'));
        assertFalse(CodeConsumer.isWordChar(' '));
        assertFalse(CodeConsumer.isWordChar('+'));
    }

    @Test
    public void testAddOp() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.addOp("+", false);
        assertEquals("+", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.lastChar = '+';
        consumer.addOp("+", false);
        assertEquals(" +", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.lastChar = 'a';
        consumer.addOp("instanceof", false);
        assertEquals(" instanceof", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.lastChar = '-';
        consumer.addOp(">", false);
        assertEquals(" >", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.appendOp("*", true);
        assertEquals("*", consumer.getOutput());
    }

    @Test
    public void testAddNumber() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.addNumber(123.0);
        assertEquals("123", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.lastChar = '-';
        consumer.addNumber(-5.0);
        assertEquals(" -5", consumer.getOutput());

        consumer = new ConcreteCodeConsumer();
        consumer.addNumber(12000.0);
        assertTrue(consumer.getOutput().contains("E"));

        consumer = new ConcreteCodeConsumer();
        consumer.addNumber(123.456);
        assertEquals("123.456", consumer.getOutput());
    }

    @Test
    public void testIsNegativeZero() throws Throwable {
        assertTrue(CodeConsumer.isNegativeZero(-0.0));
        assertFalse(CodeConsumer.isNegativeZero(0.0));
        assertFalse(CodeConsumer.isNegativeZero(1.0));
    }
}