package com.google.javascript.rhino.jstype;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ArrowTypeTest {

    private JSTypeRegistry registry;
    private JSType unknownType;

    @Before
    public void setUp() throws Throwable {
        registry = new JSTypeRegistry(new SimpleErrorReporter());
        unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }

    @Test
    public void testConstructorNullHandling() throws Throwable {
        ArrowType arrowType = new ArrowType(registry, null, null, true);
        assertNotNull(arrowType.parameters);
        assertEquals(unknownType, arrowType.returnType);
        assertTrue(arrowType.returnTypeInferred);

        ArrowType arrowType2 = new ArrowType(registry, null, null);
        assertNotNull(arrowType2.parameters);
        assertEquals(unknownType, arrowType2.returnType);
        assertFalse(arrowType2.returnTypeInferred);
    }

    @Test
    public void testIsSubtypeNonArrowType() throws Throwable {
        ArrowType arrowType = new ArrowType(registry, null, unknownType, false);
        JSType notAnArrow = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
        assertFalse(arrowType.isSubtype(notAnArrow));
    }

    @Test
    public void testIsSubtypeCovariantReturn() throws Throwable {
        JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
        JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);

        ArrowType arrow1 = new ArrowType(registry, null, stringType, false);
        ArrowType arrow2 = new ArrowType(registry, null, numberType, false);

        assertFalse(arrow1.isSubtype(arrow2));
    }

    @Test
    public void testHasUnknownParamsOrReturn() throws Throwable {
        ArrowType arrow1 = new ArrowType(registry, null, unknownType, false);
        assertTrue(arrow1.hasUnknownParamsOrReturn());

        JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
        Node paramNode = Node.newString("param");
        paramNode.setJSType(numberType);
        Node params = new Node(Token.PARAM_LIST, paramNode);

        ArrowType arrow2 = new ArrowType(registry, params, numberType, false);
        assertFalse(arrow2.hasUnknownParamsOrReturn());
    }

    @Test
    public void testHashCode() throws Throwable {
        ArrowType arrow1 = new ArrowType(registry, null, unknownType, true);
        int code1 = arrow1.hashCode();
        
        ArrowType arrow2 = new ArrowType(registry, null, unknownType, false);
        int code2 = arrow2.hashCode();

        assertTrue(code1 != code2);
    }

    @Test
    public void testUnsupportedOperations() throws Throwable {
        ArrowType arrowType = new ArrowType(registry, null, unknownType, false);
        
        try {
            arrowType.getLeastSupertype(unknownType);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            arrowType.getGreatestSubtype(unknownType);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            arrowType.testForEquality(unknownType);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            arrowType.visit(null);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testGetPossibleToBooleanOutcomes() throws Throwable {
        ArrowType arrowType = new ArrowType(registry, null, unknownType, false);
        assertEquals(BooleanLiteralSet.TRUE, arrowType.getPossibleToBooleanOutcomes());
    }

    @Test
    public void testToStringHelper() throws Throwable {
        ArrowType arrowType = new ArrowType(registry, null, unknownType, false);
        assertEquals("[ArrowType]", arrowType.toStringHelper(false));
    }

    @Test
    public void testHasAnyTemplateInternal() throws Throwable {
        ArrowType arrowType = new ArrowType(registry, null, unknownType, false);
        assertFalse(arrowType.hasAnyTemplateInternal());
    }

    @Test
    public void testResolveInternal() throws Throwable {
        ArrowType arrowType = new ArrowType(registry, null, unknownType, false);
        JSType resolved = arrowType.resolveInternal(new SimpleErrorReporter(), null);
        assertNotNull(resolved);
    }

    @Test
    public void testCheckArrowEquivalenceHelper() throws Throwable {
        ArrowType arrow1 = new ArrowType(registry, null, unknownType, false);
        ArrowType arrow2 = new ArrowType(registry, null, unknownType, false);
        assertTrue(arrow1.checkArrowEquivalenceHelper(arrow2, true));
    }
}