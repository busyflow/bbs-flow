package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.FilmExportState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import mchorse.bbs_mod.actions.types.area.ValueAreaCells;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrowdSpawnActionClip extends ActionClip
{
    public final ValueString crowdTag = new ValueString("crowd_tag", "crowd_1");
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
    public final ValueBoolean replaceExisting = new ValueBoolean("replace_existing", true);

    /* The {@link CrowdFormation#PAINT} formation's shape: ground painted by hand in the editor,
     * as the surface height of every column the brush covered. Unlike every other formation this
     * one is absolute world space, not an offset from the replay - it is drawn onto the world. */
    public final ValueAreaCells cells = new ValueAreaCells("cells");
    public final ValueInt brushSize = new ValueInt("brush_size", 4, 1, 64);

    public CrowdSpawnActionClip()
    {
        super();

        this.add(this.crowdTag);
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
        this.add(this.replaceExisting);
        this.add(this.cells);
        this.add(this.brushSize);
    }

    /* Painting, from the editor's brush. */

    public Long2IntOpenHashMap getCells()
    {
        return this.cells.get();
    }

    public void paint(int x, int y, int z)
    {
        this.getCells().put(ValueAreaCells.key(x, z), y);
    }

    public void erase(int x, int z)
    {
        this.getCells().remove(ValueAreaCells.key(x, z));
    }

    public void clearCells()
    {
        this.getCells().clear();
    }

    /**
     * Does nothing. A crowd is no longer something that happens at a tick.
     *
     * <p>This class survives only so that films written before the change still deserialize;
     * {@link mchorse.bbs_mod.film.crowds.CrowdMigration} reads one of these into a
     * {@link mchorse.bbs_mod.film.crowds.Crowd} and takes it off the timeline as the film loads.
     * Spawning here as well would put the same crowd into the world twice, under the same tags,
     * with each copy removing the other's members.</p>
     */
    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {}

    @Override
    protected Clip create()
    {
        return new CrowdSpawnActionClip();
    }
}
