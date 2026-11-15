package ans;

import ans.atmosphere.BedrockAtmosphereManager;
import ans.bedrock.BedrockAttachableDef;
import ans.bedrock.BedrockEntityDef;
import ans.bedrock.BedrockEntityState;
import ans.bedrock.BedrockModelRenderer;
import ans.bedrock.BedrockPackManager;
import ans.bedrock.BedrockParticleEngine;
import ans.molang.MolangValue;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.block.BlockState;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Main Fabric client entrypoint and runtime coordinator for the Actions &amp; Stuff port.
 */
public final class ActionsAndStuffClientMod implements ClientModInitializer {

    public static final String MOD_ID = "actions_and_stuff";
    public static final Logger LOGGER = LoggerFactory.getLogger("ActionsAndStuff");

    public static final BedrockPackManager PACK = new BedrockPackManager();
    public static final BedrockAtmosphereManager ATMOSPHERE = new BedrockAtmosphereManager();
    public static final BedrockParticleEngine PARTICLES = new BedrockParticleEngine();
    public static final BedrockModelRenderer RENDERER = new BedrockModelRenderer();

    private static final Map<Entity, BedrockEntityState> ENTITY_STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, BedrockEntityState> ATTACHABLE_STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Biome, String> BIOME_ID_CACHE = Collections.synchronizedMap(new IdentityHashMap<>());

    private static volatile boolean enabled = true;
    private static volatile boolean initialLoadAttempted = false;
    private static long lastFrameNanos = System.nanoTime();
    private static float frameDeltaSec = 1f / 60f;
    private static long renderFrameId = 0L;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Actions & Stuff Java 1.20.1 Fabric client runtime initialized");
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isReady() {
        return enabled && PACK.isLoaded();
    }

    public static boolean isAtmosphereReady() {
        return enabled && ATMOSPHERE.isLoaded();
    }

    public static synchronized void onReload(ResourceManager rm) {
        initialLoadAttempted = true;
        PACK.loadFromResourceManager(rm);
        ATMOSPHERE.loadFromPack(PACK);
        ENTITY_STATES.clear();
        ATTACHABLE_STATES.clear();
        BIOME_ID_CACHE.clear();
        PARTICLES.clear();
        LOGGER.info("Loaded Actions & Stuff pack: {} entities, {} attachables, {} geometries, {} animations, {} biomes, {} atmospherics",
            PACK.entities.size(), PACK.attachablesById.size(), PACK.geometries.size(),
            PACK.animations.size(), ATMOSPHERE.biomes.size(), ATMOSPHERE.atmospheres.size());
    }

    public static void onClientTick(MinecraftClient client) {
        if (!enabled) return;
        if (!initialLoadAttempted && client != null && client.getResourceManager() != null) {
            onReload(client.getResourceManager());
        }
        if (client != null && client.world != null && !client.isPaused()) {
            PARTICLES.tick(0.05f);
        }
    }

    public static void updateFrameClock() {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        frameDeltaSec = Math.max(0.0001f, Math.min(dt, 0.05f));
        renderFrameId++;
    }

    private static float claimStepDt(BedrockEntityState state, MinecraftClient client) {
        boolean stepped = (state.lastRenderFrameId == renderFrameId);
        state.lastRenderFrameId = renderFrameId;
        return (client != null && client.isPaused() || stepped) ? 0f : frameDeltaSec;
    }

    public static boolean tryRenderEntity(Entity entity,
                                          double x, double y, double z,
                                          float yaw, float tickDelta,
                                          MatrixStack matrices,
                                          VertexConsumerProvider consumers,
                                          int light) {
        if (!isReady() || entity == null) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == client.player && client.options.getPerspective().isFirstPerson() && client.currentScreen == null) {
            // Do not override invisible first-person local player body pass
            return false;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (typeId == null) return false;
        BedrockEntityDef def = PACK.resolveEntity(typeId.toString());
        if (def == null) return false;

        BedrockEntityState state = ENTITY_STATES.computeIfAbsent(entity, e -> new BedrockEntityState());
        float dt = claimStepDt(state, client);
        syncEntityState(entity, state, tickDelta, false);
        if (entity instanceof LivingEntity living) {
            applyEquippedParentSetups(living, def, state, false);
        }
        state.updateEntity(def, dt, PACK, PARTICLES, entity.getX(), entity.getY(), entity.getZ());
        playPendingSounds(state, client);

        int overlay = entity instanceof LivingEntity le
            ? LivingEntityRenderer.getOverlay(le, 0f)
            : OverlayTexture.DEFAULT_UV;

        float bodyYaw = entity instanceof LivingEntity le
            ? (le instanceof net.minecraft.entity.player.PlayerEntity && le.isSneaking()
                ? MathHelper.lerpAngleDegrees(tickDelta, le.prevHeadYaw, le.headYaw)
                : MathHelper.lerpAngleDegrees(tickDelta, le.prevBodyYaw, le.bodyYaw))
            : MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());

