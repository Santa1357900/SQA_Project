package com.fasterxml.jackson.databind.introspect;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

public class JacksonAnnotationIntrospectorClaudeTest
{
    private ObjectMapper mapper;
    private JacksonAnnotationIntrospector ai;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
        ai = new JacksonAnnotationIntrospector();
    }

    // ---- helper types ----

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @JacksonAnnotationsInside
    public @interface BundleMarker { }

    public static class BundleHolder {
        @BundleMarker
        public String marked;
        @JsonProperty
        public String notMarked;
    }

    public enum SimpleEnum {
        NO_ANN,
        @JsonProperty("customValue") HAS_ANN,
        @JsonProperty("") EMPTY_ANN
    }

    @JsonRootName("wrappedRoot")
    public static class RootNamedBean { public int id = 1; }

    public static class PlainRootBean { public int id = 1; }

    @JsonIgnoreProperties(value = {"secret"})
    public static class IgnoreSerDefaultBean {
        public String id = "x";
        public String secret = "hidden";
    }

    @JsonIgnoreProperties(value = {"secret"}, allowGetters = true)
    public static class IgnoreSerAllowGettersBean {
        public String id = "x";
        public String secret = "hidden";
    }

    @JsonIgnoreProperties(value = {"secret"})
    public static class IgnoreDeserBean {
        public String id;
        public String secret;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgnoreUnknownBean { public String id; }

    public static class StrictBean { public String id; }

    @JsonIgnoreType
    public static class HiddenInner { public String secret = "s"; }

    public static class OuterWithHiddenInner {
        public String id = "x";
        public HiddenInner inner = new HiddenInner();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class AutoDetectAnyBean { private int value = 5; }

    public static class AutoDetectDefaultBean { private int value = 5; }

    public static class IgnoreFieldBean {
        public int a = 1;
        @JsonIgnore
        public int b = 2;
    }

    public static class ReadOnlyBean {
        public String id;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public String secret = "orig";
    }

    public static class WriteOnlyBean {
        public String id;
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        public String secret;
    }

    public static class GetterRenameBean {
        private int value = 42;
        @JsonGetter("customName")
        public int getValue() { return value; }
    }

    public static class SetterRenameBean {
        private int value;
        public int getValue() { return value; }
        @JsonSetter("customName")
        public void setValue(int v) { this.value = v; }
    }

    public static class JsonValueBean {
        @JsonValue
        public String asText() { return "VAL"; }
    }

    public static class AnyGetterBean {
        public String id = "x";
        private Map<String, Object> extra;
        public AnyGetterBean() {
            extra = new HashMap<String, Object>();
            extra.put("foo", "bar");
        }
        @JsonAnyGetter
        public Map<String, Object> getExtra() { return extra; }
    }

    public static class AnySetterBean {
        public String id;
        private Map<String, Object> extra = new HashMap<String, Object>();
        @JsonAnySetter
        public void addExtra(String key, Object value) { extra.put(key, value); }
        public Map<String, Object> getExtra() { return extra; }
    }

    public static class CreatorBean {
        private final int id;
        private final String name;
        @JsonCreator
        public CreatorBean(@JsonProperty("id") int id, @JsonProperty("name") String name) {
            this.id = id;
            this.name = name;
        }
        public int getId() { return id; }
        public String getName() { return name; }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
    public static class IdBean { public int value = 1; }

    @JsonIdentityInfo(generator = ObjectIdGenerators.None.class, property = "@id")
    public static class NoIdBean { public int value = 1; }

    public static class Address { public String city = "NYC"; }

    public static class PersonWithUnwrapped {
        public String name = "John";
        @JsonUnwrapped
        public Address address = new Address();
    }

    public static class ParentRef {
        public String name = "p";
        @JsonManagedReference
        public ChildRef child;
    }

    public static class ChildRef {
        public String name = "c";
        @JsonBackReference
        public ParentRef parent;
    }

    public static class InclNonNullBean {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String name;
        public String other = "x";
    }

    public static class InclNonEmptyBean {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> items = new ArrayList<String>();
    }

    public static class RawValueBean {
        @JsonRawValue
        public String raw = "{\"x\":1}";
    }

    @JsonPropertyOrder({"b", "a"})
    public static class OrderBean {
        public String a = "A";
        public String b = "B";
    }

    @JsonPropertyOrder(alphabetic = true)
    public static class AlphaBean {
        public String zebra = "z";
        public String apple = "a";
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({ @JsonSubTypes.Type(value = DogSub.class, name = "dog") })
    public static abstract class AnimalSub { }

    public static class DogSub extends AnimalSub { public String sound = "woof"; }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({ @JsonSubTypes.Type(value = CatSub.class) })
    public static abstract class AnimalSub2 { }

    @JsonTypeName("cat")
    public static class CatSub extends AnimalSub2 { public String sound = "meow"; }

    // ---- tests ----

    // version(): must return non-null Version
    @Test
    public void testVersion_returnsNonNull() throws Throwable {
        assertNotNull(ai.version());
    }

    // isAnnotationBundle: annotation type meta-annotated with @JacksonAnnotationsInside -> true
    @Test
    public void testIsAnnotationBundle_withMetaAnnotation_returnsTrue() throws Throwable {
        Field f = BundleHolder.class.getField("marked");
        Annotation ann = f.getAnnotation(BundleMarker.class);
        assertTrue(ai.isAnnotationBundle(ann));
    }

    // isAnnotationBundle: annotation type NOT meta-annotated -> false
    @Test
    public void testIsAnnotationBundle_withoutMetaAnnotation_returnsFalse() throws Throwable {
        Field f = BundleHolder.class.getField("notMarked");
        Annotation ann = f.getAnnotation(JsonProperty.class);
        assertFalse(ai.isAnnotationBundle(ann));
    }

    // findEnumValue: no annotation on constant -> falls back to .name()
    @Test
    public void testFindEnumValue_noAnnotation_returnsEnumName() throws Throwable {
        assertEquals("NO_ANN", ai.findEnumValue(SimpleEnum.NO_ANN));
    }

    // findEnumValue: @JsonProperty with non-empty value -> returns that value
    @Test
    public void testFindEnumValue_withNonEmptyJsonProperty_returnsCustomName() throws Throwable {
        assertEquals("customValue", ai.findEnumValue(SimpleEnum.HAS_ANN));
    }

    // findEnumValue: @JsonProperty with empty value -> treated as absent, falls back to .name()
    @Test
    public void testFindEnumValue_withEmptyJsonProperty_returnsEnumName() throws Throwable {
        assertEquals("EMPTY_ANN", ai.findEnumValue(SimpleEnum.EMPTY_ANN));
    }

    // _classIfExplicit(cls): null short-circuits before bogus check -> null
    @Test
    public void testClassIfExplicit_nullClass_returnsNull() throws Throwable {
        assertNull(ai._classIfExplicit((Class<?>) null));
    }

    // _classIfExplicit(cls): concrete non-bogus class -> returned unchanged
    @Test
    public void testClassIfExplicit_concreteClass_returnsSameClass() throws Throwable {
        assertEquals(String.class, ai._classIfExplicit(String.class));
    }

    // _classIfExplicit(cls, implicit): equals implicit -> null
    @Test
    public void testClassIfExplicitTwoArg_matchesImplicit_returnsNull() throws Throwable {
        assertNull(ai._classIfExplicit(String.class, String.class));
    }

    // _classIfExplicit(cls, implicit): different from implicit -> returns cls
    @Test
    public void testClassIfExplicitTwoArg_differsFromImplicit_returnsClass() throws Throwable {
        assertEquals(String.class, ai._classIfExplicit(String.class, Integer.class));
    }

    // _propertyName: empty local name -> USE_DEFAULT marker
    @Test
    public void testPropertyName_emptyLocalName_returnsUseDefault() throws Throwable {
        assertSame(PropertyName.USE_DEFAULT, ai._propertyName("", "ns"));
    }

    // _propertyName: non-empty local name, empty namespace -> simple name preserved
    @Test
    public void testPropertyName_emptyNamespace_returnsSimpleName() throws Throwable {
        PropertyName pn = ai._propertyName("name", "");
        assertEquals("name", pn.getSimpleName());
    }

    // _propertyName: non-empty local name and namespace -> simple name preserved
    @Test
    public void testPropertyName_withNamespace_returnsSimpleName() throws Throwable {
        PropertyName pn = ai._propertyName("name", "ns");
        assertEquals("name", pn.getSimpleName());
    }

    // _constructStdTypeResolverBuilder: returns a usable builder instance
    @Test
    public void testConstructStdTypeResolverBuilder_returnsInstance() throws Throwable {
        StdTypeResolverBuilder b = ai._constructStdTypeResolverBuilder();
        assertNotNull(b);
    }

    // _constructNoTypeResolverBuilder: returns non-null marker builder
    @Test
    public void testConstructNoTypeResolverBuilder_returnsInstance() throws Throwable {
        StdTypeResolverBuilder b = ai._constructNoTypeResolverBuilder();
        assertNotNull(b);
    }

    // findRootName: @JsonRootName present -> wrapped root key matches annotation value
    @Test
    public void testFindRootName_withAnnotation_usesAnnotatedName() throws Throwable {
        mapper.enable(SerializationFeature.WRAP_ROOT_VALUE);
        String json = mapper.writeValueAsString(new RootNamedBean());
        assertTrue(json.contains("\"wrappedRoot\""));
    }

    // findRootName: no annotation -> returns null, default wrap uses simple class name
    @Test
    public void testFindRootName_withoutAnnotation_usesDefaultClassName() throws Throwable {
        mapper.enable(SerializationFeature.WRAP_ROOT_VALUE);
        String json = mapper.writeValueAsString(new PlainRootBean());
        assertTrue(json.contains("\"PlainRootBean\""));
    }

    // findPropertiesToIgnore(forSerialization=true): default allowGetters=false -> property excluded
    @Test
    public void testFindPropertiesToIgnore_serializationDefault_excludesProperty() throws Throwable {
        String json = mapper.writeValueAsString(new IgnoreSerDefaultBean());
        assertFalse(json.contains("secret"));
        assertTrue(json.contains("\"id\":\"x\""));
    }

    // findPropertiesToIgnore(forSerialization=true): allowGetters=true -> property included
    @Test
    public void testFindPropertiesToIgnore_allowGettersTrue_includesProperty() throws Throwable {
        String json = mapper.writeValueAsString(new IgnoreSerAllowGettersBean());
        assertTrue(json.contains("\"secret\":\"hidden\""));
    }

    // findPropertiesToIgnore(forSerialization=false): default allowSetters=false -> ignored on input, no exception
    @Test
    public void testFindPropertiesToIgnore_deserializationDefault_ignoresProperty() throws Throwable {
        IgnoreDeserBean bean = mapper.readValue("{\"id\":\"abc\",\"secret\":\"hidden\"}", IgnoreDeserBean.class);
        assertEquals("abc", bean.id);
        assertNull(bean.secret);
    }

    // findIgnoreUnknownProperties: ignoreUnknown=true -> unknown property silently skipped
    @Test
    public void testFindIgnoreUnknownProperties_true_noException() throws Throwable {
        IgnoreUnknownBean b = mapper.readValue("{\"id\":\"x\",\"extra\":\"y\"}", IgnoreUnknownBean.class);
        assertEquals("x", b.id);
    }

    // findIgnoreUnknownProperties: no annotation -> default fails on unknown property
    @Test
    public void testFindIgnoreUnknownProperties_defaultFalse_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"id\":\"x\",\"extra\":\"y\"}", StrictBean.class);
            fail("expected exception for unknown property");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // isIgnorableType: @JsonIgnoreType on nested type -> whole property excluded from serialization
    @Test
    public void testIsIgnorableType_withJsonIgnoreType_excludesProperty() throws Throwable {
        String json = mapper.writeValueAsString(new OuterWithHiddenInner());
        assertFalse(json.contains("inner"));
        assertTrue(json.contains("\"id\":\"x\""));
    }

    // findAutoDetectVisibility: fieldVisibility=ANY -> normally-hidden private field becomes visible
    @Test
    public void testFindAutoDetectVisibility_anyFieldVisibility_exposesPrivateField() throws Throwable {
        String json = mapper.writeValueAsString(new AutoDetectAnyBean());
        assertTrue(json.contains("\"value\":5"));
    }

    // findAutoDetectVisibility: default visibility -> private field without getter stays hidden
    @Test
    public void testFindAutoDetectVisibility_default_hidesPrivateField() throws Throwable {
        String json = mapper.writeValueAsString(new AutoDetectDefaultBean());
        assertEquals("{}", json);
    }

    // hasIgnoreMarker: @JsonIgnore(true) on field -> excluded from serialization
    @Test
    public void testHasIgnoreMarker_withJsonIgnore_excludesField() throws Throwable {
        String json = mapper.writeValueAsString(new IgnoreFieldBean());
        assertTrue(json.contains("\"a\":1"));
        assertFalse(json.contains("\"b\""));
    }

    // findPropertyAccess: READ_ONLY -> ignored on deserialization, original value retained
    @Test
    public void testFindPropertyAccess_readOnly_ignoredOnDeserialization() throws Throwable {
        ReadOnlyBean bean = mapper.readValue("{\"id\":\"x\",\"secret\":\"changed\"}", ReadOnlyBean.class);
        assertEquals("x", bean.id);
        assertEquals("orig", bean.secret);
    }

    // findPropertyAccess: WRITE_ONLY -> excluded from serialization, but settable on input
    @Test
    public void testFindPropertyAccess_writeOnly_excludedFromSerializationOnly() throws Throwable {
        WriteOnlyBean bean = new WriteOnlyBean();
        bean.id = "x";
        bean.secret = "hidden";
        String json = mapper.writeValueAsString(bean);
        assertFalse(json.contains("secret"));
        WriteOnlyBean bean2 = mapper.readValue("{\"id\":\"y\",\"secret\":\"newval\"}", WriteOnlyBean.class);
        assertEquals("newval", bean2.secret);
    }

    // findNameForSerialization: @JsonGetter renames serialized property key
    @Test
    public void testFindNameForSerialization_withJsonGetter_renamesProperty() throws Throwable {
        String json = mapper.writeValueAsString(new GetterRenameBean());
        assertTrue(json.contains("\"customName\":42"));
    }

    // findNameForDeserialization: @JsonSetter renames the expected input property key
    @Test
    public void testFindNameForDeserialization_withJsonSetter_renamesProperty() throws Throwable {
        SetterRenameBean bean = mapper.readValue("{\"customName\":7}", SetterRenameBean.class);
        assertEquals(7, bean.getValue());
    }

    // hasAsValueAnnotation: @JsonValue method is used as sole serialized value
    @Test
    public void testHasAsValueAnnotation_withJsonValue_usesReturnValue() throws Throwable {
        String json = mapper.writeValueAsString(new JsonValueBean());
        assertEquals("\"VAL\"", json);
    }

    // hasAnyGetterAnnotation: @JsonAnyGetter map entries flattened into output
    @Test
    public void testHasAnyGetterAnnotation_withJsonAnyGetter_flattensMapEntries() throws Throwable {
        String json = mapper.writeValueAsString(new AnyGetterBean());
        assertTrue(json.contains("\"id\":\"x\""));
        assertTrue(json.contains("\"foo\":\"bar\""));
    }

    // hasAnySetterAnnotation: @JsonAnySetter captures unrecognized properties
    @Test
    public void testHasAnySetterAnnotation_withJsonAnySetter_capturesExtraProperties() throws Throwable {
        AnySetterBean bean = mapper.readValue("{\"id\":\"x\",\"foo\":\"bar\"}", AnySetterBean.class);
        assertEquals("x", bean.id);
        assertEquals("bar", bean.getExtra().get("foo"));
    }

    // hasCreatorAnnotation: @JsonCreator constructor used for deserialization
    @Test
    public void testHasCreatorAnnotation_withJsonCreator_usesAnnotatedConstructor() throws Throwable {
        CreatorBean bean = mapper.readValue("{\"id\":5,\"name\":\"abc\"}", CreatorBean.class);
        assertEquals(5, bean.getId());
        assertEquals("abc", bean.getName());
    }

    // findObjectIdInfo: non-None generator -> object id property included in output
    @Test
    public void testFindObjectIdInfo_withGenerator_includesIdProperty() throws Throwable {
        String json = mapper.writeValueAsString(new IdBean());
        assertTrue(json.contains("\"@id\""));
    }

    // findObjectIdInfo: generator == ObjectIdGenerators.None -> treated as no id info at all
    @Test
    public void testFindObjectIdInfo_withNoneGenerator_returnsNoIdHandling() throws Throwable {
        String json = mapper.writeValueAsString(new NoIdBean());
        assertFalse(json.contains("@id"));
        assertTrue(json.contains("\"value\":1"));
    }

    // findUnwrappingNameTransformer: @JsonUnwrapped flattens nested object's fields
    @Test
    public void testFindUnwrappingNameTransformer_withJsonUnwrapped_flattensNestedFields() throws Throwable {
        String json = mapper.writeValueAsString(new PersonWithUnwrapped());
        assertTrue(json.contains("\"city\":\"NYC\""));
        assertFalse(json.contains("\"address\""));
    }

    // findReferenceType: managed/back reference pair avoids infinite recursion, back ref excluded
    @Test
    public void testFindReferenceType_managedAndBackReference_excludesBackReference() throws Throwable {
        ParentRef p = new ParentRef();
        ChildRef c = new ChildRef();
        p.child = c;
        c.parent = p;
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("\"child\""));
        assertFalse(json.contains("\"parent\""));
    }

    // findPropertyInclusion: NON_NULL excludes null-valued property from output
    @Test
    public void testFindPropertyInclusion_nonNull_excludesNullProperty() throws Throwable {
        String json = mapper.writeValueAsString(new InclNonNullBean());
        assertFalse(json.contains("\"name\""));
        assertTrue(json.contains("\"other\":\"x\""));
    }

    // findSerializationInclusion: NON_EMPTY excludes empty collection but includes non-empty one
    @Test
    public void testFindSerializationInclusion_nonEmpty_excludesEmptyIncludesNonEmpty() throws Throwable {
        InclNonEmptyBean b = new InclNonEmptyBean();
        assertEquals("{}", mapper.writeValueAsString(b));
        b.items.add("a");
        assertTrue(mapper.writeValueAsString(b).contains("\"items\":[\"a\"]"));
    }

    // findSerializer: @JsonRawValue embeds string content as raw (unescaped) JSON
    @Test
    public void testFindSerializer_withJsonRawValue_embedsRawJson() throws Throwable {
        String json = mapper.writeValueAsString(new RawValueBean());
        assertEquals("{\"raw\":{\"x\":1}}", json);
    }

    // findSerializationPropertyOrder: explicit order places listed property first
    @Test
    public void testFindSerializationPropertyOrder_explicitOrder_ordersFields() throws Throwable {
        String json = mapper.writeValueAsString(new OrderBean());
        assertTrue(json.indexOf("\"b\"") < json.indexOf("\"a\""));
    }

    // findSerializationSortAlphabetically: alphabetic=true sorts fields alphabetically
    @Test
    public void testFindSerializationSortAlphabetically_alphabeticTrue_sortsFields() throws Throwable {
        String json = mapper.writeValueAsString(new AlphaBean());
        assertTrue(json.indexOf("\"apple\"") < json.indexOf("\"zebra\""));
    }

    // findSubtypes: @JsonSubTypes.Type(name=) determines serialized type discriminator
    @Test
    public void testFindSubtypes_withExplicitName_usesGivenTypeName() throws Throwable {
        AnimalSub a = new DogSub();
        String json = mapper.writeValueAsString(a);
        assertTrue(json.contains("\"type\":\"dog\""));
    }

    // findTypeName: @JsonTypeName on subtype supplies type discriminator when not set in @JsonSubTypes
    @Test
    public void testFindTypeName_withJsonTypeNameAnnotation_usesAnnotatedName() throws Throwable {
        AnimalSub2 c = new CatSub();
        String json = mapper.writeValueAsString(c);
        assertTrue(json.contains("\"type\":\"cat\""));
    }
}
