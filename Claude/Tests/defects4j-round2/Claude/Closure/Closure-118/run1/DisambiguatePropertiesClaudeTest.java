package com.google.javascript.jscomp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Multimap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DisambiguatePropertiesClaudeTest {

  private Compiler compiler;
  private Map<String, CheckLevel> emptyMap;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    emptyMap = new HashMap<String, CheckLevel>();
  }

  // forJSTypeSystem กับ compiler/map ที่ถูกต้อง ต้องคืน instance ที่ไม่เป็น null
  @Test
  public void testForJSTypeSystem_validArgs_returnsNonNullInstance() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    assertNotNull(dp);
  }

  // forJSTypeSystem กับ propertiesToErrorFor ที่มีข้อมูล ต้องคืน instance ที่ไม่เป็น null
  @Test
  public void testForJSTypeSystem_nonEmptyPropertiesToErrorFor_returnsNonNullInstance() throws Throwable {
    Map<String, CheckLevel> map = new HashMap<String, CheckLevel>();
    map.put("toString", CheckLevel.OFF);
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, map);
    assertNotNull(dp);
  }

  // forJSTypeSystem กับ compiler เป็น null ต้องโยน NullPointerException
  @Test
  public void testForJSTypeSystem_nullCompiler_throwsNullPointerException() throws Throwable {
    try {
      DisambiguateProperties.forJSTypeSystem(null, emptyMap);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // forJSTypeSystem กับ propertiesToErrorFor เป็น null ต้องโยน NullPointerException
  @Test
  public void testForJSTypeSystem_nullPropertiesToErrorFor_throwsNullPointerException() throws Throwable {
    try {
      DisambiguateProperties.forJSTypeSystem(compiler, null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // process() ต้องโยน IllegalStateException เมื่อ compiler ยังไม่อยู่ใน NORMALIZED stage (node เป็น null)
  @Test
  public void testProcess_notNormalized_nullNodes_throwsIllegalStateException() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    try {
      dp.process(null, null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // process() ต้องโยน IllegalStateException เมื่อ compiler ยังไม่อยู่ใน NORMALIZED stage (node จริง)
  @Test
  public void testProcess_notNormalized_realNodes_throwsIllegalStateException() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    Node externs = IR.block();
    Node root = IR.block();
    try {
      dp.process(externs, root);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // process() ต้องโยน IllegalStateException แม้มี propertiesToErrorFor ไม่ว่าง เมื่อยังไม่ normalized
  @Test
  public void testProcess_notNormalized_withErrorMap_throwsIllegalStateException() throws Throwable {
    Map<String, CheckLevel> map = new HashMap<String, CheckLevel>();
    map.put("foo", CheckLevel.OFF);
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, map);
    try {
      dp.process(IR.block(), IR.block());
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // getProperty() เรียกด้วยชื่อเดียวกันสองครั้ง ต้องคืน instance เดียวกัน (containsKey == true branch)
  @Test
  public void testGetProperty_sameName_returnsSameInstance() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    Object p1 = dp.getProperty("foo");
    Object p2 = dp.getProperty("foo");
    assertSame(p1, p2);
  }

  // getProperty() เรียกด้วยชื่อต่างกัน ต้องคืน instance ต่างกัน (containsKey == false branch)
  @Test
  public void testGetProperty_differentNames_returnsDifferentInstances() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    Object p1 = dp.getProperty("foo");
    Object p2 = dp.getProperty("bar");
    assertNotSame(p1, p2);
  }

  // getProperty() เรียกซ้ำสามครั้งด้วยชื่อเดียวกัน ต้อง idempotent ตลอด
  @Test
  public void testGetProperty_repeatedCallsSameName_idempotent() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    Object p1 = dp.getProperty("x");
    Object p2 = dp.getProperty("x");
    Object p3 = dp.getProperty("x");
    assertSame(p1, p2);
    assertSame(p2, p3);
  }

  // getProperty() กับชื่อว่าง ไม่ควร throw และต้องคืนค่าไม่เป็น null
  @Test
  public void testGetProperty_emptyStringName_doesNotThrow() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    Object p = dp.getProperty("");
    assertNotNull(p);
  }

  // getRenamedTypesForTesting() บน instance ใหม่ ยังไม่มีการเรียก process ต้องว่างเปล่า
  @Test
  public void testGetRenamedTypesForTesting_freshInstance_isEmpty() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    Multimap<String, Collection<JSType>> result = dp.getRenamedTypesForTesting();
    assertTrue(result.isEmpty());
  }

  // getRenamedTypesForTesting() หลังจากเรียก getProperty หลายครั้งแต่ไม่มี type ถูกบันทึก ต้องยังว่างเปล่า
  @Test
  public void testGetRenamedTypesForTesting_afterGetProperty_isStillEmpty() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    dp.getProperty("a");
    dp.getProperty("b");
    Multimap<String, Collection<JSType>> result = dp.getRenamedTypesForTesting();
    assertTrue(result.isEmpty());
  }

  // renameProperties() บน properties ว่าง ต้องไม่ throw และไม่เปลี่ยนสถานะ
  @Test
  public void testRenameProperties_emptyProperties_doesNotThrow() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    dp.renameProperties();
    assertTrue(dp.getRenamedTypesForTesting().isEmpty());
  }

  // renameProperties() กับ properties ที่ถูกสร้างแต่ไม่มี type (shouldRename==false) ต้องไม่ rename อะไร
  @Test
  public void testRenameProperties_withUnrenamedProperties_doesNotThrow() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    dp.getProperty("a");
    dp.getProperty("b");
    dp.getProperty("c");
    dp.renameProperties();
    assertTrue(dp.getRenamedTypesForTesting().isEmpty());
  }

  // getTypeWithProperty() กับ type เป็น null ต้องคืน null ตามเงื่อนไขแรกสุดของสัญญา
  @Test
  public void testGetTypeWithProperty_nullType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSType result = dp.getTypeWithProperty("foo", null);
    assertNull(result);
  }

  // getTypeWithProperty() กับ type เป็น null และ field เป็นสตริงว่าง ต้องคืน null
  @Test
  public void testGetTypeWithProperty_nullType_emptyFieldName_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSType result = dp.getTypeWithProperty("", null);
    assertNull(result);
  }

  // getTypeWithProperty() กับ type เป็น null และ field เป็น unicode ต้องคืน null
  @Test
  public void testGetTypeWithProperty_nullType_unicodeFieldName_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSType result = dp.getTypeWithProperty("\u30d7\u30ed\u30d1\u30c6\u30a3", null);
    assertNull(result);
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (ALL_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onAllType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.ALL_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (OBJECT_PROTOTYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onObjectPrototypeType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (UNKNOWN_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onUnknownType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (NO_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onNoType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.NO_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (NO_OBJECT_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onNoObjectType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (FUNCTION_INSTANCE_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onFunctionInstanceType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (FUNCTION_PROTOTYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onFunctionPrototypeType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (TOP_LEVEL_PROTOTYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onTopLevelPrototypeType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (BOOLEAN_OBJECT_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onBooleanObjectType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (NUMBER_OBJECT_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onNumberObjectType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.NUMBER_OBJECT_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // getTypeWithProperty() กับ field "prototype" ต้องคืน null เสมอ (STRING_OBJECT_TYPE)
  @Test
  public void testGetTypeWithProperty_prototypeField_onStringObjectType_returnsNull() throws Throwable {
    DisambiguateProperties<JSType> dp =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.getNativeType(JSTypeNative.STRING_OBJECT_TYPE);
    assertNull(dp.getTypeWithProperty("prototype", type));
  }

  // สอง instance ของ DisambiguateProperties ที่สร้างแยกกันต้องมีสถานะ properties เป็นอิสระต่อกัน
  @Test
  public void testTwoInstances_independentPropertyState() throws Throwable {
    DisambiguateProperties<JSType> dp1 =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    DisambiguateProperties<JSType> dp2 =
        DisambiguateProperties.forJSTypeSystem(compiler, emptyMap);
    dp1.getProperty("shared");
    assertTrue(dp2.getRenamedTypesForTesting().isEmpty());
    assertTrue(dp1.getRenamedTypesForTesting().isEmpty());
    assertFalse(dp1 == dp2);
  }
}
