package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.film.crowds.Crowd;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class CrowdUtils
{
    public static final String INTERNAL_TAG = "bbs_crowd";
    public static final String RUN_TAG_PREFIX = "bbs_crowd_run_";
    public static final String INDEX_TAG_PREFIX = "bbs_crowd_index_";

    private static final AtomicInteger RUN_COUNTER = new AtomicInteger();
    private static final Map<Film, String> RUN_TAGS = new WeakHashMap<>();

    public static String crowdTag(String raw)
    {
        String tag = raw == null ? "" : raw.trim();

        return tag.isEmpty() ? "crowd_1" : tag;
    }

    public static String getRunTag(Film film)
    {
        if (film == null)
        {
            return RUN_TAG_PREFIX + "unknown";
        }

        synchronized (RUN_TAGS)
        {
            return RUN_TAGS.computeIfAbsent(film, (f) -> RUN_TAG_PREFIX + Integer.toHexString(RUN_COUNTER.incrementAndGet()));
        }
    }

    public static void tag(Entity entity, Film film, String crowdTag)
    {
        tag(entity, film, crowdTag, -1);
    }

    public static void tag(Entity entity, Film film, String crowdTag, int index)
    {
        entity.addCommandTag(INTERNAL_TAG);
        entity.addCommandTag(getRunTag(film));
        entity.addCommandTag(crowdTag(crowdTag));

        if (index >= 0)
        {
            entity.addCommandTag(INDEX_TAG_PREFIX + index);
        }
    }

    public static boolean hasTags(Entity entity, Film film, String crowdTag)
    {
        return entity.getCommandTags().contains(INTERNAL_TAG)
            && entity.getCommandTags().contains(getRunTag(film))
            && entity.getCommandTags().contains(crowdTag(crowdTag));
    }

    /**
     * Every member of a tagged crowd, nearest-index first.
     *
     * <p>{@code range} of 0 or less means the whole world. Membership is decided by the tags
     * alone, so a crowd spread wider than the query box would otherwise have its far half
     * silently ignored - the visible symptom being that only the members near the target react
     * to a behaviour clip.</p>
     *
     * <p>The unlimited case walks the world's loaded entities rather than asking for a
     * world-sized box: a box query visits every chunk section it spans, so a box big enough to
     * cover the world costs seconds per call and stalls the server thread outright.</p>
     */
    public static List<LivingEntity> getCrowd(ServerWorld world, Film film, String crowdTag, Vec3d center, double range)
    {
        Predicate<LivingEntity> member = memberTest(film, crowdTag);
        List<LivingEntity> entities = range <= 0D
            ? collectTagged(world, member)
            : world.getEntitiesByClass(LivingEntity.class,
                Box.of(center, Math.max(8D, range) * 2D, Math.max(16D, range), Math.max(8D, range) * 2D),
                member);

        /* entityIndex is a field read off the member now (it caches the tag it was parsed from),
         * so the comparator can call it directly - the map that used to hold the numbers for the
         * sort was itself the allocation a five-figure crowd noticed. */
        entities.sort(Comparator
            .comparingInt(CrowdUtils::entityIndex)
            .thenComparingInt(Entity::getId));

        return entities;
    }

    /**
     * A membership test with the film's tags already resolved.
     *
     * <p>Worth its own method because the obvious spelling - calling {@link #hasTags} per entity -
     * re-resolves the film's run tag through a synchronised map and re-trims the crowd tag for
     * every entity in the world, and the scan behind this visits every one of them. At five
     * figures that lookup was a bigger cost than the scan it was guarding.</p>
     */
    private static Predicate<LivingEntity> memberTest(Film film, String crowdTag)
    {
        String runTag = getRunTag(film);
        String tag = crowdTag(crowdTag);

        return (entity) ->
        {
            Set<String> tags = entity.getCommandTags();

            return tags.contains(INTERNAL_TAG) && tags.contains(runTag) && tags.contains(tag);
        };
    }

    /**
     * Walks every loaded entity once. Used wherever the whole world has to be covered, since a
     * world-sized {@link Box} query iterates chunk sections instead of entities and takes
     * seconds.
     */
    private static List<LivingEntity> collectTagged(ServerWorld world, Predicate<LivingEntity> predicate)
    {
        List<LivingEntity> found = new ArrayList<>();

        for (Entity entity : world.iterateEntities())
        {
            if (entity instanceof LivingEntity living && predicate.test(living))
            {
                found.add(living);
            }
        }

        return found;
    }

    public static void removeCrowd(ServerWorld world, Film film, String crowdTag)
    {
        for (LivingEntity mob : collectTagged(world, memberTest(film, crowdTag)))
        {
            mob.discard();
        }
    }

    public static void removeAllForFilm(ServerWorld world, Film film)
    {
        String runTag = getRunTag(film);

        for (LivingEntity mob : collectTagged(world, (entity) ->
        {
            Set<String> tags = entity.getCommandTags();

            return tags.contains(INTERNAL_TAG) && tags.contains(runTag);
        }))
        {
            mob.discard();
        }
    }

    /**
     * Discard every crowd member that no running playback owns.
     *
     * <p>Stopping a playback only removes the members carrying its own run tag, which leaves
     * behind every crowd whose run ended some other way: a film reloaded in the editor gets a
     * fresh tag and abandons the previous one, and a playback that never reached its stop -
     * closing the world, a crash, an error mid-scene - never removes anything at all. Those
     * members are ordinary mobs once abandoned, so they stay, and they accumulate a crowd at a
     * time until the world holds tens of thousands of villagers.</p>
     *
     * <p>A crowd member only ever belongs to a playback, so one that no live run claims cannot
     * become claimed later and is safe to remove. Sweeping on both start and stop means the
     * leftovers of a previous session are gone the first time anything plays.</p>
     */
    public static void sweepOrphans(ServerWorld world, Collection<String> activeRunTags)
    {
        for (LivingEntity mob : collectTagged(world, (entity) ->
        {
            Set<String> tags = entity.getCommandTags();

            if (!tags.contains(INTERNAL_TAG))
            {
                return false;
            }

            for (String tag : tags)
            {
                if (tag.startsWith(RUN_TAG_PREFIX) && activeRunTags.contains(tag))
                {
                    return false;
                }
            }

            return true;
        }))
        {
            mob.discard();
        }
    }

    /**
     * The tag of the crowd a replay drives, or {@code null} when it drives none.
     *
     * <p>A crowd belongs to the replay carrying its form, so this is how anything addressing "that
     * crowd over there" names it: pick the replay, resolve the crowd from it. Naming a crowd by
     * its own tag from somewhere else let a clip on one replay reach into another's crowd, which
     * read as two unrelated ways of saying the same thing and only ever confused matters.</p>
     */
    public static String replayCrowdTag(Replay replay)
    {
        if (replay == null || !(replay.form.get() instanceof CrowdForm form))
        {
            return null;
        }

        String tag = form.crowd.get();

        return tag == null || tag.trim().isEmpty() ? null : tag;
    }

    /** As {@link #replayCrowdTag(Replay)}, for a replay named by index. */
    public static String replayCrowdTag(Film film, int index)
    {
        return replayCrowdTag(getReplay(film, index));
    }

    public static Replay getReplay(Film film, int index)
    {
        if (film == null || index < 0 || index >= film.replays.getList().size())
        {
            return null;
        }

        return film.replays.getList().get(index);
    }

    public static Vec3d replayPosition(Replay replay, int tick)
    {
        int replayTick = replay.getTick(tick);

        return new Vec3d(
            replay.keyframes.x.interpolate(replayTick),
            replay.keyframes.y.interpolate(replayTick),
            replay.keyframes.z.interpolate(replayTick)
        );
    }

    public static Vec3d getCrowdCenter(Film film, Replay replay, Crowd crowd, int tick)
    {
        if (crowd == null && replay != null && replay.form.get() instanceof CrowdForm crowdForm)
        {
            crowd = film == null ? null : film.crowds.byTag(crowdForm.crowd.get());
        }

        if (crowd != null && crowd.getFormation() == CrowdFormation.PAINT)
        {
            Long2IntOpenHashMap cells = crowd.getCells();

            if (!cells.isEmpty())
            {
                double sumX = 0D;
                double sumY = 0D;
                double sumZ = 0D;
                int count = 0;

                for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry : cells.long2IntEntrySet())
                {
                    long key = entry.getLongKey();
                    sumX += mchorse.bbs_mod.actions.types.area.ValueAreaCells.keyX(key) + 0.5D;
                    sumY += entry.getIntValue();
                    sumZ += mchorse.bbs_mod.actions.types.area.ValueAreaCells.keyZ(key) + 0.5D;
                    count++;
                }

                if (count > 0)
                {
                    return new Vec3d(sumX / count, sumY / count, sumZ / count);
                }
            }
        }

        if (film != null && crowd != null)
        {
            Replay anchor = getReplay(film, crowd.anchor.get());

            if (anchor != null)
            {
                return replayPosition(anchor, crowd.start.get());
            }
        }

        if (replay != null)
        {
            return replayPosition(replay, tick);
        }

        return Vec3d.ZERO;
    }

    public static Vec3d formationPoint(CrowdFormation formation, int index, int count, double spacing)
    {
        return formationPoint(formation, index, count, spacing, 0D);
    }

    /**
     * Place one crowd member. Every formation derives its own extent from the member count and
     * the spacing, so there is no separate area to configure - the shape is exactly as big as
     * the crowd needs to be. {@code hole} is the empty inner radius used by
     * {@link CrowdFormation#DONUT}.
     */
    public static Vec3d formationPoint(CrowdFormation formation, int index, int count, double spacing, double hole)
    {
        spacing = Math.max(0.1D, spacing);
        count = Math.max(1, count);
        hole = Math.max(0D, hole);

        if (formation == null)
        {
            formation = CrowdFormation.CIRCLE;
        }

        double boxSide = Math.max(spacing, Math.ceil(Math.sqrt(count)) * spacing);

        switch (formation)
        {
            case BOX:
            {
                double x = randomSigned(index, 0x45d9f3b) * boxSide * 0.5D;
                double z = randomSigned(index, 0x27d4eb2d) * boxSide * 0.5D;

                return new Vec3d(x, 0D, z);
            }
            case BOX_OUTLINE:
            {
                double boxX = boxSide;
                double boxZ = boxSide;
                double perimeter = boxX * 2D + boxZ * 2D;
                double distance = count <= 1 ? 0D : (index / (double) count) * perimeter;
                double halfX = boxX * 0.5D;
                double halfZ = boxZ * 0.5D;
                double x;
                double z;

                if (distance < boxX)
                {
                    x = -halfX + distance;
                    z = -halfZ;
                }
                else if (distance < boxX + boxZ)
                {
                    x = halfX;
                    z = -halfZ + distance - boxX;
                }
                else if (distance < boxX * 2D + boxZ)
                {
                    x = halfX - (distance - boxX - boxZ);
                    z = halfZ;
                }
                else
                {
                    x = -halfX;
                    z = halfZ - (distance - boxX * 2D - boxZ);
                }

                return new Vec3d(x, 0D, z);
            }
            case LINE:
            {
                double half = (count - 1) / 2D;

                return new Vec3d((index - half) * spacing, 0D, 0D);
            }
            case GRID:
            case SQUARE:
            {
                int side = (int) Math.ceil(Math.sqrt(count));
                int row = index / side;
                int col = index % side;
                double half = (side - 1) / 2D;

                return new Vec3d((col - half) * spacing, 0D, (row - half) * spacing);
            }
            case SQUARE_OUTLINE:
            {
                double side = Math.max(spacing, count * spacing / 4D);
                double half = side / 2D;
                double t = count <= 1 ? 0D : (index / (double) count) * 4D;
                double x;
                double z;

                if (t < 1D)
                {
                    x = -half + side * t;
                    z = -half;
                }
                else if (t < 2D)
                {
                    x = half;
                    z = -half + side * (t - 1D);
                }
                else if (t < 3D)
                {
                    x = half - side * (t - 2D);
                    z = half;
                }
                else
                {
                    x = -half;
                    z = half - side * (t - 3D);
                }

                return new Vec3d(x, 0D, z);
            }
            case DONUT:
            {
                /* Solve the outer radius so the ring's area still gives every member its
                 * spacing^2 of floor, then sample r uniformly by area so the ring doesn't
                 * bunch up against the hole. */
                double outer = Math.sqrt(hole * hole + count * spacing * spacing / Math.PI);
                double t = (index + 0.5D) / count;
                double r = Math.sqrt(hole * hole + t * (outer * outer - hole * hole));
                double angle = index * (Math.PI * (3D - Math.sqrt(5D)));

                return new Vec3d(Math.cos(angle) * r, 0D, Math.sin(angle) * r);
            }
            case CIRCLE_OUTLINE:
            {
                double radius = count <= 1 ? 0D : count * spacing / (Math.PI * 2D);
                double angle = count <= 1 ? 0D : index / (double) count * Math.PI * 2D;

                return new Vec3d(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
            }
            case CIRCLE:
            default:
            {
                double radius = Math.sqrt(count / Math.PI) * spacing;
                double goldenAngle = Math.PI * (3D - Math.sqrt(5D));
                double t = (index + 0.5D) / count;
                double r = Math.sqrt(t) * radius;
                double angle = index * goldenAngle;

                return new Vec3d(Math.cos(angle) * r, 0D, Math.sin(angle) * r);
            }
        }
    }

    /**
     * How far from the centre the outermost member of a formation lands. This is what the
     * editor draws, so it is derived from the same numbers the placement uses.
     */
    public static double formationRadius(CrowdFormation formation, int count, double spacing, double hole)
    {
        spacing = Math.max(0.1D, spacing);
        count = Math.max(1, count);
        hole = Math.max(0D, hole);

        if (formation == null)
        {
            formation = CrowdFormation.CIRCLE;
        }

        switch (formation)
        {
            /* Painted ground has its own outline drawn over it — a ring around it would only be a
             * second, wrong boundary. */
            case PAINT:
                return 0D;
            case DONUT:
                return Math.sqrt(hole * hole + count * spacing * spacing / Math.PI);
            case CIRCLE_OUTLINE:
                return count <= 1 ? 0D : count * spacing / (Math.PI * 2D);
            case LINE:
                return (count - 1) / 2D * spacing;
            case SQUARE_OUTLINE:
                return Math.max(spacing, count * spacing / 4D) * Math.sqrt(2D) / 2D;
            case GRID:
            case SQUARE:
            case BOX:
            case BOX_OUTLINE:
            {
                double side = Math.max(spacing, Math.ceil(Math.sqrt(count)) * spacing);

                return side * Math.sqrt(2D) / 2D;
            }
            case CIRCLE:
            default:
                return Math.sqrt(count / Math.PI) * spacing;
        }
    }

    /** The empty inner radius, which only the donut has. */
    public static double formationHole(CrowdFormation formation, double hole)
    {
        return formation == CrowdFormation.DONUT ? Math.max(0D, hole) : 0D;
    }

    /**
     * This member's number in its crowd.
     *
     * <p>Read from the command tag once and then kept on the entity. A member's number is fixed
     * at the moment it is spawned, and this is asked several times per member per tick - so at
     * five figures the tag walk, the substring and the parse were a real part of a crowd's tick
     * all by themselves.</p>
     */
    public static int entityIndex(Entity entity)
    {
        if (!(entity instanceof CrowdDrivenEntity driven))
        {
            return readIndex(entity);
        }

        int cached = driven.bbs$getCrowdIndex();

        if (cached != Integer.MIN_VALUE)
        {
            return cached;
        }

        int index = readIndex(entity);

        driven.bbs$setCrowdIndex(index);

        return index;
    }

    private static int readIndex(Entity entity)
    {
        for (String tag : entity.getCommandTags())
        {
            if (tag.startsWith(INDEX_TAG_PREFIX))
            {
                try
                {
                    return Integer.parseInt(tag.substring(INDEX_TAG_PREFIX.length()));
                }
                catch (NumberFormatException e)
                {}
            }
        }

        return Math.floorMod(entity.getUuid().hashCode(), 1000000);
    }

    public static UUID deterministicUuid(Film film, String crowdTag, int seed, int index)
    {
        String filmId = film == null ? "unknown" : film.getId();
        String key = filmId + "|" + crowdTag(crowdTag) + "|" + seed + "|" + index;

        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public static int entitySeed(Entity entity, int seed, int salt)
    {
        return hash(seed ^ entityIndex(entity) * 0x9e3779b9 ^ salt);
    }

    public static double randomUnit(int seed, int step, int salt)
    {
        return (hash(seed ^ step * 0x632be5ab ^ salt) & 0x00ffffff) / (double) 0x01000000;
    }

    public static double randomSigned(int seed, int step, int salt)
    {
        return randomUnit(seed, step, salt) * 2D - 1D;
    }

    public static int hash(int value)
    {
        int hash = value;

        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;

        return hash;
    }

    public static Vec3d personalOffset(LivingEntity mob, double spacing)
    {
        if (spacing <= 0D)
        {
            return Vec3d.ZERO;
        }

        int hash = entitySeed(mob, 0, 0x51f15e);
        double angle = (hash & 0xffff) / 65535D * Math.PI * 2D;
        double ring = 0.4D + ((hash >>> 16) & 0x7) / 7D;
        double radius = spacing * ring;

        return new Vec3d(MathHelper.cos((float) angle) * radius, 0D, MathHelper.sin((float) angle) * radius);
    }

    private static double randomUnit(int index, int salt)
    {
        int hash = index;

        hash ^= salt;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;

        return (hash & 0x00ffffff) / (double) 0x01000000;
    }

    private static double randomSigned(int index, int salt)
    {
        return randomUnit(index, salt) * 2D - 1D;
    }

    public static boolean isServerWorld(World world)
    {
        return world instanceof ServerWorld;
    }
}
