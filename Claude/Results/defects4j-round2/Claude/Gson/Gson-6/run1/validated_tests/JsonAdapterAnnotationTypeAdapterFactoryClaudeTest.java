package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class JsonAdapterAnnotationTypeAdapterFactoryClaudeTest {

  // ---- support fixture classes ----

  public static class PlainClass {
    String value = "plain";
  }

  public static class CustomTypeAdapter extends TypeAdapter<AnnotatedWithTypeAdapter> {
    @Override
    public void write(JsonWriter out, AnnotatedWithTypeAdapter value) throws IOException {
      out.value("custom-written");
    }
    @Override
    public AnnotatedWithTypeAdapter read(JsonReader in) throws IOException {
      in.nextString();
      AnnotatedWithTypeAdapter result = new AnnotatedWithTypeAdapter();
      result.value = "custom-read";
      return result;
    }
  }

  @JsonAdapter(CustomTypeAdapter.class)
  public static class AnnotatedWithTypeAdapter {
    String value = "initial";
  }

  public static class Wrapper {
    AnnotatedWithTypeAdapter inner;
  }

  public static class FactoryProducedAdapter extends TypeAdapter<AnnotatedWithFactory> {
    @Override
    public void write(JsonWriter out, AnnotatedWithFactory value) throws IOException {
      out.value("factory-written");
    }
    @Override
    public AnnotatedWithFactory read(JsonReader in) throws IOException {
      in.nextString();
      AnnotatedWithFactory result = new AnnotatedWithFactory();
      result.value = "factory-read";
      return result;
    }
  }

  public static class WorkingFactory implements TypeAdapterFactory {
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      return (TypeAdapter<T>) new FactoryProducedAdapter();
    }
  }

  @JsonAdapter(WorkingFactory.class)
  public static class AnnotatedWithFactory {
    String value = "initial";
  }

  public static class WrapperFactory {
    AnnotatedWithFactory inner;
  }

  public static class NullReturningFactory implements TypeAdapterFactory {
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      return null;
    }
  }

  @JsonAdapter(NullReturningFactory.class)
  public static class AnnotatedWithNullFactory {
    String value = "initial";
  }

  @JsonAdapter(String.class)
  public static class AnnotatedWithInvalidValue {
    int number = 5;
  }

  @JsonAdapter(Object.class)
  public static class AnnotatedWithInvalidValue2 {
    int number = 5;
  }

  // ---- tests for create(): annotation == null branch ----

  // annotation == null -> factory declines, default reflective adapter is used instead
  @Test
  public void testCreate_noAnnotation_returnsWorkingDefaultAdapter() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<PlainClass> adapter = gson.getAdapter(PlainClass.class);
    assertNotNull(adapter);
    String json = gson.toJson(new PlainClass());
    assertTrue(json.contains("plain"));
  }

  // ---- tests for getTypeAdapter(): TypeAdapter.class.isAssignableFrom(value) branch ----

  @Test
  public void testCreate_typeAdapterAnnotation_usesCustomAdapterForWrite() throws Throwable {
    Gson gson = new Gson();
    String json = gson.toJson(new AnnotatedWithTypeAdapter());
    assertEquals("\"custom-written\"", json);
  }

  @Test
  public void testCreate_typeAdapterAnnotation_usesCustomAdapterForRead() throws Throwable {
    Gson gson = new Gson();
    AnnotatedWithTypeAdapter result = gson.fromJson("\"anything\"", AnnotatedWithTypeAdapter.class);
    assertEquals("custom-read", result.value);
  }

  @Test
  public void testCreate_typeAdapterAnnotation_roundTripPreservesCustomBehavior() throws Throwable {
    Gson gson = new Gson();
    String json = gson.toJson(new AnnotatedWithTypeAdapter());
    AnnotatedWithTypeAdapter result = gson.fromJson(json, AnnotatedWithTypeAdapter.class);
    assertEquals("custom-read", result.value);
  }

  // ---- tests for getTypeAdapter(): TypeAdapterFactory.class.isAssignableFrom(value) branch ----

  @Test
  public void testCreate_typeAdapterFactoryAnnotation_usesFactoryProducedAdapterForWrite() throws Throwable {
    Gson gson = new Gson();
    String json = gson.toJson(new AnnotatedWithFactory());
    assertEquals("\"factory-written\"", json);
  }

  @Test
  public void testCreate_typeAdapterFactoryAnnotation_usesFactoryProducedAdapterForRead() throws Throwable {
    Gson gson = new Gson();
    AnnotatedWithFactory result = gson.fromJson("\"anything\"", AnnotatedWithFactory.class);
    assertEquals("factory-read", result.value);
  }

  @Test
  public void testCreate_typeAdapterFactoryAnnotation_roundTripPreservesCustomBehavior() throws Throwable {
    Gson gson = new Gson();
    String json = gson.toJson(new AnnotatedWithFactory());
    AnnotatedWithFactory result = gson.fromJson(json, AnnotatedWithFactory.class);
    assertEquals("factory-read", result.value);
  }

  // ---- tests for getTypeAdapter(): else branch (throws IllegalArgumentException) ----

  @Test
  public void testCreate_invalidValueNotTypeAdapterOrFactory_throwsIllegalArgumentException() throws Throwable {
    Gson gson = new Gson();
    try {
      gson.getAdapter(AnnotatedWithInvalidValue.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("TypeAdapter"));
    }
  }

  @Test
  public void testCreate_invalidValueObjectClass_throwsIllegalArgumentException() throws Throwable {
    Gson gson = new Gson();
    try {
      gson.getAdapter(AnnotatedWithInvalidValue2.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("TypeAdapterFactory"));
    }
  }

  // ---- extra branch/behavior coverage for create() ----

  @Test
  public void testCreate_plainClassWithoutAnnotation_defaultAdapterIgnoresCustomLogic() throws Throwable {
    Gson gson = new Gson();
    PlainClass result = gson.fromJson("{\"value\":\"x\"}", PlainClass.class);
    assertEquals("x", result.value);
  }

  @Test
  public void testCreate_annotatedTypeInsideList_appliesAdapterToEachElement() throws Throwable {
    Gson gson = new Gson();
    List<AnnotatedWithTypeAdapter> list = new ArrayList<AnnotatedWithTypeAdapter>();
    list.add(new AnnotatedWithTypeAdapter());
    list.add(new AnnotatedWithTypeAdapter());
    String json = gson.toJson(list);
    assertEquals("[\"custom-written\",\"custom-written\"]", json);
  }

  @Test
  public void testCreate_annotatedFactoryTypeInsideList_appliesAdapterToEachElement() throws Throwable {
    Gson gson = new Gson();
    List<AnnotatedWithFactory> list = new ArrayList<AnnotatedWithFactory>();
    list.add(new AnnotatedWithFactory());
    String json = gson.toJson(list);
    assertEquals("[\"factory-written\"]", json);
  }

  @Test
  public void testCreate_usingTypeTokenDirectly_returnsAdapterForAnnotatedClass() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithTypeAdapter> adapter =
        gson.getAdapter(new TypeToken<AnnotatedWithTypeAdapter>(){});
    assertNotNull(adapter);
  }

  // ---- tests for getTypeAdapter()'s final nullSafe() wrapping ----

  @Test
  public void testGetTypeAdapter_typeAdapterBranch_nullSafeWriteHandlesNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithTypeAdapter> adapter = gson.getAdapter(AnnotatedWithTypeAdapter.class);
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, null);
    writer.close();
    assertEquals("null", sw.toString());
  }

  @Test
  public void testGetTypeAdapter_typeAdapterBranch_writeNonNullValueUsesCustomLogic() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithTypeAdapter> adapter = gson.getAdapter(AnnotatedWithTypeAdapter.class);
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, new AnnotatedWithTypeAdapter());
    writer.close();
    assertEquals("\"custom-written\"", sw.toString());
  }

  @Test
  public void testGetTypeAdapter_typeAdapterBranch_nullSafeReadHandlesNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithTypeAdapter> adapter = gson.getAdapter(AnnotatedWithTypeAdapter.class);
    JsonReader reader = new JsonReader(new StringReader("null"));
    reader.setLenient(true);
    AnnotatedWithTypeAdapter result = adapter.read(reader);
    assertNull(result);
  }

  @Test
  public void testGetTypeAdapter_typeAdapterBranch_adapterIsReusableAcrossCalls() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithTypeAdapter> adapter1 = gson.getAdapter(AnnotatedWithTypeAdapter.class);
    TypeAdapter<AnnotatedWithTypeAdapter> adapter2 = gson.getAdapter(AnnotatedWithTypeAdapter.class);
    assertNotNull(adapter1);
    assertNotNull(adapter2);
  }

  @Test
  public void testGetTypeAdapter_factoryBranch_nullSafeWriteHandlesNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithFactory> adapter = gson.getAdapter(AnnotatedWithFactory.class);
    StringWriter sw = new StringWriter();
    JsonWriter writer = new JsonWriter(sw);
    writer.setLenient(true);
    adapter.write(writer, null);
    writer.close();
    assertEquals("null", sw.toString());
  }

  @Test
  public void testGetTypeAdapter_factoryBranch_nullSafeReadHandlesNull() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithFactory> adapter = gson.getAdapter(AnnotatedWithFactory.class);
    JsonReader reader = new JsonReader(new StringReader("null"));
    reader.setLenient(true);
    AnnotatedWithFactory result = adapter.read(reader);
    assertNull(result);
  }

  // Bug hunt: TypeAdapterFactory.create() is explicitly allowed to return null
  // (meaning "this factory does not handle this type"). getTypeAdapter() must not
  // blindly call nullSafe() on a null adapter, otherwise a NullPointerException
  // leaks out instead of letting Gson fall back to another factory.
  @Test
  public void testGetTypeAdapter_factoryReturnsNull_doesNotThrowNPEAndFallsBack() throws Throwable {
    Gson gson = new Gson();
    TypeAdapter<AnnotatedWithNullFactory> adapter = gson.getAdapter(AnnotatedWithNullFactory.class);
    assertNotNull(adapter);
  }

  @Test
  public void testGetTypeAdapter_factoryReturnsNull_fallbackAdapterStillSerializes() throws Throwable {
    Gson gson = new Gson();
    String json = gson.toJson(new AnnotatedWithNullFactory());
    assertTrue(json.contains("initial"));
  }

  @Test
  public void testGetTypeAdapter_nullFieldOfAnnotatedType_withSerializeNulls_usesNullSafeWrite() throws Throwable {
    Gson gson = new GsonBuilder().serializeNulls().create();
    Wrapper w = new Wrapper();
    w.inner = null;
    String json = gson.toJson(w);
    assertTrue(json.contains("\"inner\":null"));
  }

  @Test
  public void testGetTypeAdapter_nullFieldOfFactoryAnnotatedType_withSerializeNulls_usesNullSafeWrite() throws Throwable {
    Gson gson = new GsonBuilder().serializeNulls().create();
    WrapperFactory w = new WrapperFactory();
    w.inner = null;
    String json = gson.toJson(w);
    assertTrue(json.contains("\"inner\":null"));
  }
}
