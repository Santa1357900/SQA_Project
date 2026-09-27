package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;

public class TypeValidatorTest {

  @Test
  public void testTypeMismatchEqualsAndHashCode() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    
    JSType strType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    Node node = IR.name("test");
    JSError error = JSError.make("testfile", node, TypeValidator.TYPE_MISMATCH_WARNING, "mismatch");
    
    TypeValidator.TypeMismatch mismatch1 = new TypeValidator.TypeMismatch(strType, numType, error);
    TypeValidator.TypeMismatch mismatch2 = new TypeValidator.TypeMismatch(numType, strType, error);
    TypeValidator.TypeMismatch mismatch3 = new TypeValidator.TypeMismatch(strType, strType, error);
    
    assertTrue(mismatch1.equals(mismatch2));
    assertTrue(mismatch2.equals(mismatch1));
    assertFalse(mismatch1.equals(mismatch3));
    assertFalse(mismatch1.equals(null));
    assertFalse(mismatch1.equals("some string"));
    
    assertEquals(mismatch1.hashCode(), mismatch2.hashCode());
    assertNotNull(mismatch1.toString());
  }

  @Test
  public void testTypeValidatorBasicGetters() throws Throwable {
    Compiler compiler = new Compiler();
    TypeValidator validator = new TypeValidator(compiler);
    
    validator.setShouldReport(false);
    assertNotNull(validator.getMismatches());
    
    Node node = IR.string("hello");
    NodeTraversal traversal = new NodeTraversal(compiler, null);
    validator.expectValidTypeofName(traversal, node, "unknown");
  }
}