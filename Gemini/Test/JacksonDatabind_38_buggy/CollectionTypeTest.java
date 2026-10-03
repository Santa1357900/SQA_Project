package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;

public class CollectionTypeTest {

    @Test
    public void testConstructAndToString() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType elemType = tf.uncheckedSimpleType(String.class);
        
        CollectionType type = CollectionType.construct(java.util.List.class, elemType);
        assertNotNull(type);
        assertEquals(java.util.List.class, type.getRawClass());
        assertEquals(elemType, type.getContentType());
        
        String str = type.toString();
        assertNotNull(str);
        assertTrue(str.contains("collection type"));
        assertTrue(str.contains("java.util.List"));
    }

    @Test
    public void testConstructWithBindings() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType elemType = tf.uncheckedSimpleType(Integer.class);
        TypeBindings bindings = TypeBindings.emptyBindings();
        JavaType superClass = tf.uncheckedSimpleType(Object.class);
        JavaType[] superInts = new JavaType[0];

        CollectionType type = CollectionType.construct(java.util.Set.class, bindings, superClass, superInts, elemType);
        assertNotNull(type);
        assertEquals(java.util.Set.class, type.getRawClass());
        assertEquals(elemType, type.getContentType());
    }

    @Test
    public void testWithContentType() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType elemType1 = tf.uncheckedSimpleType(String.class);
        JavaType elemType2 = tf.uncheckedSimpleType(Long.class);

        CollectionType type = CollectionType.construct(java.util.List.class, elemType1);
        
        // Same content type should return this
        JavaType same = type.withContentType(elemType1);
        assertSame(type, same);

        // Different content type should return new instance
        JavaType modified = type.withContentType(elemType2);
        assertNotSame(type, modified);
        assertEquals(elemType2, modified.getContentType());
    }

    @Test
    public void testHandlersAndModifiers() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType elemType = tf.uncheckedSimpleType(String.class);
        CollectionType type = CollectionType.construct(java.util.List.class, elemType);

        Object typeHandler = new Object();
        Object valueHandler = new Object();

        CollectionType withTypeH = type.withTypeHandler(typeHandler);
        assertNotNull(withTypeH);
        assertEquals(typeHandler, withTypeH.getTypeHandler());

        CollectionType withContentTypeH = type.withContentTypeHandler(typeHandler);
        assertNotNull(withContentTypeH);

        CollectionType withValueH = type.withValueHandler(valueHandler);
        assertNotNull(withValueH);
        assertEquals(valueHandler, withValueH.getValueHandler());

        CollectionType withContentValueH = type.withContentValueHandler(valueHandler);
        assertNotNull(withContentValueH);

        CollectionType staticTyping1 = type.withStaticTyping();
        assertNotNull(staticTyping1);
        
        // Second call should return this if already static
        CollectionType staticTyping2 = staticTyping1.withStaticTyping();
        assertSame(staticTyping1, staticTyping2);
    }

    @Test
    public void testRefineAndNarrow() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType elemType = tf.uncheckedSimpleType(String.class);
        CollectionType type = CollectionType.construct(java.util.List.class, elemType);

        TypeBindings bindings = TypeBindings.emptyBindings();
        JavaType superClass = tf.uncheckedSimpleType(Object.class);
        JavaType[] superInts = new JavaType[0];

        JavaType refined = type.refine(java.util.ArrayList.class, bindings, superClass, superInts);
        assertNotNull(refined);
        assertEquals(java.util.ArrayList.class, refined.getRawClass());

        JavaType narrowed = type._narrow(java.util.ArrayList.class);
        assertNotNull(narrowed);
        assertEquals(java.util.ArrayList.class, narrowed.getRawClass());
    }
}