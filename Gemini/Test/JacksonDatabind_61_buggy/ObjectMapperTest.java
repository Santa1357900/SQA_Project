package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.cfg.MutableConfigOverride;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class ObjectMapperTest {

    @Test
    public void testConstructorsAndCopy() throws Throwable {
        ObjectMapper mapper1 = new ObjectMapper();
        assertNotNull(mapper1);
        assertNotNull(mapper1.getFactory());
        assertNotNull(mapper1.getTypeFactory());

        JsonFactory jf = new JsonFactory();
        ObjectMapper mapper2 = new ObjectMapper(jf);
        assertNotNull(mapper2);

        ObjectMapper mapper3 = mapper1.copy();
        assertNotNull(mapper3);
        assertEquals(mapper1.version(), mapper3.version());
    }

    @Test
    public void testDefaultTyping() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectMapper mapperTyped1 = mapper.enableDefaultTyping();
        assertNotNull(mapperTyped1);

        ObjectMapper mapperTyped2 = mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        assertNotNull(mapperTyped2);

        ObjectMapper mapperTyped3 = mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS, JsonTypeInfo.As.PROPERTY);
        assertNotNull(mapperTyped3);

        ObjectMapper mapperTyped4 = mapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "classProperty");
        assertNotNull(mapperTyped4);

        ObjectMapper mapperDisabled = mapper.disableDefaultTyping();
        assertNotNull(mapperDisabled);

        try {
            mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, JsonTypeInfo.As.EXTERNAL_PROPERTY);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not use includeAs"));
        }
    }

    @Test
    public void testConfigurationFeatures() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));

        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        assertTrue(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        mapper.enable(MapperFeature.USE_ANNOTATIONS);
        mapper.disable(MapperFeature.USE_ANNOTATIONS);

        mapper.enable(SerializationFeature.CLOSE_CLOSEABLE);
        mapper.disable(SerializationFeature.CLOSE_CLOSEABLE);

        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.disable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

        assertNotNull(mapper.getSerializationConfig());
        assertNotNull(mapper.getDeserializationConfig());
        assertNotNull(mapper.getDeserializationContext());
        assertNotNull(mapper.getSerializerFactory());
        assertNotNull(mapper.getSerializerProvider());
        assertNotNull(mapper.getSerializerProviderInstance());
        assertNotNull(mapper.getSubtypeResolver());
        assertNotNull(mapper.getVisibilityChecker());
        assertNotNull(mapper.getNodeFactory());
    }

    @Test
    public void testSettersAndGetters() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();

        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        assertNotNull(mapper.getDateFormat());

        mapper.setLocale(Locale.FRANCE);
        mapper.setTimeZone(TimeZone.getTimeZone("GMT"));

        mapper.setInjectableValues(null);
        assertNull(mapper.getInjectableValues());

        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        assertNotNull(mapper.getPropertyNamingStrategy());

        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyInclusion(JsonInclude.Value.always());

        mapper.setDefaultPrettyPrinter(null);
        mapper.setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        mapper.setFilterProvider(null);
        mapper.setBase64Variant(com.fasterxml.jackson.core.Base64Variants.MIME);
        mapper.setHandlerInstantiator(null);

        assertNotNull(mapper.setSerializerFactory(mapper.getSerializerFactory()));
        assertNotNull(mapper.setSerializerProvider(null));
        assertNotNull(mapper.setTypeFactory(mapper.getTypeFactory()));
        assertNotNull(mapper.setSubtypeResolver(mapper.getSubtypeResolver()));
        assertNotNull(mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector()));
        assertNotNull(mapper.setAnnotationIntrospectors(new JacksonAnnotationIntrospector(), new JacksonAnnotationIntrospector()));
        assertNotNull(mapper.setVisibility(JsonAbstTypeResolver.class, JsonAutoDetect.Visibility.ANY));
        assertNotNull(mapper.setVisibility(VisibilityChecker.Std.defaultInstance()));

        MutableConfigOverride override = mapper.configOverride(String.class);
        assertNotNull(override);

        mapper.registerSubtypes(String.class);
        mapper.registerSubtypes(new com.fasterxml.jackson.databind.jsontype.NamedType(String.class, "str"));

        mapper.addHandler(null);
        mapper.clearProblemHandlers();
    }

    @Test
    public void testReadAndWriteValues() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();

        String json = "{\"name\":\"test\",\"value\":123}";
        
        Map<?, ?> map = mapper.readValue(json, Map.class);
        assertNotNull(map);
        assertEquals("test", map.get("name"));

        Map<?, ?> mapFromBytes = mapper.readValue(json.getBytes("UTF-8"), Map.class);
        assertNotNull(mapFromBytes);

        Map<?, ?> mapFromBytesOffset = mapper.readValue(json.getBytes("UTF-8"), 0, json.length(), Map.class);
        assertNotNull(mapFromBytesOffset);

        Map<?, ?> mapFromReader = mapper.readValue(new StringReader(json), Map.class);
        assertNotNull(mapFromReader);

        Map<?, ?> mapFromStream = mapper.readValue(new ByteArrayInputStream(json.getBytes("UTF-8")), Map.class);
        assertNotNull(mapFromStream);

        Map<?, ?> mapFromTypeRef = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        assertNotNull(mapFromTypeRef);

        Map<?, ?> mapFromJavaType = mapper.readValue(json, mapper.constructType(Map.class));
        assertNotNull(mapFromJavaType);

        Map<?, ?> mapFromBytesTypeRef = mapper.readValue(json.getBytes("UTF-8"), new TypeReference<Map<String, Object>>() {});
        assertNotNull(mapFromBytesTypeRef);

        Map<?, ?> mapFromBytesOffsetTypeRef = mapper.readValue(json.getBytes("UTF-8"), 0, json.length(), new TypeReference<Map<String, Object>>() {});
        assertNotNull(mapFromBytesOffsetTypeRef);

        Map<?, ?> mapFromBytesJavaType = mapper.readValue(json.getBytes("UTF-8"), mapper.constructType(Map.class));
        assertNotNull(mapFromBytesJavaType);

        Map<?, ?> mapFromBytesOffsetJavaType = mapper.readValue(json.getBytes("UTF-8"), 0, json.length(), mapper.constructType(Map.class));
        assertNotNull(mapFromBytesOffsetJavaType);

        Map<?, ?> mapFromReaderJavaType = mapper.readValue(new StringReader(json), mapper.constructType(Map.class));
        assertNotNull(mapFromReaderJavaType);

        Map<?, ?> mapFromStreamJavaType = mapper.readValue(new ByteArrayInputStream(json.getBytes("UTF-8")), mapper.constructType(Map.class));
        assertNotNull(mapFromStreamJavaType);

        String jsonStr = mapper.writeValueAsString(map);
        assertNotNull(jsonStr);

        byte[] jsonBytes = mapper.writeValueAsBytes(map);
        assertNotNull(jsonBytes);

        JsonNode node = mapper.readTree(json);
        assertNotNull(node);

        JsonNode nodeStream = mapper.readTree(new ByteArrayInputStream(json.getBytes("UTF-8")));
        assertNotNull(nodeStream);

        JsonNode nodeReader = mapper.readTree(new StringReader(json));
        assertNotNull(nodeReader);

        JsonNode nodeBytes = mapper.readTree(json.getBytes("UTF-8"));
        assertNotNull(nodeBytes);

        ObjectNode objNode = mapper.createObjectNode();
        assertNotNull(objNode);

        ArrayNode arrNode = mapper.createArrayNode();
        assertNotNull(arrNode);

        JsonNode treeToNode = mapper.valueToTree(map);
        assertNotNull(treeToNode);

        Map<?, ?> valueFromTree = mapper.treeToValue(treeToNode, Map.class);
        assertNotNull(valueFromTree);

        ObjectNode convertedObj = mapper.convertValue(map, ObjectNode.class);
        assertNotNull(convertedObj);

        Map<?, ?> convertedMap = mapper.convertValue(null, Map.class);
        assertNull(convertedMap);

        assertTrue(mapper.canSerialize(Map.class));
        assertTrue(mapper.canSerialize(Map.class, new AtomicReference<Throwable>()));
        assertTrue(mapper.canDeserialize(mapper.constructType(Map.class)));
        assertTrue(mapper.canDeserialize(mapper.constructType(Map.class), new AtomicReference<Throwable>()));
    }

    @Test
    public void testWritersAndReaders() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();

        assertNotNull(mapper.writer());
        assertNotNull(mapper.writer(SerializationFeature.INDENT_OUTPUT));
        assertNotNull(mapper.writer(SerializationFeature.INDENT_OUTPUT, SerializationFeature.CLOSE_CLOSEABLE));
        assertNotNull(mapper.writer(new SimpleDateFormat("yyyy-MM-dd")));
        assertNotNull(mapper.writerWithView(Object.class));
        assertNotNull(mapper.writerFor(Object.class));
        assertNotNull(mapper.writerFor(new TypeReference<Object>() {}));
        assertNotNull(mapper.writerFor(mapper.constructType(Object.class)));
        assertNotNull(mapper.writer(mapper.getSerializationConfig().getDefaultPrettyPrinter()));
        assertNotNull(mapper.writerWithDefaultPrettyPrinter());
        assertNotNull(mapper.writer((FilterProvider) null));
        assertNotNull(mapper.writer(com.fasterxml.jackson.core.Base64Variants.getDefaultVariant()));
        assertNotNull(mapper.writer(new com.fasterxml.jackson.core.io.CharacterEscapes() {
            public int[] getEscapeCodesForAscii() { return new int[128]; }
            public com.fasterxml.jackson.core.io.SerializedString getEscapeSequence(int ch) { return null; }
        }));
        assertNotNull(mapper.writer(ContextAttributes.getEmpty()));

        assertNotNull(mapper.reader());
        assertNotNull(mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertNotNull(mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertNotNull(mapper.readerForUpdating(new HashMap<String, Object>()));
        assertNotNull(mapper.readerFor(Object.class));
        assertNotNull(mapper.readerFor(new TypeReference<Object>() {}));
        assertNotNull(mapper.readerFor(mapper.constructType(Object.class)));
        assertNotNull(mapper.reader(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
        assertNotNull(mapper.reader((InjectableValues) null));
        assertNotNull(mapper.readerWithView(Object.class));
        assertNotNull(mapper.reader(com.fasterxml.jackson.core.Base64Variants.getDefaultVariant()));
        assertNotNull(mapper.reader(ContextAttributes.getEmpty()));
    }

    @Test
    public void testMixInsAndModuleRegistration() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(String.class, Object.class);
        assertNotNull(mapper.findMixInClassFor(String.class));
        assertEquals(1, mapper.mixInCount());

        Map<Class<?>, Class<?>> mixins = new HashMap<Class<?>, Class<?>>();
        mixins.put(Integer.class, Object.class);
        mapper.setMixIns(mixins);

        mapper.setMixInResolver(mapper.getSubtypeResolver());

        List<Module> modules = ObjectMapper.findModules();
        assertNotNull(modules);
        
        mapper.registerModules(modules);
        mapper.findAndRegisterModules();
    }

    @Test
    public void testFormatVisitorAndSchema() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.acceptJsonFormatVisitor(String.class, (com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("type must be provided"));
        }

        try {
            mapper.generateJsonSchema(String.class);
        } catch (Exception e) {
            // Deprecated, but should execute
        }
    }
}