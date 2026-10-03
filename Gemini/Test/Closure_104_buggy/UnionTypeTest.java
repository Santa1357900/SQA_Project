package com.google.javascript.rhino.jstype;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class UnionTypeTest {

  @Test
  public void testUnionTypeCreationAndAlternates() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    
    alternates.add(numberType);
    alternates.add(stringType);

    UnionType unionType = new UnionType(registry, alternates);
    
    assertTrue(unionType.isUnionType());
    assertNotNull(unionType.getAlternates());
    assertTrue(unionType.contains(numberType));
    assertTrue(unionType.contains(stringType));
    assertEquals(2, unionType.hashCode());
  }

  @Test
  public void testContextPredicates() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType voidType = registry.getNativeType(JSTypeNative.VOID_TYPE);
    
    alternates.add(numberType);
    alternates.add(voidType);

    UnionType unionType = new UnionType(registry, alternates);

    assertTrue(unionType.matchesNumberContext());
    assertTrue(unionType.matchesStringContext());
    assertTrue(unionType.matchesObjectContext());
    assertTrue(unionType.isNullable());
    assertFalse(unionType.isUnknownType());
  }

  @Test
  public void testCanAssignAndCanBeCalled() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    alternates.add(numberType);

    UnionType unionType = new UnionType(registry, alternates);

    assertTrue(unionType.canAssignTo(numberType));
    assertFalse(unionType.canBeCalled());
    assertFalse(unionType.isObject());
  }

  @Test
  public void testEqualsAndHashCode() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates1 = new HashSet<JSType>();
    Set<JSType> alternates2 = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    alternates1.add(numberType);
    alternates2.add(numberType);

    UnionType union1 = new UnionType(registry, alternates1);
    UnionType union2 = new UnionType(registry, alternates2);
    Object notAUnion = new Object();

    assertTrue(union1.equals(union2));
    assertFalse(union1.equals(notAUnion));
    assertFalse(union1.equals(null));
    assertEquals(union1.hashCode(), union2.hashCode());
  }

  @Test
  public void testToString() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    
    alternates.add(numberType);
    alternates.add(stringType);

    UnionType unionType = new UnionType(registry, alternates);
    String str = unionType.toString();
    assertNotNull(str);
    assertTrue(str.startsWith("("));
    assertTrue(str.endsWith(")"));
  }

  @Test
  public void testVisitorAndRestrictions() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    alternates.add(numberType);

    UnionType unionType = new UnionType(registry, alternates);

    Visitor<Boolean> dummyVisitor = new Visitor<Boolean>() {
      public Boolean caseNoObjectType(NoObjectType t) { return false; }
      public Boolean caseAllType(AllType t) { return false; }
      public Boolean caseBooleanType(BooleanType t) { return false; }
      public Boolean caseEnumElementType(EnumElementType t) { return false; }
      public Boolean caseFunctionType(FunctionType t) { return false; }
      public Boolean caseNoType(NoType t) { return false; }
      public Boolean caseNullType(NullType t) { return false; }
      public Boolean caseNumberType(NumberType t) { return false; }
      public Boolean caseStringType(StringType t) { return false; }
      public Boolean caseUnknownType(UnknownType t) { return false; }
      public Boolean caseVoidType(VoidType t) { return false; }
      public Boolean caseUnionType(UnionType t) { return true; }
      public Boolean caseParameterizedType(ParameterizedType t) { return false; }
      public Boolean caseTemplateType(TemplateType t) { return false; }
      public Boolean caseNamedType(NamedType t) { return false; }
      public Boolean caseRecordType(RecordType t) { return false; }
      public Boolean caseEnumType(EnumType t) { return false; }
    };

    assertTrue(unionType.visit(dummyVisitor));
    assertNotNull(unionType.restrictByNotNullOrUndefined());
    assertNotNull(unionType.getRestrictedUnion(numberType));
    assertNotNull(unionType.getRestrictedTypeGivenToBooleanOutcome(true));
    assertNotNull(unionType.getPossibleToBooleanOutcomes());
  }

  @Test
  public void testEqualityAndInequalityOperations() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    alternates.add(numberType);

    UnionType unionType = new UnionType(registry, alternates);

    assertNotNull(unionType.testForEquality(numberType));
    assertNotNull(unionType.getTypesUnderEquality(numberType));
    assertNotNull(unionType.getTypesUnderInequality(numberType));
    assertNotNull(unionType.getTypesUnderShallowInequality(numberType));
  }
  
  @Test
  public void testForgiveUnknownNames() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Set<JSType> alternates = new HashSet<JSType>();
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    alternates.add(numberType);

    UnionType unionType = new UnionType(registry, alternates);
    unionType.forgiveUnknownNames();
    assertTrue(unionType.contains(numberType));
  }
}