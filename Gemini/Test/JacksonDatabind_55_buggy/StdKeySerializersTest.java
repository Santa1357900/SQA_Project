package com.fasterxml.jackson.databind.ser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;

public class StdKeySerializersTest {

    @Test
    public void testGetStdKeySerializerNullAndObject() throws Throwable {
        JsonSerializer<Object> ser1 = StdKeySerializers.getStdKeySerializer(null, null, true);
        assertNotNull(ser1);
        assertTrue(ser1 instanceof StdKeySerializers.Dynamic);

        JsonSerializer<Object> ser2 = StdKeySerializers.getStdKeySerializer(null, Object.class, false);
        assertNotNull(ser2);
        assertTrue(ser2 instanceof StdKeySerializers.Dynamic);
    }

    @Test
    public void testGetStdKeySerializerString() throws Throwable {
        JsonSerializer<Object> ser = StdKeySerializers.getStdKeySerializer(null, String.class, true);
        assertNotNull(ser);
        assertTrue(ser instanceof StdKeySerializers.StringKeySerializer);
    }

    @Test
    public void testGetStdKeySerializerPrimitivesAndNumbers() throws Throwable {
        JsonSerializer<Object> serIntPrim = StdKeySerializers.getStdKeySerializer(null, int.class, true);
        assertNotNull(serIntPrim);

        JsonSerializer<Object> serInteger = StdKeySerializers.getStdKeySerializer(null, Integer.class, true);
        assertNotNull(serInteger);

        JsonSerializer<Object> serLong = StdKeySerializers.getStdKeySerializer(null, Long.class, true);
        assertNotNull(serLong);
    }

    @Test
    public void testGetStdKeySerializerClassType() throws Throwable {
        JsonSerializer<Object> ser = StdKeySerializers.getStdKeySerializer(null, Class.class, true);
        assertNotNull(ser);
        assertTrue(ser instanceof StdKeySerializers.Default);
    }

    @Test
    public void testGetStdKeySerializerDateAndCalendar() throws Throwable {
        JsonSerializer<Object> serDate = StdKeySerializers.getStdKeySerializer(null, Date.class, true);
        assertNotNull(serDate);
        assertTrue(serDate instanceof StdKeySerializers.Default);

        JsonSerializer<Object> serCalendar = StdKeySerializers.getStdKeySerializer(null, Calendar.class, true);
        assertNotNull(serCalendar);
        assertTrue(serCalendar instanceof StdKeySerializers.Default);
    }

    @Test
    public void testGetStdKeySerializerUUID() throws Throwable {
        JsonSerializer<Object> ser = StdKeySerializers.getStdKeySerializer(null, UUID.class, true);
        assertNotNull(ser);
        assertTrue(ser instanceof StdKeySerializers.Default);
    }

    @Test
    public void testGetStdKeySerializerFallbackBehavior() throws Throwable {
        JsonSerializer<Object> serTrue = StdKeySerializers.getStdKeySerializer(null, java.io.File.class, true);
        assertNotNull(serTrue);

        JsonSerializer<Object> serFalse = StdKeySerializers.getStdKeySerializer(null, java.io.File.class, false);
        assertNull(serFalse);
    }

    @Test
    public void testGetFallbackKeySerializer() throws Throwable {
        JsonSerializer<Object> serNull = StdKeySerializers.getFallbackKeySerializer(null, null);
        assertNotNull(serNull);

        JsonSerializer<Object> serEnumClass = StdKeySerializers.getFallbackKeySerializer(null, Enum.class);
        assertNotNull(serEnumClass);
        assertTrue(serEnumClass instanceof StdKeySerializers.Dynamic);

        JsonSerializer<Object> serConcreteEnum = StdKeySerializers.getFallbackKeySerializer(null, MockEnum.class);
        assertNotNull(serConcreteEnum);
        assertTrue(serConcreteEnum instanceof StdKeySerializers.Default);

        JsonSerializer<Object> serOther = StdKeySerializers.getFallbackKeySerializer(null, java.io.File.class);
        assertNotNull(serOther);
    }

    @Test
    public void testGetDefault() throws Throwable {
        JsonSerializer<Object> def = StdKeySerializers.getDefault();
        assertNotNull(def);
    }

    @Test
    public void testDynamicReadResolve() throws Throwable {
        StdKeySerializers.Dynamic dynamic = new StdKeySerializers.Dynamic();
        Object resolved = dynamic.readResolve();
        assertNotNull(resolved);
        assertTrue(resolved instanceof StdKeySerializers.Dynamic);
    }

    private enum MockEnum {
        VALUE_A, VALUE_B
    }
}