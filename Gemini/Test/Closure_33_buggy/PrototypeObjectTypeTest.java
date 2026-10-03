package com.google.javascript.rhino.jstype;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.StaticScope;

import java.util.Set;

public class PrototypeObjectTypeTest {

    private JSTypeRegistry registry;
    private PrototypeObjectType prototypeObjectType;

    @Before
    public void setUp() throws Throwable {
        registry = new JSTypeRegistry(new DummyErrorReporter());
        prototypeObjectType = new PrototypeObjectType(registry, "TestClass", null);
    }

    @Test
    public void testConstructors() throws Throwable {
        PrototypeObjectType obj1 = new PrototypeObjectType(registry, null, null);
        assertNull(obj1.getReferenceName());
        assertFalse(obj1.hasReferenceName());

        PrototypeObjectType obj2 = new PrototypeObjectType(registry, "MyClass", null, true);
        assertEquals("MyClass", obj2.getReferenceName());
        assertTrue(obj2.isNativeObjectType());
    }

    @Test
    public void testPropertyOperations() throws Throwable {
        String propName = "testProp";
        JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
        
        assertFalse(prototypeObjectType.hasProperty(propName));
        assertFalse(prototypeObjectType.hasOwnProperty(propName));
        assertNull(prototypeObjectType.getSlot(propName));

        boolean defined = prototypeObjectType.defineProperty(propName, stringType, false, null);
        assertTrue(defined);

        assertTrue(prototypeObjectType.hasProperty(propName));
        assertTrue(prototypeObjectType.hasOwnProperty(propName));
        assertNotNull(prototypeObjectType.getSlot(propName));
        assertTrue(prototypeObjectType.isPropertyTypeDeclared(propName));
        assertFalse(prototypeObjectType.isPropertyTypeInferred(propName));
        assertEquals(stringType, prototypeObjectType.getPropertyType(propName));

        Set<String> ownProps = prototypeObjectType.getOwnPropertyNames();
        assertTrue(ownProps.contains(propName));

        boolean removed = prototypeObjectType.removeProperty(propName);
        assertTrue(removed);
        assertFalse(prototypeObjectType.hasOwnProperty(propName));
    }

    @Test
    public void testInferredProperty() throws Throwable {
        String propName = "inferredProp";
        JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);

        boolean defined = prototypeObjectType.defineProperty(propName, numType, true, null);
        assertTrue(defined);
        assertTrue(prototypeObjectType.isPropertyTypeInferred(propName));
        assertFalse(prototypeObjectType.isPropertyTypeDeclared(propName));
    }

    @Test
    public void testDefineExistingProperty() throws Throwable {
        String propName = "existingProp";
        JSType type = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);

        assertTrue(prototypeObjectType.defineProperty(propName, type, false, null));
        // Defining again with hasOwnDeclaredProperty should return false
        assertFalse(prototypeObjectType.defineProperty(propName, type, false, null));
    }

    @Test
    public void testPropertyJSDocInfo() throws Throwable {
        String propName = "docProp";
        JSDocInfo info = new JSDocInfo();
        
        assertNull(prototypeObjectType.getOwnPropertyJSDocInfo(propName));

        prototypeObjectType.setPropertyJSDocInfo(propName, info);
        assertNotNull(prototypeObjectType.getOwnPropertyJSDocInfo(propName));
        assertEquals(info, prototypeObjectType.getOwnPropertyJSDocInfo(propName));
    }

    @Test
    public void testPropertyNode() throws Throwable {
        String propName = "nodeProp";
        Node node = new Node(38); // Token for NAME or similar generic node
        JSType type = registry.getNativeType(JSTypeNative.NUMBER_TYPE);

        assertNull(prototypeObjectType.getPropertyNode(propName));

        prototypeObjectType.defineProperty(propName, type, false, node);
        assertEquals(node, prototypeObjectType.getPropertyNode(propName));
    }

    @Test
    public void testContextMatches() throws Throwable {
        assertTrue(prototypeObjectType.matchesObjectContext());
        assertFalse(prototypeObjectType.matchesNumberContext());
        assertFalse(prototypeObjectType.matchesStringContext());
        assertFalse(prototypeObjectType.canBeCalled());
    }

    @Test
    public void testUnboxesTo() throws Throwable {
        assertEquals(prototypeObjectType, prototypeObjectType.unboxesTo());
    }

    @Test
    public void testPrettyPrint() throws Throwable {
        assertFalse(prototypeObjectType.isPrettyPrint());
        prototypeObjectType.setPrettyPrint(true);
        assertTrue(prototypeObjectType.isPrettyPrint());

        String str = prototypeObjectType.toStringHelper(false);
        assertTrue(str.contains("{}") || str.contains("..."));
    }

    @Test
    public void testGetConstructorAndOwnerFunction() throws Throwable {
        assertNull(prototypeObjectType.getConstructor());
        assertNull(prototypeObjectType.getOwnerFunction());

        FunctionType funcType = registry.createFunctionType(
            registry.getNativeType(JSTypeNative.NO_TYPE),
            ImmutableList.<JSType>of()
        );
        prototypeObjectType.setOwnerFunction(funcType);
        assertEquals(funcType, prototypeObjectType.getOwnerFunction());
        assertEquals("TestClass.prototype", prototypeObjectType.getReferenceName());
    }

    @Test
    public void testGetPropertiesCount() throws Throwable {
        assertEquals(0, prototypeObjectType.getPropertiesCount());
        prototypeObjectType.defineProperty("a", registry.getNativeType(JSTypeNative.NUMBER_TYPE), false, null);
        assertEquals(1, prototypeObjectType.getPropertiesCount());
    }

    @Test
    public void testIsSubtype() throws Throwable {
        ObjectType unknownType = registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
        assertTrue(prototypeObjectType.isSubtype(unknownType));
        assertTrue(prototypeObjectType.isSubtype(prototypeObjectType));
    }

    @Test
    public void testResolveInternal() throws Throwable {
        ErrorReporter reporter = new DummyErrorReporter();
        StaticScope<JSType> scope = registry.getGlobalScope();
        JSType resolved = prototypeObjectType.resolveInternal(reporter, scope);
        assertEquals(prototypeObjectType, resolved);
    }

    private static class DummyErrorReporter implements ErrorReporter {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public com.google.javascript.rhino.EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new com.google.javascript.rhino.EvaluatorException(message, sourceName, line, lineOffset);
        }
    }
}