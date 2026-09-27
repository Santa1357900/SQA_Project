package com.google.gson.internal.bind;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.*;

public class ReflectiveTypeAdapterFactoryTest {

  private static class SampleEntity {
    public int primitiveField = 42;
    public String stringField = "hello";

    @SerializedName(value = "customName", alternate = {"altName1", "altName2"})
    public String annotatedField = "annotated";
  }

  private static interface SampleInterface {
    void foo();
  }

  @Test
  public void testConstructorAndExcludeField() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, com.google.gson.internal.ObjectConstructor<?>>());
    FieldNamingPolicy fieldNamingPolicy = FieldNamingPolicy.IDENTITY;
    Excluder excluder = Excluder.DEFAULT;

    ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(
        constructorConstructor, fieldNamingPolicy, excluder);

    Field field = SampleEntity.class.getField("primitiveField");
    boolean excludeSerialize = factory.excludeField(field, true);
    boolean excludeDeserialize = factory.excludeField(field, false);

    assertFalse(excludeSerialize);
    assertFalse(excludeDeserialize);

    boolean staticExclude = ReflectiveTypeAdapterFactory.excludeField(field, true, excluder);
    assertFalse(staticExclude);
  }

  @Test
  public void testCreateWithPrimitiveOrNonObject() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, com.google.gson.internal.ObjectConstructor<?>>());
    ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(
        constructorConstructor, FieldNamingPolicy.IDENTITY, Excluder.DEFAULT);

    Gson gson = new Gson();
    TypeAdapter<Integer> adapter = factory.create(gson, TypeToken.get(int.class));
    assertNull(adapter);
  }

  @Test
  public void testCreateWithInterface() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, com.google.gson.internal.ObjectConstructor<?>>());
    ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(
        constructorConstructor, FieldNamingPolicy.IDENTITY, Excluder.DEFAULT);

    Gson gson = new Gson();
    TypeAdapter<SampleInterface> adapter = factory.create(gson, TypeToken.get(SampleInterface.class));
    assertNotNull(adapter);
    
    StringWriter sw = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(sw);
    adapter.write(jsonWriter, null);
    assertEquals("null", sw.toString());

    StringReader sr = new StringReader("null");
    JsonReader jsonReader = new JsonReader(sr);
    SampleInterface obj = adapter.read(jsonReader);
    assertNull(obj);
  }

  @Test
  public void testAdapterSerializeAndDeserialize() throws Throwable {
    Gson gson = new GsonBuilder().create();
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, com.google.gson.internal.ObjectConstructor<?>>());
    ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(
        constructorConstructor, FieldNamingPolicy.IDENTITY, Excluder.DEFAULT);

    @SuppressWarnings("unchecked")
    TypeAdapter<SampleEntity> adapter = (TypeAdapter<SampleEntity>) factory.create(gson, TypeToken.get(SampleEntity.class));
    assertNotNull(adapter);

    SampleEntity entity = new SampleEntity();
    entity.primitiveField = 100;
    entity.stringField = "testString";
    entity.annotatedField = "customVal";

    String json = gson.toJson(entity, SampleEntity.class);
    assertTrue(json.contains("\"primitiveField\":100"));
    assertTrue(json.contains("\"stringField\":\"testString\""));
    assertTrue(json.contains("\"customName\":\"customVal\""));

    SampleEntity deserialized = gson.fromJson(json, SampleEntity.class);
    assertNotNull(deserialized);
    assertEquals(100, deserialized.primitiveField);
    assertEquals("testString", deserialized.stringField);
    assertEquals("customVal", deserialized.annotatedField);
  }

  @Test
  public void testAdapterReadNull() throws Throwable {
    Gson gson = new Gson();
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, com.google.gson.internal.ObjectConstructor<?>>());
    ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(
        constructorConstructor, FieldNamingPolicy.IDENTITY, Excluder.DEFAULT);

    @SuppressWarnings("unchecked")
    TypeAdapter<SampleEntity> adapter = (TypeAdapter<SampleEntity>) factory.create(gson, TypeToken.get(SampleEntity.class));

    StringReader sr = new StringReader("null");
    JsonReader jsonReader = new JsonReader(sr);
    SampleEntity result = adapter.read(jsonReader);
    assertNull(result);
  }

  @Test
  public void testAdapterSerializeNullValue() throws Throwable {
    Gson gson = new Gson();
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, com.google.gson.internal.ObjectConstructor<?>>());
    ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(
        constructorConstructor, FieldNamingPolicy.IDENTITY, Excluder.DEFAULT);

    @SuppressWarnings("unchecked")
    TypeAdapter<SampleEntity> adapter = (TypeAdapter<SampleEntity>) factory.create(gson, TypeToken.get(SampleEntity.class));

    StringWriter sw = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(sw);
    adapter.write(jsonWriter, null);
    assertEquals("null", sw.toString());
  }

  @Test
  public void testDuplicateFieldNamesException() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap<Type, com.google.gson.internal.ObjectConstructor<?>>());
    ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(
        constructorConstructor, FieldNamingPolicy.IDENTITY, Excluder.DEFAULT);

    class DuplicateFieldEntity {
      @SerializedName("sameName")
      public int field1 = 1;

      @SerializedName("sameName")
      public int field2 = 2;
    }

    Gson gson = new Gson();
    try {
      factory.create(gson, TypeToken.get(DuplicateFieldEntity.class));
      fail("Expected IllegalArgumentException due to duplicate field names");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("declares multiple JSON fields named sameName"));
    }
  }

  @Test
  public void testAlternateNamesDeserialization() throws Throwable {
    Gson gson = new Gson();
    String jsonAlt1 = "{\"altName1\":\"val1\"}";
    SampleEntity obj1 = gson.fromJson(jsonAlt1, SampleEntity.class);
    assertEquals("val1", obj1.annotatedField);

    String jsonAlt2 = "{\"altName2\":\"val2\"}";
    SampleEntity obj2 = gson.fromJson(jsonAlt2, SampleEntity.class);
    assertEquals("val2", obj2.annotatedField);
  }
}