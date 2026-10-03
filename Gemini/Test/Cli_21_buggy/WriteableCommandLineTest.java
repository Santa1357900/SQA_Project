package org.apache.commons.cli2;

import junit.framework.TestCase;
import java.util.List;
import java.util.ArrayList;

public class WriteableCommandLineTest extends TestCase {

    public void testInterfaceExists() throws Throwable {
        Class clazz = WriteableCommandLine.class;
        assertNotNull(clazz);
        assertTrue(WriteableCommandLine.class.isAssignableFrom(WriteableCommandLine.class));
    }
}