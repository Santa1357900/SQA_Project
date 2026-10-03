package com.fasterxml.jackson.databind.introspect;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public class POJOPropertiesCollectorClaudeTest {

    // ---------------- Test POJOs ----------------

    public static class PublicFieldBean {
        public String name;
    }

    public static class GetterBean {
        private int value;
        public int getValue() { return value; }
        public void setValue(int v) { this.value = v; }
    }

    public static class BooleanBean {
        private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean b) { this.active = b; }
    }

    public static class SetterOnlyBean {
        public int storage;
        public void setValue(int v) { storage = v; }
    }

    public static class IgnoreBean {
        public String name;
        @JsonIgnore
        public String secret;
    }

    public static class RenameBean {
        @JsonProperty("customName")
        public int value;
    }

    public static class BarePropBean {
        @JsonProperty
        private String data;
        public BarePropBean() { }
        public BarePropBean(String data) { this.data = data; }
    }

    public static class TransientBean {
        public String keep = "k";
        public transient String skip = "s";
    }

    public static class FinalFieldBean {
        public final int fixedValue = 5;
        public String other = "o";
    }

    public static class ValueBean {
        private int code;
        public ValueBean() { }
        public ValueBean(int code) { this.code = code; }
        @JsonValue
        public int toValue() { return code; }
    }

    public static class AnyGetterBean {
        public String name = "n";
        private Map<String, Object> extra = new LinkedHashMap<String, Object>();
        public AnyGetterBean() {
            extra.put("dyn", "v1");
        }
        @JsonAnyGetter
        public Map<String, Object> getExtra() { return extra; }
    }

    public static class AnySetterMethodBean {
        public Map<String, Object> extra = new LinkedHashMap<String, Object>();
        @JsonAnySetter
        public void set(String name, Object value) { extra.put(name, value); }
    }

    public static class MultiAnySetterFieldBean {
        @JsonAnySetter
        public Map<String, Object> extra1 = new HashMap<String, Object>();
        @JsonAnySetter
        public Map<String, Object> extra2 = new HashMap<String, Object>();
    }

    public static class MultiAnySetterMethodBean {
        @JsonAnySetter
        public void setA(String name, Object value) { }
        @JsonAnySetter
        public void setB(String name, Object value) { }
    }

    public static class CreatorBean {
        private final String id;
        private final int amount;
        @JsonCreator
        public CreatorBean(@JsonProperty("id") String id, @JsonProperty("amount") int amount) {
            this.id = id;
            this.amount = amount;
        }
        public String getId() { return id; }
        public int getAmount() { return amount; }
    }

    public static class SnakeBean {
        public String firstName = "John";
    }

    public static class SimpleBean {
        public String name = "n";
    }

    @JsonPropertyOrder(alphabetic = true)
    public static class AlphaOrderBean {
        public String zeta = "z";
        public String alpha = "a";
    }

    @JsonPropertyOrder({"b", "a"})
    public static class ExplicitOrderBean {
        public String a = "av";
        public String b = "bv";
    }

    @JsonIgnoreProperties({"secret"})
    public static class ClassIgnoreBean {
        public String name = "n";
        public String secret = "s";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgnoreUnknownBean {
        public String name;
    }

    public static class FieldAndGetterBean {
        public int value = 1;
        public int getValue() { return value; }
    }

    public static class BareGetterBean {
        @JsonProperty
        public String getFoo() { return "bar"; }
    }

    public static class BareSetterBean {
        private String data;
        @JsonProperty
        public void setData(String d) { this.data = d; }
        public String getDataForCheck() { return data; }
    }

    // ---------------- Tests ----------------

    // covers _addFields: plain public field with no annotation -> included via visibility
    @Test
    public void testSerialize_publicField_included() throws Throwable {
        PublicFieldBean b = new PublicFieldBean();
        b.name = "Alice";
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"name\":\"Alice\""));
    }

    // covers _addGetterMethod: okNameForRegularGetter branch
    @Test
    public void testSerialize_getterMethod_included() throws Throwable {
        GetterBean b = new GetterBean();
        b.setValue(42);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"value\":42"));
    }

    // covers _addGetterMethod: okNameForIsGetter branch (boolean)
    @Test
    public void testSerialize_isGetterBoolean_included() throws Throwable {
        BooleanBean b = new BooleanBean();
        b.setActive(true);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"active\":true"));
    }

    // covers _addSetterMethod: okNameForMutator implicit-name branch
    @Test
    public void testDeserialize_setterOnly_setsField() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SetterOnlyBean b = mapper.readValue("{\"value\":7}", SetterOnlyBean.class);
        assertEquals(7, b.storage);
    }

    // covers _addFields ignore marker + _removeUnwantedProperties removal
    @Test
    public void testSerialize_fieldIgnore_excluded() throws Throwable {
        IgnoreBean b = new IgnoreBean();
        b.name = "A";
        b.secret = "S";
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertFalse(json.contains("secret"));
        assertTrue(json.contains("\"name\":\"A\""));
    }

    // covers explicit @JsonProperty name -> _renameProperties simple rename
    @Test
    public void testSerialize_explicitPropertyName_renamed() throws Throwable {
        RenameBean b = new RenameBean();
        b.value = 5;
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"customName\":5"));
        assertFalse(json.contains("\"value\""));
    }

    // covers _addFields: nameExplicit && pn.isEmpty() -> implicit name, force visible for private field
    @Test
    public void testSerialize_barePropertyAnnotationOnPrivateField_included() throws Throwable {
        BarePropBean b = new BarePropBean("hello");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"data\":\"hello\""));
    }

    // covers _addFields: transient field without explicit name -> visible = false
    @Test
    public void testSerialize_transientFieldWithoutAnnotation_excluded() throws Throwable {
        TransientBean b = new TransientBean();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"keep\":\"k\""));
        assertFalse(json.contains("skip"));
    }

    // covers _addFields: pruneFinalFields branch (continue) removing final field for deserialization
    @Test
    public void testDeserialize_finalFieldNoAnnotation_unknownPropertyFails() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"fixedValue\":10,\"other\":\"z\"}", FinalFieldBean.class);
            fail("expected JsonMappingException for unrecognized final field property");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers _addFields: pruneFinalFields only applies when !_forSerialization
    @Test
    public void testSerialize_finalField_includedForSerialization() throws Throwable {
        FinalFieldBean b = new FinalFieldBean();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"fixedValue\":5"));
    }

    // covers _addGetterMethod: ai.hasAsValueAnnotation -> getJsonValueMethod single entry
    @Test
    public void testSerialize_jsonValueMethod_usedAsScalar() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new ValueBean(9));
        assertEquals("9", json);
    }

    // covers _addGetterMethod: ai.hasAnyGetterAnnotation -> getAnyGetter, flattened output
    @Test
    public void testSerialize_anyGetter_includesExtraProperties() throws Throwable {
        AnyGetterBean b = new AnyGetterBean();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"name\":\"n\""));
        assertTrue(json.contains("\"dyn\":\"v1\""));
    }

    // covers _addMethods argCount==2 with hasAnySetterAnnotation, single entry -> no error
    @Test
    public void testDeserialize_anySetterMethod_singleField_capturesExtras() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        AnySetterMethodBean b = mapper.readValue("{\"foo\":\"bar\"}", AnySetterMethodBean.class);
        assertEquals("bar", b.extra.get("foo"));
    }

    // BUG: getAnySetterField() references _anySetters (null) instead of _anySetterField when
    // building the "multiple any-setters" error message, causing NullPointerException instead
    // of the expected JsonMappingException (IllegalArgumentException wrapped by DeserializerCache)
    @Test
    public void testDeserialize_multipleAnySetterFields_throwsJsonMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{}", MultiAnySetterFieldBean.class);
            fail("expected JsonMappingException due to multiple any-setter fields");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers getAnySetterMethod(): size()>1 branch with consistent internal references
    @Test
    public void testDeserialize_multipleAnySetterMethods_throwsJsonMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{}", MultiAnySetterMethodBean.class);
            fail("expected JsonMappingException due to multiple any-setter methods");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers _addCreators / _addCreatorParam with explicit @JsonProperty names on constructor params
    @Test
    public void testDeserialize_creatorConstructor_bindsProperties() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        CreatorBean b = mapper.readValue("{\"id\":\"abc\",\"amount\":3}", CreatorBean.class);
        assertEquals("abc", b.getId());
        assertEquals(3, b.getAmount());
    }

    // covers _renameUsing with custom PropertyNamingStrategy applied to fields
    @Test
    public void testSerialize_propertyNamingStrategySnakeCase_renamesField() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        String json = mapper.writeValueAsString(new SnakeBean());
        assertTrue(json.contains("\"first_name\":\"John\""));
    }

    // covers class-level @JsonIgnoreProperties(ignoreUnknown=true) allowing unrecognized keys
    @Test
    public void testDeserialize_classLevelIgnoreUnknown_noException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        IgnoreUnknownBean b = mapper.readValue("{\"name\":\"n\",\"extra\":\"z\"}", IgnoreUnknownBean.class);
        assertEquals("n", b.name);
    }

    // baseline: default FAIL_ON_UNKNOWN_PROPERTIES causes failure for non-collected property
    @Test
    public void testDeserialize_unknownProperty_defaultFailOnUnknown_throwsException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"name\":\"n\",\"bogus\":1}", SimpleBean.class);
            fail("expected JsonMappingException for unknown property 'bogus'");
        } catch (JsonMappingException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // covers _sortProperties: alphabetic sort true -> TreeMap based ordering
    @Test
    public void testSerialize_propertyOrderAlphabetical_sortsFields() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new AlphaOrderBean());
        int idxAlpha = json.indexOf("alpha");
        int idxZeta = json.indexOf("zeta");
        assertTrue(idxAlpha >= 0 && idxZeta >= 0 && idxAlpha < idxZeta);
    }

    // covers _sortProperties: explicit @JsonPropertyOrder array controls ordering
    @Test
    public void testSerialize_explicitPropertyOrder_ordersFields() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new ExplicitOrderBean());
        int idxB = json.indexOf("\"b\"");
        int idxA = json.indexOf("\"a\"");
        assertTrue(idxB >= 0 && idxA >= 0 && idxB < idxA);
    }

    // covers class-level @JsonIgnoreProperties excluding a named property from output
    @Test
    public void testSerialize_classLevelIgnoreProperties_excludesField() throws Throwable {
        ClassIgnoreBean b = new ClassIgnoreBean();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertFalse(json.contains("secret"));
        assertTrue(json.contains("\"name\":\"n\""));
    }

    // covers _property() reuse: field and getter sharing implicit name merge into single property
    @Test
    public void testSerialize_fieldAndGetterSameName_mergedSingleProperty() throws Throwable {
        FieldAndGetterBean b = new FieldAndGetterBean();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertEquals("{\"value\":1}", json);
    }

    // covers _addGetterMethod: explicit but empty @JsonProperty on getter -> implicit name used
    @Test
    public void testSerialize_explicitNameOnGetterEmptyValue_usesImplicitName() throws Throwable {
        BareGetterBean b = new BareGetterBean();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"foo\":\"bar\""));
    }

    // covers _addSetterMethod: explicit but empty @JsonProperty on setter -> implicit name used
    @Test
    public void testDeserialize_barePropertyOnSetter_usesImplicitName() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        BareSetterBean b = mapper.readValue("{\"data\":\"hi\"}", BareSetterBean.class);
        assertEquals("hi", b.getDataForCheck());
    }
}
