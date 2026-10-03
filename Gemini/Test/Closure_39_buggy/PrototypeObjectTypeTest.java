package com.google.javascript.rhino.jstype;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;

import java.util.Set;

public class PrototypeObjectTypeTest {

    @Test
    public void testConstructorsAndBasicProperties() throws Throwable {
        JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
            public void warning(String message, String sourceName, int line, int lineOffset) {}
            public void error(String message, String sourceName, int line, int lineOffset) {}
            public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
                return new EvaluatorException(message);
            }
        });

        PrototypeObjectType obj1 = new PrototypeObjectType(registry, "MyClass", null);
        assertEquals("MyClass", obj1.getReferenceName());
        assertTrue(obj1.hasReferenceName());
        assertFalse(obj1.isNativeObjectType());
        assertNull(obj1.getConstructor());
        assertNull(obj1.getOwnerFunction());
        assertTrue(obj1.matchesObjectContext());
        assertFalse(obj1.canBeCalled());

        PrototypeObjectType obj2 = new PrototypeObjectType(registry, null, null, true);
        assertNull(obj2.getReferenceName());
        assertFalse(obj2.hasReferenceName());
        assertTrue(obj2.isNativeObjectType());
    }

    @Test
    public void testPropertyManagement() throws Throwable {
        JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
            public void warning(String message, String sourceName, int line, int lineOffset) {}
            public void error(String message, String sourceName, int line, int lineOffset) {}
            public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
                return new EvaluatorException(message);
            }
        });

        PrototypeObjectType obj = new PrototypeObjectType(registry, "Obj", null);
        JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
        Node propNode = IR.name("test");

        assertFalse(obj.hasProperty("p1"));
        assertFalse(obj.hasOwnProperty("p1"));
        assertNull(obj.getSlot("p1"));
        assertEquals(0, obj.getPropertiesCount());

        boolean defined = obj.defineProperty("p1", numType, false, propNode);
        assertTrue(defined);

        assertTrue(obj.hasProperty("p1"));
        assertTrue(obj.hasOwnProperty("p1"));
        assertNotNull(obj.getSlot("p1"));
        assertEquals(1, obj.getPropertiesCount());
        assertTrue(obj.isPropertyTypeDeclared("p1"));
        assertFalse(obj.isPropertyTypeInferred("p1"));
        assertEquals(numType, obj.getPropertyType("p1"));
        assertEquals(propNode, obj.getPropertyNode("p1"));

        Set<String> ownNames = obj.getOwnPropertyNames();
        assertTrue(ownNames.contains("p1"));

        boolean definedAgain = obj.defineProperty("p1", numType, false, propNode);
        assertFalse(definedAgain);

        boolean removed = obj.removeProperty("p1");
        assertTrue(removed);
        assertFalse(obj.hasProperty("p1"));
        assertEquals(0, obj.getPropertiesCount());
        assertNull(obj.getPropertyNode("p1"));
    }

    @Test
    public void testInferredPropertyAndJSDoc() throws Throwable {
        JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
            public void warning(String message, String sourceName, int line, int lineOffset) {}
            public void error(String message, String sourceName, int line, int lineOffset) {}
            public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
                return new EvaluatorException(message);
            }
        });

        PrototypeObjectType obj = new PrototypeObjectType(registry, "Obj", null);
        JSType strType = registry.getNativeType(JSTypeNative.STRING_TYPE);

        obj.defineProperty("inferredProp", strType, true, null);
        assertTrue(obj.isPropertyTypeInferred("inferredProp"));
        assertFalse(obj.isPropertyTypeDeclared("inferredProp"));

        JSDocInfo info = new JSDocInfo();
        obj.setPropertyJSDocInfo("inferredProp", info);
        assertEquals(info, obj.getOwnPropertyJSDocInfo("inferredProp"));

        obj.setPropertyJSDocInfo("nonExistent", info);
        assertTrue(obj.hasProperty("nonExistent"));
        assertTrue(obj.isPropertyTypeInferred("nonExistent"));
    }

    @Test
    public void testContextMatchingAndUnboxing() throws Throwable {
        JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
            public void warning(String message, String sourceName, int line, int lineOffset) {}
            public void error(String message, String sourceName, int line, int lineOffset) {}
            public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
                return new EvaluatorException(message);
            }
        });

        PrototypeObjectType obj = new PrototypeObjectType(registry, "Obj", null);
        assertFalse(obj.matchesNumberContext());
        assertTrue(obj.matchesStringContext());
        assertTrue(obj.matchesObjectContext());

        assertEquals(obj.getNativeType(JSTypeNative.UNKNOWN_TYPE), obj.unboxesTo());
    }

    @Test
    public void testPrettyPrintAndToString() throws Throwable {
        JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
            public void warning(String message, String sourceName, int line, int lineOffset) {}
            public void error(String message, String sourceName, int line, int lineOffset) {}
            public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
                return new EvaluatorException(message);
            }
        });

        PrototypeObjectType obj = new PrototypeObjectType(registry, "NamedObj", null);
        assertEquals("NamedObj", obj.toString());

        PrototypeObjectType anonObj = new PrototypeObjectType(registry, null, null);
        assertEquals("{...}", anonObj.toString());

        anonObj.setPrettyPrint(true);
        assertTrue(anonObj.isPrettyPrint());
        assertEquals("{}", anonObj.toString());

        JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
        anonObj.defineProperty("a", numType, false, null);
        anonObj.defineProperty("b", numType, false, null);
        anonObj.defineProperty("c", numType, false, null);
        anonObj.defineProperty("d", numType, false, null);
        anonObj.defineProperty("e", numType, false, null);

        String prettyStr = anonObj.toString();
        assertTrue(prettyStr.contains("..."));
    }

    @Test
    public void testOwnerFunctionAndImplicitPrototype() throws Throwable {
        JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
            public void warning(String message, String sourceName, int line, int lineOffset) {}
            public void error(String message, String sourceName, int line, int lineOffset) {}
            public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
                return new EvaluatorException(message);
            }
        });

        PrototypeObjectType obj = new PrototypeObjectType(registry, null, null);
        assertNull(obj.getOwnerFunction());

        obj.setImplicitPrototype(null);
        assertNull(obj.getImplicitPrototype());

        Iterable<?> implemented = obj.getCtorImplementedInterfaces();
        assertNotNull(implemented);

        Iterable<?> extended = obj.getCtorExtendedInterfaces();
        assertNotNull(extended);
    }
}