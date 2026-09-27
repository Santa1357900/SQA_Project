package com.fasterxml.jackson.databind.deser;

import junit.framework.TestCase;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.NameTransformer;

public class BuilderBasedDeserializerTest extends TestCase {

    public void testCopyConstructorsAndUnwrapping() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        JavaType type = TypeFactory.defaultInstance().constructType(SimpleBuilderBean.class);
        BeanDescription beanDesc = BasicBeanDescription.forOtherUse(config, type, null);

        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, config);
        
        BuilderBasedDeserializer deser = new BuilderBasedDeserializer(
                builder, beanDesc, null, null, null, true, false);

        assertNotNull(deser.unwrappingDeserializer(NameTransformer.NOP));
        assertNotNull(deser.withObjectIdReader(null));
        assertNotNull(deser.withIgnorableProperties(null));
        assertNotNull(deser.withBeanProperties(null));
    }

    public void testObjectIdsNotSupported() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        JavaType type = TypeFactory.defaultInstance().constructType(SimpleBuilderBean.class);
        BeanDescription beanDesc = BasicBeanDescription.forOtherUse(config, type, null);

        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, config);
        com.fasterxml.jackson.databind.deser.impl.ObjectIdReader oir = 
                com.fasterxml.jackson.databind.deser.impl.ObjectIdReader.construct(
                        type, null, null, null, null, null);
        builder.setObjectIdReader(oir);

        try {
            new BuilderBasedDeserializer(builder, beanDesc, null, null, null, false, false);
            fail("Expected IllegalArgumentException for Object Id with Builder");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not use Object Id with Builder-based deserialization"));
        }
    }

    public void testExternalTypeIDPropertyBasedNotImplemented() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        JavaType type = TypeFactory.defaultInstance().constructType(SimpleBuilderBean.class);
        BeanDescription beanDesc = BasicBeanDescription.forOtherUse(config, type, null);

        BeanDeserializerBuilder builder = new BeanDeserializerBuilder(beanDesc, config);
        BuilderBasedDeserializer deser = new BuilderBasedDeserializer(
                builder, beanDesc, null, null, null, false, false);

        com.fasterxml.jackson.core.JsonParser p = mapper.getFactory().createParser("{}");
        DeserializationContext ctxt = mapper.getDeserializationContext();

        try {
            java.lang.reflect.Method m = BuilderBasedDeserializer.class.getDeclaredMethod(
                    "deserializeUsingPropertyBasedWithExternalTypeId",
                    com.fasterxml.jackson.core.JsonParser.class,
                    DeserializationContext.class);
            m.setAccessible(true);
            m.invoke(deser, p, ctxt);
            fail("Expected IllegalStateException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertTrue(cause instanceof IllegalStateException);
            assertTrue(cause.getMessage().contains("Deserialization with Builder, External type id"));
        }
    }

    static class SimpleBuilderBean {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}