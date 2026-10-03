package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.text.SimpleDateFormat;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.cfg.BaseSettings;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

public class ObjectMapperTest {

    @Test
    public void testConstructorsAndCopy() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertNotNull(mapper);
        assertNotNull(mapper.version());
        assertNotNull(mapper.getFactory());

        ObjectMapper mapperWithFactory = new ObjectMapper(mapper.getFactory());
        assertNotNull(mapperWithFactory);

        ObjectMapper copyMapper = mapper.copy();
        assertNotNull(copyMapper);
    }

    @Test
    public void testDefaultTypingEnumAndBuilder() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder builder = 
            new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        assertNotNull(builder);

        JavaType objType = TypeFactory.defaultInstance().constructType(Object.class);
        assertTrue(builder.useForType(objType));

        JavaType stringType = TypeFactory.defaultInstance().constructType(String.class);
        assertFalse(builder.useForType(stringType));

        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping();
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        mapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS, "type");
        mapper.disableDefaultTyping();
    }

    @Test
    public void testModuleRegistration() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        
        List<Module> found = ObjectMapper.findModules();
        assertNotNull(found);
        
        List<Module> foundWithLoader = ObjectMapper.findModules(Thread.currentThread().getContextClassLoader());
        assertNotNull(foundWithLoader);

        mapper.findAndRegisterModules();

        try {
            mapper.registerModules(new Module() {
                @Override
                public String getModuleName() { return null; }
                @Override
                public Version version() { return Version.unknownVersion(); }
                @Override
                public void setupModule(SetupContext context) {}
            });
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Module without defined name"));
        }

        try {
            mapper.registerModules(new Module() {
                @Override
                public String getModuleName() { return "test"; }
                @Override
                public Version version() { return null; }
                @Override
                public void setupModule(SetupContext context) {}
            });
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Module without defined version"));
        }
    }

    @Test
    public void testConfigAccessorsAndSetters() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertNotNull(mapper.getSerializationConfig());
        assertNotNull(mapper.getDeserializationConfig());
        assertNotNull(mapper.getDeserializationContext());
        assertNotNull(mapper.getSerializerFactory());
        assertNotNull(mapper.getSerializerProvider());

        mapper.setSerializerFactory(mapper.getSerializerFactory());
        mapper.setSerializerProvider((DefaultSerializerProvider) mapper.getSerializerProvider());
        
        mapper.setVisibilityChecker(mapper.getVisibilityChecker());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        
        mapper.setSubtypeResolver(mapper.getSubtypeResolver());
        mapper.registerSubtypes(String.class);
        mapper.registerSubtypes(new com.fasterxml.jackson.databind.jsontype.NamedType(Integer.class, "int"));

        mapper.setAnnotationIntrospector(mapper.getDeserializationConfig().getAnnotationIntrospector());
        mapper.setAnnotationIntrospectors(
            mapper.getSerializationConfig().getAnnotationIntrospector(),
            mapper.getDeserializationConfig().getAnnotationIntrospector()
        );

        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setNodeFactory(JsonNodeFactory.instance);
        mapper.setFilters(null);
        mapper.setBase64Variant(Base64Variants.MIME);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        mapper.setHandlerInstantiator(null);
        mapper.setInjectableValues(null);
        mapper.setLocale(Locale.US);
        mapper.setTimeZone(TimeZone.getDefault());

        mapper.setConfig(mapper.getDeserializationConfig());
        mapper.setConfig(mapper.getSerializationConfig());
    }

    @Test
    public void testMixIns() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(String.class, Object.class);
        assertEquals(1, mapper.mixInCount());
        assertNotNull(mapper.findMixInClassFor(String.class));

        Map<Class<?>, Class<?>> mixins = new HashMap<ClassKey, Class<?>>();
        mixins.put(Integer.class, Object.class);
        mapper.setMixInAnnotations(mixins);
        assertEquals(1, mapper.mixInCount());
    }

    @Test
    public void testFeatureConfiguration() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(MapperFeature.USE_ANNOTATIONS, true);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, true);

        mapper.enable(MapperFeature.USE_ANNOTATIONS);
        mapper.disable(MapperFeature.USE_ANNOTATIONS);

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.CLOSE_CLOSEABLE);
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.CLOSE_CLOSEABLE);

        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.disable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        assertTrue(mapper.isEnabled(MapperFeature.USE_ANNOTATIONS));
        assertTrue(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
        assertTrue(mapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertTrue(mapper.isEnabled(JsonFactory.Feature.INTERN_FIELD_NAMES));
        assertTrue(mapper.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
        assertTrue(mapper.isEnabled(JsonGenerator.Feature.QUOTE_FIELD_NAMES));
        assertNotNull(mapper.getNodeFactory());
    }

    @Test
    public void testReadWriteTreeAndValues() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"hello\":\"world\",\"val\":123}";

        JsonNode node = mapper.readTree(json);
        assertNotNull(node);
        assertEquals("world", node.get("hello").asText());

        JsonNode nodeStream = mapper.readTree(new ByteArrayInputStream(json.getBytes("UTF-8")));
        assertNotNull(nodeStream);

        JsonNode nodeReader = mapper.readTree(new StringReader(json));
        assertNotNull(nodeReader);

        JsonNode nodeFile = mapper.readTree(new File(System.getProperty("java.io.tmpdir")));
        assertNotNull(nodeFile); // Will return NullNode or handle safely

        ObjectNode objNode = mapper.createObjectNode();
        ArrayNode arrNode = mapper.createArrayNode();
        assertNotNull(objNode);
        assertNotNull(arrNode);

        String strVal = mapper.writeValueAsString(objNode);
        assertNotNull(strVal);

        byte[] byteVal = mapper.writeValueAsBytes(objNode);
        assertNotNull(byteVal);

        mapper.writeValue(new StringWriter(), objNode);
        mapper.writeValue(new ByteArrayOutputStream(), objNode);
        mapper.writeValue(new File(System.getProperty("java.io.tmpdir"), "jackson_test.json"), objNode);

        JsonParser parser = mapper.treeAsTokens(objNode);
        assertNotNull(parser);

        Map<?, ?> mapped = mapper.readValue(json, Map.class);
        assertNotNull(mapped);

        Map<?, ?> mappedRef = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        assertNotNull(mappedRef);

        Map<?, ?> mappedJavaType = mapper.readValue(json, TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(mappedJavaType);

        JsonNode treeRes = mapper.readTree(mapper.treeAsTokens(objNode));
        assertNotNull(treeRes);

        MappingIterator<Map> iter = mapper.readValues(mapper.getFactory().createParser(json), Map.class);
        assertNotNull(iter);

        MappingIterator<Map> iterRef = mapper.readValues(mapper.getFactory().createParser(json), new TypeReference<Map<String, Object>>() {});
        assertNotNull(iterRef);

        MappingIterator<Map> iterType = mapper.readValues(mapper.getFactory().createParser(json), TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(iterType);
    }

    @Test
    public void testCanSerializeDeserialize() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(mapper.canSerialize(String.class));
        AtomicReference<Throwable> cause = new AtomicReference<Throwable>();
        assertTrue(mapper.canSerialize(String.class, cause));

        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        assertTrue(mapper.canDeserialize(type));
        assertTrue(mapper.canDeserialize(type, cause));
    }

    @Test
    public void testFileUrlReaderMethods() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        File dummyFile = File.createTempFile("jackson", ".json");
        dummyFile.deleteOnExit();
        FileWriter fw = new FileWriter(dummyFile);
        fw.write("{\"a\":1}");
        fw.close();

        Map<?, ?> res1 = mapper.readValue(dummyFile, Map.class);
        assertNotNull(res1);

        Map<?, ?> res2 = mapper.readValue(dummyFile, new TypeReference<Map<String, Object>>() {});
        assertNotNull(res2);

        Map<?, ?> res3 = mapper.readValue(dummyFile, TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(res3);

        URL url = dummyFile.toURI().toURL();
        Map<?, ?> res4 = mapper.readValue(url, Map.class);
        assertNotNull(res4);

        Map<?, ?> res5 = mapper.readValue(url, new TypeReference<Map<String, Object>>() {});
        assertNotNull(res5);

        Map<?, ?> res6 = mapper.readValue(url, TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(res6);

        Map<?, ?> res7 = mapper.readValue("{\"a\":1}", Map.class);
        assertNotNull(res7);

        Map<?, ?> res8 = mapper.readValue("{\"a\":1}", new TypeReference<Map<String, Object>>() {});
        assertNotNull(res8);

        Map<?, ?> res9 = mapper.readValue("{\"a\":1}", TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(res9);

        Map<?, ?> res10 = mapper.readValue(new StringReader("{\"a\":1}"), Map.class);
        assertNotNull(res10);

        Map<?, ?> res11 = mapper.readValue(new StringReader("{\"a\":1}"), new TypeReference<Map<String, Object>>() {});
        assertNotNull(res11);

        Map<?, ?> res12 = mapper.readValue(new StringReader("{\"a\":1}"), TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(res12);

        InputStream is = new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8"));
        Map<?, ?> res13 = mapper.readValue(is, Map.class);
        assertNotNull(res13);

        InputStream is2 = new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8"));
        Map<?, ?> res14 = mapper.readValue(is2, new TypeReference<Map<String, Object>>() {});
        assertNotNull(res14);

        InputStream is3 = new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8"));
        Map<?, ?> res15 = mapper.readValue(is3, TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(res15);

        byte[] bytes = "{\"a\":1}".getBytes("UTF-8");
        Map<?, ?> res16 = mapper.readValue(bytes, Map.class);
        assertNotNull(res16);

        Map<?, ?> res17 = mapper.readValue(bytes, 0, bytes.length, Map.class);
        assertNotNull(res17);

        Map<?, ?> res18 = mapper.readValue(bytes, new TypeReference<Map<String, Object>>() {});
        assertNotNull(res18);

        Map<?, ?> res19 = mapper.readValue(bytes, 0, bytes.length, new TypeReference<Map<String, Object>>() {});
        assertNotNull(res19);

        Map<?, ?> res20 = mapper.readValue(bytes, TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(res20);

        Map<?, ?> res21 = mapper.readValue(bytes, 0, bytes.length, TypeFactory.defaultInstance().constructType(Map.class));
        assertNotNull(res21);
    }

    @Test
    public void testWritersAndReadersBuilders() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        assertNotNull(mapper.writer());
        assertNotNull(mapper.writer(SerializationFeature.INDENT_OUTPUT));
        assertNotNull(mapper.writer(SerializationFeature.INDENT_OUTPUT, SerializationFeature.CLOSE_CLOSEABLE));
        assertNotNull(mapper.writer(new SimpleDateFormat()));
        assertNotNull(mapper.writerWithView(Object.class));
        assertNotNull(mapper.writerWithType(String.class));
        assertNotNull(mapper.writerWithType(new TypeReference<String>() {}));
        assertNotNull(mapper.writerWithType(TypeFactory.defaultInstance().constructType(String.class)));
        assertNotNull(mapper.writer(mapper.getSerializationConfig().getDefaultPrettyPrinter()));
        assertNotNull(mapper.writer((PrettyPrinter) null));
        assertNotNull(mapper.writerWithDefaultPrettyPrinter());
        assertNotNull(mapper.writer((FilterProvider) null));
        assertNotNull(mapper.writer(Base64Variants.getDefaultVariant()));
        assertNotNull(mapper.writer(CharacterEscapes.DEFAULT_ESCAPEes));
        assertNotNull(mapper.writer(ContextAttributes.getEmpty()));

        assertNotNull(mapper.reader());
        assertNotNull(mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertNotNull(mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertNotNull(mapper.readerForUpdating(new HashMap<String, Object>()));
        assertNotNull(mapper.reader(String.class));
        assertNotNull(mapper.reader(new TypeReference<String>() {}));
        assertNotNull(mapper.reader(TypeFactory.defaultInstance().constructType(String.class)));
        assertNotNull(mapper.reader(JsonNodeFactory.instance));
        assertNotNull(mapper.reader((InjectableValues) null));
        assertNotNull(mapper.readerWithView(Object.class));
        assertNotNull(mapper.reader(Base64Variants.getDefaultVariant()));
        assertNotNull(mapper.reader(ContextAttributes.getEmpty()));
    }

    @Test
    public void testConvertValueAndTree() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("key", "value");

        String convertedStr = mapper.convertValue(map, String.class);
        assertNotNull(convertedStr);

        String convertedRef = mapper.convertValue(map, new TypeReference<String>() {});
        assertNotNull(convertedRef);

        String convertedType = mapper.convertValue(map, TypeFactory.defaultInstance().constructType(String.class));
        assertNotNull(convertedType);

        assertNull(mapper.convertValue(null, String.class));
        assertNull(mapper.valueToTree(null));

        JsonNode tree = mapper.valueToTree(map);
        assertNotNull(tree);

        Map<?, ?> backToMap = mapper.treeToValue(tree, Map.class);
        assertNotNull(backToMap);
    }

    @Test
    public void testSchemaAndVisitors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.generateJsonSchema(String.class);
        } catch (Exception e) {
            // expected or handled
        }

        try {
            mapper.acceptJsonFormatVisitor(String.class, (com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper) null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("type must be provided"));
        }

        try {
            mapper.acceptJsonFormatVisitor((JavaType) null, null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("type must be provided"));
        }
    }

    @Test
    public void testProblemHandlersAndMisc() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addHandler(new DeserializationProblemHandler() {});
        mapper.clearProblemHandlers();

        assertNotNull(mapper.constructType(String.class));
        assertNotNull(mapper.getTypeFactory());
        mapper.setTypeFactory(mapper.getTypeFactory());

        assertNotNull(mapper.getFactory());
        assertNotNull(mapper.getJsonFactory());
    }
}