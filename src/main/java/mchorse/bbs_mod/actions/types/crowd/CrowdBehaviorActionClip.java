package mchorse.bbs_mod.actions.types.crowd;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.DamageActionClip;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class CrowdBehaviorActionClip extends ActionClip
{
    /** {@code target} value meaning "use this clip's own replay" (default). */
    public static final int TARGET_SELF = -1;
    /**
     * {@code target} value meaning "no replay target" — the crowd ignores every
     * replay and anchors to each mob's spawn point instead, so Wander mopes /
     * wanders around where they spawned rather than walking to a replay.
     */
    public static final int TARGET_NONE = -2;

    /** {@code enemyReplay} value meaning "no enemy replay". */
    public static final int ENEMY_NONE = -1;

    /**
     * Fallback crowd tag, for a clip whose replay drives no crowd.
     *
     * <p>A behaviour clip drives the crowd of the replay it sits on - see
     * {@link #resolveCrowdTag}. This is only read when there is no such crowd, which is how films
     * authored before that rule keep working: back then a clip could sit on an actor's timeline
     * and name any crowd it liked, and this is the name it wrote down.</p>
     */
    public final ValueString crowdTag = new ValueString("crowd_tag", "crowd_1");
    public final ValueInt target = new ValueInt("target", TARGET_SELF);
    public final ValueInt mode = new ValueInt("mode", CrowdBehaviorMode.FOLLOW.ordinal(), 0, CrowdBehaviorMode.values().length - 1);
    public final ValueBoolean pause = new ValueBoolean("pause", false);
    public final ValueBoolean sprint = new ValueBoolean("sprint", false);
    /** Movement speed in blocks per second. Vanilla walking is ~4.3, sprinting ~5.6. */
    public final ValueFloat speed = new ValueFloat("speed", 4.3F, 0F, 20F);
    public final ValueInt moveEase = new ValueInt("move_ease", 10, 0, 200);
    public final ValueFloat stopDistance = new ValueFloat("stop_distance", 1.5F, 0F, 32F);
    public final ValueFloat targetSpread = new ValueFloat("target_spread", 2F, 0F, 64F);
    public final ValueFloat disperseRadius = new ValueFloat("disperse_radius", 12F, 0F, 128F);
    public final ValueInt wanderInterval = new ValueInt("wander_interval", 80, 20, 400);
    public final ValueInt lookAroundTicks = new ValueInt("look_around_ticks", 25, 0, 200);
    public final ValueFloat range = new ValueFloat("range", 128F, 8F, 512F);
    public final ValueInt pathRefresh = new ValueInt("path_refresh", 5, 1, 40);
    public final ValueInt seed = new ValueInt("seed", 1);
    public final ValueFloat wanderRadius = new ValueFloat("wander_radius", 12F, 0.1F, 256F);

    /** Neighbour index for the tick being applied; null outside of it. Not saved. */
    private CrowdGrid grid;

    /** Fingerprint of the crowd last announced to the clients. Not saved. */
    private int announced;

    /* Where the target's eyes are this tick, when the target is a real entity rather than a
     * keyframed position. Watching an entity means watching its face; a fixed height above its
     * feet reads as staring at its chest, and past a few blocks the error is a visible squint. */
    private Vec3d targetEye;

    /** Scratch for {@link #getSeparationMotion}, see the note there. */
    private double pushX;
    private double pushZ;
    private int pushSamples;
    /** Set by {@link #tryAutoJump} while steering one member, read back when its velocity is written. */
    private boolean jumped;
    public final ValueFloat separation = new ValueFloat("separation", 0.85F, 0F, 6F);
    public final ValueFloat maxStepHeight = new ValueFloat("max_step_height", 0.55F, 0F, 0.75F);
    public final ValueBoolean autoJump = new ValueBoolean("auto_jump", true);
    public final ValueBoolean crouch = new ValueBoolean("crouch", false);
    public final ValueBoolean zigZag = new ValueBoolean("zig_zag", false);
    public final ValueBoolean randomJump = new ValueBoolean("random_jump", false);
    public final ValueFloat jumpRate = new ValueFloat("jump_rate", 0.25F, 0F, 5F);
    public final ValueBoolean armSwing = new ValueBoolean("arm_swing", false);
    public final ValueFloat armSwingRate = new ValueFloat("arm_swing_rate", 1F, 0F, 20F);
    public final ValueBoolean headMotion = new ValueBoolean("head_motion", true);
    public final ValueFloat energy = new ValueFloat("energy", 1F, 0F, 2F);
    public final ValueBoolean lookAtTarget = new ValueBoolean("look_at_target", true);
    public final ValueInt lookEase = new ValueInt("look_ease", 10, 0, 200);
    public final ValueFloat headYawLimit = new ValueFloat("head_yaw_limit", 70F, 0F, 120F);
    public final ValueBoolean lookBodyYaw = new ValueBoolean("look_body_yaw", false);
    public final ValueBoolean lookHeadYaw = new ValueBoolean("look_head_yaw", true);
    public final ValueBoolean lookHeadPitch = new ValueBoolean("look_head_pitch", true);
    /**
     * The replay whose crowd this one fights, by index, or {@link #ENEMY_NONE} for nobody.
     *
     * <p>Two crowds are set on each other by pointing one at the other's replay, the same way
     * {@link #target} names where to walk. It used to be a tag typed by hand into a box, which
     * had to match a crowd's own tag exactly and said nothing at all when it did not.</p>
     */
    public final ValueInt enemyReplay = new ValueInt("enemy_replay", ENEMY_NONE);
    /** Fallback enemy tag for films authored before {@link #enemyReplay}. */
    public final ValueString enemyGroup = new ValueString("enemy_group", "");
    public final ValueFloat fightDamage = new ValueFloat("fight_damage", 2F, 0F, 1024F);
    public final ValueFloat attackRate = new ValueFloat("attack_rate", 0.8F, 0F, 20F);
    public final ValueFloat engagementDistance = new ValueFloat("engagement_distance", 1.6F, 0.25F, 16F);
    public final ValueFloat fightRadius = new ValueFloat("fight_radius", 24F, 1F, 256F);
    public final ValueInt retargetTicks = new ValueInt("retarget_ticks", 30, 1, 400);
    public final ValueFloat fightRandomness = new ValueFloat("fight_randomness", 1F, 0F, 8F);
    public final ValueBoolean shoot = new ValueBoolean("shoot", false);
    public final ValueFloat shootRate = new ValueFloat("shoot_rate", 0.5F, 0F, 20F);
    public final ValueItemStack shootItem = new ValueItemStack("shoot_item");
    public final ValueForm projectileModel = new ValueForm("projectile_model");
    public final ValueFloat projectileSpeed = new ValueFloat("projectile_speed", 1.6F, 0F, 16F);
    public final ValueInt projectileLifeSpan = new ValueInt("projectile_life_span", 100, 1, 1200);
    public final ValueForm impactModel = new ValueForm("impact_model");
    public final ValueInt impactBounces = new ValueInt("impact_bounces", 0, 0, 64);
    public final ValueFloat impactBounceDamping = new ValueFloat("impact_bounce_damping", 0.5F, 0F, 1F);
    public final ValueBoolean impactVanish = new ValueBoolean("impact_vanish", true);
    public final ValueFloat impactDamage = new ValueFloat("impact_damage", 0F, 0F, 1024F);
    public final ValueFloat impactKnockback = new ValueFloat("impact_knockback", 0F, 0F, 64F);
    public final ValueBoolean impactCollideBlocks = new ValueBoolean("impact_collide_blocks", true);
    public final ValueBoolean impactCollideEntities = new ValueBoolean("impact_collide_entities", true);
    public final ValueString cmdFiring = new ValueString("cmd_firing", "");
    public final ValueString cmdImpact = new ValueString("cmd_impact", "");
    public final ValueString cmdVanish = new ValueString("cmd_vanish", "");
    public final ValueString cmdTicking = new ValueString("cmd_ticking", "");
    public final ValueInt ticking = new ValueInt("ticking", 0, 0, 1200);

    public CrowdBehaviorActionClip()
    {
        super();

        this.shootItem.set(new ItemStack(Items.SNOWBALL));

        this.add(this.crowdTag);
        this.add(this.target);
        this.add(this.mode);
        this.add(this.pause);
        this.add(this.sprint);
        this.add(this.speed);
        this.add(this.moveEase);
        this.add(this.stopDistance);
        this.add(this.targetSpread);
        this.add(this.disperseRadius);
        this.add(this.wanderInterval);
        this.add(this.lookAroundTicks);
        this.add(this.range);
        this.add(this.pathRefresh);
        this.add(this.seed);
        this.add(this.wanderRadius);
        this.add(this.separation);
        this.add(this.maxStepHeight);
        this.add(this.autoJump);
        this.add(this.crouch);
        this.add(this.zigZag);
        this.add(this.randomJump);
        this.add(this.jumpRate);
        this.add(this.armSwing);
        this.add(this.armSwingRate);
        this.add(this.headMotion);
        this.add(this.energy);
        this.add(this.lookAtTarget);
        this.add(this.lookEase);
        this.add(this.headYawLimit);
        this.add(this.lookBodyYaw);
        this.add(this.lookHeadYaw);
        this.add(this.lookHeadPitch);
        this.add(this.enemyReplay);
        this.add(this.enemyGroup);
        this.add(this.fightDamage);
        this.add(this.attackRate);
        this.add(this.engagementDistance);
        this.add(this.fightRadius);
        this.add(this.retargetTicks);
        this.add(this.fightRandomness);
        this.add(this.shoot);
        this.add(this.shootRate);
        this.add(this.shootItem);
        this.add(this.projectileModel);
        this.add(this.projectileSpeed);
        this.add(this.projectileLifeSpan);
        this.add(this.impactModel);
        this.add(this.impactBounces);
        this.add(this.impactBounceDamping);
        this.add(this.impactVanish);
        this.add(this.impactDamage);
        this.add(this.impactKnockback);
        this.add(this.impactCollideBlocks);
        this.add(this.impactCollideEntities);
        this.add(this.cmdFiring);
        this.add(this.cmdImpact);
        this.add(this.cmdVanish);
        this.add(this.cmdTicking);
        this.add(this.ticking);

        this.frequency.set(1);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        if (player == null || !CrowdUtils.isServerWorld(player.getWorld()) || film == null)
        {
            return;
        }

        boolean noTarget = this.target.get() == TARGET_NONE;
        Vec3d gatherPos;
        Replay targetReplay = null;

        /* Per-tick state: a target that has gone away must not leave the crowd watching where
         * its eyes used to be. */
        this.targetEye = null;

        if (noTarget)
        {
            /* No replay target: anchor each mob to its own spawn point. We only
             * need a rough centre to find the tagged crowd, so use the clip's
             * own replay position for that gather query. */
            if (replay == null)
            {
                return;
            }

            gatherPos = CrowdUtils.replayPosition(replay, tick);
        }
        else
        {
            targetReplay = CrowdUtils.getReplay(film, this.target.get());

            if (targetReplay == null)
            {
                targetReplay = replay;
            }

            if (targetReplay == null)
            {
                return;
            }

            gatherPos = CrowdUtils.replayPosition(targetReplay, tick);

            /* The keyframe track only knows where the target was authored to be. A replay with
             * no position keyframes reads as the world origin, which puts the gather point
             * thousands of blocks away and leaves the crowd standing still. The actor playing
             * that replay knows where it really is, so prefer it whenever it exists. */
            LivingEntity targetActor = this.resolveReplayTargetEntity(film, targetReplay);

            if (targetActor != null && targetActor.isAlive())
            {
                gatherPos = targetActor.getPos();
                this.targetEye = eyePoint(targetActor);
            }
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        CrowdBehaviorMode mode = CrowdBehaviorMode.get(this.mode.get());
        /* The whole tagged crowd, not just the part standing near the target - a target far from
         * the formation would otherwise only ever pull in the members closest to it, leaving the
         * rest of the crowd standing where they spawned. */
        List<LivingEntity> crowd = CrowdUtils.getCrowd(world, film, this.resolveCrowdTag(replay), gatherPos, 0D);

        if (crowd.isEmpty())
        {
            return;
        }

        this.announceCrowd(world, crowd);

        List<LivingEntity> enemies = this.getEnemyCrowd(world, film, gatherPos, mode);
        LivingEntity replayEnemy = mode == CrowdBehaviorMode.FIGHT
            ? this.resolveReplayTargetEntity(film, noTarget ? null : targetReplay)
            : null;
        double stopDistance = Math.max(0D, this.stopDistance.get());
        double stopDistanceSq = stopDistance * stopDistance;
        double speed = Math.max(0D, this.speed.get()) * this.getMoveBlend(tick);
        int refresh = Math.max(1, this.pathRefresh.get());
        double jumpChancePerTick = this.randomJump.get() ? Math.min(1D, Math.max(0D, this.jumpRate.get()) / 20D) : 0D;
        float shootRate = this.shoot.get() ? Math.max(0F, this.shootRate.get()) : 0F;
        boolean paused = this.pause.get() || speed <= 0.0001D;
        GunProperties projectileProperties = shootRate > 0F ? this.createProjectileProperties() : null;
        float lookBlend = this.getLookBlend(tick);

        /* Neighbour lookups below run once per member, so they get an index instead of a scan
         * over the whole crowd. Held in a field rather than threaded through five signatures;
         * this only ever runs on the server tick, one clip at a time.
         *
         * Cell size is what makes the index worth having. Sizing it for the widest query (the
         * 8-block glance) makes the nine cells a 24-block square, which in a packed crowd holds
         * hundreds of members - so separation, which only cares about a metre or so, walked
         * hundreds of candidates per member and put the server thread right back into quadratic
         * work. Size it for the tight query instead; the glance simply settles for a nearer
         * neighbour, which is not something the shot can show. */
        this.grid = new CrowdGrid(crowd, Math.max(1D, this.separation.get()));

        for (LivingEntity entity : crowd)
        {
            if (!entity.isAlive() || entity.isRemoved())
            {
                continue;
            }

            this.prepareCrowdEntity(entity);

            FightTarget fightTarget = mode == CrowdBehaviorMode.FIGHT ? this.chooseFightTarget(entity, enemies, replayEnemy, gatherPos, tick) : null;
            boolean lookingAround = this.isLookAroundPause(entity, mode, tick);
            Vec3d destination = fightTarget == null
                ? this.getDestination(entity, gatherPos, mode, tick, noTarget)
                : fightTarget.position;
            double distanceSq = entity.squaredDistanceTo(destination);
            boolean holding = mode == CrowdBehaviorMode.HOLD;
            double effectiveStopDistanceSq = fightTarget == null ? stopDistanceSq : this.engagementDistance.get() * this.engagementDistance.get();
            boolean moving = !holding && !lookingAround && !paused && distanceSq > effectiveStopDistanceSq;
            boolean sprinting = this.sprint.get() && moving;

            entity.setSprinting(sprinting);
            entity.setSneaking(this.crouch.get() || mode == CrowdBehaviorMode.SAD_WALK || (mode == CrowdBehaviorMode.PANIC && this.shouldCrouchInPanic(entity, tick)));
            this.moveEntity(world, entity, destination, speed, moving, refresh, tick, crowd);

            Vec3d lookPoint = fightTarget == null
                ? this.getLookPoint(entity, gatherPos, mode, tick, noTarget, crowd)
                : fightTarget.lookPoint;

            this.applyLook(entity, lookPoint, mode, tick, lookBlend);
            this.applyPerformanceMotion(entity, mode, tick, crowd);
            this.constrainHeadYaw(entity);

            double jumpChance = mode == CrowdBehaviorMode.CHEER
                ? Math.max(jumpChancePerTick, 0.045D * Math.max(0.2D, this.energy.get()))
                : jumpChancePerTick;

            if (mode == CrowdBehaviorMode.SAD_WALK)
            {
                jumpChance = 0D;
            }

            if (jumpChance > 0D && !moving && entity.isOnGround() && !entity.isTouchingWater() && this.deterministicChance(entity, tick, jumpChance, 0x5a91))
            {
                this.jump(entity);
            }

            float swingRate = (this.armSwing.get() || mode == CrowdBehaviorMode.CHEER) ? this.armSwingRate.get() : 0F;

            if (mode == CrowdBehaviorMode.CHEER)
            {
                swingRate = Math.max(swingRate, 1.25F * Math.max(0.25F, this.energy.get()));
            }

            if (swingRate > 0F && shouldPulse(entity, tick, swingRate))
            {
                entity.swingHand(Hand.MAIN_HAND);
            }

            if (fightTarget != null)
            {
                this.applyFight(entity, fightTarget, tick);
            }

            if (sprinting && moving)
            {
                this.spawnSprintParticles(world, entity, tick);
            }

            if (!noTarget && projectileProperties != null && shouldShoot(entity, tick, shootRate))
            {
                this.shootSnowballLike(world, entity, gatherPos, projectileProperties);
            }
        }

        this.grid = null;
    }

    /**
     * Tell the clients who this crowd is, when the answer has changed.
     *
     * <p>They need to know because a body's facing is worked out on the client, from movement,
     * rather than sent - so without this the torsos ignore everything the clip says. Announced
     * from here rather than from the spawner because this is the one place that already has the
     * whole crowd in hand every tick, so respawning, sweeping and a member dying are all covered
     * by the same check. The fingerprint is what keeps it from being a per-tick broadcast: it
     * only misses a change that swaps one member for another of the same count and identical
     * combined ids, which the next change corrects.</p>
     */
    private void announceCrowd(ServerWorld world, List<LivingEntity> crowd)
    {
        int fingerprint = crowd.size();

        for (LivingEntity entity : crowd)
        {
            fingerprint = fingerprint * 31 + entity.getId();
        }

        if (fingerprint == this.announced)
        {
            return;
        }

        this.announced = fingerprint;

        IntList ids = new IntArrayList(crowd.size());

        for (LivingEntity entity : crowd)
        {
            ids.add(entity.getId());
        }

        ServerNetwork.sendCrowdMembers(world, ids);
    }

    private void prepareCrowdEntity(LivingEntity entity)
    {
        entity.noClip = false;
        entity.setStepHeight(Math.min(entity.getStepHeight(), Math.max(0F, Math.min(0.75F, this.maxStepHeight.get()))));

        /* A falling entity that covers a block in one tick has vanilla raycast its fall path to
         * find what it is about to land on. That walk reads blocks the entity has not reached
         * yet, and reading one in terrain that has not been generated makes the server generate
         * it there and then - minutes, on the server thread, inside a single tick, which the
         * watchdog then reports as a dead server. It is the one block read in movement that the
         * loaded-chunk guard cannot see coming, because the path is decided after the step.
         *
         * A crowd member carries no fall distance, so the check never fires. They are placed on
         * the surface and driven every tick anyway, so there is nothing for it to tell us, and
         * it also spares them fall damage on a slope. */
        entity.fallDistance = 0F;
        entity.setInvulnerable(true);
        entity.hurtTime = 0;
        entity.maxHurtTime = 0;

        if (entity instanceof MobEntity mob)
        {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }

    /**
     * The crowd this clip drives: the one belonging to the replay it sits on.
     *
     * <p>A crowd replay owns exactly one crowd, so the timeline the clip was dropped onto already
     * says which crowd it is about, and there is nothing to pick and nothing to get wrong. The
     * stored tag is the fallback for a clip on a replay that drives no crowd, which only old
     * films have.</p>
     */
    public String resolveCrowdTag(Replay replay)
    {
        String tag = CrowdUtils.replayCrowdTag(replay);

        return tag == null ? this.crowdTag.get() : tag;
    }

    /** The crowd this one fights: the one belonging to the chosen enemy replay. */
    private String resolveEnemyTag(Film film)
    {
        String tag = CrowdUtils.replayCrowdTag(film, this.enemyReplay.get());

        if (tag != null)
        {
            return tag;
        }

        /* Only an old film reaches here, and only until its enemy is re-picked. A clip that has
         * never named an enemy leaves both empty, which is no fight rather than a fight with
         * whatever crowd_1 happens to be. */
        String group = this.enemyGroup.get() == null ? "" : this.enemyGroup.get().trim();

        return group.isEmpty() ? null : group;
    }

    private List<LivingEntity> getEnemyCrowd(ServerWorld world, Film film, Vec3d center, CrowdBehaviorMode mode)
    {
        if (mode != CrowdBehaviorMode.FIGHT)
        {
            return List.of();
        }

        String group = this.resolveEnemyTag(film);

        if (group == null)
        {
            return List.of();
        }

        return CrowdUtils.getCrowd(world, film, group, center, Math.max(this.range.get(), this.fightRadius.get()));
    }

    /**
     * Turns the chosen target replay into the living entity currently performing it, so a
     * crowd can fight a specific actor and not just an enemy group. The player publishes the
     * cast every applied tick; before playback starts there is nobody to resolve.
     */
    private LivingEntity resolveReplayTargetEntity(Film film, Replay targetReplay)
    {
        if (this.target.get() == TARGET_NONE || targetReplay == null)
        {
            return null;
        }

        if (this.target.get() == DamageActionClip.recordingReplay)
        {
            return DamageActionClip.recordingPlayer;
        }

        if (DamageActionClip.actorContext == null)
        {
            return null;
        }

        return DamageActionClip.actorContext.get(targetReplay.getId());
    }

    private FightTarget chooseFightTarget(LivingEntity entity, List<LivingEntity> enemies, LivingEntity replayEnemy,
        Vec3d fallback, int tick)
    {
        List<LivingEntity> candidates = new ArrayList<>();

        for (LivingEntity enemy : enemies)
        {
            if (this.isValidEnemy(entity, enemy))
            {
                candidates.add(enemy);
            }
        }

        if (this.isValidEnemy(entity, replayEnemy))
        {
            candidates.add(replayEnemy);
        }

        if (candidates.isEmpty())
        {
            return new FightTarget(null, fallback, fallback.add(0D, 1.25D, 0D));
        }

        int targetStep = Math.floorDiv(Math.max(0, tick - this.tick.get()), Math.max(1, this.retargetTicks.get()));
        int preferred = Math.floorMod(CrowdUtils.entityIndex(entity) + targetStep, candidates.size());
        double randomness = Math.max(0D, this.fightRandomness.get());
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++)
        {
            LivingEntity candidate = candidates.get(i);
            double distance = entity.squaredDistanceTo(candidate);
            double assignment = Math.floorMod(i - preferred, candidates.size()) * Math.max(0.25D, this.separation.get());
            double jitter = CrowdUtils.randomUnit(CrowdUtils.entitySeed(entity, this.seed.get(), 0x312d), targetStep, 0x2000 + CrowdUtils.entityIndex(candidate)) * randomness;
            double score = distance + assignment + jitter;

            if (score < bestScore)
            {
                best = candidate;
                bestScore = score;
            }
        }

        Vec3d pos = best == null ? fallback : best.getPos();

        return new FightTarget(best, pos, best == null ? pos.add(0D, 1.25D, 0D) : eyePoint(best));
    }

    private boolean isValidEnemy(LivingEntity entity, LivingEntity enemy)
    {
        return enemy != null && enemy != entity && enemy.isAlive() && !enemy.isRemoved();
    }

    private void applyFight(LivingEntity entity, FightTarget target, int tick)
    {
        if (target.entity == null || this.fightDamage.get() <= 0F || this.attackRate.get() <= 0F)
        {
            return;
        }

        double reach = Math.max(0.25D, this.engagementDistance.get() + 0.25D);

        if (entity.squaredDistanceTo(target.entity) > reach * reach)
        {
            return;
        }

        if (!this.shouldPulse(entity, tick, this.attackRate.get(), 0x5f17))
        {
            return;
        }

        float damage = Math.max(0.001F, this.fightDamage.get());
        float before = target.entity.getHealth();
        DamageSource source = entity.getWorld().getDamageSources().mobAttack(entity);

        entity.swingHand(Hand.MAIN_HAND);
        target.entity.timeUntilRegen = 0;
        target.entity.damage(source, damage);

        float exact = Math.max(0F, before - damage);

        if (target.entity.getHealth() != exact)
        {
            target.entity.setHealth(exact);
        }
    }

    private boolean deterministicChance(LivingEntity entity, int tick, double chance, int salt)
    {
        if (chance <= 0D)
        {
            return false;
        }

        if (chance >= 1D)
        {
            return true;
        }

        return CrowdUtils.randomUnit(CrowdUtils.entitySeed(entity, this.seed.get(), salt), tick - this.tick.get(), salt ^ 0x55aa) < chance;
    }

    private boolean shouldCrouchInPanic(LivingEntity entity, int tick)
    {
        int phase = Math.floorMod(tick - this.tick.get() + CrowdUtils.entitySeed(entity, this.seed.get(), 0x4f00), 80);

        return phase < 6;
    }

    private float getLookBlend(int tick)
    {
        return this.getEaseBlend(tick, this.lookEase.get());
    }

    private float getMoveBlend(int tick)
    {
        return this.getEaseBlend(tick, this.moveEase.get());
    }

    private float getEaseBlend(int tick, int ease)
    {
        if (ease <= 0)
        {
            return 1F;
        }

        float t = Math.min(1F, Math.max(0F, (tick - this.tick.get()) / (float) ease));

        return t * t * (3F - 2F * t);
    }

    private boolean isLookAroundPause(LivingEntity entity, CrowdBehaviorMode mode, int tick)
    {
        if (mode != CrowdBehaviorMode.WANDER_LOOK)
        {
            return false;
        }

        int cycle = Math.max(20, this.wanderInterval.get());
        int pause = Math.min(cycle - 1, Math.max(0, this.lookAroundTicks.get()));

        if (pause <= 0)
        {
            return false;
        }

        int offset = Math.floorMod(CrowdUtils.entitySeed(entity, this.seed.get(), 0x1c77), cycle);
        int phase = Math.floorMod(tick - this.tick.get() + offset, cycle);

        return phase < pause;
    }

    private Vec3d getDestination(LivingEntity entity, Vec3d targetPos, CrowdBehaviorMode mode, int tick, boolean noTarget)
    {
        /* No replay target: every mode roams freely (each mob picks its own
         * drifting direction) instead of walking towards a replay. HOLD is
         * already kept stationary by the caller. The Wander+look idle pause
         * still stops them so they can glance around. */
        if (noTarget)
        {
            if (mode == CrowdBehaviorMode.HOLD || mode == CrowdBehaviorMode.IDLE_CROWD || mode == CrowdBehaviorMode.WATCH || mode == CrowdBehaviorMode.MEETING)
            {
                return entity.getPos();
            }

            if (mode == CrowdBehaviorMode.WANDER_LOOK && this.isLookAroundPause(entity, mode, tick))
            {
                return entity.getPos();
            }

            return this.getFreeWanderDestination(entity, tick);
        }

        Vec3d personal = CrowdUtils.personalOffset(entity, this.targetSpread.get());

        if (mode == CrowdBehaviorMode.WANDER || mode == CrowdBehaviorMode.WANDER_LOOK || mode == CrowdBehaviorMode.MARKET || mode == CrowdBehaviorMode.WORKERS || mode == CrowdBehaviorMode.GUARD_PATROL)
        {
            if (this.isLookAroundPause(entity, mode, tick))
            {
                return entity.getPos();
            }

            return this.getWanderDestination(entity, targetPos, tick);
        }

        if (mode == CrowdBehaviorMode.GATHER || mode == CrowdBehaviorMode.WATCH || mode == CrowdBehaviorMode.IDLE_CROWD || mode == CrowdBehaviorMode.MEETING)
        {
            return targetPos.add(personal.multiply(mode == CrowdBehaviorMode.MEETING ? 1.45D : 1D));
        }

        if (mode == CrowdBehaviorMode.DISPERSE || mode == CrowdBehaviorMode.FLEE || mode == CrowdBehaviorMode.PANIC)
        {
            Vec3d direction = this.horizontalDirection(personal, entity);
            double radius = Math.max(this.targetSpread.get(), this.disperseRadius.get());

            return targetPos.add(direction.x * radius, 0D, direction.z * radius);
        }

        if (mode == CrowdBehaviorMode.SAD_WALK || this.zigZag.get())
        {
            Vec3d direction = this.horizontalDirection(personal, entity);
            Vec3d side = new Vec3d(-direction.z, 0D, direction.x);
            double wave = Math.sin((tick + entity.getId() * 13) * 0.11D) * Math.min(2.5D, Math.max(0D, this.targetSpread.get()));

            personal = personal.add(side.multiply(wave));
        }

        return targetPos.add(personal);
    }

    /**
     * Free, un-anchored wandering used by the "None" target. Each wander cycle
     * the mob picks a fresh random heading and aims a few blocks ahead of its
     * CURRENT position, so the destination always stays reachable and ahead —
     * the mob keeps strolling and simply turns every cycle, instead of fighting
     * to reach a fixed (often blocked) point and shuffling in place.
     */
    private Vec3d getFreeWanderDestination(LivingEntity entity, int tick)
    {
        int cycle = Math.max(20, this.wanderInterval.get());
        int relative = Math.max(0, tick - this.tick.get());
        int seed = CrowdUtils.entitySeed(entity, this.seed.get(), 0x6021);
        int step = Math.floorDiv(relative + Math.floorMod(seed, cycle), cycle);
        double angle = CrowdUtils.randomUnit(seed, step, 0x3f91) * Math.PI * 2D;
        double radius = Math.max(2D, this.disperseRadius.get());
        double distance = Math.max(3D, radius * (0.55D + CrowdUtils.randomUnit(seed, step, 0x71ab) * 0.6D));
        Vec3d pos = entity.getPos();

        return pos.add(Math.cos(angle) * distance, 0D, Math.sin(angle) * distance);
    }

    private Vec3d getWanderDestination(LivingEntity entity, Vec3d center, int tick)
    {
        int cycle = Math.max(20, this.wanderInterval.get());
        int relative = Math.max(0, tick - this.tick.get());
        int seed = CrowdUtils.entitySeed(entity, this.seed.get(), 0x729f);
        int step = Math.floorDiv(relative + Math.floorMod(seed, cycle), cycle);
        /* Sample a disc rather than a box, so wandering stays inside the same round area the
         * editor draws. sqrt keeps the picks even instead of crowding the middle. */
        double radius = Math.max(0.1D, this.wanderRadius.get());
        double angle = CrowdUtils.randomUnit(seed, step, 0x3f91) * Math.PI * 2D;
        double distance = Math.sqrt(CrowdUtils.randomUnit(seed, step, 0x71ab)) * radius;

        return center.add(Math.cos(angle) * distance, 0D, Math.sin(angle) * distance);
    }

    private Vec3d getLookPoint(LivingEntity entity, Vec3d targetPos, CrowdBehaviorMode mode, int tick, boolean noTarget, List<LivingEntity> crowd)
    {
        if (mode == CrowdBehaviorMode.WANDER_LOOK && this.isLookAroundPause(entity, mode, tick))
        {
            float limit = Math.max(20F, Math.min(120F, this.headYawLimit.get()));
            double wave = Math.sin((tick + entity.getId() * 19) * 0.12D);
            double yaw = Math.toRadians(entity.getBodyYaw() + wave * limit);
            double distance = 4D;

            return entity.getPos().add(-Math.sin(yaw) * distance, entity.getEyeHeight(entity.getPose()), Math.cos(yaw) * distance);
        }

        /* No replay target: don't stare anywhere — face the direction of travel
         * (handled by the movement code) so free-wandering looks natural. */
        if (noTarget && mode != CrowdBehaviorMode.TALK && mode != CrowdBehaviorMode.MEETING && mode != CrowdBehaviorMode.IDLE_CROWD)
        {
            return null;
        }

        if (mode == CrowdBehaviorMode.WANDER || mode == CrowdBehaviorMode.WANDER_LOOK || mode == CrowdBehaviorMode.MARKET || mode == CrowdBehaviorMode.WORKERS || mode == CrowdBehaviorMode.GUARD_PATROL)
        {
            return this.getWanderDestination(entity, targetPos, tick).add(0D, 1.25D, 0D);
        }

        if (mode == CrowdBehaviorMode.TALK || mode == CrowdBehaviorMode.MEETING)
        {
            LivingEntity conversationTarget = this.getConversationTarget(entity, crowd, tick);

            if (conversationTarget != null)
            {
                return eyePoint(conversationTarget);
            }

            Vec3d personal = CrowdUtils.personalOffset(entity, Math.max(1D, this.targetSpread.get()));

            return targetPos.subtract(personal.x, -1.1D, personal.z);
        }

        if (mode == CrowdBehaviorMode.IDLE_CROWD)
        {
            LivingEntity glance = this.getNearbyGlanceTarget(entity, crowd, tick);

            if (glance != null)
            {
                return eyePoint(glance);
            }
        }

        return this.targetEye == null ? targetPos.add(0D, 1.25D, 0D) : this.targetEye;
    }

    private LivingEntity getConversationTarget(LivingEntity entity, List<LivingEntity> crowd, int tick)
    {
        if (crowd.size() <= 1)
        {
            return null;
        }

        int turn = Math.floorDiv(Math.max(0, tick - this.tick.get()), 38);
        int speakerIndex = Math.floorMod(turn, crowd.size());
        LivingEntity speaker = crowd.get(speakerIndex);

        if (!this.isValidConversationEntity(speaker))
        {
            return null;
        }

        if (speaker == entity)
        {
            int self = Math.max(0, crowd.indexOf(entity));

            for (int i = 1; i < crowd.size(); i++)
            {
                LivingEntity candidate = crowd.get(Math.floorMod(self + i, crowd.size()));

                if (this.isValidConversationEntity(candidate))
                {
                    return candidate;
                }
            }

            return null;
        }

        return speaker;
    }

    private boolean isConversationSpeaker(LivingEntity entity, List<LivingEntity> crowd, int tick)
    {
        if (crowd.isEmpty())
        {
            return false;
        }

        int turn = Math.floorDiv(Math.max(0, tick - this.tick.get()), 38);
        int speakerIndex = Math.floorMod(turn, crowd.size());

        return crowd.get(speakerIndex) == entity;
    }

    private boolean isValidConversationEntity(LivingEntity entity)
    {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private LivingEntity getNearbyGlanceTarget(LivingEntity entity, List<LivingEntity> crowd, int tick)
    {
        if (crowd.size() <= 1)
        {
            return null;
        }

        /* Only members within 8 blocks can be glanced at, so search the buckets that can hold
         * them instead of walking the whole crowd to find the same handful. */
        List<LivingEntity> nearby = this.grid == null ? crowd : this.grid.neighbours(entity.getX(), entity.getZ());

        if (nearby.isEmpty())
        {
            return null;
        }

        int step = Math.floorDiv(Math.max(0, tick - this.tick.get()), 55);
        int start = Math.floorMod(CrowdUtils.entityIndex(entity) + step, nearby.size());
        double maxDistanceSq = 8D * 8D;

        for (int i = 0; i < nearby.size(); i++)
        {
            LivingEntity candidate = nearby.get(Math.floorMod(start + i, nearby.size()));

            if (candidate != entity && this.isValidConversationEntity(candidate) && candidate.squaredDistanceTo(entity) <= maxDistanceSq)
            {
                return candidate;
            }
        }

        return null;
    }

    private static Vec3d eyePoint(LivingEntity entity)
    {
        return entity.getPos().add(0D, entity.getEyeHeight(entity.getPose()), 0D);
    }

    private Vec3d horizontalDirection(Vec3d vector, LivingEntity entity)
    {
        Vec3d direction = new Vec3d(vector.x, 0D, vector.z);

        if (direction.lengthSquared() < 1.0E-6D)
        {
            double angle = (CrowdUtils.entitySeed(entity, this.seed.get(), 0x4241) & 0xffff) / 65535D * Math.PI * 2D;

            direction = new Vec3d(Math.cos(angle), 0D, Math.sin(angle));
        }

        return direction.normalize();
    }

    private void moveEntity(ServerWorld world, LivingEntity entity, Vec3d destination, double speed, boolean moving, int refresh, int tick, List<LivingEntity> crowd)
    {
        if (!moving)
        {
            this.stopHorizontal(entity);

            return;
        }

        /* A destination that isn't a number - an actor with no resolvable position, a keyframe
         * track that evaluated to nothing - would otherwise travel through the arithmetic below
         * untouched and be written into the member's position, where it becomes a permanent
         * defect in the save rather than a bad frame. */
        if (!isFinite(destination.x) || !isFinite(destination.y) || !isFinite(destination.z))
        {
            this.stopHorizontal(entity);

            return;
        }

        this.jumped = false;

        Vec3d delta = destination.subtract(entity.getPos());
        Vec3d horizontal = new Vec3d(delta.x, 0D, delta.z);
        Vec3d separation = this.getSeparationMotion(entity, crowd);

        if (separation.lengthSquared() > 1.0E-6D)
        {
            horizontal = horizontal.add(separation);
        }

        if (horizontal.lengthSquared() < 1.0E-5D)
        {
            this.stopHorizontal(entity);

            return;
        }

        double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        /* Speed is blocks per second: the velocity set here is what the entity actually travels
         * this tick (vanilla applies friction after the move, and we overwrite it next tick), so
         * one tick of travel is simply speed / 20. Vanilla walking is ~4.3 for reference. */
        double maxStep = MathUtils.clamp(speed / 20D, 0.005D, 1D);
        /* Ease off over the last stretch so they settle onto the spot instead of overshooting,
         * but never below a quarter speed or the final approach turns into a crawl. */
        double step = Math.min(maxStep, Math.max(maxStep * 0.25D, distance * 0.18D));
        Vec3d desired = horizontal.normalize().multiply(step);
        Vec3d velocity = entity.getVelocity();
        double blend = 0.28D + Math.min(0.22D, speed * 0.035D);
        /* Ease towards the step wanted rather than snapping onto it, then check that once.
         * Checking the step and then checking the eased result is two collision queries per
         * member per tick where the crowd is in the open and both always pass - and in the
         * open is where a crowd this size spends nearly all of its time. */
        Vec3d smooth = new Vec3d(
            velocity.x + (desired.x - velocity.x) * blend,
            0D,
            velocity.z + (desired.z - velocity.z) * blend
        );
        Vec3d safe = this.steerAroundObstacles(world, entity, smooth, tick);

        if (safe.lengthSquared() < 1.0E-6D)
        {
            /* Every direction is blocked, so try to make progress with a shorter step. Only
             * members actually up against something pay for this. */
            safe = this.trimToSafeMotion(world, entity, smooth);
        }

        /* Deliberately not flagging velocityModified. That flag exists to push a velocity packet
         * to clients, and a client that is told a velocity simulates the entity forward with it
         * and then gets snapped back by the next position update - which is seen as the crowd
         * lurching and stalling rather than walking. Steering runs every tick anyway, so the
         * position stream alone describes the motion, and the client interpolates it smoothly.
         * It also spares one packet per member per tick, which at crowd scale is the difference
         * between keeping up and not. */
        entity.setVelocity(safe.x, this.jumped ? 0.42D : velocity.y, safe.z);

        if (safe.lengthSquared() > 1.0E-6D)
        {
            float yaw = (float) Math.toDegrees(Math.atan2(-safe.x, safe.z));
            float yawStep = (float) Math.min(24D, 8D + speed * 4D);
            float turned = stepAngle(entity.getBodyYaw(), yaw, yawStep);

            entity.setYaw(turned);
            entity.setBodyYaw(turned);
            /* Head follows travel too, so a walking member faces the way it is going rather than
             * staring off at its last heading. A look target, when one is set, overrides this in
             * applyLook afterwards. */
            entity.setHeadYaw(stepAngle(entity.getHeadYaw(), yaw, yawStep));

            /* Claim the body yaw for this tick, or vanilla swings it back toward its own idea of
             * the movement heading before the tick is out - which is what made the crowd strafe
             * sideways (body facing one way, sliding another) instead of facing where it walks. */
            if (entity instanceof CrowdDrivenEntity owner)
            {
                owner.bbs$driveBodyYaw();
            }
        }
        else
        {
            this.stopHorizontal(entity);
        }
    }

    private Vec3d steerAroundObstacles(ServerWorld world, LivingEntity entity, Vec3d desired, int tick)
    {
        if (this.canMove(world, entity, desired))
        {
            return desired;
        }

        /* Before giving up on the direction and turning, see whether hopping clears it. The step
         * height a walk can absorb tops out below a block, so a single grass block is enough to
         * stop a member dead, and a crowd walking into a one-block rise piles up against it
         * instead of crossing. */
        if (this.tryAutoJump(world, entity, desired))
        {
            return desired;
        }

        boolean flip = Math.floorMod(CrowdUtils.entitySeed(entity, this.seed.get(), 0x2a71) + tick / 10, 2) == 0;
        double[] angles = flip
            ? new double[] {25D, -25D, 45D, -45D, 70D, -70D, 105D, -105D, 150D}
            : new double[] {-25D, 25D, -45D, 45D, -70D, 70D, -105D, 105D, -150D};

        for (double angle : angles)
        {
            Vec3d rotated = rotateXZ(desired, angle);

            if (this.canMove(world, entity, rotated))
            {
                return rotated;
            }
        }

        return Vec3d.ZERO;
    }

    /**
     * Hop a member over an obstacle that walking cannot climb.
     *
     * <p>Only worth doing when the way is blocked at foot level but open a block higher, which
     * is the shape of a kerb, a step or a single block of terrain - the things a crowd should
     * cross without noticing. Anything taller is a wall, and the member should turn instead, so
     * this reports failure and leaves the steering to look for a way round.</p>
     *
     * <p>The jump is recorded rather than applied, because the caller has already read the
     * velocity it is about to write and would put the old vertical component straight back over
     * the top of it.</p>
     */
    private boolean tryAutoJump(ServerWorld world, LivingEntity entity, Vec3d motion)
    {
        if (!this.autoJump.get() || !entity.isOnGround() || this.jumped)
        {
            return false;
        }

        Box box = entity.getBoundingBox().offset(motion.x, 0D, motion.z);

        /* One block up clears a one-block rise; the taller probe covers a member standing a
         * little below the obstacle, on a slab or partway down a slope. */
        for (double lift : new double[] {1D, 1.25D})
        {
            if (world.isSpaceEmpty(null, box.offset(0D, lift, 0D)))
            {
                this.jumped = true;

                return true;
            }
        }

        return false;
    }

    private boolean canMove(ServerWorld world, LivingEntity entity, Vec3d motion)
    {
        Box box = entity.getBoundingBox().offset(motion.x, 0D, motion.z);

        /* Passing null asks about blocks only. Handing the entity over would also collect the
         * entity collisions in that box, and in a packed crowd that walks hundreds of members
         * per query, several queries per member, every tick - the square term all over again.
         * Crowd members do not hard-collide with each other anyway; the separation steering is
         * what keeps them apart. */
        if (world.isSpaceEmpty(null, box))
        {
            return true;
        }

        double maxStep = Math.max(0D, Math.min(0.75D, this.maxStepHeight.get()));

        if (maxStep <= 0D || !entity.isOnGround())
        {
            return false;
        }

        double[] lifts = new double[] {0.0625D, 0.125D, 0.25D, 0.375D, 0.5D, maxStep};

        for (double lift : lifts)
        {
            if (lift <= 0D || lift > maxStep + 1.0E-5D)
            {
                continue;
            }

            Box lifted = box.offset(0D, lift, 0D);

            if (world.isSpaceEmpty(null, lifted))
            {
                return true;
            }
        }

        return false;
    }

    private Vec3d trimToSafeMotion(ServerWorld world, LivingEntity entity, Vec3d motion)
    {
        if (motion.lengthSquared() < 1.0E-8D)
        {
            return Vec3d.ZERO;
        }

        if (this.canMove(world, entity, motion))
        {
            return motion;
        }

        for (int i = 7; i >= 1; i--)
        {
            Vec3d scaled = motion.multiply(i / 8D);

            if (this.canMove(world, entity, scaled))
            {
                return scaled;
            }
        }

        Vec3d xOnly = new Vec3d(motion.x, 0D, 0D);
        Vec3d zOnly = new Vec3d(0D, 0D, motion.z);
        boolean canX = Math.abs(motion.x) > 1.0E-5D && this.canMove(world, entity, xOnly);
        boolean canZ = Math.abs(motion.z) > 1.0E-5D && this.canMove(world, entity, zOnly);

        if (canX && canZ)
        {
            return Math.abs(motion.x) > Math.abs(motion.z) ? xOnly : zOnly;
        }

        if (canX)
        {
            return xOnly;
        }

        if (canZ)
        {
            return zOnly;
        }

        return Vec3d.ZERO;
    }

    private static boolean isFinite(double value)
    {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private Vec3d getSeparationMotion(LivingEntity entity, List<LivingEntity> crowd)
    {
        double radius = Math.max(0D, this.separation.get());

        if (radius <= 0D || crowd.size() <= 1)
        {
            return Vec3d.ZERO;
        }

        double radiusSq = radius * radius;
        /* Summed in place rather than through Vec3d: this is the innermost loop of the whole
         * clip, run once per member per tick, and every add would otherwise allocate. */
        this.pushX = 0D;
        this.pushZ = 0D;
        this.pushSamples = 0;

        if (this.grid == null)
        {
            for (int i = 0; i < crowd.size(); i++)
            {
                this.accumulateSeparation(entity, crowd.get(i), radius, radiusSq);
            }
        }
        else
        {
            this.grid.forEachNeighbour(entity.getX(), entity.getZ(), (other) -> this.accumulateSeparation(entity, other, radius, radiusSq));
        }

        Vec3d push = new Vec3d(this.pushX, 0D, this.pushZ);

        if (this.pushSamples <= 0 || push.lengthSquared() < 1.0E-6D)
        {
            return Vec3d.ZERO;
        }

        return push.normalize().multiply(Math.min(2D, radius) * 0.65D);
    }

    private void accumulateSeparation(LivingEntity entity, LivingEntity other, double radius, double radiusSq)
    {
        if (other == entity || !other.isAlive() || other.isRemoved())
        {
            return;
        }

        double dx = entity.getX() - other.getX();
        double dz = entity.getZ() - other.getZ();
        double distanceSq = dx * dx + dz * dz;

        if (distanceSq < 1.0E-6D || distanceSq > radiusSq)
        {
            return;
        }

        double distance = Math.sqrt(distanceSq);
        double strength = (radius - distance) / radius;

        this.pushX += dx / distance * strength;
        this.pushZ += dz / distance * strength;
        this.pushSamples += 1;
    }

    private static Vec3d rotateXZ(Vec3d vector, double degrees)
    {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec3d(vector.x * cos - vector.z * sin, 0D, vector.x * sin + vector.z * cos);
    }

    private void stopHorizontal(LivingEntity entity)
    {
        Vec3d velocity = entity.getVelocity();

        entity.setVelocity(0D, velocity.y, 0D);
    }

    private void jump(LivingEntity entity)
    {
        if (entity instanceof MobEntity mob)
        {
            mob.getJumpControl().setActive();
            mob.setJumping(true);

            return;
        }

        Vec3d velocity = entity.getVelocity();

        entity.setVelocity(velocity.x, Math.max(velocity.y, 0.42D), velocity.z);
        entity.velocityModified = true;
        entity.setJumping(true);
    }

    private void spawnSprintParticles(ServerWorld world, LivingEntity entity, int tick)
    {
        if (Math.floorMod(tick + entity.getId(), 2) != 0 || !entity.isOnGround())
        {
            return;
        }

        /* Only where the feet actually moved this tick. The sprint flag is an intent - a member
         * held at a stop, or one a keyframe pins in place, can still carry it - and dust kicking
         * up under a crowd that is standing still is the giveaway that it is faked. Gate on real
         * ground travel so the particles follow the movement, not the flag. */
        double dx = entity.getX() - entity.prevX;
        double dz = entity.getZ() - entity.prevZ;

        if (dx * dx + dz * dz < 0.0025D)
        {
            return;
        }

        BlockPos below = entity.getBlockPos().down();
        BlockState state = world.getBlockState(below);

        if (state.isAir())
        {
            return;
        }

        double width = Math.max(0.25D, entity.getWidth() * 0.35D);

        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
            entity.getX(), entity.getY() + 0.05D, entity.getZ(),
            3, width, 0.01D, width, 0.02D);
    }

    private static boolean shouldShoot(LivingEntity mob, int tick, float shootRate)
    {
        return shouldPulse(mob, tick, shootRate);
    }

    private static boolean shouldPulse(LivingEntity mob, int tick, float rate)
    {
        if (rate <= 0F)
        {
            return false;
        }

        int interval = Math.max(1, Math.round(20F / rate));
        int offset = Math.floorMod(CrowdUtils.entityIndex(mob), interval);

        return Math.floorMod(tick + offset, interval) == 0;
    }

    private boolean shouldPulse(LivingEntity mob, int tick, float rate, int salt)
    {
        if (rate <= 0F)
        {
            return false;
        }

        int interval = Math.max(1, Math.round(20F / rate));
        int offset = Math.floorMod(CrowdUtils.entitySeed(mob, this.seed.get(), salt), interval);

        return Math.floorMod(tick + offset, interval) == 0;
    }

    private GunProperties createProjectileProperties()
    {
        GunProperties properties = new GunProperties();

        properties.projectileForm = this.createProjectileForm();
        properties.impactForm = FormUtils.copy(this.impactModel.get());
        properties.useTarget = true;
        properties.lifeSpan = Math.max(1, this.projectileLifeSpan.get());
        properties.speed = Math.max(0F, this.projectileSpeed.get());
        properties.friction = 0.99F;
        properties.gravity = 0.03F;
        properties.yaw = true;
        properties.pitch = true;
        properties.bounces = Math.max(0, this.impactBounces.get());
        properties.bounceDamping = Math.max(0F, Math.min(1F, this.impactBounceDamping.get()));
        properties.vanish = this.impactVanish.get();
        properties.damage = Math.max(0F, this.impactDamage.get());
        properties.knockback = Math.max(0F, this.impactKnockback.get());
        properties.collideBlocks = this.impactCollideBlocks.get();
        properties.collideEntities = this.impactCollideEntities.get();
        properties.cmdFiring = this.cmdFiring.get();
        properties.cmdImpact = this.cmdImpact.get();
        properties.cmdVanish = this.cmdVanish.get();
        properties.cmdTicking = this.cmdTicking.get();
        properties.ticking = Math.max(0, this.ticking.get());

        return properties;
    }

    private Form createProjectileForm()
    {
        Form model = this.projectileModel.get();

        if (model != null)
        {
            return FormUtils.copy(model);
        }

        ItemStack stack = this.shootItem.get();

        if (stack == null || stack.isEmpty())
        {
            stack = new ItemStack(Items.SNOWBALL);
        }
        else
        {
            stack = stack.copy();
        }

        ItemForm itemForm = new ItemForm();

        itemForm.stack.set(stack);

        return itemForm;
    }

    private void shootSnowballLike(ServerWorld world, LivingEntity mob, Vec3d targetPos, GunProperties properties)
    {
        if (properties.speed <= 0F)
        {
            return;
        }

        GunProjectileEntity projectile = new GunProjectileEntity(BBSMod.GUN_PROJECTILE_ENTITY, world);
        double x = targetPos.x - mob.getX();
        double y = (targetPos.y + 0.52D) - (mob.getY() + mob.getEyeHeight(mob.getPose()));
        double z = targetPos.z - mob.getZ();
        double horizontal = Math.sqrt(x * x + z * z);

        projectile.setOwner(mob);
        projectile.setProperties(properties);
        projectile.setForm(FormUtils.copy(properties.projectileForm));
        projectile.setPos(mob.getX(), mob.getY() + mob.getEyeHeight(mob.getPose()), mob.getZ());
        projectile.setVelocity(x, y + horizontal * 0.2D, z, properties.speed, 12F);
        projectile.calculateDimensions();

        world.spawnEntity(projectile);
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_SNOW_GOLEM_SHOOT, mob.getSoundCategory(), 1F, 1F / (world.random.nextFloat() * 0.4F + 0.8F));

        if (!properties.cmdFiring.isEmpty() && mob.getServer() != null)
        {
            mob.getServer().getCommandManager().executeWithPrefix(mob.getCommandSource(), properties.cmdFiring);
        }
    }

    private void applyLook(LivingEntity mob, Vec3d targetPos, CrowdBehaviorMode mode, int tick, float blend)
    {
        if (targetPos == null)
        {
            return;
        }

        if (!this.lookAtTarget.get() || (!this.lookBodyYaw.get() && !this.lookHeadYaw.get() && !this.lookHeadPitch.get()))
        {
            return;
        }

        double dx = targetPos.x - mob.getX();
        double dy = targetPos.y - (mob.getY() + mob.getEyeHeight(mob.getPose()));
        double dz = targetPos.z - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal < 1.0E-6D && Math.abs(dy) < 1.0E-6D)
        {
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.max(horizontal, 1.0E-6D)));

        /* The ease softens the start of the behaviour; it must not make the crowd unable to keep
         * up. Turning by a share of the error settles at a lag of roughly twice whatever the
         * target's angular speed is, so halving the share doubles the lag - at a quarter, a
         * target crossing the shot leaves the crowd a quarter-turn behind it for the whole take,
         * which does not read as easing in, it reads as not looking. Half is as slow as tracking
         * survives. */
        float stepScale = 0.5F + 0.5F * blend;

        if (mode == CrowdBehaviorMode.SAD_WALK)
        {
            pitch = 28F;
        }

        /* Every angle is written here rather than handed to the mob's own LookControl. A fixed
         * ceiling per tick is what stops a head from keeping up with anything that moves: a
         * target crossing the frame outruns it and the crowd trails behind at a constant lag,
         * never arriving. Turning by a share of the error instead means the head closes fast
         * when it is far off and eases in as it arrives, and once it is on target it stays there
         * for as long as the target keeps moving - the ceilings below only cap the extremes. */
        /* The first tick of a run is a placement, not a movement. Whatever way the member was
         * facing beforehand is not something the shot ever meant to show, so turning from it is
         * everyone visibly correcting themselves over the opening seconds of the take.
         *
         * "First tick of a run" is the last tick it was driven not being the one just gone,
         * rather than the clip's own start tick: an export of a loop range begins in the middle
         * of the clip and never crosses its start, and scrubbing does not drive the crowd at all,
         * so a take could start from angles left over from whenever it was last played - which is
         * why it looked right in the editor and wrong in the export. */
        boolean calibrating = tick <= this.tick.get();

        if (mob instanceof CrowdDrivenEntity driven)
        {
            calibrating |= driven.bbs$getCrowdLookTick() != tick - 1;

            driven.bbs$setCrowdLookTick(tick);
        }

        if (this.lookHeadYaw.get())
        {
            mob.setHeadYaw(calibrating ? yaw : stepAngle(mob.getHeadYaw(), yaw, turnStep(mob.getHeadYaw(), yaw, stepScale, 4F, 75F)));
        }

        if (this.lookBodyYaw.get())
        {
            /* The torso follows the head rather than the target, so the two agree: they turn at
             * different rates, and a body converging on its own arrives late and leaves the head
             * craned over one shoulder for the whole approach. Pitch is the head's alone - a
             * torso does not look up or down. */
            float bodyTarget = this.lookHeadYaw.get() ? mob.getHeadYaw() : yaw;
            float bodyYaw = calibrating
                ? bodyTarget
                : stepAngle(mob.getBodyYaw(), bodyTarget, turnStep(mob.getBodyYaw(), bodyTarget, stepScale, 3F, 60F));

            mob.setBodyYaw(bodyYaw);
            mob.setYaw(bodyYaw);

            /* Vanilla would otherwise swing the body back toward whichever way the member is
             * walking before the tick is out, and the torso would never be seen pointing anywhere
             * the clip asked for. */
            if (mob instanceof CrowdDrivenEntity owner)
            {
                owner.bbs$driveBodyYaw();
            }
        }

        if (this.lookHeadPitch.get())
        {
            mob.setPitch(calibrating ? pitch : stepAngle(mob.getPitch(), pitch, turnStep(mob.getPitch(), pitch, stepScale, 3F, 60F)));
        }
    }

    /**
     * How far an angle may turn this tick: a share of how far off it is, floored so the last
     * degree does not creep and capped so a target appearing behind the crowd is not a snap.
     */
    private static float turnStep(float current, float target, float scale, float min, float max)
    {
        float error = Math.abs(wrapDegrees(target - current));

        return Math.min(max, Math.max(min, error * 0.45F)) * scale;
    }

    private void applyPerformanceMotion(LivingEntity entity, CrowdBehaviorMode mode, int tick, List<LivingEntity> crowd)
    {
        if (!this.headMotion.get() && mode != CrowdBehaviorMode.SAD_WALK && mode != CrowdBehaviorMode.WANDER_LOOK)
        {
            return;
        }

        float energy = Math.max(0F, this.energy.get());
        float phase = (tick + CrowdUtils.entitySeed(entity, this.seed.get(), 0x4221) * 0.0007F) * 0.18F;

        if (mode == CrowdBehaviorMode.WANDER_LOOK && this.isLookAroundPause(entity, mode, tick))
        {
            float limit = Math.max(20F, Math.min(120F, this.headYawLimit.get()));
            float yawOffset = (float) Math.sin(phase * 0.55F) * limit;
            float pitch = (float) Math.sin(phase * 0.72F) * 8F * Math.max(0.25F, energy);

            entity.setHeadYaw(entity.getBodyYaw() + yawOffset);
            entity.setPitch(stepAngle(entity.getPitch(), pitch, 4F));
        }
        else if (mode == CrowdBehaviorMode.CHEER)
        {
            entity.setHeadYaw(entity.getHeadYaw() + (float) Math.sin(phase * 1.35F) * 10F * energy);
            entity.setPitch(entity.getPitch() + (float) Math.sin(phase * 1.9F) * 5F * energy);
        }
        else if (mode == CrowdBehaviorMode.TALK || mode == CrowdBehaviorMode.MEETING)
        {
            boolean speaker = this.isConversationSpeaker(entity, crowd, tick);
            float nod = speaker && (float) Math.sin(phase * 1.2F) > 0.15F ? 6F : -1.5F;

            entity.setPitch(stepAngle(entity.getPitch(), entity.getPitch() + nod * energy, speaker ? 4.5F : 2F));
        }
        else if (mode == CrowdBehaviorMode.SAD_WALK)
        {
            entity.setPitch(stepAngle(entity.getPitch(), 28F, 5F));
            entity.setHeadYaw(entity.getHeadYaw() + (float) Math.sin(phase * 0.45F) * 2F);
        }
        else if (mode == CrowdBehaviorMode.IDLE_CROWD || mode == CrowdBehaviorMode.WATCH || mode == CrowdBehaviorMode.MARKET || mode == CrowdBehaviorMode.GATHER)
        {
            float yaw = (float) Math.sin(phase * 0.35F) * 4F * energy;
            float pitch = (float) Math.sin(phase * 0.47F) * 2.5F * energy;

            entity.setHeadYaw(entity.getHeadYaw() + yaw);
            entity.setPitch(stepAngle(entity.getPitch(), entity.getPitch() + pitch, 2.5F));
        }
        else if (mode == CrowdBehaviorMode.PANIC || mode == CrowdBehaviorMode.FLEE)
        {
            entity.setHeadYaw(entity.getHeadYaw() + (float) Math.sin(phase * 1.4F) * 8F * energy);
        }
    }

    private void constrainHeadYaw(LivingEntity entity)
    {
        float limit = Math.max(0F, Math.min(120F, this.headYawLimit.get()));
        float bodyYaw = entity.getBodyYaw();
        float delta = wrapDegrees(entity.getHeadYaw() - bodyYaw);

        if (delta > limit)
        {
            delta = limit;
        }
        else if (delta < -limit)
        {
            delta = -limit;
        }

        entity.setHeadYaw(bodyYaw + delta);
    }

    private static float stepAngle(float current, float target, float maxStep)
    {
        float delta = wrapDegrees(target - current);

        if (delta > maxStep)
        {
            delta = maxStep;
        }
        else if (delta < -maxStep)
        {
            delta = -maxStep;
        }

        return current + delta;
    }

    private static float wrapDegrees(float angle)
    {
        angle = angle % 360F;

        if (angle >= 180F)
        {
            angle -= 360F;
        }
        else if (angle < -180F)
        {
            angle += 360F;
        }

        return angle;
    }

    private record FightTarget(LivingEntity entity, Vec3d position, Vec3d lookPoint) {}

    @Override
    protected Clip create()
    {
        return new CrowdBehaviorActionClip();
    }
}
