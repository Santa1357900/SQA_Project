package org.jsoup;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class UncheckedIOExceptionTest {

    @Test
    public void testUncheckedIOExceptionCreationAndGetCause() throws Throwable {
        IOException cause = new IOException("Test IO Exception");
        UncheckedIOException exception = new UncheckedIOException(cause);

        assertNotNull(exception);
        assertEquals(cause, exception.getCause());
        assertEquals("java.io.IOException: Test IO Exception", exception.getMessage());
    }

    @Test
    public void testIoExceptionMethod() throws Throwable {
        IOException cause = new IOException("Another IO Exception");
        UncheckedIOException exception = new UncheckedIOException(cause);

        IOException retrievedIoException = exception.ioException();
        assertNotNull(retrievedIoException);
        assertEquals(cause, retrievedIoException);
        assertEquals("Another IO Exception", retrievedIoException.getMessage());
    }

    @Test
    public void testWithNullCause() throws Throwable {
        UncheckedIOException exception = new UncheckedIOException(null);

        assertNotNull(exception);
        assertNull(exception.getCause());
        
        try {
            exception.ioException();
            fail("Expected NullPointerException when cause is null");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testSubclassingBehavior() throws Throwable {
        IOException cause = new IOException();
        UncheckedIOException exception = new UncheckedIOException(cause);

        boolean isRuntimeException = exception instanceof RuntimeException;
        assertTrue(isRuntimeException);

        Exception generalException = exception;
        assertNotNull(generalException);
    }
}