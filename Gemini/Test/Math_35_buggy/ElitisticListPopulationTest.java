package org.apache.commons.math3.genetics;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.exception.OutOfRangeException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ElitisticListPopulationTest {

    private static class DummyChromosome extends Chromosome {
        private final double fitness;

        public DummyChromosome(double fitness) {
            this.fitness = fitness;
        }

        public double fitness() {
            return this.fitness;
        }
    }

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        List<Chromosome> chromosomes = new ArrayList<Chromosome>();
        chromosomes.add(new DummyChromosome(1.0));
        chromosomes.add(new DummyChromosome(2.0));

        ElitisticListPopulation pop1 = new ElitisticListPopulation(chromosomes, 10, 0.75);
        assertEquals(10, pop1.getPopulationLimit());
        assertEquals(0.75, pop1.getElitismRate(), 1e-6);
        assertEquals(2, pop1.getChromosomes().size());

        ElitisticListPopulation pop2 = new ElitisticListPopulation(5, 0.5);
        assertEquals(5, pop2.getPopulationLimit());
        assertEquals(0.5, pop2.getElitismRate(), 1e-6);
        assertEquals(0, pop2.getChromosomes().size());
    }

    @Test
    public void testSetElitismRateValid() throws Throwable {
        ElitisticListPopulation pop = new ElitisticListPopulation(10, 0.5);
        pop.setElitismRate(0.0);
        assertEquals(0.0, pop.getElitismRate(), 1e-6);

        pop.setElitismRate(1.0);
        assertEquals(1.0, pop.getElitismRate(), 1e-6);

        pop.setElitismRate(0.25);
        assertEquals(0.25, pop.getElitismRate(), 1e-6);
    }

    @Test
    public void testSetElitismRateOutOfRangeLow() throws Throwable {
        ElitisticListPopulation pop = new ElitisticListPopulation(10, 0.5);
        try {
            pop.setElitismRate(-0.1);
            fail("Expected OutOfRangeException");
        } catch (OutOfRangeException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testSetElitismRateOutOfRangeHigh() throws Throwable {
        ElitisticListPopulation pop = new ElitisticListPopulation(10, 0.5);
        try {
            pop.setElitismRate(1.1);
            fail("Expected OutOfRangeException");
        } catch (OutOfRangeException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testNextGeneration() throws Throwable {
        List<Chromosome> chromosomes = new ArrayList<Chromosome>();
        chromosomes.add(new DummyChromosome(10.0));
        chromosomes.add(new DummyChromosome(5.0));
        chromosomes.add(new DummyChromosome(20.0));

        // Elitism rate 0.5 -> 1.0 - 0.5 = 0.5 of oldChromosomes size will be boundIndex.
        // wait, let's trace:
        // oldChromosomes size = 3
        // sorted by natural order (ascending fitness): 5.0, 10.0, 20.0
        // boundIndex = ceil((1.0 - 0.5) * 3) = ceil(1.5) = 2.
        // loop from i = 2 to < 3, so index 2 is added (fitness 20.0).
        // Let's test with elitism rate = 0.0 (boundIndex = ceil(1.0 * 3) = 3 -> loop empty)
        // Let's test with elitism rate = 1.0 (boundIndex = ceil(0.0 * 3) = 0 -> all added)
        
        ElitisticListPopulation pop = new ElitisticListPopulation(chromosomes, 10, 0.0);
        Population nextGen = pop.nextGeneration();
        assertNotNull(nextGen);
        assertEquals(10, nextGen.getPopulationLimit());
        // elitism rate 0 means 0% copied? Wait, elitismRate means percentage of best chromosomes directly copied.
        // According to code:
        // int boundIndex = (int) FastMath.ceil((1.0 - this.getElitismRate()) * oldChromosomes.size());
        // for (int i=boundIndex; i<oldChromosomes.size(); i++) { nextGeneration.addChromosome(oldChromosomes.get(i)); }
        // If elitismRate = 1.0, (1.0 - 1.0) = 0, boundIndex = 0. All elements copied (best ones).
        // If elitismRate = 0.0, (1.0 - 0.0) = 1, boundIndex = size. 0 elements copied.

        ElitisticListPopulation popFullElitism = new ElitisticListPopulation(chromosomes, 10, 1.0);
        Population nextGenFull = popFullElitism.nextGeneration();
        assertNotNull(nextGenFull);
        assertEquals(3, nextGenFull.getChromosomes().size());
    }

    @Test
    public void testNextGenerationWithZeroElitism() throws Throwable {
        List<Chromosome> chromosomes = new ArrayList<Chromosome>();
        chromosomes.add(new DummyChromosome(10.0));
        chromosomes.add(new DummyChromosome(5.0));

        ElitisticListPopulation pop = new ElitisticListPopulation(chromosomes, 10, 0.0);
        Population nextGen = pop.nextGeneration();
        assertNotNull(nextGen);
        assertEquals(0, nextGen.getChromosomes().size());
    }
}