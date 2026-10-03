package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnixStatTest {

    @Test
    public void testConstantsValues() throws Throwable {
        assertEquals(07777, UnixStat.PERM_MASK);
        assertEquals(0120000, UnixStat.LINK_FLAG);
        assertEquals(0100000, UnixStat.FILE_FLAG);
        assertEquals(040000, UnixStat.DIR_FLAG);
        assertEquals(0777, UnixStat.DEFAULT_LINK_PERM);
        assertEquals(0755, UnixStat.DEFAULT_DIR_PERM);
        assertEquals(0644, UnixStat.DEFAULT_FILE_PERM);
    }

    @Test
    public void testInterfaceInstantiationOrReference() throws Throwable {
        // UnixStat is an interface containing only constants. 
        // We can reference its constants through a concrete implementation or anonymously if needed,
        // but directly accessing them as static fields (as tested in testConstantsValues) is standard.
        UnixStat stat = new UnixStat() {
            // Anonymous class implementing UnixStat to ensure interface compatibility
        };
        assertNotNull(stat);
    }
}