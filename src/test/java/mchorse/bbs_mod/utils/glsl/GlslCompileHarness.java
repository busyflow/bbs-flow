package mchorse.bbs_mod.utils.glsl;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manual check of {@link GlslDynamicOptions} with the real GPU driver: every program of a pack
 * is compiled and linked as it is and after the transformation (through JCPP like Iris does),
 * in a hidden OpenGL 4.6 compatibility context. Reports programs the transformation breaks and
 * how much longer they take to build.
 *
 * <p>Needs LWJGL (with its natives) on the class path. Arguments: shader pack zips or folders,
 * {@code --set NAME=value} to set an option as the menu would.</p>
 */
public class GlslCompileHarness
{
    /** Where to write the preprocessed sources of programs that break, if anywhere */
    private static Path dump;

    /** Print why originals that don't build here fail */
    private static boolean originals;

    public static void main(String[] args) throws Exception
    {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit())
        {
            throw new IllegalStateException("GLFW failed to start");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 6);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);

        long window = GLFW.glfwCreateWindow(64, 64, "glsl", 0, 0);

        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL " + GL20.glGetString(GL20.GL_VERSION) + " / " + GL20.glGetString(GL20.GL_RENDERER));

        int broken = 0;

        for (int i = 0; i < args.length; i++)
        {
            if (args[i].equals("--dump"))
            {
                dump = Paths.get(args[++i]);
            }
            else if (args[i].equals("--originals"))
            {
                originals = true;
            }
            else if (args[i].equals("--set"))
            {
                String[] pair = args[++i].split("=", 2);

                GlslPackHarness.values.put(pair[0], pair[1]);
            }
            else
            {
                broken += run(Paths.get(args[i]));
            }
        }

        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println(broken == 0 ? "OK" : broken + " programs broke");
    }

    private static int run(Path pack) throws Exception
    {
        Map<String, String> files = GlslPackHarness.read(pack);
        Map<String, GlslOption> options = GlslPackHarness.options(files);
        String[][] environment = GlslPackHarness.environment();
        List<String[]> env = new ArrayList<>();
        Map<String, List<String>> programs = new TreeMap<>();
        Map<String, String> blocked = new HashMap<>(GlslPackHarness.properties(files, options));

        for (String[] define : environment)
        {
            env.add(define);
        }

        for (String path : files.keySet())
        {
            if (path.matches("/(world-?\\d+/)?\\w+\\.(vsh|fsh|gsh|csh|tcs|tes)"))
            {
                programs.computeIfAbsent(path.substring(0, path.lastIndexOf('.')), (k) -> new ArrayList<>()).add(path);
            }
        }

        System.out.println("== " + pack.getFileName() + ": " + programs.size() + " programs");

        /* Transform everything first, as Iris loads the whole pack before building programs */
        Map<String, String[]> sources = new HashMap<>();

        for (List<String> stages : programs.values())
        {
            for (String stage : stages)
            {
                String source = GlslPackHarness.include(files, stage, new HashSet<>());
                Map<String, GlslOption> available = new HashMap<>(options);

                available.keySet().removeAll(blocked.keySet());

                GlslDynamicOptions.Result result = GlslDynamicOptions.transform(source, env, available, "bbs_");

                result.blocked.forEach(blocked::putIfAbsent);
                sources.put(stage, new String[] {
                    upgrade(GlslDynamicOptionsTest.preprocess(source, environment)),
                    upgrade(GlslDynamicOptionsTest.preprocess(result.source, environment))
                });
            }
        }

        long originalTime = 0;
        long transformedTime = 0;
        int built = 0;
        int skipped = 0;
        int broke = 0;

        for (Map.Entry<String, List<String>> program : programs.entrySet())
        {
            long start = System.nanoTime();
            String original = build(program.getValue(), sources, 0);
            long middle = System.nanoTime();
            String transformed = build(program.getValue(), sources, 1);
            long end = System.nanoTime();

            if (original != null)
            {
                skipped += 1;

                if (originals)
                {
                    System.out.println("  original doesn't build " + program.getKey() + ":\n" + indent(original));
                }

                continue;
            }

            built += 1;
            originalTime += middle - start;
            transformedTime += end - middle;

            if (transformed != null)
            {
                broke += 1;
                System.out.println("  BROKE " + program.getKey() + ":\n" + indent(transformed));

                if (dump != null)
                {
                    for (String stage : program.getValue())
                    {
                        Path out = dump.resolve(pack.getFileName().toString()).resolve(stage.substring(1));

                        Files.createDirectories(out.getParent());
                        Files.write(out.resolveSibling(out.getFileName() + ".pp"), sources.get(stage)[1].getBytes(StandardCharsets.UTF_8));
                        Files.write(out.resolveSibling(out.getFileName() + ".original.pp"), sources.get(stage)[0].getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }

        System.out.println("  built " + built + ", originals that don't build here " + skipped + ", broken by the transform " + broke);
        System.out.println("  build time " + originalTime / 1_000_000 + " ms -> " + transformedTime / 1_000_000 + " ms");

        return broke;
    }

    /**
     * Iris raises every program to at least GLSL 4.10 (core, with its own compatibility
     * patches); a compatibility profile 4.10 keeps the old built-ins working without them.
     */
    private static String upgrade(String source)
    {
        Matcher matcher = Pattern.compile("#version\\s+(\\d+)[^\\n]*").matcher(source);

        /* A comment unique to this run keeps the driver's shader cache out of the timings */
        if (matcher.find())
        {
            String version = Integer.parseInt(matcher.group(1)) < 410 ? "#version 410 compatibility" : matcher.group();

            return source.substring(0, matcher.start()) + version + "\n// " + SALT + source.substring(matcher.end());
        }

        return source;
    }

    private static final String SALT = Long.toHexString(System.nanoTime());

    /** Compile and link the stages; null when that works, the logs otherwise */
    private static String build(List<String> stages, Map<String, String[]> sources, int which)
    {
        int program = GL20.glCreateProgram();
        List<Integer> shaders = new ArrayList<>();
        StringBuilder errors = new StringBuilder();

        for (String stage : stages)
        {
            int shader = GL20.glCreateShader(type(stage));

            GL20.glShaderSource(shader, sources.get(stage)[which]);
            GL20.glCompileShader(shader);

            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
            {
                errors.append(stage).append(": ").append(first(GL20.glGetShaderInfoLog(shader))).append('\n');
            }

            GL20.glAttachShader(program, shader);
            shaders.add(shader);
        }

        if (errors.length() == 0)
        {
            GL20.glLinkProgram(program);

            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
            {
                errors.append("link: ").append(first(GL20.glGetProgramInfoLog(program))).append('\n');
            }
        }

        for (int shader : shaders)
        {
            GL20.glDeleteShader(shader);
        }

        GL20.glDeleteProgram(program);

        return errors.length() == 0 ? null : errors.toString();
    }

    private static int type(String path)
    {
        if (path.endsWith(".vsh")) return GL20.GL_VERTEX_SHADER;
        if (path.endsWith(".fsh")) return GL20.GL_FRAGMENT_SHADER;
        if (path.endsWith(".gsh")) return GL32.GL_GEOMETRY_SHADER;
        if (path.endsWith(".tcs")) return GL40.GL_TESS_CONTROL_SHADER;
        if (path.endsWith(".tes")) return GL40.GL_TESS_EVALUATION_SHADER;

        return GL43.GL_COMPUTE_SHADER;
    }

    /** The first errors of a log (warnings left out) */
    private static String first(String log)
    {
        StringBuilder builder = new StringBuilder();
        int count = 0;

        for (String line : log.split("\n"))
        {
            if (line.contains("error") && count < 6)
            {
                builder.append(line).append('\n');
                count += 1;
            }
        }

        return builder.length() == 0 ? log.trim() : builder.toString().trim();
    }

    private static String indent(String text)
    {
        return "    " + text.trim().replace("\n", "\n    ");
    }
}
