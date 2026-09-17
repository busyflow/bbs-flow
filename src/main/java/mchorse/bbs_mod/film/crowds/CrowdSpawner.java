package mchorse.bbs_mod.film.crowds;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdPaintArea;
import mchorse.bbs_mod.actions.crowd.CrowdLookEvaluator;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmExportState;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Puts a {@link Crowd}'s members into the world.
 *
 * <p>Placement only - deciding whether a crowd should be standing there at all is
 * {@link CrowdReconciler}'s job.</p>
 */
public class CrowdSpawner
{
    private static final int VERTICAL_RANGE = 32;

    private CrowdSpawner()
    {}

    /**
     * @return how many members were actually placed, which is not the count asked for: members
     *         over ungenerated chunks or with nowhere safe to stand are skipped.
     */
    public static int spawn(ServerWorld world, Film film, Crowd crowd, Vec3d center)
    {
        return spawn(world, film, crowd, center, 0);
    }

    /**
     * @param tick the film tick being spawned at, so members arrive already facing what the look
     *             keyframes say they should be facing at that moment
     */
    public static int spawn(ServerWorld world, Film film, Crowd crowd, Vec3d center, int tick)
    {
        String tag = CrowdUtils.crowdTag(crowd.crowdTag.get());
        boolean useActor = crowd.useActorForm.get();
        EntityType<?> type = null;

        if (!useActor)
        {
            try
            {
                type = Registries.ENTITY_TYPE.get(new Identifier(crowd.mobType.get()));
            }
            catch (Exception e)
            {
                return 0;
            }

            if (type == null)
            {
                return 0;
            }
        }
        else if (crowd.actorForm.get() == null)
        {
            return 0;
        }

        CrowdFormation formation = crowd.getFormation();
        int count = crowd.count.get();
        double spacing = crowd.spacing.get();

        Map<Long, Double> surfaceCache = crowd.spawnOnBlock.get() ? new HashMap<>() : null;
        EntityData entityData = null;
        CrowdPaintArea area = null;

        if (formation == CrowdFormation.PAINT)
        {
            area = new CrowdPaintArea(crowd.getCells());

            /* Nothing painted means nothing was asked for. Falling back to a circle here would
             * put a crowd somewhere the shot never called for. */
            if (area.isEmpty())
            {
                return 0;
            }
        }

        /* How many of the crowd to spawn while the shot is being built rather than exported;
         * zero for all of them. A five-figure crowd cannot be worked with live - it is spawned,
         * ticked and drawn in full, and the editor falls to single-figure frame rates. But a
         * crowd is placed against the world it stands in, and the things worth catching early
         * are where it does not fit: members dropped into a hole, pressed against a wall,
         * standing on a roof. Seeing none of it until the export is no better than seeing all
         * of it. So the preview spawns a sample rather than a prefix - see the stride below. */
        int preview = BBSSettings.crowdPreviewCount.get();
        int spawnCount = FilmExportState.isAnyExporting() || preview <= 0 ? count : Math.min(preview, count);
        IntList spawned = new IntArrayList(spawnCount);

        /* Where this crowd is looking at the tick it appears, so a member can be placed already
         * turned. Spawning everyone facing their formation angle and letting the look keyframes
         * turn them on the next tick is a crowd that visibly swings round the instant it appears -
         * and on the first frame of a render, that swing is in the shot. */
        CrowdLookEvaluator.Sample look = lookSample(film, crowd, tick);
        boolean visible = isCrowdVisible(film, crowd, tick);
        float[] rotation = new float[2];

        for (int j = 0; j < spawnCount; j++)
        {
            /* The member's number in the full crowd, not in this sample. Everything downstream -
             * where it stands, which texture it gets, its identity - is worked out from this, so
             * a preview member stands exactly where that member of the full crowd would, and the
             * sample is spread across the whole area rather than filling the first corner of it. */
            int i = spawnCount == count ? j : (int) ((long) j * count / spawnCount);
            LivingEntity entity;

            if (useActor)
            {
                Form form = createActorForm(crowd);

                if (form == null)
                {
                    continue;
                }

                ActorEntity actorEntity = new ActorEntity(BBSMod.ACTOR_ENTITY, world);

                actorEntity.setForm(form);
                entity = actorEntity;
            }
            else
            {
                Entity created = type.create(world);

                if (!(created instanceof MobEntity mob))
                {
                    if (created != null)
                    {
                        created.discard();
                    }

                    continue;
                }

                BlockPos initial = BlockPos.ofFloored(center);

                entityData = mob.initialize(world, world.getLocalDifficulty(initial), SpawnReason.COMMAND, entityData, null);
                mob.setPersistent();
                mob.setAiDisabled(crowd.disableAi.get());
                mob.setSilent(false);
                mob.setCustomNameVisible(false);
                entity = mob;
            }

            entity.setUuid(CrowdUtils.deterministicUuid(film, tag, crowd.seed.get(), i));

            Vec3d spawn = findSpawnPoint(world, crowd, entity, center, formation, area, i, count, spacing, surfaceCache);

            if (spawn == null)
            {
                continue;
            }

            float yaw = crowd.randomYaw.get() ? (float) ((i * 137.507764D) % 360D) : 0F;
            float pitch = 0F;

            /* Ask from the eyes rather than the feet, the same as the runtime does, or the crowd
             * arrives pitched at the target's shoes and corrects itself a tick later. */
            if (look != null && CrowdLookEvaluator.rotation(spawn.x, spawn.y + entity.getStandingEyeHeight(), spawn.z, look, rotation))
            {
                /* The same axes the runtime will drive a tick later, so a member does not arrive
                 * turned in a way the look keyframe never asked for and then correct itself. */
                if (look.control().yaw())
                {
                    yaw = rotation[0];
                }

                if (look.control().pitch())
                {
                    pitch = rotation[1];
                }
            }

            entity.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, yaw, pitch);
            entity.prevX = spawn.x;
            entity.prevY = spawn.y;
            entity.prevZ = spawn.z;
            entity.lastRenderX = spawn.x;
            entity.lastRenderY = spawn.y;
            entity.lastRenderZ = spawn.z;
            entity.prevYaw = yaw;
            entity.prevPitch = pitch;
            entity.setHeadYaw(yaw);
            entity.prevHeadYaw = yaw;
            entity.setBodyYaw(yaw);
            entity.prevBodyYaw = yaw;
            entity.setVelocity(Vec3d.ZERO);
            entity.fallDistance = 0F;
            entity.setInvulnerable(true);
            entity.calculateDimensions();
            entity.setSprinting(false);
            entity.setSneaking(false);
            CrowdUtils.tag(entity, film, tag, i);
            equipArmor(entity, crowd, i);

            if (!visible)
            {
                entity.setInvisible(true);
                entity.setSilent(true);
            }

            world.spawnEntity(entity);
            spawned.add(entity.getId());
        }