        matrices.push();
        boolean rendered = RENDERER.renderEntity(entity, def, state, PACK, bodyYaw, matrices, consumers, light, overlay);
        if (rendered && entity == client.player && entity instanceof LivingEntity livingPlayer && dt > 0f) {
            logPlayerSwingDiag(livingPlayer, state, dt);
        }
        if (rendered && def.enableAttachables && entity instanceof LivingEntity living) {
            renderAttachedItemsAndArmor(living, def, state, bodyYaw, dt, tickDelta, matrices, consumers, light, overlay);
        }
        matrices.pop();
        return rendered;
    }

    private static void applyEquippedParentSetups(LivingEntity living,
                                                  BedrockEntityDef entityDef,
                                                  BedrockEntityState ownerState,
                                                  boolean firstPerson) {
        if (!entityDef.hideArmor) {
            applyAttachableParentSetup(living.getEquippedStack(EquipmentSlot.HEAD), "head", ownerState, firstPerson);
            applyAttachableParentSetup(living.getEquippedStack(EquipmentSlot.CHEST), "torso", ownerState, firstPerson);
            applyAttachableParentSetup(living.getEquippedStack(EquipmentSlot.LEGS), "legs", ownerState, firstPerson);
            applyAttachableParentSetup(living.getEquippedStack(EquipmentSlot.FEET), "feet", ownerState, firstPerson);
        }
        boolean mainRight = living.getMainArm() == Arm.RIGHT;
        applyAttachableParentSetup(living.getMainHandStack(), mainRight ? "main_hand" : "off_hand", ownerState, firstPerson);
        applyAttachableParentSetup(living.getOffHandStack(), mainRight ? "off_hand" : "main_hand", ownerState, firstPerson);
    }

    private static void applyAttachableParentSetup(ItemStack stack, String slotName,
                                                   BedrockEntityState ownerState, boolean firstPerson) {
        if (stack.isEmpty()) return;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (itemId == null) return;
        BedrockAttachableDef attDef = PACK.resolveAttachable(itemId.toString(), ownerState.scope);
        if (attDef == null || attDef.parentSetup.isEmpty()) return;
        String prevSlot = ownerState.queryContext.itemSlot;
        boolean prevFp = ownerState.queryContext.firstPerson;
        boolean prevAttached = ownerState.queryContext.attached;
        ownerState.queryContext.itemSlot = slotName;
        ownerState.queryContext.firstPerson = firstPerson;
        ownerState.queryContext.attached = true;
        try {
            for (var expr : attDef.parentSetup) {
                expr.evaluate(ownerState.scope);
            }
            ownerState.scope.clearTemps();
        } finally {
            ownerState.queryContext.itemSlot = prevSlot;
            ownerState.queryContext.firstPerson = prevFp;
            ownerState.queryContext.attached = prevAttached;
        }
    }

    private static String lastLoggedCamimd = "";
    private static int swingLogCooldown = 0;

    private static void logPlayerSwingDiag(LivingEntity player, BedrockEntityState state, float dt) {
        String camimd = "?";
        float camimdTime = 0f;
        String pwqhcf = "?";
        for (var en : state.getControllerStates().entrySet()) {
            String k = en.getKey();
            if (k.contains("camimd") || k.contains("ptaudk")) {
                camimd = en.getValue().currentState != null ? en.getValue().currentState.nameLower : "null";
                camimdTime = en.getValue().stateTime;
            } else if (k.contains("pwqhcf")) {
                pwqhcf = en.getValue().currentState != null ? en.getValue().currentState.nameLower : "null";
            }
        }
        double atk = state.scope.getVariable("attack_time").asDouble();
        boolean stateChanged = !camimd.equals(lastLoggedCamimd);
        if (stateChanged || (atk > 0.001 && swingLogCooldown <= 0)) {
            lastLoggedCamimd = camimd;
            swingLogCooldown = 3;
            var armMat = state.getBoneMatrix("rightarm");
            var itemMat = state.getBoneMatrix("rightitem");
            org.joml.Vector3f armDir = armMat != null ? armMat.transformDirection(new org.joml.Vector3f(0, -1, 0)) : new org.joml.Vector3f();
            org.joml.Vector3f itemPos = itemMat != null ? itemMat.getTranslation(new org.joml.Vector3f()) : new org.joml.Vector3f();
            LOGGER.info("[ANS-SWING] item={} atk={} dqirzw={} camimd={}({}) pwqhcf={} custPos={} armDir=({},{},{}) itemPos=({},{},{})",
                Registries.ITEM.getId(player.getMainHandStack().getItem()),
                String.format(java.util.Locale.ROOT, "%.3f", atk),
                String.format(java.util.Locale.ROOT, "%.2f", state.scope.getVariable("dqirzw").asDouble()),
                camimd, String.format(java.util.Locale.ROOT, "%.2f", camimdTime),
                pwqhcf,
                String.format(java.util.Locale.ROOT, "%.1f", state.scope.getVariable("oreville_mainhand_custom_pos").asDouble()),
                String.format(java.util.Locale.ROOT, "%.2f", armDir.x), String.format(java.util.Locale.ROOT, "%.2f", armDir.y), String.format(java.util.Locale.ROOT, "%.2f", armDir.z),
                String.format(java.util.Locale.ROOT, "%.2f", itemPos.x), String.format(java.util.Locale.ROOT, "%.2f", itemPos.y), String.format(java.util.Locale.ROOT, "%.2f", itemPos.z));
        } else if (swingLogCooldown > 0) {
            swingLogCooldown--;
        }
    }

    private static void renderAttachedItemsAndArmor(LivingEntity living,
                                                    BedrockEntityDef entityDef,
                                                    BedrockEntityState ownerState,
                                                    float bodyYaw,
                                                    float dt,
                                                    float tickDelta,
                                                    MatrixStack matrices,
                                                    VertexConsumerProvider consumers,
                                                    int light,
                                                    int overlay) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        float ownerScale = sanitizeScale(entityDef.scaleExpr.evaluateFloat(ownerState.scope), 1f);
        if (ownerScale != 1f) {
            matrices.scale(ownerScale, ownerScale, ownerScale);
        }

        if (!entityDef.hideArmor) {
            renderArmorSlot(living, EquipmentSlot.HEAD, "head", ownerState, dt, matrices, consumers, light, overlay);
            renderArmorSlot(living, EquipmentSlot.CHEST, "torso", ownerState, dt, matrices, consumers, light, overlay);
            renderArmorSlot(living, EquipmentSlot.LEGS, "legs", ownerState, dt, matrices, consumers, light, overlay);
            renderArmorSlot(living, EquipmentSlot.FEET, "feet", ownerState, dt, matrices, consumers, light, overlay);
        }

        boolean mainRight = living.getMainArm() == Arm.RIGHT;
        renderHandAttachable(living, Hand.MAIN_HAND, mainRight ? "main_hand" : "off_hand",
            ownerState, dt, matrices, consumers, light, overlay);
        renderHandAttachable(living, Hand.OFF_HAND, mainRight ? "off_hand" : "main_hand",
            ownerState, dt, matrices, consumers, light, overlay);

        matrices.pop();
    }

    private static void renderArmorSlot(LivingEntity living, EquipmentSlot slot, String slotName,
                                        BedrockEntityState ownerState, float dt,
                                        MatrixStack matrices, VertexConsumerProvider consumers,
                                        int light, int overlay) {
        ItemStack stack = living.getEquippedStack(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) return;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (itemId == null) return;

        BedrockEntityState attState = getAttachableState(living.getUuidAsString() + ":" + slotName);
        Identifier ownerTypeId = Registries.ENTITY_TYPE.getId(living.getType());
        attState.queryContext.owningEntity = MolangValue.entityRef(ownerState.scope);
        attState.queryContext.ownerIdentifier = ownerTypeId != null ? ownerTypeId.toString() : "minecraft:player";
        attState.queryContext.attached = true;
        attState.queryContext.firstPerson = false;
        attState.queryContext.inUi = false;
        attState.queryContext.itemSlot = slotName;
        attState.queryContext.itemCharged = stack.hasGlint();
        copyCorePoseQueries(ownerState, attState);

        BedrockAttachableDef attDef = PACK.resolveAttachable(itemId.toString(), attState.scope);
        if (attDef == null) return;
        float attDt = claimStepDt(attState, MinecraftClient.getInstance());
        attState.updateAttachable(attDef, attDt, PACK, PARTICLES, living.getX(), living.getY(), living.getZ());
        RENDERER.renderAttachable(attDef, attState, ownerState, PACK, matrices, consumers, light, overlay);
    }

    private static void renderHandAttachable(LivingEntity living, Hand hand, String slotName,
                                             BedrockEntityState ownerState, float dt,
                                             MatrixStack matrices, VertexConsumerProvider consumers,
                                             int light, int overlay) {
        ItemStack stack = living.getStackInHand(hand);
        if (stack.isEmpty()) return;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (itemId == null) return;

        BedrockEntityState attState = getAttachableState(living.getUuidAsString() + ":" + hand.name() + ":3p");
        syncAttachableFromOwner(living, stack, hand, slotName, ownerState, attState, false);
        BedrockAttachableDef attDef = PACK.resolveAttachable(itemId.toString(), attState.scope);
        if (attDef != null) {
            // Owner rightitem / leftitem bone matrix aligns attachable bone bindings
            float attDt = claimStepDt(attState, MinecraftClient.getInstance());
            attState.updateAttachable(attDef, attDt, PACK, PARTICLES, living.getX(), living.getY(), living.getZ());
            RENDERER.renderAttachable(attDef, attState, ownerState, PACK, matrices, consumers, light, overlay);
            return;
        }

        boolean isOffHand = "off_hand".equals(slotName) || "slot.weapon.offhand".equals(slotName);
        Matrix4f boneMat = ownerState.getBoneMatrix(isOffHand ? "leftitem" : "rightitem");
        float pivX = isOffHand ? (-6f / 16f) : (6f / 16f);
        float pivY = 15f / 16f;
        float pivZ = 1f / 16f;
        if (boneMat == null) {
            boneMat = ownerState.getBoneMatrix(isOffHand ? "leftarm" : "rightarm");
            pivX = isOffHand ? (-5f / 16f) : (5f / 16f);
            pivY = 12f / 16f;
            pivZ = 0f;
        }
        if (boneMat != null) {
            matrices.push();
            matrices.peek().getPositionMatrix().mul(boneMat);
            Matrix3f norm = new Matrix3f();
            boneMat.normal(norm);
            if (Float.isFinite(norm.m00)) {
                matrices.peek().getNormalMatrix().mul(norm);
            }
            matrices.translate(pivX, pivY, pivZ);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            MinecraftClient.getInstance().getItemRenderer().renderItem(
                living,
                stack,
                isOffHand ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
                isOffHand,
                matrices,
                consumers,
                living.getWorld(),
                light,
                overlay,
                living.getId() + hand.ordinal()
            );
            matrices.pop();
        }
    }

    public static boolean tryRenderFirstPersonItem(AbstractClientPlayerEntity player,
                                                   float tickDelta,
                                                   float pitch,
                                                   Hand hand,
                                                   float swingProgress,
                                                   ItemStack stack,
                                                   float equipProgress,
                                                   MatrixStack matrices,
                                                   VertexConsumerProvider consumers,
                                                   int light) {
        if (!isReady() || player == null || stack.isEmpty()) return false;
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (itemId == null) return false;

        BedrockEntityState ownerState = ENTITY_STATES.computeIfAbsent(player, e -> new BedrockEntityState());
        syncEntityState(player, ownerState, tickDelta, true);

        boolean mainRight = player.getMainArm() == Arm.RIGHT;
        boolean isRightHand = (hand == Hand.MAIN_HAND && mainRight) || (hand == Hand.OFF_HAND && !mainRight);
        String slotName = isRightHand ? "main_hand" : "off_hand";

        BedrockEntityState attState = getAttachableState(player.getUuidAsString() + ":" + hand.name() + ":1p");
        syncAttachableFromOwner(player, stack, hand, slotName, ownerState, attState, true);
        attState.queryContext.swingDuration = 6.0f;
        attState.scope.setVariable("attack_time", MolangValue.of(swingProgress));

        BedrockAttachableDef attDef = PACK.resolveAttachable(itemId.toString(), attState.scope);
        if (attDef == null) return false;

        float dt = claimStepDt(attState, MinecraftClient.getInstance());
        attState.updateAttachable(attDef, dt, PACK, PARTICLES, player.getX(), player.getY(), player.getZ());
        playPendingSounds(attState, MinecraftClient.getInstance());

        // Position first-person camera-space anchor cleanly before evaluating Bedrock attachable transform
        matrices.push();
        float armSign = isRightHand ? 1f : -1f;
        matrices.translate(armSign * 0.42f, -0.58f - equipProgress * 0.35f, -0.68f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        boolean rendered = RENDERER.renderAttachable(attDef, attState, null, PACK, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        return rendered;
    }

    public static boolean tryRenderItemByMode(LivingEntity entity,
                                              ItemStack stack,
                                              ModelTransformationMode mode,
                                              boolean leftHanded,
                                              MatrixStack matrices,
                                              VertexConsumerProvider consumers,
                                              int light) {
        // Standalone non-entity world item rendering hook (third-person non-Bedrock entities)
        if (!isReady() || entity == null || stack.isEmpty() || mode.isFirstPerson()) return false;
        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (typeId != null && PACK.resolveEntity(typeId.toString()) != null) {
            // Already handled by renderAttachedItemsAndArmor
            return false;
        }
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (itemId == null) return false;
        BedrockEntityState attState = getAttachableState(entity.getUuidAsString() + ":ext:" + mode.name());
        attState.queryContext.attached = true;
        attState.queryContext.firstPerson = false;
        attState.queryContext.itemSlot = leftHanded ? "off_hand" : "main_hand";
        BedrockAttachableDef attDef = PACK.resolveAttachable(itemId.toString(), attState.scope);
        if (attDef == null) return false;
        float dt = claimStepDt(attState, MinecraftClient.getInstance());
        attState.updateAttachable(attDef, dt, PACK, PARTICLES, entity.getX(), entity.getY(), entity.getZ());
        return RENDERER.renderAttachable(attDef, attState, null, PACK, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
    }

    private static BedrockEntityState getAttachableState(String key) {
        return ATTACHABLE_STATES.computeIfAbsent(key, k -> new BedrockEntityState());
    }

    private static void syncAttachableFromOwner(LivingEntity owner, ItemStack stack, Hand hand, String slotName,
                                                BedrockEntityState ownerState, BedrockEntityState attState,
                                                boolean firstPerson) {
        var ctx = attState.queryContext;
        Identifier ownerTypeId = Registries.ENTITY_TYPE.getId(owner.getType());
        ctx.owningEntity = MolangValue.entityRef(ownerState.scope);
        ctx.ownerIdentifier = ownerTypeId != null ? ownerTypeId.toString() : "minecraft:player";
        ctx.attached = true;
        ctx.firstPerson = firstPerson;
        ctx.inUi = false;
        ctx.itemSlot = slotName;
        ctx.itemCharged = stack.hasGlint();
        ctx.usingItem = owner.isUsingItem() && owner.getActiveHand() == hand;
        ctx.blocking = owner.isBlocking();
        ctx.itemUseDuration = ctx.usingItem ? owner.getItemUseTime() / 20f : 0f;
        ctx.itemMaxDuration = !stack.isEmpty() ? stack.getMaxUseTime() / 20f : 0f;
        copyCorePoseQueries(ownerState, attState);
    }

    private static void copyCorePoseQueries(BedrockEntityState src, BedrockEntityState dst) {
        var s = src.queryContext;
        var d = dst.queryContext;
        d.alive = s.alive;
        d.moving = s.moving;
        d.onGround = s.onGround;
        d.inWater = s.inWater;
        d.sneaking = s.sneaking;
        d.sprinting = s.sprinting;
        d.swimming = s.swimming;
        d.gliding = s.gliding;
        d.sleeping = s.sleeping;
        d.riding = s.riding;
        d.baby = s.baby;
        d.headXRotation = s.headXRotation;
        d.headYRotation = s.headYRotation;
        d.bodyXRotation = s.bodyXRotation;
        d.bodyYRotation = s.bodyYRotation;
        d.groundSpeed = s.groundSpeed;
        d.modifiedMoveSpeed = s.modifiedMoveSpeed;
        d.modifiedDistanceMoved = s.modifiedDistanceMoved;
        d.walkDistance = s.walkDistance;
        d.hurtTime = s.hurtTime;
        d.swingDuration = s.swingDuration;
        d.frameAlpha = s.frameAlpha;
        d.biomeIdentifier = s.biomeIdentifier;
        d.mainHandItem = s.mainHandItem;
        d.offHandItem = s.offHandItem;
        d.timeOfDay = s.timeOfDay;
    }

    public static void syncEntityState(Entity entity, BedrockEntityState state, float tickDelta, boolean firstPerson) {
        var q = state.queryContext;
        q.alive = entity.isAlive();
        q.onGround = entity.isOnGround();
        q.inWater = entity.isTouchingWater();
        q.inWaterContact = entity.isWet();
        q.inLava = entity.isInLava();
        q.headInWater = entity.isSubmergedInWater();
        q.sneaking = entity.isSneaking();
        q.sprinting = entity.isSprinting();
        q.swimming = entity.isSwimming();
        q.onFire = entity.isOnFire();
        q.invisible = entity.isInvisible();
        q.riding = entity.hasVehicle();
        if (q.riding && entity.getVehicle() != null) {
            Identifier vehId = Registries.ENTITY_TYPE.getId(entity.getVehicle().getType());
            q.ridingEntityIdentifier = vehId != null ? vehId.toString() : "";
        } else {
            q.ridingEntityIdentifier = "";
        }
        q.hasRider = entity.hasPassengers();
        q.firstPerson = firstPerson;
        q.frameAlpha = tickDelta;
        q.localPlayer = entity == MinecraftClient.getInstance().player;

        double vx = entity.getX() - entity.prevX;
        double vy = entity.getY() - entity.prevY;
        double vz = entity.getZ() - entity.prevZ;
        q.posX = (float) MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        q.posY = (float) MathHelper.lerp(tickDelta, entity.prevY, entity.getY())
            + (entity instanceof PlayerEntity ? 1.62f : 0f);
        q.posZ = (float) MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
        q.deltaX = (float) vx * 20f;
        q.deltaY = (float) vy * 20f;
        q.deltaZ = (float) vz * 20f;
        q.groundSpeed = (float) Math.hypot(vx, vz) * 20f;
        q.verticalSpeed = (float) vy * 20f;
        q.moving = q.groundSpeed > 0.08f;

        if (entity instanceof LivingEntity living) {
            q.health = living.getHealth();
            q.maxHealth = living.getMaxHealth();
            q.hurtTime = living.hurtTime;
            q.deathTicks = living.deathTime;
            q.baby = living.isBaby();
            q.gliding = living.isFallFlying();
            q.crawling = !entity.isTouchingWater() && living.isInSwimmingPose();
            q.sleeping = living.isSleeping();
            q.usingItem = living.isUsingItem();
            q.blocking = living.isBlocking();
            q.itemUseDuration = q.usingItem ? living.getItemUseTime() / 20f : 0f;
            q.itemMaxDuration = q.usingItem ? living.getActiveItem().getMaxUseTime() / 20f : 0f;

            float headYaw = MathHelper.lerpAngleDegrees(tickDelta, living.prevHeadYaw, living.headYaw);
            float bodyYaw = (living instanceof net.minecraft.entity.player.PlayerEntity && living.isSneaking())
                ? headYaw
                : MathHelper.lerpAngleDegrees(tickDelta, living.prevBodyYaw, living.bodyYaw);
            float pitch = MathHelper.lerp(tickDelta, living.prevPitch, living.getPitch());
            q.bodyYRotation = bodyYaw;
            q.headYRotation = headYaw;
            q.targetYRotation = MathHelper.wrapDegrees(headYaw - bodyYaw);
            q.headXRotation = pitch;
            q.targetXRotation = pitch;
            q.yawSpeed = MathHelper.wrapDegrees(living.headYaw - living.prevHeadYaw) * 20f;

            float limbSpeed = living.limbAnimator.getSpeed(tickDelta);
            float limbPos = living.limbAnimator.getPos(tickDelta);
            q.modifiedMoveSpeed = Math.min(1f, limbSpeed);
            q.modifiedDistanceMoved = (entity instanceof ChickenEntity || entity instanceof PlayerEntity)
                ? limbPos
                : (limbPos * 16f);
            q.walkDistance = limbPos;

            float swing = living.getHandSwingProgress(tickDelta);
            state.scope.setVariable("attack_time", MolangValue.of(swing));
            q.swingDuration = 6.0f;

            state.scope.setVariable("is_first_person", MolangValue.of(firstPerson));
            state.scope.setVariable("is_paperdoll", MolangValue.ZERO);
            state.scope.setVariable("map_face_icon", MolangValue.ZERO);
            state.scope.setVariable("player_x_rotation", MolangValue.of(pitch));
            boolean mainRight = living.getMainArm() == Arm.RIGHT;
            ItemStack rightStack = mainRight ? living.getMainHandStack() : living.getOffHandStack();
            ItemStack leftStack = mainRight ? living.getOffHandStack() : living.getMainHandStack();
            state.scope.setVariable("is_holding_right", MolangValue.of(!rightStack.isEmpty()));
            state.scope.setVariable("is_holding_left", MolangValue.of(!leftStack.isEmpty()));

            q.mainHandItem = itemIdString(living.getMainHandStack());
            q.offHandItem = itemIdString(living.getOffHandStack());
            q.headArmorItem = itemIdString(living.getEquippedStack(EquipmentSlot.HEAD));
            q.chestArmorItem = itemIdString(living.getEquippedStack(EquipmentSlot.CHEST));
            q.legsArmorItem = itemIdString(living.getEquippedStack(EquipmentSlot.LEGS));
            q.feetArmorItem = itemIdString(living.getEquippedStack(EquipmentSlot.FEET));
        } else {
            float yaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
            float pitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
            q.bodyYRotation = yaw;
            q.headYRotation = yaw;
            q.targetYRotation = 0f;
            q.headXRotation = pitch;
            q.targetXRotation = pitch;
            q.yawSpeed = MathHelper.wrapDegrees(entity.getYaw() - entity.prevYaw) * 20f;
        }

        if (entity instanceof PassiveEntity pe) {
            q.baby = pe.isBaby();
        }
        if (entity instanceof TameableEntity te) {
            q.tamed = te.isTamed();
            q.sitting = te.isInSittingPose() || te.isSitting();
            q.sitAmount = q.sitting ? 1f : 0f;
        }
        if (entity instanceof MobEntity mob) {
            q.hasTarget = mob.getTarget() != null;
            q.facingRangeAttack = mob.isAttacking();
            q.leashed = mob.isLeashed();
        }
        if (entity instanceof PlayerEntity player) {
            q.entityName = player.getName().getString();
            q.spectator = player.isSpectator();
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            var cam = mc.gameRenderer.getCamera();
            var camPos = cam.getPos();
            q.distanceFromCamera = (float) Math.sqrt(entity.squaredDistanceTo(camPos.x, camPos.y, camPos.z));
            q.cameraRotX = cam.getPitch();
            q.cameraRotY = cam.getYaw();
        }

        World world = entity.getWorld();
        if (world != null) {
            q.timeOfDay = world.getSkyAngle(tickDelta);
            q.moonPhase = world.getMoonPhase();
            RegistryEntry<Biome> biomeEntry = world.getBiome(entity.getBlockPos());
            if (biomeEntry != null) {
                q.biomeIdentifier = getBiomeId(world, biomeEntry.value());
            }
            final World worldRef = world;
            final float baseX = q.posX;
            final float baseY = q.posY;
            final float baseZ = q.posZ;
            q.blockTagChecker = (relative, rx, ry, rz, requireAll, args, startIdx) ->
                checkBlockTags(worldRef, baseX, baseY, baseZ, relative, rx, ry, rz, requireAll, args, startIdx);
        } else {
            q.blockTagChecker = null;
        }
    }

    private static boolean checkBlockTags(World world, float baseX, float baseY, float baseZ,
                                          boolean relative, int rx, int ry, int rz,
                                          boolean requireAll, MolangValue[] args, int startIdx) {
        int wx = relative ? MathHelper.floor(baseX) + rx : rx;
        int wy = relative ? MathHelper.floor(baseY) + ry : ry;
        int wz = relative ? MathHelper.floor(baseZ) + rz : rz;
        BlockState state = world.getBlockState(new BlockPos(wx, wy, wz));
        boolean anyMatch = false;
        for (int i = startIdx; i < args.length; i++) {
            boolean match = matchesBlockTag(state, args[i].asString());
            if (requireAll && !match) return false;
            if (!requireAll && match) return true;
            anyMatch |= match;
        }
        return requireAll ? (args.length > startIdx && anyMatch) : false;
    }

    private static boolean matchesBlockTag(BlockState state, String tag) {
        if (state == null || tag == null) return false;
        return switch (tag.toLowerCase(java.util.Locale.ROOT)) {
            case "water" -> !state.getFluidState().isEmpty() && state.getFluidState().isIn(FluidTags.WATER);
            case "snow" -> state.isIn(BlockTags.SNOW);
            case "minecraft:is_pickaxe_item_destructible" -> state.isIn(BlockTags.PICKAXE_MINEABLE);
            case "minecraft:is_axe_item_destructible" -> state.isIn(BlockTags.AXE_MINEABLE);
            case "minecraft:is_shovel_item_destructible" -> state.isIn(BlockTags.SHOVEL_MINEABLE);
            case "minecraft:is_hoe_item_destructible" -> state.isIn(BlockTags.HOE_MINEABLE);
            case "minecraft:is_shears_item_destructible" -> state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.WOOL);
            case "not_feature_replaceable" -> !state.isAir() && !state.isReplaceable();
            case "metal" -> state.isIn(BlockTags.ANVIL) || state.isIn(BlockTags.BEACON_BASE_BLOCKS);
            case "minecraft:iron_tier_destructible" -> state.isIn(BlockTags.NEEDS_IRON_TOOL);
            case "minecraft:stone_tier_destructible" -> state.isIn(BlockTags.NEEDS_STONE_TOOL);
            default -> false;
        };
    }

    private static void playPendingSounds(BedrockEntityState state, MinecraftClient client) {
        if (client == null || client.getSoundManager() == null) return;
        for (String sfx : state.drainPendingSounds()) {
            Identifier id = Identifier.tryParse(sfx);
            if (id != null) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(id), 1.0f));
            }
        }
    }

    public static String getBiomeId(World world, Biome biome) {
        if (biome == null) return "minecraft:plains";
        String cached = BIOME_ID_CACHE.get(biome);
        if (cached != null) return cached;
        if (world != null) {
            Registry<Biome> reg = world.getRegistryManager().get(RegistryKeys.BIOME);
            Identifier id = reg.getId(biome);
            if (id != null) {
                String str = id.toString();
                BIOME_ID_CACHE.put(biome, str);
                return str;
            }
        }
        return "minecraft:plains";
    }

    public static String getCurrentBiomeId() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return "minecraft:plains";
        BlockPos pos = client.gameRenderer.getCamera().getBlockPos();
        if (pos == null && client.player != null) pos = client.player.getBlockPos();
        if (pos == null) return "minecraft:plains";
        return getBiomeId(client.world, client.world.getBiome(pos).value());
    }

    private static String itemIdString(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null ? id.toString() : "";
    }

    private static float sanitizeScale(float v, float fallback) {
        return (Float.isFinite(v) && v > 0.001f && v < 100f) ? v : fallback;
    }

    public static Matrix4f debugDummyMatrix() {
        return new Matrix4f();
    }
}
