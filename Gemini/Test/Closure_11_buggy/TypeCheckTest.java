package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.*;

public class TypeCheckTest {

  @Test
  public void testConstructorsAndGetters() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    TypeCheck typeCheck = new TypeCheck(
        compiler,
        null,
        registry,
        CheckLevel.WARNING,
        CheckLevel.OFF
    );

    assertNotNull(typeCheck);
    
    TypeCheck typeCheckWithScope = new TypeCheck(
        compiler,
        null,
        registry,
        null,
        null,
        CheckLevel.WARNING,
        CheckLevel.OFF
    );
    assertNotNull(typeCheckWithScope);

    TypeCheck chained = typeCheck.reportMissingProperties(false);
    assertNotNull(chained);
    
    double percent = typeCheck.getTypedPercent();
    assertEquals(0.0, percent, 0.001);
  }

  @Test
  public void testProcessValidations() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    TypeCheck typeCheck = new TypeCheck(
        compiler,
        null,
        registry,
        CheckLevel.WARNING,
        CheckLevel.OFF
    );

    Node externsRoot = new Node(Token.BLOCK);
    Node jsRoot = new Node(Token.BLOCK);
    Node parent = new Node(Token.BLOCK);
    parent.addChildToBack(externsRoot);
    parent.addChildToBack(jsRoot);

    boolean exceptionThrown = false;
    try {
      typeCheck.process(externsRoot, jsRoot);
    } catch (NullPointerException e) {
      exceptionThrown = true;
    } catch (IllegalStateException e) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }

  @Test
  public void testShouldTraverseWithFunction() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    TypeCheck typeCheck = new TypeCheck(
        compiler,
        null,
        registry,
        CheckLevel.WARNING,
        CheckLevel.OFF
    );

    NodeTraversal traversal = new NodeTraversal(compiler, typeCheck);
    Node funcNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "testFunc"));
    Node parentNode = new Node(Token.BLOCK);

    boolean result = typeCheck.shouldTraverse(traversal, funcNode, parentNode);
    assertTrue(result);
  }
}