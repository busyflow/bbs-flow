package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.actions.types.area.ValueAreaCells;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * A crowd the film owns: who they are, where they stand, and when they exist.
 *
 * <p>This used to be an action clip, which made a crowd an event - something that happens at a
 * moment and is gone from the model afterwards. That was the wrong shape for it. A crowd is not
 * an event; it is a group of people who are present over a stretch of the film, and describing
 * it as a firing has cost real bugs: a spawn that was missed for any reason left no crowd and no
 * error, and the only way to find out was to look at the shot and see nobody there.</p>
 *
 * <p>As a thing the film owns, the question each tick is not "did the spawn fire?" but "should
 * this crowd be here now?" - and if it should and is not, it is made so. There is no firing to
 * miss. See {@link CrowdReconciler}.</p>
 *
 * <p>Field ids match the old {@code CrowdSpawnActionClip} exactly, so converting an old film is
 * a copy of the clip's data rather than a field-by-field translation.</p>
 */
public class Crowd extends ValueGroup
{
    /** Shown in the editor's list. Not an identity - {@link #getId()} is. */
    public final ValueString name = new ValueString("name", "");
    public final ValueBoolean enabled = new ValueBoolean("enabled", true);

    /**
     * The tag the behaviour clips address this crowd by, and part of every member's identity.
     *
     * <p>Kept as a string because it is written into the members' command tags and into their
     * deterministic UUIDs, so an old film's members keep the identities they already had. Nobody
     * types it and nobody picks it: the crowd belongs to the replay carrying its form, and a
     * behaviour clip on that replay's timeline drives it by sitting there.</p>
     */
    public final ValueString crowdTag = new ValueString("crowd_tag", "crowd_1");

    /**
     * The tick the crowd appears on. It stays for the rest of the film.
     *
     * <p>There was a duration too, and it earned nothing: a crowd that stops existing part way
     * through is a crowd that vanishes on camera, which is not a thing anyone was asking for.
     * Whether a crowd is wanted at all is the enabled flag, and when it should go is the end of
     * the film.</p>
     */
    public final ValueInt start = new ValueInt("start", 0, 0, Integer.MAX_VALUE);

    /**
     * Which replay the crowd is placed around, by index, or -1 for none.
     *
     * <p>The old clip took its centre from whichever replay's timeline it happened to be sitting
     * on, which tied a crowd to an actor it had nothing to do with. Here it is stated outright,
     * and a crowd is free to belong to no one - a painted crowd is drawn onto the world in
     * absolute coordinates and never needed a centre at all.</p>
     */
    public final ValueInt anchor = new ValueInt("anchor", -1, -1, Integer.MAX_VALUE);

    /* Who they are. */
    public final ValueString mobType = new ValueString("mob_type", "minecraft:villager");
    public final ValueBoolean useActorForm = new ValueBoolean("use_actor_form", false);
    public final ValueForm actorForm = new ValueForm("actor_form");
    public final ValueBoolean randomTextures = new ValueBoolean("random_textures", false);
    public final ValueLink randomTextureFolder = new ValueLink("random_texture_folder", null);

    /* Neighbour work is bucketed rather than all-pairs (see CrowdGrid), so the ceiling is the
     * entity tick itself rather than the crowd logic. The high end of this range is meant for
     * rendering out a shot, not for editing one live - vanilla's own entity ticking will not
     * hold 20 TPS there. */
    public final ValueInt count = new ValueInt("count", 20, 1, 100000);
    public final ValueInt seed = new ValueInt("seed", 1);

    /* Where they stand. */
    public final ValueFloat spacing = new ValueFloat("spacing", 1.0F, 0.1F, 64F);
    public final ValueInt formation = new ValueInt("formation", CrowdFormation.CIRCLE.ordinal(), 0, CrowdFormation.values().length - 1);
    public final ValueFloat holeRadius = new ValueFloat("hole_radius", 4F, 0F, 128F);

    /* The crowd behaviour clip drives movement by setting velocity directly and stops the
     * navigator anyway, so vanilla AI contributes nothing but cost - and for villagers that
     * cost is the brain, comfortably the most expensive thing they do per tick. Turning it off
     * also stops them wandering off on their own errands, out of the loaded chunks. */
    public final ValueBoolean disableAi = new ValueBoolean("disable_ai", false);
    public final ValueBoolean randomYaw = new ValueBoolean("random_yaw", true);
    public final ValueBoolean spawnOnBlock = new ValueBoolean("spawn_on_block", true);
    public final ValueBoolean skipUnsafe = new ValueBoolean("skip_unsafe", true);

    /* The {@link CrowdFormation#PAINT} formation's shape: ground painted by hand in the editor,
     * as the surface height of every column the brush covered. Unlike every other formation this
     * one is absolute world space, not an offset from the anchor - it is drawn onto the world. */
    public final ValueAreaCells cells = new ValueAreaCells("cells");
    public final ValueInt brushSize = new ValueInt("brush_size", 4, 1, 64);
    public final ValueBoolean showOutline = new ValueBoolean("show_outline", true);

    /** Per-member armour: profiles a member rolls into, mixed independently per slot. */
    public final ValueCrowdArmor armor = new ValueCrowdArmor("armor");

