package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class MethodCompilerPassTest extends TestCase {

    private static class DummySignatureStore implements MethodCompilerPass.SignatureStore {
        int resetCount = 0;
        int addCount = 0;
        int removeCount = 0;
        String lastName;
        Node lastNode;
        String lastSourceFile;

        public void reset() {
            resetCount++;
        }

        public void addSignature(String functionName, Node functionNode, String sourceFile) {
            addCount++;
            lastName = functionName;
            lastNode = functionNode;
            lastSourceFile = sourceFile;
        }

        public void removeSignature(String functionName) {
            removeCount++;
            lastName = functionName;
        }
    }

    private static class DummyCompiler extends Compiler {
        private boolean ideMode = false;

        public void setIdeMode(boolean ideMode) {
            this.ideMode = ideMode;
        }

        public boolean isIdeMode() {
            return ideMode;
        }
    }

    private static class TestMethodCompilerPass extends MethodCompilerPass {
        private final DummySignatureStore store = new DummySignatureStore();
        private final Callback actingCallback;

        public TestMethodCompilerPass(AbstractCompiler compiler, Callback actingCallback) {
            super(compiler);
            this.actingCallback = actingCallback;
        }

        Callback getActingCallback() {
            return actingCallback != null ? actingCallback : new AbstractPostOrderCallback() {
                public void visit(NodeTraversal t, Node n, Node parent) {
                }
            };
        }

        SignatureStore getSignatureStore() {
            return store;
        }
    }

    public void testProcessWithNullExterns() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        Node root = new Node(Token.BLOCK);
        
        pass.process(null, root);
        assertEquals(1, pass.store.resetCount);
    }

    public void testGetExternsGetPropValidFunction() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        
        // assign
        //   getprop
        //     name externs
        //     string setTimeout
        //   function
        Node nameNode = Node.newString(Token.NAME, "externs");
        Node stringNode = Node.newString(Token.STRING, "setTimeout");
        Node getProp = new Node(Token.GETPROP, nameNode, stringNode);
        Node funcNode = new Node(Token.FUNCTION);
        Node assign = new Node(Token.ASSIGN, getProp, funcNode);
        
        pass.process(assign, new Node(Token.BLOCK));
        assertEquals(1, pass.store.addCount);
        assertEquals("setTimeout", pass.store.lastName);
    }

    public void testGetExternsGetPropNonFunction() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        
        Node nameNode = Node.newString(Token.NAME, "externs");
        Node stringNode = Node.newString(Token.STRING, "someProp");
        Node getProp = new Node(Token.GETPROP, nameNode, stringNode);
        Node numberNode = Node.newNumber(1.0);
        Node assign = new Node(Token.ASSIGN, getProp, numberNode);
        
        pass.process(assign, new Node(Token.BLOCK));
        assertEquals(1, pass.store.removeCount);
        assertEquals("someProp", pass.store.lastName);
        assertTrue(pass.externMethodsWithoutSignatures.contains("someProp"));
    }

    public void testGetExternsObjectLit() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        
        Node keyString = Node.newString(Token.STRING, "method1");
        Node funcNode = new Node(Token.FUNCTION);
        keyString.addChildToBack(funcNode);
        
        Node objLit = new Node(Token.OBJECTLIT, keyString);
        
        pass.process(objLit, new Node(Token.BLOCK));
        assertEquals(1, pass.store.addCount);
        assertEquals("method1", pass.store.lastName);
    }

    public void testGatherSignaturesObjectLitNonFunction() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        
        Node keyString = Node.newString(Token.STRING, "prop1");
        Node numberNode = Node.newNumber(5.0);
        keyString.addChildToBack(numberNode);
        
        Node objLit = new Node(Token.OBJECTLIT, keyString);
        
        pass.process(null, objLit);
        assertTrue(pass.nonMethodProperties.contains("prop1"));
    }

    public void testGatherSignaturesPrototypeAssignment() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        
        // Foo.prototype.bar = function() {}
        Node nameFoo = Node.newString(Token.NAME, "Foo");
        Node strProto = Node.newString(Token.STRING, "prototype");
        Node getPropProto = new Node(Token.GETPROP, nameFoo, strProto);
        
        Node strBar = Node.newString(Token.STRING, "bar");
        Node getPropBar = new Node(Token.GETPROP, getPropProto, strBar);
        
        Node funcNode = new Node(Token.FUNCTION);
        Node assign = new Node(Token.ASSIGN, getPropBar, funcNode);
        
        pass.process(null, assign);
        assertEquals(1, pass.store.addCount);
        assertEquals("bar", pass.store.lastName);
    }

    public void testAddPossibleSignatureUndefinedVarIdeMode() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        compiler.setIdeMode(true);
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        
        // Foo.bar = undefinedName
        Node nameFoo = Node.newString(Token.NAME, "Foo");
        Node strBar = Node.newString(Token.STRING, "bar");
        Node getProp = new Node(Token.GETPROP, nameFoo, strBar);
        
        Node nameUndefined = Node.newString(Token.NAME, "nonExistentVar");
        Node assign = new Node(Token.ASSIGN, getProp, nameUndefined);
        
        pass.process(null, assign);
        assertTrue(pass.nonMethodProperties.contains("bar"));
    }

    public void testAddPossibleSignatureUndefinedVarNonIdeMode() throws Throwable {
        DummyCompiler compiler = new DummyCompiler();
        compiler.setIdeMode(false);
        TestMethodCompilerPass pass = new TestMethodCompilerPass(compiler, null);
        
        Node nameFoo = Node.newString(Token.NAME, "Foo");
        Node strBar = Node.newString(Token.STRING, "bar");
        Node getProp = new Node(Token.GETPROP, nameFoo, strBar);
        
        Node nameUndefined = Node.newString(Token.NAME, "nonExistentVar");
        Node assign = new Node(Token.ASSIGN, getProp, nameUndefined);
        
        boolean thrown = false;
        try {
            pass.process(null, assign);
        } catch (IllegalStateException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("VarCheck should have caught"));
        }
        assertTrue(thrown);
    }
}