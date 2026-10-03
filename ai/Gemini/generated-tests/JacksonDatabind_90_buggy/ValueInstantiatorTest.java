package com.fasterxml.jackson.databind.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer;

public class ValueInstantiatorTest {

    private static class ConcreteValueInstantiator extends ValueInstantiator {
        private final Class<?> _vc;
        private boolean _canCreateFromString = false;
        private boolean _canCreateFromInt = false;
        private boolean _canCreateFromLong = false;
        private boolean _canCreateFromDouble = false;
        private boolean _canCreateFromBoolean = false;
        private boolean _canCreateUsingDefault = false;
        private boolean _canCreateUsingDelegate = false;
        private boolean _canCreateUsingArrayDelegate = false;
        private boolean _canCreateFromObjectWith = false;

        public ConcreteValueInstantiator(Class<?> vc) {
            _vc = vc;
        }

        @Override
        public Class<?> getValueClass() {
            return _vc;
        }

        public void setCanCreateFromString(boolean b) { _canCreateFromString = b; }
        public void setCanCreateFromInt(boolean b) { _canCreateFromInt = b; }
        public void setCanCreateFromLong(boolean b) { _canCreateFromLong = b; }
        public void setCanCreateFromDouble(boolean b) { _canCreateFromDouble = b; }
        public void setCanCreateFromBoolean(boolean b) { _canCreateFromBoolean = b; }
        public void setCanCreateUsingDefault(boolean b) { _canCreateUsingDefault = b; }
        public void setCanCreateUsingDelegate(boolean b) { _canCreateUsingDelegate = b; }
        public void setCanCreateUsingArrayDelegate(boolean b) { _canCreateUsingArrayDelegate = b; }
        public void setCanCreateFromObjectWith(boolean b) { _canCreateFromObjectWith = b; }

        @Override
        public boolean canCreateFromString() { return _canCreateFromString; }
        @Override
        public boolean canCreateFromInt() { return _canCreateFromInt; }
        @Override
        public boolean canCreateFromLong() { return _canCreateFromLong; }
        @Override
        public boolean canCreateFromDouble() { return _canCreateFromDouble; }
        @Override
        public boolean canCreateFromBoolean() { return _canCreateFromBoolean; }
        @Override
        public boolean canCreateUsingDefault() { return _canCreateUsingDefault; }
        @Override
        public boolean canCreateUsingDelegate() { return _canCreateUsingDelegate; }
        @Override
        public boolean canCreateUsingArrayDelegate() { return _canCreateUsingArrayDelegate; }
        @Override
        public boolean canCreateFromObjectWith() { return _canCreateFromObjectWith; }

        @Override
        public Object createFromString(DeserializationContext ctxt, String value) throws java.io.IOException {
            if (_canCreateFromString) {
                return "created:" + value;
            }
            return super.createFromString(ctxt, value);
        }

        @Override
        public Object createFromBoolean(DeserializationContext ctxt, boolean value) throws java.io.IOException {
            if (_canCreateFromBoolean) {
                return Boolean.valueOf(value);
            }
            return super.createFromBoolean(ctxt, value);
        }
    }

    @Test
    public void testBaseConstructorsAndMetadata() throws Throwable {
        ValueInstantiator.Base base1 = new ValueInstantiator.Base(String.class);
        assertEquals(String.class, base1.getValueClass());
        assertEquals("java.lang.String", base1.getValueTypeDesc());

        JavaType jtype = TypeFactory.defaultInstance().constructType(Integer.class);
        ValueInstantiator.Base base2 = new ValueInstantiator.Base(jtype);
        assertEquals(Integer.class, base2.getValueClass());
        assertEquals("java.lang.Integer", base2.getValueTypeDesc());
    }

