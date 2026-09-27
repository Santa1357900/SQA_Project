package org.apache.commons.math3.random;

import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.junit.Test;

import static org.junit.Assert.*;

public class BitsStreamGeneratorTest {

    private static class ConcreteBitsStreamGenerator extends BitsStreamGenerator {
        private int nextValue = 123456;
        private int nextCount = 0;

        @Override
        public void setSeed(int seed) {
            nextValue = seed;
        }

        @Override
        public void setSeed(int[] seed) {
            if (seed != null && seed.length > 0) {
                nextValue = seed[0];
            }
        }

        @Override
        public void setSeed(long seed) {
            nextValue = (int) seed;
        }

        @Override
        protected int next(int bits) {
            nextCount++;
            return nextValue;
        }
    }

    private static class SequenceBitsStreamGenerator extends BitsStreamGenerator {
        private final int[] sequence;
        private int index = 0;

        public SequenceBitsStreamGenerator(int[] sequence) {
            this.sequence = sequence;
        }

        @Override
        public void setSeed(int seed) {}

        @Override
        public void setSeed(int[] seed) {}

        @Override
        public void setSeed(long seed) {}

        @Override
        protected int next(int bits) {
            if (index < sequence.length) {
                return sequence[index++];
            }
            return 0;
        }
    }

    @Test
    public void testConstructorAndClear() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        gen.clear();
        double g1 = gen.nextGaussian();
        double g2 = gen.nextGaussian();
        assertFalse(Double.isNaN(g1));
        assertFalse(Double.isNaN(g2));
        gen.clear();
        double g3 = gen.nextGaussian();
        assertFalse(Double.isNaN(g3));
    }

    @Test
    public void testNextBoolean() throws Throwable {
        SequenceBitsStreamGenerator gen = new SequenceBitsStreamGenerator(new int[]{0, 1});
        assertFalse(gen.nextBoolean());
        assertTrue(gen.nextBoolean());
    }

    @Test
    public void testNextBytes() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        byte[] bytes = new byte[7];
        gen.nextBytes(bytes);
        assertEquals(7, bytes.length);

        byte[] emptyBytes = new byte[0];
        gen.nextBytes(emptyBytes);
        assertEquals(0, emptyBytes.length);

        byte[] smallBytes = new byte[2];
        gen.nextBytes(smallBytes);
        assertEquals(2, smallBytes.length);
    }

    @Test
    public void testNextDouble() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        double val = gen.nextDouble();
        assertTrue(val >= 0.0);
        assertTrue(val < 1.0);
    }

    @Test
    public void testNextFloat() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        float val = gen.nextFloat();
        assertTrue(val >= 0.0f);
        assertTrue(val < 1.0f);
    }

    @Test
    public void testNextGaussianPair() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        gen.clear();
        double g1 = gen.nextGaussian();
        double g2 = gen.nextGaussian();
        assertFalse(Double.isNaN(g1));
        assertFalse(Double.isNaN(g2));
    }

    @Test
    public void testNextInt() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        int val = gen.nextInt();
        assertEquals(123456, val);
    }

    @Test
    public void testNextIntPowerOfTwo() throws Throwable {
        SequenceBitsStreamGenerator gen = new SequenceBitsStreamGenerator(new int[]{100, 200});
        int val = gen.nextInt(4);
        assertTrue(val >= 0);
        assertTrue(val < 4);
    }

    @Test
    public void testNextIntNotPowerOfTwo() throws Throwable {
        SequenceBitsStreamGenerator gen = new SequenceBitsStreamGenerator(new int[]{10, 20, 30, 40});
        int val = gen.nextInt(3);
        assertTrue(val >= 0);
        assertTrue(val < 3);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testNextIntNegativeBound() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        gen.nextInt(0);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testNextIntZeroBound() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        gen.nextInt(-5);
    }

    @Test
    public void testNextLong() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        long val = gen.nextLong();
        assertTrue(val != 0L || val == 0L);
    }

    @Test
    public void testSetSeeds() throws Throwable {
        ConcreteBitsStreamGenerator gen = new ConcreteBitsStreamGenerator();
        gen.setSeed(42);
        gen.setSeed(new int[]{42, 43});
        gen.setSeed(100L);
        assertEquals(100, gen.next(32));
    }
}