package com.google.javascript.rhino.jstype;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.RecordTypeBuilder.RecordProperty;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecordTypeTest {

  @Test
  public void testRecordTypeCreationAndFrozen() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Map<String, RecordProperty> properties = new HashMap<String, RecordProperty>();
    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    properties.put("prop1", new RecordProperty(stringType, null));

    RecordType recordType = new RecordType(registry, properties);
    assertNotNull(recordType);
    assertTrue(recordType.isRecordType());
    assertFalse(recordType.defineProperty("prop2", stringType, false, null));
  }

  @Test
  public void testRecordPropertyNullThrowsException() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Map<String, RecordProperty> properties = new HashMap<String, RecordProperty>();
    properties.put("prop1", null);

    try {
      new RecordType(registry, properties);
      fail("Expected IllegalStateException when RecordProperty is null");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("RecordProperty associated with a property should not be null!"));
    }
  }

  @Test
  public void testIsEquivalentTo() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Map<String, RecordProperty> props1 = new HashMap<String, RecordProperty>();
    Map<String, RecordProperty> props2 = new HashMap<String, RecordProperty>();
    Map<String, RecordProperty> props3 = new HashMap<String, RecordProperty>();

    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);

    props1.put("a", new RecordProperty(stringType, null));
    props2.put("a", new RecordProperty(stringType, null));
    props3.put("a", new RecordProperty(numberType, null));

    RecordType record1 = new RecordType(registry, props1);
    RecordType record2 = new RecordType(registry, props2);
    RecordType record3 = new RecordType(registry, props3);

    assertTrue(record1.isEquivalentTo(record1));
    assertTrue(record1.isEquivalentTo(record2));
    assertFalse(record1.isEquivalentTo(record3));
    assertFalse(record1.isEquivalentTo(stringType));
  }

  @Test
  public void testGetImplicitPrototype() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Map<String, RecordProperty> props = new HashMap<String, RecordProperty>();
    props.put("a", new RecordProperty(registry.getNativeType(JSTypeNative.STRING_TYPE), null));

    RecordType recordType = new RecordType(registry, props);
    ObjectType prototype = recordType.getImplicitPrototype();
    assertNotNull(prototype);
  }

  @Test
  public void testGetLeastSupertype() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Map<String, RecordProperty> props1 = new HashMap<String, RecordProperty>();
    Map<String, RecordProperty> props2 = new HashMap<String, RecordProperty>();

    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    props1.put("a", new RecordProperty(stringType, null));
    props1.put("b", new RecordProperty(stringType, null));

    props2.put("a", new RecordProperty(stringType, null));

    RecordType record1 = new RecordType(registry, props1);
    RecordType record2 = new RecordType(registry, props2);

    JSType superType = record1.getLeastSupertype(record2);
    assertNotNull(superType);
    
    JSType nonRecord = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType superTypeNonRecord = record1.getLeastSupertype(nonRecord);
    assertNotNull(superTypeNonRecord);
  }

  @Test
  public void testGetGreatestSubtypeHelper() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Map<String, RecordProperty> props1 = new HashMap<String, RecordProperty>();
    Map<String, RecordProperty> props2 = new HashMap<String, RecordProperty>();
    Map<String, RecordProperty> props3 = new HashMap<String, RecordProperty>();

    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);

    props1.put("a", new RecordProperty(stringType, null));
    props2.put("a", new RecordProperty(stringType, null));
    props2.put("b", new RecordProperty(numberType, null));
    
    props3.put("a", new RecordProperty(numberType, null)); // Conflict on 'a'

    RecordType record1 = new RecordType(registry, props1);
    RecordType record2 = new RecordType(registry, props2);
    RecordType record3 = new RecordType(registry, props3);

    JSType greatestSub = record1.getGreatestSubtypeHelper(record2);
    assertNotNull(greatestSub);

    JSType conflictSub = record1.getGreatestSubtypeHelper(record3);
    assertEquals(registry.getNativeObjectType(JSTypeNative.NO_TYPE), conflictSub);

    JSType nonRecord = registry.getNativeType(JSTypeNative.OBJECT_TYPE);
    JSType greatestSubNonRecord = record1.getGreatestSubtypeHelper(nonRecord);
    assertNotNull(greatestSubNonRecord);
  }

  @Test
  public void testIsSubtype() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Map<String, RecordProperty> props1 = new HashMap<String, RecordProperty>();
    Map<String, RecordProperty> props2 = new HashMap<String, RecordProperty>();

    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    props1.put("a", new RecordProperty(stringType, null));
    props2.put("a", new RecordProperty(stringType, null));
    props2.put("b", new RecordProperty(stringType, null));

    RecordType record1 = new RecordType(registry, props1);
    RecordType record2 = new RecordType(registry, props2);

    assertTrue(record2.isSubtype(record1));
    assertFalse(record1.isSubtype(record2));

    JSType nonRecord = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    assertFalse(record1.isSubtype(nonRecord));
  }
}