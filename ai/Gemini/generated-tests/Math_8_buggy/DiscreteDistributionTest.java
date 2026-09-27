package org.apache.commons.math3.distribution;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.util.Pair;

public class DiscreteDistributionTest {

    @Test
    public void testConstructorAndGetSamples() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("a", 1.0));
        samples.add(new Pair<String, Double>("b", 3.0));

        DiscreteDistribution<String> dist = new DiscreteDistribution<String>(samples);
        List<Pair<String, Double>> resultSamples = dist.getSamples();

        Assert.assertEquals(2, resultSamples.size());
        Assert.assertEquals("a", resultSamples.get(0).getKey());
        Assert.assertEquals(0.25, resultSamples.get(0).getValue(), 1e-12);
        Assert.assertEquals("b", resultSamples.get(1).getKey());
        Assert.assertEquals(0.75, resultSamples.get(1).getValue(), 1e-12);
    }

    @Test
    public void testConstructorWithRng() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("x", 2.0));
        samples.add(new Pair<String, Double>("y", 2.0));

        Well19937c rng = new Well19937c(123L);
        DiscreteDistribution<String> dist = new DiscreteDistribution<String>(rng, samples);

        List<Pair<String, Double>> resultSamples = dist.getSamples();
        Assert.assertEquals(2, resultSamples.size());
        Assert.assertEquals(0.5, resultSamples.get(0).getValue(), 1e-12);
        Assert.assertEquals(0.5, resultSamples.get(1).getValue(), 1e-12);
    }

    @Test
    public void testNegativeProbability() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("a", -1.0));

        try {
            new DiscreteDistribution<String>(samples);
            Assert.fail("Expected NotPositiveException");
        } catch (NotPositiveException e) {
            // Expected
        }
    }

    @Test
    public void testZeroSumProbabilities() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("a", 0.0));
        samples.add(new Pair<String, Double>("b", 0.0));

        try {
            new DiscreteDistribution<String>(samples);
            Assert.fail("Expected MathArithmeticException");
        } catch (MathArithmeticException e) {
            // Expected
        }
    }

    @Test
    public void testProbabilityMethod() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("a", 1.0));
        samples.add(new Pair<String, Double>(null, 1.0));

        DiscreteDistribution<String> dist = new DiscreteDistribution<String>(samples);

        Assert.assertEquals(0.5, dist.probability("a"), 1e-12);
        Assert.assertEquals(0.0, dist.probability("nonexistent"), 1e-12);
        Assert.assertEquals(0.5, dist.probability(null), 1e-12);
    }

    @Test
    public void testSampleSingle() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("only", 1.0));

        DiscreteDistribution<String> dist = new DiscreteDistribution<String>(samples);
        String sampled = dist.sample();
        Assert.assertEquals("only", sampled);
    }

    @Test
    public void testSampleMultiple() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("a", 1.0));
        samples.add(new Pair<String, Double>("b", 1.0));

        DiscreteDistribution<String> dist = new DiscreteDistribution<String>(samples);
        String[] sampledArray = dist.sample(5);

        Assert.assertEquals(5, sampledArray.length);
        for (int i = 0; i < sampledArray.length; i++) {
            boolean matches = "a".equals(sampledArray[i]) || "b".equals(sampledArray[i]);
            Assert.assertTrue(matches);
        }
    }

    @Test
    public void testSampleInvalidSize() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("a", 1.0));

        DiscreteDistribution<String> dist = new DiscreteDistribution<String>(samples);

        try {
            dist.sample(0);
            Assert.fail("Expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException e) {
            // Expected
        }

        try {
            dist.sample(-5);
            Assert.fail("Expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException e) {
            // Expected
        }
    }

    @Test
    public void testReseedRandomGenerator() throws Throwable {
        List<Pair<String, Double>> samples = new ArrayList<Pair<String, Double>>();
        samples.add(new Pair<String, Double>("a", 1.0));
        samples.add(new Pair<String, Double>("b", 1.0));

        DiscreteDistribution<String> dist = new DiscreteDistribution<String>(samples);
        dist.reseedRandomGenerator(42L);
        String s1 = dist.sample();

        dist.reseedRandomGenerator(42L);
        String s2 = dist.sample();

        Assert.assertEquals(s1, s2);
    }
}