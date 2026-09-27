package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.cfg.BaseSettings;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.ser.SerializerFactory;

public class ObjectMapperTest {

    @Test
    public void testConstructorsAndCopy() throws Throwable {
        ObjectMapper mapper1 = new ObjectMapper();
        assertNotNull(mapper1.getFactory());
        assertNotNull(mapper1.getSerializationConfig());
        assertNotNull(mapper1.getDeserializationConfig());

        JsonFactory jf = new JsonFactory();
        ObjectMapper mapper2 = new ObjectMapper(jf);
        assertNotNull(mapper2.getFactory());

        ObjectMapper mapper3 = new ObjectMapper(null, null, null);
        assertNotNull(mapper3);

        ObjectMapper copy = mapper3.copy();
        assertNotNull(copy);
        
        try {
            class CustomObjectMapper extends ObjectMapper {
                public CustomObjectMapper() { super(); }
            }
            CustomObjectMapper custom = new CustomObjectMapper();
            custom.copy();
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("does not override copy"));
        }
    }

    @Test
    public void testVersionAndModuleRegistration() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Version v = mapper.version();
        assertNotNull(v);

        try {
            mapper.registerModule(null);
            fail("Should throw exception or handle gracefully");
        } catch (Exception e) {
            // Expected for null module or handled via NPE/IllegalArgument
        }

        class DummyModule extends Module {
            public String getModuleName() { return "DummyModule"; }
            public Version version() { return Version.unknownVersion(); }
            public void setupModule(SetupContext context) {
                context.getOwner();
                context.getTypeFactory();
                context.isEnabled(MapperFeature.AUTO_DETECT_CREATORS);
                context.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                context.isEnabled(SerializationFeature.INDENT_OUTPUT);
                context.isEnabled(JsonFactory.Feature.INTERN_FIELD_NAMES);
                context.isEnabled(JsonParser.Feature.ALLOW_COMMENTS);
                context.isEnabled(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
                context.addDeserializers(null);
                context.addKeyDeserializers(null);
                context.addBeanDeserializerModifier(null);
                context.addSerializers(null);
                context.addKeySerializers(null);
                context.addBeanSerializerModifier(null);
                context.addAbstractTypeResolver(null);
                context.addTypeModifier(null);
                context.addValueInstantiators(null);
                context.setClassIntrospector(null);
                context.insertAnnotationIntrospector(null);
                context.appendAnnotationIntrospector(null);
                context.registerSubtypes(Object.class);
                context.registerSubtypes(new NamedType(Object.class, "obj"));
                context.setMixInAnnotations(Object.class, Object.class);
                context.addDeserializationProblemHandler(null);
                context.setNamingStrategy(null);
            }
        }

        DummyModule mod = new DummyModule();
        mapper.registerModule(mod);
        mapper.registerModules(mod);
        
        List<Module> mods = new ArrayList<Module>();
        mods.add(mod);
        mapper.registerModules(mods);

        List<Module> found = ObjectMapper.findModules();
        assertNotNull(found);
        List<Module> foundWithLoader = ObjectMapper.findModules(Thread.currentThread().getContextClassLoader());
        assertNotNull(foundWithLoader);

        mapper.findAndRegisterModules();
    }

    @Test
    public void testConfigurationAccessorsAndSetters() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        
        assertNotNull(mapper.getSerializationConfig());
        assertNotNull(mapper.getDeserializationConfig());
        assertNotNull(mapper.getDeserializationContext());
        assertNotNull(mapper.getSerializerFactory());
        assertNotNull(mapper.getSerializerProvider());
        
        SerializerFactory sf = mapper.getSerializerFactory();
        mapper.setSerializerFactory(sf);
        
        DefaultSerializerProvider sp = new DefaultSerializerProvider.Impl();
        mapper.setSerializerProvider(sp);

        mapper.setMixIns(null);
        mapper.addMixIn(String.class, Object.class);
        assertNotNull(mapper.findMixInClassFor(String.class));
        assertEquals(1, mapper.mixInCount());

        mapper.setMixInResolver(null);
        mapper.setMixInAnnotations(null);
        mapper.addMixInAnnotations(String.class, Object.class);

        assertNotNull(mapper.getVisibilityChecker());
        mapper.setVisibilityChecker(null);
        mapper.setVisibility(null);
        mapper.setVisibility(PropertyAccessor.ALL, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);

        assertNotNull(mapper.getSubtypeResolver());
        mapper.setSubtypeResolver(mapper.getSubtypeResolver());

        mapper.setAnnotationIntrospector(null);
        mapper.setAnnotationIntrospectors(null, null);

        mapper.setPropertyNamingStrategy(null);
        assertNull(mapper.getPropertyNamingStrategy());

        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        mapper.setDefaultPrettyPrinter(null);

        mapper.enableDefaultTyping();
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        try {
            mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.EXTERNAL_PROPERTY);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("EXTERNAL_PROPERTY"));
        }
        mapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "type");
        mapper.disableDefaultTyping();
        mapper.setDefaultTyping(null);

        mapper.registerSubtypes(String.class);
        mapper.registerSubtypes(new NamedType(String.class));

        assertNotNull(mapper.getTypeFactory());
        mapper.setTypeFactory(mapper.getTypeFactory());
        assertNotNull(mapper.constructType(String.class));

        assertNotNull(mapper.getNodeFactory());
        mapper.setNodeFactory(JsonNodeFactory.instance);

        mapper.addHandler(null);
        mapper.clearProblemHandlers();
        mapper.setConfig(mapper.getDeserializationConfig());
        mapper.setConfig(mapper.getSerializationConfig());

        mapper.setFilters(null);
        mapper.setFilterProvider(null);
        mapper.setBase64Variant(com.fasterxml.jackson.core.Base64Variants.MIME_NO_LINEFEEDS);

        mapper.setDateFormat(new SimpleDateFormat());
        assertNotNull(mapper.getDateFormat());

        mapper.setHandlerInstantiator(null);
        mapper.setInjectableValues(null);
        assertNull(mapper.getInjectableValues());

        mapper.setLocale(Locale.US);
        mapper.setTimeZone(TimeZone.getDefault());
    }

    @Test
    public void testFeaturesAndSettings() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();

        assertTrue(mapper.isEnabled(MapperFeature.AUTO_DETECT_CREATORS));
        mapper.configure(MapperFeature.AUTO_DETECT_CREATORS, true);
        mapper.enable(MapperFeature.AUTO_DETECT_CREATORS);
        mapper.disable(MapperFeature.AUTO_DETECT_CREATORS);

        assertTrue(mapper.isEnabled(SerializationFeature.CLOSE_CLOSEABLE));
        mapper.configure(SerializationFeature.CLOSE_CLOSEABLE, true);
        mapper.enable(SerializationFeature.CLOSE_CLOSEABLE);
        mapper.enable(SerializationFeature.CLOSE_CLOSEABLE, SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.CLOSE_CLOSEABLE);
        mapper.disable(SerializationFeature.CLOSE_CLOSEABLE, SerializationFeature.INDENT_OUTPUT);

        assertTrue(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT));
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        assertTrue(mapper.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        mapper.disable(JsonParser.Feature.ALLOW_COMMENTS);

        assertTrue(mapper.isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
        mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, true);
        mapper.enable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        assertTrue(mapper.isEnabled(JsonFactory.Feature.INTERN_FIELD_NAMES));
        assertNotNull(mapper.getJsonFactory());
    }

    @Test
    public void testReadAndWriteMethods() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();

        String json = "{\"test\":123}";
        
        JsonNode node = mapper.readTree(json);
        assertNotNull(node);

        JsonNode nodeBytes = mapper.readTree(json.getBytes("UTF-8"));
        assertNotNull(nodeBytes);

        JsonNode nodeReader = mapper.readTree(new StringReader(json));
        assertNotNull(nodeReader);

        JsonNode nodeStream = mapper.readTree(new ByteArrayInputStream(json.getBytes("UTF-8")));
        assertNotNull(nodeStream);

        File tempFile = File.createTempFile("jackson-test", ".json");
        tempFile.deleteOnExit();
        mapper.writeValue(tempFile, node);

        JsonNode nodeFile = mapper.readTree(tempFile);
        assertNotNull(nodeFile);

        JsonNode nodeURL = mapper.readTree(tempFile.toURI().toURL());
        assertNotNull(nodeURL);

        // Read value variants
        Object val1 = mapper.readValue(json, Object.class);
        assertNotNull(val1);

        Object val2 = mapper.readValue(json, new TypeReference<Object>() {});
        assertNotNull(val2);

        Object val3 = mapper.readValue(json, mapper.constructType(Object.class));
        assertNotNull(val3);

        Object val4 = mapper.readValue(new StringReader(json), Object.class);
        assertNotNull(val4);

        Object val5 = mapper.readValue(new StringReader(json), new TypeReference<Object>() {});
        assertNotNull(val5);

        Object val6 = mapper.readValue(new StringReader(json), mapper.constructType(Object.class));
        assertNotNull(val6);

        Object val7 = mapper.readValue(new ByteArrayInputStream(json.getBytes("UTF-8")), Object.class);
        assertNotNull(val7);

        Object val8 = mapper.readValue(new ByteArrayInputStream(json.getBytes("UTF-8")), new TypeReference<Object>() {});
        assertNotNull(val8);

        Object val9 = mapper.readValue(new ByteArrayInputStream(json.getBytes("UTF-8")), mapper.constructType(Object.class));
        assertNotNull(val9);

        Object val10 = mapper.readValue(json.getBytes("UTF-8"), Object.class);
        assertNotNull(val10);

        Object val11 = mapper.readValue(json.getBytes("UTF-8"), 0, json.getBytes("UTF-8").length, Object.class);
        assertNotNull(val11);

        Object val12 = mapper.readValue(json.getBytes("UTF-8"), new TypeReference<Object>() {});
        assertNotNull(val12);

        Object val13 = mapper.readValue(json.getBytes("UTF-8"), 0, json.getBytes("UTF-8").length, new TypeReference<Object>() {});
        assertNotNull(val13);

        Object val14 = mapper.readValue(json.getBytes("UTF-8"), mapper.constructType(Object.class));
        assertNotNull(val14);

        Object val15 = mapper.readValue(json.getBytes("UTF-8"), 0, json.getBytes("UTF-8").length, mapper.constructType(Object.class));
        assertNotNull(val15);

        Object val16 = mapper.readValue(tempFile, Object.class);
        assertNotNull(val16);

        Object val17 = mapper.readValue(tempFile, new TypeReference<Object>() {});
        assertNotNull(val17);

        Object val18 = mapper.readValue(tempFile, mapper.constructType(Object.class));
        assertNotNull(val18);

        Object val19 = mapper.readValue(tempFile.toURI().toURL(), Object.class);
        assertNotNull(val19);

        Object val20 = mapper.readValue(tempFile.toURI().toURL(), new TypeReference<Object>() {});
        assertNotNull(val20);

        Object val21 = mapper.readValue(tempFile.toURI().toURL(), mapper.constructType(Object.class));
        assertNotNull(val21);

        // Write value variants
        String strVal = mapper.writeValueAsString(node);
        assertNotNull(strVal);

        byte[] bytesVal = mapper.writeValueAsBytes(node);
        assertNotNull(bytesVal);

        java.io.StringWriter sw = new java.io.StringWriter();
        mapper.writeValue(sw, node);

        java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
        mapper.writeValue(os, node);

        // Tree and Converters
        ObjectNode objNode = mapper.createObjectNode();
        assertNotNull(objNode);

        ArrayNode arrNode = mapper.createArrayNode();
        assertNotNull(arrNode);

        mapper.writeTree(mapper.getFactory().createGenerator(sw), objNode);
        mapper.writeTree(mapper.getFactory().createGenerator(os, com.fasterxml.jackson.core.JsonEncoding.UTF8), (JsonNode) objNode);

        assertNotNull(mapper.treeAsTokens(objNode));
        assertNotNull(mapper.treeToValue(objNode, Object.class));
        assertNotNull(mapper.valueToTree(node));
        assertNull(mapper.valueToTree(null));

        assertNotNull(mapper.convertValue(node, Object.class));
        assertNotNull(mapper.convertValue(node, new TypeReference<Object>() {}));
        assertNotNull(mapper.convertValue(node, mapper.constructType(Object.class)));
        assertNull(mapper.convertValue(null, Object.class));

        assertTrue(mapper.canSerialize(Object.class));
        assertTrue(mapper.canSerialize(Object.class, new AtomicReference<Throwable>()));
        assertTrue(mapper.canDeserialize(mapper.constructType(Object.class)));
        assertTrue(mapper.canDeserialize(mapper.constructType(Object.class), new AtomicReference<Throwable>()));

        // Writers and Readers builders
        assertNotNull(mapper.writer());
        assertNotNull(mapper.writer(SerializationFeature.INDENT_OUTPUT));
        assertNotNull(mapper.writer(SerializationFeature.INDENT_OUTPUT, SerializationFeature.CLOSE_CLOSEABLE));
        assertNotNull(mapper.writer(new SimpleDateFormat()));
        assertNotNull(mapper.writerWithView(Object.class));
        assertNotNull(mapper.writerFor(Object.class));
        assertNotNull(mapper.writerFor(new TypeReference<Object>() {}));
        assertNotNull(mapper.writerFor(mapper.constructType(Object.class)));
        assertNotNull(mapper.writer(mapper.getSerializationConfig().constructDefaultPrettyPrinter()));
        assertNotNull(mapper.writerWithDefaultPrettyPrinter());
        assertNotNull(mapper.writer((com.fasterxml.jackson.databind.ser.FilterProvider) null));
        assertNotNull(mapper.writer(com.fasterxml.jackson.core.Base64Variants.MIME));
        assertNotNull(mapper.writer(new com.fasterxml.jackson.core.io.CharacterEscapes() {
            public int[] escapeStdFor128() { return null; }
            public com.fasterxml.jackson.core.SerializableString getEscapeSequence(int ch) { return null; }
        }));
        assertNotNull(mapper.writer(com.fasterxml.jackson.databind.cfg.ContextAttributes.getEmpty()));

        assertNotNull(mapper.reader());
        assertNotNull(mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertNotNull(mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertNotNull(mapper.readerForUpdating(new Object()));
        assertNotNull(mapper.readerFor(Object.class));
        assertNotNull(mapper.readerFor(new TypeReference<Object>() {}));
        assertNotNull(mapper.readerFor(mapper.constructType(Object.class)));
        assertNotNull(mapper.reader(JsonNodeFactory.instance));
        assertNotNull(mapper.reader((InjectableValues) null));
        assertNotNull(mapper.readerWithView(Object.class));
        assertNotNull(mapper.reader(com.fasterxml.jackson.core.Base64Variants.MIME));
        assertNotNull(mapper.reader(com.fasterxml.jackson.databind.cfg.ContextAttributes.getEmpty()));

        // Deprecated or legacy mapper methods
        mapper.writerWithType(Object.class);
        mapper.writerWithType(new TypeReference<Object>() {});
        mapper.writerWithType(mapper.constructType(Object.class));
        mapper.reader(Object.class);
        mapper.reader(new TypeReference<Object>() {});
        mapper.reader(mapper.constructType(Object.class));
        mapper.generateJsonSchema(Object.class);

        mapper.acceptJsonFormatVisitor(Object.class, new com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper.Base());
        mapper.acceptJsonFormatVisitor(mapper.constructType(Object.class), new com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper.Base());
    }

    @Test
    public void testDefaultTypeResolverBuilder() throws Throwable {
        ObjectMapper.DefaultTypeResolverBuilder builder = new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS);
        assertNotNull(builder.useForType(mapper.constructType(Object.class)));
        
        ObjectMapper.DefaultTypeResolverBuilder builder2 = new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        assertNotNull(builder2.useForType(mapper.constructType(Object.class)));

        ObjectMapper.DefaultTypeResolverBuilder builder3 = new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
        assertNotNull(builder3.useForType(mapper.constructType(Object.class)));

        ObjectMapper.DefaultTypeResolverBuilder builder4 = new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
        assertNotNull(builder4.useForType(mapper.constructType(Object.class)));

        assertNotNull(builder.buildTypeDeserializer(mapper.getDeserializationConfig(), mapper.constructType(Object.class), null));
        assertNotNull(builder.buildTypeSerializer(mapper.getSerializationConfig(), mapper.constructType(Object.class), null));
    }
    
    private final ObjectMapper mapper = new ObjectMapper();
}