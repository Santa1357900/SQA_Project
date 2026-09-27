package org.apache.commons.math3.genetics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;

import org.junit.Test;
import static org.junit.Assert.*;

public class ListPopulationTest {

    private static class DummyChromosome extends Chromosome {
        private final double fitness;

        public DummyChromosome(double fitness) {
            this.fitness = fitness;
        }

        public double fitness() {
            return fitness;
        }
    }

    private static class ConcreteListPopulation extends ListPopulation {
        public ConcreteListPopulation(int populationLimit) {
            super(populationLimit);
        }

        public ConcreteListPopulation(List<Chromosome> chromosomes, int populationLimit) {
            super(chromosomes, populationLimit);
        }
    }

    @Test
    public void testConstructorLimitOnly() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(10);
        assertEquals(10, pop.getPopulationLimit());
        assertEquals(0, pop.getPopulationSize());
        assertNotNull(pop.getChromosomes());
        assertTrue(pop.getChromosomes().isEmpty());
    }

    @Test
    public void testConstructorLimitZero() throws Throwable {
        try {
            new ConcreteListPopulation(0);
            fail("Expected NotPositiveException");
        } catch (NotPositiveException e) {
            // expected
        }
    }

    @Test
    public void testConstructorLimitNegative() throws Throwable {
        try {
            new ConcreteListPopulation(-5);
            fail("Expected NotPositiveException");
        } catch (NotPositiveException e) {
            // expected
        }
    }

    @Test
    public void testConstructorWithChromosomesNull() throws Throwable {
        try {
            new ConcreteListPopulation(null, 10);
            fail("Expected NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testConstructorWithChromosomesTooLarge() throws Throwable {
        List<Chromosome> list = new ArrayList<Chromosome>();
        list.add(new DummyChromosome(1.0));
        list.add(new DummyChromosome(2.0));

        try {
            new ConcreteListPopulation(list, 1);
            fail("Expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException e) {
            // expected
        }
    }

    @Test
    public void testConstructorWithChromosomesValid() throws Throwable {
        List<Chromosome> list = new ArrayList<Chromosome>();
        list.add(new DummyChromosome(1.0));

        ListPopulation pop = new ConcreteListPopulation(list, 5);
        assertEquals(5, pop.getPopulationLimit());
        assertEquals(1, pop.getPopulationSize());
    }

    @Test
    public void testSetChromosomesNull() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(10);
        try {
            pop.setChromosomes(null);
            fail("Expected NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetChromosomesTooLarge() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(2);
        List<Chromosome> list = new ArrayList<Chromosome>();
        list.add(new DummyChromosome(1.0));
        list.add(new DummyChromosome(2.0));
        list.add(new DummyChromosome(3.0));

        try {
            pop.setChromosomes(list);
            fail("Expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException e) {
            // expected
        }
    }

    @Test
    public void testSetChromosomesValid() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        List<Chromosome> list = new ArrayList<Chromosome>();
        list.add(new DummyChromosome(1.0));
        list.add(new DummyChromosome(2.0));

        pop.setChromosomes(list);
        assertEquals(2, pop.getPopulationSize());
    }

    @Test
    public void testAddChromosomesTooLarge() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(3);
        pop.addChromosome(new DummyChromosome(1.0));

        List<Chromosome> list = new ArrayList<Chromosome>();
        list.add(new DummyChromosome(2.0));
        list.add(new DummyChromosome(3.0));
        list.add(new DummyChromosome(4.0));

        try {
            pop.addChromosomes(list);
            fail("Expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException e) {
            // expected
        }
    }

    @Test
    public void testAddChromosomesValid() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        List<Chromosome> list = new ArrayList<Chromosome>();
        list.add(new DummyChromosome(1.0));
        list.add(new DummyChromosome(2.0));

        pop.addChromosomes(list);
        assertEquals(2, pop.getPopulationSize());
    }

    @Test
    public void testAddChromosomeTooLarge() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(1);
        pop.addChromosome(new DummyChromosome(1.0));

        try {
            pop.addChromosome(new DummyChromosome(2.0));
            fail("Expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException e) {
            // expected
        }
    }

    @Test
    public void testAddChromosomeValid() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(2);
        pop.addChromosome(new DummyChromosome(1.0));
        assertEquals(1, pop.getPopulationSize());
    }

    @Test
    public void testGetFittestChromosome() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        DummyChromosome c1 = new DummyChromosome(1.0);
        DummyChromosome c2 = new DummyChromosome(5.0);
        DummyChromosome c3 = new DummyChromosome(3.0);

        pop.addChromosome(c1);
        pop.addChromosome(c2);
        pop.addChromosome(c3);

        Chromosome fittest = pop.getFittestChromosome();
        assertEquals(c2, fittest);
    }

    @Test
    public void testSetPopulationLimitNotPositive() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        try {
            pop.setPopulationLimit(0);
            fail("Expected NotPositiveException");
        } catch (NotPositiveException e) {
            // expected
        }
    }

    @Test
    public void testSetPopulationLimitTooSmall() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        pop.addChromosome(new DummyChromosome(1.0));
        pop.addChromosome(new DummyChromosome(2.0));

        try {
            pop.setPopulationLimit(1);
            fail("Expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException e) {
            // expected
        }
    }

    @Test
    public void testSetPopulationLimitValid() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        pop.setPopulationLimit(10);
        assertEquals(10, pop.getPopulationLimit());
    }

    @Test
    public void testToString() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        pop.addChromosome(new DummyChromosome(1.0));
        String str = pop.toString();
        assertNotNull(str);
        assertTrue(str.length() > 0);
    }

    @Test
    public void testIterator() throws Throwable {
        ListPopulation pop = new ConcreteListPopulation(5);
        pop.addChromosome(new DummyChromosome(1.0));

        Iterator<Chromosome> it = pop.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertNotNull(it.next());
    }

    @Test
    public void testGetChromosomeList() throws Throwable {
        ConcreteListPopulation pop = new ConcreteListPopulation(5);
        pop.addChromosome(new DummyChromosome(1.0));
        assertNotNull(pop.getChromosomeList());
        assertEquals(1, pop.getChromosomeList().size());
    }
}