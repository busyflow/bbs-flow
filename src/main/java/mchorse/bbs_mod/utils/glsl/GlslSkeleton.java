package mchorse.bbs_mod.utils.glsl;

import mchorse.bbs_mod.utils.glsl.PpSimulation.Dyn;
import mchorse.bbs_mod.utils.glsl.PpSimulation.DynBranch;
import mchorse.bbs_mod.utils.glsl.PpSimulation.Seq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Second stage: reads the shape of the GLSL around the conditionals that depend on options.
 * A conditional becomes a runtime {@code if} only where it wraps whole statements inside a
 * function, or whole declarations outside of one. Everything else is reported to block its
 * options. Around the conversions it plans what keeps the code valid: declarations used after a
 * runtime {@code if} move before it, functions defined differently per option value get renamed
 * with a dispatcher in front, duplicate declarations are dropped, and places that need constants
 * (array sizes, case labels, layout, Iris' own settings) block the options that would reach them.
 */
class GlslSkeleton
{
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
        "void", "bool", "int", "uint", "float", "double",
        "vec2", "vec3", "vec4", "dvec2", "dvec3", "dvec4", "bvec2", "bvec3", "bvec4",
        "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4",
        "mat2", "mat3", "mat4", "mat2x2", "mat2x3", "mat2x4", "mat3x2", "mat3x3", "mat3x4", "mat4x2", "mat4x3", "mat4x4",
        "dmat2", "dmat3", "dmat4", "dmat2x2", "dmat2x3", "dmat2x4", "dmat3x2", "dmat3x3", "dmat3x4", "dmat4x2", "dmat4x3", "dmat4x4",
        "atomic_uint"
    ));

    private static final Pattern OPAQUE_TYPE = Pattern.compile("^[iu]?(sampler|image)\\w+$");

    private static final Set<String> QUALIFIERS = new HashSet<>(Arrays.asList(
        "const", "uniform", "in", "out", "inout", "varying", "attribute", "buffer", "shared",
        "centroid", "flat", "smooth", "noperspective", "patch", "sample", "invariant", "precise",
        "highp", "mediump", "lowp", "coherent", "volatile", "restrict", "readonly", "writeonly", "subroutine", "layout"
    ));

    /** Their offset argument has to be a constant */
    private static final Set<String> OFFSET_FUNCTIONS = new HashSet<>(Arrays.asList(
        "textureOffset", "texelFetchOffset", "textureProjOffset", "textureLodOffset", "textureProjLodOffset",
        "textureGradOffset", "textureProjGradOffset", "textureGatherOffset", "textureGatherOffsets"
    ));

    /** Constants Iris reads from the source to set up the pipeline */
    private static final Set<String> IRIS_CONSTS = new HashSet<>(Arrays.asList(
        "ambientOcclusionLevel", "centerDepthHalflife", "drynessHalflife", "eyeBrightnessHalflife", "noiseTextureResolution",
        "sunPathRotation", "wetnessHalflife", "entityShadowDistanceMul", "generateShadowColorMipmap", "generateShadowMipmap",
        "shadowDistance", "shadowDistanceRenderMul", "shadowFarPlane", "shadowHardwareFiltering", "shadowIntervalSize",
        "shadowMapFov", "shadowMapResolution", "shadowNearPlane", "shadowtexMipmap", "shadowtexNearest", "voxelDistance",
        "superSamplingLevel"
    ));

    private static final Pattern IRIS_CONST_PATTERN = Pattern.compile("^(colortex\\d+|gcolor|gdepth|gnormal|composite|gaux\\d|shadowcolor\\d*|shadowColor\\d*|shadowtex\\d*|shadow\\d)"
        + "(Format|Clear|ClearColor|MipmapEnabled|Mipmap|Nearest|MinMagNearest)$|^shadowHardwareFiltering\\d$");

    private static final Set<String> IRIS_CONST_TYPES = new HashSet<>(Arrays.asList("int", "float", "vec2", "ivec3", "vec4", "bool"));

    private static final Set<String> ASSIGNMENTS = new HashSet<>(Arrays.asList("=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "^=", "|=", "++", "--"));

    /** Built-in functions writing to an argument */
    private static final Set<String> WRITING_FUNCTIONS = new HashSet<>(Arrays.asList(
        "modf", "frexp", "uaddCarry", "usubBorrow", "umulExtended", "imulExtended",
        "atomicAdd", "atomicMin", "atomicMax", "atomicAnd", "atomicOr", "atomicXor", "atomicExchange", "atomicCompSwap"
    ));

    private static final Pattern OUT_PARAMETER = Pattern.compile("\\b(out|inout)\\b");

    private final List<GlslToken> tokens;
    private final PpSimulation simulation;
    private final String prefix;

    /* Parsing state */

    /** Index of the last token read, and of the one where reading failed */
    private int last = -1;
    int failure = -1;

    /** The last statement read was an if without an else, which a following "else" would continue */
    private boolean openIf;

    private final Set<String> structs = new HashSet<>();
    private final List<DynBranch> presence = new ArrayList<>();
    private final List<Integer> events = new ArrayList<>();

    /** The name each event reads: the identifier, or a name a macro there expands to */
    private final List<String> eventNames = new ArrayList<>();

    /** The conditional branches each identifier of {@link #events} sits in */
    private final List<List<DynBranch>> eventPresence = new ArrayList<>();
    private List<DynBranch> presenceSnapshot = new ArrayList<>();
    private final Deque<Block> blocks = new ArrayDeque<>();

    /* Records */

    /** Types declared for each variable name (type, then array sizes) */
    private final Map<String, Set<String>> declaredTypes = new HashMap<>();

    /** The tokens where each name looks declared, in any branch (found when resolving) */
    private final Map<String, List<Integer>> declarations = new HashMap<>();

    final Map<String, String> blocked = new LinkedHashMap<>();
    final List<StatementConversion> statementConversions = new ArrayList<>();
    final List<Dyn> globalConversions = new ArrayList<>();
    private final List<GlobalDecl> globals = new ArrayList<>();
    private final List<FunctionDef> functions = new ArrayList<>();
    private final List<Decl> consts = new ArrayList<>();
    private final List<ConstantContext> constants = new ArrayList<>();

    /* Edits planned by the resolution */

    final Map<Integer, String> replace = new HashMap<>();
    final Set<Integer> blank = new HashSet<>();
    final Map<Integer, String> before = new HashMap<>();
    final Map<Integer, String> after = new HashMap<>();

    /** Declarations to put after a global conditional's #endif */
    final Map<Dyn, StringBuilder> endifText = new HashMap<>();

    GlslSkeleton(List<GlslToken> tokens, PpSimulation simulation, String prefix)
    {
        this.tokens = tokens;
        this.simulation = simulation;
        this.prefix = prefix;
    }

    static boolean isType(String name)
    {
        return TYPES.contains(name) || OPAQUE_TYPE.matcher(name).matches();
    }

    /** Whether a macro body starting with this word can't be an expression */
    static boolean isTypeOrQualifier(String first, int size)
    {
        return QUALIFIERS.contains(first) || (size == 1 && isType(first)) || first.equals("struct");
    }

    static boolean isIrisConst(String name)
    {
        return IRIS_CONSTS.contains(name) || IRIS_CONST_PATTERN.matcher(name).matches();
    }

    /** Parse everything; false when the code can't be followed at all ({@link #failure} tells where) */
    boolean run()
    {
        Cursor cursor = new Cursor(this.simulation.root);
        Block block = this.openBlock();

        try
        {
            this.unit(cursor, false);
        }
        catch (Unstructured e)
        {
            this.failure = this.last;

            return false;
        }

        this.closeBlock(block);
        this.resolve();

        return true;
    }

    /* Global scope */

    private void unit(Cursor cursor, boolean strict)
    {
        while (true)
        {
            Object item = cursor.peek();

            if (item == null)
            {
                return;
            }

            if (item instanceof Dyn)
            {
                cursor.next();
                this.globalDyn((Dyn) item, cursor);

                continue;
            }

            this.external(cursor);
        }
    }

    private void globalDyn(Dyn dyn, Cursor cursor)
    {
        Snapshot snapshot = this.snapshot();
        boolean ok = true;

        for (DynBranch branch : dyn.branches)
        {
            this.enter(branch);

            try
            {
                this.unit(new Cursor(branch.body), true);
            }
            catch (Unstructured e)
            {
                ok = false;
            }

            this.leave();

            if (!ok)
            {
                break;
            }
        }

        if (!ok)
        {
            this.rollback(snapshot);
            this.unstructured(dyn, cursor, "it doesn't wrap whole declarations");

            return;
        }

        this.globalConversions.add(dyn);
    }

    /** One external declaration: a function definition, or anything up to a semicolon */
    private void external(Cursor cursor)
    {
        if (this.statementMacro(cursor))
        {
            return;
        }

        List<Integer> decl = new ArrayList<>();
        int depth = 0;

        while (true)
        {
            Object item = cursor.peek();

            if (item == null)
            {
                throw Unstructured.INSTANCE;
            }

            if (item instanceof Dyn)
            {
                cursor.next();
                this.unstructured((Dyn) item, cursor, "it cuts a declaration");

                continue;
            }

            int index = (Integer) item;
            GlslToken token = this.tokens.get(index);

            if (token.is("}") && depth == 0)
            {
                throw Unstructured.INSTANCE;
            }

            cursor.next();
            decl.add(index);

            if (token.is("(") || token.is("["))
            {
                depth += 1;
            }
            else if (token.is(")") || token.is("]"))
            {
                depth -= 1;
            }
            else if (token.is(";") && depth == 0)
            {
                this.globalDeclaration(decl);

                return;
            }
            else if (token.is("{") && depth == 0)
            {
                if (this.isFunctionHeader(decl))
                {
                    this.function(decl, cursor);

                    return;
                }

                this.aggregate(cursor, decl);
            }
        }
    }

    private boolean isFunctionHeader(List<Integer> decl)
    {
        int close = decl.size() - 2;

        if (close < 2 || !this.token(decl.get(close)).is(")"))
        {
            return false;
        }

        int open = this.matchingOpen(decl, close);

        return open >= 1 && this.token(decl.get(open - 1)).isIdentifier() && !this.token(decl.get(0)).is("layout");
    }

    private int matchingOpen(List<Integer> list, int close)
    {
        int depth = 0;

        for (int i = close; i >= 0; i--)
        {
            GlslToken token = this.token(list.get(i));

            if (token.is(")"))
            {
                depth += 1;
            }
            else if (token.is("("))
            {
                depth -= 1;

                if (depth == 0)
                {
                    return i;
                }
            }
        }

        return -1;
    }

    /** A struct or interface block body, up to its closing brace */
    private void aggregate(Cursor cursor, List<Integer> decl)
    {
        int depth = 1;

        while (depth > 0)
        {
            Object item = cursor.next();

            if (item == null)
            {
                throw Unstructured.INSTANCE;
            }

            if (item instanceof Dyn)
            {
                this.unstructured((Dyn) item, cursor, "it cuts a structure");

                continue;
            }

            int index = (Integer) item;
            GlslToken token = this.tokens.get(index);

            decl.add(index);

            if (token.is("{"))
            {
                depth += 1;
            }
            else if (token.is("}"))
            {
                depth -= 1;
            }
        }
    }

    private void function(List<Integer> decl, Cursor cursor)
    {
        int close = decl.size() - 2;
        int open = this.matchingOpen(decl, close);
        FunctionDef function = new FunctionDef();

        function.first = decl.get(0);
        function.name = decl.get(open - 1);
        this.declares(function.name);
        function.presence = new ArrayList<>(this.presence);
        function.returnType = this.join(decl.subList(0, open - 1));
        function.params = this.join(decl.subList(open + 1, close));
        function.paramNames = new ArrayList<>();

        StringBuilder signature = new StringBuilder(this.token(function.name).text).append('(');

        for (List<Integer> param : this.split(decl.subList(open + 1, close)))
        {
            this.parameter(param, signature, function.paramNames);
        }

        function.signature = signature.append(')').toString();

        Block block = this.openBlock();
        int start = this.events.size();

        this.statements(cursor, true, null);

        Object closing = cursor.next();

        function.last = (Integer) closing;
        function.body = new ArrayList<>(this.events.subList(start, this.events.size()));
        this.closeBlock(block);
        this.functions.add(function);
    }

    /** Add a parameter's type to the signature and its name to the list */
    private void parameter(List<Integer> param, StringBuilder signature, List<String> names)
    {
        List<String> type = new ArrayList<>();
        String name = null;

        for (int i = 0; i < param.size(); i++)
        {
            GlslToken token = this.token(param.get(i));

            if (token.is("["))
            {
                int end = this.closeBracket(param, i);

                this.constants.add(new ConstantContext(param.subList(i + 1, end), "it sets an array size", this.presence));
                type.add(this.join(param.subList(i, end + 1)));
                i = end;
            }
            else if (token.isIdentifier() && (i == param.size() - 1 || this.token(param.get(i + 1)).is("[")) && !type.isEmpty() && !isType(token.text) && !this.structs.contains(token.text))
            {
                name = token.text;
                this.declares(param.get(i));
            }
            else if (!(token.is("in") || token.is("const") || token.is("highp") || token.is("mediump") || token.is("lowp") || token.is("precise")))
            {
                type.add(token.text);
            }
        }

        if (type.size() == 1 && type.get(0).equals("void") && name == null)
        {
            return;
        }

        if (signature.charAt(signature.length() - 1) != '(')
        {
            signature.append(',');
        }

        signature.append(String.join(" ", type));
        names.add(name);
    }

    private int closeBracket(List<Integer> list, int open)
    {
        int depth = 0;

        for (int i = open; i < list.size(); i++)
        {
            GlslToken token = this.token(list.get(i));

            if (token.is("["))
            {
                depth += 1;
            }
            else if (token.is("]"))
            {
                depth -= 1;

                if (depth == 0)
                {
                    return i;
                }
            }
        }

        return list.size() - 1;
    }

    /** Split by top-level commas */
    private List<List<Integer>> split(List<Integer> list)
    {
        List<List<Integer>> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < list.size(); i++)
        {
            GlslToken token = this.token(list.get(i));

            if (token.is("(") || token.is("[") || token.is("{"))
            {
                depth += 1;
            }
            else if (token.is(")") || token.is("]") || token.is("}"))
            {
                depth -= 1;
            }
            else if (token.is(",") && depth == 0)
            {
                parts.add(list.subList(start, i));
                start = i + 1;
            }
        }

        if (start < list.size())
        {
            parts.add(list.subList(start, list.size()));
        }

        return parts;
    }

    private void globalDeclaration(List<Integer> decl)
    {
        GlobalDecl global = new GlobalDecl();

        global.from = decl.get(0);
        global.to = decl.get(decl.size() - 1);
        global.presence = new ArrayList<>(this.presence);
        global.text = this.join(decl);

        GlslToken first = this.token(decl.get(0));

        if (first.is("precision") || first.is(";"))
        {
            return;
        }

        if (first.is("struct"))
        {
            if (decl.size() > 1 && this.token(decl.get(1)).isIdentifier())
            {
                this.structs.add(this.token(decl.get(1)).text);
                global.kind = GlobalDecl.Kind.STRUCT;
                global.names.add(this.token(decl.get(1)).text);
                this.globals.add(global);
            }

            return;
        }

        Decl parsed = this.declaration(decl, true);

        if (parsed == null)
        {
            /* A layout default such as "layout(local_size_x = 8) in;", or something unknown */
            global.kind = GlobalDecl.Kind.OTHER;
            this.globals.add(global);

            return;
        }

        global.decl = parsed;

        if (parsed.block != null)
        {
            global.kind = GlobalDecl.Kind.BLOCK;
            global.names.add(parsed.block);
        }
        else if (parsed.prototype)
        {
            global.kind = GlobalDecl.Kind.PROTOTYPE;
        }
        else
        {
            global.kind = GlobalDecl.Kind.VARIABLE;

            for (Declarator declarator : parsed.declarators)
            {
                global.names.add(this.token(declarator.name).text);

                /* Iris reads these from the source text: they can't follow the options */
                if (parsed.isConst && isIrisConst(this.token(declarator.name).text) && parsed.declarators.size() == 1
                    && parsed.type >= 0 && IRIS_CONST_TYPES.contains(this.token(parsed.type).text))
                {
                    parsed.iris = true;

                    if (!this.presence.isEmpty())
                    {
                        this.block(symbols(this.presence), "Iris reads " + this.token(declarator.name).text + " from the source");
                    }

                    if (declarator.init != null)
                    {
                        this.constants.add(new ConstantContext(declarator.init, "Iris reads " + this.token(declarator.name).text + " from the source", this.presence));
                    }
                }
            }
        }

        this.globals.add(global);
    }

    /**
     * Take a declaration apart: qualifiers, type, declarators. Null when the tokens don't make
     * one. Array sizes and layout qualifiers are recorded as places needing constants.
     */
    private Decl declaration(List<Integer> stmt, boolean global)
    {
        Decl decl = new Decl();
        int i = 0;
        int n = stmt.size();

        while (i < n && QUALIFIERS.contains(this.token(stmt.get(i)).text))
        {
            GlslToken token = this.token(stmt.get(i));

            if (token.is("layout"))
            {
                int close = i + 1 < n && this.token(stmt.get(i + 1)).is("(") ? this.closeParenthesis(stmt, i + 1) : -1;

                if (close < 0)
                {
                    return null;
                }

                this.constants.add(new ConstantContext(stmt.subList(i + 2, close), "it's in a layout qualifier", this.presence));
                decl.qualifiers.addAll(stmt.subList(i, close + 1));
                i = close + 1;

                continue;
            }

            if (token.is("const"))
            {
                decl.isConst = true;
                decl.constToken = stmt.get(i);
            }

            decl.storage |= !(token.is("const") || token.is("highp") || token.is("mediump") || token.is("lowp") || token.is("precise") || token.is("invariant"));
            decl.qualifiers.add(stmt.get(i));
            i += 1;
        }

        if (i >= n || !this.token(stmt.get(i)).isIdentifier())
        {
            return null;
        }

        GlslToken type = this.token(stmt.get(i));
        boolean known = isType(type.text) || this.structs.contains(type.text);
        GlslToken next = i + 1 < n ? this.token(stmt.get(i + 1)) : null;

        if (next == null)
        {
            return null;
        }

        if (global && next.is("{") && !decl.qualifiers.isEmpty())
        {
            /* Interface block: "uniform Name { ... } instance;" */
            decl.block = type.text;

            return decl;
        }

        if (!(known && (next.isIdentifier() || next.is("["))) && !(next.isIdentifier() && !decl.qualifiers.isEmpty()) && !(next.isIdentifier() && !QUALIFIERS.contains(next.text) && this.isUnknownType(type.text)))
        {
            return null;
        }

        decl.type = stmt.get(i);
        i += 1;

        while (i < n && this.token(stmt.get(i)).is("["))
        {
            int close = this.closeBracket(stmt, i);

            decl.typeArrays.add(new ArrayList<>(stmt.subList(i, close + 1)));
            this.constants.add(new ConstantContext(stmt.subList(i + 1, close), "it sets an array size", this.presence));
            i = close + 1;
        }

        while (i < n)
        {
            GlslToken name = this.token(stmt.get(i));

            if (!name.isIdentifier())
            {
                return null;
            }

            Declarator declarator = new Declarator();

            declarator.name = stmt.get(i);
            this.declares(declarator.name);
            i += 1;

            if (i < n && this.token(stmt.get(i)).is("("))
            {
                /* A prototype */
                decl.prototype = true;

                return decl;
            }

            while (i < n && this.token(stmt.get(i)).is("["))
            {
                int close = this.closeBracket(stmt, i);

                declarator.arrays.add(new ArrayList<>(stmt.subList(i, close + 1)));
                declarator.unsized |= close == i + 1;
                this.constants.add(new ConstantContext(stmt.subList(i + 1, close), "it sets an array size", this.presence));
                i = close + 1;
            }

            if (i < n && this.token(stmt.get(i)).is("="))
            {
                int start = i + 1;
                int depth = 0;

                i += 1;

                while (i < n)
                {
                    GlslToken token = this.token(stmt.get(i));

                    if (token.is("(") || token.is("[") || token.is("{"))
                    {
                        depth += 1;
                    }
                    else if (token.is(")") || token.is("]") || token.is("}"))
                    {
                        depth -= 1;
                    }
                    else if ((token.is(",") || token.is(";")) && depth == 0)
                    {
                        break;
                    }

                    i += 1;
                }

                declarator.init = stmt.subList(start, i);
            }

            decl.declarators.add(declarator);
            this.declaredTypes.computeIfAbsent(this.token(declarator.name).text, (k) -> new HashSet<>())
                .add(this.typeText(decl, true) + this.arraysText(declarator));

            if (i < n && this.token(stmt.get(i)).is(","))
            {
                i += 1;

                continue;
            }

            if (i < n && this.token(stmt.get(i)).is(";"))
            {
                break;
            }

            return null;
        }

        if (decl.isConst)
        {
            this.consts.add(decl);
        }

        return decl;
    }

    /** A user type we haven't seen declared, such as one coming from a macro */
    private boolean isUnknownType(String name)
    {
        return !QUALIFIERS.contains(name) && !name.equals("return") && !name.equals("else") && !name.equals("case");
    }

    private int closeParenthesis(List<Integer> list, int open)
    {
        int depth = 0;

        for (int i = open; i < list.size(); i++)
        {
            GlslToken token = this.token(list.get(i));

            if (token.is("("))
            {
                depth += 1;
            }
            else if (token.is(")"))
            {
                depth -= 1;

                if (depth == 0)
                {
                    return i;
                }
            }
        }

        return -1;
    }

    /* Statements */

    /** Statements up to a closing brace (left for the caller) or, unbraced, to the end */
    private void statements(Cursor cursor, boolean braced, BranchInfo info)
    {
        this.openIf = false;

        while (true)
        {
            Object item = cursor.peek();

            if (item == null)
            {
                if (braced)
                {
                    throw Unstructured.INSTANCE;
                }

                if (info != null)
                {
                    info.openIf = this.openIf;
                }

                return;
            }

            if (item instanceof Dyn)
            {
                cursor.next();
                this.statementDyn((Dyn) item, cursor, false, info);

                continue;
            }

            GlslToken token = this.tokens.get((Integer) item);

            if (token.is("}"))
            {
                if (braced)
                {
                    return;
                }

                throw Unstructured.INSTANCE;
            }

            /* A label or a dangling else can't be wrapped into a block */
            if (info != null && (token.is("case") || token.is("default") || token.is("else")))
            {
                throw Unstructured.INSTANCE;
            }

            this.statement(cursor, info);
        }
    }

    private void statement(Cursor cursor, BranchInfo info)
    {
        int event = this.events.size();

        if (this.statementMacro(cursor))
        {
            this.openIf = false;

            return;
        }

        Object item = cursor.next();

        if (!(item instanceof Integer))
        {
            throw Unstructured.INSTANCE;
        }

        int first = (Integer) item;
        GlslToken token = this.tokens.get(first);

        if (token.is("{"))
        {
            Block block = this.openBlock();

            this.statements(cursor, true, null);
            this.expect(cursor, "}");
            this.closeBlock(block);
            this.openIf = false;
        }
        else if (token.is("if"))
        {
            this.group(cursor);
            this.substatement(cursor);

            if (this.peekElse(cursor))
            {
                cursor.next();
                this.substatement(cursor);
            }
            else
            {
                this.openIf = true;
            }
        }
        else if (token.is("for") || token.is("while"))
        {
            /* An else after "for (...) if (...) x;" belongs to that if */
            this.group(cursor);
            this.substatement(cursor);
        }
        else if (token.is("do"))
        {
            this.substatement(cursor);
            this.expect(cursor, "while");
            this.group(cursor);
            this.expect(cursor, ";");
            this.openIf = false;
        }
        else if (token.is("switch"))
        {
            this.group(cursor);
            this.expect(cursor, "{");

            Block block = this.openBlock();

            this.statements(cursor, true, null);
            this.expect(cursor, "}");
            this.closeBlock(block);
            this.openIf = false;
        }
        else if (token.is("case"))
        {
            List<Integer> label = this.until(cursor, ":");

            this.constants.add(new ConstantContext(label, "it's a case label", this.presence));
            this.openIf = false;
        }
        else if (token.is("default"))
        {
            this.expect(cursor, ":");
            this.openIf = false;
        }
        else if (token.is(";"))
        {
            this.openIf = false;
        }
        else
        {
            List<Integer> statement = new ArrayList<>();

            this.openIf = false;

            statement.add(first);
            statement.addAll(this.until(cursor, ";"));

            if (token.is("struct"))
            {
                if (statement.size() > 1 && this.token(statement.get(1)).isIdentifier())
                {
                    this.structs.add(this.token(statement.get(1)).text);
                }

                if (info != null)
                {
                    info.structs.add(statement);
                }

                return;
            }

            Decl decl = this.declaration(statement, false);

            if (decl != null && info != null && !decl.prototype)
            {
                info.declarations.add(new BranchDecl(decl, event));
            }
        }
    }

    /**
     * A macro expanding to whole statements or declarations, used without a semicolon
     * ("ITERATION(4u)" on its own line): read its call as one statement.
     */
    private boolean statementMacro(Cursor cursor)
    {
        Object item = cursor.peek();
        Boolean function = item instanceof Integer ? this.simulation.statementMacros.get(item) : null;

        if (function == null)
        {
            return false;
        }

        cursor.next();

        if (function && this.peekIs(cursor, "("))
        {
            this.group(cursor);
        }

        if (this.peekIs(cursor, ";"))
        {
            cursor.next();
        }

        return true;
    }

    /** The statement after if/else/for/while/do: possibly a whole conditional */
    private void substatement(Cursor cursor)
    {
        while (true)
        {
            Object item = cursor.peek();

            if (!(item instanceof Dyn))
            {
                this.statement(cursor, null);

                return;
            }

            cursor.next();

            /* A conditional that can't be converted leaves its current branch to read instead */
            if (this.statementDyn((Dyn) item, cursor, true, null))
            {
                return;
            }
        }
    }

    /**
     * Whether the conditional was converted (otherwise its current branch is next in the cursor).
     * One sitting right in a branch of another (parent) shares that one's scope.
     */
    private boolean statementDyn(Dyn dyn, Cursor cursor, boolean sole, BranchInfo parent)
    {
        Snapshot snapshot = this.snapshot();
        StatementConversion conversion = new StatementConversion(dyn, sole);
        List<BranchInfo> infos = conversion.branches;
        boolean ok = true;

        conversion.parent = parent == null ? null : parent.owner;

        for (DynBranch branch : dyn.branches)
        {
            BranchInfo info = new BranchInfo(branch, this.events.size(), conversion);

            this.enter(branch);

            try
            {
                this.statements(new Cursor(branch.body), false, info);
            }
            catch (Unstructured e)
            {
                ok = false;
            }

            this.leave();

            if (!ok)
            {
                break;
            }

            info.end = this.events.size();
            infos.add(info);
        }

        if (!ok)
        {
            this.rollback(snapshot);
            this.unstructured(dyn, cursor, "it doesn't wrap whole statements");

            return false;
        }

        boolean openIf = false;

        for (BranchInfo info : infos)
        {
            openIf |= info.openIf;
        }

        /* "#ifdef A if (x) y(); #endif else z();" chains the else to the if inside */
        if (openIf && this.elseFollows(cursor))
        {
            this.rollback(snapshot);
            this.unstructured(dyn, cursor, "an else after it continues an if inside it");

            return false;
        }

        this.openIf = openIf;
        conversion.after = this.events.size();
        this.statementConversions.add(conversion);

        if (!sole)
        {
            conversion.block = this.blocks.peek();
            conversion.block.conversions.add(conversion);
        }

        return true;
    }

    /** A parenthesised group, as after "if" */
    private void group(Cursor cursor)
    {
        this.expect(cursor, "(");

        int depth = 1;

        while (depth > 0)
        {
            Object item = cursor.next();

            if (item == null)
            {
                throw Unstructured.INSTANCE;
            }

            if (item instanceof Dyn)
            {
                this.unstructured((Dyn) item, cursor, "it cuts an expression");

                continue;
            }

            GlslToken token = this.tokens.get((Integer) item);

            if (token.is("("))
            {
                depth += 1;
            }
            else if (token.is(")"))
            {
                depth -= 1;
            }
        }
    }

    /** Tokens up to the terminator at the top level (consumed, not returned) */
    private List<Integer> until(Cursor cursor, String terminator)
    {
        List<Integer> list = new ArrayList<>();
        int depth = 0;

        while (true)
        {
            Object item = cursor.peek();

            if (item == null)
            {
                throw Unstructured.INSTANCE;
            }

            if (item instanceof Dyn)
            {
                cursor.next();
                this.unstructured((Dyn) item, cursor, "it cuts a statement");

                continue;
            }

            int index = (Integer) item;
            GlslToken token = this.tokens.get(index);

            if (depth == 0 && token.is("}"))
            {
                throw Unstructured.INSTANCE;
            }

            cursor.next();

            if (depth == 0 && token.is(terminator))
            {
                list.add(index);

                return list;
            }

            if (token.is("(") || token.is("[") || token.is("{"))
            {
                depth += 1;
            }
            else if (token.is(")") || token.is("]") || token.is("}"))
            {
                depth -= 1;
            }

            list.add(index);
        }
    }

    private void expect(Cursor cursor, String text)
    {
        Object item = cursor.next();

        if (!(item instanceof Integer) || !this.tokens.get((Integer) item).is(text))
        {
            throw Unstructured.INSTANCE;
        }
    }

    private boolean peekIs(Cursor cursor, String text)
    {
        Object item = cursor.peek();

        return item instanceof Integer && this.tokens.get((Integer) item).is(text);
    }

    /**
     * Whether an "else" continues the if just read. A conditional holding such an "else" (packs
     * chain if/else across several of them) can't be converted: it takes its current branch.
     */
    private boolean peekElse(Cursor cursor)
    {
        while (true)
        {
            Object item = cursor.peek();

            if (item instanceof Dyn && this.startsWithElse((Dyn) item))
            {
                cursor.next();
                this.unstructured((Dyn) item, cursor, "it continues an if with an else");

                continue;
            }

            return item instanceof Integer && this.tokens.get((Integer) item).is("else");
        }
    }

    private boolean elseFollows(Cursor cursor)
    {
        Object item = cursor.peek();

        return (item instanceof Integer && this.tokens.get((Integer) item).is("else")) || (item instanceof Dyn && this.startsWithElse((Dyn) item));
    }

    private boolean startsWithElse(Dyn dyn)
    {
        for (DynBranch branch : dyn.branches)
        {
            Object first = branch.body.items.isEmpty() ? null : branch.body.items.get(0);

            if ((first instanceof Integer && this.tokens.get((Integer) first).is("else")) || (first instanceof Dyn && this.startsWithElse((Dyn) first)))
            {
                return true;
            }
        }

        return false;
    }

    /** A name declared in code that a macro takes over for some option values only */
    private void declares(int index)
    {
        Set<String> symbols = this.simulation.partial.get(this.tokens.get(index).text);

        if (symbols != null)
        {
            this.block(symbols, "a macro replaces " + this.tokens.get(index).text + " only for some of its values");
        }
    }

    private void event(int index, String name)
    {
        this.events.add(index);
        this.eventNames.add(name);
        this.eventPresence.add(this.presenceSnapshot);
    }

    private void enter(DynBranch branch)
    {
        this.presence.add(branch);
        this.presenceSnapshot = new ArrayList<>(this.presence);
    }

    private void leave()
    {
        this.presence.remove(this.presence.size() - 1);
        this.presenceSnapshot = new ArrayList<>(this.presence);
    }

    /* Conditionals that can't be converted */

    private void unstructured(Dyn dyn, Cursor cursor, String reason)
    {
        this.block(dyn.symbols, reason);

        DynBranch fallback = this.defaultBranch(dyn);

        if (fallback != null)
        {
            cursor.inline(fallback.body);
        }
    }

    /** The branch taken with the options as they are set */
    private DynBranch defaultBranch(Dyn dyn)
    {
        for (DynBranch branch : dyn.branches)
        {
            if (branch.taken.evaluate(this.simulation.defaults) != 0)
            {
                return branch;
            }
        }

        return null;
    }

    private void block(Set<String> symbols, String reason)
    {
        for (String symbol : symbols)
        {
            this.blocked.putIfAbsent(symbol, reason);
        }
    }

    private static Set<String> symbols(List<DynBranch> presence)
    {
        Set<String> symbols = new LinkedHashSet<>();

        for (DynBranch branch : presence)
        {
            branch.taken.symbols(symbols);
        }

        return symbols;
    }

    /* Blocks and identifier events */

    private Block openBlock()
    {
        Block block = new Block();

        this.blocks.push(block);

        return block;
    }

    /** Close a block: the conversions in it learn which names its later code uses */
    private void closeBlock(Block block)
    {
        this.blocks.pop();

        for (StatementConversion conversion : block.conversions)
        {
            for (int i = conversion.after; i < this.events.size(); i++)
            {
                conversion.usedAfter.add(this.eventNames.get(i));
            }
        }
    }

    /* Resolution */

    private void resolve()
    {
        Map<String, Set<String>> tainted = new HashMap<>();
        Map<String, Set<String>> hoisted = new HashMap<>();

        this.resolveNames();
        this.resolveSelects();
        this.resolveGlobals(tainted);
        this.resolveHoisting(hoisted);
        this.resolveTaint(tainted, hoisted);
    }

    /**
     * Code in a converted branch is compiled for every value, so a name it uses must be declared
     * for every value too. One declared only where the preprocessor drops it (an include behind
     * another switch, say) keeps its branch's options constant.
     */
    private void resolveNames()
    {
        this.declared(this.simulation.root, new DeclarationScan(), this.declarations);

        for (int i = 0; i < this.events.size(); i++)
        {
            List<DynBranch> presence = this.eventPresence.get(i);

            if (presence.isEmpty())
            {
                continue;
            }

            String name = this.eventNames.get(i);

            if (!this.declarations.containsKey(name) && !GlslBuiltins.contains(name) && !this.simulation.definedNames.contains(name))
            {
                this.block(symbols(presence), "it uses " + name + ", declared only for other values");
            }
        }
    }

    /**
     * A macro choosing between variables at runtime must choose between equal types: arrays of
     * different sizes on the two sides of "?:" don't compile.
     */
    private void resolveSelects()
    {
        Set<String> used = new HashSet<>(this.eventNames);

        for (Map.Entry<String, Set<String>> entry : this.simulation.selects.entrySet())
        {
            if (!used.contains(entry.getKey()))
            {
                continue;
            }

            Set<String> types = new HashSet<>();
            boolean known = true;

            for (String body : entry.getValue())
            {
                Set<String> type = this.typeOf(body);

                if (type == null)
                {
                    known = false;

                    break;
                }

                types.addAll(type);
            }

            if (known && types.size() > 1)
            {
                this.block(this.simulation.selectSymbols.get(entry.getKey()), "a macro chooses between values of different types");
            }
        }
    }

    /**
     * Type of a macro body when it's plain enough to tell: a declared variable, a constructor
     * (arrays included) or a number (int and float mix fine). Null when it can't be told.
     */
    private Set<String> typeOf(String body)
    {
        List<GlslToken> tokens = new ArrayList<>();

        for (GlslToken token : GlslLexer.tokenizeFragment(body))
        {
            if (!token.isTrivia())
            {
                tokens.add(token);
            }
        }

        if (tokens.size() == 1 && tokens.get(0).type == GlslToken.Type.NUMBER)
        {
            return Collections.singleton("number");
        }

        if (tokens.size() == 1 && tokens.get(0).isIdentifier() && !this.simulation.definedNames.contains(tokens.get(0).text))
        {
            Set<String> declared = this.declaredTypes.get(tokens.get(0).text);

            return declared == null ? Collections.singleton("?" + tokens.get(0).text) : declared;
        }

        if (tokens.size() > 2 && isType(tokens.get(0).text))
        {
            StringBuilder type = new StringBuilder(tokens.get(0).text);
            int i = 1;

            while (i + 2 < tokens.size() && tokens.get(i).is("[") && tokens.get(i + 2).is("]"))
            {
                type.append('[').append(tokens.get(i + 1).text).append(']');
                i += 3;
            }

            if (tokens.get(i).is("("))
            {
                return Collections.singleton(type.toString());
            }
        }

        return null;
    }

    /** Names that look declared anywhere in the code, any branch: after a type, in a declarator list... */
    private void declared(Seq seq, DeclarationScan scan, Map<String, List<Integer>> declared)
    {
        for (int i = 0; i < seq.items.size(); i++)
        {
            Object item = seq.items.get(i);

            if (item instanceof Dyn)
            {
                for (DynBranch branch : ((Dyn) item).branches)
                {
                    this.declared(branch.body, scan, declared);
                }

                continue;
            }

            GlslToken token = this.tokens.get((Integer) item);

            if (token.isIdentifier() && scan.previous != null)
            {
                String previous = scan.previous.text;
                Object next = i + 1 < seq.items.size() ? seq.items.get(i + 1) : null;
                boolean instance = previous.equals("}") && next instanceof Integer && (this.tokens.get((Integer) next).is(";") || this.tokens.get((Integer) next).is("["));
                boolean declares = isType(previous) || this.structs.contains(previous) || previous.equals("struct")
                    || previous.equals("]") || instance
                    || (scan.previous.isIdentifier() && !QUALIFIERS.contains(previous) && !GlslBuiltins.contains(previous) && !previous.equals("return"))
                    || (scan.declaring && previous.equals(",") && scan.depth == scan.declarationDepth);

                if (declares)
                {
                    declared.computeIfAbsent(token.text, (k) -> new ArrayList<>()).add((Integer) item);

                    /* "uniform sampler2D MAIN_OUTPUT;" with "#define MAIN_OUTPUT colortex6" declares colortex6 */
                    for (String name : this.simulation.expansions.getOrDefault((Integer) item, Collections.emptySet()))
                    {
                        declared.computeIfAbsent(name, (k) -> new ArrayList<>()).add((Integer) item);
                    }

                    if (!scan.declaring)
                    {
                        scan.declaring = true;
                        scan.declarationDepth = scan.depth;
                    }
                }
            }

            if (token.is("(") || token.is("[") || token.is("{"))
            {
                scan.depth += 1;
            }
            else if (token.is(")") || token.is("]") || token.is("}"))
            {
                scan.depth -= 1;

                if (scan.depth < scan.declarationDepth)
                {
                    scan.declaring = false;
                }
            }
            else if (token.is(";") && scan.depth <= scan.declarationDepth)
            {
                scan.declaring = false;
            }

            scan.previous = token;
        }
    }

    private static class DeclarationScan
    {
        GlslToken previous;
        int depth;
        boolean declaring;
        int declarationDepth;
    }

    /** Declarations made unconditional must not clash */
    private void resolveGlobals(Map<String, Set<String>> tainted)
    {
        Map<String, List<FunctionDef>> bySignature = new LinkedHashMap<>();

        for (FunctionDef function : this.functions)
        {
            bySignature.computeIfAbsent(function.signature, (k) -> new ArrayList<>()).add(function);
        }

        for (List<FunctionDef> group : bySignature.values())
        {
            if (group.size() > 1)
            {
                this.dispatch(group);
            }
        }

        Map<String, List<GlobalDecl>> byName = new LinkedHashMap<>();

        for (GlobalDecl global : this.globals)
        {
            if (global.kind == GlobalDecl.Kind.OTHER && !global.presence.isEmpty())
            {
                this.block(symbols(global.presence), "it chooses a layout");
            }

            for (String name : global.names)
            {
                byName.computeIfAbsent(global.kind + ":" + name, (k) -> new ArrayList<>()).add(global);
            }
        }

        for (List<GlobalDecl> group : byName.values())
        {
            if (group.size() > 1)
            {
                this.deduplicate(group, tainted);
            }
        }
    }

    /** Functions defined per option value: rename each and choose at runtime */
    private void dispatch(List<FunctionDef> group)
    {
        Set<String> symbols = new LinkedHashSet<>();

        for (FunctionDef function : group)
        {
            if (function.presence.isEmpty())
            {
                for (FunctionDef other : group)
                {
                    symbols.addAll(symbols(other.presence));
                }

                this.block(symbols, "a function is defined both always and only for some values");

                return;
            }

            symbols.addAll(symbols(function.presence));
        }

        FunctionDef first = group.get(0);
        String name = this.token(first.name).text;

        if (first.paramNames.contains(null))
        {
            this.block(symbols, "a function it chooses has unnamed parameters");

            return;
        }

        String args = String.join(", ", first.paramNames);
        boolean returns = !first.returnType.equals("void");
        StringBuilder dispatcher = new StringBuilder(" ").append(first.returnType).append(' ').append(name).append('(').append(first.params).append(") {");

        for (int i = 0; i < group.size(); i++)
        {
            FunctionDef function = group.get(i);
            String renamed = name + "_" + this.prefix + i;
            String call = renamed + "(" + args + ")";

            this.replace.put(function.name, renamed);

            if (i < group.size() - 1)
            {
                dispatcher.append(" if (").append(this.condition(function.presence)).append(") ");
                dispatcher.append(returns ? "return " + call + ";" : "{ " + call + "; return; }");
            }
            else
            {
                dispatcher.append(returns ? " return " + call + ";" : " " + call + ";");
            }
        }

        dispatcher.append(" }");
        this.before.merge(first.first, first.returnType + " " + name + "(" + first.params + "); ", (a, b) -> a + b);
        this.after.merge(group.get(group.size() - 1).last, dispatcher.toString(), (a, b) -> a + b);
    }

    private String condition(List<DynBranch> presence)
    {
        return presenceCondition(presence).glsl(this.prefix, true);
    }

    private static PpExpr presenceCondition(List<DynBranch> presence)
    {
        PpExpr condition = PpExpr.TRUE;

        for (DynBranch branch : presence)
        {
            condition = PpExpr.and(condition, branch.taken);
        }

        return condition;
    }

    private void deduplicate(List<GlobalDecl> group, Map<String, Set<String>> tainted)
    {
        boolean conditional = false;
        boolean identical = true;
        Set<String> symbols = new LinkedHashSet<>();

        for (GlobalDecl global : group)
        {
            conditional |= !global.presence.isEmpty();
            identical &= global.text.equals(group.get(0).text);
            symbols.addAll(symbols(global.presence));
        }

        if (!conditional)
        {
            return;
        }

        if (identical)
        {
            for (int i = 1; i < group.size(); i++)
            {
                GlobalDecl global = group.get(i);

                for (int t = global.from; t <= global.to; t++)
                {
                    this.blank.add(t);
                }
            }

            return;
        }

        GlobalDecl first = group.get(0);

        if (this.mergeable(group))
        {
            Dyn dyn = first.presence.get(first.presence.size() - 1).dyn();
            Declarator declarator = first.decl.declarators.get(0);
            String name = this.token(declarator.name).text;
            StringBuilder select = new StringBuilder();

            for (int i = 0; i < group.size() - 1; i++)
            {
                GlobalDecl global = group.get(i);

                select.append("(").append(this.condition(global.presence)).append(" ? (")
                    .append(this.join(global.decl.declarators.get(0).init)).append(") : ");
            }

            select.append("(").append(this.join(group.get(group.size() - 1).decl.declarators.get(0).init)).append(")");

            for (int i = 0; i < group.size() - 1; i++)
            {
                select.append(")");
            }

            for (GlobalDecl global : group)
            {
                for (int t = global.from; t <= global.to; t++)
                {
                    this.blank.add(t);
                }
            }

            this.endifText.computeIfAbsent(dyn, (k) -> new StringBuilder())
                .append(' ').append(this.typeText(first.decl, false)).append(' ').append(name)
                .append(this.arraysText(declarator)).append(" = ").append(select).append(';');
            tainted.merge(name, symbols, (a, b) -> { Set<String> set = new HashSet<>(a); set.addAll(b); return set; });

            return;
        }

        if (this.variants(group, symbols, tainted))
        {
            return;
        }

        this.block(symbols, "a declaration differs between its values");
    }

    /**
     * One variable declared differently per option value, like a uniform for some values and a
     * variable computing the same thing for others (Complementary's depth of field focus,
     * "centerDepthSmooth"): every variant gets declared, those Iris doesn't bind by name get
     * renamed, and each read of the name picks one at runtime. Not when the name gets written,
     * is declared anywhere else or is read through a macro.
     */
    private boolean variants(List<GlobalDecl> group, Set<String> symbols, Map<String, Set<String>> tainted)
    {
        Map<String, List<GlobalDecl>> byText = new LinkedHashMap<>();
        Set<Integer> declarators = new HashSet<>();
        String name = null;
        String type = null;
        int lastDeclarator = -1;

        for (GlobalDecl global : group)
        {
            Decl decl = global.decl;

            if (global.kind != GlobalDecl.Kind.VARIABLE || global.presence.isEmpty() || decl.iris || decl.declarators.size() != 1 || decl.declarators.get(0).unsized)
            {
                return false;
            }

            Declarator declarator = decl.declarators.get(0);
            String valueType = this.valueType(decl, declarator);

            if (type != null && !type.equals(valueType))
            {
                return false;
            }

            type = valueType;
            name = this.token(declarator.name).text;
            declarators.add(declarator.name);
            lastDeclarator = Math.max(lastDeclarator, declarator.name);
            byText.computeIfAbsent(global.text, (k) -> new ArrayList<>()).add(global);
        }

        List<List<GlobalDecl>> variants = new ArrayList<>(byText.values());
        int kept = -1;

        /* A uniform, input or output is bound by its name: only one can keep it */
        for (int i = 0; i < variants.size(); i++)
        {
            if (variants.get(i).get(0).decl.storage)
            {
                if (kept >= 0)
                {
                    return false;
                }

                kept = i;
            }
        }

        for (int index : this.declarations.getOrDefault(name, Collections.emptyList()))
        {
            if (!declarators.contains(index))
            {
                return false;
            }
        }

        List<Integer> reads = new ArrayList<>();

        for (int i = 0; i < this.events.size(); i++)
        {
            int index = this.events.get(i);

            if (!this.eventNames.get(i).equals(name) || declarators.contains(index))
            {
                continue;
            }

            if (!this.token(index).text.equals(name) || index < lastDeclarator || this.isWrite(index))
            {
                return false;
            }

            reads.add(index);
        }

        kept = Math.max(kept, 0);

        List<String> names = new ArrayList<>();
        StringBuilder select = new StringBuilder();

        for (int i = 0; i < variants.size(); i++)
        {
            names.add(i == kept ? name : name + "_" + this.prefix + i);
        }

        for (int i = 0; i < variants.size() - 1; i++)
        {
            PpExpr condition = PpExpr.FALSE;

            for (GlobalDecl global : variants.get(i))
            {
                condition = PpExpr.or(condition, presenceCondition(global.presence));
            }

            select.append("(").append(condition.glsl(this.prefix, true)).append(" ? ").append(names.get(i)).append(" : ");
        }

        select.append(names.get(variants.size() - 1));

        for (int i = 0; i < variants.size() - 1; i++)
        {
            select.append(")");
        }

        for (int i = 0; i < variants.size(); i++)
        {
            List<GlobalDecl> same = variants.get(i);

            for (int j = 1; j < same.size(); j++)
            {
                for (int t = same.get(j).from; t <= same.get(j).to; t++)
                {
                    this.blank.add(t);
                }
            }

            if (i != kept)
            {
                this.replace.put(same.get(0).decl.declarators.get(0).name, names.get(i));
            }
        }

        for (int index : reads)
        {
            this.replace.put(index, select.toString());
        }

        tainted.merge(name, symbols, (a, b) -> { Set<String> set = new HashSet<>(a); set.addAll(b); return set; });

        return true;
    }

    /** The type a variable holds, without qualifiers: "float", "vec2[4]"... */
    private String valueType(Decl decl, Declarator declarator)
    {
        StringBuilder builder = new StringBuilder(this.token(decl.type).text);

        for (List<Integer> range : decl.typeArrays)
        {
            for (int t : range)
            {
                builder.append(this.token(t).text);
            }
        }

        return builder.append(this.arraysText(declarator)).toString();
    }

    /** Whether the variable named at this token may get written there: assigned, stepped or handed to a function writing it */
    private boolean isWrite(int index)
    {
        int previous = this.significant(index, -1);

        if (previous >= 0 && (this.token(previous).is("++") || this.token(previous).is("--")))
        {
            return true;
        }

        int next = this.significant(index, 1);

        /* Through members and elements: "v.x = ...", "a[i] += ..." */
        while (next >= 0 && (this.token(next).is(".") || this.token(next).is("[")))
        {
            int skip = this.token(next).is(".") ? this.significant(next, 1) : this.closing(next);

            next = skip < 0 ? -1 : this.significant(skip, 1);
        }

        if (next >= 0 && this.token(next).type == GlslToken.Type.PUNCTUATION && ASSIGNMENTS.contains(this.token(next).text))
        {
            return true;
        }

        if (previous < 0 || next < 0 || !(this.token(previous).is("(") || this.token(previous).is(",")) || !(this.token(next).is(")") || this.token(next).is(",")))
        {
            return false;
        }

        String callee = this.callee(previous);

        if (callee == null)
        {
            return false;
        }

        if (WRITING_FUNCTIONS.contains(callee) || this.simulation.definedNames.contains(callee))
        {
            return true;
        }

        for (FunctionDef function : this.functions)
        {
            if (this.token(function.name).text.equals(callee) && OUT_PARAMETER.matcher(function.params).find())
            {
                return true;
            }
        }

        return false;
    }

    /** The function whose arguments this "(" or "," starts, null when it isn't a call */
    private String callee(int index)
    {
        int depth = 0;

        for (int i = index; i >= 0; i--)
        {
            GlslToken token = this.token(i);

            if (token.type != GlslToken.Type.PUNCTUATION)
            {
                continue;
            }

            if (token.is(")") || token.is("]") || token.is("}"))
            {
                depth += 1;
            }
            else if (token.is("(") || token.is("[") || token.is("{"))
            {
                if (depth == 0)
                {
                    int name = token.is("(") ? this.significant(i, -1) : -1;

                    return name >= 0 && this.token(name).isIdentifier() ? this.token(name).text : null;
                }

                depth -= 1;
            }
            else if (token.is(";") && depth == 0)
            {
                return null;
            }
        }

        return null;
    }

    /** The bracket closing the one at this token, -1 if there's none */
    private int closing(int open)
    {
        int depth = 0;

        for (int i = open; i < this.tokens.size(); i++)
        {
            GlslToken token = this.token(i);

            if (token.is("[") || token.is("("))
            {
                depth += 1;
            }
            else if (token.is("]") || token.is(")"))
            {
                depth -= 1;

                if (depth == 0)
                {
                    return i;
                }
            }
        }

        return -1;
    }

    /** The nearest token in this direction that's code, -1 if there's none */
    private int significant(int index, int step)
    {
        for (int i = index + step; i >= 0 && i < this.tokens.size(); i += step)
        {
            GlslToken token = this.tokens.get(i);

            if (!token.isTrivia() && token.type != GlslToken.Type.DIRECTIVE)
            {
                return i;
            }
        }

        return -1;
    }

    /** Variables with one declarator each, of the same type, in different branches of one conditional */
    private boolean mergeable(List<GlobalDecl> group)
    {
        GlobalDecl first = group.get(0);

        if (first.presence.isEmpty() || first.kind != GlobalDecl.Kind.VARIABLE)
        {
            return false;
        }

        Dyn dyn = first.presence.get(first.presence.size() - 1).dyn();
        Set<DynBranch> branches = new HashSet<>();

        for (GlobalDecl global : group)
        {
            Decl decl = global.decl;

            if (global.kind != GlobalDecl.Kind.VARIABLE || decl.storage || decl.iris || decl.declarators.size() != 1 || decl.declarators.get(0).init == null)
            {
                return false;
            }

            if (global.presence.isEmpty() || global.presence.get(global.presence.size() - 1).dyn() != dyn || !branches.add(global.presence.get(global.presence.size() - 1)))
            {
                return false;
            }

            if (!this.typeText(decl, false).equals(this.typeText(first.decl, false))
                || !this.arraysText(decl.declarators.get(0)).equals(this.arraysText(first.decl.declarators.get(0))))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Move declarations used after a converted conditional before it: before the outermost of
     * the conditionals nested right in each other's branches, as they all shared one scope.
     */
    private void resolveHoisting(Map<String, Set<String>> hoisted)
    {
        Map<StatementConversion, Map<String, List<int[]>>> declarations = new LinkedHashMap<>();
        Map<StatementConversion, Map<String, String>> texts = new HashMap<>();
        Set<StatementConversion> failed = new HashSet<>();

        for (StatementConversion conversion : this.statementConversions)
        {
            StatementConversion root = conversion;

            while (root.parent != null)
            {
                root = root.parent;
            }

            Map<String, List<int[]>> rootDeclarations = declarations.computeIfAbsent(root, (k) -> new LinkedHashMap<>());
            Map<String, String> rootTexts = texts.computeIfAbsent(root, (k) -> new HashMap<>());

            for (BranchInfo info : conversion.branches)
            {
                for (List<Integer> struct : info.structs)
                {
                    if (struct.size() > 1 && conversion.usedAfter.contains(this.token(struct.get(1)).text))
                    {
                        failed.add(conversion);
                        failed.add(root);
                    }
                }

                for (BranchDecl branchDecl : info.declarations)
                {
                    Decl decl = branchDecl.decl;
                    boolean used = false;

                    for (Declarator declarator : decl.declarators)
                    {
                        used |= conversion.usedAfter.contains(this.token(declarator.name).text);
                    }

                    if (!used)
                    {
                        continue;
                    }

                    for (Declarator declarator : decl.declarators)
                    {
                        String name = this.token(declarator.name).text;
                        String text = this.typeText(decl, true) + " " + name + this.arraysText(declarator) + ";";
                        String previous = rootTexts.putIfAbsent(name, text);

                        if (declarator.unsized || (previous != null && !previous.equals(text)))
                        {
                            failed.add(conversion);
                            failed.add(root);
                        }

                        rootDeclarations.computeIfAbsent(name, (k) -> new ArrayList<>()).add(new int[] {branchDecl.event});
                        hoisted.computeIfAbsent(name, (k) -> new LinkedHashSet<>()).addAll(conversion.dyn.symbols);

                        for (List<Integer> range : declarator.arrays)
                        {
                            this.blank.addAll(range);
                        }
                    }

                    for (int t : decl.qualifiers)
                    {
                        this.blank.add(t);
                    }

                    this.blank.add(decl.type);

                    for (List<Integer> range : decl.typeArrays)
                    {
                        this.blank.addAll(range);
                    }
                }
            }
        }

        /* One declaration per name and scope; a branch reading the name before declaring it (or
         * without declaring it) meant another variable, which the moved one would hide */
        Map<Object, Map<String, String>> declaredPerScope = new HashMap<>();

        for (Map.Entry<StatementConversion, Map<String, List<int[]>>> entry : declarations.entrySet())
        {
            StatementConversion root = entry.getKey();
            Map<String, String> declared = declaredPerScope.computeIfAbsent(root.block != null ? root.block : root, (k) -> new HashMap<>());
            StringBuilder hoist = new StringBuilder();

            for (Map.Entry<String, List<int[]>> name : entry.getValue().entrySet())
            {
                String text = texts.get(root).get(name.getKey());
                String previous = declared.putIfAbsent(name.getKey(), text);

                if (previous == null)
                {
                    hoist.append(text).append(' ');
                }
                else if (!previous.equals(text))
                {
                    failed.add(root);
                }

                if (this.readsAnother(root, name.getKey(), name.getValue()))
                {
                    failed.add(root);
                }
            }

            root.hoist = hoist.toString();
        }

        for (StatementConversion conversion : failed)
        {
            this.block(conversion.dyn.symbols, "a declaration in it can't move out of it");
        }
    }

    /** Whether a branch of the conversion reads the name where its moved declaration isn't in effect */
    private boolean readsAnother(StatementConversion root, String name, List<int[]> declarations)
    {
        for (BranchInfo info : root.branches)
        {
            int first = Integer.MAX_VALUE;

            for (int[] declaration : declarations)
            {
                if (declaration[0] >= info.start && declaration[0] < info.end)
                {
                    first = Math.min(first, declaration[0]);
                }
            }

            for (int i = info.start; i < info.end && i < first; i++)
            {
                if (this.eventNames.get(i).equals(name))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Follow which names come to depend on the options (constants initialized from them,
     * functions reading them, merged variables), drop "const" from the constants, and block the
     * options that reach places needing a constant.
     */
    private void resolveTaint(Map<String, Set<String>> tainted, Map<String, Set<String>> hoisted)
    {
        Map<String, Set<String>> functions = new HashMap<>();
        Set<Decl> deconst = new HashSet<>();
        boolean changed = true;

        while (changed)
        {
            changed = false;

            for (Decl decl : this.consts)
            {
                if (deconst.contains(decl))
                {
                    continue;
                }

                Set<String> symbols = new LinkedHashSet<>();

                for (Declarator declarator : decl.declarators)
                {
                    if (declarator.init != null)
                    {
                        this.taintOf(declarator.init, tainted, functions, symbols);
                    }
                }

                if (!symbols.isEmpty())
                {
                    deconst.add(decl);
                    changed = true;

                    for (Declarator declarator : decl.declarators)
                    {
                        tainted.computeIfAbsent(this.token(declarator.name).text, (k) -> new LinkedHashSet<>()).addAll(symbols);
                    }
                }
            }

            for (FunctionDef function : this.functions)
            {
                String name = this.token(function.name).text;

                if (functions.containsKey(name))
                {
                    continue;
                }

                Set<String> symbols = new LinkedHashSet<>();

                this.taintOf(function.body, tainted, functions, symbols);

                if (!symbols.isEmpty())
                {
                    functions.put(name, symbols);
                    changed = true;
                }
            }
        }

        for (ConstantContext context : this.constants)
        {
            Set<String> symbols = new LinkedHashSet<>();

            this.taintOf(context.tokens, tainted, functions, symbols);

            for (int index : context.tokens)
            {
                Set<String> moved = hoisted.get(this.tokens.get(index).text);

                if (moved != null)
                {
                    symbols.addAll(moved);
                }
            }

            if (!symbols.isEmpty())
            {
                this.block(symbols, context.reason);
            }
        }

        for (Decl decl : deconst)
        {
            if (!decl.iris && decl.constToken >= 0)
            {
                this.replace.put(decl.constToken, "");
            }
        }
    }

    private void taintOf(List<Integer> list, Map<String, Set<String>> tainted, Map<String, Set<String>> functions, Set<String> out)
    {
        for (int i = 0; i < list.size(); i++)
        {
            int index = list.get(i);
            Set<String> symbols = this.simulation.taint.get(index);

            if (symbols != null)
            {
                out.addAll(symbols);
            }

            GlslToken token = this.tokens.get(index);

            if (token.isIdentifier())
            {
                Set<String> name = tainted.get(token.text);

                if (name != null)
                {
                    out.addAll(name);
                }

                Set<String> function = functions.get(token.text);

                if (function != null)
                {
                    out.addAll(function);
                }
            }
        }
    }

    /* Helpers */

    private String typeText(Decl decl, boolean dropConst)
    {
        StringBuilder builder = new StringBuilder();

        for (int index : decl.qualifiers)
        {
            if (dropConst && index == decl.constToken)
            {
                continue;
            }

            builder.append(this.tokens.get(index).text).append(' ');
        }

        if (!dropConst && decl.constToken >= 0)
        {
            /* A merged variable takes its value at runtime */
            builder = new StringBuilder(builder.toString().replace("const ", ""));
        }

        builder.append(this.tokens.get(decl.type).text);

        for (List<Integer> range : decl.typeArrays)
        {
            for (int t : range)
            {
                builder.append(this.tokens.get(t).text);
            }
        }

        return builder.toString().trim();
    }

    private String arraysText(Declarator declarator)
    {
        StringBuilder builder = new StringBuilder();

        for (List<Integer> range : declarator.arrays)
        {
            for (int t : range)
            {
                builder.append(this.tokens.get(t).text);
            }
        }

        return builder.toString();
    }

    private String join(List<Integer> list)
    {
        StringBuilder builder = new StringBuilder();

        for (int index : list)
        {
            if (builder.length() > 0)
            {
                builder.append(' ');
            }

            builder.append(this.tokens.get(index).text);
        }

        return builder.toString();
    }

    private GlslToken token(int index)
    {
        return this.tokens.get(index);
    }

    /* Rollback of a failed attempt to read a conditional's branches */

    private Snapshot snapshot()
    {
        return new Snapshot(this);
    }

    private void rollback(Snapshot snapshot)
    {
        truncate(this.statementConversions, snapshot.statementConversions);
        truncate(this.globalConversions, snapshot.globalConversions);
        truncate(this.globals, snapshot.globals);
        truncate(this.functions, snapshot.functions);
        truncate(this.consts, snapshot.consts);
        truncate(this.constants, snapshot.constants);

        for (Block block : this.blocks)
        {
            truncate(block.conversions, snapshot.blockConversions.getOrDefault(block, 0));
        }

        this.blocked.keySet().retainAll(snapshot.blocked);
    }

    private static void truncate(List<?> list, int size)
    {
        while (list.size() > size)
        {
            list.remove(list.size() - 1);
        }
    }

    private static class Snapshot
    {
        final int statementConversions;
        final int globalConversions;
        final int globals;
        final int functions;
        final int consts;
        final int constants;
        final Map<Block, Integer> blockConversions = new HashMap<>();
        final Set<String> blocked;

        Snapshot(GlslSkeleton skeleton)
        {
            this.statementConversions = skeleton.statementConversions.size();
            this.globalConversions = skeleton.globalConversions.size();
            this.globals = skeleton.globals.size();
            this.functions = skeleton.functions.size();
            this.consts = skeleton.consts.size();
            this.constants = skeleton.constants.size();
            this.blocked = new HashSet<>(skeleton.blocked.keySet());

            for (Block block : skeleton.blocks)
            {
                this.blockConversions.put(block, block.conversions.size());
            }
        }
    }

    /* The cursor */

    /**
     * Walks a {@link Seq}; a conditional that can't be converted is replaced by the branch the
     * current option values take ({@link #inline}). Identifiers are recorded as they pass, and
     * calls needing a constant offset are noted.
     */
    private class Cursor
    {
        private final Deque<int[]> positions = new ArrayDeque<>();
        private final Deque<List<Object>> lists = new ArrayDeque<>();
        private int offsetDepth = -1;
        private List<Integer> offsetTokens;
        private boolean offsetPending;
        private boolean afterDot;

        Cursor(Seq seq)
        {
            this.inline(seq);
        }

        void inline(Seq seq)
        {
            this.lists.push(seq.items);
            this.positions.push(new int[] {0});
        }

        Object peek()
        {
            while (!this.lists.isEmpty())
            {
                List<Object> list = this.lists.peek();
                int[] position = this.positions.peek();

                if (position[0] < list.size())
                {
                    return list.get(position[0]);
                }

                this.lists.pop();
                this.positions.pop();
            }

            return null;
        }

        Object next()
        {
            Object item = this.peek();

            if (item != null)
            {
                this.positions.peek()[0] += 1;

                if (item instanceof Integer)
                {
                    this.consume((Integer) item);
                }
            }

            return item;
        }

        private void consume(int index)
        {
            GlslToken token = GlslSkeleton.this.tokens.get(index);

            GlslSkeleton.this.last = index;

            /* A member after a dot isn't a use of a variable of that name */
            if (token.isIdentifier() && !this.afterDot)
            {
                GlslSkeleton.this.event(index, token.text);

                Set<String> expanded = GlslSkeleton.this.simulation.expansions.get(index);

                if (expanded != null)
                {
                    for (String name : expanded)
                    {
                        GlslSkeleton.this.event(index, name);
                    }
                }
            }

            this.afterDot = token.is(".");

            if (this.offsetTokens != null)
            {
                if (token.is("("))
                {
                    this.offsetDepth += 1;
                }
                else if (token.is(")"))
                {
                    this.offsetDepth -= 1;

                    if (this.offsetDepth == 0)
                    {
                        GlslSkeleton.this.constants.add(new ConstantContext(this.offsetTokens, "it's a texture offset", GlslSkeleton.this.presence));
                        this.offsetTokens = null;

                        return;
                    }
                }

                this.offsetTokens.add(index);

                return;
            }

            if (this.offsetPending && token.is("("))
            {
                this.offsetTokens = new ArrayList<>();
                this.offsetDepth = 1;
            }

            this.offsetPending = token.isIdentifier() && OFFSET_FUNCTIONS.contains(token.text);
        }
    }

    private static class Unstructured extends RuntimeException
    {
        static final Unstructured INSTANCE = new Unstructured();

        private Unstructured()
        {
            super(null, null, false, false);
        }
    }

    /* Records */

    private static class Block
    {
        final List<StatementConversion> conversions = new ArrayList<>();
    }

    static class StatementConversion
    {
        final Dyn dyn;

        /** The whole body of an if/else/for/while/do, rather than a statement in a block */
        final boolean sole;
        final List<BranchInfo> branches = new ArrayList<>();

        /** The conversion this one sits in, right in one of its branches */
        StatementConversion parent;
        int after;
        final Set<String> usedAfter = new HashSet<>();
        Block block;

        /** Declarations that go before the "if" */
        String hoist = "";

        StatementConversion(Dyn dyn, boolean sole)
        {
            this.dyn = dyn;
            this.sole = sole;
        }
    }

    static class BranchInfo
    {
        final DynBranch branch;
        final int start;
        final StatementConversion owner;
        int end;
        boolean openIf;
        final List<BranchDecl> declarations = new ArrayList<>();
        final List<List<Integer>> structs = new ArrayList<>();

        BranchInfo(DynBranch branch, int start, StatementConversion owner)
        {
            this.branch = branch;
            this.start = start;
            this.owner = owner;
        }
    }

    private static class BranchDecl
    {
        final Decl decl;
        final int event;

        BranchDecl(Decl decl, int event)
        {
            this.decl = decl;
            this.event = event;
        }
    }

    private static class Decl
    {
        final List<Integer> qualifiers = new ArrayList<>();
        int type = -1;
        final List<List<Integer>> typeArrays = new ArrayList<>();
        final List<Declarator> declarators = new ArrayList<>();
        boolean isConst;
        int constToken = -1;

        /** Has a storage qualifier (uniform, in, out...) */
        boolean storage;
        boolean prototype;
        boolean iris;

        /** Interface block name */
        String block;
    }

    private static class Declarator
    {
        int name;
        final List<List<Integer>> arrays = new ArrayList<>();
        boolean unsized;
        List<Integer> init;
    }

    private static class GlobalDecl
    {
        enum Kind
        {
            VARIABLE, STRUCT, BLOCK, PROTOTYPE, OTHER
        }

        Kind kind;
        int from;
        int to;
        String text;
        List<DynBranch> presence;
        final List<String> names = new ArrayList<>();
        Decl decl;
    }

    private static class FunctionDef
    {
        int first;
        int name;
        int last;
        String returnType;
        String params;
        List<String> paramNames;
        String signature;
        List<DynBranch> presence;
        List<Integer> body;
    }

    private static class ConstantContext
    {
        final List<Integer> tokens;
        final String reason;
        final List<DynBranch> presence;

        ConstantContext(List<Integer> tokens, String reason, List<DynBranch> presence)
        {
            this.tokens = new ArrayList<>(tokens);
            this.reason = reason;
            this.presence = new ArrayList<>(presence);
        }
    }
}
