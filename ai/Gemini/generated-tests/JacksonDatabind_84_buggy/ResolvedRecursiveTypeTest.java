package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;

public class ResolvedRecursiveTypeTest {

    @Test
    public void testConstructionAndInitialState() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);

        assertNull(type.getSelfReferencedType());
        assertFalse(type.isContainerType());
        assertEquals(String.class, type.getRawClass());
        assertTrue(type.toString().contains("UNRESOLVED"));
    }

    @Test
    public void testSetReference() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);
        
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refType = tf.constructType(Integer.class);

        type.setReference(refType);
        assertEquals(refType, type.getSelfReferencedType());
        assertTrue(type.toString().contains("java.lang.Integer"));
    }

    @Test
    public void testSetReferenceMultipleTimesThrowsException() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);
        
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refType1 = tf.constructType(Integer.class);
        JavaType refType2 = tf.constructType(Boolean.class);

        type.setReference(refType1);

        boolean exceptionThrown = false;
        try {
            type.setReference(refType2);
        } catch (IllegalStateException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("Trying to re-set self reference"));
        }
        assertTrue("Should have thrown IllegalStateException when setting reference twice", exceptionThrown);
    }

    @Test
    public void testWithMethods() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);

        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType dummy = tf.constructType(Integer.class);

        assertEquals(type, type.withContentType(dummy));
        assertEquals(type, type.withTypeHandler(new Object()));
        assertEquals(type, type.withContentTypeHandler(new Object()));
        assertEquals(type, type.withValueHandler(new Object()));
        assertEquals(type, type.withContentValueHandler(new Object()));
        assertEquals(type, type.withStaticTyping());
        assertEquals(type, type._narrow(Integer.class));
    }

    @Test
    public void testRefine() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);

        JavaType refined = type.refine(String.class, bindings, null, null);
        assertNull(refined);
    }

    @Test
    public void testSignaturesAndMethodsWithReferencedType() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);

        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refType = tf.constructType(String.class);
        type.setReference(refType);

        StringBuilder sbGen = new StringBuilder();
        StringBuilder resGen = type.getGenericSignature(sbGen);
        assertNotNull(resGen);

        StringBuilder sbErase = new StringBuilder();
        StringBuilder resErase = type.getErasedSignature(sbErase);
        assertNotNull(resErase);
    }

    @Test
    public void testEqualsAndHashCodeEdgeCases() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type1 = new ResolvedRecursiveType(String.class, bindings);
        ResolvedRecursiveType type2 = new ResolvedRecursiveType(String.class, bindings);

        // Unresolved references should not equal anything (even themselves per implementation)
        assertFalse(type1.equals(type1));
        assertFalse(type1.equals(null));
        assertFalse(type1.equals("some string"));
        assertFalse(type1.equals(type2));

        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refType = tf.constructType(String.class);

        type1.setReference(refType);
        // type2 is still unresolved
        assertFalse(type1.equals(type2));

        ResolvedRecursiveType type3 = new ResolvedRecursiveType(String.class, bindings);
        type3.setReference(refType);

        assertTrue(type1.equals(type3));
    }
}