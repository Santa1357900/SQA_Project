ว่าคลาสมี Constructor อะไรบ้าง
   - ❌ ห้ามส่ง `null` ให้ parameter ที่เป็น primitive type (`int`, `double`, `boolean`, `char`, `long`) เด็ดขาด เพราะ javac จะฟ้อง type incompatible
   - ❌ ห้ามเรียกใช้ `private` method / constructor หรือ private field หรือ private inner class (เช่น `Assign` ใน `RemoveUnusedVars`) โดยตรง
   - ❌ ห้ามใช้ `assertEquals(null, val)` ให้ใช้ `assertNull(val)` แทนเสมอ
   - ❌ ห้ามใช้ `assertEquals(Double.NaN, val)` เพราะ ambiguous ใน JUnit 4 ให้ใช้ `assertEquals(Double.valueOf(Double.NaN), val)` หรือ `assertTrue(Double.isNaN(val))`
5. Full Imports Required:
   - ต้อง Import ทุกคลาสที่เรียกใช้อย่างครบถ้วน ไม่ละเลย Utility หรือ Annotation package (เช่น `com.fasterxml.jackson.databind.util.ClassUtil`, `com.fasterxml.jackson.annotation.*`, `org.jsoup.parser.*`, `org.jsoup.nodes.*`)

[TEST DESIGN & FAULT DETECTION DIRECTIVES (เพิ่มโอกาส REVEALING และลด INCONCLUSIVE)]
1. Oracle Precision (ลด Inconclusive จากการ Assert เปราะบาง):
   - ❌ อย่า Assert ข้อความ Exception หรือ toString() แบบ Exact Match (เพราะข้อความอาจเปลี่ยนตามเวอร์ชัน) ให้เน้น Assert ชนิดของ Exception หรือใช้ `assertTrue(e.getMessage().contains(...))` แทน
   - สำหรับกรณีทดสอบที่คาดว่าจะเกิด Exception ให้ใช้โครงสร้าง `try { method(); fail("Should throw exception"); } catch (SpecificException e) { /* Expected */ }` หรือ `@Test(expected = SpecificException.class)`
2. Bug-Hunting Edge Cases (กระตุ้นจุดบกพร่องที่ซ่อนอยู่):
   - ค่าขอบเขตวิกฤต: ค่าศูนย์ (0), ค่าลบ (-1), Maximum/Minimum values, Integer overflow/underflow
   - ปัญหา Null Safety: ส่ง `null` เข้าไปยัง Public method ที่รับ Object เพื่อดูว่ามี NullPointerException โดยไม่ได้จัดการหรือไม่
   - กรณี Collection/Array: ทดสอบ Empty, Single element, Duplicate elements, Null elements, Unmodifiable collection
   - กรณี String: Empty string `""`, Whitespace only `"   "`, String containing delimiters, Escape sequences, Special Unicode characters
   - Boundary & Loop conditions: จุดเปลี่ยนเงื่อนไข `<` vs `<=`, Off-by-one errors, Empty input streams

[TARGET SOURCE CODE]
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.math.distribution;

import java.io.Serializable;

import org.apache.commons.math.MathException;
import org.apache.commons.math.exception.NotStrictlyPositiveException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.apache.commons.math.exception.util.LocalizedFormats;
import org.apache.commons.math.special.Erf;
import org.apache.commons.math.util.FastMath;

/**
 * Default implementation of
 * {@link org.apache.commons.math.distribution.NormalDistribution}.
 *
 * @version $Revision$ $Date$
 */
