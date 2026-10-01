package com.fasterxml.jackson.databind.introspect;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JacksonAnnotationIntrospectorClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // ---- helper annotation type for isAnnotationBundle tests ----
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @JacksonAnnotationsInside
    public @interface BundleMarker { }

    public static class BundleHolder {
        @BundleMarker
        public String field;
    }

    public static class PlainHolder {
        @JsonProperty("x")
        public String field;
    }

    @JsonIgnoreProperties({"a", "b"})
    public static class IgnorePropsBean {
        public String a = "A";
        public String b = "B";
        public String c = "C";
    }

    @JsonIgnoreProperties(value = {"a"}, allowGetters = true)
    public static class AllowGettersBean {
        public String a = "A";
        public String b = "B";
    }

    @JsonIgnoreProperties(value = {"a"}, allowSetters = true)
    public static class AllowSettersBean {
        public String a = "A";
        public String b = "B";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgnoreUnknownBean {
        public String known = "K";
    }

    @JsonIgnoreType
    public static class IgnoredType {
        public String x = "x";
    }

    public static class HolderBean {
        public IgnoredType ignored = new IgnoredType();
        public String kept = "k";
    }

    @JsonFilter("myFilter")
    public static class FilteredBean {
        public String a = "A";
    }

    public static class IgnoreBean {
        @JsonIgnore
        public String secret = "hidden";
        public String visible = "shown";
    }

    public static class GetterPriorityBean {
        private String value = "X";
        @JsonGetter("getterName")
        @JsonProperty("propName")
        public String getValue() { return value; }
        public void setValue(String v) { this.value = v; }
    }

    public static class RenameBean {
        @JsonProperty("renamed")
        public String original = "value";
    }

    public static class SetterBean {
        private String value;
        public String getValue() { return value; }
        @JsonSetter("setterName")
        public void setValue(String v) { this.value = v; }
    }

    public static class RawValueBean {
        @JsonRawValue
        public String json = "{\"x\":1}";
    }

    @JsonPropertyOrder({"z", "a"})
    public static class OrderBean {
        public String a = "A";
        public String z = "Z";
    }

    @JsonPropertyOrder(alphabetic = true)
    public static class AlphaOrderBean {
        public String zeta = "Z";
        public String alpha = "A";
    }

    @JsonRootName("root")
    public static class RootBean {
        public String value = "v";
    }

    public static class IncludeBean {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String nullable;
        public String always = "A";
    }

    public static class Address {
        public String city = "NYC";
    }

    public static class UnwrapBean {
        @JsonUnwrapped
        public Address address = new Address();
        public String name = "John";
    }

    public static class ViewPublic { }
    public static class ViewInternal extends ViewPublic { }

    public static class ViewBean {
        @JsonView(ViewPublic.class)
        public String pub = "pub";
        @JsonView(ViewInternal.class)
        public String internal = "internal";
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({ @JsonSubTypes.Type(value = Dog.class, name = "dog") })
    public static abstract class Animal { }

    public static class Dog extends Animal {
        public String name = "Rex";
    }

    public static class CreatorBean {
        private final String value;
        @JsonCreator
        public CreatorBean(@JsonProperty("value") String value) {
            this.value = value;
        }
        public String getValue() { return value; }
    }

    public static class AnyGetterBean {
        private Map<String, String> extra = new LinkedHashMap<String, String>();
        public AnyGetterBean() { extra.put("k1", "v1"); }
        @JsonAnyGetter
        public Map<String, String> getExtra() { return extra; }
    }

    public static class AnySetterBean {
        private Map<String, String> extra = new LinkedHashMap<String, String>();
        @JsonAnySetter
        public void set(String key, String value) { extra.put(key, value); }
        public Map<String, String> getExtra() { return extra; }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
    public static class IdentityBean {
        public String name = "n";
    }

    public static class IndexBean {
        @JsonProperty(index = 2)
        public String second = "2";
        @JsonProperty(index = 1)
        public String first = "1";
    }

    public static class ParentRef {
        public String name = "parent";
        @JsonManagedReference
        public ChildRef child;
    }

    public static class ChildRef {
        public String name = "child";
        @JsonBackReference
        public ParentRef parent;
    }

    public static class AccessBean {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public String readOnly = "R";
        public String normal = "N";
    }

    public static class WriteOnlyBean {
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        public String writeOnly = "W";
        public String normal = "N";
    }

    public static class DateFormatBean {
        @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING)
        public Date when = new Date(0L);
    }

    // covers version() returns a non-null Version instance
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Version v = introspector.version();
        assertNotNull(v);
    }

    // covers isAnnotationBundle true branch: annotationType has @JacksonAnnotationsInside
    @Test
    public void testIsAnnotationBundle_withMetaAnnotation_true() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Field f = BundleHolder.class.getDeclaredField("field");
        Annotation ann = f.getAnnotation(BundleMarker.class);
        assertTrue(introspector.isAnnotationBundle(ann));
    }

    // covers isAnnotationBundle false branch: annotationType lacks @JacksonAnnotationsInside
    @Test
    public void testIsAnnotationBundle_withoutMetaAnnotation_false() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Field f = PlainHolder.class.getDeclaredField("field");
        Annotation ann = f.getAnnotation(JsonProperty.class);
        assertFalse(introspector.isAnnotationBundle(ann));
    }

    // covers findPropertiesToIgnore forSerialization branch, allowGetters()==false default -> excluded
    @Test
    public void testFindPropertiesToIgnore_allowGettersFalseDefault_excludedFromSerialization() throws Throwable {
        String json = mapper.writeValueAsString(new IgnorePropsBean());
        assertFalse(json.contains("\"a\""));
        assertTrue(json.contains("\"c\""));
    }

    // covers findPropertiesToIgnore forSerialization branch, allowGetters()==true -> included
    @Test
    public void testFindPropertiesToIgnore_allowGettersTrue_includedInSerialization() throws Throwable {
        String json = mapper.writeValueAsString(new AllowGettersBean());
        assertTrue(json.contains("\"a\""));
        assertTrue(json.contains("\"b\""));
    }

    // covers findPropertiesToIgnore forDeserialization branch, allowSetters()==false default -> excluded
    @Test
    public void testFindPropertiesToIgnore_allowSettersFalseDefault_excludedFromDeserialization() throws Throwable {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        IgnorePropsBean bean = mapper.readValue("{\"a\":\"X\",\"c\":\"Y\"}", IgnorePropsBean.class);
        assertEquals("A", bean.a);
        assertEquals("Y", bean.c);
    }

    // covers findPropertiesToIgnore forDeserialization branch, allowSetters()==true -> included
    @Test
    public void testFindPropertiesToIgnore_allowSettersTrue_includedInDeserialization() throws Throwable {
        AllowSettersBean bean = mapper.readValue("{\"a\":\"X\",\"b\":\"Y\"}", AllowSettersBean.class);
        assertEquals("X", bean.a);
        assertEquals("Y", bean.b);
    }

    // covers findIgnoreUnknownProperties returning true so unknown field does not fail
    @Test
    public void testFindIgnoreUnknownProperties_ignoreUnknownTrue_unknownFieldsSkipped() throws Throwable {
        IgnoreUnknownBean bean = mapper.readValue("{\"known\":\"V\",\"extra\":\"E\"}", IgnoreUnknownBean.class);
        assertEquals("V", bean.known);
    }

    // covers isIgnorableType true branch via @JsonIgnoreType on property's declared type
    @Test
    public void testIsIgnorableType_jsonIgnoreType_excludesPropertyOfThatType() throws Throwable {
        String json = mapper.writeValueAsString(new HolderBean());
        assertFalse(json.contains("ignored"));
        assertTrue(json.contains("\"kept\":\"k\""));
    }

    // covers _findFilterId non-empty id branch, requiring a FilterProvider that is not configured
    @Test
    public void testFindFilterId_unresolvedFilter_throwsJsonMappingException() throws Throwable {
        try {
            mapper.writeValueAsString(new FilteredBean());
            fail("expected JsonMappingException due to unresolved filter id");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("filter"));
        }
    }

    // covers hasIgnoreMarker true via @JsonIgnore excluding a field from serialization
    @Test
    public void testJsonIgnore_fieldExcludedFromSerialization() throws Throwable {
        String json = mapper.writeValueAsString(new IgnoreBean());
        assertFalse(json.contains("secret"));
        assertTrue(json.contains("\"visible\":\"shown\""));
    }

    // covers findNameForSerialization: JsonGetter takes precedence over JsonProperty
    @Test
    public void testFindNameForSerialization_jsonGetterTakesPrecedence() throws Throwable {
        String json = mapper.writeValueAsString(new GetterPriorityBean());
        assertTrue(json.contains("\"getterName\""));
        assertFalse(json.contains("\"propName\""));
    }

    // covers findNameForSerialization/findNameForDeserialization using explicit @JsonProperty name
    @Test
    public void testJsonProperty_renamesFieldInSerialization() throws Throwable {
        String json = mapper.writeValueAsString(new RenameBean());
        assertTrue(json.contains("\"renamed\":\"value\""));
        assertFalse(json.contains("\"original\""));
    }

    // covers findNameForDeserialization: JsonSetter custom name used
    @Test
    public void testJsonSetter_customNameUsedForDeserialization() throws Throwable {
        SetterBean bean = mapper.readValue("{\"setterName\":\"hi\"}", SetterBean.class);
        assertEquals("hi", bean.getValue());
    }

    // covers findSerializer JsonRawValue branch embedding raw JSON unescaped
    @Test
    public void testJsonRawValue_embedsRawJsonWithoutQuoting() throws Throwable {
        String json = mapper.writeValueAsString(new RawValueBean());
        assertTrue(json.contains("\"json\":{\"x\":1}"));
    }

    // covers findSerializationPropertyOrder explicit ordering
    @Test
    public void testJsonPropertyOrder_explicitOrderRespected() throws Throwable {
        String json = mapper.writeValueAsString(new OrderBean());
        assertTrue(json.indexOf("\"z\"") < json.indexOf("\"a\""));
    }

    // covers findSerializationSortAlphabetically alphabetic branch
    @Test
    public void testJsonPropertyOrder_alphabeticTrue_sortsAlphabetically() throws Throwable {
        String json = mapper.writeValueAsString(new AlphaOrderBean());
        assertTrue(json.indexOf("\"alpha\"") < json.indexOf("\"zeta\""));
    }

    // covers findRootName wrapping value under given root name
    @Test
    public void testJsonRootName_wrapsValueWhenEnabled() throws Throwable {
        ObjectMapper wrapMapper = new ObjectMapper();
        wrapMapper.enable(SerializationFeature.WRAP_ROOT_VALUE);
        String json = wrapMapper.writeValueAsString(new RootBean());
        assertTrue(json.startsWith("{\"root\":"));
    }

    // covers findSerializationInclusion NON_NULL excluding null-valued property
    @Test
    public void testJsonInclude_nonNull_excludesNullField() throws Throwable {
        String json = mapper.writeValueAsString(new IncludeBean());
        assertFalse(json.contains("nullable"));
        assertTrue(json.contains("\"always\":\"A\""));
    }

    // covers findUnwrappingNameTransformer flattening nested properties
    @Test
    public void testJsonUnwrapped_flattensNestedProperties() throws Throwable {
        String json = mapper.writeValueAsString(new UnwrapBean());
        assertFalse(json.contains("address"));
        assertTrue(json.contains("\"city\":\"NYC\""));
        assertTrue(json.contains("\"name\":\"John\""));
    }

    // covers findViews filtering properties by active JsonView
    @Test
    public void testJsonView_filtersPropertiesByActiveView() throws Throwable {
        String json = mapper.writerWithView(ViewPublic.class).writeValueAsString(new ViewBean());
        assertTrue(json.contains("\"pub\""));
        assertFalse(json.contains("\"internal\""));
    }

    // covers findTypeResolver + findSubtypes adding a type id property
    @Test
    public void testJsonTypeInfoAndSubTypes_includesTypeIdProperty() throws Throwable {
        Dog dog = new Dog();
        String json = mapper.writeValueAsString(dog);
        assertTrue(json.contains("\"type\":\"dog\""));
        assertTrue(json.contains("\"name\":\"Rex\""));
    }

    // covers hasCreatorAnnotation true branch used to deserialize via constructor
    @Test
    public void testJsonCreator_usedForDeserialization() throws Throwable {
        CreatorBean bean = mapper.readValue("{\"value\":\"hi\"}", CreatorBean.class);
        assertEquals("hi", bean.getValue());
    }

    // covers hasAnyGetterAnnotation merging dynamic map entries into serialized output
    @Test
    public void testJsonAnyGetter_includesDynamicProperties() throws Throwable {
        String json = mapper.writeValueAsString(new AnyGetterBean());
        assertTrue(json.contains("\"k1\":\"v1\""));
    }

    // covers hasAnySetterAnnotation routing unrecognized properties to any-setter
    @Test
    public void testJsonAnySetter_capturesUnrecognizedProperties() throws Throwable {
        AnySetterBean bean = mapper.readValue("{\"foo\":\"bar\"}", AnySetterBean.class);
        assertEquals("bar", bean.getExtra().get("foo"));
    }

    // covers findObjectIdInfo adding an object id property during serialization
    @Test
    public void testJsonIdentityInfo_addsObjectIdProperty() throws Throwable {
        String json = mapper.writeValueAsString(new IdentityBean());
        assertTrue(json.contains("\"@id\""));
        assertTrue(json.contains("\"name\":\"n\""));
    }

    // covers findPropertyIndex ordering properties ascending by explicit index
    @Test
    public void testJsonPropertyIndex_ordersPropertiesByIndex() throws Throwable {
        String json = mapper.writeValueAsString(new IndexBean());
        assertTrue(json.indexOf("\"first\"") < json.indexOf("\"second\""));
    }

    // covers findReferenceType managed/back reference branches avoiding infinite recursion
    @Test
    public void testJsonManagedAndBackReference_avoidsInfiniteRecursion() throws Throwable {
        ParentRef parent = new ParentRef();
        ChildRef child = new ChildRef();
        child.parent = parent;
        parent.child = child;
        String json = mapper.writeValueAsString(parent);
        assertTrue(json.contains("\"child\""));
        assertFalse(json.contains("\"parent\":{"));
    }

    // covers findPropertyAccess READ_ONLY branch still serialized
    @Test
    public void testFindPropertyAccess_readOnly_includedInSerialization() throws Throwable {
        String json = mapper.writeValueAsString(new AccessBean());
        assertTrue(json.contains("\"readOnly\":\"R\""));
    }

    // covers findPropertyAccess READ_ONLY branch excluded from deserialization (setter ignored)
    @Test
    public void testFindPropertyAccess_readOnly_excludedFromDeserialization() throws Throwable {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AccessBean bean = mapper.readValue("{\"readOnly\":\"X\",\"normal\":\"Y\"}", AccessBean.class);
        assertEquals("R", bean.readOnly);
        assertEquals("Y", bean.normal);
    }

    // covers findPropertyAccess WRITE_ONLY branch excluded from serialization (getter ignored)
    @Test
    public void testFindPropertyAccess_writeOnly_excludedFromSerialization() throws Throwable {
        String json = mapper.writeValueAsString(new WriteOnlyBean());
        assertFalse(json.contains("writeOnly"));
        assertTrue(json.contains("\"normal\":\"N\""));
    }

    // covers findPropertyAccess WRITE_ONLY branch still usable for deserialization
    @Test
    public void testFindPropertyAccess_writeOnly_includedInDeserialization() throws Throwable {
        WriteOnlyBean bean = mapper.readValue("{\"writeOnly\":\"X\",\"normal\":\"Y\"}", WriteOnlyBean.class);
        assertEquals("X", bean.writeOnly);
    }

    // covers findFormat wrapping JsonFormat annotation applied during date serialization
    @Test
    public void testJsonFormat_datePattern_appliesCustomFormatting() throws Throwable {
        ObjectMapper fmtMapper = new ObjectMapper();
        fmtMapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        String json = fmtMapper.writeValueAsString(new DateFormatBean());
        assertTrue(json.contains("\"when\":\"1970-01-01\""));
    }
}
