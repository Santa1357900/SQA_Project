package com.fasterxml.jackson.databind.ser;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.Annotations;

public class PropertyBuilderClaudeTest
{
    private ObjectMapper mapper;
    private SerializationConfig config;
    private PropertyBuilder pb;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
        config = mapper.getSerializationConfig();
        BeanDescription beanDesc = config.introspect(mapper.constructType(SimpleBean.class));
        pb = new PropertyBuilder(config, beanDesc);
    }

    private BeanDescription introspect(Class<?> cls) {
        return config.introspect(mapper.constructType(cls));
    }

    private JavaType type(Class<?> cls) {
        return mapper.constructType(cls);
    }

    private BeanPropertyDefinition findProperty(BeanDescription bd, String name) {
        List<BeanPropertyDefinition> props = bd.findProperties();
        for (int i = 0; i < props.size(); i++) {
            BeanPropertyDefinition p = props.get(i);
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    // ---- POJOs ----

    public static class SimpleBean {
        private int intValue = 5;
        private String stringValue = "hello";

        public SimpleBean() { }

        public int getIntValue() { return intValue; }
        public void setIntValue(int v) { this.intValue = v; }
        public String getStringValue() { return stringValue; }
        public void setStringValue(String v) { this.stringValue = v; }
    }

    public static class NoDefaultCtorBean {
        private final int value;
        public NoDefaultCtorBean(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    public static class NullSuppressBean {
        private String name;
        public NullSuppressBean() { }
        public NullSuppressBean(String name) { this.name = name; }
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
    }

    public static class NonEmptyBean {
        private String text;
        public NonEmptyBean() { }
        public NonEmptyBean(String text) { this.text = text; }
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getText() { return text; }
        public void setText(String v) { this.text = v; }
    }

    public static class ArrayBean {
        private int[] items = new int[0];
        public ArrayBean() { }
        public int[] getItems() { return items; }
        public void setItems(int[] v) { this.items = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class NonDefaultClassBean {
        private int num;
        public NonDefaultClassBean() { }
        public NonDefaultClassBean(int n) { this.num = n; }
        public int getNum() { return num; }
        public void setNum(int n) { this.num = n; }
    }

    public static class NonDefaultPropertyWrapperBean {
        private Integer num;
        public NonDefaultPropertyWrapperBean() { }
        public NonDefaultPropertyWrapperBean(Integer n) { this.num = n; }
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public Integer getNum() { return num; }
        public void setNum(Integer n) { this.num = n; }
    }

    public static class NonAbsentBean {
        private String label;
        public NonAbsentBean() { }
        public NonAbsentBean(String label) { this.label = label; }
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public String getLabel() { return label; }
        public void setLabel(String v) { this.label = v; }
    }

    // ---- Constructor ----

    // Covers constructor: fields _config/_beanDesc assigned from parameters (reference equality)
    @Test
    public void testConstructor_setsFieldsFromConfigAndBeanDesc() throws Throwable {
        BeanDescription beanDesc = introspect(SimpleBean.class);
        PropertyBuilder pb2 = new PropertyBuilder(config, beanDesc);
        assertSame(config, pb2._config);
        assertSame(beanDesc, pb2._beanDesc);
    }

    // Covers constructor: _annotationIntrospector assigned from config.getAnnotationIntrospector()
    @Test
    public void testConstructor_annotationIntrospectorMatchesConfig() throws Throwable {
        BeanDescription beanDesc = introspect(SimpleBean.class);
        PropertyBuilder pb2 = new PropertyBuilder(config, beanDesc);
        assertSame(config.getAnnotationIntrospector(), pb2._annotationIntrospector);
    }

    // ---- getClassAnnotations ----

    // Covers getClassAnnotations(): delegates to beanDesc.getClassAnnotations(), non-null
    @Test
    public void testGetClassAnnotations_returnsNonNullAnnotations() throws Throwable {
        Annotations ann = pb.getClassAnnotations();
        assertNotNull(ann);
    }

    // ---- getDefaultValue ----

    // Covers getDefaultValue(): primitive int -> defaultValue branch, expect 0
    @Test
    public void testGetDefaultValue_primitiveInt_returnsZero() throws Throwable {
        Object def = pb.getDefaultValue(type(int.class));
        assertEquals(Integer.valueOf(0), def);
    }

    // Covers getDefaultValue(): primitive boolean -> expect false
    @Test
    public void testGetDefaultValue_primitiveBoolean_returnsFalse() throws Throwable {
        Object def = pb.getDefaultValue(type(boolean.class));
        assertEquals(Boolean.FALSE, def);
    }

    // Covers getDefaultValue(): primitive long -> expect 0L
    @Test
    public void testGetDefaultValue_primitiveLong_returnsZero() throws Throwable {
        Object def = pb.getDefaultValue(type(long.class));
        assertEquals(Long.valueOf(0L), def);
    }

    // Covers getDefaultValue(): primitive double -> expect 0.0
    @Test
    public void testGetDefaultValue_primitiveDouble_returnsZero() throws Throwable {
        Object def = pb.getDefaultValue(type(double.class));
        assertTrue(def instanceof Double);
        assertEquals(0.0, ((Double) def).doubleValue(), 0.0001);
    }

    // Bug hunt: per Javadoc, wrapper type Integer must resolve same default as primitive int (0)
    @Test
    public void testGetDefaultValue_wrapperInteger_returnsZero() throws Throwable {
        Object def = pb.getDefaultValue(type(Integer.class));
        assertEquals(Integer.valueOf(0), def);
    }

    // Covers getDefaultValue(): wrapper Long -> expect 0L per Javadoc contract
    @Test
    public void testGetDefaultValue_wrapperLong_returnsZero() throws Throwable {
        Object def = pb.getDefaultValue(type(Long.class));
        assertEquals(Long.valueOf(0L), def);
    }

    // Covers getDefaultValue(): wrapper Boolean -> expect false per Javadoc contract
    @Test
    public void testGetDefaultValue_wrapperBoolean_returnsFalse() throws Throwable {
        Object def = pb.getDefaultValue(type(Boolean.class));
        assertEquals(Boolean.FALSE, def);
    }

    // Covers getDefaultValue(): wrapper Double -> expect 0.0 per Javadoc contract
    @Test
    public void testGetDefaultValue_wrapperDouble_returnsZero() throws Throwable {
        Object def = pb.getDefaultValue(type(Double.class));
        assertTrue(def instanceof Double);
        assertEquals(0.0, ((Double) def).doubleValue(), 0.0001);
    }

    // Covers getDefaultValue(): String type -> expect empty string ""
    @Test
    public void testGetDefaultValue_string_returnsEmptyString() throws Throwable {
        Object def = pb.getDefaultValue(type(String.class));
        assertEquals("", def);
    }

    // Covers getDefaultValue(): container (List) type -> expect NON_EMPTY marker constant
    @Test
    public void testGetDefaultValue_collectionType_returnsNonEmptyMarker() throws Throwable {
        Object def = pb.getDefaultValue(type(java.util.List.class));
        assertEquals(JsonInclude.Include.NON_EMPTY, def);
    }

    // Covers getDefaultValue(): array type -> expect NON_EMPTY marker constant
    @Test
    public void testGetDefaultValue_arrayType_returnsNonEmptyMarker() throws Throwable {
        Object def = pb.getDefaultValue(type(int[].class));
        assertEquals(JsonInclude.Include.NON_EMPTY, def);
    }

    // Covers getDefaultValue(): Map type -> expect NON_EMPTY marker constant
    @Test
    public void testGetDefaultValue_mapType_returnsNonEmptyMarker() throws Throwable {
        Object def = pb.getDefaultValue(type(java.util.Map.class));
        assertEquals(JsonInclude.Include.NON_EMPTY, def);
    }

    // Covers getDefaultValue(): plain POJO type with no special rule -> expect null
    @Test
    public void testGetDefaultValue_plainPojoType_returnsNull() throws Throwable {
        Object def = pb.getDefaultValue(type(SimpleBean.class));
        assertNull(def);
    }

    // ---- getDefaultBean ----

    // Covers getDefaultBean(): class with public no-arg constructor -> returns an instance
    @Test
    public void testGetDefaultBean_beanWithDefaultCtor_returnsInstance() throws Throwable {
        Object def = pb.getDefaultBean();
        assertNotNull(def);
        assertTrue(def instanceof SimpleBean);
    }

    // Covers getDefaultBean(): memoization - second call returns same cached reference
    @Test
    public void testGetDefaultBean_cachesResultAcrossCalls() throws Throwable {
        Object first = pb.getDefaultBean();
        Object second = pb.getDefaultBean();
        assertSame(first, second);
    }

    // Covers getDefaultBean(): class without default constructor -> instantiation fails -> null
    @Test
    public void testGetDefaultBean_beanWithoutDefaultCtor_returnsNull() throws Throwable {
        BeanDescription bd = introspect(NoDefaultCtorBean.class);
        PropertyBuilder pb2 = new PropertyBuilder(config, bd);
        Object def = pb2.getDefaultBean();
        assertNull(def);
    }

    // ---- getPropertyDefaultValue ----

    // Covers getPropertyDefaultValue(): uses actual default-bean instance value (5), not zero
    @Test
    public void testGetPropertyDefaultValue_intPropertyWithInitializer_returnsActualValue() throws Throwable {
        BeanDescription bd = introspect(SimpleBean.class);
        BeanPropertyDefinition prop = findProperty(bd, "intValue");
        assertNotNull(prop);
        AnnotatedMember am = prop.getAccessor();
        PropertyBuilder pb2 = new PropertyBuilder(config, bd);
        Object def = pb2.getPropertyDefaultValue("intValue", am, type(int.class));
        assertEquals(Integer.valueOf(5), def);
    }

    // Covers getPropertyDefaultValue(): uses actual default-bean instance value for String field
    @Test
    public void testGetPropertyDefaultValue_stringPropertyWithInitializer_returnsActualValue() throws Throwable {
        BeanDescription bd = introspect(SimpleBean.class);
        BeanPropertyDefinition prop = findProperty(bd, "stringValue");
        assertNotNull(prop);
        AnnotatedMember am = prop.getAccessor();
        PropertyBuilder pb2 = new PropertyBuilder(config, bd);
        Object def = pb2.getPropertyDefaultValue("stringValue", am, type(String.class));
        assertEquals("hello", def);
    }

    // Covers getPropertyDefaultValue(): no default bean available -> falls back to getDefaultValue(type)
    @Test
    public void testGetPropertyDefaultValue_noDefaultBean_fallsBackToGetDefaultValue() throws Throwable {
        BeanDescription bd = introspect(NoDefaultCtorBean.class);
        BeanPropertyDefinition prop = findProperty(bd, "value");
        assertNotNull(prop);
        AnnotatedMember am = prop.getAccessor();
        PropertyBuilder pb2 = new PropertyBuilder(config, bd);
        Object def = pb2.getPropertyDefaultValue("value", am, type(int.class));
        assertEquals(Integer.valueOf(0), def);
    }

    // ---- findSerializationType ----

    // Covers findSerializationType(): no static typing, no refining annotation -> returns null
    @Test
    public void testFindSerializationType_noStaticTyping_returnsNull() throws Throwable {
        BeanDescription bd = introspect(SimpleBean.class);
        BeanPropertyDefinition prop = findProperty(bd, "intValue");
        AnnotatedMember am = prop.getAccessor();
        PropertyBuilder pb2 = new PropertyBuilder(config, bd);
        JavaType result = pb2.findSerializationType(am, false, type(int.class));
        assertNull(result);
    }

    // Covers findSerializationType(): useStaticTyping=true -> returns declared type marked static
    @Test
    public void testFindSerializationType_staticTyping_returnsDeclaredTypeStatic() throws Throwable {
        BeanDescription bd = introspect(SimpleBean.class);
        BeanPropertyDefinition prop = findProperty(bd, "stringValue");
        AnnotatedMember am = prop.getAccessor();
        PropertyBuilder pb2 = new PropertyBuilder(config, bd);
        JavaType declared = type(String.class);
        JavaType result = pb2.findSerializationType(am, true, declared);
        assertNotNull(result);
        assertEquals(String.class, result.getRawClass());
    }

    // ---- buildWriter (exercised end-to-end via ObjectMapper) ----

    // Covers buildWriter(): NON_NULL inclusion suppresses a null property value
    @Test
    public void testBuildWriter_viaObjectMapper_nonNullSuppressesNullField() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NullSuppressBean(null));
        assertFalse(json.contains("\"name\""));
    }

    // Covers buildWriter(): NON_NULL inclusion keeps a non-null property value
    @Test
    public void testBuildWriter_viaObjectMapper_nonNullIncludesNonNullField() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NullSuppressBean("joe"));
        assertTrue(json.contains("\"name\":\"joe\""));
    }

    // Covers buildWriter(): NON_EMPTY inclusion suppresses empty string value
    @Test
    public void testBuildWriter_viaObjectMapper_nonEmptySuppressesEmptyString() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NonEmptyBean(""));
        assertFalse(json.contains("\"text\""));
    }

    // Covers buildWriter(): NON_EMPTY inclusion keeps non-empty string value
    @Test
    public void testBuildWriter_viaObjectMapper_nonEmptyIncludesNonEmptyString() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NonEmptyBean("abc"));
        assertTrue(json.contains("\"text\":\"abc\""));
    }

    // Covers buildWriter(): NON_EMPTY also suppresses null values (suppressNulls=true)
    @Test
    public void testBuildWriter_viaObjectMapper_nonEmptySuppressesNull() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NonEmptyBean(null));
        assertFalse(json.contains("\"text\""));
    }

    // Covers buildWriter(): default ALWAYS inclusion + WRITE_EMPTY_JSON_ARRAYS enabled (default) -> empty array kept
    @Test
    public void testBuildWriter_viaObjectMapper_alwaysIncludesEmptyArrayByDefault() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new ArrayBean());
        assertTrue(json.contains("\"items\":[]"));
    }

    // Covers buildWriter(): default inclusion + WRITE_EMPTY_JSON_ARRAYS disabled -> empty array suppressed
    @Test
    public void testBuildWriter_viaObjectMapper_alwaysExcludesEmptyArrayWhenFeatureDisabled() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        m.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
        String json = m.writeValueAsString(new ArrayBean());
        assertFalse(json.contains("\"items\""));
    }

    // Covers buildWriter(): NON_DEFAULT class-level, value equal to default-bean's primitive value -> suppressed
    @Test
    public void testBuildWriter_viaObjectMapper_nonDefaultClassLevelSuppressesMatchingPrimitive() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NonDefaultClassBean(0));
        assertFalse(json.contains("\"num\""));
    }

    // Covers buildWriter(): NON_DEFAULT class-level, value differing from default-bean's value -> included
    @Test
    public void testBuildWriter_viaObjectMapper_nonDefaultClassLevelIncludesDifferingValue() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NonDefaultClassBean(9));
        assertTrue(json.contains("\"num\":9"));
    }

    // Bug hunt: NON_DEFAULT property-level override on wrapper Integer must suppress value equal to 0 (per Javadoc)
    @Test
    public void testBuildWriter_viaObjectMapper_nonDefaultPropertyLevelWrapperSuppressesZero() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NonDefaultPropertyWrapperBean(Integer.valueOf(0)));
        assertFalse(json.contains("\"num\""));
    }

    // Covers buildWriter(): NON_DEFAULT property-level wrapper with non-zero value is included
    @Test
    public void testBuildWriter_viaObjectMapper_nonDefaultPropertyLevelWrapperIncludesNonZero() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(new NonDefaultPropertyWrapperBean(Integer.valueOf(7)));
        assertTrue(json.contains("\"num\":7"));
    }

    // Covers buildWriter(): NON_ABSENT suppresses null but keeps empty string (String is not a reference type)
    @Test
    public void testBuildWriter_viaObjectMapper_nonAbsentSuppressesNullButKeepsEmpty() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        String jsonNull = m.writeValueAsString(new NonAbsentBean(null));
        String jsonEmpty = m.writeValueAsString(new NonAbsentBean(""));
        assertFalse(jsonNull.contains("\"label\""));
        assertTrue(jsonEmpty.contains("\"label\":\"\""));
    }
}
