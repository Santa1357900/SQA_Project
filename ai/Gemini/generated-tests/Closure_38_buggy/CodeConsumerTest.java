package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;

public class CodeConsumerTest {

    private static class ConcreteCodeConsumer extends CodeConsumer {
        private StringBuilder sb = new StringBuilder();
        private char lastChar = '\0';

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
        Node node = IR.name("test");
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
    public void testBlockMethods() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.appendBlockStart();
        assertEquals("{", consumer.getOutput());

        consumer.appendBlockEnd();
        assertEquals("{}", consumer.getOutput());

        consumer.beginBlock();
        assertEquals("{}{;", consumer.getOutput());

        consumer.endBlock(true);
        assertEquals("{}{;}{}", consumer.getOutput());

        consumer.endBlock(false);
        assertEquals("{}{;}{}{}", consumer.getOutput());
    }

    @Test
    public void testListSeparator() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.listSeparator();
        assertEquals(",", consumer.getOutput());
    }

    @Test
    public void testEndStatementVariations() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.endStatement(false);
        assertFalse(consumer.statementNeedsEnded);

        consumer.statementStarted = true;
        consumer.endStatement(false);
        assertTrue(consumer.statementNeedsEnded);

        consumer.endStatement(true);
        assertFalse(consumer.statementNeedsEnded);
        assertTrue(consumer.getOutput().contains(";"));
    }

    @Test
    public void testMaybeEndStatement() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.statementNeedsEnded = true;
        consumer.maybeEndStatement();
        assertFalse(consumer.statementNeedsEnded);
        assertTrue(consumer.statementStarted);
        assertEquals(";", consumer.getOutput());
    }

    @Test
    public void testEndFunction() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.endFunction(false);
        assertTrue(consumer.sawFunction);

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
    public void testAddIdentifierAndAddMethods() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.addIdentifier("foo");
        assertEquals("foo", consumer.getOutput());

        consumer.add("");
        assertEquals("foo", consumer.getOutput());

        consumer.add("bar");
        assertEquals("foobar", consumer.getOutput());

        consumer.append("return");
        consumer.add("foo");
        assertEquals("foobarreturn foo", consumer.getOutput());

        consumer.append("/");
        consumer.add("/");
        assertEquals("foobarreturn foo/ /", consumer.getOutput());
    }

    @Test
    public void testAddOpVariations() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.addOp("+", false);
        assertEquals("+", consumer.getOutput());

        consumer.append("+");
        consumer.addOp("+", false);
        assertEquals("++ +", consumer.getOutput());

        consumer.append("typeof");
        consumer.addOp("instanceof", true);
        assertEquals("++ +typeof instanceof", consumer.getOutput());

        consumer.append("-");
        consumer.addOp(">", false);
        assertEquals("++ +typeof instanceof- >", consumer.getOutput());
    }

    @Test
    public void testAddNumber() throws Throwable {
        ConcreteCodeConsumer consumer = new ConcreteCodeConsumer();
        consumer.addNumber(0.0);
        assertEquals("0", consumer.getOutput());

        consumer.addNumber(-0.0);
        assertEquals("00", consumer.getOutput());

        consumer.append("-");
        consumer.addNumber(-5.0);
        assertEquals("00- -5", consumer.getOutput());

        ConcreteCodeConsumer consumer3 = new ConcreteCodeConsumer();
        consumer3.addNumber(1234500.0);
        assertTrue(consumer3.getOutput().contains("E") || consumer3.getOutput().equals("1234500"));

        ConcreteCodeConsumer consumer4 = new ConcreteCodeConsumer();
        consumer4.addNumber(3.14159);
        assertEquals("3.14159", consumer4.getOutput());
    }

    @Test
    public void testIsNegativeZero() throws Throwable {
        assertTrue(CodeConsumer.isNegativeZero(-0.0));
        assertFalse(CodeConsumer.isNegativeZero(0.0));
        assertFalse(CodeConsumer.isNegativeZero(1.0));
    }

    @Test
    public void testIsWordChar() throws Throwable {
        assertTrue(CodeConsumer.isWordChar('_'));
        assertTrue(CodeConsumer.isWordChar('$'));
        assertTrue(CodeConsumer.isWordChar('a'));
        assertTrue(CodeConsumer.isWordChar('Z'));
        assertTrue(CodeConsumer.isWordChar('5'));
        assertFalse(CodeConsumer.isWordChar('+'));
        assertFalse(CodeConsumer.isWordChar(' '));
    }
}