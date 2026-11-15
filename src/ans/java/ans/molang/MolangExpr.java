package ans.molang;

import java.util.List;
import java.util.Locale;

/**
 * Compiled MoLang AST expression nodes and fast thread-safe evaluation against {@link MolangScope}.
 */
public interface MolangExpr {

    MolangExpr ZERO = (scope, ctrl) -> MolangValue.ZERO;
    MolangExpr ONE = (scope, ctrl) -> MolangValue.ONE;

    /** Flow-control state carried through statement/block/loop execution. */
    final class ControlState {
        public boolean returned;
        public boolean broken;
        public boolean continued;
        public MolangValue returnValue = MolangValue.ZERO;

        public void reset() {
            returned = false;
            broken = false;
            continued = false;
            returnValue = MolangValue.ZERO;
        }
    }

    MolangValue eval(MolangScope scope, ControlState ctrl);

    default MolangValue evaluate(MolangScope scope) {
        ControlState ctrl = new ControlState();
        MolangValue result = eval(scope, ctrl);
        return ctrl.returned ? ctrl.returnValue : result;
    }

    default float evaluateFloat(MolangScope scope) {
        return evaluate(scope).asFloat();
    }

    default boolean evaluateBoolean(MolangScope scope) {
        return evaluate(scope).asBoolean();
    }

    default boolean isConstantZero() {
        return this == ZERO;
    }

    final class Literal implements MolangExpr {
        private final MolangValue value;

        public Literal(MolangValue value) {
            this.value = value;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            return value;
        }
    }

