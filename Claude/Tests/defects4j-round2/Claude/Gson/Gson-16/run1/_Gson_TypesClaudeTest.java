package com.google.gson.internal;

import static org.junit.Assert.*;
import org.junit.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import com.google.gson.reflect.TypeToken;

public class _Gson_TypesClaudeTest {

  static class StaticNested {
  }

  class NonStaticInner {
  }

  static class Box<T> {
    T value;
  }

  static class StringBox extends Box<String> {
  }

  static class Box2<E> {
  }

  // ownerType null + rawType top-level generic class => valid ParameterizedType with correct raw/args
  @Test
  public void testNewParameterizedTypeWithOwner_listOfString_rawTypeAndArgsCorrect() throws Throwable {
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertNull(pt.getOwnerType());
    assertEquals(List.class, pt.getRawType());
    assertEquals(1, pt.getActualTypeArguments().length);
    assertEquals(String.class, pt.getActualTypeArguments()[0]);
  }

  // isStaticOrTopLevelClass branch: static nested class with null owner succeeds
  @Test
  public void testNewParameterizedTypeWithOwner_staticNestedClassNoOwner_succeeds() throws Throwable {
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, StaticNested.class);
    assertEquals(StaticNested.class, pt.getRawType());
    assertNull(pt.getOwnerType());
  }

  // isStaticOrTopLevelClass branch: non-static inner class requires an owner type, else throws
  @Test
  public void testNewParameterizedTypeWithOwner_nonStaticInnerClassNoOwner_throwsIllegalArgumentException() throws Throwable {
    try {
      $Gson$Types.newParameterizedTypeWithOwner(null, NonStaticInner.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // toString formats as rawType<arg1, arg2>
  @Test
  public void testNewParameterizedTypeWithOwner_toString_formatsCorrectly() throws Throwable {
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals("java.util.List<java.lang.String>", pt.toString());
  }

  // arrayOf creates GenericArrayType wrapping componentType
  @Test
  public void testArrayOf_componentTypeString_genericArrayTypeCreated() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String.class, gat.getGenericComponentType());
  }

  // arrayOf toString appends []
  @Test
  public void testArrayOf_toString_appendsBrackets() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals("java.lang.String[]", gat.toString());
  }

  // subtypeOf with a Class bound sets upper bound, empty lower bounds
  @Test
  public void testSubtypeOf_classBound_upperBoundSetLowerEmpty() throws Throwable {
    WildcardType w = $Gson$Types.subtypeOf(String.class);
    assertEquals(1, w.getUpperBounds().length);
    assertEquals(String.class, w.getUpperBounds()[0]);
    assertEquals(0, w.getLowerBounds().length);
  }

  // subtypeOf(Object.class) toString shorthand "?"
  @Test
  public void testSubtypeOf_objectBound_toStringQuestionMark() throws Throwable {
    WildcardType w = $Gson$Types.subtypeOf(Object.class);
    assertEquals("?", w.toString());
  }

  // subtypeOf given a WildcardType bound reuses its upper bounds
  @Test
  public void testSubtypeOf_wildcardBound_usesWildcardUpperBounds() throws Throwable {
    WildcardType inner = $Gson$Types.subtypeOf(String.class);
    WildcardType outer = $Gson$Types.subtypeOf(inner);
    assertEquals(String.class, outer.getUpperBounds()[0]);
    assertEquals(0, outer.getLowerBounds().length);
  }

  // supertypeOf with a Class bound sets lower bound and upper bound Object
  @Test
  public void testSupertypeOf_classBound_lowerBoundSetUpperObject() throws Throwable {
    WildcardType w = $Gson$Types.supertypeOf(String.class);
    assertEquals(1, w.getLowerBounds().length);
    assertEquals(String.class, w.getLowerBounds()[0]);
    assertEquals(Object.class, w.getUpperBounds()[0]);
    assertEquals("? super java.lang.String", w.toString());
  }

  // supertypeOf given a WildcardType bound reuses its lower bounds
  @Test
  public void testSupertypeOf_wildcardBound_usesWildcardLowerBounds() throws Throwable {
    WildcardType inner = $Gson$Types.supertypeOf(String.class);
    WildcardType outer = $Gson$Types.supertypeOf(inner);
    assertEquals(String.class, outer.getLowerBounds()[0]);
    assertEquals(Object.class, outer.getUpperBounds()[0]);
  }

  // canonicalize of a non-array Class returns the same Class reference
  @Test
  public void testCanonicalize_classType_returnsSameClass() throws Throwable {
    Type canon = $Gson$Types.canonicalize(String.class);
    assertSame(String.class, canon);
  }

  // canonicalize of an array Class returns a GenericArrayType with the array's component type
  @Test
  public void testCanonicalize_arrayClassType_returnsGenericArrayType() throws Throwable {
    Type canon = $Gson$Types.canonicalize(String[].class);
    assertTrue(canon instanceof GenericArrayType);
    assertEquals(String.class, ((GenericArrayType) canon).getGenericComponentType());
  }

  // canonicalize of a ParameterizedType returns a functionally equal but new instance
  @Test
  public void testCanonicalize_parameterizedType_functionallyEqual() throws Throwable {
    Type original = new TypeToken<List<String>>() {}.getType();
    Type canon = $Gson$Types.canonicalize(original);
    assertTrue(canon instanceof ParameterizedType);
    assertTrue($Gson$Types.equals(original, canon));
  }

  // canonicalize of a GenericArrayType (non-Class) returns new but functionally equal instance
  @Test
  public void testCanonicalize_genericArrayType_functionallyEqual() throws Throwable {
    GenericArrayType original = $Gson$Types.arrayOf(String.class);
    Type canon = $Gson$Types.canonicalize(original);
    assertNotSame(original, canon);
    assertTrue($Gson$Types.equals(original, canon));
  }

  // canonicalize of a WildcardType returns new but functionally equal instance
  @Test
  public void testCanonicalize_wildcardType_functionallyEqual() throws Throwable {
    WildcardType original = $Gson$Types.subtypeOf(String.class);
    Type canon = $Gson$Types.canonicalize(original);
    assertNotSame(original, canon);
    assertTrue($Gson$Types.equals(original, canon));
  }

  // canonicalize of a TypeVariable (unsupported) returns the same reference unchanged
  @Test
  public void testCanonicalize_typeVariable_returnsSameReference() throws Throwable {
    TypeVariable<?> tv = Box.class.getTypeParameters()[0];
    Type canon = $Gson$Types.canonicalize(tv);
    assertSame(tv, canon);
  }

  // getRawType of a plain Class returns the class itself
  @Test
  public void testGetRawType_class_returnsClassItself() throws Throwable {
    assertEquals(String.class, $Gson$Types.getRawType(String.class));
  }

  // getRawType of a ParameterizedType returns its raw Class
  @Test
  public void testGetRawType_parameterizedType_returnsRawClass() throws Throwable {
    Type pt = new TypeToken<List<String>>() {}.getType();
    assertEquals(List.class, $Gson$Types.getRawType(pt));
  }

  // getRawType of a GenericArrayType returns the corresponding array Class
  @Test
  public void testGetRawType_genericArrayType_returnsArrayClass() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String[].class, $Gson$Types.getRawType(gat));
  }

  // getRawType of a TypeVariable falls back to Object.class
  @Test
  public void testGetRawType_typeVariable_returnsObjectClass() throws Throwable {
    TypeVariable<?> tv = Box.class.getTypeParameters()[0];
    assertEquals(Object.class, $Gson$Types.getRawType(tv));
  }

  // getRawType of a WildcardType returns the raw class of its upper bound
  @Test
  public void testGetRawType_wildcardType_returnsUpperBoundClass() throws Throwable {
    WildcardType w = $Gson$Types.subtypeOf(String.class);
    assertEquals(String.class, $Gson$Types.getRawType(w));
  }

  // getRawType of an unsupported Type implementation throws IllegalArgumentException
  @Test
  public void testGetRawType_unsupportedType_throwsIllegalArgumentException() throws Throwable {
    Type custom = new Type() {
    };
    try {
      $Gson$Types.getRawType(custom);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Expected a Class"));
    }
  }

  // equals: identical reference returns true
  @Test
  public void testEquals_sameReference_true() throws Throwable {
    assertTrue($Gson$Types.equals(String.class, String.class));
  }

  // equals: two Class objects compared via Class.equals, equal and different cases
  @Test
  public void testEquals_classes_equalAndDifferent() throws Throwable {
    assertTrue($Gson$Types.equals(String.class, String.class));
    assertFalse($Gson$Types.equals(String.class, Integer.class));
    assertFalse($Gson$Types.equals(String.class, null));
  }

  // equals: ParameterizedTypes with same/different type arguments
  @Test
  public void testEquals_parameterizedTypes_equalAndDifferent() throws Throwable {
    Type listString1 = new TypeToken<List<String>>() {}.getType();
    Type listString2 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    Type listInteger = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, Integer.class);
    assertTrue($Gson$Types.equals(listString1, listString2));
    assertFalse($Gson$Types.equals(listString1, listInteger));
  }

  // equals: ParameterizedType vs non-ParameterizedType returns false
  @Test
  public void testEquals_parameterizedTypeVsClass_false() throws Throwable {
    Type listString = new TypeToken<List<String>>() {}.getType();
    assertFalse($Gson$Types.equals(listString, List.class));
  }

  // equals: GenericArrayTypes equal by component type, differ by component type
  @Test
  public void testEquals_genericArrayTypes_equalAndDifferent() throws Throwable {
    GenericArrayType g1 = $Gson$Types.arrayOf(String.class);
    GenericArrayType g2 = $Gson$Types.arrayOf(String.class);
    GenericArrayType g3 = $Gson$Types.arrayOf(Integer.class);
    assertTrue($Gson$Types.equals(g1, g2));
    assertFalse($Gson$Types.equals(g1, g3));
  }

  // equals: WildcardTypes equal by bounds, differ by bounds
  @Test
  public void testEquals_wildcardTypes_equalAndDifferent() throws Throwable {
    WildcardType w1 = $Gson$Types.subtypeOf(String.class);
    WildcardType w2 = $Gson$Types.subtypeOf(String.class);
    WildcardType w3 = $Gson$Types.subtypeOf(Integer.class);
    assertTrue($Gson$Types.equals(w1, w2));
    assertFalse($Gson$Types.equals(w1, w3));
  }

  // equals: TypeVariables with same declaration/name are equal, different declaration are not
  @Test
  public void testEquals_typeVariables_sameAndDifferentDeclaration() throws Throwable {
    TypeVariable<?> tv1 = Box.class.getTypeParameters()[0];
    TypeVariable<?> tv2 = Box.class.getTypeParameters()[0];
    TypeVariable<?> tv3 = Box2.class.getTypeParameters()[0];
    assertTrue($Gson$Types.equals(tv1, tv2));
    assertFalse($Gson$Types.equals(tv1, tv3));
  }

  // equals: unsupported Type implementations fall through to final else branch, returning false
  @Test
  public void testEquals_unsupportedTypePairs_false() throws Throwable {
    Type t1 = new Type() {
    };
    Type t2 = new Type() {
    };
    assertFalse($Gson$Types.equals(t1, t2));
  }

  // typeToString for a Class returns its fully qualified name
  @Test
  public void testTypeToString_classType_returnsClassName() throws Throwable {
    assertEquals("java.lang.String", $Gson$Types.typeToString(String.class));
  }

  // typeToString for non-Class delegates to the type's own toString()
  @Test
  public void testTypeToString_parameterizedType_usesToString() throws Throwable {
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals("java.util.List<java.lang.String>", $Gson$Types.typeToString(pt));
  }

  // getArrayComponentType for a Class array returns component via getComponentType
  @Test
  public void testGetArrayComponentType_classArray_returnsComponent() throws Throwable {
    assertEquals(String.class, $Gson$Types.getArrayComponentType(String[].class));
  }

  // getArrayComponentType for a GenericArrayType returns its generic component type
  @Test
  public void testGetArrayComponentType_genericArrayType_returnsComponent() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String.class, $Gson$Types.getArrayComponentType(gat));
  }

  // getArrayComponentType for a non-Class, non-GenericArrayType input throws ClassCastException
  @Test
  public void testGetArrayComponentType_parameterizedType_throwsClassCastException() throws Throwable {
    Type pt = new TypeToken<List<String>>() {}.getType();
    try {
      $Gson$Types.getArrayComponentType(pt);
      fail("expected ClassCastException");
    } catch (ClassCastException expected) {
    }
  }

  // getCollectionElementType resolves the element type from a parameterized Collection context
  @Test
  public void testGetCollectionElementType_parameterizedList_returnsElementType() throws Throwable {
    Type context = new TypeToken<ArrayList<String>>() {}.getType();
    Type elementType = $Gson$Types.getCollectionElementType(context, ArrayList.class);
    assertEquals(String.class, elementType);
  }

  // getMapKeyAndValueTypes special-cases Properties as String,String
  @Test
  public void testGetMapKeyAndValueTypes_propertiesSpecialCase_returnsStringString() throws Throwable {
    Type[] types = $Gson$Types.getMapKeyAndValueTypes(Properties.class, Properties.class);
    assertEquals(String.class, types[0]);
    assertEquals(String.class, types[1]);
  }

  // getMapKeyAndValueTypes resolves key/value types from a parameterized Map context
  @Test
  public void testGetMapKeyAndValueTypes_parameterizedMap_returnsKeyValueTypes() throws Throwable {
    Type context = new TypeToken<HashMap<String, Integer>>() {}.getType();
    Type[] types = $Gson$Types.getMapKeyAndValueTypes(context, HashMap.class);
    assertEquals(String.class, types[0]);
    assertEquals(Integer.class, types[1]);
  }

  // resolve: a TypeVariable declared on a generic superclass resolves to the concrete bound type
  @Test
  public void testResolve_typeVariableFromSuperclass_resolvesToConcreteType() throws Throwable {
    TypeVariable<?> tv = Box.class.getTypeParameters()[0];
    Type resolved = $Gson$Types.resolve(StringBox.class, StringBox.class, tv);
    assertEquals(String.class, resolved);
  }

  // resolve: a TypeVariable unrelated to the context hierarchy cannot be resolved, returns itself
  @Test
  public void testResolve_typeVariableUnrelatedToContext_returnsSameReference() throws Throwable {
    TypeVariable<?> tv2 = Box2.class.getTypeParameters()[0];
    Type resolved = $Gson$Types.resolve(StringBox.class, StringBox.class, tv2);
    assertSame(tv2, resolved);
  }

  // resolve: a Class array type with no variables inside returns the same instance unchanged
  @Test
  public void testResolve_arrayTypeNoVariables_returnsSameInstance() throws Throwable {
    Type resolved = $Gson$Types.resolve(Object.class, Object.class, String[].class);
    assertSame(String[].class, resolved);
  }

  // resolve: a GenericArrayType whose component is a type variable gets its component resolved
  @Test
  public void testResolve_genericArrayTypeWithVariable_resolvesComponent() throws Throwable {
    TypeVariable<?> tv = Box.class.getTypeParameters()[0];
    Type arrayOfVar = $Gson$Types.arrayOf(tv);
    Type resolved = $Gson$Types.resolve(StringBox.class, StringBox.class, arrayOfVar);
    assertTrue(resolved instanceof GenericArrayType);
    assertEquals(String.class, ((GenericArrayType) resolved).getGenericComponentType());
  }

  // resolve: a ParameterizedType whose type argument is a type variable gets the argument resolved
  @Test
  public void testResolve_parameterizedTypeWithVariable_resolvesArgument() throws Throwable {
    TypeVariable<?> tv = Box.class.getTypeParameters()[0];
    Type ptWithVar = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, tv);
    Type resolved = $Gson$Types.resolve(StringBox.class, StringBox.class, ptWithVar);
    assertTrue(resolved instanceof ParameterizedType);
    assertEquals(String.class, ((ParameterizedType) resolved).getActualTypeArguments()[0]);
  }

  // resolve: a WildcardType whose upper bound is a type variable gets the bound resolved
  @Test
  public void testResolve_wildcardTypeWithVariable_resolvesUpperBound() throws Throwable {
    TypeVariable<?> tv = Box.class.getTypeParameters()[0];
    Type wildcardWithVar = $Gson$Types.subtypeOf(tv);
    Type resolved = $Gson$Types.resolve(StringBox.class, StringBox.class, wildcardWithVar);
    assertTrue(resolved instanceof WildcardType);
    assertEquals(String.class, ((WildcardType) resolved).getUpperBounds()[0]);
  }

  // checkNotPrimitive throws IllegalArgumentException for a primitive Class type
  @Test
  public void testCheckNotPrimitive_primitiveType_throwsIllegalArgumentException() throws Throwable {
    try {
      $Gson$Types.checkNotPrimitive(int.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }
}
