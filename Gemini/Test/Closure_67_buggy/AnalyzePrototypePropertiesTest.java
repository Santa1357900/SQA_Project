package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.NameInfo;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.AssignmentProperty;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.LiteralProperty;
import com.google.javascript.jscomp.AnalyzePrototypeProperties.GlobalFunction;

import junit.framework.TestCase;

import java.util.Collection;

public class AnalyzePrototypePropertiesTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testCreationWithoutModuleGraph() throws Throwable {
    AnalyzePrototypeProperties analyzer = new AnalyzePrototypeProperties(compiler, null, true, false);
    Collection<NameInfo> nameInfos = analyzer.getAllNameInfo();
    assertNotNull(nameInfos);
  }

  public void testCreationWithModuleGraph() throws Throwable {
    JSModule m1 = new JSModule("m1");
    JSModule m2 = new JSModule("m2");
    JSModuleGraph moduleGraph = new JSModuleGraph(new JSModule[] { m1, m2 });

    AnalyzePrototypeProperties analyzer = new AnalyzePrototypeProperties(compiler, moduleGraph, false, true);
    Collection<NameInfo> nameInfos = analyzer.getAllNameInfo();
    assertNotNull(nameInfos);
  }

  public void testProcessEmptyAST() throws Throwable {
    AnalyzePrototypeProperties analyzer = new AnalyzePrototypeProperties(compiler, null, true, false);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    analyzer.process(externs, root);
    Collection<NameInfo> nameInfos = analyzer.getAllNameInfo();
    assertTrue(nameInfos.isEmpty());
  }

  public void testAssignmentPropertyOperations() throws Throwable {
    Node expr = IR.exprResult(
        IR.assign(
            IR.getprop(
                IR.getprop(IR.name("Foo"), IR.string("prototype")),
                IR.string("bar")
            ),
            IR.function(IR.name(""), IR.paramList(), IR.block())
        )
    );
    JSModule module = new JSModule("m1");
    AssignmentProperty prop = new AssignmentProperty(expr, module);

    assertNotNull(prop.getPrototype());
    assertNotNull(prop.getValue());
    assertEquals(module, prop.getModule());

    Node parent = new Node(Token.BLOCK);
    parent.addChildToBack(expr);
    prop.remove();
    assertFalse(parent.hasChildren());
  }

  public void testLiteralPropertyOperations() throws Throwable {
    Node key = IR.string("bar");
    Node value = IR.function(IR.name(""), IR.paramList(), IR.block());
    key.addChildToBack(value);
    Node map = IR.objectlit(key);
    Node assign = IR.assign(
        IR.getprop(IR.name("Foo"), IR.string("prototype")),
        map
    );
    JSModule module = new JSModule("m1");
    LiteralProperty prop = new LiteralProperty(key, value, map, assign, module);

    assertNotNull(prop.getPrototype());
    assertEquals(value, prop.getValue());
    assertEquals(module, prop.getModule());

    prop.remove();
    assertFalse(map.hasChildren());
  }

  public void testGlobalFunctionOperations() throws Throwable {
    Node nameNode = IR.name("myFunc");
    Node funcNode = IR.function(nameNode, IR.paramList(), IR.block());
    Node varNode = new Node(Token.VAR, funcNode);
    JSModule module = new JSModule("m1");
    
    GlobalFunction globalFunc = new GlobalFunction(nameNode, varNode, null, module);
    assertEquals(module, globalFunc.getModule());
    assertNotNull(globalFunc.getFunctionNode());

    globalFunc.remove();
    assertFalse(varNode.hasChildren());
  }

  public void testNameInfoBehavior() throws Throwable {
    AnalyzePrototypeProperties analyzer = new AnalyzePrototypeProperties(compiler, null, true, false);
    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    analyzer.process(externs, root);
    
    Collection<NameInfo> nameInfos = analyzer.getAllNameInfo();
    for (NameInfo info : nameInfos) {
      assertNotNull(info.toString());
      assertFalse(info.isReferenced());
      assertFalse(info.readsClosureVariables());
      assertNull(info.getDeepestCommonModuleRef());
      assertNotNull(info.getDeclarations());
    }
  }
}