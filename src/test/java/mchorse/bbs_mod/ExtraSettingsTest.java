package mchorse.bbs_mod;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every switch this fork adds actually reaches the settings screen.
 *
 * <p>Worth a test because of how it failed: {@code SettingsBuilder#category} does
 * {@code categories.put(id, new ValueGroup(id))}, so naming a category a second time replaces the
 * first group with an empty one and quietly drops everything already registered into it. A merge
 * left three {@code category("extra")} calls in a row and all but the last switch vanished from the
 * screen - while still compiling, still passing every other test, and still being a field that very
 * much existed if you asked the class for it.</p>
 */
public class ExtraSettingsTest
{
    private static ValueGroup extra;

    @BeforeAll
    public static void register()
    {
        SettingsBuilder builder = new SettingsBuilder(null, "bbs", new File("build/tmp/bbs-settings-test.json"));

        BBSSettings.register(builder);

        extra = builder.getConfig().categories.get("extra");
    }

    /** Every id the Extra category is supposed to carry, as the settings file spells them. */
    private static final List<String> EXPECTED = List.of(
        "crowd_preview_count",
        "creative_show_hearts",
        "creative_show_hunger",
        "creative_show_xp_bar",
        "sprint_particles",
        "recording_armor",
        "orbit_attach_rotates"
    );

    private static List<String> idsIn(ValueGroup group)
    {
        List<String> ids = new ArrayList<>();

        for (BaseValue value : group.getAll())
        {
            ids.add(value.getId());
        }

        return ids;
    }

    @Test
    public void theExtraCategoryExists()
    {
        assertNotNull(extra, "there is no 'extra' category at all - nothing this fork adds is reachable");
    }

    @Test
    public void everySwitchIsInIt()
    {
        List<String> ids = idsIn(extra);

        for (String id : EXPECTED)
        {
            assertTrue(ids.contains(id), id + " is missing from the Extra category - it was registered "
                + "into a group that a later category() call threw away. Found: " + ids);
        }
    }

    @Test
    public void theFieldsAreActuallyBound()
    {
        /* A field left null is a switch that reads as false forever and throws the moment
         * anything asks it a question. */
        assertNotNull(BBSSettings.crowdPreviewCount);
        assertNotNull(BBSSettings.creativeShowHearts);
        assertNotNull(BBSSettings.creativeShowHunger);
        assertNotNull(BBSSettings.creativeShowXpBar);
        assertNotNull(BBSSettings.sprintParticles);
        assertNotNull(BBSSettings.recordingArmor);
        assertNotNull(BBSSettings.orbitAttachRotates);
    }

    @Test
    public void recordingArmourDefaultsToOn()
    {
        /* Off would silently change what every existing recording captures. */
        assertEquals(true, BBSSettings.recordingArmor.get());
    }

    @Test
    public void orbitFollowsTheReplaysRotationByDefault()
    {
        assertEquals(true, BBSSettings.orbitAttachRotates.get());
    }
}
