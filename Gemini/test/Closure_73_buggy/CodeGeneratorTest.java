package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.common.base.Charsets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class CodeGeneratorTest {

    private static class TestCodeConsumer extends CodeConsumer {
        private final StringBuilder sb = new StringBuilder();
        private boolean continueProc = true;
        private boolean preserveBlocks = false;

        @Override
        void add(String str) {
            sb.append(str);
        }

        @Override
        void add(CharSequence str) {
            sb.append(str);
        }

        @Override
        void startSourceMapping(Node node) {}

        @Override
        void endSourceMapping(Node node) {}

        @Override
        boolean continueProcessing() {
            return continueProc;
        }

        @Override
        String getResult() {
            return sb.toString();
        }

        public void clear() {
            sb.setLength(0);
        }

        @Override
        boolean shouldPreserveExtraBlocks() {
            return preserveBlocks;
        }
    }

    @Test
    public void testConstructors() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen1 = new CodeGenerator(consumer);
        assertNotNull(gen1);

        CodeGenerator gen2 = new CodeGenerator(consumer, Charsets.UTF_8);
        assertNotNull(gen2);

        CodeGenerator gen3 = new CodeGenerator(consumer, Charsets.US_ASCII);
        assertNotNull(gen3);
    }

    @Test
    public void testTagAsStrict() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);
        gen.tagAsStrict();
        assertEquals("'use strict';", consumer.getResult());
    }

    @Test
    public void testSimpleNumberAndGetSimpleNumber() throws Throwable {
        assertTrue(CodeGenerator.isSimpleNumber("12345"));
        assertFalse(CodeGenerator.isSimpleNumber("123a5"));
        assertFalse(CodeGenerator.isSimpleNumber(""));

        assertEquals(123.0, CodeGenerator.getSimpleNumber("123"), 0.001);
        assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("123a")));
        assertTrue(Double.isNaN(CodeGenerator.getSimpleNumber("999999999999999999999")));
    }

    @Test
    public void testJsStringAndEscapes() throws Throwable {
        String res1 = CodeGenerator.jsString("hello \"world\"", null);
        assertTrue(res1.contains("hello"));

        String res2 = CodeGenerator.jsString("hello 'world'", null);
        assertTrue(res2.contains("world"));

        String reg = CodeGenerator.regexpEscape("abc/def");
        assertTrue(reg.contains("/"));

        String doubleQ = CodeGenerator.escapeToDoubleQuotedJsString("test");
        assertTrue(doubleQ.contains("test"));

        String ident = CodeGenerator.identifierEscape("myIdentifier");
        assertEquals("myIdentifier", ident);

        String nonLatinIdent = CodeGenerator.identifierEscape("a\u0100b");
        assertTrue(nonLatinIdent.contains("\\u"));
    }

    @Test
    public void testStrEscapeSpecialChars() throws Throwable {
        char[] chars = new char[] { '\0', '\n', '\r', '\t', '\\', '\"', '\'', '>', '<' };
        for (char c : chars) {
            String s = String.valueOf(c);
            String escaped = CodeGenerator.strEscape(s, '"', "\\\"", "\'", "\\\\", null);
            assertNotNull(escaped);
        }

        // Test special sequences for > and <
        String gtSeq = CodeGenerator.strEscape("-->", '"', "\\\"", "\'", "\\\\", null);
        assertTrue(gtSeq.contains("\\>"));

        String ltSeq1 = CodeGenerator.strEscape("</script>", '"', "\\\"", "\'", "\\\\", null);
        assertTrue(ltSeq1.contains("<\\"));

        String ltSeq2 = CodeGenerator.strEscape("<!--", '"', "\\\"", "\'", "\\\\", null);
        assertTrue(ltSeq2.contains("<\\"));
    }

    @Test
    public void testAddBinaryOp() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node left = Node.newNumber(1.0);
        Node right = Node.newNumber(2.0);
        Node addNode = new Node(Token.ADD, left, right);

        gen.add(addNode);
        assertEquals("1+2", consumer.getResult());
    }

    @Test
    public void testAddUnaryOp() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node child = Node.newNumber(5.0);
        Node notNode = new Node(Token.NOT, child);

        gen.add(notNode);
        assertEquals("!5", consumer.getResult());
    }

    @Test
    public void testAddNegOp() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node child = Node.newNumber(5.0);
        Node negNode = new Node(Token.NEG, child);

        gen.add(negNode);
        assertEquals("-5", consumer.getResult());

        consumer.clear();
        Node nameNode = Node.newString(Token.NAME, "x");
        Node negNameNode = new Node(Token.NEG, nameNode);
        gen.add(negNameNode);
        assertEquals("-x", consumer.getResult());
    }

    @Test
    public void testAddNumberAndStringAndKeywords() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        gen.add(Node.newNumber(10.5));
        assertEquals("10.5", consumer.getResult());

        consumer.clear();
        gen.add(new Node(Token.TRUE));
        assertEquals("true", consumer.getResult());

        consumer.clear();
        gen.add(new Node(Token.FALSE));
        assertEquals("false", consumer.getResult());

        consumer.clear();
        gen.add(new Node(Token.NULL));
        assertEquals("null", consumer.getResult());

        consumer.clear();
        gen.add(new Node(Token.THIS));
        assertEquals("this", consumer.getResult());

        consumer.clear();
        gen.add(new Node(Token.DEBUGGER));
        assertEquals("debugger;", consumer.getResult());
    }

    @Test
    public void testAddVar() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node name = Node.newString(Token.NAME, "a");
        Node varNode = new Node(Token.VAR, name);

        gen.add(varNode);
        assertEquals("var a", consumer.getResult());
    }

    @Test
    public void testAddIf() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node cond = new Node(Token.TRUE);
        Node thenBlock = new Node(Token.BLOCK, new Node(Token.EMPTY));
        Node ifNode = new Node(Token.IF, cond, thenBlock);

        gen.add(ifNode);
        assertTrue(consumer.getResult().contains("if"));
    }

    @Test
    public void testAddIfElse() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node cond = new Node(Token.TRUE);
        Node thenBlock = new Node(Token.BLOCK, new Node(Token.EMPTY));
        Node elseBlock = new Node(Token.BLOCK, new Node(Token.EMPTY));
        Node ifNode = new Node(Token.IF, cond, thenBlock, elseBlock);

        gen.add(ifNode);
        assertTrue(consumer.getResult().contains("else"));
    }

    @Test
    public void testAddWhile() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node cond = new Node(Token.TRUE);
        Node body = new Node(Token.BLOCK, new Node(Token.EMPTY));
        Node whileNode = new Node(Token.WHILE, cond, body);

        gen.add(whileNode);
        assertTrue(consumer.getResult().contains("while"));
    }

    @Test
    public void testAddDo() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node body = new Node(Token.BLOCK, new Node(Token.EMPTY));
        Node cond = new Node(Token.TRUE);
        Node doNode = new Node(Token.DO, body, cond);

        gen.add(doNode);
        assertTrue(consumer.getResult().contains("do"));
        assertTrue(consumer.getResult().contains("while"));
    }

    @Test
    public void testAddFor() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node init = new Node(Token.VAR, Node.newString(Token.NAME, "i"));
        Node cond = new Node(Token.TRUE);
        Node incr = new Node(Token.INC, Node.newString(Token.NAME, "i"));
        Node body = new Node(Token.BLOCK, new Node(Token.EMPTY));

        Node forNode = new Node(Token.FOR, init, cond, incr, body);

        gen.add(forNode);
        assertTrue(consumer.getResult().contains("for"));
    }

    @Test
    public void testAddForIn() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node lhs = Node.newString(Token.NAME, "x");
        Node obj = Node.newString(Token.NAME, "arr");
        Node body = new Node(Token.BLOCK, new Node(Token.EMPTY));

        Node forInNode = new Node(Token.FOR, lhs, obj, body);

        gen.add(forInNode);
        assertTrue(consumer.getResult().contains("in"));
    }

    @Test
    public void testAddSwitchCaseDefault() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node cond = Node.newNumber(1.0);
        Node caseNode = new Node(Token.CASE, Node.newNumber(1.0), new Node(Token.BLOCK, new Node(Token.EMPTY)));
        Node defaultNode = new Node(Token.DEFAULT, new Node(Token.BLOCK, new Node(Token.EMPTY)));
        Node switchNode = new Node(Token.SWITCH, cond, caseNode, defaultNode);

        gen.add(switchNode);
        assertTrue(consumer.getResult().contains("switch"));
        assertTrue(consumer.getResult().contains("case"));
        assertTrue(consumer.getResult().contains("default"));
    }

    @Test
    public void testAddTryCatchFinally() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node tryBlock = new Node(Token.BLOCK, new Node(Token.EMPTY));
        Node catchName = Node.newString(Token.NAME, "e");
        Node catchBody = new Node(Token.BLOCK, new Node(Token.EMPTY));
        Node catchNode = new Node(Token.CATCH, catchName, catchBody);

        tryBlock.addChildToBack(new Node(Token.BLOCK, catchNode));
        Node finallyBlock = new Node(Token.BLOCK, new Node(Token.EMPTY));

        Node tryStmt = new Node(Token.TRY, tryBlock, finallyBlock);

        gen.add(tryStmt);
        assertTrue(consumer.getResult().contains("try"));
        assertTrue(consumer.getResult().contains("finally"));
    }

    @Test
    public void testAddThrowAndReturn() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node throwNode = new Node(Token.THROW, Node.newString(Token.NAME, "err"));
        gen.add(throwNode);
        assertTrue(consumer.getResult().contains("throw"));

        consumer.clear();
        Node returnNode = new Node(Token.RETURN);
        gen.add(returnNode);
        assertTrue(consumer.getResult().contains("return"));
    }

    @Test
    public void testAddArrayLit() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1.0), Node.newNumber(2.0));
        gen.add(arrayLit);
        assertEquals("[1,2]", consumer.getResult());
    }

    @Test
    public void testAddObjectLit() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node key = Node.newString(Token.STRING, "a");
        key.putBooleanProp(Node.QUOTED_STRING, false);
        key.addChildToBack(Node.newNumber(1.0));

        Node objLit = new Node(Token.OBJECTLIT, key);
        gen.add(objLit);
        assertTrue(consumer.getResult().contains("a"));
        assertTrue(consumer.getResult().contains("1"));
    }

    @Test
    public void testAddHook() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node hook = new Node(Token.HOOK, new Node(Token.TRUE), Node.newNumber(1.0), Node.newNumber(2.0));
        gen.add(hook);
        assertTrue(consumer.getResult().contains("?"));
        assertTrue(consumer.getResult().contains(":"));
    }

    @Test
    public void testAddGetPropAndElem() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node getProp = new Node(Token.GETPROP, Node.newString(Token.NAME, "obj"), Node.newString(Token.STRING, "prop"));
        gen.add(getProp);
        assertEquals("obj.prop", consumer.getResult());

        consumer.clear();
        Node getElem = new Node(Token.GETELEM, Node.newString(Token.NAME, "obj"), Node.newString(Token.STRING, "prop"));
        gen.add(getElem);
        assertEquals("obj[\"prop\"]", consumer.getResult());
    }

    @Test
    public void testAddCall() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node call = new Node(Token.CALL, Node.newString(Token.NAME, "func"), Node.newNumber(1.0));
        gen.add(call);
        assertTrue(consumer.getResult().contains("func"));
        assertTrue(consumer.getResult().contains("1"));
    }

    @Test
    public void testAddNewLabelAndBreakContinue() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node labelName = Node.newString(Token.LABEL_NAME, "mylbl");
        Node label = new Node(Token.LABEL, labelName, new Node(Token.BLOCK, new Node(Token.EMPTY)));
        gen.add(label);
        assertTrue(consumer.getResult().contains("mylbl"));

        consumer.clear();
        Node breakNode = new Node(Token.BREAK, Node.newString(Token.LABEL_NAME, "mylbl"));
        gen.add(breakNode);
        assertTrue(consumer.getResult().contains("break"));

        consumer.clear();
        Node continueNode = new Node(Token.CONTINUE, Node.newString(Token.LABEL_NAME, "mylbl"));
        gen.add(continueNode);
        assertTrue(consumer.getResult().contains("continue"));
    }

    @Test
    public void testAddRegexp() throws Throwable {
        TestCodeConsumer consumer = new TestCodeConsumer();
        CodeGenerator gen = new CodeGenerator(consumer);

        Node regexp = new Node(Token.REGEXP, Node.newString(Token.STRING, "abc"), Node.newString(Token.STRING, "g"));
        gen.add(regexp);
        assertTrue(consumer.getResult().contains("abc"));
    }
}