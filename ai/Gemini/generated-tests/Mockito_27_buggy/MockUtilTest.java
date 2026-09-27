package org.mockito.internal.util;

import org.junit.Test;
import org.mockito.exceptions.misusing.NotAMockException;
import org.mockito.internal.creation.MockSettingsImpl;

import static org.junit.Assert.*;

public class MockUtilTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        MockUtil mockUtil = new MockUtil();
        assertNotNull(mockUtil);
    }

    @Test
    public void testConstructorWithCreationValidator() throws Throwable {
        MockCreationValidator validator = new MockCreationValidator();
        MockUtil mockUtil = new MockUtil(validator);
        assertNotNull(mockUtil);
    }

    @Test
    public void testIsMockWithNull() throws Throwable {
        MockUtil mockUtil = new MockUtil();
        assertFalse(mockUtil.isMock(null));
    }

    @Test
    public void testIsMockWithNonMockObject() throws Throwable {
        MockUtil mockUtil = new MockUtil();
        Object nonMock = new Object();
        assertFalse(mockUtil.isMock(nonMock));
    }

    @Test
    public void testGetMockHandlerWithNull() throws Throwable {
        MockUtil mockUtil = new MockUtil();
        try {
            mockUtil.getMockHandler(null);
            fail("Should have thrown NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("null"));
        }
    }

    @Test
    public void testGetMockHandlerWithNonMockObject() throws Throwable {
        MockUtil mockUtil = new MockUtil();
        Object nonMock = "Not a mock string";
        try {
            mockUtil.getMockHandler(nonMock);
            fail("Should have thrown NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("Not a mock string"));
        }
    }

    @Test
    public void testCreateMockValidationFailure() throws Throwable {
        MockUtil mockUtil = new MockUtil();
        MockSettingsImpl settings = new MockSettingsImpl();
        try {
            mockUtil.createMock(String.class, settings);
            fail("Should have thrown exception due to final class validation");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }
}