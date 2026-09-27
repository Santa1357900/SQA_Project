package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class PeepholeFoldConstantsTest extends TestCase {

    private PeepholeFoldConstants folder;
    private AbstractCompiler compiler;

    protected void setUp() throws Exception {
        super.setUp();
        folder = new PeepholeFoldConstants();
        compiler = new Compiler();
        folder.beginTraversal(compiler);
    }

    public void testOptimizeSubtreeNew() throws Throwable {
        Node expr = new Node(Token.EXPR_RESULT, new Node(Token.NEW, Node.newString("String"), Node.newString("abc")));
        Node getElem = new Node(Token.GETELEM, expr.getFirstChild(), Node.newNumber(0));
        expr.replaceChild(expr.getFirstChild(), getElem);
        Node optimized = folder.optimizeSubtree(getElem);
        assertNotNull(optimized);
    }

    public void testOptimizeSubtreeTypeof() throws Throwable {
        Node typeofNode = new Node(Token.TYPEOF, Node.newNumber(5));
        Node parent = new Node(Token.EXPR_RESULT, typeofNode);
        Node optimized = folder.optimizeSubtree(typeofNode);
        assertEquals(Token.STRING, optimized.getType());
        assertEquals("number", optimized.getString());
    }

    public void testOptimizeSubtreeUnary() throws Throwable {
        Node notNode = new Node(Token.NOT, Node.newTrue());
        Node parent = new Node(Token.EXPR_RESULT, notNode);
        Node optimized = folder.optimizeSubtree(notNode);
        assertEquals(Token.FALSE, optimized.getType());
    }

    public void testOptimizeSubtreeVoid() throws Throwable {
        Node voidNode = new Node(Token.VOID, Node.newNumber(5));
        Node parent = new Node(Token.EXPR_RESULT, voidNode);
        Node optimized = folder.optimizeSubtree(voidNode);
        assertNotNull(optimized);
    }

    public void testOptimizeSubtreeBinary() throws Throwable {
        Node addNode = new Node(Token.ADD, Node.newNumber(1), Node.newNumber(2));
        Node parent = new Node(Token.EXPR_RESULT, addNode);
        Node optimized = folder.optimizeSubtree(addNode);
        assertEquals(Token.NUMBER, optimized.getType());
        assertEquals(3.0, optimized.getDouble());
    }

    public void testTryFoldBinaryOperatorEdgeCases() throws Throwable {
        Node addNode = new Node(Token.ADD);
        Node optimized = folder.optimizeSubtree(addNode);
        assertEquals(addNode, optimized);

        addNode.addChildToBack(Node.newNumber(1));
        optimized = folder.optimizeSubtree(addNode);
        assertEquals(addNode, optimized);
    }

    public void testTryReduceVoid() throws Throwable {
        Node voidZero = new Node(Token.VOID, Node.newNumber(0));
        Node parent = new Node(Token.EXPR_RESULT, voidZero);
        Node optimized = folder.optimizeSubtree(voidZero);
        assertEquals(Token.VOID, optimized.getType());

        Node voidNonZero = new Node(Token.VOID, Node.newNumber(5));
        Node parent2 = new Node(Token.EXPR_RESULT, voidNonZero);
        Node optimized2 = folder.optimizeSubtree(voidNonZero);
        assertEquals(Token.VOID, optimized2.getType());
    }

    public void testTryReduceOperandsForOpAdd() throws Throwable {
        Node add = new Node(Token.ADD, Node.newString("a"), Node.newNumber(1));
        Node parent = new Node(Token.EXPR_RESULT, add);
        Node optimized = folder.optimizeSubtree(add);
        assertNotNull(optimized);
    }

    public void testTryReduceOperandsForOpAssign() throws Throwable {
        Node assign = new Node(Token.ASSIGN_ADD, Node.newName("x"), Node.newNumber(1));
        Node parent = new Node(Token.EXPR_RESULT, assign);
        Node optimized = folder.optimizeSubtree(assign);
        assertNotNull(optimized);
    }

    public void testTryConvertToNumber() throws Throwable {
        Node hook = new Node(Token.HOOK, Node.newTrue(), Node.newNumber(1), Node.newNumber(2));
        Node parent = new Node(Token.EXPR_RESULT, hook);
        folder.optimizeSubtree(hook);
        
        Node name = Node.newName("undefined");
        Node parentName = new Node(Token.EXPR_RESULT, name);
        folder.optimizeSubtree(name);
    }

    public void testTryFoldTypeofVariants() throws Throwable {
        Node f = new Node(Token.TYPEOF, new Node(Token.FUNCTION));
        new Node(Token.EXPR_RESULT, f);
        assertEquals("function", folder.optimizeSubtree(f).getString());

        Node s = new Node(Token.TYPEOF, Node.newString("hello"));
        new Node(Token.EXPR_RESULT, s);
        assertEquals("string", folder.optimizeSubtree(s).getString());

        Node n = new Node(Token.TYPEOF, Node.newNumber(10));
        new Node(Token.EXPR_RESULT, n);
        assertEquals("number", folder.optimizeSubtree(n).getString());

        Node t = new Node(Token.TYPEOF, Node.newTrue());
        new Node(Token.EXPR_RESULT, t);
        assertEquals("boolean", folder.optimizeSubtree(t).getString());

        Node f2 = new Node(Token.TYPEOF, Node.newFalse());
        new Node(Token.EXPR_RESULT, f2);
        assertEquals("boolean", folder.optimizeSubtree(f2).getString());

        Node nul = new Node(Token.TYPEOF, new Node(Token.NULL));
        new Node(Token.EXPR_RESULT, nul);
        assertEquals("object", folder.optimizeSubtree(nul).getString());

        Node obj = new Node(Token.TYPEOF, new Node(Token.OBJECTLIT));
        new Node(Token.EXPR_RESULT, obj);
        assertEquals("object", folder.optimizeSubtree(obj).getString());

        Node arr = new Node(Token.TYPEOF, new Node(Token.ARRAYLIT));
        new Node(Token.EXPR_RESULT, arr);
        assertEquals("object", folder.optimizeSubtree(arr).getString());

        Node v = new Node(Token.TYPEOF, new Node(Token.VOID, Node.newNumber(0)));
        new Node(Token.EXPR_RESULT, v);
        assertEquals("undefined", folder.optimizeSubtree(v).getString());

        Node undefName = new Node(Token.TYPEOF, Node.newName("undefined"));
        new Node(Token.EXPR_RESULT, undefName);
        assertEquals("undefined", folder.optimizeSubtree(undefName).getString());

        Node unknownTypeof = new Node(Token.TYPEOF, Node.newName("foo"));
        new Node(Token.EXPR_RESULT, unknownTypeof);
        assertEquals(Token.TYPEOF, folder.optimizeSubtree(unknownTypeof).getType());
    }

    public void testTryFoldUnaryOperatorVariants() throws Throwable {
        Node notZero = new Node(Token.NOT, Node.newNumber(0));
        new Node(Token.EXPR_RESULT, notZero);
        assertEquals(Token.NOT, folder.optimizeSubtree(notZero).getType());

        Node pos = new Node(Token.POS, Node.newNumber(5));
        new Node(Token.EXPR_RESULT, pos);
        assertEquals(Token.NUMBER, folder.optimizeSubtree(pos).getType());

        Node negInfinity = new Node(Token.NEG, Node.newName("Infinity"));
        new Node(Token.EXPR_RESULT, negInfinity);
        assertEquals(Token.NEG, folder.optimizeSubtree(negInfinity).getType());

        Node negNaN = new Node(Token.NEG, Node.newName("NaN"));
        new Node(Token.EXPR_RESULT, negNaN);
        assertEquals(Token.NAME, folder.optimizeSubtree(negNaN).getType());

        Node negNonNum = new Node(Token.NEG, Node.newString("abc"));
        new Node(Token.EXPR_RESULT, negNonNum);
        assertEquals(Token.NEG, folder.optimizeSubtree(negNonNum).getType());

        Node bitNotValid = new Node(Token.BITNOT, Node.newNumber(5.5));
        new Node(Token.EXPR_RESULT, bitNotValid);
        assertEquals(Token.BITNOT, folder.optimizeSubtree(bitNotValid).getType());

        Node bitNotOutOfRange = new Node(Token.BITNOT, Node.newNumber(1e20));
        new Node(Token.EXPR_RESULT, bitNotOutOfRange);
        assertEquals(Token.BITNOT, folder.optimizeSubtree(bitNotOutOfRange).getType());

        Node bitNotNonNum = new Node(Token.BITNOT, Node.newString("abc"));
        new Node(Token.EXPR_RESULT, bitNotNonNum);
        assertEquals(Token.BITNOT, folder.optimizeSubtree(bitNotNonNum).getType());
    }

    public void testTryFoldInstanceof() throws Throwable {
        Node inst = new Node(Token.INSTANCEOF, Node.newString("abc"), Node.newString("String"));
        new Node(Token.EXPR_RESULT, inst);
        Node optimized = folder.optimizeSubtree(inst);
        assertEquals(Token.FALSE, optimized.getType());

        Node instObj = new Node(Token.INSTANCEOF, Node.newObjectlit(), Node.newName("Object"));
        new Node(Token.EXPR_RESULT, instObj);
        Node optimizedObj = folder.optimizeSubtree(instObj);
        assertEquals(Token.TRUE, optimizedObj.getType());
    }

    public void testTryFoldAssign() throws Throwable {
        Node left = Node.newName("x");
        Node right = new Node(Token.ADD, Node.newName("x"), Node.newNumber(1));
        Node assign = new Node(Token.ASSIGN, left, right);
        new Node(Token.EXPR_RESULT, assign);
        Node optimized = folder.optimizeSubtree(assign);
        assertEquals(Token.ASSIGN_ADD, optimized.getType());

        Node leftComm = Node.newName("x");
        Node rightComm = new Node(Token.ADD, Node.newNumber(1), Node.newName("x"));
        Node assignComm = new Node(Token.ASSIGN, leftComm, rightComm);
        new Node(Token.EXPR_RESULT, assignComm);
        Node optimizedComm = folder.optimizeSubtree(assignComm);
        assertEquals(Token.ASSIGN_ADD, optimizedComm.getType());
    }

    public void testTryFoldAndOr() throws Throwable {
        Node and = new Node(Token.AND, Node.newFalse(), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, and);
        assertEquals(Token.FALSE, folder.optimizeSubtree(and).getType());

        Node or = new Node(Token.OR, Node.newTrue(), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, or);
        assertEquals(Token.TRUE, folder.optimizeSubtree(or).getType());

        Node andTrue = new Node(Token.AND, Node.newTrue(), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, andTrue);
        assertEquals(Token.NUMBER, folder.optimizeSubtree(andTrue).getType());
    }

    public void testTryFoldAddStringAndArithmetic() throws Throwable {
        Node addStr = new Node(Token.ADD, Node.newString("a"), Node.newString("b"));
        new Node(Token.EXPR_RESULT, addStr);
        assertEquals("ab", folder.optimizeSubtree(addStr).getString());

        Node childAdd = new Node(Token.ADD, new Node(Token.ADD, Node.newString("a"), Node.newString("b")), Node.newString("c"));
        new Node(Token.EXPR_RESULT, childAdd);
        assertNotNull(folder.optimizeSubtree(childAdd));

        Node leftChildAdd = new Node(Token.ADD, Node.newString("a"), new Node(Token.ADD, Node.newString("b"), Node.newString("c")));
        new Node(Token.EXPR_RESULT, leftChildAdd);
        assertNotNull(folder.optimizeSubtree(leftChildAdd));
    }

    public void testPerformArithmeticOpEdgeCases() throws Throwable {
        Node divZero = new Node(Token.DIV, Node.newNumber(5), Node.newNumber(0));
        new Node(Token.EXPR_RESULT, divZero);
        assertEquals(Token.DIV, folder.optimizeSubtree(divZero).getType());

        Node modZero = new Node(Token.MOD, Node.newNumber(5), Node.newNumber(0));
        new Node(Token.EXPR_RESULT, modZero);
        assertEquals(Token.MOD, folder.optimizeSubtree(modZero).getType());

        Node infResult = new Node(Token.MUL, Node.newNumber(1e308), Node.newNumber(1e308));
        new Node(Token.EXPR_RESULT, infResult);
        Node optInf = folder.optimizeSubtree(infResult);
        assertNotNull(optInf);
    }

    public void testTryFoldShift() throws Throwable {
        Node lsh = new Node(Token.LSH, Node.newNumber(1), Node.newNumber(2));
        new Node(Token.EXPR_RESULT, lsh);
        assertEquals(4.0, folder.optimizeSubtree(lsh).getDouble());

        Node rsh = new Node(Token.RSH, Node.newNumber(4), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, rsh);
        assertEquals(2.0, folder.optimizeSubtree(rsh).getDouble());

        Node ursh = new Node(Token.URSH, Node.newNumber(-1), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, ursh);
        assertNotNull(folder.optimizeSubtree(ursh));

        Node outRange = new Node(Token.LSH, Node.newNumber(1e20), Node.newNumber(2));
        new Node(Token.EXPR_RESULT, outRange);
        assertEquals(Token.LSH, folder.optimizeSubtree(outRange).getType());

        Node outBound = new Node(Token.LSH, Node.newNumber(1), Node.newNumber(35));
        new Node(Token.EXPR_RESULT, outBound);
        assertEquals(Token.LSH, folder.optimizeSubtree(outBound).getType());

        Node fracL = new Node(Token.LSH, Node.newNumber(1.5), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, fracL);
        assertEquals(Token.LSH, folder.optimizeSubtree(fracL).getType());
    }

    public void testTryFoldComparisonVariants() throws Throwable {
        Node eq = new Node(Token.EQ, Node.newNumber(1), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, eq);
        assertEquals(Token.TRUE, folder.optimizeSubtree(eq).getType());

        Node ne = new Node(Token.NE, Node.newNumber(1), Node.newNumber(2));
        new Node(Token.EXPR_RESULT, ne);
        assertEquals(Token.TRUE, folder.optimizeSubtree(ne).getType());

        Node lt = new Node(Token.LT, Node.newNumber(1), Node.newNumber(2));
        new Node(Token.EXPR_RESULT, lt);
        assertEquals(Token.TRUE, folder.optimizeSubtree(lt).getType());

        Node gt = new Node(Token.GT, Node.newNumber(2), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, gt);
        assertEquals(Token.TRUE, folder.optimizeSubtree(gt).getType());

        Node le = new Node(Token.LE, Node.newNumber(1), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, le);
        assertEquals(Token.TRUE, folder.optimizeSubtree(le).getType());

        Node ge = new Node(Token.GE, Node.newNumber(1), Node.newNumber(1));
        new Node(Token.EXPR_RESULT, ge);
        assertEquals(Token.TRUE, folder.optimizeSubtree(ge).getType());

        Node sheqThis = new Node(Token.SHEQ, new Node(Token.THIS), new Node(Token.THIS));
        new Node(Token.EXPR_RESULT, sheqThis);
        assertEquals(Token.TRUE, folder.optimizeSubtree(sheqThis).getType());

        Node shneThis = new Node(Token.SHNE, new Node(Token.THIS), new Node(Token.THIS));
        new Node(Token.EXPR_RESULT, shneThis);
        assertEquals(Token.FALSE, folder.optimizeSubtree(shneThis).getType());

        Node eqStr = new Node(Token.EQ, Node.newString("a"), Node.newString("a"));
        new Node(Token.EXPR_RESULT, eqStr);
        assertEquals(Token.TRUE, folder.optimizeSubtree(eqStr).getType());

        Node neStr = new Node(Token.NE, Node.newString("a"), Node.newString("b"));
        new Node(Token.EXPR_RESULT, neStr);
        assertEquals(Token.TRUE, folder.optimizeSubtree(neStr).getType());

        Node eqNull = new Node(Token.EQ, new Node(Token.NULL), Node.newName("undefined"));
        new Node(Token.EXPR_RESULT, eqNull);
        assertEquals(Token.TRUE, folder.optimizeSubtree(eqNull).getType());
    }

    public void testTryFoldCtorCallString() throws Throwable {
        Node newStr = new Node(Token.NEW, Node.newName("String"), Node.newString("hello"));
        Node getElem = new Node(Token.GETELEM, Node.newObjectlit(), newStr);
        new Node(Token.EXPR_RESULT, getElem);
        Node optimized = folder.optimizeSubtree(getElem);
        assertNotNull(optimized);

        Node newStrEmpty = new Node(Token.NEW, Node.newName("String"));
        Node getElemEmpty = new Node(Token.GETELEM, Node.newObjectlit(), newStrEmpty);
        new Node(Token.EXPR_RESULT, getElemEmpty);
        Node optimizedEmpty = folder.optimizeSubtree(getElemEmpty);
        assertNotNull(optimizedEmpty);
    }

    public void testTryFoldGetElemAndGetProp() throws Throwable {
        Node arrLit = new Node(Token.ARRAYLIT, Node.newNumber(10), Node.newNumber(20));
        Node getElem = new Node(Token.GETELEM, arrLit, Node.newNumber(0));
        new Node(Token.EXPR_RESULT, getElem);
        assertEquals(10.0, folder.optimizeSubtree(getElem).getDouble());

        Node arrLength = new Node(Token.GETPROP, new Node(Token.ARRAYLIT, Node.newNumber(10)), Node.newString("length"));
        new Node(Token.EXPR_RESULT, arrLength);
        assertEquals(1.0, folder.optimizeSubtree(arrLength).getDouble());

        Node strLength = new Node(Token.GETPROP, Node.newString("abc"), Node.newString("length"));
        new Node(Token.EXPR_RESULT, strLength);
        assertEquals(3.0, folder.optimizeSubtree(strLength).getDouble());
    }

    public void testTryFoldObjectPropAccess() throws Throwable {
        Node objLit = new Node(Token.OBJECTLIT, Node.newStringKey("a", Node.newNumber(5)));
        Node getProp = new Node(Token.GETPROP, objLit, Node.newString("a"));
        new Node(Token.EXPR_RESULT, getProp);
        assertEquals(5.0, folder.optimizeSubtree(getProp).getDouble());
    }
}