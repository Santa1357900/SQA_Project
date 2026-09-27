package com.fasterxml.jackson.databind;

import java.io.*;
import java.net.URL;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.ArrayList;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.DataFormatReaders;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ObjectReaderTest {

    @Test
    public void testVersion() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        assertNotNull(reader.version());
    }

    @Test
    public void testSimpleAccessors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        
        assertNotNull(reader.getConfig());
        assertNotNull(reader.getFactory());
        assertNotNull(reader.getJsonFactory());
        assertNotNull(reader.getTypeFactory());
        assertNotNull(reader.getAttributes());
        
        assertTrue(reader.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) || !reader.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertTrue(reader.isEnabled(MapperFeature.USE_ANNOTATIONS) || !reader.isEnabled(MapperFeature.USE_ANNOTATIONS));
        assertTrue(reader.isEnabled(JsonParser.Feature.ALLOW_COMMENTS) || !reader.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
    }

    @Test
    public void testFluentFeatures() throws Throwable {
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
    public void testFluentOther() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        assertNotNull(reader.with(reader.getConfig()));
        assertNotNull(reader.with(InjectableValues.Std.EMPTY));
        assertNotNull(reader.with(JsonNodeFactory.instance));
        assertNotNull(reader.with(mapper.getJsonFactory()));
        assertNotNull(reader.withRootName("root"));
        assertNotNull(reader.withView(Object.class));
        assertNotNull(reader.with(Locale.US));
        assertNotNull(reader.with(TimeZone.getDefault()));
        assertNotNull(reader.with(Base64Variant.getDefaultBase64()));
        assertNotNull(reader.with(ContextAttributes.getEmpty()));
        
        Map<Object, Object> attrs = new HashMap<Object, Object>();
        attrs.put("key", "value");
        assertNotNull(reader.withAttributes(attrs));
        assertNotNull(reader.withAttribute("key", "value"));
        assertNotNull(reader.withoutAttribute("key"));
    }

    @Test
    public void testForTypeVariants() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        assertNotNull(reader.forType(Object.class));
        assertNotNull(reader.forType(mapper.constructType(Object.class)));
        assertNotNull(reader.forType(new TypeReference<Object>() {}));

        assertNotNull(reader.withType(Object.class));
        assertNotNull(reader.withType(mapper.constructType(Object.class)));
        assertNotNull(reader.withType((java.lang.reflect.Type) Object.class));
        assertNotNull(reader.withType(new TypeReference<Object>() {}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateNullValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        reader.withValueToUpdate(null);
    }

    @Test
    public void testUpdateValidValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        Object dummy = new Object();
        ObjectReader r2 = reader.withValueToUpdate(dummy);
        assertNotNull(r2);
        assertSame(r2, reader.withValueToUpdate(dummy));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArrayUpdateException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String[] arr = new String[0];
        mapper.reader().forType(arr.getClass()).withValueToUpdate(arr);
    }

    @Test
    public void testTreeMethods() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        assertNotNull(reader.createArrayNode());
        assertNotNull(reader.createObjectNode());
        
        JsonNode node = reader.createObjectNode();
        assertNotNull(reader.treeAsTokens(node));
        
        try {
            reader.writeTree(null, node);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testReadValueString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Map.class);
        Map<?, ?> map = reader.readValue("{\"a\":1}");
        assertNotNull(map);
        assertEquals(Integer.valueOf(1), map.get("a"));
    }

    @Test
    public void testReadValueBytes() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Map.class);
        byte[] bytes = "{\"a\":1}".getBytes("UTF-8");
        Map<?, ?> map1 = reader.readValue(bytes);
        assertNotNull(map1);
        
        Map<?, ?> map2 = reader.readValue(bytes, 0, bytes.length);
        assertNotNull(map2);
    }

    @Test
    public void testReadValueReader() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Map.class);
        StringReader sr = new StringReader("{\"a\":1}");
        Map<?, ?> map = reader.readValue(sr);
        assertNotNull(map);
    }

    @Test
    public void testReadValueInputStream() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Map.class);
        ByteArrayInputStream bis = new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8"));
        Map<?, ?> map = reader.readValue(bis);
        assertNotNull(map);
    }

    @Test
    public void testReadValueJsonNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Map.class);
        JsonNode node = mapper.readTree("{\"a\":1}");
        Map<?, ?> map = reader.readValue(node);
        assertNotNull(map);
    }

    @Test
    public void testReadTreeSources() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        assertNotNull(reader.readTree("{\"a\":1}"));
        assertNotNull(reader.readTree(new StringReader("{\"a\":1}")));
        assertNotNull(reader.readTree(new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8"))));
    }

    @Test
    public void testReadValuesVariants() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(Map.class);

        byte[] bytes = "{\"a\":1}".getBytes("UTF-8");
        assertNotNull(reader.readValues(bytes));
        assertNotNull(reader.readValues(bytes, 0, bytes.length));
        assertNotNull(reader.readValues("{\"a\":1}"));
        assertNotNull(reader.readValues(new StringReader("{\"a\":1}")));
        assertNotNull(reader.readValues(new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8"))));
        
        JsonParser p = mapper.getJsonFactory().createParser("{\"a\":1}");
        assertNotNull(reader.readValues(p, Map.class));
        
        JsonParser p2 = mapper.getJsonFactory().createParser("{\"a\":1}");
        assertNotNull(reader.readValues(p2, new TypeReference<Map<String, Object>>() {}));
        
        JsonParser p3 = mapper.getJsonFactory().createParser("{\"a\":1}");
        assertNotNull(reader.readValues(p3, reader.getConfig().constructType(Map.class)));
    }

    @Test
    public void testTreeToValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonNode node = mapper.readTree("{\"a\":1}");
        Map<?, ?> map = reader.treeToValue(node, Map.class);
        assertNotNull(map);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testWriteValueUnsupported() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        reader.writeValue(null, null);
    }

    @Test
    public void testFormatDetection() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        ObjectReader reader2 = mapper.reader();
        
        ObjectReader detected = reader.withFormatDetection(reader2);
        assertNotNull(detected);

        ObjectReader detected2 = reader.withFormatDetection(new DataFormatReaders(reader2));
        assertNotNull(detected2);
    }
}