package com.google.javascript.rhino.jstype;

import com.google.common.base.Predicate;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class NamedTypeTest {

    private JSTypeRegistry registry;

    @Before
    public void setUp() throws Throwable {
        ErrorReporter errorReporter = new SimpleErrorReporter();
        registry = new JSTypeRegistry(errorReporter);
    }

    @Test
    public void testConstructorAndBasicGetters() throws Throwable {
        NamedType namedType = new NamedType(registry, "my.TestType", "testSource.js", 10, 5);

        assertNotNull(namedType);
        assertEquals("my.TestType", namedType.getReferenceName());
        assertEquals("my.TestType", namedType.toStringHelper(false));
        assertTrue(namedType.hasReferenceName());
        assertTrue(namedType.isNamedType());
        assertTrue(namedType.isNominalType());
        assertEquals("my.TestType".hashCode(), namedType.hashCode());
    }

    @Test
    public void testDefinePropertyUnresolvedAndResolved() throws Throwable {
        NamedType namedType = new NamedType(registry, "UnresolvedType", "source.js", 1, 1);
        JSType nativeUnknown = registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
        
        // Define property when unresolved (should store in propertyContinuations)
        boolean definedUnresolved = namedType.defineProperty("prop1", nativeUnknown, false, null);
        assertTrue(definedUnresolved);

        // Resolve via registry by registering the type
        ObjectType objType = registry.createObjectType("UnresolvedType", null);
        
        ErrorReporter reporter = new SimpleErrorReporter();
        StaticScope<JSType> scope = new StaticScope<JSType>() {
            public JSTypeRegistry getRegistry() { return registry; }
            public StaticSlot<JSType> getSlot(String name) { return null; }
            public StaticScope<JSType> getParentScope() { return null; }
            public int getDepth() { return 0; }
        };

        JSType resolved = namedType.resolveInternal(reporter, scope);
        assertNotNull(resolved);
    }

    @Test
    public void testResolveViaRegistrySuccess() throws Throwable {
        NamedType namedType = new NamedType(registry, "RegisteredType", "source.js", 2, 3);
        ObjectType objType = registry.createObjectType("RegisteredType", null);

        ErrorReporter reporter = new SimpleErrorReporter();
        StaticScope<JSType> scope = new StaticScope<JSType>() {
            public JSTypeRegistry getRegistry() { return registry; }
            public StaticSlot<JSType> getSlot(String name) { return null; }
            public StaticScope<JSType> getParentScope() { return null; }
            public int getDepth() { return 0; }
        };

        JSType resolved = namedType.resolveInternal(reporter, scope);
        assertNotNull(resolved);
        assertEquals(objType, namedType.getReferencedType());
    }

    @Test
    public void testResolveViaPropertiesNotFound() throws Throwable {
        NamedType namedType = new NamedType(registry, "NonExistent.Property", "source.js", 5, 5);

        ErrorReporter reporter = new SimpleErrorReporter();
        StaticScope<JSType> scope = new StaticScope<JSType>() {
            public JSTypeRegistry getRegistry() { return registry; }
            public StaticSlot<JSType> getSlot(String name) { return null; }
            public StaticScope<JSType> getParentScope() { return null; }
            public int getDepth() { return 0; }
        };

        registry.setLastGeneration(true);
        JSType resolved = namedType.resolveInternal(reporter, scope);
        assertNotNull(resolved);
    }

    @Test
    public void testSetValidatorUnresolvedAndResolved() throws Throwable {
        final NamedType namedType = new NamedType(registry, "ValidationType", "source.js", 1, 1);
        
        Predicate<JSType> validator = new Predicate<JSType>() {
            public boolean apply(JSType input) {
                return input != null;
            }
        };

        boolean result = namedType.setValidator(validator);
        assertTrue(result);

        ObjectType objType = registry.createObjectType("ValidationType", null);
        ErrorReporter reporter = new SimpleErrorReporter();
        StaticScope<JSType> scope = new StaticScope<JSType>() {
            public JSTypeRegistry getRegistry() { return registry; }
            public StaticSlot<JSType> getSlot(String name) { return null; }
            public StaticScope<JSType> getParentScope() { return null; }
            public int getDepth() { return 0; }
        };

        namedType.resolveInternal(reporter, scope);

        boolean resultResolved = namedType.setValidator(validator);
        assertTrue(resultResolved);
    }

    @Test
    public void testGetTypedefTypeWithNullSlotType() throws Throwable {
        NamedType namedType = new NamedType(registry, "TypedefType", "source.js", 1, 1);
        ErrorReporter reporter = new SimpleErrorReporter();
        
        StaticSlot<JSType> slot = new StaticSlot<JSType>() {
            public String getName() { return "slotName"; }
            public JSType getType() { return null; }
            public boolean isTypeInferred() { return false; }
        };

        registry.setLastGeneration(false);
        JSType type = namedType.getTypedefType(reporter, slot, "slotName");
        assertNull(type);
    }
}