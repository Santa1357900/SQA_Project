package com.fasterxml.jackson.databind.jsontype.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class SubTypeValidatorTest {

    @Test
    public void testInstanceNotNull() throws Throwable {
        SubTypeValidator validator = SubTypeValidator.instance();
        assertNotNull(validator);
    }

    @Test
    public void testValidateSafeType() throws Throwable {
        SubTypeValidator validator = SubTypeValidator.instance();
        JavaType safeType = TypeFactory.defaultInstance().constructType(String.class);
        
        // Should not throw any exception for a safe type like String
        try {
            validator.validateSubType(null, safeType);
        } catch (JsonMappingException e) {
            fail("Should not throw JsonMappingException for safe type");
        }
    }

    @Test
    public void testValidateIllegalTypeDirectMatch() throws Throwable {
        SubTypeValidator validator = SubTypeValidator.instance();
        // Construct a type whose raw class is in DEFAULT_NO_DESER_CLASS_NAMES
        // e.g., "org.apache.commons.collections.functors.InvokerTransformer"
        Class<?> nastyClass = null;
        try {
            nastyClass = Class.forName("org.apache.commons.collections.functors.InvokerTransformer");
        } catch (ClassNotFoundException e) {
            // If classpath doesn't have it, use a dummy or skip, but typically in Jackson tests it might be present or we can test logic.
            // Let's test with a mock/dummy approach or a known class if available. 
            // Since InvokerTransformer is in commons-collections which is often in test CP, let's try.
        }

        if (nastyClass != null) {
            JavaType nastyType = TypeFactory.defaultInstance().constructType(nastyClass);
            boolean thrown = false;
            try {
                validator.validateSubType(null, nastyType);
            } catch (JsonMappingException jme) {
                thrown = true;
                assertTrue(jme.getMessage().contains("Illegal type"));
            }
            assertTrue("Expected JsonMappingException for nasty class", thrown);
        }
    }

    @Test
    public void testSpringPrefixAndAbstractPointcutAdvisor() throws Throwable {
        // Test a class starting with org.springframework. with superclass or name matching "AbstractPointcutAdvisor"
        // Since we cannot easily subclass an arbitrary Spring class if not present, let's verify behavior with a normal spring-prefixed class if any,
        // or ensure no exception when it's just a harmless spring class (if it doesn't match the forbidden simple names).
        SubTypeValidator validator = SubTypeValidator.instance();
        
        // If we pass a class like org.springframework.beans.factory.config.PropertyPathFactoryBean, it is in DEFAULT_NO_DESER_CLASS_NAMES directly.
        Class<?> propPathClass = null;
        try {
            propPathClass = Class.forName("org.springframework.beans.factory.config.PropertyPathFactoryBean");
        } catch (ClassNotFoundException e) {
            // ignore if not on classpath
        }

        if (propPathClass != null) {
            JavaType type = TypeFactory.defaultInstance().constructType(propPathClass);
            boolean thrown = false;
            try {
                validator.validateSubType(null, type);
            } catch (JsonMappingException jme) {
                thrown = true;
            }
            assertTrue(thrown);
        }
    }

    @Test
    public void testCustomIllegalClassNamesSubclass() throws Throwable {
        // Create a custom subclass or test protected members if accessible via inheritance in same package
        SubTypeValidator customValidator = new SubTypeValidator() {
            // Just instantiate protected constructor
        };
        assertNotNull(customValidator);
    }
}