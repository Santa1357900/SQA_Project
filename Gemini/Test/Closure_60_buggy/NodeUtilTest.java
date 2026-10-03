package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class NodeUtilTest {

  @Test
  public void testGetStringValueDouble() throws Throwable {
    assertEquals("1", NodeUtil.getStringValue(1.0));
    assertEquals("1.5", NodeUtil.getStringValue(1.5));
    assertEquals("0", NodeUtil.getStringValue(0.0));
  }

  @Test
  public void testGetStringValueNode() throws Throwable {
    Node strNode = Node.newString("hello");
    assertEquals("hello", NodeUtil.getStringValue(strNode));

    Node nameNode = Node.newString(Token.NAME, "undefined");
    assertEquals("undefined", NodeUtil.getStringValue(nameNode));

    Node nameInfinity = Node.newString(Token.NAME, "Infinity");
    assertEquals("Infinity", NodeUtil.getStringValue(nameInfinity));

    Node nameNan = Node.newString(Token.NAME, "NaN");
    assertEquals("NaN", NodeUtil.getStringValue(nameNan));

    Node nameOther = Node.newString(Token.NAME, "other");
    assertNull(NodeUtil.getStringValue(nameOther));

    Node numNode = Node.newNumber(42.0);
    assertEquals("42", NodeUtil.getStringValue(numNode));

    Node falseNode = new Node(Token.FALSE);
    assertEquals("false", NodeUtil.getStringValue(falseNode));

    Node trueNode = new Node(Token.TRUE);
    assertEquals("true", NodeUtil.getStringValue(trueNode));

    Node nullNode = new Node(Token.NULL);
    assertEquals("null", NodeUtil.getStringValue(nullNode));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertEquals("undefined", NodeUtil.getStringValue(voidNode));

    Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
    assertEquals("false", NodeUtil.getStringValue(notNode));

    Node arrayLit = new Node(Token.ARRAYLIT, Node.newString("a"), Node.newString("b"));
    assertEquals("a,b", NodeUtil.getStringValue(arrayLit));

    Node objLit = new Node(Token.OBJECTLIT);
    assertEquals("[object Object]", NodeUtil.getStringValue(objLit));
  }

  @Test
  public void testGetNumberValue() throws Throwable {
    assertEquals(Double.valueOf(1.0), NodeUtil.getNumberValue(new Node(Token.TRUE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.FALSE)));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(new Node(Token.NULL)));
    assertEquals(Double.valueOf(5.0), NodeUtil.getNumberValue(Node.newNumber(5.0)));

    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(voidNode));

    Node nameUndef = Node.newString(Token.NAME, "undefined");
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(nameUndef));

    Node nameNan = Node.newString(Token.NAME, "NaN");
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getNumberValue(nameNan));

    Node nameInf = Node.newString(Token.NAME, "Infinity");
    assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), NodeUtil.getNumberValue(nameInf));

    Node nameOther = Node.newString(Token.NAME, "unknown");
    assertNull(NodeUtil.getNumberValue(nameOther));

    Node negInf = new Node(Token.NEG, Node.newString(Token.NAME, "Infinity"));
    assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), NodeUtil.getNumberValue(negInf));

    Node negOther = new Node(Token.NEG, Node.newNumber(1.0));
    assertNull(NodeUtil.getNumberValue(negOther));

    Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
    assertEquals(Double.valueOf(0.0), NodeUtil.getNumberValue(notNode));

    Node strNode = Node.newString("123");
    assertEquals(Double.valueOf(123.0), NodeUtil.getNumberValue(strNode));
  }

  @Test
  public void testGetStringNumberValue() throws Throwable {
    assertNull(NodeUtil.getStringNumberValue("abc\u000bdef"));
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue(""));
    assertEquals(Double.valueOf(0.0), NodeUtil.getStringNumberValue("   "));
    assertEquals(Double.valueOf(255.0), NodeUtil.getStringNumberValue("0xFF"));
    assertEquals(Double.valueOf(255.0), NodeUtil.getStringNumberValue("0Xff"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("0xZZZ"));
    assertNull(NodeUtil.getStringNumberValue("-0xFF"));
    assertNull(NodeUtil.getStringNumberValue("infinity"));
    assertNull(NodeUtil.getStringNumberValue("-infinity"));
    assertNull(NodeUtil.getStringNumberValue("+infinity"));
    assertEquals(Double.valueOf(Double.NaN), NodeUtil.getStringNumberValue("not-a-number"));
  }

  @Test
  public void testIsStrWhiteSpaceChar() throws Throwable {
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.isStrWhiteSpaceChar('\u000B'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar(' '));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\n'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\r'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\t'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u00A0'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u000C'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u2028'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\u2029'));
    assertEquals(TernaryValue.TRUE, NodeUtil.isStrWhiteSpaceChar('\uFEFF'));
    assertEquals(TernaryValue.FALSE, NodeUtil.isStrWhiteSpaceChar('a'));
  }

  @Test
  public void testGetPureBooleanValue() throws Throwable {
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(Node.newString("abc")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newString("")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(Node.newNumber(1.0)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newNumber(0.0)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(new Node(Token.NULL)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(new Node(Token.FALSE)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(new Node(Token.VOID, Node.newNumber(0))));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(new Node(Token.TRUE)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(new Node(Token.REGEXP)));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "undefined")));
    assertEquals(TernaryValue.FALSE, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "NaN")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "Infinity")));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getPureBooleanValue(Node.newString(Token.NAME, "unknownName")));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(new Node(Token.ARRAYLIT)));
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(new Node(Token.OBJECTLIT)));
  }

  @Test
  public void testGetImpureBooleanValue() throws Throwable {
    Node assignNode = new Node(Token.ASSIGN, Node.newString(Token.NAME, "x"), new Node(Token.TRUE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(assignNode));

    Node commaNode = new Node(Token.COMMA, Node.newNumber(1), new Node(Token.FALSE));
    assertEquals(TernaryValue.FALSE, NodeUtil.getImpureBooleanValue(commaNode));

    Node notNode = new Node(Token.NOT, new Node(Token.FALSE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(notNode));

    Node andNode = new Node(Token.AND, new Node(Token.TRUE), new Node(Token.FALSE));
    assertEquals(TernaryValue.FALSE, NodeUtil.getImpureBooleanValue(andNode));

    Node orNode = new Node(Token.OR, new Node(Token.FALSE), new Node(Token.TRUE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(orNode));

    Node hookSame = new Node(Token.HOOK, new Node(Token.TRUE), new Node(Token.TRUE), new Node(Token.TRUE));
    assertEquals(TernaryValue.TRUE, NodeUtil.getImpureBooleanValue(hookSame));

    Node hookDiff = new Node(Token.HOOK, new Node(Token.TRUE), new Node(Token.TRUE), new Node(Token.FALSE));
    assertEquals(TernaryValue.UNKNOWN, NodeUtil.getImpureBooleanValue(hookDiff));
  }

  @Test
  public void testIsImmutableValue() throws Throwable {
    assertTrue(NodeUtil.isImmutableValue(Node.newString("str")));
    assertTrue(NodeUtil.isImmutableValue(Node.newNumber(1.0)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NULL)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.FALSE)));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NOT, new Node(Token.TRUE))));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.VOID, Node.newNumber(0))));
    assertTrue(NodeUtil.isImmutableValue(new Node(Token.NEG, Node.newNumber(1))));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "undefined")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "Infinity")));
    assertTrue(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "NaN")));
    assertFalse(NodeUtil.isImmutableValue(Node.newString(Token.NAME, "variable")));
  }

  @Test
  public void testIsLiteralValue() throws Throwable {
    Node arrayLit = new Node(Token.ARRAYLIT, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isLiteralValue(arrayLit, false));

    Node arrayLitEmpty = new Node(Token.ARRAYLIT, new Node(Token.EMPTY));
    assertTrue(NodeUtil.isLiteralValue(arrayLitEmpty, false));

    Node arrayLitNonConst = new Node(Token.ARRAYLIT, Node.newString(Token.NAME, "a"));
    assertFalse(NodeUtil.isLiteralValue(arrayLitNonConst, false));

    Node regexpNode = new Node(Token.REGEXP, Node.newString("abc"));
    assertTrue(NodeUtil.isLiteralValue(regexpNode, false));

    Node regexpNonConst = new Node(Token.REGEXP, Node.newString(Token.NAME, "a"));
    assertFalse(NodeUtil.isLiteralValue(regexpNonConst, false));

    Node objLit = new Node(Token.OBJECTLIT, new Node(Token.STRING, "key", Node.newNumber(1)));
    assertTrue(NodeUtil.isLiteralValue(objLit, false));

    Node funcNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), new Node(Token.BLOCK));
    assertFalse(NodeUtil.isLiteralValue(funcNode, true));
  }

  @Test
  public void testIsValidDefineValue() throws Throwable {
    Set<String> defines = new HashSet<String>();
    defines.add("myDefine");

    assertTrue(NodeUtil.isValidDefineValue(Node.newString("test"), defines));
    assertTrue(NodeUtil.isValidDefineValue(Node.newNumber(1), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.TRUE), defines));
    assertTrue(NodeUtil.isValidDefineValue(new Node(Token.FALSE), defines));

    Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
    assertTrue(NodeUtil.isValidDefineValue(addNode, defines));

    Node notNode = new Node(Token.NOT, new Node(Token.TRUE));
    assertTrue(NodeUtil.isValidDefineValue(notNode, defines));

    Node nameValid = Node.newString(Token.NAME, "myDefine");
    assertTrue(NodeUtil.isValidDefineValue(nameValid, defines));

    Node nameInvalid = Node.newString(Token.NAME, "otherDefine");
    assertFalse(NodeUtil.isValidDefineValue(nameInvalid, defines));
  }

  @Test
  public void testIsEmptyBlock() throws Throwable {
    Node emptyBlock = new Node(Token.BLOCK);
    assertTrue(NodeUtil.isEmptyBlock(emptyBlock));

    Node nonBlock = new Node(Token.EMPTY);
    assertFalse(NodeUtil.isEmptyBlock(nonBlock));

    Node blockWithEmpty = new Node(Token.BLOCK, new Node(Token.EMPTY));
    assertTrue(NodeUtil.isEmptyBlock(blockWithEmpty));

    Node blockWithContent = new Node(Token.BLOCK, Node.newNumber(1));
    assertFalse(NodeUtil.isEmptyBlock(blockWithContent));
  }

  @Test
  public void testIsSimpleOperator() throws Throwable {
    assertTrue(NodeUtil.isSimpleOperatorType(Token.ADD));
    assertTrue(NodeUtil.isSimpleOperatorType(Token.SUB));
    assertFalse(NodeUtil.isSimpleOperatorType(Token.ASSIGN));

    Node addNode = new Node(Token.ADD);
    assertTrue(NodeUtil.isSimpleOperator(addNode));
  }

  @Test
  public void testNewExpr() throws Throwable {
    Node num = Node.newNumber(1);
    Node expr = NodeUtil.newExpr(num);
    assertEquals(Token.EXPR_RESULT, expr.getType());
    assertEquals(num, expr.getFirstChild());
  }

  @Test
  public void testPrecedence() throws Throwable {
    assertEquals(0, NodeUtil.precedence(Token.COMMA));
    assertEquals(1, NodeUtil.precedence(Token.ASSIGN));
    assertEquals(2, NodeUtil.precedence(Token.HOOK));
    assertEquals(3, NodeUtil.precedence(Token.OR));
    assertEquals(4, NodeUtil.precedence(Token.AND));
    assertEquals(5, NodeUtil.precedence(Token.BITOR));
    assertEquals(6, NodeUtil.precedence(Token.BITXOR));
    assertEquals(7, NodeUtil.precedence(Token.BITAND));
    assertEquals(8, NodeUtil.precedence(Token.EQ));
    assertEquals(9, NodeUtil.precedence(Token.LT));
    assertEquals(10, NodeUtil.precedence(Token.LSH));
    assertEquals(11, NodeUtil.precedence(Token.ADD));
    assertEquals(12, NodeUtil.precedence(Token.MUL));
    assertEquals(13, NodeUtil.precedence(Token.INC));
    assertEquals(15, NodeUtil.precedence(Token.CALL));
    assertEquals(15, NodeUtil.precedence(Token.NUMBER));
  }

  @Test(expected = Error.class)
  public void testPrecedenceError() throws Throwable {
    NodeUtil.precedence(-999);
  }

  @Test
  public void testNumericResult() throws Throwable {
    Node num = Node.newNumber(5);
    assertTrue(NodeUtil.isNumericResult(num));
    Node nameNan = Node.newString(Token.NAME, "NaN");
    assertTrue(NodeUtil.isNumericResult(nameNan));
    Node nameInf = Node.newString(Token.NAME, "Infinity");
    assertTrue(NodeUtil.isNumericResult(nameInf));
    Node str = Node.newString("abc");
    assertFalse(NodeUtil.isNumericResult(str));
  }

  @Test
  public void testBooleanResult() throws Throwable {
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.TRUE)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.FALSE)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.EQ)));
    assertTrue(NodeUtil.isBooleanResult(new Node(Token.NOT)));
    assertFalse(NodeUtil.isBooleanResult(Node.newNumber(1)));
  }

  @Test
  public void testIsUndefinedAndNull() throws Throwable {
    Node voidNode = new Node(Token.VOID, Node.newNumber(0));
    assertTrue(NodeUtil.isUndefined(voidNode));
    Node nameUndef = Node.newString(Token.NAME, "undefined");
    assertTrue(NodeUtil.isUndefined(nameUndef));
    assertFalse(NodeUtil.isUndefined(Node.newNumber(1)));

    Node nullNode = new Node(Token.NULL);
    assertTrue(NodeUtil.isNull(nullNode));
    assertFalse(NodeUtil.isNull(Node.newNumber(1)));

    assertTrue(NodeUtil.isNullOrUndefined(nullNode));
    assertTrue(NodeUtil.isNullOrUndefined(voidNode));
    assertFalse(NodeUtil.isNullOrUndefined(Node.newNumber(1)));
  }

  @Test
  public void testAssociativeAndCommutative() throws Throwable {
    assertTrue(NodeUtil.isAssociative(Token.MUL));
    assertFalse(NodeUtil.isAssociative(Token.ADD));

    assertTrue(NodeUtil.isCommutative(Token.MUL));
    assertFalse(NodeUtil.isCommutative(Token.ADD));
  }

  @Test
  public void testAssignmentOps() throws Throwable {
    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN)));
    assertTrue(NodeUtil.isAssignmentOp(new Node(Token.ASSIGN_ADD)));
    assertFalse(NodeUtil.isAssignmentOp(new Node(Token.ADD)));

    Node assignAdd = new Node(Token.ASSIGN_ADD);
    assertEquals(Token.ADD, NodeUtil.getFnFromAssignmentOp(assignAdd));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetFnFromAssignmentOpError() throws Throwable {
    NodeUtil.getFnFromAssignmentOp(new Node(Token.ADD));
  }

  @Test
  public void testNodeInspectors() throws Throwable {
    Node expr = new Node(Token.EXPR_RESULT);
    assertTrue(NodeUtil.isExpressionNode(expr));

    Node getProp = new Node(Token.GETPROP);
    Node getElem = new Node(Token.GETELEM);
    assertTrue(NodeUtil.isGet(getProp));
    assertTrue(NodeUtil.isGet(getElem));
    assertTrue(NodeUtil.isGetProp(getProp));

    Node name = new Node(Token.NAME);
    assertTrue(NodeUtil.isName(name));

    Node newToken = new Node(Token.NEW);
    assertTrue(NodeUtil.isNew(newToken));

    Node var = new Node(Token.VAR);
    assertTrue(NodeUtil.isVar(var));

    Node str = new Node(Token.STRING);
    assertTrue(NodeUtil.isString(str));

    Node assign = new Node(Token.ASSIGN);
    assertTrue(NodeUtil.isAssign(assign));

    Node func = new Node(Token.FUNCTION);
    assertTrue(NodeUtil.isFunction(func));

    Node thisNode = new Node(Token.THIS);
    assertTrue(NodeUtil.isThis(thisNode));

    Node arrayLit = new Node(Token.ARRAYLIT);
    assertTrue(NodeUtil.isArrayLiteral(arrayLit));
  }

  @Test
  public void testOpToStr() throws Throwable {
    assertEquals("+", NodeUtil.opToStr(Token.ADD));
    assertEquals("-", NodeUtil.opToStr(Token.SUB));
    assertEquals("*", NodeUtil.opToStr(Token.MUL));
    assertEquals("/", NodeUtil.opToStr(Token.DIV));
    assertNull(NodeUtil.opToStr(-999));

    assertEquals("+", NodeUtil.opToStrNoFail(Token.ADD));
  }

  @Test(expected = Error.class)
  public void testOpToStrNoFailError() throws Throwable {
    NodeUtil.opToStrNoFail(-999);
  }

  @Test
  public void testIsLatin() throws Throwable {
    assertTrue(NodeUtil.isLatin("abc123XYZ!"));
    assertFalse(NodeUtil.isLatin("abc\u0100xyz"));
  }

  @Test
  public void testIsValidPropertyName() throws Throwable {
    assertTrue(NodeUtil.isValidPropertyName("validProp"));
    assertFalse(NodeUtil.isValidPropertyName("if"));
    assertFalse(NodeUtil.isValidPropertyName("not\u0100valid"));
  }

  @Test
  public void testNewUndefinedNode() throws Throwable {
    Node undef = NodeUtil.newUndefinedNode(null);
    assertEquals(Token.VOID, undef.getType());

    Node src = Node.newNumber(1);
    Node undefWithSrc = NodeUtil.newUndefinedNode(src);
    assertEquals(Token.VOID, undefWithSrc.getType());
  }

  @Test
  public void testNewVarNode() throws Throwable {
    Node varNode = NodeUtil.newVarNode("x", Node.newNumber(1));
    assertEquals(Token.VAR, varNode.getType());
    assertEquals("x", varNode.getFirstChild().getString());

    Node varNoInit = NodeUtil.newVarNode("y", null);
    assertEquals(Token.VAR, varNoInit.getType());
  }

  @Test
  public void testMayHaveSideEffectsAndMutableState() throws Throwable {
    Node num = Node.newNumber(1);
    assertFalse(NodeUtil.mayHaveSideEffects(num));
    assertFalse(NodeUtil.mayEffectMutableState(num));

    Node throwNode = new Node(Token.THROW, Node.newString("err"));
    assertTrue(NodeUtil.mayHaveSideEffects(throwNode));
    assertTrue(NodeUtil.mayEffectMutableState(throwNode));
  }

  private static class DummyCodingConvention implements CodingConvention {
    public boolean isConstant(String variableName) {
      return "CONST_VAR".equals(variableName);
    }
    public boolean isConstantKey(String keyName) {
      return "CONST_KEY".equals(keyName);
    }
    public boolean isExported(String name) { return false; }
    public boolean isExported(String name, boolean local) { return false; }
    public boolean isGlobalStringPrototypeMethod(String name) { return false; }
    public String getAbstractMethodName() { return null; }
    public String getExportSymbolFunction() { return null; }
    public String getExportPropertyFunction() { return null; }
    public String getGlobalObject() { return null; }
    public boolean checkClosurePass() { return false; }
    public boolean isSingletonGetter(Node callNode, Node receiver, String methodName) { return false; }
    public void applySingletonGetterDecl(Scope.Var scopeVar, Map<String, String> stringMap, String string) {}
    public void definePackageObject(String name) {}
    public String getExportTestFunction() { return null; }
    public void applySubclassRelationship(FunctionType parent, FunctionType child, SubclassType type) {}
    public SubclassRelationship getClassesDefinedByCall(Node callNode) { return null; }
    public boolean isPropertyTestFunction(Node call) { return false; }
    public boolean isComposedMethod(Node call) { return false; }
    public String getSkipsJsdocParsingString() { return null; }
    public boolean dontRemoveClosureAsserts() { return false; }
    public String getAssignedValue(Node name) { return null; }
    public void checkAllVarsDefined() {}
    public boolean shouldClassifyMessageType() { return false; }
    public String extractMessageTypeId(Node node) { return null; }
  }

  @Test
  public void testQualifiedNameCreation() throws Throwable {
    CodingConvention convention = new DummyCodingConvention();
    Node qName = NodeUtil.newQualifiedNameNode(convention, "a.b.CONST_KEY", 1, 1);
    assertNotNull(qName);

    Node simpleName = NodeUtil.newQualifiedNameNode(convention, "simpleVar", 1, 1);
    assertNotNull(simpleName);

    Node qNameWithBasis = NodeUtil.newQualifiedNameNode(convention, "a.b", Node.newNumber(1), "orig");
    assertNotNull(qNameWithBasis);
  }

  @Test
  public void testGetRootOfQualifiedName() throws Throwable {
    Node name = Node.newString(Token.NAME, "root");
    Node prop = new Node(Token.GETPROP, name, Node.newString(Token.STRING, "prop"));
    assertEquals(name, NodeUtil.getRootOfQualifiedName(prop));
  }
}