package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.*;

public class AnalyzePrototypePropertiesTest {

  @Test
  public void testConstructorAndGetAllNameInfoWithoutModuleGraph() throws Throwable {
    Compiler compiler = new Compiler();
    AnalyzePrototypeProperties pass = new AnalyzePrototypeProperties(compiler, null, true, true);
    
    Collection<AnalyzePrototypeProperties.NameInfo> nameInfos = pass.getAllNameInfo();
    assertNotNull(nameInfos);
  }

  @Test
  public void testProcessWithEmptyNodes() throws Throwable {
    Compiler compiler = new Compiler();
    JSModuleGraph moduleGraph = null;
    AnalyzePrototypeProperties pass = new AnalyzePrototypeProperties(compiler, moduleGraph, false, false);
    
    Node externRoot = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    pass.process(externRoot, root);
    Collection<AnalyzePrototypeProperties.NameInfo> nameInfos = pass.getAllNameInfo();
    assertNotNull(nameInfos);
  }

  @Test
  public void testAssignmentPropertyRemove() throws Throwable {
    Node exprNode = new Node(Token.EXPR_RESULT, new Node(Token.ASSIGN, 
        new Node(Token.GETPROP, new Node(Token.GETPROP, IR.name("Foo"), IR.string("prototype")), IR.string("bar")), 
        IR.number(1)));
    Node parent = new Node(Token.BLOCK, exprNode);
    
    AnalyzePrototypeProperties.AssignmentProperty prop = 
        new AnalyzePrototypeProperties.AssignmentProperty(exprNode, null);
    
    assertEquals(exprNode.getFirstChild().getFirstChild(), prop.getPrototype());
    assertEquals(exprNode.getFirstChild().getLastChild(), prop.getValue());
    assertNull(prop.getModule());
    
    prop.remove();
    assertEquals(0, parent.getChildCount());
  }

  @Test
  public void testLiteralPropertyRemove() throws Throwable {
    Node key = IR.string("bar");
    Node value = IR.number(1);
    Node map = new Node(Token.OBJECTLIT, key, value);
    Node assign = new Node(Token.ASSIGN, new Node(Token.GETPROP, IR.name("Foo"), IR.string("prototype")), map);
    Node parent = new Node(Token.EXPR_RESULT, assign);
    
    AnalyzePrototypeProperties.LiteralProperty prop = 
        new AnalyzePrototypeProperties.LiteralProperty(key, value, map, assign, null);
    
    assertEquals(assign.getFirstChild(), prop.getPrototype());
    assertEquals(value, prop.getValue());
    assertNull(prop.getModule());
    
    prop.remove();
    assertFalse(map.hasChildren());
  }

  @Test
  public void testGlobalFunctionRemoveSingleChild() throws Throwable {
    Node nameNode = IR.name("foo");
    Node varNode = new Node(Token.VAR, nameNode);
    Node parent = new Node(Token.BLOCK, varNode);
    
    AnalyzePrototypeProperties.GlobalFunction func = 
        new AnalyzePrototypeProperties.GlobalFunction(nameNode, varNode, null, null);
    
    assertNull(func.getModule());
    assertEquals(varNode, func.getFunctionNode());
    
    func.remove();
    assertEquals(0, parent.getChildCount());
  }

  @Test
  public void testGlobalFunctionRemoveMultipleChildren() throws Throwable {
    Node nameNode1 = IR.name("foo");
    Node nameNode2 = IR.name("bar");
    Node varNode = new Node(Token.VAR, nameNode1, nameNode2);
    Node parent = new Node(Token.BLOCK, varNode);
    
    AnalyzePrototypeProperties.GlobalFunction func = 
        new AnalyzePrototypeProperties.GlobalFunction(nameNode1, varNode, null, null);
    
    func.remove();
    assertEquals(1, varNode.getChildCount());
    assertEquals(nameNode2, varNode.getFirstChild());
  }
}