    public Crowd(String id)
    {
        super(id);

        this.add(this.name);
        this.add(this.enabled);
        this.add(this.crowdTag);
        this.add(this.start);
        this.add(this.anchor);

        this.add(this.mobType);
        this.add(this.useActorForm);
        this.add(this.actorForm);
        this.add(this.randomTextures);
        this.add(this.randomTextureFolder);
        this.add(this.count);
        this.add(this.seed);

        this.add(this.spacing);
        this.add(this.formation);
        this.add(this.holeRadius);
        this.add(this.disableAi);
        this.add(this.randomYaw);
        this.add(this.spawnOnBlock);
        this.add(this.skipUnsafe);

        this.add(this.cells);
        this.add(this.brushSize);
        this.add(this.showOutline);
        this.add(this.armor);
    }

    public Long2IntOpenHashMap getCells()
    {
        return this.cells.get();
    }

    /* Painting writes into the cell map directly, which nothing would otherwise hear about -
     * the map is mutated in place, so the value it belongs to never fires. Announcing the edit
     * is what puts painted ground into the undo history and, more to the point, what sends it to
     * the server; a stroke nobody was told about is a crowd that spawns on unpainted ground. */

    public void paint(int x, int y, int z)
    {
        BaseValue.edit(this.cells, (cells) -> cells.get().put(ValueAreaCells.key(x, z), y));
    }

    public void erase(int x, int z)
    {
        BaseValue.edit(this.cells, (cells) -> cells.get().remove(ValueAreaCells.key(x, z)));
    }

    public void clearCells()
    {
        BaseValue.edit(this.cells, (cells) -> cells.get().clear());
    }

    public void setCells(Long2IntOpenHashMap newCells)
    {
        BaseValue.edit(this.cells, (cells) ->
        {
            cells.get().clear();

            if (newCells != null)
            {
                cells.get().putAll(newCells);
            }
        });
    }

    public CrowdFormation getFormation()
    {
        return CrowdFormation.get(this.formation.get());
    }

    /** Whether the crowd is meant to be standing there at this film tick. */
    public boolean existsAt(int tick)
    {
        return this.enabled.get() && tick >= this.start.get();
    }

    public String getDisplayName()
    {
        String name = this.name.get();

        return name == null || name.isEmpty() ? this.crowdTag.get() : name;
    }

    /**
     * Everything about the crowd that changes where its members are or what they are.
     *
     * <p>The reconciler respawns when this changes, so it must cover every setting the placement
     * reads and nothing else - the window and the enabled flag are not in it, because moving a
     * crowd's window does not move the crowd.</p>
     */
    public int signature()
    {
        int hash = this.crowdTag.get().hashCode();

        hash = hash * 31 + this.mobType.get().hashCode();
        hash = hash * 31 + Boolean.hashCode(this.useActorForm.get());
        hash = hash * 31 + (this.actorForm.get() == null ? 0 : this.actorForm.get().toData().toString().hashCode());
        hash = hash * 31 + Boolean.hashCode(this.randomTextures.get());
        hash = hash * 31 + (this.randomTextureFolder.get() == null ? 0 : this.randomTextureFolder.get().toString().hashCode());
        hash = hash * 31 + this.count.get();
        hash = hash * 31 + this.seed.get();
        hash = hash * 31 + Float.hashCode(this.spacing.get());
        hash = hash * 31 + this.formation.get();
        hash = hash * 31 + Float.hashCode(this.holeRadius.get());
        hash = hash * 31 + Boolean.hashCode(this.disableAi.get());
        hash = hash * 31 + Boolean.hashCode(this.randomYaw.get());
        hash = hash * 31 + Boolean.hashCode(this.spawnOnBlock.get());
        hash = hash * 31 + Boolean.hashCode(this.skipUnsafe.get());
        hash = hash * 31 + this.anchor.get();
        hash = hash * 31 + this.getCells().hashCode();

        return hash;
    }

    /**
     * Signature excluding painted cells, used when crowd is animated by paint keyframes
     * so that scrubbing or playback position updates don't destructively despawn/respawn members.
     */
    public int signatureWithoutCells()
    {
        int hash = this.crowdTag.get().hashCode();

        hash = hash * 31 + this.mobType.get().hashCode();
        hash = hash * 31 + Boolean.hashCode(this.useActorForm.get());
        hash = hash * 31 + (this.actorForm.get() == null ? 0 : this.actorForm.get().toData().toString().hashCode());
        hash = hash * 31 + Boolean.hashCode(this.randomTextures.get());
        hash = hash * 31 + (this.randomTextureFolder.get() == null ? 0 : this.randomTextureFolder.get().toString().hashCode());
        hash = hash * 31 + this.count.get();
        hash = hash * 31 + this.seed.get();
        hash = hash * 31 + Float.hashCode(this.spacing.get());
        hash = hash * 31 + this.formation.get();
        hash = hash * 31 + Float.hashCode(this.holeRadius.get());
        hash = hash * 31 + Boolean.hashCode(this.disableAi.get());
        hash = hash * 31 + Boolean.hashCode(this.randomYaw.get());
        hash = hash * 31 + Boolean.hashCode(this.spawnOnBlock.get());
        hash = hash * 31 + Boolean.hashCode(this.skipUnsafe.get());
        hash = hash * 31 + this.anchor.get();

        return hash;
    }
}