    final class ThisExpr implements MolangExpr {
        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            return scope.getThisValue();
        }
    }

    final class BlockExpr implements MolangExpr {
        private final List<MolangExpr> statements;

        public BlockExpr(List<MolangExpr> statements) {
            this.statements = statements;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue last = MolangValue.ZERO;
            for (int i = 0; i < statements.size(); i++) {
                last = statements.get(i).eval(scope, ctrl);
                if (ctrl.returned || ctrl.broken || ctrl.continued) {
                    return ctrl.returned ? ctrl.returnValue : last;
                }
            }
            return last;
        }
    }

    final class ReturnExpr implements MolangExpr {
        private final MolangExpr expr;

        public ReturnExpr(MolangExpr expr) {
            this.expr = expr;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue val = expr.eval(scope, ctrl);
            ctrl.returned = true;
            ctrl.returnValue = val;
            return val;
        }
    }

    final class BreakExpr implements MolangExpr {
        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            ctrl.broken = true;
            return MolangValue.ZERO;
        }
    }

    final class ContinueExpr implements MolangExpr {
        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            ctrl.continued = true;
            return MolangValue.ZERO;
        }
    }

    final class LoopExpr implements MolangExpr {
        private final MolangExpr countExpr;
        private final MolangExpr body;

        public LoopExpr(MolangExpr countExpr, MolangExpr body) {
            this.countExpr = countExpr;
            this.body = body;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            int count = (int) Math.min(10000, Math.max(0, countExpr.eval(scope, ctrl).asDouble()));
            MolangValue last = MolangValue.ZERO;
            for (int i = 0; i < count; i++) {
                last = body.eval(scope, ctrl);
                if (ctrl.returned) {
                    return ctrl.returnValue;
                }
                if (ctrl.broken) {
                    ctrl.broken = false;
                    break;
                }
                ctrl.continued = false;
            }
            return last;
        }
    }

    final class UnaryExpr implements MolangExpr {
        private final char op; // '-', '!'
        private final MolangExpr inner;

        public UnaryExpr(char op, MolangExpr inner) {
            this.op = op;
            this.inner = inner;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue v = inner.eval(scope, ctrl);
            if (op == '!') {
                return MolangValue.of(!v.asBoolean());
            }
            if (op == '-') {
                return MolangValue.of(-v.asDouble());
            }
            return v;
        }
    }

    final class BinaryExpr implements MolangExpr {
        public enum Op {
            ADD, SUB, MUL, DIV, LT, LTE, GT, GTE, EQ, NEQ, AND, OR, NULL_COALESCE
        }

        private final Op op;
        private final MolangExpr left;
        private final MolangExpr right;

        public BinaryExpr(Op op, MolangExpr left, MolangExpr right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            return switch (op) {
                case AND -> MolangValue.of(left.eval(scope, ctrl).asBoolean() && right.eval(scope, ctrl).asBoolean());
                case OR -> MolangValue.of(left.eval(scope, ctrl).asBoolean() || right.eval(scope, ctrl).asBoolean());
                case NULL_COALESCE -> {
                    MolangValue lv = left.eval(scope, ctrl);
                    yield lv.asBoolean() ? lv : right.eval(scope, ctrl);
                }
                case ADD -> MolangValue.of(left.eval(scope, ctrl).asDouble() + right.eval(scope, ctrl).asDouble());
                case SUB -> MolangValue.of(left.eval(scope, ctrl).asDouble() - right.eval(scope, ctrl).asDouble());
                case MUL -> MolangValue.of(left.eval(scope, ctrl).asDouble() * right.eval(scope, ctrl).asDouble());
                case DIV -> {
                    double b = right.eval(scope, ctrl).asDouble();
                    yield MolangValue.of(b == 0.0 ? 0.0 : left.eval(scope, ctrl).asDouble() / b);
                }
                case LT -> MolangValue.of(left.eval(scope, ctrl).asDouble() < right.eval(scope, ctrl).asDouble());
                case LTE -> MolangValue.of(left.eval(scope, ctrl).asDouble() <= right.eval(scope, ctrl).asDouble());
                case GT -> MolangValue.of(left.eval(scope, ctrl).asDouble() > right.eval(scope, ctrl).asDouble());
                case GTE -> MolangValue.of(left.eval(scope, ctrl).asDouble() >= right.eval(scope, ctrl).asDouble());
                case EQ -> MolangValue.of(left.eval(scope, ctrl).looseEquals(right.eval(scope, ctrl)));
                case NEQ -> MolangValue.of(!left.eval(scope, ctrl).looseEquals(right.eval(scope, ctrl)));
            };
        }
    }

    final class TernaryExpr implements MolangExpr {
        private final MolangExpr condition;
        private final MolangExpr trueBranch;
        private final MolangExpr falseBranch;

        public TernaryExpr(MolangExpr condition, MolangExpr trueBranch, MolangExpr falseBranch) {
            this.condition = condition;
            this.trueBranch = trueBranch;
            this.falseBranch = falseBranch != null ? falseBranch : ZERO;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            return condition.eval(scope, ctrl).asBoolean()
                ? trueBranch.eval(scope, ctrl)
                : falseBranch.eval(scope, ctrl);
        }
    }

    final class ArrowExpr implements MolangExpr {
        private final MolangExpr targetExpr;
        private final MolangExpr memberExpr;

        public ArrowExpr(MolangExpr targetExpr, MolangExpr memberExpr) {
            this.targetExpr = targetExpr;
            this.memberExpr = memberExpr;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue target = targetExpr.eval(scope, ctrl);
            MolangScope targetScope = target.asEntityScope();
            if (targetScope == null) {
                return MolangValue.ZERO;
            }
            return memberExpr.eval(targetScope, ctrl);
        }
    }

    final class VariableAccessExpr implements MolangExpr {
        public enum Namespace {
            VARIABLE, TEMP, CONTEXT, ARRAY, MATERIAL, TEXTURE, GEOMETRY
        }

        private final Namespace namespace;
        private final String rootNameLower;
        private final List<String> memberPath;

        public VariableAccessExpr(Namespace namespace, String rootName, List<String> memberPath) {
            this.namespace = namespace;
            this.rootNameLower = rootName.toLowerCase(Locale.ROOT);
            this.memberPath = memberPath;
        }

        public Namespace namespace() {
            return namespace;
        }

        public String rootNameLower() {
            return rootNameLower;
        }

        public List<String> memberPath() {
            return memberPath;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue cur = switch (namespace) {
                case VARIABLE -> scope.getVariable(rootNameLower);
                case TEMP -> scope.getTemp(rootNameLower);
                case CONTEXT -> scope.getQueryContext().context(rootNameLower, scope);
                case ARRAY -> scope.getArray(rootNameLower);
                case MATERIAL -> MolangValue.of("material." + rootNameLower);
                case TEXTURE -> MolangValue.of("texture." + rootNameLower);
                case GEOMETRY -> MolangValue.of("geometry." + rootNameLower);
            };
            for (int i = 0; i < memberPath.size(); i++) {
                cur = cur.getMember(memberPath.get(i));
            }
            return cur;
        }

        public MolangValue assign(MolangScope scope, MolangValue value) {
            if (memberPath.isEmpty()) {
                switch (namespace) {
                    case VARIABLE -> scope.setVariable(rootNameLower, value);
                    case TEMP -> scope.setTemp(rootNameLower, value);
                    case CONTEXT -> scope.setContextVar(rootNameLower, value);
                    case ARRAY -> scope.setArray(rootNameLower, value);
                    default -> {
                    }
                }
                return value;
            }

            MolangValue cur = switch (namespace) {
                case VARIABLE -> scope.ensureVariableStruct(rootNameLower);
                case TEMP -> scope.ensureTempStruct(rootNameLower);
                default -> null;
            };
            if (cur == null) {
                return value;
            }
            for (int i = 0; i < memberPath.size() - 1; i++) {
                cur = cur.ensureStructMember(memberPath.get(i));
            }
            cur.setMember(memberPath.get(memberPath.size() - 1), value);
            return value;
        }
    }

    final class AssignExpr implements MolangExpr {
        private final VariableAccessExpr target;
        private final MolangExpr valueExpr;

        public AssignExpr(VariableAccessExpr target, MolangExpr valueExpr) {
            this.target = target;
            this.valueExpr = valueExpr;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue val = valueExpr.eval(scope, ctrl);
            return target.assign(scope, val);
        }
    }

    final class IndexExpr implements MolangExpr {
        private final MolangExpr target;
        private final MolangExpr indexExpr;

        public IndexExpr(MolangExpr target, MolangExpr indexExpr) {
            this.target = target;
            this.indexExpr = indexExpr;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue container = target.eval(scope, ctrl);
            int idx = (int) Math.floor(indexExpr.eval(scope, ctrl).asDouble());
            return container.getIndex(idx);
        }
    }

    final class CallExpr implements MolangExpr {
        public enum Kind {
            MATH, QUERY
        }

        private final Kind kind;
        private final String nameLower;
        private final List<MolangExpr> args;

        public CallExpr(Kind kind, String name, List<MolangExpr> args) {
            this.kind = kind;
            this.nameLower = name.toLowerCase(Locale.ROOT);
            this.args = args;
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            MolangValue[] evaluated = new MolangValue[args.size()];
            for (int i = 0; i < args.size(); i++) {
                evaluated[i] = args.get(i).eval(scope, ctrl);
            }
            if (kind == Kind.MATH) {
                return MolangMath.invoke(nameLower, evaluated);
            }
            return scope.getQueryContext().query(nameLower, evaluated, scope);
        }
    }

    final class MemberExpr implements MolangExpr {
        private final MolangExpr target;
        private final String memberLower;

        public MemberExpr(MolangExpr target, String member) {
            this.target = target;
            this.memberLower = member.toLowerCase(Locale.ROOT);
        }

        @Override
        public MolangValue eval(MolangScope scope, ControlState ctrl) {
            return target.eval(scope, ctrl).getMember(memberLower);
        }
    }
}
