package org.apache.commons.math.analysis.solvers;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;

public class BrentSolverClaudeTest {

    private UnivariateRealFunction linear(final double root) {
        return new UnivariateRealFunction() {
            public double value(double x) {
                return x - root;
            }
        };
    }

    // constructor เริ่มต้น: ใช้งานได้ปกติผ่าน solve(f,min,max)
    @Test
    public void testDefaultConstructor_solveLinear_returnsRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(7.0), 0.0, 10.0);
        assertEquals(7.0, result, 1e-6);
    }

    // constructor ที่รับฟังก์ชัน (deprecated) เก็บฟังก์ชันไว้ใช้กับ solve(min,max)
    @Test
    public void testDeprecatedConstructor_storesFunction_usableLater() throws Throwable {
        BrentSolver solver = new BrentSolver(linear(5.0));
        double result = solver.solve(0.0, 10.0);
        assertEquals(5.0, result, 1e-6);
    }

    // verifySequence: initial < min -> IllegalArgumentException
    @Test
    public void testSolveWithInitial_initialBelowMin_throwsIllegalArgumentException() throws Throwable {
        BrentSolver solver = new BrentSolver();
        try {
            solver.solve(linear(5.0), 5.0, 10.0, 4.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // verifySequence: initial > max -> IllegalArgumentException
    @Test
    public void testSolveWithInitial_initialAboveMax_throwsIllegalArgumentException() throws Throwable {
        BrentSolver solver = new BrentSolver();
        try {
            solver.solve(linear(5.0), 0.0, 5.0, 6.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // yInitial == 0 ตรง -> คืนค่า initial ทันที
    @Test
    public void testSolveWithInitial_initialIsExactRoot_returnsInitial() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(5.0), 0.0, 10.0, 5.0);
        assertEquals(5.0, result, 1e-9);
    }

    // |yInitial| อยู่ในขอบ functionValueAccuracy (ไม่เท่ากับ 0 เป๊ะ) -> คืนค่า initial
    @Test
    public void testSolveWithInitial_initialWithinFunctionValueAccuracy_returnsInitial() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double initial = 5.0000005; // ห่างจากราก 5e-7 < accuracy 1e-6
        double result = solver.solve(linear(5.0), 0.0, 10.0, initial);
        assertEquals(initial, result, 1e-9);
    }

    // ล่าบั๊ก: min เป็นราก (yMin ~ 0) แต่ min != 0 ต้องคืน min ไม่ใช่ yMin
    @Test
    public void testSolveWithInitial_minIsExactRootNotZero_returnsMin() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(5.0), 5.0, 10.0, 7.0);
        assertEquals(5.0, result, 1e-9);
    }

    // ล่าบั๊ก: max เป็นราก (yMax ~ 0) แต่ max != 0 ต้องคืน max ไม่ใช่ yMax
    @Test
    public void testSolveWithInitial_maxIsExactRootNotZero_returnsMax() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(10.0), 0.0, 10.0, 3.0);
        assertEquals(10.0, result, 1e-9);
    }

    // min และ initial bracket รากกัน -> ลู่เข้ารากจริง
    @Test
    public void testSolveWithInitial_minAndInitialBracketRoot_convergesToRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(5.0), 0.0, 100.0, 10.0);
        assertEquals(5.0, result, 1e-6);
    }

    // initial และ max bracket รากกัน -> ลู่เข้ารากจริง
    @Test
    public void testSolveWithInitial_initialAndMaxBracketRoot_convergesToRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(5.0), -100.0, 10.0, 0.0);
        assertEquals(5.0, result, 1e-6);
    }

    // ทุกจุดค่าเดียวกัน ไม่มีรากในช่วง -> IllegalArgumentException
    @Test
    public void testSolveWithInitial_sameSignNoRootNear_throwsIllegalArgumentException() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return x * x + 1.0;
            }
        };
        BrentSolver solver = new BrentSolver();
        try {
            solver.solve(f, 1.0, 2.0, 1.5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // full Brent algorithm ด้วย initial guess บนฟังก์ชันลูกบาศก์ -> ลู่เข้ารากที่ทราบค่า
    @Test
    public void testSolveWithInitial_cubicFunction_convergesToKnownRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return x * x * x - 2.0 * x - 5.0;
            }
        };
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(f, 1.0, 3.0, 2.0);
        assertEquals(2.0945515, result, 1e-4);
    }

    // verifyInterval: min == max -> IllegalArgumentException
    @Test
    public void testSolveNoInitial_minEqualsMax_throwsIllegalArgumentException() throws Throwable {
        BrentSolver solver = new BrentSolver();
        try {
            solver.solve(linear(5.0), 5.0, 5.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // verifyInterval: min > max -> IllegalArgumentException
    @Test
    public void testSolveNoInitial_minGreaterThanMax_throwsIllegalArgumentException() throws Throwable {
        BrentSolver solver = new BrentSolver();
        try {
            solver.solve(linear(5.0), 10.0, 0.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // yMin*yMax > 0 และไม่มีจุดใดใกล้ศูนย์ -> IllegalArgumentException
    @Test
    public void testSolveNoInitial_sameSignFarFromZero_throwsIllegalArgumentException() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return x * x + 1.0;
            }
        };
        BrentSolver solver = new BrentSolver();
        try {
            solver.solve(f, 1.0, 2.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // sign>0 แต่ |yMin| อยู่ในขอบ accuracy -> คืน min (โค้ดถูกต้องใน overload นี้)
    @Test
    public void testSolveNoInitial_yMinCloseToZeroSameSign_returnsMin() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double min = 5.0000001;
        double result = solver.solve(linear(5.0), min, 10.0);
        assertEquals(min, result, 1e-9);
    }

    // sign>0 แต่ |yMax| อยู่ในขอบ accuracy -> คืน max
    @Test
    public void testSolveNoInitial_yMaxCloseToZeroSameSign_returnsMax() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double max = 5.0000001;
        double result = solver.solve(linear(5.0), 6.0, max);
        assertEquals(max, result, 1e-9);
    }

    // sign<0 (bracket ปกติ) -> ลู่เข้ารากจริง
    @Test
    public void testSolveNoInitial_oppositeSigns_convergesToRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(5.0), 0.0, 10.0);
        assertEquals(5.0, result, 1e-6);
    }

    // sign==0 และ yMin==0 -> คืน min
    @Test
    public void testSolveNoInitial_yMinExactZero_returnsMin() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(5.0), 5.0, 10.0);
        assertEquals(5.0, result, 1e-9);
    }

    // sign==0 และ yMax==0 (yMin!=0) -> คืน max
    @Test
    public void testSolveNoInitial_yMaxExactZero_returnsMax() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(5.0), 0.0, 5.0);
        assertEquals(5.0, result, 1e-9);
    }

    // ช่วงติดลบทั้งหมด -> ลู่เข้ารากที่เป็นค่าลบ
    @Test
    public void testSolveNoInitial_negativeInterval_convergesToRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(-3.0), -10.0, 0.0);
        assertEquals(-3.0, result, 1e-6);
    }

    // ฟังก์ชันลูกบาศก์คลาสสิก x^3-2x-5 -> ลู่เข้ารากที่ทราบค่า (กระตุ้น inverse quadratic interpolation)
    @Test
    public void testSolveNoInitial_cubicFunction_convergesToKnownRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return x * x * x - 2.0 * x - 5.0;
            }
        };
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(f, 2.0, 3.0);
        assertEquals(2.0945515, result, 1e-4);
    }

    // ฟังก์ชัน cos(x) -> รากที่ pi/2
    @Test
    public void testSolveNoInitial_cosineFunction_convergesToKnownRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return Math.cos(x);
            }
        };
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(f, 0.0, 3.0);
        assertEquals(Math.PI / 2.0, result, 1e-6);
    }

    // ฟังก์ชันกำลังสอง x^2-4 -> รากบวกคือ 2
    @Test
    public void testSolveNoInitial_quadraticFunction_convergesToPositiveRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return x * x - 4.0;
            }
        };
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(f, 0.0, 3.0);
        assertEquals(2.0, result, 1e-6);
    }

    // deprecated solve(min,max) ใช้ฟังก์ชันที่เก็บไว้จาก constructor
    @Test
    public void testDeprecatedSolveTwoArg_usesStoredFunction_returnsRoot() throws Throwable {
        BrentSolver solver = new BrentSolver(linear(5.0));
        double result = solver.solve(0.0, 10.0);
        assertEquals(5.0, result, 1e-6);
    }

    // deprecated solve(min,max,initial) ใช้ฟังก์ชันที่เก็บไว้จาก constructor
    @Test
    public void testDeprecatedSolveThreeArg_usesStoredFunction_returnsRoot() throws Throwable {
        BrentSolver solver = new BrentSolver(linear(5.0));
        double result = solver.solve(0.0, 10.0, 3.0);
        assertEquals(5.0, result, 1e-6);
    }

    // ฟังก์ชันลูกบาศก์กับ initial guess ที่ bracket กับ max ครอบคลุม full Brent + inverse quadratic path
    @Test
    public void testSolveWithInitial_cubicFunctionBracketWithMax_convergesToKnownRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return x * x * x - 2.0 * x - 5.0;
            }
        };
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(f, 1.0, 3.0, 2.0);
        assertEquals(2.0945515, result, 1e-4);
    }

    // ช่วงกว้างมากเพื่อกระตุ้นการบีบช่วงหลายรอบก่อนลู่เข้า (หลายรอบของลูป while)
    @Test
    public void testSolveNoInitial_wideInterval_convergesToRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        double result = solver.solve(linear(1.0), -1000.0, 1000.0);
        assertEquals(1.0, result, 1e-6);
    }
}
