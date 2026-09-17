package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.crowd.CrowdBehavior;
import mchorse.bbs_mod.actions.crowd.CrowdJumpEvaluator;
import mchorse.bbs_mod.actions.crowd.CrowdLookEvaluator;
import mchorse.bbs_mod.actions.crowd.CrowdTexture;
import mchorse.bbs_mod.actions.crowd.CrowdLookTarget;
import mchorse.bbs_mod.actions.crowd.CrowdPaintEvaluator;
import mchorse.bbs_mod.actions.crowd.CrowdWalkEvaluator;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdDrivenEntity;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdPaintArea;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies a replay's crowd keyframes to the crowd it drives.
 *
 * <p>A crowd exists because the film says it does - that is {@link CrowdReconciler}'s job and it
 * is deliberately separate from this. What this does is the other half: read where the crowd
 * walks, what it looks at, when it jumps and what it wears at this tick, and put that on the
 * members. Keeping the two apart is the point; a crowd that failed to be animated is a still
 * crowd, not a missing one.</p>
 *
 * <p>A channel with no keyframes says nothing rather than saying zero, so a crowd can be walked
 * by keyframes and left to its behaviour clip for everything else.</p>
 */
public class CrowdKeyframeRuntime
{
    private static final float[] ROTATION = new float[2];

    /**
     * How far a member may turn in one tick while walking.
     *
     * <p>Fast enough to have come round before it matters, slow enough to read as turning. A
     * crowd that reaches its new heading in one tick all together is the most mechanical thing a
     * crowd can do, and that is what setting the yaw outright looked like.</p>
     */
    private static final float TURN_DEGREES_PER_TICK = 18F;

    /** Far enough off the floor to count as jumping rather than as rounding. */
    private static final double AIRBORNE_EPSILON = 0.08D;

    /** Vanilla jump strength, so a crowd jumps the height everything else in the world does. */
    private static final double JUMP_VELOCITY = 0.42D;

    private CrowdKeyframeRuntime()
    {}

    public static void apply(ServerWorld world, Film film, int tick, Map<String, LivingEntity> actors)
    {
        if (world == null || film == null || film.crowds.getList().isEmpty())
        {
            return;
        }

        for (Replay replay : film.replays.getList())
        {
            if (!replay.enabled.get() || !(replay.form.get() instanceof CrowdForm form))
            {
                continue;
            }

            Crowd crowd = film.crowds.byTag(form.crowd.get());

            if (crowd == null || !crowd.existsAt(tick))
            {
                continue;
            }

            /* Unlimited range: membership is by tag, and a crowd is routinely wider than any
             * box worth querying. See CrowdUtils#getCrowd. */
            List<LivingEntity> members = CrowdUtils.getCrowd(world, film, crowd.crowdTag.get(), Vec3d.ZERO, 0D);

            if (members.isEmpty())
            {
                continue;
            }

            boolean visible = replay.keyframes.crowdVisible.isEmpty()
                || replay.keyframes.crowdVisible.interpolate(replay.getTick(tick), true);

            for (LivingEntity member : members)
            {
                if (member.isInvisible() == visible)
                {
                    member.setInvisible(!visible);
                }
                member.setSilent(!visible);
                member.setInvulnerable(true);
                member.fallDistance = 0F;
                member.hurtTime = 0;
                member.maxHurtTime = 0;
            }

            /* A keyframed behaviour steers the members live (wander, run, freak out, stand), so it
             * owns their movement and jumping this tick - a walk route would only fight it. It does
             * not own where they look: a look keyframe is someone saying outright what the crowd
             * is watching, and a behaviour's own idle glancing is not an argument against it. */
            Set<LivingEntity> movingMembers = new HashSet<>();
            Map<LivingEntity, Float> walkHeadings = new HashMap<>();

            CrowdLookEvaluator.TargetResolver resolver = (target, filmTick) ->
            {
                if (actors != null)
                {
                    LivingEntity actor = actors.get(target.getId());

                    if (actor != null)
                    {
                        return actor.getEyePos();
                    }
                }

                if (target.form.get() instanceof CrowdForm)
                {
                    Vec3d center = CrowdUtils.getCrowdCenter(film, target, null, (int) filmTick);

                    if (center != null)
                    {
                        return center.add(0D, 1.62D, 0D);
                    }
                }

                return null;
            };

            CrowdLookEvaluator.Sample lookSample = CrowdLookEvaluator.sample(film, replay, tick, resolver);
            boolean hasLook = lookSample != null;
            boolean behaved = applyBehavior(world, film, replay, crowd, tick);

            if (behaved)
            {
                /* A behaviour steers members by velocity and leaves their vertical component alone
                 * (see moveEntity), so a real jump survives on top of it - a wandering crowd can be
                 * made to jump by the jump channel even though the behaviour's own hop only fires
                 * when a member is standing still. */
                applyJump(world, replay, members, tick);
            }
            else
            {
                /* The route places members outright, so a jump has to be part of that placement
                 * rather than a push applied afterwards - a push is undone by the next tick's
                 * placement, and anything that survives it does so by fighting the route. Only a
                 * crowd with no route left to fight gets a real jump. */
                boolean walked = applyWalk(world, film, replay, crowd, members, tick, movingMembers, walkHeadings, hasLook);

                if (!walked)
                {
                    applyJump(world, replay, members, tick);
                }
            }

            /* Last, so it is the final word on the head whatever moved the body this tick. */
            applyLook(film, replay, members, tick, lookSample, movingMembers, walkHeadings);

            applyTexture(replay, members, tick);
            applyColor(replay, members, tick);
        }
    }

