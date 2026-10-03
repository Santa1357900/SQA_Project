package org.apache.commons.math.geometry;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math.exception.MathArithmeticException;

public class Vector3DClaudeTest {

    // Constructor(double,double,double): coordinates stored exactly as given
    @Test
    public void testConstructorXYZ_storesCoordinates() throws Throwable {
        Vector3D v = new Vector3D(1.5, -2.5, 3.5);
        assertEquals(1.5, v.getX(), 1.0e-12);
        assertEquals(-2.5, v.getY(), 1.0e-12);
        assertEquals(3.5, v.getZ(), 1.0e-12);
    }

    // Documented canonical constants have the expected coordinates
    @Test
    public void testConstants_haveExpectedCoordinates() throws Throwable {
        assertEquals(1.0, Vector3D.PLUS_I.getX(), 1.0e-12);
        assertEquals(1.0, Vector3D.PLUS_J.getY(), 1.0e-12);
        assertEquals(1.0, Vector3D.PLUS_K.getZ(), 1.0e-12);
        assertEquals(0.0, Vector3D.ZERO.getNorm(), 1.0e-12);
    }

    // Constructor(alpha,delta): alpha=0, delta=0 points along +X
    @Test
    public void testConstructorAlphaDelta_zeroAnglesGivesPlusI() throws Throwable {
        Vector3D v = new Vector3D(0.0, 0.0);
        assertEquals(1.0, v.getX(), 1.0e-9);
        assertEquals(0.0, v.getY(), 1.0e-9);
        assertEquals(0.0, v.getZ(), 1.0e-9);
    }

    // Constructor(alpha,delta) is the inverse of getAlpha()/getDelta()
    @Test
    public void testConstructorAlphaDelta_roundTrip() throws Throwable {
        double alpha = 0.3;
        double delta = 0.2;
        Vector3D v = new Vector3D(alpha, delta);
        assertEquals(alpha, v.getAlpha(), 1.0e-9);
        assertEquals(delta, v.getDelta(), 1.0e-9);
    }

    // Constructor(a,u): scales the base vector
    @Test
    public void testConstructorScale_multipliesVector() throws Throwable {
        Vector3D u = new Vector3D(1, 2, 3);
        Vector3D v = new Vector3D(3, u);
        assertEquals(3.0, v.getX(), 1.0e-12);
        assertEquals(6.0, v.getY(), 1.0e-12);
        assertEquals(9.0, v.getZ(), 1.0e-12);
    }

    // Constructor(a1,u1,a2,u2): linear combination of two vectors
    @Test
    public void testConstructorLinear2_combinesVectors() throws Throwable {
        Vector3D u1 = new Vector3D(1, 0, 0);
        Vector3D u2 = new Vector3D(0, 1, 0);
        Vector3D v = new Vector3D(2, u1, 3, u2);
        assertEquals(2.0, v.getX(), 1.0e-12);
        assertEquals(3.0, v.getY(), 1.0e-12);
        assertEquals(0.0, v.getZ(), 1.0e-12);
    }

    // Constructor(a1,u1,a2,u2,a3,u3): linear combination of three vectors
    @Test
    public void testConstructorLinear3_combinesVectors() throws Throwable {
        Vector3D u1 = new Vector3D(1, 0, 0);
        Vector3D u2 = new Vector3D(0, 1, 0);
        Vector3D u3 = new Vector3D(0, 0, 1);
        Vector3D v = new Vector3D(2, u1, 3, u2, 4, u3);
        assertEquals(2.0, v.getX(), 1.0e-12);
        assertEquals(3.0, v.getY(), 1.0e-12);
        assertEquals(4.0, v.getZ(), 1.0e-12);
    }

    // Constructor(a1,u1,a2,u2,a3,u3,a4,u4): linear combination of four vectors
    @Test
    public void testConstructorLinear4_combinesVectors() throws Throwable {
        Vector3D u1 = new Vector3D(1, 0, 0);
        Vector3D u2 = new Vector3D(0, 1, 0);
        Vector3D u3 = new Vector3D(0, 0, 1);
        Vector3D u4 = new Vector3D(1, 1, 1);
        Vector3D v = new Vector3D(1, u1, 2, u2, 3, u3, 4, u4);
        assertEquals(5.0, v.getX(), 1.0e-12);
        assertEquals(6.0, v.getY(), 1.0e-12);
        assertEquals(7.0, v.getZ(), 1.0e-12);
    }

    // getNorm1: sum of absolute values of coordinates
    @Test
    public void testGetNorm1_sumOfAbsoluteValues() throws Throwable {
        Vector3D v = new Vector3D(3, -4, 12);
        assertEquals(19.0, v.getNorm1(), 1.0e-9);
    }

