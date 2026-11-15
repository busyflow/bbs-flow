package ans.molang;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Holds entity-persistent {@code v.*}, per-evaluation {@code t.*}, array {@code Array.*},
 * and contextual {@code c.*} variables for Bedrock MoLang expression evaluation.
 */
public final class MolangScope {

    private final Map<String, MolangValue> variables;
    private final Map<String, MolangValue> temps;
    private final Map<String, MolangValue> contexts;
    private final Map<String, MolangValue> arrays;
    private MolangQueryContext queryContext;
    private MolangValue thisValue = MolangValue.ZERO;

    public MolangScope(MolangQueryContext queryContext) {
        this.variables = new HashMap<>(16);
        this.temps = new HashMap<>(8);
        this.contexts = new HashMap<>(8);
        this.arrays = new HashMap<>(4);
        this.queryContext = queryContext != null ? queryContext : new MolangQueryContext.DefaultContext();
    }

    private MolangScope(Map<String, MolangValue> sharedVariables,
                        Map<String, MolangValue> sharedArrays,
                        MolangQueryContext queryContext) {
        this.variables = sharedVariables;
        this.temps = new HashMap<>(4);
        this.contexts = new HashMap<>(4);
        this.arrays = sharedArrays;
        this.queryContext = queryContext;
    }

    /** Creates a nested call scope sharing entity {@code v.*} variables while isolating {@code t.*}. */
    public MolangScope pushTempScope() {
        return new MolangScope(this.variables, this.arrays, this.queryContext);
    }

    public MolangQueryContext getQueryContext() {
        return queryContext;
    }

    public void setQueryContext(MolangQueryContext queryContext) {
        this.queryContext = queryContext != null ? queryContext : new MolangQueryContext.DefaultContext();
    }

    public MolangValue getThisValue() {
        return thisValue;
    }

    public void setThisValue(MolangValue value) {
        this.thisValue = value != null ? value : MolangValue.ZERO;
    }

    public MolangValue getVariable(String nameLower) {
        return variables.getOrDefault(nameLower, MolangValue.ZERO);
    }

    public void setVariable(String nameLower, MolangValue val) {
        variables.put(nameLower, val != null ? val : MolangValue.ZERO);
    }

    public MolangValue ensureVariableStruct(String nameLower) {
        MolangValue cur = variables.get(nameLower);
        if (cur == null || cur.kind() != MolangValue.Kind.STRUCT) {
            cur = MolangValue.newStruct();
            variables.put(nameLower, cur);
        }
        return cur;
    }

    public MolangValue getTemp(String nameLower) {
        return temps.getOrDefault(nameLower, MolangValue.ZERO);
    }

    public void setTemp(String nameLower, MolangValue val) {
        temps.put(nameLower, val != null ? val : MolangValue.ZERO);
    }

    public MolangValue ensureTempStruct(String nameLower) {
        MolangValue cur = temps.get(nameLower);
        if (cur == null || cur.kind() != MolangValue.Kind.STRUCT) {
            cur = MolangValue.newStruct();
            temps.put(nameLower, cur);
        }
        return cur;
    }

    public MolangValue getContextVar(String nameLower) {
        return contexts.getOrDefault(nameLower, MolangValue.ZERO);
    }

    public void setContextVar(String nameLower, MolangValue val) {
        contexts.put(nameLower, val != null ? val : MolangValue.ZERO);
    }

    public void clearTemps() {
        temps.clear();
    }

    public void defineArray(String arrayNameLower, MolangValue[] elements) {
        MolangValue arr = MolangValue.newArray();
        for (int i = 0; i < elements.length; i++) {
            arr.setIndex(i, elements[i]);
        }
        arrays.put(arrayNameLower.toLowerCase(Locale.ROOT), arr);
    }

    public MolangValue getArray(String arrayNameLower) {
        return arrays.getOrDefault(arrayNameLower.toLowerCase(Locale.ROOT), MolangValue.ZERO);
    }

    public void setArray(String arrayNameLower, MolangValue arrVal) {
        arrays.put(arrayNameLower.toLowerCase(Locale.ROOT), arrVal);
    }
}
