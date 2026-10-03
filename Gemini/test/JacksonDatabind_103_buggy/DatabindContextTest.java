package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Type;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.ObjectIdInfo;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

public class DatabindContextTest {

    private static class DummyDatabindContext extends DatabindContext {
        @Override
        public MapperConfig<?> getConfig() {
            return null;
        }

        @Override
        public AnnotationIntrospector getAnnotationIntrospector() {
            return null;
        }

        @Override
        public boolean isEnabled(MapperFeature feature) {
            return false;
        }

        @Override
        public boolean canOverrideAccessModifiers() {
            return false;
        }

        @Override
        public Class<?> getActiveView() {
            return null;
        }

        @Override
        public Locale getLocale() {
            return null;
        }

        @Override
        public TimeZone getTimeZone() {
            return null;
        }

        @Override
        public JsonFormat.Value getDefaultPropertyFormat(Class<?> baseType) {
            return null;
        }

        @Override
        public Object getAttribute(Object key) {
            return null;
        }

        @Override
        public DatabindContext setAttribute(Object key, Object value) {
            return this;
        }

        @Override
        protected JsonMappingException invalidTypeIdException(JavaType baseType, String typeId, String extraDesc) {
            return new JsonMappingException(null, "Invalid type id");
        }

        @Override
        public TypeFactory getTypeFactory() {
            return TypeFactory.defaultInstance();
        }

        @Override
        public <T> T reportBadDefinition(JavaType type, String msg) throws JsonMappingException {
            throw new JsonMappingException(null, msg);
        }
    }

    @Test
    public void testConstructTypeNull() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        JavaType result = context.constructType(null);
        assertNull(result);
    }

    @Test
    public void testConstructTypeValid() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        Type type = String.class;
        JavaType result = context.constructType(type);
        assertNotNull(result);
        assertEquals(String.class, result.getRawClass());
    }

    @Test
    public void testConstructSpecializedTypeSameClass() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        JavaType baseType = TypeFactory.defaultInstance().constructType(String.class);
        JavaType result = context.constructSpecializedType(baseType, String.class);
        assertEquals(baseType, result);
    }

    @Test
    public void testFormatNoArgs() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String formatted = context._format("Hello World");
        assertEquals("Hello World", formatted);
    }

    @Test
    public void testFormatWithArgs() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String formatted = context._format("Hello %s", "Jackson");
        assertEquals("Hello Jackson", formatted);
    }

    @Test
    public void testTruncateNull() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String truncated = context._truncate(null);
        assertEquals("", truncated);
    }

    @Test
    public void testTruncateShort() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String truncated = context._truncate("Short string");
        assertEquals("Short string", truncated);
    }

    @Test
    public void testTruncateLong() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            sb.append("a");
        }
        String longStr = sb.toString();
        String truncated = context._truncate(longStr);
        assertTrue(truncated.contains("]...["));
    }

    @Test
    public void testQuotedStringNull() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String quoted = context._quotedString(null);
        assertEquals("[N/A]", quoted);
    }

    @Test
    public void testQuotedStringValid() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String quoted = context._quotedString("test");
        assertEquals("\"test\"", quoted);
    }

    @Test
    public void testColonConcatNullExtra() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String result = context._colonConcat("Base", null);
        assertEquals("Base", result);
    }

    @Test
    public void testColonConcatValidExtra() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String result = context._colonConcat("Base", "Extra");
        assertEquals("Base: Extra", result);
    }

    @Test
    public void testDescNull() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String desc = context._desc(null);
        assertEquals("[N/A]", desc);
    }

    @Test
    public void testDescValid() throws Throwable {
        DummyDatabindContext context = new DummyDatabindContext();
        String desc = context._desc("Description");
        assertEquals("Description", desc);
    }

    @Test
    public void testConverterInstanceNull() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        Converter<Object, Object> conv = context.converterInstance(null, null);
        assertNull(conv);
    }

    @Test
    public void testConverterInstanceDirect() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        final Converter<Object, Object> dummyConv = new Converter<Object, Object>() {
            public Object convert(Object value) { return value; }
            public JavaType getInputType(TypeFactory tf) { return null; }
            public JavaType getOutputType(TypeFactory tf) { return null; }
        };
        Converter<Object, Object> conv = context.converterInstance(null, dummyConv);
        assertEquals(dummyConv, conv);
    }

    @Test
    public void testConverterInstanceInvalidType() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        try {
            context.converterInstance(null, new Object());
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected type Converter or Class<Converter>"));
        }
    }

    @Test
    public void testConverterInstanceBogusClass() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        Converter<Object, Object> conv = context.converterInstance(null, Converter.None.class);
        assertNull(conv);
    }

    @Test
    public void testConverterInstanceNotAssignable() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        try {
            context.converterInstance(null, String.class);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("expected Class<Converter>"));
        }
    }

    @Test
    public void testReportBadDefinitionClass() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        try {
            context.reportBadDefinition(String.class, "Bad definition");
            fail("Should have thrown JsonMappingException");
        } catch (JsonMappingException e) {
            assertTrue(e.getMessage().contains("Bad definition"));
        }
    }

    @Test
    public void testResolveSubTypeCanonical() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        JavaType resolved = context.resolveSubType(baseType, "java.lang.String");
        assertNotNull(resolved);
        assertEquals(String.class, resolved.getRawClass());
    }

    @Test
    public void testResolveSubTypeWithGenerics() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        JavaType resolved = context.resolveSubType(baseType, "java.util.List<java.lang.String>");
        assertNotNull(resolved);
    }

    @Test
    public void testResolveSubTypeClassNotFound() throws Throwable {
        DatabindContext context = new DummyDatabindContext();
        JavaType baseType = TypeFactory.defaultInstance().constructType(Object.class);
        JavaType resolved = context.resolveSubType(baseType, "com.nonexistent.ClassXYZ");
        assertNull(resolved);
    }
}