package mchorse.bbs_mod.utils.glsl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Names GLSL itself provides (keywords, built-in functions of every version, compatibility
 * profile included), so a name used in code that the shader doesn't declare can be told apart
 * from one the shader would miss.
 */
class GlslBuiltins
{
    private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
        /* Keywords */
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "discard",
        "true", "false", "struct", "const", "uniform", "in", "out", "inout", "attribute", "varying", "layout", "buffer",
        "shared", "flat", "smooth", "noperspective", "centroid", "sample", "patch", "invariant", "precise", "highp",
        "mediump", "lowp", "precision", "coherent", "volatile", "restrict", "readonly", "writeonly", "subroutine",
        /* Functions */
        "radians", "degrees", "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "pow", "exp", "log", "exp2", "log2", "sqrt", "inversesqrt", "abs", "sign", "floor", "trunc", "round", "roundEven",
        "ceil", "fract", "mod", "modf", "min", "max", "clamp", "mix", "step", "smoothstep", "isnan", "isinf",
        "floatBitsToInt", "floatBitsToUint", "intBitsToFloat", "uintBitsToFloat", "fma", "frexp", "ldexp",
        "packUnorm2x16", "packSnorm2x16", "packUnorm4x8", "packSnorm4x8", "unpackUnorm2x16", "unpackSnorm2x16",
        "unpackUnorm4x8", "unpackSnorm4x8", "packHalf2x16", "unpackHalf2x16", "packDouble2x32", "unpackDouble2x32",
        "length", "distance", "dot", "cross", "normalize", "faceforward", "reflect", "refract", "ftransform",
        "matrixCompMult", "outerProduct", "transpose", "determinant", "inverse",
        "lessThan", "lessThanEqual", "greaterThan", "greaterThanEqual", "equal", "notEqual", "any", "all", "not",
        "uaddCarry", "usubBorrow", "umulExtended", "imulExtended", "bitfieldExtract", "bitfieldInsert", "bitfieldReverse",
        "bitCount", "findLSB", "findMSB",
        "textureSize", "textureQueryLod", "textureQueryLevels", "textureSamples", "texture", "textureProj", "textureLod",
        "textureOffset", "texelFetch", "texelFetchOffset", "textureProjOffset", "textureLodOffset", "textureProjLod",
        "textureProjLodOffset", "textureGrad", "textureGradOffset", "textureProjGrad", "textureProjGradOffset",
        "textureGather", "textureGatherOffset", "textureGatherOffsets",
        "texture1D", "texture1DProj", "texture1DLod", "texture1DProjLod", "texture2D", "texture2DProj", "texture2DLod",
        "texture2DProjLod", "texture3D", "texture3DProj", "texture3DLod", "texture3DProjLod", "textureCube", "textureCubeLod",
        "shadow1D", "shadow2D", "shadow1DProj", "shadow2DProj", "shadow1DLod", "shadow2DLod", "shadow1DProjLod",
        "shadow2DProjLod", "texture2DRect", "texture2DRectProj", "shadow2DRect", "shadow2DRectProj",
        "atomicCounterIncrement", "atomicCounterDecrement", "atomicCounter", "atomicAdd", "atomicMin", "atomicMax",
        "atomicAnd", "atomicOr", "atomicXor", "atomicExchange", "atomicCompSwap",
        "imageSize", "imageSamples", "imageLoad", "imageStore", "imageAtomicAdd", "imageAtomicMin", "imageAtomicMax",
        "imageAtomicAnd", "imageAtomicOr", "imageAtomicXor", "imageAtomicExchange", "imageAtomicCompSwap",
        "dFdx", "dFdy", "dFdxFine", "dFdyFine", "dFdxCoarse", "dFdyCoarse", "fwidth", "fwidthFine", "fwidthCoarse",
        "interpolateAtCentroid", "interpolateAtSample", "interpolateAtOffset", "noise1", "noise2", "noise3", "noise4",
        "EmitStreamVertex", "EndStreamPrimitive", "EmitVertex", "EndPrimitive", "barrier", "memoryBarrier",
        "memoryBarrierAtomicCounter", "memoryBarrierBuffer", "memoryBarrierShared", "memoryBarrierImage",
        "groupMemoryBarrier", "anyInvocation", "allInvocations", "allInvocationsEqual"
    ));

    static boolean contains(String name)
    {
        if (NAMES.contains(name) || GlslSkeleton.isType(name) || name.startsWith("gl_"))
        {
            return true;
        }

        /* Extension functions and subgroup operations */
        return name.endsWith("ARB") || name.endsWith("EXT") || name.endsWith("NV") || name.endsWith("AMD")
            || name.startsWith("subgroup") || name.startsWith("__");
    }
}