    /**
     * One reusable behaviour clip per crowd tag. The clip is stateless between ticks (its
     * randomness is a pure function of tick and member), so reusing it just avoids reallocating
     * a heavy value tree every tick; keying by tag keeps two crowds from sharing per-tick fields.
     */
    private static final Map<String, CrowdBehaviorActionClip> BEHAVIOR_CLIPS = new HashMap<>();

    /**
     * Drive the crowd by its keyframed behaviour, if it has one, and report whether it did.
     *
     * <p>The keyframe just names a mode and a speed; the actual per-tick stepping - wander paths,
     * sprinting, jumping, panicking - is the behaviour clip's, configured from the keyframe and
     * run against the members where they already stand. Switching mode at a keyframe therefore
     * continues from the current positions rather than resetting anyone.</p>
     */
    private static boolean applyBehavior(ServerWorld world, Film film, Replay replay, Crowd crowd, int tick)
    {
        if (replay.keyframes.crowdBehavior.isEmpty())
        {
            return false;
        }

        CrowdBehavior behavior = replay.keyframes.crowdBehavior.interpolate(replay.getTick(tick));

        if (behavior == null)
        {
            return false;
        }

        SuperFakePlayer player = SuperFakePlayer.get(world);

        if (player == null)
        {
            return false;
        }

        CrowdBehavior.Kind kind = behavior.getKind();

        if (kind == CrowdBehavior.Kind.WALK)
        {
            return false;
        }

        CrowdBehaviorActionClip clip = BEHAVIOR_CLIPS.computeIfAbsent(crowd.crowdTag.get(), (tag) ->
        {
            CrowdBehaviorActionClip fresh = new CrowdBehaviorActionClip();

            /* No ease-in: the move-ease blend is measured from a clip's start tick, which a
             * keyframe-driven clip does not have. Anchor on spawn, since there is no replay to chase. */
            fresh.moveEase.set(0);
            fresh.target.set(CrowdBehaviorActionClip.TARGET_NONE);
            /* No look target: members should face where they walk (body, legs and head), which
             * moveEntity handles, rather than being turned to look at something. */
            fresh.lookAtTarget.set(false);

            return fresh;
        });

        clip.crowdTag.set(crowd.crowdTag.get());
        clip.mode.set(kind.mode.ordinal());
        clip.sprint.set(kind.sprint);
        clip.speed.set(behavior.effectiveSpeed());

        /* Freak out is meant to leap about, so it jumps by default when the keyframe leaves the
         * rate at 0; the calmer modes stay grounded unless a rate is dialled in. */
        float jumpRate = behavior.jumpRate > 0F ? behavior.jumpRate : (kind == CrowdBehavior.Kind.FREAK_OUT ? 1.5F : 0F);

        clip.randomJump.set(jumpRate > 0F);
        clip.jumpRate.set(jumpRate);

        clip.applyAction(null, player, film, replay, tick);

        return true;
    }