public class NormalDistributionImpl extends AbstractContinuousDistribution
        implements NormalDistribution, Serializable {
    /**
     * Default inverse cumulative probability accuracy.
     * @since 2.1
     */
    public static final double DEFAULT_INVERSE_ABSOLUTE_ACCURACY = 1e-9;
    /** Serializable version identifier. */
    private static final long serialVersionUID = 8589540077390120676L;
    /** &sqrt;(2 &pi;) */
    private static final double SQRT2PI = FastMath.sqrt(2 * FastMath.PI);
    /** Mean of this distribution. */
    private final double mean;
    /** Standard deviation of this distribution. */
    private final double standardDeviation;
    /** Inverse cumulative probability accuracy. */
    private final double solverAbsoluteAccuracy;

    /**
     * Create a normal distribution using the given mean and standard deviation.
     *
     * @param mean Mean for this distribution.
     * @param sd Standard deviation for this distribution.
     */
    public NormalDistributionImpl(double mean, double sd){
        this(mean, sd, DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
    }

    /**
     * Create a normal distribution using the given mean, standard deviation and
     * inverse cumulative distribution accuracy.
     *
     * @param mean Mean for this distribution.
     * @param sd Standard deviation for this distribution.
     * @param inverseCumAccuracy Inverse cumulative probability accuracy.
     * @throws NotStrictlyPositiveException if {@code sd <= 0}.
     * @since 2.1
     */
    public NormalDistributionImpl(double mean, double sd, double inverseCumAccuracy) {
        if (sd <= 0) {
            throw new NotStrictlyPositiveException(LocalizedFormats.STANDARD_DEVIATION, sd);
        }

        this.mean = mean;
        standardDeviation = sd;
        solverAbsoluteAccuracy = inverseCumAccuracy;
    }

    /**
     * Create a normal distribution with mean equal to zero and standard
     * deviation equal to one.
     */
    public NormalDistributionImpl(){
        this(0, 1);
    }

    /**
     * {@inheritDoc}
     */
    public double getMean() {
        return mean;
    }

    /**
     * {@inheritDoc}
     */
    public double getStandardDeviation() {
        return standardDeviation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double density(double x) {
        final double x0 = x - mean;
        final double x1 = x0 / standardDeviation;
        return FastMath.exp(-0.5 * x1 * x1) / (standardDeviation * SQRT2PI);
    }

    /**
     * For this distribution, {@code X}, this method returns {@code P(X < x)}.
     * If {@code x}is more than 40 standard deviations from the mean, 0 or 1 is returned,
     * as in these cases the actual value is within {@code Double.MIN_VALUE} of 0 or 1.
     *
     * @param x Value at which the CDF is evaluated.
     * @return CDF evaluated at {@code x}.
     * @throws MathException if the algorithm fails to converge
     */
    public double cumulativeProbability(double x) throws MathException {
        final double dev = x - mean;
        try {
        return 0.5 * (1.0 + Erf.erf((dev) /
                    (standardDeviation * FastMath.sqrt(2.0))));
        } catch (MaxIterationsExceededException ex) {
            if (x < (mean - 20 * standardDeviation)) { // JDK 1.5 blows at 38
                return 0;
            } else if (x > (mean + 20 * standardDeviation)) {
                return 1;
            } else {
                throw ex;
            }
        }
    }

    /**
     * Return the absolute accuracy setting of the solver used to estimate
     * inverse cumulative probabilities.
     *
     * @return the solver absolute accuracy.
     * @since 2.1
     */
    @Override
    protected double getSolverAbsoluteAccuracy() {
        return solverAbsoluteAccuracy;
    }

    /**
     * For this distribution, X, this method returns the critical point
     * {@code x}, such that {@code P(X < x) = p}.
     * It will return {@code Double.NEGATIVE_INFINITY} when p = 0 and
     * {@code Double.POSITIVE_INFINITY} for p = 1.
     *
     * @param p Desired probability.
     * @return {@code x}, such that {@code P(X < x) = p}.
     * @throws MathException if the inverse cumulative probability cannot be
     * computed due to convergence or other numerical errors.
     * @throws org.apache.commons.math.exception.OutOfRangeException if
     * {@code p} is not a valid probability.
     */
    @Override
    public double inverseCumulativeProbability(final double p)
    throws MathException {
        if (p == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (p == 1) {
            return Double.POSITIVE_INFINITY;
        }
        return super.inverseCumulativeProbability(p);
    }

    /**
     * Generate a random value sampled from this distribution.
     *
     * @return a random value.
     * @since 2.2
     * @throws MathException if an error occurs generating the random value.
     */
    @Override
    public double sample() throws MathException {
        return randomData.nextGaussian(mean, standardDeviation);
    }

    /**
     * Access the domain value lower bound, based on {@code p}, used to
     * bracket a CDF root.  This method is used by
     * {@link #inverseCumulativeProbability(double)} to find critical values.
     *
     * @param p Desired probability for the critical value.
     * @return the domain value lower bound, i.e. {@code P(X < 'lower bound') < p}.
     */
    @Override
    protected double getDomainLowerBound(double p) {
        double ret;

        if (p < 0.5) {
            ret = -Double.MAX_VALUE;
        } else {
            ret = mean;
        }

        return ret;
    }

    /**
     * Access the domain value upper bound, based on {@code p}, used to
     * bracket a CDF root.  This method is used by
     * {@link #inverseCumulativeProbability(double)} to find critical values.
     *
     * @param p Desired probability for the critical value.
     * @return the domain value upper bound, i.e. {@code P(X < 'upper bound') > p}.
     */
    @Override
    protected double getDomainUpperBound(double p) {
        double ret;

        if (p < 0.5) {
            ret = mean;
        } else {
            ret = Double.MAX_VALUE;
        }

        return ret;
    }

    /**
     * Access the initial domain value, based on {@code p}, used to
     * bracket a CDF root.  This method is used by
     * {@link #inverseCumulativeProbability(double)} to find critical values.
     *
     * @param p Desired probability for the critical value.
     * @return the initial domain value.
     */
    @Override
    protected double getInitialDomain(double p) {
        double ret;

        if (p < 0.5) {
            ret = mean - standardDeviation;
        } else if (p > 0.5) {
            ret = mean + standardDeviation;
        } else {
            ret = mean;
        }

        return ret;
    }
}