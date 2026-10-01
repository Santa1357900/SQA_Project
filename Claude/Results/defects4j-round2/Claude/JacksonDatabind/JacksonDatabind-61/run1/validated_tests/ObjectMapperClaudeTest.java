package com.fasterxml.jackson.databind;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public class ObjectMapperClaudeTest {

    private ObjectMapper mapper;

    public static class SimpleBean {
        private String name;
        private int value;
        public SimpleBean() {}
        public SimpleBean(String name, int value) {
            this.name = name;
            this.value = value;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    public static class FieldOnlyBean {
        private String secret;
        private String publicName = "visible";
        public FieldOnlyBean() {}
        public FieldOnlyBean(String secret) { this.secret = secret; }
        public String getPublicName() { return publicName; }
    }

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // covers version() basic accessor
    @Test
    public void testVersion_returnsNonNull() throws Throwable {
        assertNotNull(mapper.version());
    }

    // covers copy() producing a distinct but same-class instance
    @Test
    public void testCopy_returnsDifferentInstanceSameClass() throws Throwable {
        ObjectMapper copyMapper = mapper.copy();
        assertNotSame(mapper, copyMapper);
        assertEquals(mapper.getClass(), copyMapper.getClass());
    }

    // covers basic accessor methods returning non-null shared config/context objects
    @Test
    public void testConfigAccessors_notNull() throws Throwable {
        assertNotNull(mapper.getSerializationConfig());
        assertNotNull(mapper.getDeserializationConfig());
        assertNotNull(mapper.getDeserializationContext());
        assertNotNull(mapper.getSerializerProvider());
    }

    // covers setSerializerFactory/getSerializerFactory round trip
    @Test
    public void testSetSerializerFactory_getterReturnsSameInstance() throws Throwable {
        SerializerFactory f = mapper.getSerializerFactory();
        mapper.setSerializerFactory(f);
        assertSame(f, mapper.getSerializerFactory());
    }

    // covers addMixIn + findMixInClassFor mapping lookup
    @Test
    public void testAddMixIn_findMixInClassFor_returnsMixin() throws Throwable {
        mapper.addMixIn(SimpleBean.class, FieldOnlyBean.class);
        assertEquals(FieldOnlyBean.class, mapper.findMixInClassFor(SimpleBean.class));
    }

    // covers mixInCount before and after addMixIn
    @Test
    public void testMixInCount_reflectsAddedMixin() throws Throwable {
        assertEquals(0, mapper.mixInCount());
        mapper.addMixIn(SimpleBean.class, FieldOnlyBean.class);
        assertEquals(1, mapper.mixInCount());
    }

    // covers setMixIns replacing previously added local definitions
    @Test
    public void testSetMixIns_replacesLocalDefinitions() throws Throwable {
        mapper.addMixIn(SimpleBean.class, FieldOnlyBean.class);
        Map<Class<?>, Class<?>> map = new HashMap<Class<?>, Class<?>>();
        map.put(FieldOnlyBean.class, SimpleBean.class);
        mapper.setMixIns(map);
        assertEquals(SimpleBean.class, mapper.findMixInClassFor(FieldOnlyBean.class));
        assertNull(mapper.findMixInClassFor(SimpleBean.class));
    }

    // covers default field visibility (private field without getter stays hidden)
    @Test
    public void testWriteValueAsString_privateFieldDefaultVisibility_hidden() throws Throwable {
        String json = mapper.writeValueAsString(new FieldOnlyBean("topsecret"));
        assertFalse(json.contains("topsecret"));
        assertTrue(json.contains("visible"));
    }

    // covers setVisibility(PropertyAccessor.FIELD, ANY) exposing previously hidden field
    @Test
    public void testSetVisibility_fieldAny_showsPrivateField() throws Throwable {
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        String json = mapper.writeValueAsString(new FieldOnlyBean("topsecret"));
        assertTrue(json.contains("topsecret"));
    }

    // covers constructType returning JavaType matching raw class
    @Test
    public void testConstructType_matchesRawClass() throws Throwable {
        JavaType t = mapper.constructType(SimpleBean.class);
        assertEquals(SimpleBean.class, t.getRawClass());
    }

    // covers enableDefaultTyping() fluent return
    @Test
    public void testEnableDefaultTyping_default_returnsSameMapper() throws Throwable {
        ObjectMapper result = mapper.enableDefaultTyping();
        assertSame(mapper, result);
    }

    // covers enableDefaultTyping with unsupported As.EXTERNAL_PROPERTY -> IllegalArgumentException
    @Test
    public void testEnableDefaultTyping_externalProperty_throwsIllegalArgumentException() throws Throwable {
        try {
            mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
                    JsonTypeInfo.As.EXTERNAL_PROPERTY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers disableDefaultTyping() fluent return
    @Test
    public void testDisableDefaultTyping_returnsSameMapper() throws Throwable {
        ObjectMapper result = mapper.disableDefaultTyping();
        assertSame(mapper, result);
    }

    // covers default-branch (JAVA_LANG_OBJECT) with java.lang.Object -> true
    @Test
    public void testUseForType_javaLangObject_objectClass_true() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
        JavaType t = mapper.constructType(Object.class);
        assertTrue(b.useForType(t));
    }

    // covers default-branch (JAVA_LANG_OBJECT) with concrete String -> false
    @Test
    public void testUseForType_javaLangObject_stringClass_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
        JavaType t = mapper.constructType(String.class);
        assertFalse(b.useForType(t));
    }

    // covers OBJECT_AND_NON_CONCRETE with a non-concrete interface type -> true
    @Test
    public void testUseForType_objectAndNonConcrete_interfaceType_true() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        JavaType t = mapper.constructType(java.util.List.class);
        assertTrue(b.useForType(t));
    }

    // covers OBJECT_AND_NON_CONCRETE with a concrete final type (String) -> false
    @Test
    public void testUseForType_objectAndNonConcrete_concreteFinalType_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        JavaType t = mapper.constructType(String.class);
        assertFalse(b.useForType(t));
    }

    // covers OBJECT_AND_NON_CONCRETE excluding TreeNode subtypes per databind#88 -> false
    @Test
    public void testUseForType_objectAndNonConcrete_treeNodeType_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        JavaType t = mapper.constructType(JsonNode.class);
        assertFalse(b.useForType(t));
    }

    // BUG TARGET: primitive-valued types must never be eligible for default typing (per databind#1395 comment)
    @Test
    public void testUseForType_objectAndNonConcrete_primitiveType_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        JavaType t = mapper.constructType(int.class);
        assertFalse(b.useForType(t));
    }

    // covers NON_CONCRETE_AND_ARRAYS stripping array to Object content -> true
    @Test
    public void testUseForType_nonConcreteAndArrays_objectArray_true() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS);
        JavaType t = mapper.constructType(Object[].class);
        assertTrue(b.useForType(t));
    }

    // covers NON_CONCRETE_AND_ARRAYS stripping array to concrete final content -> false
    @Test
    public void testUseForType_nonConcreteAndArrays_concreteArray_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS);
        JavaType t = mapper.constructType(String[].class);
        assertFalse(b.useForType(t));
    }

    // BUG TARGET: primitive array content must never be eligible for default typing
    @Test
    public void testUseForType_nonConcreteAndArrays_primitiveArray_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS);
        JavaType t = mapper.constructType(int[].class);
        assertFalse(b.useForType(t));
    }

    // covers NON_FINAL with non-final concrete type (ArrayList) -> true
    @Test
    public void testUseForType_nonFinal_nonFinalConcreteType_true() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
        JavaType t = mapper.constructType(java.util.ArrayList.class);
        assertTrue(b.useForType(t));
    }

    // covers NON_FINAL excluding final "natural" type (String) -> false
    @Test
    public void testUseForType_nonFinal_finalType_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
        JavaType t = mapper.constructType(String.class);
        assertFalse(b.useForType(t));
    }

    // covers NON_FINAL excluding TreeNode subtypes per databind#88 -> false
    @Test
    public void testUseForType_nonFinal_treeNodeType_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
        JavaType t = mapper.constructType(JsonNode.class);
        assertFalse(b.useForType(t));
    }

    // covers NON_FINAL with primitive type -> false
    @Test
    public void testUseForType_nonFinal_primitiveType_false() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder b =
                new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
        JavaType t = mapper.constructType(int.class);
        assertFalse(b.useForType(t));
    }

    // covers readValue(String,Class) normal bean deserialization
    @Test
    public void testReadValueString_simpleBean_deserializesFields() throws Throwable {
        SimpleBean bean = mapper.readValue("{\"name\":\"abc\",\"value\":42}", SimpleBean.class);
        assertEquals("abc", bean.getName());
        assertEquals(42, bean.getValue());
    }

    // covers _initForReading throwing JsonMappingException on truly empty content
    @Test
    public void testReadValueString_emptyContent_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers VALUE_NULL branch returning deserializer's null value (null for bean types)
    @Test
    public void testReadValueString_nullLiteral_returnsNull() throws Throwable {
        SimpleBean bean = mapper.readValue("null", SimpleBean.class);
        assertNull(bean);
    }

    // covers readTree(String) producing populated ObjectNode
    @Test
    public void testReadTree_objectJson_returnsObjectNodeWithValues() throws Throwable {
        JsonNode node = mapper.readTree("{\"a\":1,\"b\":\"x\"}");
        assertTrue(node.isObject());
        assertEquals(1, node.get("a").asInt());
        assertEquals("x", node.get("b").asText());
    }

    // covers readTree returning a non-null node for JSON null literal (isNull() true)
    @Test
    public void testReadTree_nullLiteral_returnsNullNode() throws Throwable {
        JsonNode node = mapper.readTree("null");
        assertTrue(node.isNull());
    }

    // covers readTree(String) producing populated ArrayNode
    @Test
    public void testReadTree_arrayJson_returnsArrayNodeWithSize() throws Throwable {
        JsonNode node = mapper.readTree("[1,2,3]");
        assertTrue(node.isArray());
        assertEquals(3, node.size());
    }

    // covers writeValueAsString then readValue round trip
    @Test
    public void testWriteValueAsString_thenReadBack_roundTrip() throws Throwable {
        SimpleBean bean = new SimpleBean("hello", 3);
        String json = mapper.writeValueAsString(bean);
        SimpleBean back = mapper.readValue(json, SimpleBean.class);
        assertEquals("hello", back.getName());
        assertEquals(3, back.getValue());
    }

    // covers writeValueAsBytes then readValue(byte[],Class) round trip
    @Test
    public void testWriteValueAsBytes_thenReadBack_roundTrip() throws Throwable {
        SimpleBean bean = new SimpleBean("world", 4);
        byte[] bytes = mapper.writeValueAsBytes(bean);
        SimpleBean back = mapper.readValue(bytes, SimpleBean.class);
        assertEquals("world", back.getName());
        assertEquals(4, back.getValue());
    }

    // covers convertValue(Map, Class) converting a structurally compatible map to a bean
    @Test
    public void testConvertValue_mapToBean_convertsFields() throws Throwable {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("name", "bob");
        map.put("value", Integer.valueOf(5));
        SimpleBean bean = mapper.convertValue(map, SimpleBean.class);
        assertEquals("bob", bean.getName());
        assertEquals(5, bean.getValue());
    }

    // covers convertValue null-check short-circuit returning null
    @Test
    public void testConvertValue_nullValue_returnsNull() throws Throwable {
        SimpleBean bean = mapper.convertValue(null, SimpleBean.class);
        assertNull(bean);
    }

    // covers convertValue simple-cast shortcut returning same instance when already of target type
    @Test
    public void testConvertValue_sameAssignableType_returnsSameInstance() throws Throwable {
        SimpleBean original = new SimpleBean("x", 1);
        SimpleBean result = mapper.convertValue(original, SimpleBean.class);
        assertSame(original, result);
    }

    // covers valueToTree producing a populated ObjectNode from a bean
    @Test
    public void testValueToTree_bean_producesObjectNodeWithFields() throws Throwable {
        JsonNode node = mapper.valueToTree(new SimpleBean("tree", 12));
        assertTrue(node.isObject());
        assertEquals(12, node.get("value").asInt());
        assertEquals("tree", node.get("name").asText());
    }

    // covers valueToTree(null) early-return null
    @Test
    public void testValueToTree_null_returnsNull() throws Throwable {
        JsonNode node = mapper.valueToTree(null);
        assertNull(node);
    }

    // covers treeToValue simple-cast shortcut when target type is assignable from node's class
    @Test
    public void testTreeToValue_sameAssignableType_returnsSameInstance() throws Throwable {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode result = mapper.treeToValue(node, ObjectNode.class);
        assertSame(node, result);
    }

    // covers treeToValue converting an ObjectNode into a bean via readValue path
    @Test
    public void testTreeToValue_objectNodeToBean_convertsFields() throws Throwable {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", "z");
        node.put("value", 9);
        SimpleBean bean = mapper.treeToValue(node, SimpleBean.class);
        assertEquals("z", bean.getName());
        assertEquals(9, bean.getValue());
    }

    // covers createObjectNode/createArrayNode producing empty tree nodes
    @Test
    public void testCreateObjectNodeAndArrayNode_emptyNodes() throws Throwable {
        ObjectNode obj = mapper.createObjectNode();
        ArrayNode arr = mapper.createArrayNode();
        assertTrue(obj.isObject());
        assertEquals(0, obj.size());
        assertTrue(arr.isArray());
        assertEquals(0, arr.size());
    }

    // covers canSerialize/canDeserialize returning true for a normal bean type
    @Test
    public void testCanSerializeAndCanDeserialize_bean_true() throws Throwable {
        assertTrue(mapper.canSerialize(SimpleBean.class));
        JavaType t = mapper.constructType(SimpleBean.class);
        assertTrue(mapper.canDeserialize(t));
    }

    // covers DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES enabled (throws) vs disabled (ignores extra)
    @Test
    public void testDeserializationFeature_failOnUnknownProperties_behaviors() throws Throwable {
        String json = "{\"name\":\"a\",\"value\":1,\"extra\":true}";
        try {
            mapper.readValue(json, SimpleBean.class);
            fail("expected JsonMappingException due to unknown property");
        } catch (JsonMappingException expected) { }

        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SimpleBean bean = mapper.readValue(json, SimpleBean.class);
        assertEquals("a", bean.getName());
        assertEquals(1, bean.getValue());
    }

    // covers configure(MapperFeature,boolean) toggling state reflected by isEnabled
    @Test
    public void testConfigureMapperFeature_setsAndReflectsState() throws Throwable {
        mapper.configure(MapperFeature.USE_ANNOTATIONS, false);
        assertFalse(mapper.isEnabled(MapperFeature.USE_ANNOTATIONS));
        mapper.configure(MapperFeature.USE_ANNOTATIONS, true);
        assertTrue(mapper.isEnabled(MapperFeature.USE_ANNOTATIONS));
    }
}
