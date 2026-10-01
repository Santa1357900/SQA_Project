package com.fasterxml.jackson.databind.type;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResolvedRecursiveTypeClaudeTest
{
    private TypeFactory typeFactory;

    @Before
    public void setUp() throws Throwable {
        typeFactory = new ObjectMapper().getTypeFactory();
    }

    // Constructor: fresh instance has no referenced type yet (unresolved)
    @Test
    public void testConstructor_unresolvedType_getSelfReferencedTypeReturnsNull() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertNull(rec.getSelfReferencedType());
    }

    // Constructor: erasedType is stored and retrievable via inherited getRawClass()
    @Test
    public void testConstructor_storesErasedType() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(String.class, TypeBindings.emptyBindings());
        assertEquals(String.class, rec.getRawClass());
    }

    // setReference: first call sets the reference, retrievable via getSelfReferencedType()
    @Test
    public void testSetReference_firstCall_setsReferencedType() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(strType);
        assertSame(strType, rec.getSelfReferencedType());
    }

    // setReference: calling a second time with a real value already set must throw IllegalStateException
    @Test
    public void testSetReference_calledTwice_throwsIllegalStateException() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        JavaType intType = typeFactory.constructType(Integer.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(strType);
        try {
            rec.setReference(intType);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // setReference: setting null then a real value does not throw, since sanity check only guards non-null
    @Test
    public void testSetReference_withNullThenNonNull_doesNotThrow() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(null);
        rec.setReference(strType);
        assertSame(strType, rec.getSelfReferencedType());
    }

    // setReference: once a non-null reference is set, even setting null again must throw
    @Test
    public void testSetReference_nonNullThenNull_throwsIllegalStateException() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(strType);
        try {
            rec.setReference(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // getGenericSignature: must delegate to the referenced type's own signature computation
    @Test
    public void testGetGenericSignature_delegatesToReferencedType() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(strType);
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        StringBuilder result = rec.getGenericSignature(sb1);
        strType.getGenericSignature(sb2);
        assertEquals(sb2.toString(), sb1.toString());
        assertSame(sb1, result);
    }

    // getErasedSignature: must delegate to the referenced type's own signature computation
    @Test
    public void testGetErasedSignature_delegatesToReferencedType() throws Throwable {
        JavaType intType = typeFactory.constructType(Integer.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(intType);
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        StringBuilder result = rec.getErasedSignature(sb1);
        intType.getErasedSignature(sb2);
        assertEquals(sb2.toString(), sb1.toString());
        assertSame(sb1, result);
    }

    // withContentType: placeholder type returns itself unchanged
    @Test
    public void testWithContentType_returnsSameInstance() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertSame(rec, rec.withContentType(strType));
    }

    // withTypeHandler: placeholder type returns itself unchanged
    @Test
    public void testWithTypeHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertSame(rec, rec.withTypeHandler(new Object()));
    }

    // withContentTypeHandler: placeholder type returns itself unchanged
    @Test
    public void testWithContentTypeHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertSame(rec, rec.withContentTypeHandler(new Object()));
    }

    // withValueHandler: placeholder type returns itself unchanged
    @Test
    public void testWithValueHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertSame(rec, rec.withValueHandler(new Object()));
    }

    // withContentValueHandler: placeholder type returns itself unchanged
    @Test
    public void testWithContentValueHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertSame(rec, rec.withContentValueHandler(new Object()));
    }

    // withStaticTyping: placeholder type returns itself unchanged
    @Test
    public void testWithStaticTyping_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertSame(rec, rec.withStaticTyping());
    }

    // refine: placeholder type explicitly returns null (no refinement possible for recursive placeholder)
    @Test
    public void testRefine_returnsNull() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        JavaType result = rec.refine(Object.class, TypeBindings.emptyBindings(), null, null);
        assertNull(result);
    }

    // isContainerType: recursive placeholder is never a container type
    @Test
    public void testIsContainerType_returnsFalse() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertFalse(rec.isContainerType());
    }

    // toString: while unresolved, must indicate UNRESOLVED state
    @Test
    public void testToString_whenUnresolved_containsUNRESOLVED() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        String s = rec.toString();
        assertTrue(s.contains("UNRESOLVED"));
    }

    // toString: once resolved, must include the referenced type's raw class name, not UNRESOLVED
    @Test
    public void testToString_whenResolved_containsRawClassName() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(strType);
        String s = rec.toString();
        assertTrue(s.contains("java.lang.String"));
        assertFalse(s.contains("UNRESOLVED"));
    }

    // equals: same instance reference must be equal to itself
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(strType);
        assertTrue(rec.equals(rec));
    }

    // equals: comparing against null must return false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        assertFalse(rec.equals(null));
    }

    // equals: an unresolved reference (_referencedType == null) must never match anything
    @Test
    public void testEquals_unresolvedReference_returnsFalse() throws Throwable {
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        ResolvedRecursiveType other = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        JavaType strType = typeFactory.constructType(String.class);
        other.setReference(strType);
        assertFalse(rec.equals(other));
    }

    // equals: comparing against an object of a different class must return false
    @Test
    public void testEquals_differentClassObject_returnsFalse() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec.setReference(strType);
        assertFalse(rec.equals(new Object()));
    }

    // equals: two resolved instances with equal referenced types must be equal
    @Test
    public void testEquals_sameClassSameReferencedType_returnsTrue() throws Throwable {
        JavaType strType1 = typeFactory.constructType(String.class);
        JavaType strType2 = typeFactory.constructType(String.class);
        ResolvedRecursiveType rec1 = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        ResolvedRecursiveType rec2 = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec1.setReference(strType1);
        rec2.setReference(strType2);
        assertTrue(rec1.equals(rec2));
    }

    // equals: two resolved instances with different referenced types must not be equal
    @Test
    public void testEquals_sameClassDifferentReferencedType_returnsFalse() throws Throwable {
        JavaType strType = typeFactory.constructType(String.class);
        JavaType intType = typeFactory.constructType(Integer.class);
        ResolvedRecursiveType rec1 = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        ResolvedRecursiveType rec2 = new ResolvedRecursiveType(Object.class, TypeBindings.emptyBindings());
        rec1.setReference(strType);
        rec2.setReference(intType);
        assertFalse(rec1.equals(rec2));
    }
}