        /* Tell the clients who these are as soon as they exist, rather than leaving it to the
         * behaviour clip: a crowd is allowed to have no behaviour at all, and one that never
         * announced itself is one the client cannot hold the bodies of - it would be drawn
         * facing wherever it spawned. */
        if (!spawned.isEmpty())
        {
            ServerNetwork.sendCrowdMembers(world, spawned);
        }

        return spawned.size();
    }

    /**
     * Roll and put on this member's armour. Seeded off the crowd seed and the member's number so the
     * same member wears the same kit every spawn; mob members also drop nothing, since a filmed crowd
     * is not loot. No profiles configured means nothing is touched - vanilla-equipped mobs keep theirs.
     */
    private static void equipArmor(LivingEntity entity, Crowd crowd, int index)
    {
        CrowdArmor armor = crowd.armor.get();

        if (armor == null || armor.isEmpty())
        {
            return;
        }

        ItemStack[] stacks = armor.roll(new Random(crowd.seed.get() * 0x9E3779B1L + index));

        for (int i = 0; i < CrowdArmor.SLOTS.length; i++)
        {
            EquipmentSlot slot = CrowdArmor.SLOTS[i];

            entity.equipStack(slot, stacks[i]);

            if (entity instanceof MobEntity mob)
            {
                mob.setEquipmentDropChance(slot, 0F);
            }
        }
    }

    /**
     * The member's form, as authored. What it wears is not decided here.
     *
     * <p>Spawning used to hand each member a random texture from a folder, which fixed a crowd's
     * appearance at the moment it appeared and left no way to change it later. Texture is a
     * keyframe now - one look for the whole crowd, or random within it when the keyframe asks -
     * so deciding it here as well would only be something for the first keyframe to undo.</p>
     */
    private static Form createActorForm(Crowd crowd)
    {
        return FormUtils.copy(crowd.actorForm.get());
    }

    /**
     * The look this crowd's driving replay asks for at the spawn tick, or {@code null} when
     * nothing looks at anything - in which case members keep their formation facing.
     *
     * <p>The crowd is addressed by tag and the keyframes live on a replay, so the replay driving
     * it has to be found the same way {@code CrowdKeyframeRuntime} finds it, from the other end.</p>
     */
    private static CrowdLookEvaluator.Sample lookSample(Film film, Crowd crowd, int tick)
    {
        if (film == null)
        {
            return null;
        }

        String tag = crowdTag(crowd);

        for (Replay replay : film.replays.getList())
        {
            if (!replay.enabled.get() || !(replay.form.get() instanceof CrowdForm form))
            {
                continue;
            }

            if (!tag.equals(CrowdUtils.crowdTag(form.crowd.get())))
            {
                continue;
            }

            CrowdLookEvaluator.Sample sample = CrowdLookEvaluator.sample(film, replay, replay.getTick(tick));

            if (sample != null)
            {
                return sample;
            }
        }

        return null;
    }

    private static boolean isCrowdVisible(Film film, Crowd crowd, int tick)
    {
        if (film == null)
        {
            return true;
        }

        String tag = crowdTag(crowd);

        for (Replay replay : film.replays.getList())
        {
            if (!replay.enabled.get() || !(replay.form.get() instanceof CrowdForm form))
            {
                continue;
            }

            if (!tag.equals(CrowdUtils.crowdTag(form.crowd.get())))
            {
                continue;
            }

            if (!replay.keyframes.crowdVisible.isEmpty())
            {
                return replay.keyframes.crowdVisible.interpolate(replay.getTick(tick), true);
            }
        }

        return true;
    }

    private static String crowdTag(Crowd crowd)
    {
        return CrowdUtils.crowdTag(crowd.crowdTag.get());
    }

    private static Vec3d findSpawnPoint(ServerWorld world, Crowd crowd, LivingEntity entity, Vec3d center, CrowdFormation formation, CrowdPaintArea area, int index, int count, double spacing, Map<Long, Double> surfaceCache)
    {
        Vec3d fallback = null;

        for (int attempt = 0; attempt < 16; attempt++)
        {
            double x;
            double z;
            double sampledY;

            if (area != null)
            {
                /* Painted ground is drawn onto the world, so its points are already absolute -
                 * the anchor's position doesn't move them. */
                Vec3d painted = area.point(index, count, attempt);

                x = painted.x;
                z = painted.z;
                sampledY = painted.y;
            }
            else
            {
                Vec3d offset = getOffset(crowd, formation, index, count, spacing, attempt);

                x = center.x + offset.x;
                z = center.z + offset.z;
                sampledY = center.y + offset.y;
            }

            Double y;

            if (area != null)
            {
                /* The brush already found this column's surface, and it did it against the world
                 * the shot was composed in. Re-deriving it here would cost a block read per
                 * member - forty thousand of them - to answer a question already answered. */
                y = sampledY;
            }
            else if (crowd.spawnOnBlock.get())
            {
                y = findSurfaceY(world, center, x, z, surfaceCache);
            }
            else
            {
                y = sampledY;
            }

            fallback = new Vec3d(x, sampledY, z);

            if (y == null)
            {
                continue;
            }

            Vec3d candidate = new Vec3d(x, y, z);

            fallback = candidate;

            if (isSpawnClear(world, entity, candidate))
            {
                return candidate;
            }
        }

        return crowd.skipUnsafe.get() ? null : fallback;
    }

    private static Vec3d getOffset(Crowd crowd, CrowdFormation formation, int index, int count, double spacing, int attempt)
    {
        int sampleIndex = index + attempt * Math.max(1, count) * 9973;
        Vec3d offset = CrowdUtils.formationPoint(formation, sampleIndex, count, spacing, crowd.holeRadius.get());

        if ((formation == CrowdFormation.BOX || formation == CrowdFormation.BOX_OUTLINE) || attempt == 0)
        {
            return offset;
        }

        Vec3d nudge = CrowdUtils.formationPoint(CrowdFormation.CIRCLE, attempt - 1, 15, Math.max(0.6D, spacing * 0.35D));

        return offset.add(nudge);
    }

    private static boolean isSpawnClear(ServerWorld world, LivingEntity entity, Vec3d spawn)
    {
        double halfWidth = Math.max(0.2D, entity.getWidth() * 0.5D - 0.01D);

        /* Same reasoning as findSurfaceY - the footprint can straddle a chunk border, and the
         * probe must not be what pulls an ungenerated chunk in on the server thread. */
        for (int cx = (int) Math.floor(spawn.x - halfWidth) >> 4; cx <= (int) Math.floor(spawn.x + halfWidth) >> 4; cx++)
        {
            for (int cz = (int) Math.floor(spawn.z - halfWidth) >> 4; cz <= (int) Math.floor(spawn.z + halfWidth) >> 4; cz++)
            {
                if (world.getChunk(cx, cz, ChunkStatus.FULL, false) == null)
                {
                    return false;
                }
            }
        }

        int height = Math.max(1, (int) Math.ceil(entity.getHeight()));

        /* The nine columns are only three distinct blocks wide in either direction most of the
         * time, and the probe runs up to sixteen times per member across a crowd of five figures.
         * Floor them once, drop the duplicates and walk with a mutable position - the version that
         * built a BlockPos per probe allocated more objects placing a crowd than the crowd had
         * members by two orders of magnitude. */
        int[] xs = distinctFloored(spawn.x, halfWidth);
        int[] zs = distinctFloored(spawn.z, halfWidth);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int dy = 0; dy < height; dy++)
        {
            int y = MathHelper.floor(spawn.y) + dy;

            for (int px : xs)
            {
                for (int pz : zs)
                {
                    pos.set(px, y, pz);

                    BlockState state = world.getBlockState(pos);

                    if (!state.getCollisionShape(world, pos).isEmpty())
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /** The distinct block coordinates the centre and both edges of the footprint fall in. */
    private static int[] distinctFloored(double centre, double halfWidth)
    {
        int mid = MathHelper.floor(centre);
        int low = MathHelper.floor(centre - halfWidth);
        int high = MathHelper.floor(centre + halfWidth);

        if (low == high)
        {
            return new int[] {mid};
        }

        return low == mid || high == mid ? new int[] {low, high} : new int[] {low, mid, high};
    }

    private static Double findSurfaceY(ServerWorld world, Vec3d center, double x, double z, Map<Long, Double> surfaceCache)
    {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        long key = columnKey(bx, bz);

        if (surfaceCache != null && surfaceCache.containsKey(key))
        {
            return surfaceCache.get(key);
        }

        Double surface = findSurfaceY(world, center, bx, bz);

        if (surfaceCache != null)
        {
            surfaceCache.put(key, surface);
        }

        return surface;
    }

    /**
     * Find the standable Y in this column. The search spans {@link #VERTICAL_RANGE} blocks
     * both above <em>and</em> below the origin, so a crowd laid over sloping ground keeps its
     * shape when the terrain drops away instead of losing every member on the low side.
     */
    /** Somewhere a member can stand: air, or anything walked straight through. */
    private static boolean isPassable(ServerWorld world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        return state.isAir() || state.getCollisionShape(world, pos).isEmpty();
    }

    private static Double findSurfaceY(ServerWorld world, Vec3d center, int bx, int bz)
    {
        /* Never touch a column whose chunk is not already finished. Reading a block out there
         * would force a synchronous generate on the server thread, and a wide crowd has enough
         * rim members to chain those into a freeze that also takes the world save down with it.
         * Skipping the member instead simply leaves the crowd's edge at the loaded boundary.
         * A chunk part-way through generation counts as loaded but is not free to read, so ask
         * for a finished one and accept its absence rather than waiting for it. */
        if (world.getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false) == null)
        {
            return null;
        }

        int origin = (int) Math.floor(center.y);
        int minY = Math.max(world.getBottomY() + 1, origin - VERTICAL_RANGE);
        int maxY = Math.min(world.getTopY() - 2, origin + VERTICAL_RANGE);

        if (maxY < minY)
        {
            return null;
        }

        /* Start from the heightmap when it falls inside the band - that skips the whole
         * scan for open sky, which is the common case. */
        int start = Math.min(maxY, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz));
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = start; y >= minY; y--)
        {
            BlockState belowState = world.getBlockState(pos.set(bx, y - 1, bz));

            if (!belowState.isSideSolidFullSquare(world, pos, Direction.UP))
            {
                continue;
            }

            /* Asked as "can this be stood in" rather than "is it air": grass, flowers and the
             * tall stuff are not air, so a meadow failed the test at its own surface and the
             * search carried on down past the ground it should have stopped at. */
            if (isPassable(world, pos.set(bx, y, bz)) && isPassable(world, pos.set(bx, y + 1, bz)))
            {
                return (double) y;
            }
        }

        return null;
    }

    private static long columnKey(int x, int z)
    {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
