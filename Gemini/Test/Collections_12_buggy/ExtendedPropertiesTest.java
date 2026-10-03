package org.apache.commons.collections;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;

public class ExtendedPropertiesTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertNotNull(props);
        assertFalse(props.isInitialized());
        assertNull(props.getInclude());
    }

    @Test
    public void testIncludeSetGet() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("include", props.getInclude());

        props.setInclude("customInclude");
        assertEquals("customInclude", props.getInclude());

        props.setInclude(null);
        assertNull(props.getInclude());

        props.setInclude("");
        assertNull(props.getInclude());
    }

    @Test
    public void testAddAndGetProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key1", "value1");
        assertEquals("value1", props.getProperty("key1"));

        props.addProperty("key1", "value2");
        Object val = props.getProperty("key1");
        assertTrue(val instanceof List);
        List list = (List) val;
        assertEquals(2, list.size());
        assertEquals("value1", list.get(0));
        assertEquals("value2", list.get(1));
    }

    @Test
    public void testAddPropertyWithCommas() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("tokens", "val1,val2,val3\\,val4");
        String[] arr = props.getStringArray("tokens");
        assertEquals(3, arr.length);
        assertEquals("val1", arr[0]);
        assertEquals("val2", arr[1]);
        assertEquals("val3,val4", arr[2]);
    }

    @Test
    public void testSetProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("key", "initial");
        assertEquals("initial", props.getString("key"));

        props.setProperty("key", "overwritten");
        assertEquals("overwritten", props.getString("key"));
    }

    @Test
    public void testClearProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("key", "val");
        assertNotNull(props.getProperty("key"));

        props.clearProperty("key");
        assertNull(props.getProperty("key"));
        assertFalse(props.getKeys().hasNext());
    }

    @Test
    public void testInterpolation() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("base", "world");
        props.setProperty("greeting", "Hello ${base}");
        assertEquals("Hello world", props.getString("greeting"));
    }

    @Test(expected = IllegalStateException.class)
    public void testInterpolationInfiniteLoop() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("a", "${b}");
        props.setProperty("b", "${a}");
        props.getString("a");
    }

    @Test
    public void testLoadInputStream() throws Throwable {
        String content = "prop1 = value1\n" +
                         "prop2 = token1, token2\n" +
                         "# comment line\n" +
                         "slashed = line1 \\\n" +
                         "          line2\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes("8859_1"));
        ExtendedProperties props = new ExtendedProperties();
        props.load(bais);

        assertEquals("value1", props.getString("prop1"));
        assertEquals("line1line2", props.getString("slashed"));
        assertTrue(props.isInitialized());
    }

    @Test
    public void testSaveAndDisplay() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("k1", "v1");
        props.addProperty("k2", "v2,v3");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.save(baos, "Header Comment");
        assertTrue(baos.size() > 0);

        // Just invoke display to ensure no exception
        props.display();
    }

    @Test
    public void testGetKeysWithPrefix() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("app.name", "TestApp");
        props.setProperty("app.version", "1.0");
        props.setProperty("other.key", "val");

        Iterator it = props.getKeys("app.");
        List keys = new ArrayList();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        assertEquals(2, keys.size());
        assertTrue(keys.contains("app.name"));
        assertTrue(keys.contains("app.version"));
    }

    @Test
    public void testSubset() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("sub.key1", "val1");
        props.setProperty("sub.key2", "val2");
        props.setProperty("other", "val3");

        ExtendedProperties subset = props.subset("sub");
        assertNotNull(subset);
        assertEquals("val1", subset.getString("key1"));
        assertEquals("val2", subset.getString("key2"));

        assertNull(props.subset("nonexistent"));
    }

    @Test
    public void testGettersAndPrimitives() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("bool_t", "true");
        props.setProperty("bool_yes", "yes");
        props.setProperty("bool_off", "off");
        props.setProperty("byte_k", "12");
        props.setProperty("short_k", "123");
        props.setProperty("int_k", "12345");
        props.setProperty("long_k", "123456789");
        props.setProperty("float_k", "1.23");
        props.setProperty("double_k", "1.23456");

        assertTrue(props.getBoolean("bool_t"));
        assertTrue(props.getBoolean("bool_yes", false));
        assertFalse(props.getBoolean("bool_off"));

        assertEquals((byte) 12, props.getByte("byte_k"));
        assertEquals((byte) 99, props.getByte("missing_byte", (byte) 99));

        assertEquals((short) 123, props.getShort("short_k"));
        assertEquals((short) 99, props.getShort("missing_short", (short) 99));

        assertEquals(12345, props.getInt("int_k"));
        assertEquals(12345, props.getInteger("int_k"));
        assertEquals(99, props.getInt("missing_int", 99));

        assertEquals(123456789L, props.getLong("long_k"));
        assertEquals(99L, props.getLong("missing_long", 99L));

        assertEquals(1.23f, props.getFloat("float_k"), 0.001f);
        assertEquals(99.0f, props.getFloat("missing_float", 99.0f), 0.001f);

        assertEquals(1.23456, props.getDouble("double_k"), 0.00001);
        assertEquals(99.0, props.getDouble("missing_double", 99.0), 0.00001);
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetBooleanNoSuchElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getBoolean("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetByteNoSuchElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getByte("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetShortNoSuchElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getShort("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetIntegerNoSuchElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getInteger("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetLongNoSuchElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getLong("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetFloatNoSuchElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getFloat("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetDoubleNoSuchElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getDouble("nonexistent");
    }

    @Test(expected = ClassCastException.class)
    public void testGetStringClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getString("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetStringArrayClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getStringArray("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetVectorClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getVector("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetListClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getList("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetBooleanClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getBoolean("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetByteClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getByte("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetShortClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getShort("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetIntegerClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getInteger("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetLongClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getLong("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetFloatClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getFloat("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetDoubleClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Object());
        props.getDouble("key");
    }

    @Test
    public void testGetProperties() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("propConfig", "foo=bar, hello=world");
        Properties javaProps = props.getProperties("propConfig");
        assertEquals("bar", javaProps.getProperty("foo"));
        assertEquals("world", javaProps.getProperty("hello"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPropertiesMalformed() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("propConfig", "invalidTokenWithoutEquals");
        props.getProperties("propConfig");
    }

    @Test
    public void testVectorAndListGetters() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("listKey", "item1");
        props.addProperty("listKey", "item2");

        List list = props.getList("listKey");
        assertEquals(2, list.size());

        Vector vec = props.getVector("listKey");
        assertEquals(2, vec.size());

        Vector vecDefault = props.getVector("missing", new Vector());
        assertNotNull(vecDefault);

        List listDefault = props.getList("missing", new ArrayList());
        assertNotNull(listDefault);
    }

    @Test
    public void testConvertProperties() throws Throwable {
        Properties standard = new Properties();
        standard.setProperty("k1", "v1");
        ExtendedProperties converted = ExtendedProperties.convertProperties(standard);
        assertEquals("v1", converted.getString("k1"));
    }

    @Test
    public void testPutAllMap() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        Map map = new HashMap();
        map.put("m1", "mv1");
        props.putAll(map);
        assertEquals("mv1", props.getString("m1"));

        ExtendedProperties other = new ExtendedProperties();
        other.setProperty("m2", "mv2");
        props.putAll(other);
        assertEquals("mv2", props.getString("m2"));
    }

    @Test
    public void testRemoveProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("rem", "val");
        Object val = props.remove("rem");
        assertEquals("val", val);
        assertNull(props.getProperty("rem"));
    }

    @Test
    public void testPropertiesReaderAndTokenizerDirectly() throws Throwable {
        StringReader sr = new StringReader("  #comment\nkey = val1, val2\\,val3 \\\n continued");
        ExtendedProperties.PropertiesReader reader = new ExtendedProperties.PropertiesReader(sr);
        String propLine = reader.readProperty();
        assertNotNull(propLine);

        ExtendedProperties.PropertiesTokenizer tokenizer = new ExtendedProperties.PropertiesTokenizer("a,b,c\\,d");
        assertTrue(tokenizer.hasMoreTokens());
        assertEquals("a", tokenizer.nextToken());
    }
}