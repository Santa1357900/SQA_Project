package com.google.gson.internal;

import static org.junit.Assert.*;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.InstanceCreator;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

public class ConstructorConstructorClaudeTest {

  enum Color { RED, GREEN, BLUE }

  static class NoArgClass {
    public int value = 5;
    public NoArgClass() {
    }
  }

  static class PrivateNoArgClass {
    public String marker;
    private PrivateNoArgClass() {
      marker = "set";
    }
  }

  static class ThrowingCtorClass {
    public ThrowingCtorClass() {
      throw new IllegalStateException("boom");
    }
  }

  abstract static class AbstractWithCtor {
    public AbstractWithCtor() {
    }
  }

  static class RequiresArgClass {
    public final int number;
    public RequiresArgClass(int number) {
      this.number = number;
    }
  }

  // covers get(): exact-type instance creator match branch
  @Test
  public void testGet_exactTypeInstanceCreator_usesCreator() throws Throwable {
    Map<Type, InstanceCreator<?>> creators = new HashMap<Type, InstanceCreator<?>>();
    creators.put(String.class, new InstanceCreator<String>() {
      public String createInstance(Type type) {
        return "created-by-creator";
      }
    });
    ConstructorConstructor cc = new ConstructorConstructor(creators);
    ObjectConstructor<String> ctor = cc.get(TypeToken.get(String.class));
    assertEquals("created-by-creator", ctor.construct());
  }

  // covers get(): raw-type instance creator fallback branch when exact type not registered
  @Test
  public void testGet_rawTypeInstanceCreator_usesCreator() throws Throwable {
    Map<Type, InstanceCreator<?>> creators = new HashMap<Type, InstanceCreator<?>>();
    creators.put(List.class, new InstanceCreator<List<String>>() {
      public List<String> createInstance(Type type) {
        List<String> l = new ArrayList<String>();
        l.add("marker");
        return l;
      }
    });
    ConstructorConstructor cc = new ConstructorConstructor(creators);
    TypeToken<List<String>> tt = new TypeToken<List<String>>() {};
    ObjectConstructor<List<String>> ctor = cc.get(tt);
    List<String> result = ctor.construct();
    assertEquals(1, result.size());
    assertEquals("marker", result.get(0));
  }

