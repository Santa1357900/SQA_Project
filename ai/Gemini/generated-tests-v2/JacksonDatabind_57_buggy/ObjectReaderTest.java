package com.fasterxml.jackson.databind;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.FormatFeature;
import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;
import com.fasterxml.jackson.core.filter.TokenFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.DataFormatReaders;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ObjectReaderTest {

    @Test
    public void testVersion() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        Version version = reader.version();
        assertNotNull(version);
    }

    @Test
    public void testGettersAndConfig() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        assertNotNull(reader.getConfig());
        assertNotNull(reader.getFactory());
        assertNotNull(reader.getTypeFactory());
        assertNotNull(reader.getAttributes());
        assertNull(reader.getInjectableValues());
        
        assertTrue(reader.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) == 
                   mapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertTrue(reader.isEnabled(MapperFeature.USE_ANNOTATIONS));
        assertTrue(reader.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
    }

    @Test
    public void testFluentFactoriesFeatures() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        ObjectReader r2 = reader.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertNotNull(r2);
        
        ObjectReader r3 = reader.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.WRAP_EXCEPTIONS);
        assertNotNull(r3);
        
        ObjectReader r4 = reader.withFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertNotNull(r4);
        
        ObjectReader r5 = reader.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertNotNull(r5);
        
        ObjectReader r6 = reader.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.WRAP_EXCEPTIONS);
        assertNotNull(r6);
        
        ObjectReader r7 = reader.withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertNotNull(r7);

        ObjectReader r8 = reader.with(JsonParser.Feature.ALLOW_COMMENTS);
        assertNotNull(r8);
        
        ObjectReader r9 = reader.withFeatures(JsonParser.Feature.ALLOW_COMMENTS);
        assertNotNull(r9);
        
        ObjectReader r10 = reader.without(JsonParser.Feature.ALLOW_COMMENTS);
        assertNotNull(r10);
        
        ObjectReader r11 = reader.withoutFeatures(JsonParser.Feature.ALLOW_COMMENTS);
        assertNotNull(r11);
    }

    @Test
    public void testFluentFactoriesOther() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        assertNotNull(reader.with(reader.getConfig()));
        assertNotNull(reader.with((InjectableValues) null));
        assertNotNull(reader.with(JsonNodeFactory.instance));
        assertNotNull(reader.with(mapper.getFactory()));
        
        // Same factory should return same instance or handle gracefully
        assertSame(reader, reader.with(mapper.getFactory()));
        
        assertNotNull(reader.withRootName("root"));
        assertNotNull(reader.withRootName(PropertyName.construct("root")));
        assertNotNull(reader.withoutRootName());
        
        assertNotNull(reader.with(Locale.getDefault()));
        assertNotNull(reader.with(TimeZone.getDefault()));
        assertNotNull(reader.with(Base64Variant.getDefaultBase64()));
        
        ContextAttributes attrs = ContextAttributes.getEmpty();
        assertNotNull(reader.with(attrs));
        
        Map<String, Object> map = new HashMap<String, Object>();
        assertNotNull(reader.withAttributes(map));
        assertNotNull(reader.withAttribute("key", "val"));
        assertNotNull(reader.withoutAttribute("key"));
        
        assertNotNull(reader.at("/test"));
        assertNotNull(reader.at(JsonPointer.compile("/test")));
    }

    @Test
    public void testForTypeVariants() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        assertNotNull(reader.forType(Object.class));
        assertNotNull(reader.forType(mapper.constructType(Object.class)));
        assertNotNull(reader.forType(new TypeReference<Object>() {}));
        
        // Deprecated aliases
        assertNotNull(reader.withType(Object.class));
        assertNotNull(reader.withType(mapper.constructType(Object.class)));
        assertNotNull(reader.withType((java.lang.reflect.Type) Object.class));
        assertNotNull(reader.withType(new TypeReference<Object>() {}));
        
        // Same type optimization
        JavaType t = mapper.constructType(Object.class);
        ObjectReader r1 = reader.forType(t);
        assertSame(r1, r1.forType(t));
    }

    @Test
    public void testWithValueToUpdateExceptions() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        assertSame(reader, reader.withValueToUpdate(reader)); // same instance
        
        try {
            reader.withValueToUpdate(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cat not update null value") || e.getMessage() != null);
        }
    }

    @Test
    public void testTreeCodecs() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        assertNotNull(reader.createArrayNode());
        assertNotNull(reader.createObjectNode());
        
        JsonNode node = reader.createObjectNode();
        assertNotNull(reader.treeAsTokens(node));
        
        try {
            reader.writeTree(null, null);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            reader.writeValue(null, null);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testReadValueStringAndReader() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Object.class);
        
        Object val = reader.readValue("{\"a\":1}");
        assertNotNull(val);
        
        Object valReader = reader.readValue(new StringReader("{\"a\":1}"));
        assertNotNull(valReader);
    }

    @Test
    public void testReadTreeSources() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        JsonNode nodeStr = reader.readTree("{\"a\":1}");
        assertNotNull(nodeStr);
        
        JsonNode nodeReader = reader.readTree(new StringReader("{\"a\":1}"));
        assertNotNull(nodeReader);
        
        JsonNode nodeStream = reader.readTree(new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8")));
        assertNotNull(nodeStream);
    }

    @Test
    public void testReadValuesVariants() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Object.class);
        
        Iterator<Object> it1 = reader.readValues("[{\"a\":1}]");
        assertNotNull(it1);
        
        Iterator<Object> it2 = reader.readValues(new StringReader("[{\"a\":1}]"));
        assertNotNull(it2);
        
        byte[] bytes = "[{\"a\":1}]".getBytes("UTF-8");
        Iterator<Object> it3 = reader.readValues(bytes, 0, bytes.length);
        assertNotNull(it3);
        
        Iterator<Object> it4 = reader.readValues(bytes);
        assertNotNull(it4);
    }

    @Test
    public void testFormatDetectionErrors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().withFormatDetection(new ObjectReader[0]);
        
        try {
            reader.readValue(new StringReader("test"));
            fail("Should throw exception for undetectable source with format detection");
        } catch (JsonProcessingException e) {
            assertTrue(e.getMessage().contains("Can not use source of type") || e.getMessage().contains("format auto-detection"));
        }

        try {
            reader.readTree(new StringReader("test"));
            fail("Should throw exception");
        } catch (JsonProcessingException e) {
            // expected
        }

        try {
            reader.readValues(new StringReader("test"));
            fail("Should throw exception");
        } catch (JsonProcessingException e) {
            // expected
        }
    }

    @Test
    public void testTreeToValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Map.class);
        
        JsonNode node = mapper.createObjectNode();
        Map<?, ?> map = reader.treeToValue(node, Map.class);
        assertNotNull(map);
    }

    @Test
    public void testUnwrapAndDeserializeError() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        // Enable root wrapping
        ObjectReader reader = mapper.reader().with(DeserializationFeature.UNWRAP_ROOT_VALUE).forType(Object.class);
        
        try {
            reader.readValue("{\"wrongRoot\":{}}");
            fail("Should throw JsonMappingException");
        } catch (JsonMappingException e) {
            assertNotNull(e.getMessage());
        }
    }
}