    // getNorm: euclidean norm
    @Test
    public void testGetNorm_euclideanNorm() throws Throwable {
        Vector3D v = new Vector3D(3, -4, 12);
        assertEquals(13.0, v.getNorm(), 1.0e-9);
    }

    // getNormSq: square of euclidean norm
    @Test
    public void testGetNormSq_squareOfNorm() throws Throwable {
        Vector3D v = new Vector3D(3, -4, 12);
        assertEquals(169.0, v.getNormSq(), 1.0e-9);
    }

    // getNormInf: maximum of absolute coordinates
    @Test
    public void testGetNormInf_maxAbsoluteValue() throws Throwable {
        Vector3D v = new Vector3D(3, -4, 12);
        assertEquals(12.0, v.getNormInf(), 1.0e-9);
    }

    // getAlpha: azimuth measured against the canonical axes
    @Test
    public void testGetAlpha_alongAxes() throws Throwable {
        assertEquals(0.0, Vector3D.PLUS_I.getAlpha(), 1.0e-9);
        assertEquals(Math.PI / 2.0, Vector3D.PLUS_J.getAlpha(), 1.0e-9);
        assertEquals(Math.PI, Vector3D.MINUS_I.getAlpha(), 1.0e-9);
    }

    // getDelta: elevation measured against the canonical axes
    @Test
    public void testGetDelta_alongAxes() throws Throwable {
        assertEquals(0.0, Vector3D.PLUS_I.getDelta(), 1.0e-9);
        assertEquals(Math.PI / 2.0, Vector3D.PLUS_K.getDelta(), 1.0e-9);
        assertEquals(-Math.PI / 2.0, Vector3D.MINUS_K.getDelta(), 1.0e-9);
    }