    /**
     * Walk the crowd along its waypoints.
     *
     * <p>Members are placed rather than steered. The waypoints already say where everyone is at
     * this tick, down to the stagger that makes the near edge leave first, so asking the
     * navigator to make its own way there would fight the curve the shot was authored on.</p>
     */
    private static boolean applyWalk(ServerWorld world, Film film, Replay replay, Crowd crowd,
        List<LivingEntity> members, int tick, Set<LivingEntity> movingMembers,
        Map<LivingEntity, Float> walkHeadings, boolean hasLook)
    {
        CrowdWalkEvaluator.Frame frame = CrowdWalkEvaluator.frame(replay, tick);
        int count = crowd.count.get();
        CrowdPaintEvaluator.Frame paintFrame = CrowdPaintEvaluator.frame(replay, crowd, tick, count);

        boolean hasWalk = frame != null && frame.list().size() >= 2;
        boolean hasPaint = paintFrame != null;

        if (!hasWalk && !hasPaint)
        {
            return false;
        }

        if (!hasWalk)
        {
            frame = null;
        }

        Vec3d anchor = crowdAnchor(film, crowd);
        CrowdFormation formation = crowd.getFormation();
        CrowdPaintArea paint = formation == CrowdFormation.PAINT ? new CrowdPaintArea(crowd.getCells()) : null;
        double spacing = crowd.spacing.get();
        double[] position = new double[3];

        Vec3d centre = crowdCentre(crowd, paint, formation, anchor, count, spacing);
        CrowdJumpEvaluator.Frame jump = CrowdJumpEvaluator.frame(replay, tick);

        for (LivingEntity member : members)
        {
            int index = CrowdUtils.entityIndex(member);
            Vec3d heading = null;

            if (paintFrame != null)
            {
                paintFrame.memberPosition(index, position);
                heading = paintFrame.memberFacing(index);

                if (frame != null)
                {
                    double[] walkOffset = new double[3];
                    CrowdWalkEvaluator.memberPosition(frame, index, 0D, 0D, 0D, 0D, 0D, 0D, walkOffset);
                    position[0] += walkOffset[0];
                    position[1] += walkOffset[1];
                    position[2] += walkOffset[2];

                    if (heading == null && frame.path().faceTravel)
                    {
                        heading = CrowdWalkEvaluator.memberFacing(frame, index);
                    }
                }
            }
            else
            {
                Vec3d base = memberBase(crowd, paint, formation, anchor, index, count, spacing);

                if (base == null)
                {
                    continue;
                }

                CrowdWalkEvaluator.memberPosition(frame, index, base.x, base.y, base.z,
                    centre.x, centre.y, centre.z, position);

                if (frame.path().faceTravel)
                {
                    heading = CrowdWalkEvaluator.memberFacing(frame, index);
                }
            }

            boolean terrainFollow = (frame != null && frame.path().terrainFollow)
                || (paintFrame != null && paintFrame.path().terrainFollow);

            if (terrainFollow)
            {
                position[1] = groundY(world, position[0], position[2], position[1]);
            }

            if (jump != null)
            {
                position[1] += jump.walkingHeight(index);
            }

            if (!Double.isFinite(position[0]) || !Double.isFinite(position[1]) || !Double.isFinite(position[2]))
            {
                continue;
            }

            double dx = position[0] - member.getX();
            double dz = position[2] - member.getZ();
            double moveDist = Math.sqrt(dx * dx + dz * dz);

            member.setPos(position[0], position[1], position[2]);
            member.setVelocity(Vec3d.ZERO);
            member.velocityDirty = true;
            member.fallDistance = 0F;
            member.setOnGround(true);
            member.setInvulnerable(true);
            member.hurtTime = 0;
            member.maxHurtTime = 0;

            boolean isRunning = (frame != null && frame.path().run) || (paintFrame != null && paintFrame.path().run);
            boolean isMoving = (frame != null && frame.moving()) || (paintFrame != null && paintFrame.isMoving(index));
            member.setSprinting(isRunning && isMoving);

            if (isMoving && movingMembers != null)
            {
                movingMembers.add(member);
            }

            if (moveDist > 0.001D && isMoving)
            {
                member.limbAnimator.updateLimbs((float) Math.min(moveDist * 4.0F, 1.5F), 0.4F);
            }

            if (heading != null)
            {
                float target = (float) (MathHelper.atan2(heading.z, heading.x) * (180D / Math.PI)) - 90F;
                float yaw = turnToward(member.getYaw(), target, TURN_DEGREES_PER_TICK);

                if (walkHeadings != null)
                {
                    walkHeadings.put(member, target);
                }

                if (!hasLook)
                {
                    member.setYaw(yaw);
                    member.setBodyYaw(yaw);
                    member.setHeadYaw(yaw);

                    if (member instanceof CrowdDrivenEntity driven)
                    {
                        driven.bbs$driveBodyYaw();
                    }
                }
            }
            else if (!hasLook && member instanceof CrowdDrivenEntity driven)
            {
                driven.bbs$driveBodyYaw();
            }
        }

        return true;
    }

