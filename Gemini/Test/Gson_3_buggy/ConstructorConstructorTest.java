package com.google.gson.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.ParameterizedType;
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

public class ConstructorConstructorTest {

  // Dummy class with a default constructor
  public static class SampleWithDefaultConstructor {
    public boolean initialized = true;
  }

  // Dummy class without a default constructor
  public static class SampleWithoutDefaultConstructor {
    private final String value;
    public SampleWithoutDefaultConstructor(String value) {
      this.value = value;
    }
  }

  // Dummy class throwing exception in default constructor
  public static class SampleThrowingConstructor {
    public SampleThrowingConstructor() {
      throw new RuntimeException("Expected exception");
    }
  }

  // Dummy class with private default constructor
  public static class SamplePrivateConstructor {
    private SamplePrivateConstructor() {}
  }

  @Test
  public void testInstanceCreatorByType() throws Throwable {
    Map<Type, InstanceCreator<?>> creators = new HashMap<Type, InstanceCreator<?>>();
    creators.put(SampleWithoutDefaultConstructor.class, new InstanceCreator<SampleWithoutDefaultConstructor>() {
      @Override
      public SampleWithoutDefaultConstructor createInstance(Type type) {
        return new SampleWithoutDefaultConstructor("custom");
      }
    });

    ConstructorConstructor constructorConstructor = new ConstructorConstructor(creators);
    ObjectConstructor<SampleWithoutDefaultConstructor> objectConstructor = 
        constructorConstructor.get(TypeToken.get(SampleWithoutDefaultConstructor.class));

    assertNotNull(objectConstructor);
    SampleWithoutDefaultConstructor instance = objectConstructor.construct();
    assertNotNull(instance);
    assertEquals("custom", instance.value);
  }

  @Test
  public void testInstanceCreatorByRawType() throws Throwable {
    Map<Type, InstanceCreator<?>> creators = new HashMap<Type, InstanceCreator<?>>();
    creators.put(SampleWithDefaultConstructor.class, new InstanceCreator<SampleWithDefaultConstructor>() {
      @Override
      public SampleWithDefaultConstructor createInstance(Type type) {
        SampleWithDefaultConstructor obj = new SampleWithDefaultConstructor();
        obj.initialized = false;
        return obj;
      }
    });

    ConstructorConstructor constructorConstructor = new ConstructorConstructor(creators);
    ObjectConstructor<SampleWithDefaultConstructor> objectConstructor = 
        constructorConstructor.get(TypeToken.get(SampleWithDefaultConstructor.class));

    assertNotNull(objectConstructor);
    SampleWithDefaultConstructor instance = objectConstructor.construct();
    assertNotNull(instance);
    assertFalse(instance.initialized);
  }

  @Test
  public void testDefaultConstructorPublic() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SampleWithDefaultConstructor> objectConstructor = 
        constructorConstructor.get(TypeToken.get(SampleWithDefaultConstructor.class));

    assertNotNull(objectConstructor);
    SampleWithDefaultConstructor instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance.initialized);
  }

  @Test
  public void testDefaultConstructorPrivate() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SamplePrivateConstructor> objectConstructor = 
        constructorConstructor.get(TypeToken.get(SamplePrivateConstructor.class));

    assertNotNull(objectConstructor);
    SamplePrivateConstructor instance = objectConstructor.construct();
    assertNotNull(instance);
  }

  @Test
  public void testDefaultConstructorThrowingException() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SampleThrowingConstructor> objectConstructor = 
        constructorConstructor.get(TypeToken.get(SampleThrowingConstructor.class));

    assertNotNull(objectConstructor);
    try {
      objectConstructor.construct();
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("Failed to invoke"));
    }
  }

  @Test
  public void testCollectionsCollection() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Collection> objectConstructor = 
        constructorConstructor.get(new TypeToken<Collection<String>>() {});

    assertNotNull(objectConstructor);
    Collection instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance instanceof ArrayList);
  }

  @Test
  public void testCollectionsSortedSet() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SortedSet> objectConstructor = 
        constructorConstructor.get(new TypeToken<SortedSet<String>>() {});

    assertNotNull(objectConstructor);
    SortedSet instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance instanceof TreeSet);
  }

  @Test
  public void testCollectionsEnumSetValid() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<EnumSet> objectConstructor = 
        constructorConstructor.get(new TypeToken<EnumSet<DummyEnum>>() {});

    assertNotNull(objectConstructor);
    EnumSet instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance.isEmpty());
  }

  private enum DummyEnum {
    A, B
  }

  @Test
  public void testCollectionsEnumSetInvalidNotParameterized() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    // Using raw EnumSet type instead of ParameterizedType
    ObjectConstructor<EnumSet> objectConstructor = 
        constructorConstructor.get(TypeToken.get(EnumSet.class));

    assertNotNull(objectConstructor);
    try {
      objectConstructor.construct();
      fail("Expected JsonIOException");
    } catch (JsonIOException e) {
      assertTrue(e.getMessage().contains("Invalid EnumSet type"));
    }
  }

  @Test
  public void testCollectionsSet() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Set> objectConstructor = 
        constructorConstructor.get(new TypeToken<Set<String>>() {});

    assertNotNull(objectConstructor);
    Set instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance instanceof LinkedHashSet);
  }

  @Test
  public void testCollectionsQueue() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Queue> objectConstructor = 
        constructorConstructor.get(new TypeToken<Queue<String>>() {});

    assertNotNull(objectConstructor);
    Queue instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance instanceof LinkedList);
  }

  @Test
  public void testMapsSortedMap() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SortedMap> objectConstructor = 
        constructorConstructor.get(new TypeToken<SortedMap<String, String>>() {});

    assertNotNull(objectConstructor);
    SortedMap instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance instanceof TreeMap);
  }

  @Test
  public void testMapsNonStringKeyParameterizedMap() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Map> objectConstructor = 
        constructorConstructor.get(new TypeToken<Map<Integer, String>>() {});

    assertNotNull(objectConstructor);
    Map instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance instanceof LinkedHashMap);
  }

  @Test
  public void testMapsDefaultMap() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<Map> objectConstructor = 
        constructorConstructor.get(new TypeToken<Map<String, String>>() {});

    assertNotNull(objectConstructor);
    Map instance = objectConstructor.construct();
    assertNotNull(instance);
    assertTrue(instance instanceof LinkedTreeMap);
  }

  @Test
  public void testUnsafeAllocatorFallback() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, InstanceCreator<?>>());
    ObjectConstructor<SampleWithoutDefaultConstructor> objectConstructor = 
        constructorConstructor.get(TypeToken.get(SampleWithoutDefaultConstructor.class));

    assertNotNull(objectConstructor);
    SampleWithoutDefaultConstructor instance = objectConstructor.construct();
    assertNotNull(instance);
  }

  @Test
  public void testToStringMethod() throws Throwable {
    Map<Type, InstanceCreator<?>> creators = new HashMap<Type, InstanceCreator<?>>();
    creators.put(String.class, new InstanceCreator<String>() {
      @Override
      public String createInstance(Type type) {
        return "test";
      }
    });

    ConstructorConstructor constructorConstructor = new ConstructorConstructor(creators);
    String str = constructorConstructor.toString();
    assertNotNull(str);
    assertTrue(str.contains("class java.lang.String"));
  }
}