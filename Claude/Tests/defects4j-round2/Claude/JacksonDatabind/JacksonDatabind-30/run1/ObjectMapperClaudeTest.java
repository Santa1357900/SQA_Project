package com.fasterxml.jackson.databind;

import java.util.Set;
import java.util.LinkedHashSet;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.impl.StdSubtypeResolver;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ObjectMapperClaudeTest {

    public static class SamplePojo {
        private String name;
        private int value;

        public SamplePojo() { }

        public SamplePojo(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    public static class MixInSource {
    }

    // DefaultTypeResolverBuilder.useForType: JAVA_LANG_OBJECT branch should match only java.lang.Object
    @Test
    public void testDefaultTypeResolverBuilder_useForType_javaLangObject_returnsTrueForObjectClass() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder builder =
            new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
        JavaType objType = TypeFactory.defaultInstance().constructType(Object.class);
        assertTrue(builder.useForType(objType));
    }

    // JAVA_LANG_OBJECT branch: non-Object type must not be flagged for default typing
    @Test
    public void testDefaultTypeResolverBuilder_useForType_javaLangObject_returnsFalseForOtherClass() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder builder =
            new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
        JavaType strType = TypeFactory.defaultInstance().constructType(String.class);
        assertFalse(builder.useForType(strType));
    }

    // NON_FINAL branch: final concrete classes (e.g. String) must be excluded
    @Test
    public void testDefaultTypeResolverBuilder_useForType_nonFinal_returnsFalseForFinalClass() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder builder =
            new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
        JavaType strType = TypeFactory.defaultInstance().constructType(String.class);
        assertFalse(builder.useForType(strType));
    }

    // NON_FINAL branch: non-final concrete classes should use default typing
    @Test
    public void testDefaultTypeResolverBuilder_useForType_nonFinal_returnsTrueForNonFinalConcreteClass() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder builder =
            new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
        JavaType pojoType = TypeFactory.defaultInstance().constructType(SamplePojo.class);
        assertTrue(builder.useForType(pojoType));
    }

    // OBJECT_AND_NON_CONCRETE branch: JSON tree model types (TreeNode subtypes) must be excluded per [databind#88]
    @Test
    public void testDefaultTypeResolverBuilder_useForType_objectAndNonConcrete_excludesTreeNode() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder builder =
            new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        JavaType nodeType = TypeFactory.defaultInstance().constructType(JsonNode.class);
        assertFalse(builder.useForType(nodeType));
    }

    // Default constructor must fall back to MappingJsonFactory when no JsonFactory is supplied
    @Test
    public void testDefaultConstructor_usesMappingJsonFactory() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(mapper.getFactory() instanceof MappingJsonFactory);
    }

    // Constructor(JsonFactory): mapper should set itself as codec when factory has none configured
    @Test
    public void testConstructorWithJsonFactory_setsCodecWhenMissing() throws Throwable {
        JsonFactory jf = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(jf);
        assertSame(jf, mapper.getFactory());
        assertSame(mapper, jf.getCodec());
    }

    // version() must return a non-null Version object
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertNotNull(mapper.version());
    }

    // copy() returns a distinct instance that preserves scalar configuration state
    @Test
    public void testCopy_returnsDifferentInstanceWithEquivalentConfig() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        ObjectMapper copy = mapper.copy();
        assertNotSame(mapper, copy);
        assertTrue(copy.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    }

    // Bug check: copy constructor must copy src._registeredModuleTypes, not the (still-unset) field on 'this'
    @Test
    public void testCopy_preservesRegisteredModuleTypes() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Set<Object> moduleIds = new LinkedHashSet<Object>();
        moduleIds.add("sample-module-id");
        mapper._registeredModuleTypes = moduleIds;
        ObjectMapper copy = mapper.copy();
        assertNotNull(copy._registeredModuleTypes);
        assertTrue(copy._registeredModuleTypes.contains("sample-module-id"));
    }

    // copy(): when source has no registered module types yet, copy should also have none (null)
    @Test
    public void testCopy_whenNoModulesRegistered_copiedModuleTypesRemainsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertNull(mapper._registeredModuleTypes);
        ObjectMapper copy = mapper.copy();
        assertNull(copy._registeredModuleTypes);
    }

    // setSerializerFactory/getSerializerFactory round trip
    @Test
    public void testSetSerializerFactory_getSerializerFactory_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializerFactory factory = BeanSerializerFactory.instance;
        ObjectMapper result = mapper.setSerializerFactory(factory);
        assertSame(mapper, result);
        assertSame(factory, mapper.getSerializerFactory());
    }

    // setSerializerProvider/getSerializerProvider round trip
    @Test
    public void testSetSerializerProvider_getSerializerProvider_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DefaultSerializerProvider.Impl provider = new DefaultSerializerProvider.Impl();
        ObjectMapper result = mapper.setSerializerProvider(provider);
        assertSame(mapper, result);
        assertSame(provider, mapper.getSerializerProvider());
    }

    // addMixIn registers a local mix-in mapping discoverable via findMixInClassFor/mixInCount
    @Test
    public void testAddMixIn_findMixInClassFor_mixInCount() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(0, mapper.mixInCount());
        mapper.addMixIn(SamplePojo.class, MixInSource.class);
        assertEquals(1, mapper.mixInCount());
        assertEquals(MixInSource.class, mapper.findMixInClassFor(SamplePojo.class));
    }

    // findMixInClassFor returns null when no mix-in registered for the class
    @Test
    public void testFindMixInClassFor_noMixIn_returnsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertNull(mapper.findMixInClassFor(SamplePojo.class));
    }

    // setSubtypeResolver/getSubtypeResolver round trip
    @Test
    public void testSetSubtypeResolver_getSubtypeResolver_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SubtypeResolver resolver = new StdSubtypeResolver();
        ObjectMapper result = mapper.setSubtypeResolver(resolver);
        assertSame(mapper, result);
        assertSame(resolver, mapper.getSubtypeResolver());
    }

    // setAnnotationIntrospector is chainable (returns same mapper instance)
    @Test
    public void testSetAnnotationIntrospector_isChainable() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector ai = new JacksonAnnotationIntrospector();
        ObjectMapper result = mapper.setAnnotationIntrospector(ai);
        assertSame(mapper, result);
    }

    // setSerializationInclusion is chainable
    @Test
    public void testSetSerializationInclusion_isChainable() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectMapper result = mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        assertSame(mapper, result);
    }

    // enableDefaultTyping() convenience overload is chainable
    @Test
    public void testEnableDefaultTyping_default_isChainable() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectMapper result = mapper.enableDefaultTyping();
        assertSame(mapper, result);
    }

    // enableDefaultTyping must reject As.EXTERNAL_PROPERTY per explicit Javadoc contract
    @Test
    public void testEnableDefaultTyping_externalProperty_throwsIllegalArgumentException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, JsonTypeInfo.As.EXTERNAL_PROPERTY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // disableDefaultTyping is chainable and clears the typer
    @Test
    public void testDisableDefaultTyping_isChainable() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping();
        ObjectMapper result = mapper.disableDefaultTyping();
        assertSame(mapper, result);
    }

    // setTypeFactory/getTypeFactory round trip
    @Test
    public void testSetTypeFactory_getTypeFactory_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        TypeFactory tf = TypeFactory.defaultInstance();
        ObjectMapper result = mapper.setTypeFactory(tf);
        assertSame(mapper, result);
        assertSame(tf, mapper.getTypeFactory());
    }

    // constructType resolves the JavaType matching the given Class
    @Test
    public void testConstructType_forClass_returnsMatchingJavaType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.constructType(String.class);
        assertEquals(String.class, type.getRawClass());
    }

    // setNodeFactory is chainable and updates getNodeFactory()
    @Test
    public void testSetNodeFactory_isChainable() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonNodeFactory nf = mapper.getNodeFactory();
        ObjectMapper result = mapper.setNodeFactory(nf);
        assertSame(mapper, result);
        assertSame(nf, mapper.getNodeFactory());
    }

    // setConfig(DeserializationConfig) directly replaces the deserialization configuration
    @Test
    public void testSetConfig_deserializationConfig_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig cfg = mapper.getDeserializationConfig();
        ObjectMapper result = mapper.setConfig(cfg);
        assertSame(mapper, result);
        assertSame(cfg, mapper.getDeserializationConfig());
    }

    // setConfig(SerializationConfig) directly replaces the serialization configuration
    @Test
    public void testSetConfig_serializationConfig_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SerializationConfig cfg = mapper.getSerializationConfig();
        ObjectMapper result = mapper.setConfig(cfg);
        assertSame(mapper, result);
        assertSame(cfg, mapper.getSerializationConfig());
    }

    // setInjectableValues/getInjectableValues round trip (null is a legal value)
    @Test
    public void testSetInjectableValues_getInjectableValues_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectMapper result = mapper.setInjectableValues(null);
        assertSame(mapper, result);
        assertNull(mapper.getInjectableValues());
    }

    // configure(MapperFeature,...) toggles feature state correctly both ways
    @Test
    public void testConfigure_MapperFeature_togglesState() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
        assertFalse(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    }

    // configure(SerializationFeature,...) toggles feature state correctly both ways
    @Test
    public void testConfigure_SerializationFeature_togglesState() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        assertTrue(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        assertFalse(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
    }

    // configure(JsonGenerator.Feature,...) toggles feature state on the underlying JsonFactory
    @Test
    public void testConfigure_JsonGeneratorFeature_togglesState() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT, false);
        assertFalse(mapper.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT));
        mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT, true);
        assertTrue(mapper.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT));
    }

    // readValue(String, Class) parses a simple scalar type correctly
    @Test
    public void testReadValue_fromString_simpleIntegerType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Integer result = mapper.readValue("42", Integer.class);
        assertEquals(Integer.valueOf(42), result);
    }

    // readValue(String, Class) binds a JSON object into the corresponding POJO fields
    @Test
    public void testReadValue_pojoFromJsonObject() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SamplePojo pojo = mapper.readValue("{\"name\":\"abc\",\"value\":7}", SamplePojo.class);
        assertEquals("abc", pojo.getName());
        assertEquals(7, pojo.getValue());
    }

    // readValue on empty input must throw JsonMappingException ("No content to map")
    @Test
    public void testReadValue_emptyContent_throwsJsonMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("", SamplePojo.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // readTree(String) parses a JSON object into an ObjectNode with accessible fields
    @Test
    public void testReadTree_fromString_objectNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("{\"a\":1}");
        assertTrue(node.isObject());
        assertEquals(1, node.get("a").asInt());
    }

    // readTree(String) for JSON null literal must yield a non-null "null node"
    @Test
    public void testReadTree_nullLiteral_returnsNullNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("null");
        assertNotNull(node);
        assertTrue(node.isNull());
    }

    // readTree(JsonParser) returns null (per Javadoc) when there is no content to bind
    @Test
    public void testReadTree_jsonParserNoContent_returnsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = mapper.getFactory().createParser("");
        JsonNode node = mapper.readTree(parser);
        assertNull(node);
    }

    // writeValueAsString serializes POJO properties into the resulting JSON text
    @Test
    public void testWriteValueAsString_pojo_containsFields() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SamplePojo pojo = new SamplePojo("xyz", 3);
        String json = mapper.writeValueAsString(pojo);
        assertTrue(json.contains("\"name\":\"xyz\""));
        assertTrue(json.contains("\"value\":3"));
    }

    // createObjectNode/createArrayNode construct tree nodes of the expected concrete kind
    @Test
    public void testCreateObjectNode_and_createArrayNode_returnCorrectTypes() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode on = mapper.createObjectNode();
        ArrayNode an = mapper.createArrayNode();
        assertTrue(on.isObject());
        assertTrue(an.isArray());
    }

    // treeToValue takes the cast shortcut when target type already matches node's runtime type
    @Test
    public void testTreeToValue_sameConcreteType_returnsSameInstance() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        ObjectNode result = mapper.treeToValue(node, ObjectNode.class);
        assertSame(node, result);
    }

    // valueToTree converts a POJO into its equivalent JSON tree representation
    @Test
    public void testValueToTree_pojo_containsExpectedField() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SamplePojo pojo = new SamplePojo("v", 2);
        JsonNode node = mapper.valueToTree(pojo);
        assertEquals(2, node.get("value").asInt());
    }

    // canSerialize must report true for a well known serializable type (String)
    @Test
    public void testCanSerialize_forString_returnsTrue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(mapper.canSerialize(String.class));
    }

    // canDeserialize must report true for a well known deserializable type (String)
    @Test
    public void testCanDeserialize_forStringType_returnsTrue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.constructType(String.class);
        assertTrue(mapper.canDeserialize(type));
    }

    // convertValue performs a string-to-number conversion using standard data binding
    @Test
    public void testConvertValue_stringToInteger() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Integer result = mapper.convertValue("123", Integer.class);
        assertEquals(Integer.valueOf(123), result);
    }
}
