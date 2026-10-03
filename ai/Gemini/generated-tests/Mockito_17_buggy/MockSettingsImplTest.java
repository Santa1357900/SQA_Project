package org.mockito.internal.creation;

import org.junit.Test;
import org.mockito.MockSettings;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;

public class MockSettingsImplTest {

    @Test
    public void testSerializable() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockSettings returnedSettings = settings.serializable();
        assertSame(settings, returnedSettings);
        assertTrue(settings.isSerializable());
        
        Class<?>[] extraInterfaces = settings.getExtraInterfaces();
        assertNotNull(extraInterfaces);
        assertEquals(1, extraInterfaces.length);
        assertEquals(java.io.Serializable.class, extraInterfaces[0]);
    }

    @Test
    public void testExtraInterfacesValid() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockSettings returnedSettings = settings.extraInterfaces(Runnable.class, java.io.Serializable.class);
        assertSame(settings, returnedSettings);
        
        Class<?>[] extraInterfaces = settings.getExtraInterfaces();
        assertNotNull(extraInterfaces);
        assertEquals(2, extraInterfaces.length);
        assertEquals(Runnable.class, extraInterfaces[0]);
        assertEquals(java.io.Serializable.class, extraInterfaces[1]);
        assertTrue(settings.isSerializable());
    }

    @Test
    public void testExtraInterfacesNullArray() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        try {
            settings.extraInterfaces((Class<?>[]) null);
            fail("Expected Reporter to throw an exception for null interfaces");
        } catch (RuntimeException e) {
            // Expected exception reported by Mockito's Reporter
        }
    }

    @Test
    public void testExtraInterfacesEmptyArray() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        try {
            settings.extraInterfaces(new Class<?>[0]);
            fail("Expected Reporter to throw an exception for empty interfaces");
        } catch (RuntimeException e) {
            // Expected exception reported by Mockito's Reporter
        }
    }

    @Test
    public void testExtraInterfacesContainsNullElement() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        try {
            settings.extraInterfaces(Runnable.class, null);
            fail("Expected Reporter to throw an exception when interface is null");
        } catch (RuntimeException e) {
            // Expected exception reported by Mockito's Reporter
        }
    }

    @Test
    public void testExtraInterfacesContainsNonInterface() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        try {
            settings.extraInterfaces(String.class); // String is a class, not an interface
            fail("Expected Reporter to throw an exception when non-interface is passed");
        } catch (RuntimeException e) {
            // Expected exception reported by Mockito's Reporter
        }
    }

    @Test
    public void testName() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockSettings returnedSettings = settings.name("myMock");
        assertSame(settings, returnedSettings);
        
        settings.initiateMockName(String.class);
        assertNotNull(settings.getMockName());
        assertTrue(settings.getMockName().toString().contains("myMock"));
    }

    @Test
    public void testSpiedInstance() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        Object instance = new Object();
        MockSettings returnedSettings = settings.spiedInstance(instance);
        assertSame(settings, returnedSettings);
        assertSame(instance, settings.getSpiedInstance());
    }

    @Test
    public void testDefaultAnswer() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        Answer<Object> answer = new Answer<Object>() {
            public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
                return null;
            }
        };
        MockSettings returnedSettings = settings.defaultAnswer(answer);
        assertSame(settings, returnedSettings);
        assertSame(answer, settings.getDefaultAnswer());
    }

    @Test
    public void testIsSerializableFalseByDefault() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        assertFalse(settings.isSerializable());
        assertNull(settings.getExtraInterfaces());
        assertNull(settings.getSpiedInstance());
        assertNull(settings.getDefaultAnswer());
        assertNull(settings.getMockName());
    }

    @Test
    public void testInitiateMockNameWithoutCustomName() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        settings.initiateMockName(Integer.class);
        assertNotNull(settings.getMockName());
    }
}