package com.google.gson.internal.bind;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

public class ReflectiveTypeAdapterFactoryClaudeTest {

  private Gson gson;

  @Before
  public void setUp() throws Throwable {
    gson = new Gson();
  }

  // ---------- fixture classes ----------

  static class Simple {
    int value;
    String name;
  }

  static class Named {
    @SerializedName("nm")
    String name;
  }

  static class Alt {
    @SerializedName(value = "primary", alternate = { "alt1", "alt2" })
    String field;
  }

  static class Dup {
    @SerializedName("same")
    String a;
    @SerializedName("same")
    String b;
  }

  static class ParentDup {
    String name = "p";
  }

  static class ChildDup extends ParentDup {
    String name = "c";
  }

  static class Excl {
    static int staticField = 1;
    transient int transientField = 2;
    int normalField = 3;
  }

  static class Exposed {
    @Expose
    String included;
    String excluded;
  }

  static class PartialExpose {
    @Expose(serialize = false, deserialize = true)
    String readOnlyFromJson;
  }

  static class WriteOnlyExpose {
    @Expose(serialize = true, deserialize = false)
    String writeOnlyToJson;
  }

  static class SelfRef {
    SelfRef self;
    String name;
  }

  static class WithList {
    List<String> items;
  }

  static class Parent {
    String parentField;
  }

  static class Child extends Parent {
    String childField;
  }

  interface Marker {
  }

  static class MarkerImpl implements Marker {
    String field = "x";
  }

  static class LowerCase {
    String myField;
  }

  static class Prim {
    boolean flag;
    double amount;
  }

  static class Animal {
  }

  static class Dog extends Animal {
  }

  public static class AnimalAdapter extends TypeAdapter<Animal> {
    @Override public void write(JsonWriter out, Animal value) throws IOException {
      out.value("animal-adapter");
    }
    @Override public Animal read(JsonReader in) throws IOException {
      in.nextString();
      return new Animal();
    }
  }

  public static class DogAdapter extends TypeAdapter<Dog> {
    @Override public void write(JsonWriter out, Dog value) throws IOException {
      out.value("dog-adapter");
    }
    @Override public Dog read(JsonReader in) throws IOException {
      in.nextString();
      return new Dog();
    }
  }

  static class Box {
    @JsonAdapter(AnimalAdapter.class)
    Animal pet;
  }

  static class Cage {
    @JsonAdapter(AnimalAdapter.class)
    Animal pet;
  }

  // ---------- tests: general round trip ----------

  // ครอบคลุม Adapter.write และ Adapter.read ปกติ (loop หลายรอบ, field ธรรมดา)
  @Test
  public void testRoundTrip_simplePojo_serializesAndDeserializesAllFields() throws Throwable {
    Simple s = new Simple();
    s.value = 42;
    s.name = "hello";
    String json = gson.toJson(s);
    Simple back = gson.fromJson(json, Simple.class);
    assertEquals(42, back.value);
    assertEquals("hello", back.name);
  }

  // ---------- getFieldNames ----------

  // ครอบคลุม getFieldNames: annotation == null -> ใช้ชื่อ field ตรงๆ
  @Test
  public void testGetFieldNames_noAnnotation_usesJavaFieldNameAsJsonKey() throws Throwable {
    Simple s = new Simple();
    s.value = 7;
    s.name = "x";
    String json = gson.toJson(s);
    assertTrue(json.contains("\"value\":7"));
    assertTrue(json.contains("\"name\":\"x\""));
  }

  // ครอบคลุม getFieldNames: annotation ไม่ null, alternate.length == 0 -> singletonList ใช้ตอน serialize
  @Test
  public void testGetFieldNames_serializedNameAnnotation_usesCustomNameForSerialize() throws Throwable {
    Named n = new Named();
    n.name = "abc";
    String json = gson.toJson(n);
    assertTrue(json.contains("\"nm\":\"abc\""));
    assertFalse(json.contains("\"name\""));
  }

  // ครอบคลุม getFieldNames: ชื่อ custom ใช้ตอน deserialize ด้วย
  @Test
  public void testGetFieldNames_serializedNameAnnotation_usesCustomNameForDeserialize() throws Throwable {
    Named n = gson.fromJson("{\"nm\":\"xyz\"}", Named.class);
    assertEquals("xyz", n.name);
  }

  // ครอบคลุม getFieldNames: alternate.length > 0 -> alt name deserialize ได้
  @Test
  public void testGetFieldNames_alternateNames_deserializesFromAlternateName() throws Throwable {
    Alt a = gson.fromJson("{\"alt1\":\"value1\"}", Alt.class);
    assertEquals("value1", a.field);
  }

