package org.jfree.chart.imagemap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StandardToolTipTagFragmentGeneratorTest {

    @Test
    public void testConstructor() throws Throwable {
        StandardToolTipTagFragmentGenerator generator = new StandardToolTipTagFragmentGenerator();
        assertNotNull(generator);
    }

    @Test
    public void testGenerateToolTipFragmentNormal() throws Throwable {
        StandardToolTipTagFragmentGenerator generator = new StandardToolTipTagFragmentGenerator();
        String result = generator.generateToolTipFragment("Tooltip Text");
        assertEquals(" title=\"Tooltip Text\" alt=\"\"", result);
    }

    @Test
    public void testGenerateToolTipFragmentEmpty() throws Throwable {
        StandardToolTipTagFragmentGenerator generator = new StandardToolTipTagFragmentGenerator();
        String result = generator.generateToolTipFragment("");
        assertEquals(" title=\"\" alt=\"\"", result);
    }

    @Test
    public void testGenerateToolTipFragmentNull() throws Throwable {
        StandardToolTipTagFragmentGenerator generator = new StandardToolTipTagFragmentGenerator();
        try {
            String result = generator.generateToolTipFragment(null);
            assertEquals(" title=\"null\" alt=\"\"", result);
        } catch (NullPointerException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testGenerateToolTipFragmentSpecialCharacters() throws Throwable {
        StandardToolTipTagFragmentGenerator generator = new StandardToolTipTagFragmentGenerator();
        String result = generator.generateToolTipFragment("Test & \"quotes\" <tag>");
        assertEquals(" title=\"Test & \"quotes\" <tag>\" alt=\"\"", result);
    }
}