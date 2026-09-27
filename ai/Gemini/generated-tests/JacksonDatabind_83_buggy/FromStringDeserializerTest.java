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
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

public class FromStringDeserializerTest {

    @Test
    public void testTypesArray() throws Throwable {
        Class<?>[] supportedTypes = FromStringDeserializer.types();
        assertNotNull(supportedTypes);
        assertTrue(supportedTypes.length > 0);
    }

    @Test
    public void testFindDeserializerAllKinds() throws Throwable {
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
        assertNotNull(FromStringDeserializer.findDeserializer(StringBuilder.class));
        
        assertNull(FromStringDeserializer.findDeserializer(Void.class));
    }

    @Test
    public void testStdDeserializeFile() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(File.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("test.txt", ctxt);
        assertTrue(res instanceof File);
        assertEquals("test.txt", ((File) res).getName());
    }

    @Test
    public void testStdDeserializeURL() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(URL.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("http://localhost", ctxt);
        assertTrue(res instanceof URL);
    }

    @Test
    public void testStdDeserializeURI() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(URI.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("urn:test", ctxt);
        assertTrue(res instanceof URI);
    }

    @Test
    public void testStdDeserializeCurrency() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Currency.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("USD", ctxt);
        assertTrue(res instanceof Currency);
        assertEquals("USD", ((Currency) res).getCurrencyCode());
    }

    @Test
    public void testStdDeserializePattern() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Pattern.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("a*b", ctxt);
        assertTrue(res instanceof Pattern);
        assertEquals("a*b", ((Pattern) res).pattern());
    }

    @Test
    public void testStdDeserializeLocale() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Locale.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res1 = des._deserialize("en", ctxt);
        assertTrue(res1 instanceof Locale);
        assertEquals("en", ((Locale) res1).getLanguage());

        Object res2 = des._deserialize("en-US", ctxt);
        assertTrue(res2 instanceof Locale);
        assertEquals("en", ((Locale) res2).getLanguage());
        assertEquals("US", ((Locale) res2).getCountry());

        Object res3 = des._deserialize("en_US_POSIX", ctxt);
        assertTrue(res3 instanceof Locale);
        assertEquals("en", ((Locale) res3).getLanguage());
        assertEquals("US", ((Locale) res3).getCountry());
        assertEquals("POSIX", ((Locale) res3).getVariant());
    }

    @Test
    public void testStdDeserializeCharset() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Charset.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("UTF-8", ctxt);
        assertTrue(res instanceof Charset);
        assertEquals("UTF-8", ((Charset) res).name());
    }

    @Test
    public void testStdDeserializeTimeZone() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(TimeZone.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("GMT", ctxt);
        assertTrue(res instanceof TimeZone);
        assertEquals("GMT", ((TimeZone) res).getID());
    }

    @Test
    public void testStdDeserializeInetAddress() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(InetAddress.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("127.0.0.1", ctxt);
        assertTrue(res instanceof InetAddress);
    }

    @Test
    public void testStdDeserializeInetSocketAddress() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(InetSocketAddress.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res1 = des._deserialize("127.0.0.1:8080", ctxt);
        assertTrue(res1 instanceof InetSocketAddress);
        assertEquals(8080, ((InetSocketAddress) res1).getPort());

        Object res2 = des._deserialize("[0:0:0:0:0:0:0:1]:9090", ctxt);
        assertTrue(res2 instanceof InetSocketAddress);
        assertEquals(9090, ((InetSocketAddress) res2).getPort());

        Object res3 = des._deserialize("127.0.0.1", ctxt);
        assertTrue(res3 instanceof InetSocketAddress);
        assertEquals(0, ((InetSocketAddress) res3).getPort());
    }

    @Test(expected = InvalidFormatException.class)
    public void testStdDeserializeInetSocketAddressInvalidBracket() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(InetSocketAddress.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        des._deserialize("[127.0.0.1", ctxt);
    }

    @Test
    public void testStdDeserializeStringBuilder() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(StringBuilder.class);
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();
        
        Object res = des._deserialize("hello", ctxt);
        assertTrue(res instanceof StringBuilder);
        assertEquals("hello", res.toString());
    }

    @Test
    public void testStdFromEmptyString() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationContext ctxt = mapper.getDeserializationContext();

        FromStringDeserializer.Std uriDes = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(URI.class);
        Object uriRes = uriDes._deserializeFromEmptyString();
        assertTrue(uriRes instanceof URI);

        FromStringDeserializer.Std locDes = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Locale.class);
        Object locRes = locDes._deserializeFromEmptyString();
        assertEquals(Locale.ROOT, locRes);

        FromStringDeserializer.Std sbDes = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(StringBuilder.class);
        Object sbRes = sbDes._deserializeFromEmptyString();
        assertTrue(sbRes instanceof StringBuilder);

        FromStringDeserializer.Std fileDes = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(File.class);
        Object fileRes = fileDes._deserializeFromEmptyString();
        assertNull(fileRes);
    }

    @Test
    public void testFirstHyphenOrUnderscore() throws Throwable {
        FromStringDeserializer.Std des = (FromStringDeserializer.Std) FromStringDeserializer.findDeserializer(Locale.class);
        assertEquals(2, des._firstHyphenOrUnderscore("en_US"));
        assertEquals(2, des._firstHyphenOrUnderscore("en-US"));
        assertEquals(-1, des._firstHyphenOrUnderscore("enUS"));
    }
}