  // ครอบคลุม getBoundFields: if (i != 0) serialize = false -> ชื่อ alternate ไม่ถูกใช้ตอน serialize
  @Test
  public void testGetFieldNames_alternateNames_serializesOnlyPrimaryName() throws Throwable {
    Alt a = new Alt();
    a.field = "v";
    String json = gson.toJson(a);
    assertTrue(json.contains("\"primary\":\"v\""));
    assertFalse(json.contains("alt1"));
  }

  // ครอบคลุม getFieldNames: fieldNamingPolicy.translateName เมื่อไม่มี annotation
  @Test
  public void testGetFieldNames_noAnnotationWithCustomNamingPolicy_translatesFieldName() throws Throwable {
    Gson g = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
    LowerCase o = new LowerCase();
    o.myField = "v";
    String json = g.toJson(o);
    assertTrue(json.contains("\"MyField\":\"v\""));
  }

  // ---------- getBoundFields: duplicate detection ----------

  // ครอบคลุม getBoundFields: ชื่อซ้ำจาก field คนละตัว -> throw IllegalArgumentException
  @Test
  public void testGetBoundFields_duplicateSerializedName_throwsIllegalArgumentException() throws Throwable {
    try {
      gson.toJson(new Dup());
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("declares multiple JSON fields named"));
    }
  }

  // ครอบคลุม getBoundFields: ชื่อซ้ำข้าม hierarchy (subclass shadow field เดียวกับ superclass)
  @Test
  public void testGetBoundFields_shadowedFieldNameAcrossHierarchy_throwsIllegalArgumentException() throws Throwable {
    try {
      gson.toJson(new ChildDup());
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("multiple JSON fields named"));
    }
  }

  // ครอบคลุม getBoundFields: resolve generic type ของ field (List<String>)
  @Test
  public void testGetBoundFields_genericListField_resolvesElementTypeCorrectly() throws Throwable {
    String json = "{\"items\":[\"a\",\"b\",\"c\"]}";
    WithList w = gson.fromJson(json, WithList.class);
    assertEquals(3, w.items.size());
    assertEquals("a", w.items.get(0));
  }

  // ครอบคลุม getBoundFields: loop while(raw != Object.class) รวม field จาก superclass
  @Test
  public void testGetBoundFields_inheritedFields_includesSuperclassFields() throws Throwable {
    Child c = new Child();
    c.parentField = "p";
    c.childField = "c";
    String json = gson.toJson(c);
    assertTrue(json.contains("\"parentField\":\"p\""));
    assertTrue(json.contains("\"childField\":\"c\""));
  }

  // ครอบคลุม getBoundFields: raw.isInterface() -> map ว่าง -> เขียน {}
  @Test
  public void testGetBoundFields_interfaceType_returnsEmptyFieldsAndWritesEmptyObject() throws Throwable {
    MarkerImpl impl = new MarkerImpl();
    String json = gson.toJson(impl, Marker.class);
    assertEquals("{}", json);
  }

  // ---------- excludeField ----------

  // ครอบคลุม excludeField: static / transient field ถูก exclude โดย default
  @Test
  public void testExcludeField_staticAndTransientFields_excludedFromSerialization() throws Throwable {
    String json = gson.toJson(new Excl());
    assertFalse(json.contains("staticField"));
    assertFalse(json.contains("transientField"));
    assertTrue(json.contains("normalField"));
  }

  // ครอบคลุม getBoundFields: if (!serialize && !deserialize) continue; เมื่อ require @Expose
  @Test
  public void testExcludeField_requireExpose_fieldsWithoutExposeAreSkippedEntirely() throws Throwable {
    Gson exposeGson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    Exposed e = new Exposed();
    e.included = "yes";
    e.excluded = "no";
    String json = exposeGson.toJson(e);
    assertTrue(json.contains("included"));
    assertFalse(json.contains("excluded"));
    Exposed back = exposeGson.fromJson("{\"included\":\"a\",\"excluded\":\"b\"}", Exposed.class);
    assertEquals("a", back.included);
    assertNull(back.excluded);
  }

  // ครอบคลุม writeField: if (!serialized) return false; แต่ deserialize ยังทำงานได้
  @Test
  public void testExcludeField_exposeSerializeFalse_fieldOmittedFromOutputButReadable() throws Throwable {
    Gson g = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    PartialExpose p = new PartialExpose();
    p.readOnlyFromJson = "abc";
    String json = g.toJson(p);
    assertFalse(json.contains("readOnlyFromJson"));
    PartialExpose back = g.fromJson("{\"readOnlyFromJson\":\"xyz\"}", PartialExpose.class);
    assertEquals("xyz", back.readOnlyFromJson);
  }

  // ครอบคลุม Adapter.read: field == null || !field.deserialized -> skipValue
  @Test
  public void testExcludeField_exposeDeserializeFalse_fieldWrittenButNotReadBack() throws Throwable {
    Gson g = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    WriteOnlyExpose w = new WriteOnlyExpose();
    w.writeOnlyToJson = "abc";
    String json = g.toJson(w);
    assertTrue(json.contains("writeOnlyToJson"));
    WriteOnlyExpose back = g.fromJson("{\"writeOnlyToJson\":\"xyz\"}", WriteOnlyExpose.class);
    assertNull(back.writeOnlyToJson);
  }

  // ---------- createBoundField: writeField / recursion ----------

  // ครอบคลุม writeField: fieldValue != value -> ป้องกัน recursion เมื่อ field ชี้กลับมาที่ object เอง
  @Test
  public void testWriteField_fieldValueEqualsContainerInstance_isSkippedToAvoidRecursion() throws Throwable {
    SelfRef s = new SelfRef();
    s.self = s;
    s.name = "n";
    String json = gson.toJson(s);
    assertFalse(json.contains("\"self\""));
    assertTrue(json.contains("\"name\":\"n\""));
  }



  // ครอบคลุม createBoundField: isPrimitive handling ใน read() และ round trip ปกติ
  @Test
  public void testCreateBoundField_primitiveFields_roundTripCorrectly() throws Throwable {
    Prim p = new Prim();
    p.flag = true;
    p.amount = 3.5;
    String json = gson.toJson(p);
    Prim back = gson.fromJson(json, Prim.class);
    assertTrue(back.flag);
    assertEquals(3.5, back.amount, 1e-9);
  }

  // ครอบคลุม createBoundField: @JsonAdapter ถูกใช้เมื่อไม่มี polymorphism (runtime type == declared type)
  @Test
  public void testCreateBoundField_jsonAdapterAnnotation_usedWhenNoPolymorphism() throws Throwable {
    Box box = new Box();
    box.pet = new Animal();
    String json = gson.toJson(box);
    assertTrue(json.contains("animal-adapter"));
  }

  // ครอบคลุม createBoundField.write: @JsonAdapter ต้องมีสิทธิ์เหนือกว่า runtime-type adapter เสมอ
  // (บั๊ก: โค้ดจริงไม่เช็ค jsonAdapterPresent ก่อน wrap ด้วย TypeAdapterRuntimeTypeWrapper)
  @Test
  public void testCreateBoundField_jsonAdapterAnnotation_takesPrecedenceOverRuntimeTypeAdapter() throws Throwable {
    Gson g = new GsonBuilder().registerTypeAdapter(Dog.class, new DogAdapter()).create();
    Cage cage = new Cage();
    cage.pet = new Dog();
    String json = g.toJson(cage);
    assertTrue(json.contains("animal-adapter"));
    assertFalse(json.contains("dog-adapter"));
  }

  // ---------- Adapter.read ----------

  // ครอบคลุม Adapter.read: in.peek() == JsonToken.NULL -> return null
  @Test
  public void testAdapterRead_jsonNull_returnsNullInstance() throws Throwable {
    Simple s = gson.fromJson("null", Simple.class);
    assertNull(s);
  }

  // ครอบคลุม Adapter.read: while(in.hasNext()) 0 รอบ -> instance ค่า default
  @Test
  public void testAdapterRead_emptyJsonObject_returnsInstanceWithDefaultFieldValues() throws Throwable {
    Simple s = gson.fromJson("{}", Simple.class);
    assertEquals(0, s.value);
    assertNull(s.name);
  }

  // ครอบคลุม Adapter.read: field == null -> in.skipValue() ไม่เกิด error
  @Test
  public void testAdapterRead_unknownFieldName_isSkippedWithoutError() throws Throwable {
    Simple s = gson.fromJson("{\"value\":5,\"unknown\":{\"x\":1},\"name\":\"n\"}", Simple.class);
    assertEquals(5, s.value);
    assertEquals("n", s.name);
  }

  // ครอบคลุม Adapter.read: catch (IllegalStateException e) -> throw new JsonSyntaxException(e)
  @Test
  public void testAdapterRead_malformedJson_wrapsIllegalStateExceptionAsJsonSyntaxException() throws Throwable {
    try {
      gson.fromJson("[1,2,3]", Simple.class);
      fail("expected JsonSyntaxException");
    } catch (JsonSyntaxException expected) {
      // expected
    }
  }

  // ---------- Adapter.write ----------

  // ครอบคลุม Adapter.write: value == null -> out.nullValue()
  @Test
  public void testAdapterWrite_nullValue_writesJsonNull() throws Throwable {
    String json = gson.toJson(null, Simple.class);
    assertEquals("null", json);
  }
}