    /** Rotate toward an angle by at most {@code maxStep}, the short way round. */
    private static float turnToward(float current, float target, float maxStep)
    {
        float delta = MathHelper.wrapDegrees(target - current);

        return MathHelper.wrapDegrees(current + MathHelper.clamp(delta, -maxStep, maxStep));
    }

    private static Vec3d crowdAnchor(Film film, Crowd crowd)
    {
        Replay anchor = CrowdUtils.getReplay(film, crowd.anchor.get());

        return anchor == null ? Vec3d.ZERO : CrowdUtils.replayPosition(anchor, crowd.start.get());
    }

    /**
     * The middle of the crowd's arrangement, for spread to push members away from.
     *
     * <p>A formation is built around its anchor, so that is the middle by construction. Painted
     * ground has whatever shape it was painted, so its middle is the average of the places
     * members actually stand - sampled rather than summed over every cell, since the count is
     * routinely in the thousands and this is wanted every tick.</p>
     */
    private static Vec3d crowdCentre(Crowd crowd, CrowdPaintArea paint, CrowdFormation formation,
        Vec3d anchor, int count, double spacing)
    {
        if (paint == null)
        {
            return anchor;
        }

        int samples = Math.min(count, 64);
        double x = 0D;
        double y = 0D;
        double z = 0D;
        int taken = 0;

        for (int i = 0; i < samples; i++)
        {
            Vec3d point = paint.point((int) ((long) i * count / samples), count, 0);

            if (point == null)
            {
                continue;
            }

            x += point.x;
            y += point.y;
            z += point.z;
            taken += 1;
        }

        return taken == 0 ? anchor : new Vec3d(x / taken, y / taken, z / taken);
    }

    private static Vec3d memberBase(Crowd crowd, CrowdPaintArea paint, CrowdFormation formation,
        Vec3d anchor, int index, int count, double spacing)
    {
        if (paint != null)
        {
            return paint.point(index, count, 0);
        }

        Vec3d offset = CrowdUtils.formationPoint(formation, index, count, spacing, crowd.holeRadius.get());

        return anchor.add(offset);
    }

    /** Keep the crowd's feet on the surface without loading chunks or levitating through ceilings. */
    private static double groundY(ServerWorld world, double x, double z, double fallback)
    {
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);

