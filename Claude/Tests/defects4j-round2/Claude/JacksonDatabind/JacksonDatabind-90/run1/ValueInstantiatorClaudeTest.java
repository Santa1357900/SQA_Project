package com.fasterxml.jackson.databind.deser;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;

public class ValueInstantiatorClaudeTest
{
    private ValueInstantiator newDefaultInstantiator() {
        return new ValueInstantiator() { };
    }

    // getValueClass() default: no override -> must be Object.class per javadoc
    @Test
    public void testGetValueClass_default_returnsObjectClass() throws Throwable {
        ValueInstantiator vi = newDefaultInstantiator();
        assertEquals(Object.class, vi.getValueClass());
    }

    // getValueTypeDesc(): cls != null branch, uses default getValueClass()
    @Test
    public void testGetValueTypeDesc_default_returnsObjectClassName() throws Throwable {
        ValueInstantiator vi = newDefaultInstantiator();
        assertEquals("java.lang.Object", vi.getValueTypeDesc());
    }

    // getValueTypeDesc(): cls == null branch -> "UNKNOWN"
    @Test
    public void testGetValueTypeDesc_overriddenNullValueClass_returnsUnknown() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public Class<?> getValueClass() { return null; }
        };
        assertEquals("UNKNOWN", vi.getValueTypeDesc());
    }

    // getValueTypeDesc(): cls != null branch with overridden class
    @Test
    public void testGetValueTypeDesc_overriddenValueClass_returnsClassName() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public Class<?> getValueClass() { return String.class; }
        };
        assertEquals("java.lang.String", vi.getValueTypeDesc());
    }

    // canInstantiate(): all sub-checks false -> false
    @Test
    public void testCanInstantiate_allDefaultsFalse_returnsFalse() throws Throwable {
        ValueInstantiator vi = newDefaultInstantiator();
        assertFalse(vi.canInstantiate());
    }

    // canInstantiate(): canCreateFromString() true branch
    @Test
    public void testCanInstantiate_canCreateFromString_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromString() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canInstantiate(): canCreateFromInt() true branch
    @Test
    public void testCanInstantiate_canCreateFromInt_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromInt() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canInstantiate(): canCreateFromLong() true branch
    @Test
    public void testCanInstantiate_canCreateFromLong_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromLong() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canInstantiate(): canCreateFromDouble() true branch
    @Test
    public void testCanInstantiate_canCreateFromDouble_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromDouble() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canInstantiate(): canCreateFromBoolean() true branch
    @Test
    public void testCanInstantiate_canCreateFromBoolean_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromBoolean() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canInstantiate(): canCreateUsingDefault() true branch
    @Test
    public void testCanInstantiate_canCreateUsingDefault_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateUsingDefault() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canInstantiate(): canCreateUsingDelegate() true branch
    @Test
    public void testCanInstantiate_canCreateUsingDelegate_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateUsingDelegate() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canInstantiate(): canCreateFromObjectWith() true branch
    @Test
    public void testCanInstantiate_canCreateFromObjectWith_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromObjectWith() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // Bug-catching: javadoc says canInstantiate() is true "if any of canCreateXxx returns true",
    // which must include canCreateUsingArrayDelegate() (added @since 2.7).
    @Test
    public void testCanInstantiate_canCreateUsingArrayDelegate_returnsTrue() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateUsingArrayDelegate() { return true; }
        };
        assertTrue(vi.canInstantiate());
    }

    // canCreateFromString() default -> false
    @Test
    public void testCanCreateFromString_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateFromString());
    }

    // canCreateFromInt() default -> false
    @Test
    public void testCanCreateFromInt_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateFromInt());
    }

    // canCreateFromLong() default -> false
    @Test
    public void testCanCreateFromLong_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateFromLong());
    }

    // canCreateFromDouble() default -> false
    @Test
    public void testCanCreateFromDouble_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateFromDouble());
    }

    // canCreateFromBoolean() default -> false
    @Test
    public void testCanCreateFromBoolean_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateFromBoolean());
    }

    // canCreateUsingDefault(): getDefaultCreator() null -> false
    @Test
    public void testCanCreateUsingDefault_nullCreator_returnsFalse() throws Throwable {
        ValueInstantiator vi = newDefaultInstantiator();
        assertNull(vi.getDefaultCreator());
        assertFalse(vi.canCreateUsingDefault());
    }

    // canCreateUsingDelegate() default -> false
    @Test
    public void testCanCreateUsingDelegate_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateUsingDelegate());
    }

    // canCreateUsingArrayDelegate() default -> false
    @Test
    public void testCanCreateUsingArrayDelegate_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateUsingArrayDelegate());
    }

    // canCreateFromObjectWith() default -> false
    @Test
    public void testCanCreateFromObjectWith_default_returnsFalse() throws Throwable {
        assertFalse(newDefaultInstantiator().canCreateFromObjectWith());
    }

    // getFromObjectArguments() default -> null, param unused so null config is safe
    @Test
    public void testGetFromObjectArguments_default_returnsNull() throws Throwable {
        ValueInstantiator vi = newDefaultInstantiator();
        DeserializationConfig config = null;
        assertNull(vi.getFromObjectArguments(config));
    }

    // getDelegateType() default -> null
    @Test
    public void testGetDelegateType_default_returnsNull() throws Throwable {
        ValueInstantiator vi = newDefaultInstantiator();
        DeserializationConfig config = null;
        assertNull(vi.getDelegateType(config));
    }

    // getArrayDelegateType() default -> null
    @Test
    public void testGetArrayDelegateType_default_returnsNull() throws Throwable {
        ValueInstantiator vi = newDefaultInstantiator();
        DeserializationConfig config = null;
        assertNull(vi.getArrayDelegateType(config));
    }

    // getDefaultCreator() default -> null
    @Test
    public void testGetDefaultCreator_default_returnsNull() throws Throwable {
        assertNull(newDefaultInstantiator().getDefaultCreator());
    }

    // getDelegateCreator() default -> null
    @Test
    public void testGetDelegateCreator_default_returnsNull() throws Throwable {
        assertNull(newDefaultInstantiator().getDelegateCreator());
    }

    // getArrayDelegateCreator() default -> null
    @Test
    public void testGetArrayDelegateCreator_default_returnsNull() throws Throwable {
        assertNull(newDefaultInstantiator().getArrayDelegateCreator());
    }

    // getWithArgsCreator() default -> null
    @Test
    public void testGetWithArgsCreator_default_returnsNull() throws Throwable {
        assertNull(newDefaultInstantiator().getWithArgsCreator());
    }

    // getIncompleteParameter() default -> null
    @Test
    public void testGetIncompleteParameter_default_returnsNull() throws Throwable {
        assertNull(newDefaultInstantiator().getIncompleteParameter());
    }

    // Base(Class<?>) constructor sets value type correctly for both accessors
    @Test
    public void testBaseConstructorClass_setsValueClassAndDesc() throws Throwable {
        ValueInstantiator.Base base = new ValueInstantiator.Base(String.class);
        assertEquals(String.class, base.getValueClass());
        assertEquals("java.lang.String", base.getValueTypeDesc());
    }

    // Base(Class<?>) constructor with a different type
    @Test
    public void testBaseConstructorClass_withIntegerType_setsValueClass() throws Throwable {
        ValueInstantiator.Base base = new ValueInstantiator.Base(Integer.class);
        assertEquals(Integer.class, base.getValueClass());
        assertEquals("java.lang.Integer", base.getValueTypeDesc());
    }

    // _createFromStringFallbacks via createFromString: canCreateFromBoolean() true, "true" branch
    @Test
    public void testCreateFromString_trueString_delegatesToCreateFromBooleanTrue() throws Throwable {
        final boolean[] called = new boolean[1];
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromBoolean() { return true; }
            @Override
            public Object createFromBoolean(DeserializationContext ctxt, boolean value) throws IOException {
                called[0] = value;
                return Boolean.valueOf(value);
            }
        };
        Object result = vi.createFromString(null, "true");
        assertEquals(Boolean.TRUE, result);
        assertTrue(called[0]);
    }

    // _createFromStringFallbacks via createFromString: canCreateFromBoolean() true, "false" branch
    @Test
    public void testCreateFromString_falseString_delegatesToCreateFromBooleanFalse() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromBoolean() { return true; }
            @Override
            public Object createFromBoolean(DeserializationContext ctxt, boolean value) throws IOException {
                return Boolean.valueOf(value);
            }
        };
        Object result = vi.createFromString(null, "false");
        assertEquals(Boolean.FALSE, result);
    }

    // _createFromStringFallbacks: value.trim() used before comparing to "true"/"false"
    @Test
    public void testCreateFromString_trueStringWithWhitespace_trimsAndDelegates() throws Throwable {
        ValueInstantiator vi = new ValueInstantiator() {
            @Override
            public boolean canCreateFromBoolean() { return true; }
            @Override
            public Object createFromBoolean(DeserializationContext ctxt, boolean value) throws IOException {
                return Boolean.valueOf(value);
            }
        };
        Object result = vi.createFromString(null, "  true  ");
        assertEquals(Boolean.TRUE, result);
    }
}
