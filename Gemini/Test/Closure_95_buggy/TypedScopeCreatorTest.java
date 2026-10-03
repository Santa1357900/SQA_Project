package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.SimpleErrorReporter;

public class TypedScopeCreatorTest {

    @Test
    public void testDelegateProxySuffix() throws Throwable {
        assertEquals("(Proxy)", TypedScopeCreator.DELEGATE_PROXY_SUFFIX);
    }

    @Test
    public void testMalformedTypedefDiagnosticType() throws Throwable {
        assertNotNull(TypedScopeCreator.MALFORMED_TYPEDEF);
        assertEquals("JSC_MALFORMED_TYPEDEF", TypedScopeCreator.MALFORMED_TYPEDEF.id);
    }

    @Test
    public void testEnumInitializerDiagnosticType() throws Throwable {
        assertNotNull(TypedScopeCreator.ENUM_INITIALIZER);
        assertEquals("JSC_ENUM_INITIALIZER_NOT_ENUM", TypedScopeCreator.ENUM_INITIALIZER.id);
    }

    @Test
    public void testConstructorExpectedDiagnosticType() throws Throwable {
        assertNotNull(TypedScopeCreator.CONSTRUCTOR_EXPECTED);
        assertEquals("JSC_REFLECT_CONSTRUCTOR_EXPECTED", TypedScopeCreator.CONSTRUCTOR_EXPECTED.id);
    }

    @Test
    public void testCreateInitialScopeAndConstructors() throws Throwable {
        Compiler compiler = new Compiler();
        DefaultCodingConvention convention = new DefaultCodingConvention();
        TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler, convention);

        Node root = new Node(Token.SCRIPT);
        Scope scope = scopeCreator.createInitialScope(root);

        assertNotNull(scope);
        assertTrue(scope.isGlobal());
        assertNotNull(scope.getVar("Object"));
        assertNotNull(scope.getVar("Array"));
        assertNotNull(scope.getVar("Date"));
        assertNotNull(scope.getVar("undefined"));
        assertNotNull(scope.getVar("goog.typedef"));
        assertNotNull(scope.getVar("ActiveXObject"));
    }

    @Test
    public void testCreateScopeGlobalAndLocal() throws Throwable {
        Compiler compiler = new Compiler();
        TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);

        Node root = new Node(Token.SCRIPT);
        Scope globalScope = scopeCreator.createScope(root, null);
        assertNotNull(globalScope);

        Node funcNode = new Node(Token.FUNCTION, new Node(Token.NAME, "f"), new Node(Token.LP), new Node(Token.BLOCK));
        Scope localScope = scopeCreator.createScope(funcNode, globalScope);
        assertNotNull(localScope);
        assertTrue(localScope.isLocal());
    }

    @Test
    public void testLiteralTypesAttachment() throws Throwable {
        Compiler compiler = new Compiler();
        TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
        Node root = new Node(Token.SCRIPT);
        Scope scope = scopeCreator.createInitialScope(root);

        Node nullNode = new Node(Token.NULL);
        Node voidNode = new Node(Token.VOID);
        Node strNode = new Node(Token.STRING, "test");
        Node numNode = new Node(Token.NUMBER, 123.0);
        Node trueNode = new Node(Token.TRUE);
        Node falseNode = new Node(Token.FALSE);
        Node objLitNode = new Node(Token.OBJECTLIT);

        root.addChildToBack(nullNode);
        root.addChildToBack(voidNode);
        root.addChildToBack(strNode);
        root.addChildToBack(numNode);
        root.addChildToBack(trueNode);
        root.addChildToBack(falseNode);
        root.addChildToBack(objLitNode);

        scopeCreator.createScope(root, null);

        assertNotNull(nullNode.getJSType());
        assertNotNull(voidNode.getJSType());
        assertNotNull(strNode.getJSType());
        assertNotNull(numNode.getJSType());
        assertNotNull(trueNode.getJSType());
        assertNotNull(falseNode.getJSType());
        assertNotNull(objLitNode.getJSType());
    }

    @Test
    public void testCatchBlockDefinition() throws Throwable {
        Compiler compiler = new Compiler();
        TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);

        Node root = new Node(Token.SCRIPT);
        Scope globalScope = scopeCreator.createScope(root, null);

        Node catchName = new Node(Token.NAME, "e");
        Node catchNode = new Node(Token.CATCH, catchName, new Node(Token.BLOCK));
        Scope localScope = scopeCreator.createScope(catchNode, globalScope);

        assertNotNull(localScope);
        assertNotNull(localScope.getVar("e"));
    }
}