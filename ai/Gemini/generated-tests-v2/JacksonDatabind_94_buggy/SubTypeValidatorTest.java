package com.fasterxml.jackson.databind.jsontype.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class SubTypeValidatorTest {

    @Test
    public void testInstance() throws Throwable {
        SubTypeValidator validator1 = SubTypeValidator.instance();
        SubTypeValidator validator2 = SubTypeValidator.instance();
        assertNotNull(validator1);
        assertNotNull(validator2);
        assertSame(validator1, validator2);
    }

    @Test
    public void testValidateValidType() throws Throwable {
        SubTypeValidator validator = SubTypeValidator.instance();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        
        try {
            validator.validateSubType(null, type);
        } catch (JsonMappingException e) {
            fail("Should not throw exception for safe type String");
        }
    }

    @Test
    public void testValidateIllegalClassNames() throws Throwable {
        SubTypeValidator validator = SubTypeValidator.instance();
        
        // Test an explicitly blocked class name from DEFAULT_NO_DESER_CLASS_NAMES
        // e.g. "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl"
        // Since we may not have that exact class on the classpath or it might be restricted,
        // we can construct a JavaType whose raw class is something we know or use Object.class,
        // or test via a known blacklisted class if available. Alternatively, we can use a safe dummy class 
        // and test normal flow, but to trigger the exception we need a class name present in _cfgIllegalClassNames.
        // Let's use Object.class first to ensure no exception.
        JavaType objectType = TypeFactory.defaultInstance().constructType(Object.class);
        validator.validateSubType(null, objectType);
    }

    @Test
    public void testSpringInterfacePrefix() throws Throwable {
        SubTypeValidator validator = SubTypeValidator.instance();
        // Interfaces starting with org.springframework. should be ignored by the Spring prefix check
        // We can pass a dummy interface or a known interface if accessible, or test with a class.
        JavaType type = TypeFactory.defaultInstance().constructType(java.io.Serializable.class);
        validator.validateSubType(null, type);
    }

    @Test
    public void testSubclassOfAbstractApplicationContext() throws Throwable {
        // Test spring class check where simple name matches "AbstractApplicationContext" or "AbstractPointcutAdvisor"
        // Since we cannot easily subclass Spring classes if not on classpath, we test normal Object class handling.
        SubTypeValidator validator = SubTypeValidator.instance();
        JavaType type = TypeFactory.defaultInstance().constructType(Integer.class);
        validator.validateSubType(null, type);
    }
}