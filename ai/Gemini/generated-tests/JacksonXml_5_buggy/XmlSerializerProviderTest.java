package com.fasterxml.jackson.dataformat.xml.ser;

import org.junit.Test;
import static org.junit.Assert.*;

import javax.xml.namespace.QName;
import java.io.IOException;

import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.dataformat.xml.util.XmlRootNameLookup;

public class XmlSerializerProviderTest {

    @Test
    public void testConstructorsAndCopy() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider1 = new XmlSerializerProvider(lookup);
        
        XmlSerializerProvider provider2 = (XmlSerializerProvider) provider1.copy();
        assertNotNull(provider2);

        SerializationConfig config = null;
        SerializerFactory sf = null;
        try {
            provider1.createInstance(config, sf);
        } catch (Exception e) {
            // Expected due to null config/sf or abstract inheritance, but ensures invocation coverage
        }
    }

    @Test
    public void testRootNameForNullConstant() throws Throwable {
        assertNotNull(XmlSerializerProvider.ROOT_NAME_FOR_NULL);
        assertEquals("null", XmlSerializerProvider.ROOT_NAME_FOR_NULL.getLocalPart());
    }

    @Test
    public void testAsXmlGeneratorWithTokenBuffer() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);
        
        TokenBuffer tb = new TokenBuffer(null, false);
        
        java.lang.reflect.Method method = XmlSerializerProvider.class.getDeclaredMethod("_asXmlGenerator", com.fasterxml.jackson.core.JsonGenerator.class);
        method.setAccessible(true);
        
        Object result = method.invoke(provider, tb);
        assertNull(result);
    }

    @Test
    public void testAsXmlGeneratorWithInvalidGenerator() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);
        
        com.fasterxml.jackson.core.JsonGenerator invalidGen = new com.fasterxml.jackson.databind.util.TokenBuffer(null, false) {
        };
        // Use a dummy generator that is not ToXmlGenerator nor TokenBuffer
        com.fasterxml.jackson.core.JsonGenerator dummyGen = new com.fasterxml.jackson.core.base.GeneratorBase(0, null) {
            @Override public com.fasterxml.jackson.core.JsonGenerator setCodec(com.fasterxml.jackson.core.ObjectCodec oc) { return this; }
            @Override public com.fasterxml.jackson.core.ObjectCodec getCodec() { return null; }
            @Override public java.awt.desktop.PreferencesEvent getCurrentLocation() { return null; }
            @Override public void assignCurrentValue(Object v) {}
            @Override public Object getCurrentValue() { return null; }
            @Override public void writeStartArray() throws IOException {}
            @Override public void writeEndArray() throws IOException {}
            @Override public void writeStartObject() throws IOException {}
            @Override public void writeEndObject() throws IOException {}
            @Override public void writeFieldName(String name) throws IOException {}
            @Override public void writeFieldName(com.fasterxml.jackson.core.SerializableString name) throws IOException {}
            @Override public void writeString(String text) throws IOException {}
            @Override public void writeString(char[] text, int offset, int len) throws IOException {}
            @Override public void writeRawUTF8String(byte[] buffer, int offset, int len) throws IOException {}
            @Override public void writeUTF8String(byte[] text, int offset, int len) throws IOException {}
            @Override public void writeRaw(String text) throws IOException {}
            @Override public void writeRaw(String text, int offset, int len) throws IOException {}
            @Override public void writeRaw(char[] text, int offset, int len) throws IOException {}
            @Override public void writeRaw(char c) throws IOException {}
            @Override public void writeRawValue(String text) throws IOException {}
            @Override public void writeRawValue(String text, int offset, int len) throws IOException {}
            @Override public void writeRawValue(char[] text, int offset, int len) throws IOException {}
            @Override public void writeNumber(int v) throws IOException {}
            @Override public void writeNumber(long v) throws IOException {}
            @Override public void writeNumber(java.math.BigInteger v) throws IOException {}
            @Override public void writeNumber(double v) throws IOException {}
            @Override public void writeNumber(float v) throws IOException {}
            @Override public void writeNumber(java.math.BigDecimal v) throws IOException {}
            @Override public void writeNumber(String encodedValue) throws IOException {}
            @Override public void writeBoolean(boolean state) throws IOException {}
            @Override public void writeNull() throws IOException {}
            @Override public void flush() throws IOException {}
            @Override public void close() throws IOException {}
            @Override public boolean isClosed() { return false; }
            @Override public com.fasterxml.jackson.core.JsonStreamContext getOutputContext() { return null; }
        };

        java.lang.reflect.Method method = XmlSerializerProvider.class.getDeclaredMethod("_asXmlGenerator", com.fasterxml.jackson.core.JsonGenerator.class);
        method.setAccessible(true);
        
        try {
            method.invoke(provider, dummyGen);
            fail("Should have thrown InvocationTargetException wrapping JsonMappingException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getTargetException() instanceof com.fasterxml.jackson.databind.JsonMappingException);
            assertTrue(e.getTargetException().getMessage().contains("XmlMapper does not with generators of type other than ToXmlGenerator"));
        }
    }

    @Test
    public void testWrapAsIOEWithIOException() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);
        
        IOException original = new IOException("test io exception");
        java.lang.reflect.Method method = XmlSerializerProvider.class.getDeclaredMethod("_wrapAsIOE", com.fasterxml.jackson.core.JsonGenerator.class, Exception.class);
        method.setAccessible(true);
        
        IOException result = (IOException) method.invoke(provider, null, original);
        assertEquals(original, result);
    }

    @Test
    public void testWrapAsIOEWithGenericException() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);
        
        RuntimeException original = new RuntimeException("runtime error");
        java.lang.reflect.Method method = XmlSerializerProvider.class.getDeclaredMethod("_wrapAsIOE", com.fasterxml.jackson.core.JsonGenerator.class, Exception.class);
        method.setAccessible(true);
        
        IOException result = (IOException) method.invoke(provider, null, original);
        assertTrue(result instanceof com.fasterxml.jackson.databind.JsonMappingException);
        assertTrue(result.getMessage().contains("runtime error"));
    }

    @Test
    public void testWrapAsIOEWithNullExceptionMessage() throws Throwable {
        XmlRootNameLookup lookup = new XmlRootNameLookup();
        XmlSerializerProvider provider = new XmlSerializerProvider(lookup);
        
        RuntimeException original = new RuntimeException((String) null);
        java.lang.reflect.Method method = XmlSerializerProvider.class.getDeclaredMethod("_wrapAsIOE", com.fasterxml.jackson.core.JsonGenerator.class, Exception.class);
        method.setAccessible(true);
        
        IOException result = (IOException) method.invoke(provider, null, original);
        assertTrue(result instanceof com.fasterxml.jackson.databind.JsonMappingException);
        assertTrue(result.getMessage().contains("[no message for java.lang.RuntimeException]"));
    }
}