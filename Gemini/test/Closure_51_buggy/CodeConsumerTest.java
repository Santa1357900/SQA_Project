package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;

public class CodeConsumerTest {

    private static class TestCodeConsumer extends CodeConsumer {
        private StringBuilder sb = new StringBuilder();
        private char lastChar = '\0';

        public TestCodeConsumer() {
        }

        public TestCodeConsumer(char initialLastChar) {
            this.lastChar = initialLastChar;
        }

        @Override
        char getLastChar() {
            if (sb.length() > 0) {
                return sb.charAt(sb.length() - 1);
            }
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
        TestCodeConsumer consumer = new TestCodeConsumer();
        assertFalse(consumer.statementNeedsEnded);
        assertFalse(consumer.statementStarted);
        assertFalse(consumer.sawFunction);
        assertTrue(consumer.continueProcessing());
        assertFalse(consumer.shouldPreserveExtraBlocks());
        assertTrue(consumer.breakAfterBlockFor(null, true));
        assertFalse(consumer.breakAfterBlockFor(null, false));
    }

    @Test
    public void testSourceMappingAndLines() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
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
        TestCodeConsumer consumer = new TestCodeConsumer();
        consumer.appendBlockStart();
        consumer.appendBlockEnd();
        assertEquals("{}", consumer.getOutput());
    }

    @Test
    public void testBeginAndEndBlock() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        consumer.statementNeedsEnded = true;
        consumer.beginBlock();
        assertEquals("{", consumer.getOutput());
        assertFalse(consumer.statementNeedsEnded);

        consumer.endBlock(true);
        assertEquals("{}", consumer.getOutput());
        assertFalse(consumer.statementNeedsEnded);

        consumer.endBlock(false);
        assertEquals("{}{}", consumer.getOutput());
    }

    @Test
    public void testListSeparator() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        consumer.listSeparator();
        assertEquals(",", consumer.getOutput());
    }

    @Test
    public void testEndStatement() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        consumer.endStatement(false);
        assertFalse(consumer.statementNeedsEnded);

        consumer.statementStarted = true;
        consumer.endStatement(false);
        assertTrue(consumer.statementNeedsEnded);

        consumer.endStatement(true);
        assertEquals(";", consumer.getOutput());
        assertFalse(consumer.statementNeedsEnded);
    }

    @Test
    public void testMaybeEndStatement() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        consumer.statementNeedsEnded = true;
        consumer.maybeEndStatement();
        assertEquals(";", consumer.getOutput());
        assertTrue(consumer.statementStarted);
        assertFalse(consumer.statementNeedsEnded);
    }

    @Test
    public void testEndFunction() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        consumer.endFunction(false);
        assertTrue(consumer.sawFunction);

        consumer.endFunction(true);
        assertTrue(consumer.sawFunction);
    }

    @Test
    public void testCaseBody() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        consumer.beginCaseBody();
        consumer.endCaseBody();
        assertEquals(":", consumer.getOutput());
    }

    @Test
    public void testAddIdentifierAndAdd() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer('a');
        consumer.addIdentifier("foo");
        assertEquals("foo", consumer.getOutput());

        // Test adding empty string
        consumer.add("");
        assertEquals("foo", consumer.getOutput());

        // Test adding word char when last char is word char (should add space)
        consumer.add("bar");
        assertEquals("foo bar", consumer.getOutput());

        // Test adding non-word char when last char is word char
        consumer.add("(");
        assertEquals("foo bar(", consumer.getOutput());
    }

    @Test
    public void testAddOp() throws Throwable {
        // Test '+' and '-' with prev == first
        TestCodeConsumer consumer = new TestCodeConsumer('+');
        consumer.addOp("+", true);
        assertEquals(" +", consumer.getOutput());

        TestCodeConsumer consumerMin = new TestCodeConsumer('-');
        consumerMin.addOp("-", true);
        assertEquals(" -", consumerMin.getOutput());

        // Test Letter and word char prev
        TestCodeConsumer consumerLet = new TestCodeConsumer('a');
        consumerLet.addOp("typeof", true);
        assertEquals(" typeof", consumerLet.getOutput());

        // Test '-' and '>'
        TestCodeConsumer consumerArrow = new TestCodeConsumer('-');
        consumerArrow.addOp(">", false);
        assertEquals(" >", consumerArrow.getOutput());
    }

    @Test
    public void testAddNumber() throws Throwable {
        // Negative number with negative prev
        TestCodeConsumer consumer = new TestCodeConsumer('-');
        consumer.addNumber(-4.0);
        assertEquals(" -4", consumer.getOutput());

        // Integer value
        TestCodeConsumer consumerInt = new TestCodeConsumer();
        consumerInt.addNumber(42.0);
        assertEquals("42", consumerInt.getOutput());

        // Mantissa and exp case
        TestCodeConsumer consumerExp = new TestCodeConsumer();
        consumerExp.addNumber(12000.0);
        assertEquals("12E3", consumerExp.getOutput());

        // Non-integer value
        TestCodeConsumer consumerDouble = new TestCodeConsumer();
        consumerDouble.addNumber(3.14);
        assertEquals("3.14", consumerDouble.getOutput());
    }

    @Test
    public void testIsWordChar() throws Throwable {
        assertTrue(CodeConsumer.isWordChar('_'));
        assertTrue(CodeConsumer.isWordChar('$'));
        assertTrue(CodeConsumer.isWordChar('a'));
        assertTrue(CodeConsumer.isWordChar('Z'));
        assertTrue(CodeConsumer.isWordChar('5'));
        assertFalse(CodeConsumer.isWordChar(' '));
        assertFalse(CodeConsumer.isWordChar(';'));
        assertFalse(CodeConsumer.isWordChar('+'));
    }
}