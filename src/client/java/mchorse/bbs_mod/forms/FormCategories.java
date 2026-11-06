package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.sections.ExtraFormSection;
import mchorse.bbs_mod.forms.sections.FormSection;
import mchorse.bbs_mod.forms.sections.ModelFormSection;
import mchorse.bbs_mod.forms.sections.ParticleFormSection;
import mchorse.bbs_mod.forms.sections.RecentFormSection;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FormCategories implements IWatchDogListener
{
    /**
     * Sections an addon added to the palette.
     *
     * <p>Factories rather than sections, because {@link #setup()} runs again on every asset
     * reload and rebuilds its list from scratch — a section handed over once would be thrown
     * away the first time the user touched a file.</p>
     */
    private static final List<Function<FormCategories, FormSection>> EXTRA_SECTIONS = new ArrayList<>();

    public final CategoryPreferences preferences = new CategoryPreferences();

    private List<FormSection> sections = new ArrayList<>();
    private RecentFormSection recentForms = new RecentFormSection(this);
    private UserFormSection userForms = new UserFormSection(this);

    private long lastUpdate;

    /**
     * Adds a section — a top-level tab — to the form palette.
     *
     * <p>Without this, an addon's forms worked but had nowhere to be picked from: the list of
     * sections was private and built from scratch in {@link #setup()}.</p>
     */
    public static void registerSection(Function<FormCategories, FormSection> factory)
    {
        EXTRA_SECTIONS.add(factory);
    }

    /* Setup */

    public void setup()
    {
        this.setup(true);
    }

    /** Rebuild asset sections without re-reading user categories whose saves may still be queued. */
    public void reloadAssets()
    {
        this.setup(false);
    }

    private void setup(boolean reloadUserForms)
    {
        this.sections.clear();
        this.sections.add(this.recentForms);
        this.sections.add(this.userForms);
        this.sections.add(new ModelFormSection(this));
        this.sections.add(new ParticleFormSection(this));
        this.sections.add(new ExtraFormSection(this));

        for (Function<FormCategories, FormSection> factory : EXTRA_SECTIONS)
        {
            this.sections.add(factory.apply(this));
        }

        for (FormSection section : this.sections)
        {
            if (reloadUserForms || (section != this.recentForms && section != this.userForms))
            {
                section.initiate();
            }
        }

        this.markDirty();
        this.preferences.read();
    }

    public long getLastUpdate()
    {
        return lastUpdate;
    }

    public void markDirty()
    {
        this.lastUpdate = System.currentTimeMillis();
    }

    public RecentFormSection getRecentForms()
    {
        return this.recentForms;
    }

    public UserFormSection getUserForms()
    {
        return this.userForms;
    }

    public List<FormCategory> getAllCategories()
    {
        return this.getAllCategories(true, true);
    }

    public List<FormCategory> getAllCategories(boolean includeModels, boolean includeParticles)
    {
        /* Fixed palette order: user forms (actors) on top, Misc ALWAYS second, then players
         * (models), then mobs, then particles, then addon sections, recent forms last. */
        List<FormCategory> formCategories = new ArrayList<>();
        ExtraFormSection extra = null;
        ModelFormSection models = null;
        ParticleFormSection particles = null;
        List<FormSection> others = new ArrayList<>();

        for (FormSection section : this.sections)
        {
            if (section instanceof ExtraFormSection s) extra = s;
            else if (section instanceof ModelFormSection s) models = s;
            else if (section instanceof ParticleFormSection s) particles = s;
            else if (section != this.userForms && section != this.recentForms) others.add(section);
        }

        formCategories.addAll(this.userForms.getCategories());

        if (extra != null) formCategories.add(extra.getExtraCategory());
        if (includeModels && models != null) formCategories.addAll(models.getCategories());
        if (extra != null) formCategories.addAll(extra.getMobCategories());
        if (includeParticles && particles != null) formCategories.addAll(particles.getCategories());

        for (FormSection section : others) formCategories.addAll(section.getCategories());

        formCategories.addAll(this.recentForms.getCategories());

        return formCategories;
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        for (FormSection section : this.sections)
        {
            section.accept(path, event);
        }
    }
}
