package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Currency;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;

public class FromStringDeserializerTest {

    @Test
    public void testTypes() throws Throwable {
        Class<?>[] types = FromStringDeserializer.types();
        assertNotNull(types);
        assertTrue(types.length > 0);
    }

    @Test
    public void testFindDeserializerValid() throws Throwable {
        assertNotNull(FromStringDeserializer.findDeserializer(File.class));
        assertNotNull(FromStringDeserializer.findDeserializer(URL.class));
        assertNotNull(FromStringDeserializer.findDeserializer(URI.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Class.class));
        assertNotNull(FromStringDeserializer.findDeserializer(JavaType.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Currency.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Pattern.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Locale.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Charset.class));
        assertNotNull(FromStringDeserializer.findDeserializer(TimeZone.class));
        assertNotNull(FromStringDeserializer.findDeserializer(InetAddress.class));
        assertNotNull(FromStringDeserializer.findDeserializer(InetSocketAddress.class));
    }

    @Test
    public void testFindDeserializerInvalid() throws Throwable {
        assertNull(FromStringDeserializer.findDeserializer(Integer.class));
    }

    @Test
    public void testStdKinds() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        // File
        FromStringDeserializer.Std fileDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(File.class);
        assertEquals(new File("test.txt"), fileDeser._deserialize("test.txt", ctxt));

        // URL
        FromStringDeserializer.Std urlDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(URL.class);
        assertEquals(new URL("http://localhost"), urlDeser._deserialize("http://localhost", ctxt));

        // URI
        FromStringDeserializer.Std uriDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(URI.class);
        assertEquals(URI.create("http://localhost"), uriDeser._deserialize("http://localhost", ctxt));

        // Currency
        FromStringDeserializer.Std currDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Currency.class);
        assertEquals(Currency.getInstance("USD"), currDeser._deserialize("USD", currDeser));

        // Pattern
        FromStringDeserializer.Std patDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Pattern.class);
        assertNotNull(patDeser._deserialize("abc", ctxt));

        // Charset
        FromStringDeserializer.Std charsetDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Charset.class);
        assertEquals(Charset.forName("UTF-8"), charsetDeser._deserialize("UTF-8", ctxt));

        // TimeZone
        FromStringDeserializer.Std tzDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(TimeZone.class);
        assertEquals(TimeZone.getTimeZone("GMT"), tzDeser._deserialize("GMT", ctxt));

        // InetAddress
        FromStringDeserializer.Std iaDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(InetAddress.class);
        assertNotNull(iaDeser._deserialize("127.0.0.1", ctxt));
    }

    @Test
    public void testLocaleDeserialization() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        FromStringDeserializer.Std localeDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Locale.class);

        // Single argument
        assertEquals(new Locale("en"), localeDeser._deserialize("en", ctxt));

        // Two pieces
        assertEquals(new Locale("en", "US"), localeDeser._deserialize("en_US", ctxt));

        // Three pieces
        assertEquals(new Locale("en", "US", "POSIX"), localeDeser._deserialize("en_US_POSIX", ctxt));
    }

    @Test
    public void testInetSocketAddressDeserialization() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        FromStringDeserializer.Std isaDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(InetSocketAddress.class);

        // Host:port
        InetSocketAddress isa1 = (InetSocketAddress) isaDeser._deserialize("localhost:8080", ctxt);
        assertEquals("localhost", isa1.getHostName());
        assertEquals(8080, isa1.getPort());

        // Bracketed IPv6 with port
        InetSocketAddress isa2 = (InetSocketAddress) isaDeser._deserialize("[0:0:0:0:0:0:0:1]:8080", ctxt);
        assertEquals(8080, isa2.getPort());

        // Host without port
        InetSocketAddress isa3 = (InetSocketAddress) isaDeser._deserialize("localhost", ctxt);
        assertEquals("localhost", isa3.getHostName());
        assertEquals(0, isa3.getPort());
    }

    @Test
    public void testEmptyStringHandling() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        FromStringDeserializer.Std uriDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(URI.class);
        assertEquals(URI.create(""), uriDeser._deserializeFromEmptyString());

        FromStringDeserializer.Std localeDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Locale.class);
        assertEquals(Locale.ROOT, localeDeser._deserializeFromEmptyString());

        FromStringDeserializer.Std fileDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(File.class);
        assertNull(fileDeser._deserializeFromEmptyString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownKind() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        FromStringDeserializer.Std unknownDeser = new FromStringDeserializer.Std(Object.class, 9999);
        unknownDeser._deserialize("test", ctxt);
    }

    @Test
    public void testDeserializeEmbeddedObject() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        FromStringDeserializer.Std fileDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(File.class);

        File f = new File("test.txt");
        Object result = fileDeser._deserializeEmbedded(f, ctxt);
        assertEquals(f, result);
    }

    @Test(expected = JsonMappingException.class)
    public void testDeserializeEmbeddedObjectInvalidType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        FromStringDeserializer.Std fileDeser = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(File.class);

        fileDeser._deserializeEmbedded(Integer.valueOf(123), ctxt);
    }
}