    @Test
    public void testDefaultValueInstantiatorMetadata() throws Throwable {
        ValueInstantiator inst = new ConcreteValueInstantiator(null);
        assertEquals(Object.class, inst.getValueClass());
        assertEquals("UNKNOWN", inst.getValueTypeDesc());

        ValueInstantiator inst2 = new ConcreteValueInstantiator(Boolean.class);
        assertEquals(Boolean.class, inst2.getValueClass());
        assertEquals("java.lang.Boolean", inst2.getValueTypeDesc());
    }

    @Test
    public void testCanInstantiateCombinations() throws Throwable {
        ConcreteValueInstantiator inst = new ConcreteValueInstantiator(Object.class);
        assertFalse(inst.canInstantiate());

        inst.setCanCreateFromString(true);
        assertTrue(inst.canInstantiate());

        ConcreteValueInstantiator inst2 = new ConcreteValueInstantiator(Object.class);
        inst2.setCanCreateFromInt(true);
        assertTrue(inst2.canInstantiate());

        ConcreteValueInstantiator inst3 = new ConcreteValueInstantiator(Object.class);
        inst3.setCanCreateFromLong(true);
        assertTrue(inst3.canInstantiate());

        ConcreteValueInstantiator inst4 = new ConcreteValueInstantiator(Object.class);
        inst4.setCanCreateFromDouble(true);
        assertTrue(inst4.canInstantiate());

        ConcreteValueInstantiator inst5 = new ConcreteValueInstantiator(Object.class);
        inst5.setCanCreateFromBoolean(true);
        assertTrue(inst5.canInstantiate());

        ConcreteValueInstantiator inst6 = new ConcreteValueInstantiator(Object.class);
        inst6.setCanCreateUsingDefault(true);
        assertTrue(inst6.canInstantiate());

        ConcreteValueInstantiator inst7 = new ConcreteValueInstantiator(Object.class);
        inst7.setCanCreateUsingDelegate(true);
        assertTrue(inst7.canInstantiate());

        ConcreteValueInstantiator inst8 = new ConcreteValueInstantiator(Object.class);
        inst8.setCanCreateFromObjectWith(true);
        assertTrue(inst8.canInstantiate());
    }

    @Test
    public void testDefaultCapabilityAccessors() throws Throwable {
        ValueInstantiator inst = new ConcreteValueInstantiator(Object.class);
        assertFalse(inst.canCreateFromString());
        assertFalse(inst.canCreateFromInt());
        assertFalse(inst.canCreateFromLong());
        assertFalse(inst.canCreateFromDouble());
        assertFalse(inst.canCreateFromBoolean());
        assertFalse(inst.canCreateUsingDefault());
        assertFalse(inst.canCreateUsingDelegate());
        assertFalse(inst.canCreateUsingArrayDelegate());
        assertFalse(inst.canCreateFromObjectWith());

        assertNull(inst.getFromObjectArguments(null));
        assertNull(inst.getDelegateType(null));
        assertNull(inst.getArrayDelegateType(null));

        assertNull(inst.getDefaultCreator());
        assertNull(inst.getDelegateCreator());
        assertNull(inst.getArrayDelegateCreator());
        assertNull(inst.getWithArgsCreator());
        assertNull(inst.getIncompleteParameter());
    }

    @Test
    public void testCreateFromObjectWithDefaultBuffer() throws Throwable {
        ValueInstantiator inst = new ConcreteValueInstantiator(Object.class);
        SettableBeanProperty[] props = new SettableBeanProperty[0];
        
        boolean exceptionThrown = false;
        try {
            inst.createFromObjectWith(null, props, null);
        } catch (Exception e) {
            exceptionThrown = true;
        } catch (Throwable t) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testCreateFromStringFallbacksBoolean() throws Throwable {
        ConcreteValueInstantiator inst = new ConcreteValueInstantiator(Boolean.class);
        inst.setCanCreateFromBoolean(true);

        Object resTrue = inst._createFromStringFallbacks(null, "  true  ");
        assertEquals(Boolean.TRUE, resTrue);

        Object resFalse = inst._createFromStringFallbacks(null, "false");
        assertEquals(Boolean.FALSE, resFalse);
    }
}