package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormArchitect;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the wiring the crowd form needs to be more than a class that exists.
 *
 * <p>None of this needs Minecraft running: registration, serialisation and the keyframe channel
 * table are all plain data. What it cannot speak for is whether a crowd then walks correctly on
 * screen - that is the game's business, and this asserts nothing about it.</p>
 */
public class CrowdFormTest
{
    /** The id {@code BBSMod} registers the form under; a film on disk names this string. */
    private static final Link CROWD = Link.bbs("crowd");

    /**
     * {@link Form}'s constructor sizes its overlay list from a setting, so no form of any kind can
     * be built before the settings fields exist. In the game {@code BBSMod} does this; here it has
     * to be done by hand. Nothing is written - the file is only somewhere for the builder to point.
     */
    @BeforeAll
    public static void fillInTheSettings()
    {
        BBSSettings.register(new SettingsBuilder(null, "bbs", new File("build/tmp/bbs-test-settings.json")));

        /* The registry is filled by an explicit call now, not a static initialiser, so a test that
         * reads FACTORIES has to make that call itself - in the game BBSMod does it. */
        KeyframeFactories.setup();
    }

    private static FormArchitect architect()
    {
        FormArchitect forms = new FormArchitect();

        forms.register(CROWD, CrowdForm.class, null);

        return forms;
    }

    /**
     * A film on disk holds the form as the id plus its two flat keys, so that map is built here by
     * hand rather than by asking a form to write itself. {@link Form#toData()} stamps the id via
     * {@code BBSMod.getForms()}, whose static setup wants Minecraft's registries bootstrapped, and
     * that path is common to every form rather than anything this port changed. What is worth
     * pinning down is the half that is new: that {@code bbs:crowd} finds this class again, and
     * that the two values come back off the map intact.
     */
    @Test
    public void savedCrowdFormLoadsBackThroughTheRegistry()
    {
        FormArchitect forms = architect();
        MapType data = new MapType();

        data.putString("id", CROWD.toString());
        data.putString("crowd", "villagers");
        data.putBool("keep_behavior", false);

        Form read = forms.fromData(data);

        assertNotNull(read, "bbs:crowd resolved to nothing - the form is not registered");
        assertInstanceOf(CrowdForm.class, read, "the registry rebuilt the wrong type");

        CrowdForm crowd = (CrowdForm) read;

        assertEquals("villagers", crowd.crowd.get(), "the crowd tag did not survive the load");
        assertEquals(false, crowd.keepBehavior.get(), "keep-behaviour did not survive the load");

        /* A different tag has to give a different form, or every replay would drive one crowd. */
        MapType other = new MapType();

        other.putString("id", CROWD.toString());
        other.putString("crowd", "guards");

        Form second = forms.fromData(other);

        assertNotSame(read, second, "the registry handed back the same instance twice");
        assertEquals("guards", ((CrowdForm) second).crowd.get());
        assertEquals(true, ((CrowdForm) second).keepBehavior.get(),
            "a map with no keep_behavior key should leave the default alone");
    }

    @Test
    public void aFreshFormKeepsTheDefaultsTheEditorRelieOn()
    {
        CrowdForm form = new CrowdForm();

        assertEquals("", form.crowd.get(), "a new form must drive nothing until pointed at a crowd");
        assertEquals(true, form.keepBehavior.get(), "behaviour clips keep steering by default");
    }

    @Test
    public void displayNameFallsBackToTheCrowdTag()
    {
        CrowdForm form = new CrowdForm();

        assertEquals("Crowd", form.getDisplayName(), "an unpointed form should still read as Crowd");

        form.crowd.set("guards");

        assertEquals("guards", form.getDisplayName(), "a pointed form should read as its crowd");
    }

    @Test
    public void everyCrowdChannelResolvesByItsSerialisedName()
    {
        /* These strings are what a saved film carries. If one stops resolving, the channel loads
         * as nothing and the keyframes on it are quietly dropped. */
        assertSame(KeyframeFactories.CROWD_LOOK_TARGET, KeyframeFactories.FACTORIES.get("crowd_look_target"));
        assertSame(KeyframeFactories.CROWD_JUMP, KeyframeFactories.FACTORIES.get("crowd_jump"));
        assertSame(KeyframeFactories.CROWD_WALK, KeyframeFactories.FACTORIES.get("crowd_motion_path"));
        assertSame(KeyframeFactories.CROWD_TEXTURE, KeyframeFactories.FACTORIES.get("crowd_texture"));
        assertSame(KeyframeFactories.CROWD_BEHAVIOR, KeyframeFactories.FACTORIES.get("crowd_behavior"));
    }

    @Test
    public void aReplayCarriesTheCrowdChannels()
    {
        ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");

        assertNotNull(keyframes.crowdVisible, "visible channel missing from the replay");
        assertNotNull(keyframes.crowdLookTarget, "look-target channel missing from the replay");
        assertNotNull(keyframes.crowdBehavior, "behaviour channel missing from the replay");
        assertNotNull(keyframes.crowdJump, "jump channel missing from the replay");
        assertNotNull(keyframes.crowdWalk, "walk channel missing from the replay");
        assertNotNull(keyframes.crowdTexture, "texture channel missing from the replay");
        assertNotNull(keyframes.crowdColor, "colour channel missing from the replay");

        assertEquals("crowd_visible", keyframes.crowdVisible.getId());
        /* The walk channel's id is deliberately not "crowd_walk" - films were written with
         * "crowd_motion_path" before the feature was renamed, and they still have to load. */
        assertEquals("crowd_motion_path", keyframes.crowdWalk.getId());

        for (String id : ReplayKeyframes.CROWD_CHANNELS)
        {
            assertTrue(KeyframeFactories.FACTORIES.containsKey(id) || id.equals("crowd_color") || id.equals("crowd_visible"),
                "channel " + id + " is advertised but has no factory behind it");
        }
    }
}
