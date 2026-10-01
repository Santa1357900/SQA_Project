package com.fasterxml.jackson.databind.type;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResolvedRecursiveTypeClaudeTest
{
    private ObjectMapper mapper;
    private TypeBindings bindings;

    @Before
    public void setUp() throws Throwable
    {
        mapper = new ObjectMapper();
        bindings = TypeBindings.emptyBindings();
    }

    // constructor: fresh instance has no self reference yet
    @Test
    public void testConstructor_createsInstance_withNullSelfReference() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        assertNull(t.getSelfReferencedType());
    }

    // getSelfReferencedType before any setReference call
    @Test
    public void testGetSelfReferencedType_initial_returnsNull() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(Integer.class, bindings);
        assertNull(t.getSelfReferencedType());
    }

    // setReference: normal path sets field, getter returns same instance
    @Test
    public void testSetReference_validReference_getterReturnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType ref = mapper.getTypeFactory().constructType(String.class);
        t.setReference(ref);
        assertSame(ref, t.getSelfReferencedType());
    }

    // setReference: sanity check branch throwing IllegalStateException on re-set
    @Test
    public void testSetReference_calledTwice_throwsIllegalStateException() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType ref1 = mapper.getTypeFactory().constructType(String.class);
        JavaType ref2 = mapper.getTypeFactory().constructType(Integer.class);
        t.setReference(ref1);
        try {
            t.setReference(ref2);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("re-set"));
        }
    }

    // getGenericSignature: unresolved type delegates to null field -> NPE
    @Test
    public void testGetGenericSignature_unresolvedType_throwsNullPointerException() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        try {
            t.getGenericSignature(new StringBuilder());
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // getGenericSignature: resolved type delegates correctly to referenced type
    @Test
    public void testGetGenericSignature_resolvedType_delegatesToReferencedType() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType ref = mapper.getTypeFactory().constructType(String.class);
        t.setReference(ref);
        String expected = ref.getGenericSignature(new StringBuilder()).toString();
        String actual = t.getGenericSignature(new StringBuilder()).toString();
        assertEquals(expected, actual);
    }

    // getErasedSignature: unresolved type delegates to null field -> NPE
    @Test
    public void testGetErasedSignature_unresolvedType_throwsNullPointerException() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        try {
            t.getErasedSignature(new StringBuilder());
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // getErasedSignature: resolved type delegates correctly to referenced type
    @Test
    public void testGetErasedSignature_resolvedType_delegatesToReferencedType() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType ref = mapper.getTypeFactory().constructType(String.class);
        t.setReference(ref);
        String expected = ref.getErasedSignature(new StringBuilder()).toString();
        String actual = t.getErasedSignature(new StringBuilder()).toString();
        assertEquals(expected, actual);
    }

    // withContentType: always returns this placeholder instance
    @Test
    public void testWithContentType_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType other = mapper.getTypeFactory().constructType(Integer.class);
        JavaType result = t.withContentType(other);
        assertSame(t, result);
    }

    // withTypeHandler: always returns this placeholder instance
    @Test
    public void testWithTypeHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType result = t.withTypeHandler("someHandler");
        assertSame(t, result);
    }

    // withContentTypeHandler: always returns this placeholder instance
    @Test
    public void testWithContentTypeHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType result = t.withContentTypeHandler("someHandler");
        assertSame(t, result);
    }

    // withValueHandler: always returns this placeholder instance
    @Test
    public void testWithValueHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType result = t.withValueHandler("someHandler");
        assertSame(t, result);
    }

    // withContentValueHandler: always returns this placeholder instance
    @Test
    public void testWithContentValueHandler_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType result = t.withContentValueHandler("someHandler");
        assertSame(t, result);
    }

    // withStaticTyping: always returns this placeholder instance
    @Test
    public void testWithStaticTyping_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType result = t.withStaticTyping();
        assertSame(t, result);
    }

    // deprecated _narrow: always returns this placeholder instance
    @Test
    public void testNarrow_deprecatedMethod_returnsSameInstance() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType result = t._narrow(Integer.class);
        assertSame(t, result);
    }

    // refine: always returns null for this placeholder type
    @Test
    public void testRefine_alwaysReturnsNull() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        JavaType result = t.refine(String.class, bindings, null, null);
        assertNull(result);
    }

    // isContainerType: false for unresolved instance
    @Test
    public void testIsContainerType_unresolved_returnsFalse() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        assertFalse(t.isContainerType());
    }

    // isContainerType: false even after resolution
    @Test
    public void testIsContainerType_resolved_returnsFalse() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        t.setReference(mapper.getTypeFactory().constructType(String.class));
        assertFalse(t.isContainerType());
    }

    // toString: unresolved branch reports UNRESOLVED marker
    @Test
    public void testToString_unresolved_containsUnresolvedMarker() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        String s = t.toString();
        assertTrue(s.contains("UNRESOLVED"));
    }

    // toString: resolved branch reports raw class name of referenced type
    @Test
    public void testToString_resolved_containsRawClassName() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        t.setReference(mapper.getTypeFactory().constructType(String.class));
        String s = t.toString();
        assertTrue(s.contains("java.lang.String"));
    }

    // equals: identity short-circuit returns true
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        assertTrue(t.equals(t));
    }

    // equals: comparing against null returns false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        assertFalse(t.equals(null));
    }

    // equals: unresolved self reference must never match anything per contract
    @Test
    public void testEquals_unresolvedSelf_returnsFalse() throws Throwable {
        ResolvedRecursiveType t1 = new ResolvedRecursiveType(String.class, bindings);
        ResolvedRecursiveType t2 = new ResolvedRecursiveType(String.class, bindings);
        t2.setReference(mapper.getTypeFactory().constructType(String.class));
        assertFalse(t1.equals(t2));
    }

    // equals: both unresolved instances never match either
    @Test
    public void testEquals_bothUnresolved_returnsFalse() throws Throwable {
        ResolvedRecursiveType t1 = new ResolvedRecursiveType(String.class, bindings);
        ResolvedRecursiveType t2 = new ResolvedRecursiveType(String.class, bindings);
        assertFalse(t1.equals(t2));
    }

    // equals: different runtime class of compared object returns false
    @Test
    public void testEquals_differentClassType_returnsFalse() throws Throwable {
        ResolvedRecursiveType t = new ResolvedRecursiveType(String.class, bindings);
        t.setReference(mapper.getTypeFactory().constructType(String.class));
        JavaType other = mapper.getTypeFactory().constructType(String.class);
        assertFalse(t.equals(other));
    }

    // equals: two resolved instances with equal referenced types match
    @Test
    public void testEquals_sameReferencedType_returnsTrue() throws Throwable {
        ResolvedRecursiveType t1 = new ResolvedRecursiveType(String.class, bindings);
        ResolvedRecursiveType t2 = new ResolvedRecursiveType(String.class, bindings);
        t1.setReference(mapper.getTypeFactory().constructType(String.class));
        t2.setReference(mapper.getTypeFactory().constructType(String.class));
        assertTrue(t1.equals(t2));
    }

    // equals: two resolved instances with different referenced types don't match
    @Test
    public void testEquals_differentReferencedType_returnsFalse() throws Throwable {
        ResolvedRecursiveType t1 = new ResolvedRecursiveType(String.class, bindings);
        ResolvedRecursiveType t2 = new ResolvedRecursiveType(String.class, bindings);
        t1.setReference(mapper.getTypeFactory().constructType(String.class));
        t2.setReference(mapper.getTypeFactory().constructType(Integer.class));
        assertFalse(t1.equals(t2));
    }
}
