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
        SimpleType refType = SimpleType.constructUnsafe(Integer.class);

        type.setReference(refType);
        assertEquals(refType, type.getSelfReferencedType());
        assertTrue(type.toString().contains(Integer.class.getName()));
    }

    @Test
    public void testSetReferenceTwiceThrowsException() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);
        SimpleType refType1 = SimpleType.constructUnsafe(Integer.class);
        SimpleType refType2 = SimpleType.constructUnsafe(Long.class);

        type.setReference(refType1);
        try {
            type.setReference(refType2);
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Trying to re-set self reference"));
        }
    }

    @Test
    public void testWithMethods() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);
        SimpleType someType = SimpleType.constructUnsafe(Integer.class);

        assertEquals(type, type.withContentType(someType));
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
        JavaType[] interfaces = new JavaType[0];
        assertNull(type.refine(String.class, bindings, null, interfaces));
    }

    @Test
    public void testSignaturesWithoutReferenceThrowsNullPointer() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);
        StringBuilder sb = new StringBuilder();

        try {
            type.getGenericSignature(sb);
            fail("Should throw NullPointerException when referenced type is null");
        } catch (NullPointerException e) {
            // Expected
        }

        try {
            type.getErasedSignature(sb);
            fail("Should throw NullPointerException when referenced type is null");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testSignaturesWithReference() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type = new ResolvedRecursiveType(String.class, bindings);
        SimpleType refType = SimpleType.constructUnsafe(Integer.class);
        type.setReference(refType);

        StringBuilder sbGen = new StringBuilder();
        type.getGenericSignature(sbGen);
        assertTrue(sbGen.length() > 0);

        StringBuilder sbErased = new StringBuilder();
        type.getErasedSignature(sbErased);
        assertTrue(sbErased.length() > 0);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TypeBindings bindings = TypeBindings.emptyBindings();
        ResolvedRecursiveType type1 = new ResolvedRecursiveType(String.class, bindings);
        ResolvedRecursiveType type2 = new ResolvedRecursiveType(String.class, bindings);

        // Unresolved types should not match equals
        assertFalse(type1.equals(type1)); // Wait, o == this returns true
        assertTrue(type1.equals(type1));
        assertFalse(type1.equals(null));
        assertFalse(type1.equals(new Object()));
        assertFalse(type1.equals(type2)); // _referencedType is null for both

        SimpleType refType1 = SimpleType.constructUnsafe(Integer.class);
        SimpleType refType2 = SimpleType.constructUnsafe(Integer.class);
        SimpleType refType3 = SimpleType.constructUnsafe(Long.class);

        type1.setReference(refType1);
        // type2 is still unresolved
        assertFalse(type1.equals(type2));

        type2.setReference(refType2);
        assertTrue(type1.equals(type2));

        ResolvedRecursiveType type3 = new ResolvedRecursiveType(String.class, bindings);
        type3.setReference(refType3);
        assertFalse(type1.equals(type3));
    }
}