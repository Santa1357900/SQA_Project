package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonAdapterAnnotationTypeAdapterFactoryTest {

  @JsonAdapter(DummyTypeAdapter.class)
  private static final class AnnotatedWithAdapter {
  }

  @JsonAdapter(DummyTypeAdapterFactory.class)
  private static final class AnnotatedWithFactory {
  }

  @JsonAdapter(InvalidAnnotationValue.class)
  private static final class AnnotatedWithInvalid {
  }

  private static final class NotAnnotated {
  }

  public static final class DummyTypeAdapter extends TypeAdapter<Object> {
    @Override
    public void write(JsonWriter out, Object value) throws IOException {
      out.nullValue();
    }

    @Override
    public Object read(JsonReader in) throws IOException {
      in.nextNull();
      return null;
    }
  }

  public static final class DummyTypeAdapterFactory implements TypeAdapterFactory {
    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      return (TypeAdapter<T>) new DummyTypeAdapter();
    }
  }

  private static final class InvalidAnnotationValue {
  }

  @Test
  public void testCreateWithoutAnnotation() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(Collections.emptyMap());
    JsonAdapterAnnotationTypeAdapterFactory factory = new JsonAdapterAnnotationTypeAdapterFactory(constructorConstructor);
    Gson gson = new Gson();
    TypeToken<NotAnnotated> typeToken = TypeToken.get(NotAnnotated.class);

    TypeAdapter<NotAnnotated> adapter = factory.create(gson, typeToken);
    assertNull(adapter);
  }

  @Test
  public void testCreateWithDirectTypeAdapter() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap());
    JsonAdapterAnnotationTypeAdapterFactory factory = new JsonAdapterAnnotationTypeAdapterFactory(constructorConstructor);
    Gson gson = new Gson();
    TypeToken<AnnotatedWithAdapter> typeToken = TypeToken.get(AnnotatedWithAdapter.class);

    TypeAdapter<AnnotatedWithAdapter> adapter = factory.create(gson, typeToken);
    assertNotNull(adapter);
  }

  @Test
  public void testCreateWithTypeAdapterFactory() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap());
    JsonAdapterAnnotationTypeAdapterFactory factory = new JsonAdapterAnnotationTypeAdapterFactory(constructorConstructor);
    Gson gson = new Gson();
    TypeToken<AnnotatedWithFactory> typeToken = TypeToken.get(AnnotatedWithFactory.class);

    TypeAdapter<AnnotatedWithFactory> adapter = factory.create(gson, typeToken);
    assertNotNull(adapter);
  }

  @Test
  public void testCreateWithInvalidAnnotationValue() throws Throwable {
    ConstructorConstructor constructorConstructor = new ConstructorConstructor(new HashMap());
    JsonAdapterAnnotationTypeAdapterFactory factory = new JsonAdapterAnnotationTypeAdapterFactory(constructorConstructor);
    Gson gson = new Gson();
    TypeToken<AnnotatedWithInvalid> typeToken = TypeToken.get(AnnotatedWithInvalid.class);

    try {
      factory.create(gson, typeToken);
      fail("Expected IllegalArgumentException for invalid @JsonAdapter value");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("@JsonAdapter value must be TypeAdapter or TypeAdapterFactory reference"));
    }
  }
}