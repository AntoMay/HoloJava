package me.holojava.fullgame;

/** CLDC 1.1 math helpers. Math.pow/exp/log are not available in CLDC 1.1. */
final class MathUtil {
    private MathUtil() { }

    static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Round to nearest with ties-to-even, matching the documented preference. */
    static int roundEven(double value) {
        double floorValue = Math.floor(value);
        double diff = value - floorValue;
        if (diff > 0.5) return (int) (floorValue + 1.0);
        if (diff < 0.5) return (int) floorValue;
        int lower = (int) floorValue;
        return (lower & 1) == 0 ? lower : lower + 1;
    }

    /** Stable approximation of base^exponent without using unavailable Math methods. */
    static double power(double base, double exponent) {
        if (base <= 0.0) return 0.0;
        if (exponent == 0.0) return 1.0;
        double x = logApprox(base) * exponent;
        if (x > 20.0) x = 20.0;
        if (x < -20.0) x = -20.0;
        return expApprox(x);
    }

    private static double logApprox(double value) {
        if (value <= 0.0) return 0.0;
        int exponent = 0;
        while (value >= 2.0) {
            value *= 0.5;
            exponent++;
        }
        while (value < 1.0) {
            value *= 2.0;
            exponent--;
        }
        double z = (value - 1.0) / (value + 1.0);
        double z2 = z * z;
        double sum = 0.0;
        double power = z;
        for (int i = 1; i <= 19; i += 2) {
            sum += power / (double) i;
            power *= z2;
        }
        return 2.0 * sum + exponent * 0.6931471805599453;
    }

    private static double expApprox(double x) {
        double term = 1.0;
        double sum = 1.0;
        for (int i = 1; i <= 18; i++) {
            term *= x / (double) i;
            sum += term;
        }
        return sum;
    }

    /** Fast sine approximation (radians) using Bhaskara I's formula, accurate to ~0.0016. */
    static double sin(double x) {
        // Reduce to [-pi, pi]
        x = x % (2.0 * 3.141592653589793);
        if (x < -3.141592653589793) x += 2.0 * 3.141592653589793;
        else if (x > 3.141592653589793) x -= 2.0 * 3.141592653589793;
        // Bhaskara I: sin(x) ≈ 16x(π-x) / (5π² - 4x(π-x))
        double pi = 3.141592653589793;
        double numerator = 16.0 * x * (pi - x);
        double denominator = 5.0 * pi * pi - 4.0 * x * (pi - x);
        return numerator / denominator;
    }
}