    // add(Vector3D): component-wise addition
    @Test
    public void testAdd_vector() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(4, 5, 6);
        Vector3D r = v1.add(v2);
        assertEquals(5.0, r.getX(), 1.0e-9);
        assertEquals(7.0, r.getY(), 1.0e-9);
        assertEquals(9.0, r.getZ(), 1.0e-9);
    }

    // add(factor,v): adds a scaled vector
    @Test
    public void testAdd_scaledVector() throws Throwable {
        Vector3D v1 = new Vector3D(1, 1, 1);
        Vector3D v2 = new Vector3D(2, 2, 2);
        Vector3D r = v1.add(3, v2);
        assertEquals(7.0, r.getX(), 1.0e-9);
        assertEquals(7.0, r.getY(), 1.0e-9);
        assertEquals(7.0, r.getZ(), 1.0e-9);
    }

    // subtract(Vector3D): component-wise subtraction
    @Test
    public void testSubtract_vector() throws Throwable {
        Vector3D v1 = new Vector3D(5, 7, 9);
        Vector3D v2 = new Vector3D(1, 2, 3);
        Vector3D r = v1.subtract(v2);
        assertEquals(4.0, r.getX(), 1.0e-9);
        assertEquals(5.0, r.getY(), 1.0e-9);
        assertEquals(6.0, r.getZ(), 1.0e-9);
    }

    // subtract(factor,v): subtracts a scaled vector
    @Test
    public void testSubtract_scaledVector() throws Throwable {
        Vector3D v1 = new Vector3D(10, 10, 10);
        Vector3D v2 = new Vector3D(1, 1, 1);
        Vector3D r = v1.subtract(3, v2);
        assertEquals(7.0, r.getX(), 1.0e-9);
        assertEquals(7.0, r.getY(), 1.0e-9);
        assertEquals(7.0, r.getZ(), 1.0e-9);
    }

    // normalize(): result has unit norm and keeps direction
    @Test
    public void testNormalize_resultHasUnitNorm() throws Throwable {
        Vector3D v = new Vector3D(3, 4, 0);
        Vector3D n = v.normalize();
        assertEquals(0.6, n.getX(), 1.0e-9);
        assertEquals(0.8, n.getY(), 1.0e-9);
        assertEquals(1.0, n.getNorm(), 1.0e-9);
    }

    // normalize() on a zero-norm vector must throw
    @Test
    public void testNormalize_zeroVector_throwsMathArithmeticException() throws Throwable {
        try {
            Vector3D.ZERO.normalize();
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
            // expected
        }
    }

    // orthogonal(): branch where |x| <= threshold
    @Test
    public void testOrthogonal_branchXSmall_resultOrthogonalUnit() throws Throwable {
        Vector3D v = new Vector3D(0, 1, 0);
        Vector3D o = v.orthogonal();
        assertEquals(0.0, Vector3D.dotProduct(v, o), 1.0e-9);
        assertEquals(1.0, o.getNorm(), 1.0e-9);
    }

    // orthogonal(): branch where x is large but |y| <= threshold
    @Test
    public void testOrthogonal_branchYSmall_resultOrthogonalUnit() throws Throwable {
        Vector3D v = new Vector3D(10, 0, 1);
        Vector3D o = v.orthogonal();
        assertEquals(0.0, Vector3D.dotProduct(v, o), 1.0e-9);
        assertEquals(1.0, o.getNorm(), 1.0e-9);
    }

    // orthogonal(): else branch where both x and y exceed threshold
    @Test
    public void testOrthogonal_elseBranch_resultOrthogonalUnit() throws Throwable {
        Vector3D v = new Vector3D(4, 4, 1);
        Vector3D o = v.orthogonal();
        assertEquals(0.0, Vector3D.dotProduct(v, o), 1.0e-9);
        assertEquals(1.0, o.getNorm(), 1.0e-9);
        assertEquals(0.0, o.getZ(), 1.0e-9);
    }

    // orthogonal() on a zero-norm vector must throw
    @Test
    public void testOrthogonal_zeroVector_throwsMathArithmeticException() throws Throwable {
        try {
            Vector3D.ZERO.orthogonal();
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
            // expected
        }
    }

    // angle(): either vector with zero norm must throw
    @Test
    public void testAngle_zeroVector_throwsMathArithmeticException() throws Throwable {
        try {
            Vector3D.angle(Vector3D.ZERO, Vector3D.PLUS_I);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
            // expected
        }
    }

    // angle(): well separated vectors use the acos branch
    @Test
    public void testAngle_wellSeparatedVectors_usesAcos() throws Throwable {
        double a = Vector3D.angle(Vector3D.PLUS_I, Vector3D.PLUS_J);
        assertEquals(Math.PI / 2.0, a, 1.0e-9);
    }

    // angle(): almost aligned vectors with dot>=0 use the asin branch
    @Test
    public void testAngle_almostAlignedDotPositive_usesAsin() throws Throwable {
        Vector3D v1 = new Vector3D(1, 0, 0);
        Vector3D v2 = new Vector3D(1, 1.0e-10, 0);
        double a = Vector3D.angle(v1, v2);
        assertEquals(1.0e-10, a, 1.0e-12);
    }

    // angle(): almost aligned vectors with dot<0 use the PI-asin branch
    @Test
    public void testAngle_almostAlignedDotNegative_usesPiMinusAsin() throws Throwable {
        Vector3D v1 = new Vector3D(1, 0, 0);
        Vector3D v2 = new Vector3D(-1, 1.0e-10, 0);
        double a = Vector3D.angle(v1, v2);
        assertEquals(Math.PI - 1.0e-10, a, 1.0e-9);
    }

    // angle(): identical vectors have zero angle
    @Test
    public void testAngle_identicalVectors_isZero() throws Throwable {
        double a = Vector3D.angle(Vector3D.PLUS_I, Vector3D.PLUS_I);
        assertEquals(0.0, a, 1.0e-9);
    }

    // negate(): flips sign of all coordinates
    @Test
    public void testNegate_negatesAllCoordinates() throws Throwable {
        Vector3D v = new Vector3D(1, -2, 3);
        Vector3D n = v.negate();
        assertEquals(-1.0, n.getX(), 1.0e-9);
        assertEquals(2.0, n.getY(), 1.0e-9);
        assertEquals(-3.0, n.getZ(), 1.0e-9);
    }

    // scalarMultiply(a): scales all coordinates
    @Test
    public void testScalarMultiply_scalesAllCoordinates() throws Throwable {
        Vector3D v = new Vector3D(1, -2, 3);
        Vector3D r = v.scalarMultiply(2.0);
        assertEquals(2.0, r.getX(), 1.0e-9);
        assertEquals(-4.0, r.getY(), 1.0e-9);
        assertEquals(6.0, r.getZ(), 1.0e-9);
    }

    // isNaN(): true when any coordinate is NaN
    @Test
    public void testIsNaN_trueForAnyNaNCoordinate() throws Throwable {
        assertTrue(Vector3D.NaN.isNaN());
        Vector3D v = new Vector3D(1, Double.NaN, 3);
        assertTrue(v.isNaN());
    }

    // isNaN(): false for an ordinary finite vector
    @Test
    public void testIsNaN_falseForFiniteVector() throws Throwable {
        Vector3D v = new Vector3D(1, 2, 3);
        assertFalse(v.isNaN());
    }

    // isInfinite(): true when a coordinate is infinite and none is NaN
    @Test
    public void testIsInfinite_trueForInfiniteWithoutNaN() throws Throwable {
        assertTrue(Vector3D.POSITIVE_INFINITY.isInfinite());
        assertFalse(Vector3D.POSITIVE_INFINITY.isNaN());
    }

    // isInfinite(): false when NaN is present, due to the short-circuit !isNaN()
    @Test
    public void testIsInfinite_falseWhenNaNPresent() throws Throwable {
        Vector3D v = new Vector3D(Double.NaN, Double.POSITIVE_INFINITY, 0);
        assertFalse(v.isInfinite());
    }

    // equals(): same reference and equal-coordinate instances
    @Test
    public void testEquals_sameReferenceAndEqualCoordinates() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        assertTrue(v1.equals(v1));
        Vector3D v2 = new Vector3D(1, 2, 3);
        assertTrue(v1.equals(v2));
    }

    // equals(): false for different type and for null
    @Test
    public void testEquals_differentTypeOrNull_false() throws Throwable {
        Vector3D v = new Vector3D(1, 2, 3);
        assertFalse(v.equals("not a vector"));
        assertFalse(v.equals(null));
    }

    // equals(): any NaN vectors are considered equal to each other
    @Test
    public void testEquals_nanVectorsConsideredEqual() throws Throwable {
        Vector3D a = new Vector3D(Double.NaN, 1, 1);
        Vector3D b = new Vector3D(1, Double.NaN, 1);
        assertTrue(a.equals(b));
    }

    // equals(): false when coordinates differ
    @Test
    public void testEquals_differentCoordinates_false() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(1, 2, 4);
        assertFalse(v1.equals(v2));
    }

    // hashCode(): all NaN vectors share hash code 8, equal objects share hash code
    @Test
    public void testHashCode_nanConstantAndConsistency() throws Throwable {
        assertEquals(8, Vector3D.NaN.hashCode());
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(1, 2, 3);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    // dotProduct(): sum of pairwise coordinate products
    @Test
    public void testDotProduct_computesSumOfProducts() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(4, 5, 6);
        assertEquals(32.0, Vector3D.dotProduct(v1, v2), 1.0e-9);
    }

    // crossProduct(): simple orthonormal case, i x j = k
    @Test
    public void testCrossProduct_perpendicularUnitVectors_correctDirection() throws Throwable {
        Vector3D c = Vector3D.crossProduct(Vector3D.PLUS_I, Vector3D.PLUS_J);
        assertEquals(0.0, c.getX(), 1.0e-9);
        assertEquals(0.0, c.getY(), 1.0e-9);
        assertEquals(1.0, c.getZ(), 1.0e-9);
    }

    // crossProduct(): nearly-parallel large-magnitude vectors; exact algebraic
    // result is (-1,2,1) since v1.x=2*v1.y+1 and v2.x=2*v2.y+1 with z=1 for both,
    // but naive multiplication of ~9e9 values causes catastrophic cancellation
    @Test
    public void testCrossProduct_cancellation_matchesExactAlgebraicResult() throws Throwable {
        Vector3D v1 = new Vector3D(9070467121.0, 4535233560.0, 1.0);
        Vector3D v2 = new Vector3D(9070467123.0, 4535233561.0, 1.0);
        Vector3D c = Vector3D.crossProduct(v1, v2);
        assertEquals(-1.0, c.getX(), 1.0e-6);
        assertEquals(2.0, c.getY(), 1.0e-6);
        assertEquals(1.0, c.getZ(), 1.0e-6);
    }

    // distance1(): L1 distance equals sum of absolute coordinate differences
    @Test
    public void testDistance1_sumOfAbsoluteDifferences() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(4, 6, 3);
        assertEquals(7.0, Vector3D.distance1(v1, v2), 1.0e-9);
    }

    // distance(): L2 (euclidean) distance between two vectors
    @Test
    public void testDistance_euclideanDistance() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(4, 6, 3);
        assertEquals(5.0, Vector3D.distance(v1, v2), 1.0e-9);
    }

    // distanceInf(): L-infinity distance equals max absolute coordinate difference
    @Test
    public void testDistanceInf_maxAbsoluteDifference() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(4, 6, 3);
        assertEquals(4.0, Vector3D.distanceInf(v1, v2), 1.0e-9);
    }

    // distanceSq(): square of the euclidean distance
    @Test
    public void testDistanceSq_squareOfEuclideanDistance() throws Throwable {
        Vector3D v1 = new Vector3D(1, 2, 3);
        Vector3D v2 = new Vector3D(4, 6, 3);
        assertEquals(25.0, Vector3D.distanceSq(v1, v2), 1.0e-9);
    }

    // toString(): returns a non-null string representation (contract of Object.toString override)
    @Test
    public void testToString_returnsNonNullRepresentation() throws Throwable {
        Vector3D v = new Vector3D(1, 2, 3);
        assertNotNull(v.toString());
    }
}
