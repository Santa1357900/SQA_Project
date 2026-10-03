package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.FunctionNode;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;

public class NodeUtilTest {

  @Test
  public void testGetBooleanValue() throws Throwable {
    Node strTrue = Node.newString("hello");
    assertTrue(NodeUtil.getBooleanValue(strTrue));

    Node strFalse = Node.newString("");
    assertFalse(NodeUtil.getBooleanValue(strFalse));

    Node numTrue = Node.newNumber(5.0);
    assertTrue(NodeUtil.getBooleanValue(numTrue));

    Node numFalse = Node.newNumber(0.0);
    assertFalse(NodeUtil.getBooleanValue(numFalse));

    Node tokenNull = new Node(Token.NULL);
    assertFalse(NodeUtil.getBooleanValue(tokenNull));

    Node tokenFalse = new Node(Token.FALSE);
    assertFalse(NodeUtil.getBooleanValue(tokenFalse));

    Node tokenVoid = new Node(Token.VOID);
    assertFalse(NodeUtil.getBooleanValue(tokenVoid));

    Node tokenTrue = new Node(Token.TRUE);
    assertTrue(NodeUtil.getBooleanValue(tokenTrue));

    Node tokenArrayLit = new Node(Token.ARRAYLIT);
    assertTrue(NodeUtil.getBooleanValue(tokenArrayLit));

    Node tokenObjectLit = new Node(Token.OBJECTLIT);
    assertTrue(NodeUtil.getBooleanValue(tokenObjectLit));

    Node tokenRegExp = new Node(Token.REGEXP);
    assertTrue(NodeUtil.getBooleanValue(tokenRegExp));

    Node nameUndefined = Node.newString(Token.NAME, "undefined");
    assertFalse(NodeUtil.getBooleanValue(nameUndefined));

    Node nameNaN = Node.newString(Token.NAME, "NaN");
    assertFalse(NodeUtil.getBooleanValue(nameNaN));

    Node nameInfinity = Node.newString(Token.NAME, "Infinity");
    assertTrue(NodeUtil.getBooleanValue(nameInfinity));

    Node nameOther = Node.newString(Token.NAME, "someIdentifier");
    boolean thrown = false;
    try {
      NodeUtil.getBooleanValue(nameOther);
    } catch (IllegalArgumentException e) {
      thrown = true;
    }
    assertTrue(thrown);
  }

