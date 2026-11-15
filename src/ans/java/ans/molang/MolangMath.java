package ans.molang;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Implements all 35 Bedrock MoLang {@code Math.*} functions with Bedrock degree-based
 * trigonometry and easing functions.
 */
public final class MolangMath {

    private MolangMath() {
    }

    public static MolangValue invoke(String fnLower, MolangValue[] args) {
        return switch (fnLower) {
            case "pi" -> MolangValue.of(Math.PI);
            case "abs" -> MolangValue.of(Math.abs(arg(args, 0)));
            case "sin" -> MolangValue.of(Math.sin(Math.toRadians(arg(args, 0))));
            case "cos" -> MolangValue.of(Math.cos(Math.toRadians(arg(args, 0))));
            case "asin" -> MolangValue.of(Math.toDegrees(Math.asin(clamp(arg(args, 0), -1.0, 1.0))));
            case "acos" -> MolangValue.of(Math.toDegrees(Math.acos(clamp(arg(args, 0), -1.0, 1.0))));
            case "atan" -> MolangValue.of(Math.toDegrees(Math.atan(arg(args, 0))));
            case "atan2" -> MolangValue.of(Math.toDegrees(Math.atan2(arg(args, 0), arg(args, 1))));
            case "ceil" -> MolangValue.of(Math.ceil(arg(args, 0)));
            case "floor" -> MolangValue.of(Math.floor(arg(args, 0)));
            case "round" -> MolangValue.of((double) Math.round(arg(args, 0)));
            case "trunc" -> {
                double v = arg(args, 0);
                yield MolangValue.of(v < 0 ? Math.ceil(v) : Math.floor(v));
            }
            case "sqrt" -> MolangValue.of(Math.sqrt(Math.max(0.0, arg(args, 0))));
            case "exp" -> MolangValue.of(Math.exp(arg(args, 0)));
            case "ln" -> {
                double v = arg(args, 0);
                yield MolangValue.of(v > 0.0 ? Math.log(v) : 0.0);
            }
            case "pow" -> MolangValue.of(Math.pow(arg(args, 0), arg(args, 1)));
            case "mod" -> {
                double a = arg(args, 0);
                double b = arg(args, 1);
                yield MolangValue.of(b == 0.0 ? 0.0 : (a % b));
            }
            case "min" -> MolangValue.of(Math.min(arg(args, 0), arg(args, 1)));
            case "max" -> MolangValue.of(Math.max(arg(args, 0), arg(args, 1)));
            case "clamp" -> MolangValue.of(clamp(arg(args, 0), arg(args, 1), arg(args, 2)));
            case "sign" -> MolangValue.of(Math.signum(arg(args, 0)));
            case "copy_sign" -> MolangValue.of(Math.copySign(arg(args, 0), arg(args, 1)));
            case "die_roll" -> {
                int num = Math.max(1, (int) arg(args, 0));
                double low = arg(args, 1);
                double high = arg(args, 2);
                double sum = 0.0;
                for (int i = 0; i < num; i++) {
                    sum += random(low, high);
                }
                yield MolangValue.of(sum);
            }
            case "die_roll_integer" -> {
                int num = Math.max(1, (int) arg(args, 0));
                int low = (int) arg(args, 1);
                int high = (int) arg(args, 2);
                int sum = 0;
                for (int i = 0; i < num; i++) {
                    sum += randomInt(low, high);
                }
                yield MolangValue.of(sum);
            }
            case "random" -> MolangValue.of(random(arg(args, 0), arg(args, 1)));
            case "random_integer" -> MolangValue.of(randomInt((int) Math.floor(arg(args, 0)), (int) Math.floor(arg(args, 1))));
            case "lerp" -> {
                double start = arg(args, 0);
                double end = arg(args, 1);
                double t = arg(args, 2);
                yield MolangValue.of(start + (end - start) * t);
            }
            case "lerprotate" -> MolangValue.of(lerpRotate(arg(args, 0), arg(args, 1), arg(args, 2)));
            case "min_angle" -> MolangValue.of(wrapDegrees(arg(args, 0)));
            case "inverse_lerp" -> {
                double a = arg(args, 0);
                double b = arg(args, 1);
                double v = arg(args, 2);
                double span = b - a;
                yield MolangValue.of(Math.abs(span) < 1e-12 ? 0.0 : clamp((v - a) / span, 0.0, 1.0));
            }
            case "hermite_blend" -> {
                double t = clamp(arg(args, 0), 0.0, 1.0);
                yield MolangValue.of(3.0 * t * t - 2.0 * t * t * t);
            }
            case "ease_in_expo" -> MolangValue.of(easeRange(args, t -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0)));
            case "ease_out_expo" -> MolangValue.of(easeRange(args, t -> t == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t)));
            case "ease_in_out_cubic" -> MolangValue.of(easeRange(args,
                t -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0));
            case "ease_out_cubic" -> MolangValue.of(easeRange(args, t -> 1.0 - Math.pow(1.0 - t, 3.0)));
            case "ease_in_out_sine" -> MolangValue.of(easeRange(args, t -> -(Math.cos(Math.PI * t) - 1.0) / 2.0));
            case "ease_out_circ" -> MolangValue.of(easeRange(args, t -> Math.sqrt(Math.max(0.0, 1.0 - Math.pow(t - 1.0, 2.0)))));
            case "ease_in_out_back" -> MolangValue.of(easeRange(args, t -> {
                double c1 = 1.70158;
                double c2 = c1 * 1.525;
                return t < 0.5
                    ? (Math.pow(2.0 * t, 2.0) * ((c2 + 1.0) * 2.0 * t - c2)) / 2.0
                    : (Math.pow(2.0 * t - 2.0, 2.0) * ((c2 + 1.0) * (t * 2.0 - 2.0) + c2) + 2.0) / 2.0;
            }));
            case "ease_in_quad" -> MolangValue.of(easeRange(args, t -> t * t));
            case "ease_out_quad" -> MolangValue.of(easeRange(args, t -> 1.0 - (1.0 - t) * (1.0 - t)));
            case "ease_in_out_quad" -> MolangValue.of(easeRange(args,
                t -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0));
            default -> MolangValue.ZERO;
        };
    }

    @FunctionalInterface
    private interface DoubleUnary {
        double apply(double t);
    }

    private static double easeRange(MolangValue[] args, DoubleUnary fn) {
        double start = arg(args, 0);
        double end = arg(args, 1);
        double t = clamp(arg(args, 2), 0.0, 1.0);
        return start + (end - start) * fn.apply(t);
    }

    private static double arg(MolangValue[] args, int idx) {
        return (args != null && idx < args.length && args[idx] != null) ? args[idx].asDouble() : 0.0;
    }

    public static double clamp(double v, double lo, double hi) {
        if (lo > hi) {
            double tmp = lo;
            lo = hi;
            hi = tmp;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    public static double wrapDegrees(double angle) {
        double d = angle % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    public static double lerpRotate(double start, double end, double t) {
        double diff = wrapDegrees(end - start);
        return start + diff * clamp(t, 0.0, 1.0);
    }

    private static double random(double low, double high) {
        if (low > high) {
            double tmp = low;
            low = high;
            high = tmp;
        }
        if (low == high) return low;
        return low + ThreadLocalRandom.current().nextDouble() * (high - low);
    }

    private static int randomInt(int low, int high) {
        if (low > high) {
            int tmp = low;
            low = high;
            high = tmp;
        }
        if (low == high) return low;
        return ThreadLocalRandom.current().nextInt(low, high + 1);
    }
}
