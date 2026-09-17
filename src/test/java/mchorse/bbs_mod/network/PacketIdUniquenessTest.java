package mchorse.bbs_mod.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * No two network channels may share an id.
 *
 * <p>Channels are named {@code "c19"}, {@code "s15"} and so on - a counter, handed out by whoever
 * adds a packet next. Upstream and this fork both add packets, and on the 31 August merge both had
 * independently taken c19, c20 and s15: upstream for the structure wand, the fork for crowds.</p>
 *
 * <p>That collision does not fail to build and does not throw. The later registration wins the
 * channel and the other packet is simply never delivered - a crowd that never populates, with
 * nothing in the log to say why. Cheap to prevent, expensive to find.</p>
 *
 * <p>Read from the source rather than from the constants, because loading {@code ServerNetwork}
 * drags in Minecraft's registries; the ids are string literals and the point is that no two of
 * them match.</p>
 */
public class PacketIdUniquenessTest
{
    private static final Path SOURCE =
        Path.of("src/main/java/mchorse/bbs_mod/network/ServerNetwork.java");

    /** {@code NAME = new Identifier(BBSMod.MOD_ID, "c19")} */
    private static final Pattern DECL = Pattern.compile(
        "Identifier\\s+(\\w+)\\s*=\\s*new Identifier\\(\\s*BBSMod\\.MOD_ID\\s*,\\s*\"([^\"]+)\"");

    private static Map<String, List<String>> byId() throws IOException
    {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        Matcher m = DECL.matcher(source);
        Map<String, List<String>> ids = new LinkedHashMap<>();

        while (m.find())
        {
            ids.computeIfAbsent(m.group(2), (k) -> new ArrayList<>()).add(m.group(1));
        }

        return ids;
    }

    @Test
    public void everyChannelIdIsUsedOnce() throws IOException
    {
        List<String> clashes = new ArrayList<>();

        for (Map.Entry<String, List<String>> e : byId().entrySet())
        {
            if (e.getValue().size() > 1)
            {
                clashes.add('"' + e.getKey() + "\" is claimed by " + String.join(" and ", e.getValue()));
            }
        }

        if (!clashes.isEmpty())
        {
            fail("two packets on one channel - the later registration wins and the other never "
                + "arrives:\n  " + String.join("\n  ", clashes));
        }
    }

    @Test
    public void theFileStillLooksLikeWeThinkItDoes()
    {
        /* Negative control for the parse: a regex that silently matched nothing would make the
         * test above pass no matter how badly the ids collided. */
        try
        {
            Map<String, List<String>> ids = byId();

            assertTrue(ids.size() > 20, "only found " + ids.size() + " packet ids; the declaration "
                + "shape in ServerNetwork must have changed, and this test is no longer reading it");
            assertTrue(ids.containsKey("c1"), "expected the c-series; found " + ids.keySet());
            assertTrue(ids.containsKey("s1"), "expected the s-series; found " + ids.keySet());
        }
        catch (IOException e)
        {
            fail("could not read " + SOURCE.toAbsolutePath() + ": " + e);
        }
    }
}
