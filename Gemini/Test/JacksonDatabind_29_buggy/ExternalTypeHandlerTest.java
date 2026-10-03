package com.fasterxml.jackson.databind.deser.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.ArrayList;

public class ExternalTypeHandlerTest {

    @Test
    public void testBuilderAndConstructors() throws Throwable {
        ExternalTypeHandler.Builder builder = new ExternalTypeHandler.Builder();
        assertNotNull(builder);

        ExternalTypeHandler handler = builder.build();
        assertNotNull(handler);

        ExternalTypeHandler started = handler.start();
        assertNotNull(started);
        assertNotSame(handler, started);
    }
}