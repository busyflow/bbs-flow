package ans.molang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dynamically-typed Bedrock MoLang runtime value supporting scalars, strings,
 * nested structs (e.g. {@code v.rysamj.t.x}), arrays, and owning-entity scope refs.
 */
public final class MolangValue {

    public static final MolangValue ZERO = new MolangValue(0.0);
    public static final MolangValue ONE = new MolangValue(1.0);

    public enum Kind {
        NUMBER,
        STRING,
        STRUCT,
        ARRAY,
        ENTITY_REF
    }

    private final Kind kind;
    private double number;
    private String string;
    private Map<String, MolangValue> members;
    private List<MolangValue> elements;
    private MolangScope entityScope;

    private MolangValue(double number) {
        this.kind = Kind.NUMBER;
        this.number = number;
    }

    private MolangValue(String string) {
        this.kind = Kind.STRING;
        this.string = string == null ? "" : string;
    }

    private MolangValue(Kind kind) {
        this.kind = kind;
    }

    public static MolangValue of(double d) {
        if (d == 0.0) {
            return ZERO;
        }
        if (d == 1.0) {
            return ONE;
        }
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return ZERO;
        }
        return new MolangValue(d);
    }

    public static MolangValue of(boolean b) {
        return b ? ONE : ZERO;
    }

    public static MolangValue of(String s) {
        return new MolangValue(s == null ? "" : s);
    }

    public static MolangValue newStruct() {
        MolangValue v = new MolangValue(Kind.STRUCT);
        v.members = new HashMap<>(4);
        return v;
    }

    public static MolangValue newArray() {
        MolangValue v = new MolangValue(Kind.ARRAY);
        v.elements = new ArrayList<>(4);
        return v;
    }

    public static MolangValue entityRef(MolangScope scope) {
        MolangValue v = new MolangValue(Kind.ENTITY_REF);
        v.entityScope = scope;
        return v;
    }

    public static MolangValue trs(double tx, double ty, double tz,
                                  double rx, double ry, double rz,
                                  double sx, double sy, double sz) {
        MolangValue root = newStruct();
        MolangValue t = newStruct();
        t.setMember("x", of(tx));
        t.setMember("y", of(ty));
        t.setMember("z", of(tz));
        MolangValue r = newStruct();
        r.setMember("x", of(rx));
        r.setMember("y", of(ry));
        r.setMember("z", of(rz));
        MolangValue s = newStruct();
        s.setMember("x", of(sx));
        s.setMember("y", of(sy));
        s.setMember("z", of(sz));
        root.setMember("t", t);
        root.setMember("r", r);
        root.setMember("s", s);
        return root;
    }

    public Kind kind() {
        return kind;
    }

    public double asDouble() {
        return switch (kind) {
            case NUMBER -> Double.isFinite(number) ? number : 0.0;
            case STRING -> {
                if (string.isEmpty()) yield 0.0;
                if ("true".equalsIgnoreCase(string)) yield 1.0;
                if ("false".equalsIgnoreCase(string)) yield 0.0;
                try {
                    yield Double.parseDouble(string);
                } catch (NumberFormatException ignored) {
                    yield 1.0; // Non-empty string is truthy in Bedrock conditional expressions
                }
            }
            case STRUCT -> (members != null && !members.isEmpty()) ? 1.0 : 0.0;
            case ARRAY -> (elements != null && !elements.isEmpty()) ? elements.size() : 0.0;
            case ENTITY_REF -> entityScope != null ? 1.0 : 0.0;
        };
    }

    public float asFloat() {
        return (float) asDouble();
    }

    public boolean asBoolean() {
        return switch (kind) {
            case NUMBER -> Double.isFinite(number) && number != 0.0;
            case STRING -> !string.isEmpty() && !"0".equals(string) && !"false".equalsIgnoreCase(string);
            case STRUCT -> members != null && !members.isEmpty();
            case ARRAY -> elements != null && !elements.isEmpty();
            case ENTITY_REF -> entityScope != null;
        };
    }

    public String asString() {
        return switch (kind) {
            case NUMBER -> {
                if (number == Math.rint(number) && Math.abs(number) < 1e15) {
                    yield Long.toString((long) number);
                }
                yield Double.toString(number);
            }
            case STRING -> string;
            case STRUCT -> "[struct]";
            case ARRAY -> "[array]";
            case ENTITY_REF -> "[entity]";
        };
    }

    public MolangScope asEntityScope() {
        return kind == Kind.ENTITY_REF ? entityScope : null;
    }

    public boolean isString() {
        return kind == Kind.STRING;
    }

    public MolangValue getMember(String name) {
        if (members == null || name == null) {
            return ZERO;
        }
        return members.getOrDefault(name.toLowerCase(Locale.ROOT), ZERO);
    }

    public void setMember(String name, MolangValue val) {
        if (members == null) {
            members = new HashMap<>(4);
        }
        members.put(name.toLowerCase(Locale.ROOT), val == null ? ZERO : val);
    }

    public MolangValue ensureStructMember(String name) {
        if (members == null) {
            members = new HashMap<>(4);
        }
        String key = name.toLowerCase(Locale.ROOT);
        MolangValue existing = members.get(key);
        if (existing == null || existing.kind != Kind.STRUCT) {
            existing = newStruct();
            members.put(key, existing);
        }
        return existing;
    }

    public MolangValue getIndex(int index) {
        if (elements == null || elements.isEmpty()) {
            return ZERO;
        }
        int wrapped = Math.floorMod(index, elements.size());
        return elements.get(wrapped);
    }

    public void setIndex(int index, MolangValue val) {
        if (elements == null) {
            elements = new ArrayList<>();
        }
        int idx = Math.max(0, Math.min(index, 4096));
        while (elements.size() <= idx) {
            elements.add(ZERO);
        }
        elements.set(idx, val == null ? ZERO : val);
    }

    public boolean looseEquals(MolangValue other) {
        if (other == null) {
            return !asBoolean();
        }
        if (kind == Kind.STRING && other.kind == Kind.STRING) {
            return string.equals(other.string);
        }
        if (kind == Kind.STRING || other.kind == Kind.STRING) {
            // Comparing e.g. q.variant == 'foo' or '0' == 0
            return asString().equalsIgnoreCase(other.asString());
        }
        return Math.abs(asDouble() - other.asDouble()) < 1e-6;
    }

    @Override
    public String toString() {
        return asString();
    }
}