  // covers newDefaultConstructor(): public no-arg constructor is invoked directly
  @Test
  public void testGet_publicNoArgConstructor_invokesConstructor() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<NoArgClass> ctor = cc.get(TypeToken.get(NoArgClass.class));
    NoArgClass instance = ctor.construct();
    assertEquals(5, instance.value);
  }

  // covers newDefaultConstructor(): private constructor made accessible then invoked
  @Test
  public void testGet_privateNoArgConstructor_setsAccessibleAndInvokes() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<PrivateNoArgClass> ctor = cc.get(TypeToken.get(PrivateNoArgClass.class));
    PrivateNoArgClass instance = ctor.construct();
    assertEquals("set", instance.marker);
  }

  // covers newDefaultConstructor() construct(): InvocationTargetException wraps original cause
  @Test
  public void testConstruct_constructorThrows_wrapsCauseInRuntimeException() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<ThrowingCtorClass> ctor = cc.get(TypeToken.get(ThrowingCtorClass.class));
    try {
      ctor.construct();
      fail("expected RuntimeException");
    } catch (RuntimeException expected) {
      assertTrue(expected.getCause() instanceof IllegalStateException);
    }
  }

  // covers newDefaultConstructor() construct(): InstantiationException on abstract class wrapped
  @Test
  public void testConstruct_abstractClassWithCtor_wrapsInstantiationException() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<AbstractWithCtor> ctor = cc.get(TypeToken.get(AbstractWithCtor.class));
    try {
      ctor.construct();
      fail("expected RuntimeException");
    } catch (RuntimeException expected) {
      assertTrue(expected.getCause() instanceof InstantiationException);
    }
  }

  // covers newDefaultImplementationConstructor(): SortedSet branch -> TreeSet
  @Test
  public void testGet_sortedSetInterface_createsTreeSet() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SortedSet> ctor = cc.get(TypeToken.get(SortedSet.class));
    Object result = ctor.construct();
    assertTrue(result instanceof TreeSet);
  }

  // covers newDefaultImplementationConstructor(): EnumSet branch with valid Class type argument
  @Test
  public void testGet_enumSetWithClassArg_createsEmptyEnumSet() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    TypeToken<EnumSet<Color>> tt = new TypeToken<EnumSet<Color>>() {};
    ObjectConstructor<EnumSet<Color>> ctor = cc.get(tt);
    EnumSet<Color> result = ctor.construct();
    assertTrue(result.isEmpty());
  }

  // covers newDefaultImplementationConstructor(): EnumSet branch, type not ParameterizedType -> throws
  @Test
  public void testGet_enumSetRawType_throwsJsonIOException() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<EnumSet> ctor = cc.get(TypeToken.get(EnumSet.class));
    try {
      ctor.construct();
      fail("expected JsonIOException");
    } catch (JsonIOException expected) {
      assertTrue(expected.getMessage().contains("Invalid EnumSet type"));
    }
  }

  // covers newDefaultImplementationConstructor(): EnumSet branch, type argument not a Class -> throws
  @Test
  public void testGet_enumSetWildcardArg_throwsJsonIOException() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    TypeToken<EnumSet<?>> tt = new TypeToken<EnumSet<?>>() {};
    ObjectConstructor<EnumSet<?>> ctor = cc.get(tt);
    try {
      ctor.construct();
      fail("expected JsonIOException");
    } catch (JsonIOException expected) {
      assertTrue(expected.getMessage().contains("Invalid EnumSet type"));
    }
  }

  // covers newDefaultImplementationConstructor(): generic Set branch -> LinkedHashSet
  @Test
  public void testGet_setInterface_createsLinkedHashSet() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Set> ctor = cc.get(TypeToken.get(Set.class));
    Object result = ctor.construct();
    assertTrue(result instanceof LinkedHashSet);
  }

  // covers newDefaultImplementationConstructor(): Queue branch -> LinkedList
  @Test
  public void testGet_queueInterface_createsLinkedList() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Queue> ctor = cc.get(TypeToken.get(Queue.class));
    Object result = ctor.construct();
    assertTrue(result instanceof LinkedList);
  }

  // covers newDefaultImplementationConstructor(): default Collection branch (List) -> ArrayList
  @Test
  public void testGet_listInterface_createsArrayList() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<List> ctor = cc.get(TypeToken.get(List.class));
    Object result = ctor.construct();
    assertTrue(result instanceof ArrayList);
  }

  // covers newDefaultImplementationConstructor(): Collection interface itself -> ArrayList
  @Test
  public void testGet_collectionInterface_createsArrayList() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Collection> ctor = cc.get(TypeToken.get(Collection.class));
    Object result = ctor.construct();
    assertTrue(result instanceof ArrayList);
  }

  // covers newDefaultImplementationConstructor(): SortedMap branch -> TreeMap
  @Test
  public void testGet_sortedMapInterface_createsTreeMap() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SortedMap> ctor = cc.get(TypeToken.get(SortedMap.class));
    Object result = ctor.construct();
    assertTrue(result instanceof TreeMap);
  }

  // covers newDefaultImplementationConstructor(): parameterized Map with non-String key -> LinkedHashMap
  @Test
  public void testGet_mapWithNonStringKey_createsLinkedHashMap() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    TypeToken<Map<Integer, String>> tt = new TypeToken<Map<Integer, String>>() {};
    ObjectConstructor<Map<Integer, String>> ctor = cc.get(tt);
    Map<Integer, String> result = ctor.construct();
    assertTrue(result instanceof LinkedHashMap);
  }

  // covers newDefaultImplementationConstructor(): parameterized Map with String key -> LinkedTreeMap
  @Test
  public void testGet_mapWithStringKey_createsLinkedTreeMap() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    TypeToken<Map<String, String>> tt = new TypeToken<Map<String, String>>() {};
    ObjectConstructor<Map<String, String>> ctor = cc.get(tt);
    Map<String, String> result = ctor.construct();
    assertTrue(result instanceof LinkedTreeMap);
  }

  // covers newDefaultImplementationConstructor(): raw Map type (not ParameterizedType) -> LinkedTreeMap
  @Test
  public void testGet_rawMapType_createsLinkedTreeMap() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Map> ctor = cc.get(TypeToken.get(Map.class));
    Object result = ctor.construct();
    assertTrue(result instanceof LinkedTreeMap);
  }

  // covers newUnsafeAllocator(): class without no-arg constructor falls back to unsafe allocation
  @Test
  public void testGet_classWithoutNoArgConstructor_usesUnsafeAllocator() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<RequiresArgClass> ctor = cc.get(TypeToken.get(RequiresArgClass.class));
    RequiresArgClass result = ctor.construct();
    assertNotNull(result);
    assertEquals(0, result.number);
  }

  // covers toString(): delegates to instanceCreators map toString for empty map
  @Test
  public void testToString_emptyCreators_returnsEmptyBraces() throws Throwable {
    ConstructorConstructor cc = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    assertEquals("{}", cc.toString());
  }

  // covers toString(): delegates to instanceCreators map toString for single-entry map
  @Test
  public void testToString_singleCreator_matchesMapToString() throws Throwable {
    Map<Type, InstanceCreator<?>> creators = new HashMap<Type, InstanceCreator<?>>();
    InstanceCreator<String> creator = new InstanceCreator<String>() {
      public String createInstance(Type type) {
        return "x";
      }
    };
    creators.put(String.class, creator);
    ConstructorConstructor cc = new ConstructorConstructor(creators);
    assertEquals(creators.toString(), cc.toString());
  }

  // covers get(): exact-type instance creator takes priority over default no-arg constructor
  @Test
  public void testGet_instanceCreatorOverridesDefaultConstructor() throws Throwable {
    Map<Type, InstanceCreator<?>> creators = new HashMap<Type, InstanceCreator<?>>();
    creators.put(NoArgClass.class, new InstanceCreator<NoArgClass>() {
      public NoArgClass createInstance(Type type) {
        NoArgClass n = new NoArgClass();
        n.value = 99;
        return n;
      }
    });
    ConstructorConstructor cc = new ConstructorConstructor(creators);
    ObjectConstructor<NoArgClass> ctor = cc.get(TypeToken.get(NoArgClass.class));
    NoArgClass result = ctor.construct();
    assertEquals(99, result.value);
  }
}
