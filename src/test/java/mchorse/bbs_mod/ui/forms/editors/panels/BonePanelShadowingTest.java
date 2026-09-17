package mchorse.bbs_mod.ui.forms.editors.panels;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * No bone panel may re-declare a field its base class already owns.
 *
 * <p>The three bone panels - IK, physics, constraints - were given a shared base that builds the
 * bone list and assigns it in its constructor. A subclass that also declares {@code bones} does
 * not get a second list: it gets a second FIELD, left null, and shadowing means the subclass's
 * own code reads the null one. The panel then dies in its constructor, and only when a person
 * clicks Edit on a model form - the compiler is perfectly happy, since both fields exist and both
 * have the right type.</p>
 *
 * <p>Written as a sweep over the hierarchy rather than a check for the one field that broke,
 * because the base is new and still gaining fields; every field moved up is another chance to
 * leave the old declaration behind.</p>
 */
public class BonePanelShadowingTest
{
    private static final String[] PANELS = {
        "mchorse.bbs_mod.ui.forms.editors.panels.UIModelIKFormPanel",
        "mchorse.bbs_mod.ui.forms.editors.panels.UIModelPhysicsFormPanel",
        "mchorse.bbs_mod.ui.forms.editors.panels.UIModelConstraintsFormPanel"
    };

    /** Loaded without running static initialisers - the class shape is all this needs. */
    private static Class<?> load(String name) throws ClassNotFoundException
    {
        return Class.forName(name, false, BonePanelShadowingTest.class.getClassLoader());
    }

    @Test
    public void noBonePanelShadowsAnInheritedField() throws Exception
    {
        List<String> problems = new ArrayList<>();

        for (String name : PANELS)
        {
            Class<?> panel = load(name);
            Map<String, Field> inherited = new HashMap<>();

            for (Class<?> c = panel.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass())
            {
                for (Field f : c.getDeclaredFields())
                {
                    inherited.putIfAbsent(f.getName(), f);
                }
            }

            for (Field f : panel.getDeclaredFields())
            {
                Field base = inherited.get(f.getName());

                /* Same name AND same type is the signature of a field moved into the base with
                 * the old declaration left behind - the base fills its copy, the subclass reads
                 * its own, and the subclass's is null.
                 *
                 * Same name, DIFFERENT type is not that. All three panels have a UIToggle called
                 * `enabled` over UIElement's `protected boolean enabled`, which has always been
                 * there and works: each class's code resolves to the field of the type it expects.
                 * Ugly, but flagging it would mean this test cries wolf from the day it is written,
                 * and a test that always fails is a test nobody reads. */
                if (base != null && base.getType() == f.getType())
                {
                    problems.add(panel.getSimpleName() + " re-declares '" + f.getName()
                        + "' (" + f.getType().getSimpleName() + "), already owned by "
                        + base.getDeclaringClass().getSimpleName());
                }
            }
        }

        if (!problems.isEmpty())
        {
            fail("shadowed fields will read as null however the base fills them:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    public void theBaseStillOwnsTheBoneList() throws Exception
    {
        /* A negative control for the sweep above: if the base ever stops declaring these, the
         * test would pass by vacuum rather than by the panels being correct. */
        Class<?> base = load("mchorse.bbs_mod.ui.forms.editors.panels.UIBoneListFormPanel");
        List<String> declared = new ArrayList<>();

        for (Field f : base.getDeclaredFields())
        {
            declared.add(f.getName());
        }

        assertTrue(declared.contains("bones"), "UIBoneListFormPanel no longer declares 'bones': " + declared);
        assertTrue(declared.contains("bonesSearch"), "UIBoneListFormPanel no longer declares 'bonesSearch': " + declared);
    }

    @Test
    public void everyPanelActuallyDescendsFromThatBase() throws Exception
    {
        Class<?> base = load("mchorse.bbs_mod.ui.forms.editors.panels.UIBoneListFormPanel");

        for (String name : PANELS)
        {
            assertTrue(base.isAssignableFrom(load(name)), name + " is no longer a bone panel");
        }
    }
}
