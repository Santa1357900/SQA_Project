package com.google.javascript.rhino.jstype;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.Set;

public class PrototypeObjectTypeTest {

  @Test
  public void testConstructorAndBasicProperties() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, "MyClass", null, false);
    assertEquals("MyClass", obj.getReferenceName());
    assertTrue(obj.hasReferenceName());
    assertFalse(obj.isNativeObjectType());
    assertEquals(0, obj.getPropertiesCount());
    assertNull(obj.getConstructor());
    assertNull(obj.getOwnerFunction());
    assertTrue(obj.matchesObjectContext());
    assertFalse(obj.canBeCalled());
  }

  @Test
  public void testNativeTypeConstructor() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, null, null, true);
    assertNull(obj.getReferenceName());
    assertFalse(obj.hasReferenceName());
    assertTrue(obj.isNativeObjectType());
  }

  @Test
  public void testPropertyOperations() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, "TestObj", null);
    JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    Node propNode = new Node(42);

    boolean defined = obj.defineProperty("p1", numType, false, propNode);
    assertTrue(defined);
    assertTrue(obj.hasProperty("p1"));
    assertTrue(obj.hasOwnProperty("p1"));
    assertEquals(1, obj.getPropertiesCount());
    assertTrue(obj.isPropertyTypeDeclared("p1"));
    assertFalse(obj.isPropertyTypeInferred("p1"));
    assertEquals(numType, obj.getPropertyType("p1"));
    assertEquals(propNode, obj.getPropertyNode("p1"));

    Set<String> ownProps = obj.getOwnPropertyNames();
    assertTrue(ownProps.contains("p1"));

    // Redefining declared property should return false
    boolean redefined = obj.defineProperty("p1", numType, false, propNode);
    assertFalse(redefined);

    // Remove property
    boolean removed = obj.removeProperty("p1");
    assertTrue(removed);
    assertFalse(obj.hasProperty("p1"));
    assertEquals(0, obj.getPropertiesCount());
  }

  @Test
  public void testInferredProperty() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, "InferredObj", null);
    JSType strType = registry.getNativeType(JSTypeNative.STRING_TYPE);

    obj.defineProperty("inferredProp", strType, true, null);
    assertTrue(obj.isPropertyTypeInferred("inferredProp"));
    assertFalse(obj.isPropertyTypeDeclared("inferredProp"));
  }

  @Test
  public void testPropertyJSDocInfo() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, "JsDocObj", null);
    JSDocInfo info = new JSDocInfo();

    obj.setPropertyJSDocInfo("docProp", info);
    assertNotNull(obj.getOwnPropertyJSDocInfo("docProp"));
    assertTrue(obj.hasProperty("docProp"));

    obj.setPropertyJSDocInfo("docProp", null);
    // null info should not crash or add new properties if not present
  }

  @Test
  public void testPrettyPrint() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, null, null);
    obj.setPrettyPrint(true);
    assertTrue(obj.isPrettyPrint());

    String str = obj.toStringHelper(false);
    assertTrue(str.contains("{}"));
  }

  @Test
  public void testContextMatches() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, "ContextObj", null);
    assertFalse(obj.matchesNumberContext());
    assertTrue(obj.matchesObjectContext());
    assertFalse(obj.matchesStringContext());
    assertFalse(obj.canBeCalled());
  }

  @Test
  public void testUnboxesTo() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, "UnboxObj", null);
    assertNotNull(obj.unboxesTo());
  }

  @Test
  public void testMatchRecordTypeConstraint() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
        public void warning(String message, String sourceName, int line, int lineOffset) {}
        public void error(String message, String sourceName, int line, int lineOffset) {}
        public EvaluatorException runtimeError(String message, String sourceName, int line, int lineOffset) {
            return new EvaluatorException(message);
        }
    });

    PrototypeObjectType obj = new PrototypeObjectType(registry, null, null);
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("foo", registry.getNativeType(JSTypeNative.NUMBER_TYPE), null);
    RecordType recordType = builder.build();

    obj.matchRecordTypeConstraint(recordType);
    assertTrue(obj.hasProperty("foo"));
  }
}