  @Test
  public void testGetStringValue() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "abc");
    assertEquals("abc", NodeUtil.getStringValue(nameNode));

    Node stringNode = Node.newString("xyz");
    assertEquals("xyz", NodeUtil.getStringValue(stringNode));

    Node intNumNode = Node.newNumber(1.0);
    assertEquals("1", NodeUtil.getStringValue(intNumNode));

    Node doubleNumNode = Node.newNumber(1.5);
    assertEquals("1.5", NodeUtil.getStringValue(doubleNumNode));

    Node falseNode = new Node(Token.FALSE);
    assertEquals("false", NodeUtil.getStringValue(falseNode));

    Node trueNode = new Node(Token.TRUE);
    assertEquals("true", NodeUtil.getStringValue(trueNode));

    Node nullNode = new Node(Token.NULL);
    assertEquals("null", NodeUtil.getStringValue(nullNode));

    Node voidNode = new Node(Token.VOID);
    assertEquals("undefined", NodeUtil.getStringValue(voidNode));

    Node otherNode = new Node(Token.BLOCK);
    assertNull(NodeUtil.getStringValue(otherNode));
  }

  @Test
  public void testGetFunctionName() throws Throwable {
    Node nameChild = Node.newString(Token.NAME, "myFunc");
    Node funcNode = new Node(Token.FUNCTION, nameChild);

    Node parentName = Node.newString(Token.NAME, "varName");
    assertEquals("varName", NodeUtil.getFunctionName(funcNode, parentName));

    Node getPropNode = Node.newString(Token.GETPROP, "a.b");
    Node assignNode = new Node(Token.ASSIGN, getPropNode, funcNode);
    assertEquals("a.b", NodeUtil.getFunctionName(funcNode, assignNode));

    Node blockParent = new Node(Token.BLOCK);
    assertEquals("myFunc", NodeUtil.getFunctionName(funcNode, blockParent));

    Node emptyNameChild = Node.newString(Token.NAME, "");
    Node anonFuncNode = new Node(Token.FUNCTION, emptyNameChild);
    assertNull(NodeUtil.getFunctionName(anonFuncNode, blockParent));
  }

  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString("a")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(1.0)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.FALSE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.VOID)));

    Node negNode = new Node(Token.NEG, Node.newNumber(1.0));
    assertTrue(NodeUtil.isImmutableValue(negNode));

    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "Infinity")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "NaN")));

    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "other")));
  }

  @Test
  public void testIsLiteralValue() throws Throwable {
    Node arrLit = new Node(Token.ARRAYLIT, Node.newNumber(1.0));
    assertTrue(NodeUtil.isLiteralValue(arrLit));

    Node badArrLit = new Node(Token.ARRAYLIT, Node.newString(Token.NAME, "x"));
    assertFalse(NodeUtil.isLiteralValue(badArrLit));

    Node objLit = new Node(Token.OBJECTLIT);
    assertTrue(NodeUtil.isLiteralValue(objLit));

    Node regExp = new Node(Token.REGEXP);
    assertTrue(NodeUtil.isLiteralValue(regExp));

    assertTrue(NodeUtil.isLiteralValue(Node.newNumber(10.0)));
  }

  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("myDefine");

    assertTrue(NodeUtil.isValidDefineValue(Node.newString("str"), defines));
    assertTrue(NodeUtil.isValidDefineValue(Node.newNumber(1.0), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.TRUE), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.FALSE), defines));

    Node bitAnd = new Node(Token.BITAND, Node.newNumber(1.0));
    assertTrue(NodeUtil.isValidDefineValue(bitAnd, defines));

    Node validName = Node.newString(Token.NAME, "myDefine");
    assertTrue(NodeUtil.isValidDefineValue(validName, defines));

    Node invalidName = Node.newString(Token.NAME, "otherDefine");
    assertFalse(NodeUtil.isValidDefineValue(invalidName, defines));

    Node invalidNode = new Node(Token.BLOCK);
    assertFalse(NodeUtil.isValidDefineValue(invalidNode, defines));
  }

  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node emptyBlock = new Node(Token.BLOCK);
    assertTrue(NodeUtil.isEmptyBlock(emptyBlock));

    Node nonBlock = new Node(Token.EMPTY);
    assertFalse(NodeUtil.isEmptyBlock(nonBlock));

    Node blockWithEmpty = new Node(Token.BLOCK, new Node(Token.EMPTY));
    assertTrue(NodeUtil.isEmptyBlock(blockWithEmpty));

    Node blockWithStmt = new Node(Token.BLOCK, Node.newNumber(1.0));
    assertFalse(NodeUtil.isEmptyBlock(blockWithStmt));
  }

  @Test
  public void testIsSimpleOperatorType() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.MUL));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.DIV));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.FUNCTION));
  }

  @Test
  public void testNewExpr() throws Throwable {
    Node child = Node.newNumber(1.0);
    Node expr = NodeUtil.newExpr(child);
    assertEquals(Token.EXPR_RESULT, expr.getType());
    assertEquals(child, expr.getFirstChild());
  }

  @Test
  public void testMayEffectMutableStateAndSideEffects() throws Throwable {
    Node throwNode = new Node(Token.THROW, Node.newString("error"));
    assertTrue(NodeUtil.mayHaveSideEffects(throwNode));
    assertTrue(NodeUtil.mayEffectMutableState(throwNode));

    Node numNode = Node.newNumber(5.0);
    assertFalse(NodeUtil.mayHaveSideEffects(numNode));

    Node objLit = new Node(Token.OBJECTLIT);
    assertTrue(NodeUtil.mayEffectMutableState(objLit));
    assertFalse(NodeUtil.mayHaveSideEffects(objLit));

    Node varNode = new Node(Token.VAR, Node.newString(Token.NAME, "x"));
    assertFalse(NodeUtil.mayHaveSideEffects(varNode));
    
    Node varNodeWithInit = new Node(Token.VAR, Node.newString(Token.NAME, "x", -1, -1));
    varNodeWithInit.getFirstChild().addChildrenToBack(Node.newNumber(1.0));
    assertTrue(NodeUtil.mayHaveSideEffects(varNodeWithInit));

    Node anonFunc = new Node(Token.FUNCTION, Node.newString(Token.NAME, ""), new Node(Token.LP), new Node(Token.BLOCK));
    assertFalse(NodeUtil.mayHaveSideEffects(anonFunc));

    Node namedFunc = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), new Node(Token.BLOCK));
    assertTrue(NodeUtil.mayHaveSideEffects(namedFunc));
  }

  @Test
  public void testConstructorCallHasSideEffects() throws Throwable {
    Node callNode = new Node(Token.NEW, Node.newString(Token.NAME, "Array"));
    assertFalse(NodeUtil.constructorCallHasSideEffects(callNode));

    Node customCall = new Node(Token.NEW, Node.newString(Token.NAME, "CustomClass"));
    assertTrue(NodeUtil.constructorCallHasSideEffects(customCall));
  }

  @Test
  public void testFunctionCallHasSideEffects() throws Throwable {
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "String"));
    assertFalse(NodeUtil.functionCallHasSideEffects(callNode));

    Node mathCall = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(Token.NAME, "Math"), Node.newString(Token.STRING, "floor")));
    assertFalse(NodeUtil.functionCallHasSideEffects(mathCall));

    Node customCall = new Node(Token.CALL, Node.newString(Token.NAME, "customFunc"));
    assertTrue(NodeUtil.functionCallHasSideEffects(customCall));
  }

  @Test
  public void testNodeTypeMayHaveSideEffects() throws Throwable {
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(new Node(Token.CALL)));
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(new Node(Token.THROW)));
    
    Node nameNode = Node.newString(Token.NAME, "x");
    assertFalse(NodeUtil.nodeTypeMayHaveSideEffects(nameNode));
    nameNode.addChildrenToBack(Node.newNumber(1));
    assertTrue(NodeUtil.nodeTypeMayHaveSideEffects(nameNode));
    
    assertFalse(NodeUtil.nodeTypeMayHaveSideEffects(Node.newNumber(1)));
  }

  @Test
  public void testCanBeSideEffected() throws Throwable {
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "f"));
    assertTrue(NodeUtil.canBeSideEffected(callNode));

    Node nameNode = Node.newString(Token.NAME, "x");
    assertTrue(NodeUtil.canBeSideEffected(nameNode));

    Set<String> constants = new HashSet<String>();
    constants.add("x");
    assertFalse(NodeUtil.canBeSideEffected(nameNode, constants));
  }

  @Test
  public void testPrecedenceAndOperators() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(15, NodeUtil.precedence(Token.NUMBER));
    
    boolean errorThrown = false;
    try {
      NodeUtil.precedence(-999);
    } catch (Error e) {
      errorThrown = true;
    }
    assertTrue(errorThrown);

    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertFalse(NodeUtil.isAssociative(Token.ADD));

    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN)));
    assertFalse(NodeUtil.isAssignmentOp(Node.newNumber(1)));

    Node assignMod = new Node(Token.ASSIGN_MOD);
    assertEquals(Token.MOD, NodeUtil.getOpFromAssignmentOp(assignMod));

    boolean assignErr = false;
    try {
      NodeUtil.getOpFromAssignmentOp(Node.newNumber(1));
    } catch (IllegalArgumentException e) {
      assignErr = true;
    }
    assertTrue(assignErr);

    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertNull(NodeUtil.opToStr(-999));
    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
    
    boolean opErr = false;
    try {
      NodeUtil.opToStrNoFail(-999);
    } catch (Error e) {
      opErr = true;
    }
    assertTrue(opErr);
  }

  @Test
  public void testHelperNodeChecks() throws Throwable {
    Node exprResult = new Node(Token.EXPR_RESULT);
    assertTrue(NodeUtil.isExpressionNode(exprResult));

    Node getProp = new Node(Token.GETPROP);
    assertTrue(NodeUtil.isGet(getProp));
    assertTrue(NodeUtil.isGetProp(getProp));
    assertFalse(NodeUtil.isName(getProp));
    assertFalse(NodeUtil.isNew(getProp));
    assertFalse(NodeUtil.isVar(getProp));
    assertFalse(NodeUtil.isString(getProp));
    assertFalse(NodeUtil.isAssign(getProp));
    assertFalse(NodeUtil.isCall(getProp));
    assertFalse(NodeUtil.isFunction(getProp));
    assertFalse(NodeUtil.isThis(getProp));

    Node nameNode = Node.newString(Token.NAME, "a");
    assertTrue(NodeUtil.isName(nameNode));

    Node newNode = new Node(Token.NEW);
    assertTrue(NodeUtil.isNew(newNode));

    Node varNode = new Node(Token.VAR);
    assertTrue(NodeUtil.isVar(varNode));

    Node strNode = new Node(Token.STRING);
    assertTrue(NodeUtil.isString(strNode));

    Node assignNode = new Node(Token.ASSIGN);
    assertTrue(NodeUtil.isAssign(assignNode));

    Node callNode = new Node(Token.CALL);
    assertTrue(NodeUtil.isCall(callNode));

    Node funcNode = new Node(Token.FUNCTION);
    assertTrue(NodeUtil.isFunction(funcNode));

    Node thisNode = new Node(Token.THIS);
    assertTrue(NodeUtil.isThis(thisNode));

    Node varChild = Node.newString(Token.NAME, "x");
    varNode.addChildToBack(varChild);
    assertTrue(NodeUtil.isVarDeclaration(varChild));

    Node exprAssign = new Node(Token.EXPR_RESULT, new Node(Token.ASSIGN));
    assertTrue(NodeUtil.isExprAssign(exprAssign));

    Node exprCall = new Node(Token.EXPR_RESULT, new Node(Token.CALL));
    assertTrue(NodeUtil.isExprCall(exprCall));

    Node forInNode = new Node(Token.FOR, new Node(Token.NAME), new Node(Token.IN), new Node(Token.BLOCK));
    assertTrue(NodeUtil.isForIn(forInNode));

    assertTrue(NodeUtil.isLoopStructure(new Node(Token.FOR)));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.DO)));
    assertTrue(NodeUtil.isLoopStructure(new Node(Token.WHILE)));
    assertFalse(NodeUtil.isLoopStructure(new Node(Token.IF)));

    assertEquals(Token.BLOCK, NodeUtil.getLoopCodeBlock(new Node(Token.FOR, new Node(Token.NAME), new Node(Token.NAME), new Node(Token.BLOCK))).getType());
    assertEquals(Token.BLOCK, NodeUtil.getLoopCodeBlock(new Node(Token.DO, new Node(Token.BLOCK), new Node(Token.NAME))).getType());
    assertNull(NodeUtil.getLoopCodeBlock(new Node(Token.IF)));

    assertTrue(NodeUtil.isControlStructure(new Node(Token.IF)));
    assertFalse(NodeUtil.isControlStructure(new Node(Token.EXPR_RESULT)));

    Node ifNode = new Node(Token.IF, new Node(Token.NAME), new Node(Token.BLOCK));
    assertTrue(NodeUtil.isControlStructureCodeBlock(ifNode, ifNode.getLastChild()));

    Node scriptNode = new Node(Token.SCRIPT);
    assertTrue(NodeUtil.isStatementBlock(scriptNode));
  }

  @Test
  public void testGetAssignedValue() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    Node varNode = new Node(Token.VAR, nameNode);
    Node valNode = Node.newNumber(1.0);
    nameNode.addChildrenToBack(valNode);
    
    assertEquals(valNode, NodeUtil.getAssignedValue(nameNode));

    Node assignLhs = Node.newString(Token.NAME, "y");
    Node assignRhs = Node.newNumber(2.0);
    Node assignNode = new Node(Token.ASSIGN, assignLhs, assignRhs);
    
    assertEquals(assignRhs, NodeUtil.getAssignedValue(assignLhs));
    
    Node otherNode = Node.newString(Token.NAME, "z");
    new Node(Token.EXPR_RESULT, otherNode);
    assertNull(NodeUtil.getAssignedValue(otherNode));
  }

  @Test
  public void testGetConditionExpression() throws Throwable {
    Node cond = new Node(Token.NAME, "cond");
    Node block = new Node(Token.BLOCK);
    Node ifNode = new Node(Token.IF, cond, block);
    assertEquals(cond, NodeUtil.getConditionExpression(ifNode));

    Node doNode = new Node(Token.DO, block, cond);
    assertEquals(cond, NodeUtil.getConditionExpression(doNode));

    Node forNode3 = new Node(Token.FOR, new Node(Token.EMPTY), new Node(Token.EMPTY), new Node(Token.BLOCK));
    assertNull(NodeUtil.getConditionExpression(forNode3));

    Node forNode4 = new Node(Token.FOR, new Node(Token.EMPTY), cond, new Node(Token.EMPTY), new Node(Token.BLOCK));
    assertEquals(cond, NodeUtil.getConditionExpression(forNode4));

    Node caseNode = new Node(Token.CASE, cond);
    assertNull(NodeUtil.getConditionExpression(caseNode));
  }

  @Test
  public void testIsReferenceNameAndLabel() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "label");
    Node labelNode = new Node(Token.LABEL, nameNode, new Node(Token.BLOCK));
    assertTrue(NodeUtil.isLabelName(nameNode));
    assertFalse(NodeUtil.isReferenceName(nameNode));

    Node normalName = Node.newString(Token.NAME, "varName");
    assertTrue(NodeUtil.isReferenceName(normalName));
  }

  @Test
  public void testTryRemoveAndMergeBlock() throws Throwable {
    Node block = new Node(Token.BLOCK, new Node(Token.EMPTY));
    Node script = new Node(Token.SCRIPT, block);
    assertTrue(NodeUtil.tryMergeBlock(block));

    Node labelNode = new Node(Token.LABEL, Node.newString(Token.NAME, "l"), new Node(Token.BLOCK, Node.newNumber(1.0)));
    assertFalse(NodeUtil.tryMergeBlock(labelNode.getLastChild()));

    Node parentVar = new Node(Token.VAR, Node.newString(Token.NAME, "a"));
    NodeUtil.removeChild(parentVar, parentVar.getFirstChild());
  }

  @Test
  public void testFunctionCharacteristics() throws Throwable {
    Node fnName = Node.newString(Token.NAME, "f");
    Node fnLp = new Node(Token.LP);
    Node fnBody = new Node(Token.BLOCK);
    Node fnNode = new Node(Token.FUNCTION, fnName, fnLp, fnBody);

    assertTrue(NodeUtil.isFunctionDeclaration(fnNode));
    assertTrue(NodeUtil.isHoistedFunctionDeclaration(new Node(Token.SCRIPT, fnNode)));
    assertFalse(NodeUtil.isAnonymousFunction(fnNode));
    assertEquals(fnBody, NodeUtil.getFunctionBody(fnNode));
    assertEquals(fnLp, NodeUtil.getFnParameters(fnNode));
  }

  @Test
  public void testObjectCallMethods() throws Throwable {
    Node callCall = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(Token.NAME, "x"), Node.newString(Token.STRING, "call")));
    assertTrue(NodeUtil.isFunctionObjectCall(callCall));
    assertTrue(NodeUtil.isSimpleFunctionObjectCall(callCall));

    Node callApply = new Node(Token.CALL, new Node(Token.GETPROP, Node.newString(Token.NAME, "x"), Node.newString(Token.STRING, "apply")));
    assertTrue(NodeUtil.isFunctionObjectApply(callApply));
  }

  @Test
  public void testIsLhsAndObjectLitKey() throws Throwable {
    Node assign = new Node(Token.ASSIGN, Node.newString(Token.NAME, "x"), Node.newNumber(1));
    assertTrue(NodeUtil.isLhs(assign.getFirstChild(), assign));

    Node keyNode = Node.newString("key");
    Node objLit = new Node(Token.OBJECTLIT, keyNode, Node.newNumber(1));
    assertTrue(NodeUtil.isObjectLitKey(keyNode, objLit));
  }

  @Test
  public void testRedeclareVarsAndQualifiedNames() throws Throwable {
    Node branch = new Node(Token.BLOCK, new Node(Token.VAR, Node.newString(Token.NAME, "a")));
    Node script = new Node(Token.SCRIPT, branch);
    NodeUtil.redeclareVarsInsideBranch(branch);

    Node qNode = NodeUtil.newQualifiedNameNode("foo.bar", 1, 1);
    assertEquals(Token.GETPROP, qNode.getType());

    Node basis = Node.newNumber(1);
    Node qNode2 = NodeUtil.newQualifiedNameNode("foo.bar", basis, "orig");
    assertNotNull(qNode2);

    Node nameN = NodeUtil.newName("abc", basis, "orig2");
    assertNotNull(nameN);
  }

  @Test
  public void testLatinAndValidPropertyName() throws Throwable {
    assertTrue(NodeUtil.isLatin("abcABC123"));
    assertFalse(NodeUtil.isLatin("\u0100"));

    assertTrue(NodeUtil.isValidPropertyName("validProp"));
    assertFalse(NodeUtil.isValidPropertyName("if"));
  }

  @Test
  public void testPrototypeHelpers() throws Throwable {
    Node expr = NodeUtil.newExpr(new Node(Token.ASSIGN, NodeUtil.newQualifiedNameNode("A.prototype.b", 1, 1), Node.newNumber(1)));
    assertTrue(NodeUtil.isPrototypePropertyDeclaration(expr));

    Node qName = NodeUtil.newQualifiedNameNode("A.prototype.b", 1, 1);
    assertTrue(NodeUtil.isPrototypeProperty(qName));
    assertNotNull(NodeUtil.getPrototypeClassName(qName));
    assertEquals("b", NodeUtil.getPrototypePropertyName(qName));
  }

  @Test
  public void testMiscellaneousNodeUtilities() throws Throwable {
    assertNotNull(NodeUtil.newUndefinedNode());
    assertNotNull(NodeUtil.newVarNode("x", Node.newNumber(1)));
    
    Node root = Node.newString(Token.NAME, "x");
    assertFalse(NodeUtil.containsType(root, Token.FUNCTION));
    assertTrue(NodeUtil.isNodeTypeReferenced(root, Token.NAME));
    assertEquals(1, NodeUtil.getNodeTypeReferenceCount(root, Token.NAME));
    assertTrue(NodeUtil.isNameReferenced(root, "x"));
    assertEquals(1, NodeUtil.getNameReferenceCount(root, "x"));

    Node tryNode = new Node(Token.TRY, new Node(Token.BLOCK), new Node(Token.BLOCK, new Node(Token.CATCH)), new Node(Token.BLOCK));
    assertTrue(NodeUtil.hasFinally(tryNode));
    assertNotNull(NodeUtil.getCatchBlock(tryNode));
    assertTrue(NodeUtil.hasCatchHandler(tryNode.getChildCount() > 1 ? tryNode.getFirstChild().getNext() : tryNode));

    Node constName = Node.newString(Token.NAME, "C");
    constName.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    assertTrue(NodeUtil.isConstantName(constName));

    assertNull(NodeUtil.getInfoForNameNode(null));
    assertNull(NodeUtil.getSourceName(root));

    List<Node> params = new ArrayList<Node>();
    params.add(Node.newString(Token.NAME, "p1"));
    FunctionNode fnNode = NodeUtil.newFunctionNode("fn", params, new Node(Token.BLOCK), 1, 1);
    assertNotNull(fnNode);
  }
}