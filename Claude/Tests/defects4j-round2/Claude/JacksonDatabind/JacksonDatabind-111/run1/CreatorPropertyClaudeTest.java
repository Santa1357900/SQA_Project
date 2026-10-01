package com.fasterxml.jackson.databind.deser;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SettableBeanProperty;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;

public class CreatorPropertyClaudeTest {

    private ObjectMapper mapper;
    private JavaType stringType;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
        stringType = mapper.getTypeFactory().constructType(String.class);
    }

    private CreatorProperty createProperty(String name, int index, Object injectableId) {
        return new CreatorProperty(PropertyName.construct(name), stringType, null, null,
                null, null, index, injectableId, PropertyMetadata.STD_REQUIRED_OR_OPTIONAL);
    }

    // constructor: name, index, injectable id fields set as provided
    @Test
    public void testConstructor_basicFields_setCorrectly() throws Throwable {
        CreatorProperty prop = createProperty("foo", 2, "injectId");
        assertEquals("foo", prop.getName());
        assertEquals(2, prop.getCreatorIndex());
        assertEquals("injectId", prop.getInjectableValueId());
    }

    // constructor: null injectableValueId is preserved as null (not defaulted)
    @Test
    public void testConstructor_nullInjectableValueId_returnsNull() throws Throwable {
        CreatorProperty prop = createProperty("bar", 0, null);
        assertNull(prop.getInjectableValueId());
    }

    // withName: returns new CreatorProperty with updated name, preserving index/injectId
    @Test
    public void testWithName_returnsNewInstanceWithNewName() throws Throwable {
        CreatorProperty prop = createProperty("foo", 3, "id1");
        SettableBeanProperty renamed = prop.withName(PropertyName.construct("newName"));
        assertEquals("newName", renamed.getName());
        assertTrue(renamed instanceof CreatorProperty);
        assertEquals(3, ((CreatorProperty) renamed).getCreatorIndex());
        assertEquals("id1", ((CreatorProperty) renamed).getInjectableValueId());
    }

    // withName: copy constructor preserves ignorable flag set before renaming
    @Test
    public void testWithName_preservesIgnorableFlag() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        prop.markAsIgnorable();
        CreatorProperty renamed = (CreatorProperty) prop.withName(PropertyName.construct("bar"));
        assertTrue(renamed.isIgnorable());
    }

    // withValueDeserializer: identical (both null) deserializer returns same instance
    @Test
    public void testWithValueDeserializer_sameDeserializer_returnsSameInstance() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        SettableBeanProperty result = prop.withValueDeserializer(null);
        assertSame(prop, result);
    }

    // withValueDeserializer: different (non-null) deserializer creates new instance
    @Test
    public void testWithValueDeserializer_differentDeserializer_returnsNewInstance() throws Throwable {
        CreatorProperty prop = createProperty("foo", 1, null);
        JsonDeserializer<Object> deser = new JsonDeserializer<Object>() {
            @Override
            public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return null;
            }
        };
        SettableBeanProperty result = prop.withValueDeserializer(deser);
        assertNotSame(prop, result);
        assertTrue(result instanceof CreatorProperty);
        assertEquals(1, ((CreatorProperty) result).getCreatorIndex());
    }

    // withValueDeserializer: calling again with same non-null reference returns same instance
    @Test
    public void testWithValueDeserializer_sameNonNullDeserializerTwice_returnsSameInstance() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        JsonDeserializer<Object> deser = new JsonDeserializer<Object>() {
            @Override
            public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return "x";
            }
        };
        SettableBeanProperty first = prop.withValueDeserializer(deser);
        SettableBeanProperty second = first.withValueDeserializer(deser);
        assertSame(first, second);
    }

    // withNullProvider: always creates a new instance preserving name/index/injectId
    @Test
    public void testWithNullProvider_returnsNewInstancePreservingFields() throws Throwable {
        CreatorProperty prop = createProperty("foo", 4, "xid");
        SettableBeanProperty result = prop.withNullProvider(null);
        assertNotSame(prop, result);
        assertTrue(result instanceof CreatorProperty);
        assertEquals("foo", result.getName());
        assertEquals(4, ((CreatorProperty) result).getCreatorIndex());
        assertEquals("xid", ((CreatorProperty) result).getInjectableValueId());
    }

    // fixAccess: no fallback setter configured -> NOP, no exception thrown
    @Test
    public void testFixAccess_noFallbackSetter_doesNotThrow() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        DeserializationConfig config = mapper.getDeserializationConfig();
        prop.fixAccess(config);
        assertNull(prop.getInjectableValueId());
    }

    // fixAccess: fallback setter present -> delegates call without throwing
    @Test
    public void testFixAccess_withFallbackSetter_delegatesWithoutException() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        CreatorProperty fallback = createProperty("fallback", 0, null);
        prop.setFallbackSetter(fallback);
        DeserializationConfig config = mapper.getDeserializationConfig();
        prop.fixAccess(config);
        assertEquals("foo", prop.getName());
    }

    // isIgnorable/markAsIgnorable: default false, becomes true after marking
    @Test
    public void testIsIgnorable_defaultFalse_thenTrueAfterMark() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        assertFalse(prop.isIgnorable());
        prop.markAsIgnorable();
        assertTrue(prop.isIgnorable());
    }

    // getAnnotation: null annotated member (synthetic property) -> returns null
    @Test
    public void testGetAnnotation_nullAnnotatedMember_returnsNull() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        assertNull(prop.getAnnotation(Deprecated.class));
    }

    // getMember: returns underlying annotated parameter (null in synthetic construction)
    @Test
    public void testGetMember_returnsNullWhenNoAnnotatedParameter() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        assertNull(prop.getMember());
    }

    // getCreatorIndex: zero boundary value
    @Test
    public void testGetCreatorIndex_zero() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        assertEquals(0, prop.getCreatorIndex());
    }

    // getCreatorIndex: positive value preserved exactly
    @Test
    public void testGetCreatorIndex_positiveValue() throws Throwable {
        CreatorProperty prop = createProperty("foo", 7, null);
        assertEquals(7, prop.getCreatorIndex());
    }

    // getCreatorIndex: negative value also preserved (no validation performed)
    @Test
    public void testGetCreatorIndex_negativeValuePreserved() throws Throwable {
        CreatorProperty prop = createProperty("foo", -1, null);
        assertEquals(-1, prop.getCreatorIndex());
    }

    // deserializeAndSet: no fallback setter configured -> throws InvalidDefinitionException
    @Test
    public void testDeserializeAndSet_noFallbackSetter_throwsInvalidDefinitionException() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        try {
            prop.deserializeAndSet(null, null, new Object());
            fail("expected InvalidDefinitionException");
        } catch (InvalidDefinitionException expected) {
        }
    }

    // deserializeSetAndReturn: no fallback setter configured -> throws InvalidDefinitionException
    @Test
    public void testDeserializeSetAndReturn_noFallbackSetter_throwsInvalidDefinitionException() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        try {
            prop.deserializeSetAndReturn(null, null, new Object());
            fail("expected InvalidDefinitionException");
        } catch (InvalidDefinitionException expected) {
        }
    }

    // set: no fallback setter configured -> throws InvalidDefinitionException
    @Test
    public void testSet_noFallbackSetter_throwsInvalidDefinitionException() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        try {
            prop.set(new Object(), "value");
            fail("expected InvalidDefinitionException");
        } catch (InvalidDefinitionException expected) {
        }
    }

    // setAndReturn: no fallback setter configured -> throws InvalidDefinitionException
    @Test
    public void testSetAndReturn_noFallbackSetter_throwsInvalidDefinitionException() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        try {
            prop.setAndReturn(new Object(), "value");
            fail("expected InvalidDefinitionException");
        } catch (InvalidDefinitionException expected) {
        }
    }

    // getInjectableValueId: returns exactly the id supplied at construction
    @Test
    public void testGetInjectableValueId_returnsConfiguredValue() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, "injectionKey");
        assertEquals("injectionKey", prop.getInjectableValueId());
    }

    // getInjectableValueId: null when not configured
    @Test
    public void testGetInjectableValueId_nullWhenNotConfigured() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        assertNull(prop.getInjectableValueId());
    }

    // toString: format contains property name and inject id per implementation contract
    @Test
    public void testToString_containsNameAndInjectId() throws Throwable {
        CreatorProperty prop = createProperty("myProp", 0, "myId");
        String s = prop.toString();
        assertTrue(s.contains("myProp"));
        assertTrue(s.contains("myId"));
        assertTrue(s.startsWith("[creator property, name '"));
    }

    // toString: with null injectable id, literal 'null' appears in text
    @Test
    public void testToString_withNullInjectId_containsNullText() throws Throwable {
        CreatorProperty prop = createProperty("myProp", 0, null);
        String s = prop.toString();
        assertTrue(s.contains("myProp"));
        assertTrue(s.contains("null"));
    }

    // setFallbackSetter: setting fallback does not alter ignorable flag
    @Test
    public void testSetFallbackSetter_doesNotAffectIgnorableFlag() throws Throwable {
        CreatorProperty prop = createProperty("foo", 0, null);
        CreatorProperty fallback = createProperty("fallback", 0, null);
        prop.setFallbackSetter(fallback);
        assertFalse(prop.isIgnorable());
    }

    // withNullProvider followed by withName: chained transformations preserve independent fields
    @Test
    public void testWithNullProvider_thenWithName_chainPreservesData() throws Throwable {
        CreatorProperty prop = createProperty("foo", 9, "chainId");
        CreatorProperty afterNullProvider = (CreatorProperty) prop.withNullProvider(null);
        CreatorProperty afterRename = (CreatorProperty) afterNullProvider.withName(PropertyName.construct("renamed"));
        assertEquals("renamed", afterRename.getName());
        assertEquals(9, afterRename.getCreatorIndex());
        assertEquals("chainId", afterRename.getInjectableValueId());
    }
}