        if (world.getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false) == null)
        {
            return fallback;
        }

        int origin = MathHelper.floor(fallback);
        int bottomY = Math.max(world.getBottomY() + 1, origin - 16);
        int topY = Math.min(world.getTopY() - 2, origin + 16);

        if (topY < bottomY)
        {
            return fallback;
        }

        BlockPos.Mutable probe = new BlockPos.Mutable();

        /* Overhead ceiling detection:
         * If an entity is indoors or in a cave, any solid block overhead (from origin + 2 upwards)
         * forms a ceiling. The actor cannot pass through a solid ceiling, so any candidate floor
         * above that ceiling must not be reached. */
        int maxReachableY = topY;

        for (int y = origin + 2; y <= topY; y++)
        {
            BlockState state = world.getBlockState(probe.set(blockX, y, blockZ));

            if (!state.isAir() && !state.getCollisionShape(world, probe).isEmpty())
            {
                maxReachableY = y;
                break;
            }
        }

        /* Find the valid walkable surface closest to fallback within [bottomY, maxReachableY].
         * This ensures:
         * 1) Inside buildings/caves, the entity stays on the floor and never pops onto the roof or higher floors.
         * 2) Proximity to fallback ensures slopes, stairs, and stepping are tracked smoothly. */
        double bestSurfaceY = Double.NaN;
        double bestDist = Double.MAX_VALUE;

        for (int y = bottomY; y <= maxReachableY; y++)
        {
            BlockState belowState = world.getBlockState(probe.set(blockX, y - 1, blockZ));

            if (belowState.isAir())
            {
                continue;
            }

            VoxelShape belowShape = belowState.getCollisionShape(world, probe);

            if (belowShape.isEmpty())
            {
                continue;
            }

            double surface = (y - 1) + belowShape.getMax(Direction.Axis.Y);

            Box box = new Box(x - 0.25D, surface + 0.001D, z - 0.25D, x + 0.25D, surface + 1.799D, z + 0.25D);

            if (!world.isSpaceEmpty(null, box))
            {
                continue;
            }

            double dist = Math.abs(surface - fallback);

            if (dist < bestDist)
            {
                bestDist = dist;
                bestSurfaceY = surface;

                if (dist < 0.01D)
                {
                    break;
                }
            }
        }

        if (!Double.isNaN(bestSurfaceY) && bestDist <= 8.0D)
        {
            return bestSurfaceY;
        }

        return fallback;
    }

    /**
     * Make the jumping part of the crowd jump, the way the behaviour clip always has.
     *
     * <p>A real jump: upward velocity, and gravity to bring it back. The previous attempt placed
     * members along an arc of its own, which meant owning their vertical position outright - and
     * an authored height that disagrees with the floor by any amount puts them through it. Left
     * to the physics, a member lands on whatever is under it.</p>
     */
    private static void applyJump(ServerWorld world, Replay replay, List<LivingEntity> members, int tick)
    {
        CrowdJumpEvaluator.Frame frame = CrowdJumpEvaluator.frame(replay, tick);

        if (frame == null)
        {
            return;
        }

        for (LivingEntity member : members)
        {
            if (member.isTouchingWater())
            {
                continue;
            }

            /* Standing on the ground is measured, not asked for. A walked crowd is placed with
             * setPos, which goes around the movement code that maintains isOnGround - so the
             * flag reads false forever and every member was skipped here, which is a crowd that
             * never jumps at all. */
            double ground = groundY(world, member.getX(), member.getZ(), member.getY());

            if (member.getY() > ground + AIRBORNE_EPSILON)
            {
                continue;
            }

            int index = CrowdUtils.entityIndex(member);

            if (frame.jumps(index))
            {
                jump(member, frame.power(index));
            }
        }
    }

    /**
     * Push a member off the ground and let gravity have it back.
     *
     * <p>Done by velocity rather than through the mob's jump control, which only acts when the
     * mob's AI is ticking - and a crowd with No mob AI on has none, so that route would jump
     * some crowds and not others for a reason nobody could see from the outside.</p>
     */
    private static void jump(LivingEntity entity, double power)
    {
        Vec3d velocity = entity.getVelocity();

        entity.setVelocity(velocity.x, Math.max(velocity.y, JUMP_VELOCITY * power), velocity.z);
        entity.velocityModified = true;
        entity.velocityDirty = true;
        entity.setOnGround(false);
        entity.setJumping(true);
    }

    /**
     * How far a head turns before the shoulders have to come with it. Vanilla allows a mob about
     * this much, and it is the angle that separates a glance from a turn.
     */
    private static final float MAX_HEAD_TURN = 60F;

    /** How fast a head turns toward what it is watching. Brisk, but not instant. */
    private static final float HEAD_DEGREES_PER_TICK = 25F;

    private static void applyLook(Film film, Replay replay, List<LivingEntity> members, int tick,
        CrowdLookEvaluator.Sample sample, Set<LivingEntity> movingMembers, Map<LivingEntity, Float> walkHeadings)
    {
        if (sample == null)
        {
            return;
        }

        CrowdLookTarget control = sample.control();

        for (LivingEntity member : members)
        {
            float curYaw = member.getYaw();
            float curPitch = member.getPitch();

            if (!CrowdLookEvaluator.rotation(member.getX(), member.getEyeY(), member.getZ(), curYaw, curPitch, sample, ROTATION))
            {
                continue;
            }

            float lookYaw = ROTATION[0];
            float lookPitch = ROTATION[1];

            if (control.pitch())
            {
                member.setPitch(lookPitch);
            }

            if (control.headYaw())
            {
                member.setHeadYaw(lookYaw);
            }

            boolean isMoving = movingMembers != null && movingMembers.contains(member);
            float body;

            if (control.bodyYaw())
            {
                body = lookYaw;
            }
            else if (isMoving)
            {
                body = walkHeadings != null && walkHeadings.containsKey(member)
                    ? walkHeadings.get(member)
                    : curYaw;
            }
            else
            {
                body = member.getBodyYaw();
            }

            member.setBodyYaw(body);
            member.setYaw(body);

            if (member instanceof CrowdDrivenEntity driven)
            {
                driven.bbs$driveBodyYaw();
            }
        }
    }

    /**
     * Push a member's changed form to everyone watching it.
     *
     * <p>A member's form is sent once, when a client starts tracking it, and never again - so a
     * texture or colour written into it on a later tick stayed on the server and the crowd on
     * screen never changed. Re-sent only on the tick the value actually changes (the callers
     * guard on that), so a settled crowd costs nothing; a member's form goes out once per change,
     * not per tick.</p>
     *
     * <p>ponytail: whole-form resend per changed member. Fine at the scale a keyframed texture
     * change happens (once, on the keyframe); a diff packet would matter only if this ran per tick.</p>
     */
    private static void resyncForm(ActorEntity actor)
    {
        for (ServerPlayerEntity player : PlayerLookup.tracking(actor))
        {
            ServerNetwork.sendEntityForm(player, actor);
        }
    }

    /**
     * Dress the crowd.
     *
     * <p>One texture for everyone unless the keyframe asks for random, which is the only thing
     * in here that makes members differ from each other. Textures live on BBS model forms, so
     * members spawned as plain mobs have nothing to set and are left alone.</p>
     */
    private static void applyTexture(Replay replay, List<LivingEntity> members, int tick)
    {
        if (replay.keyframes.crowdTexture.isEmpty())
        {
            return;
        }

        CrowdTexture texture = replay.keyframes.crowdTexture.interpolate(replay.getTick(tick));

        if (texture == null)
        {
            return;
        }

        List<Link> folder = texture.random ? CrowdTextures.list(texture.folder, texture.recursive) : List.of();

        if (texture.random && folder.isEmpty())
        {
            return;
        }

        for (LivingEntity member : members)
        {
            if (!(member instanceof ActorEntity actor))
            {
                continue;
            }

            Form form = actor.getForm();

            if (form == null)
            {
                continue;
            }

            Link link = texture.random
                ? folder.get(Math.floorMod(CrowdUtils.entityIndex(member), folder.size()))
                : texture.texture;

            if (link == null)
            {
                continue;
            }

            BaseValue property = FormUtils.getProperty(form, "texture");

            if (property instanceof ValueLink value && !link.equals(value.get()))
            {
                value.set(link);
                actor.setForm(form);
                resyncForm(actor);
            }
        }
    }

    /** Tints the whole crowd together; there is no per-member colour. */
    private static void applyColor(Replay replay, List<LivingEntity> members, int tick)
    {
        if (replay.keyframes.crowdColor.isEmpty())
        {
            return;
        }

        Color color = replay.keyframes.crowdColor.interpolate(replay.getTick(tick));

        if (color == null)
        {
            return;
        }

        for (LivingEntity member : members)
        {
            if (!(member instanceof ActorEntity actor))
            {
                continue;
            }

            Form form = actor.getForm();

            if (form == null)
            {
                continue;
            }

            /* Colour is a property of the individual form types rather than of Form itself, so
             * it is looked up by name the same way the texture is. A form that has none - an
             * anchor, say - simply is not tinted. */
            BaseValue property = FormUtils.getProperty(form, "color");

            if (property instanceof ValueColor value && !value.get().equals(color))
            {
                value.set(color);
                actor.setForm(form);
                resyncForm(actor);
            }
        }
    }
}
