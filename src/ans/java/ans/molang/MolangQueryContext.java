package ans.molang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Provides runtime bindings for all 174 Bedrock {@code q.*} / {@code query.*} queries
 * and {@code c.*} context values used by Actions &amp; Stuff animations, attachables,
 * render controllers, and particle systems.
 */
public interface MolangQueryContext {

    /** Active pack settings loaded from Bedrock {@code manifest.json} defaults and toggles. */
    Map<String, String> PACK_SETTINGS = new HashMap<>(Map.ofEntries(
        Map.entry("oreville_ans:fxwjdy", "enabled"),
        Map.entry("oreville_ans:mowakw", "disabled"),
        Map.entry("oreville_ans:pntihm", "default"),
        Map.entry("oreville_ans:jyuhzq", "pink"),
        Map.entry("oreville_ans:cvmbbr", "enabled"),
        Map.entry("oreville_ans:stghmf", "default"),
        Map.entry("oreville_ans:iocufj", "magenta"),
        Map.entry("oreville_ans:eijyts", "aqapck"),
        Map.entry("oreville_ans:wzqdjp", "enabled"),
        Map.entry("oreville_ans:jlfjfs", "disabled"),
        Map.entry("oreville_ans:ksgtzn", "disabled"),
        Map.entry("oreville_ans:ltqtrn", "enabled"),
        Map.entry("oreville_ans:nrfgyi", "enabled"),
        Map.entry("oreville_ans:unewfl", "enabled"),
        Map.entry("oreville_ans:rtnckq", "enabled"),
        Map.entry("oreville_ans:iafdwt", "disabled")
    ));

    Set<String> PACK_MULTISELECT = Collections.unmodifiableSet(Set.of("xcbjxh", "qqewgw", "lrwjip"));
    Set<String> PACK_MULTISELECT_FYUMXP = Collections.unmodifiableSet(Set.of(
        "hfjbef", "flpxjs", "rhxywv", "eysaic", "yzjxra", "eeuspn", "taekbc", "rwbslw", "jwgzkw"
    ));

    MolangValue query(String nameLower, MolangValue[] args, MolangScope scope);

    MolangValue context(String nameLower, MolangScope scope);

    @FunctionalInterface
    interface BlockTagChecker {
        boolean hasTags(boolean relative, int x, int y, int z, boolean requireAll, MolangValue[] args, int startIdx);
    }

    default void playSoundEffect(String effectKey) {
    }

    default void spawnParticleEffect(String effectKey, String locator) {
    }

    /**
     * Evaluates a pack setting toggle/selection query across both scalar dropdowns and multi-select toggles.
     */
    static boolean isPackSettingSelected(String settingName, String optionValue) {
        if (settingName == null) return false;
        String k = settingName.toLowerCase(Locale.ROOT);
        String v = optionValue == null ? "" : optionValue.toLowerCase(Locale.ROOT);
        if ("oreville_ans:wjwymg".equals(k)) {
            return PACK_MULTISELECT.contains(v);
        }
        if ("oreville_ans:fyumxp".equals(k)) {
            return PACK_MULTISELECT_FYUMXP.contains(v);
        }
        String current = PACK_SETTINGS.getOrDefault(k, "");
        return current.equalsIgnoreCase(v);
    }

    static boolean isPackSettingEnabled(String settingName) {
        if (settingName == null) return false;
        String k = settingName.toLowerCase(Locale.ROOT);
        String current = PACK_SETTINGS.getOrDefault(k, "");
        return !"disabled".equalsIgnoreCase(current) && !current.isEmpty();
    }

    /**
     * Stateless / standalone fallback implementation supporting all 174 {@code q.*} queries
     * for offline verification and unit simulation when no live client entity is attached.
     */
    class DefaultContext implements MolangQueryContext {
        public float animTime = 0f;
        public float lifeTime = 0f;
        public float stateTime = 0f;
        public float deltaTime = 1f / 20f;
        public float modifiedDistanceMoved = 0f;
        public float modifiedMoveSpeed = 0f;
        public float groundSpeed = 0f;
        public float verticalSpeed = 0f;
        public float walkDistance = 0f;
        public float yawSpeed = 0f;
        public float health = 20f;
        public float maxHealth = 20f;
        public float hurtTime = 0f;
        public float hurtDirection = 0f;
        public float deathTicks = 0f;
        public float headXRotation = 0f;
        public float headYRotation = 0f;
        public float bodyXRotation = 0f;
        public float bodyYRotation = 0f;
        public float targetXRotation = 0f;
        public float targetYRotation = 0f;
        public float capeFlap = 0f;
        public float modelScale = 0.0625f;
        public float standingScale = 1f;
        public float swellAmount = 0f;
        public float swellingDir = 0f;
        public float fuseTime = 0f;
        public float sitAmount = 0f;
        public float lieAmount = 0f;
        public float tailAngle = 0f;
        public float timeOfDay = 0.25f;
        public float moonPhase = 0f;
        public float frameAlpha = 0f;
        public float distanceFromCamera = 5f;
        public float cameraRotX = 0f;
        public float cameraRotY = 0f;
        public float posX = 0f;
        public float posY = 64f;
        public float posZ = 0f;
        public float deltaX = 0f;
        public float deltaY = 0f;
        public float deltaZ = 0f;
        public float swingDuration = 0.3f;
        public float itemUseDuration = 0f;
        public float itemMaxDuration = 0f;
        public float cooldownTime = 0f;
        public float cooldownRemaining = 0f;
        public float timeStamp = 0f;
        public int variant = 0;
        public int markVariant = 0;
        public int skinId = 0;
        public int tradeTier = 0;
        public int equipmentCount = 0;
        public int invulnerableTicks = 0;
        public int rollCounter = 0;
        public int sneezeCounter = 0;
        public int textureFrameIndex = 0;
        public boolean alive = true;
        public boolean moving = false;
        public boolean onGround = true;
        public boolean inWater = false;
        public boolean inWaterContact = false;
        public boolean inLava = false;
        public boolean headInWater = false;
        public boolean baby = false;
        public boolean sneaking = false;
        public boolean sprinting = false;
        public boolean swimming = false;
        public boolean crawling = false;
        public boolean gliding = false;
        public boolean sleeping = false;
        public boolean sitting = false;
        public boolean riding = false;
        public boolean hasRider = false;
        public boolean hasPlayerRider = false;
        public boolean hasTarget = false;
        public boolean tamed = false;
        public boolean saddled = false;
        public boolean chested = false;
        public boolean sheared = false;
        public boolean angry = false;
        public boolean charged = false;
        public boolean charging = false;
        public boolean powered = false;
        public boolean shieldPowered = false;
        public boolean onFire = false;
        public boolean invisible = false;
        public boolean usingItem = false;
        public boolean blocking = false;
        public boolean itemCharged = false;
        public boolean leashed = false;
        public boolean attached = false;
        public boolean firstPerson = false;
        public boolean localPlayer = true;
        public boolean hasCape = true;
        public boolean onScreen = true;
        public boolean inUi = false;
        public boolean spectator = false;
        public boolean dancing = false;
        public boolean wallClimbing = false;
        public boolean shaking = false;
        public boolean shakingWetness = false;
        public boolean roaring = false;
        public boolean sniffing = false;
        public boolean digging = false;
        public boolean emerging = false;
        public boolean grazing = false;
        public boolean resting = false;
        public boolean playingDead = false;
        public boolean sonicBoom = false;
        public boolean casting = false;
        public boolean admiring = false;
        public boolean eating = false;
        public boolean eatingMob = false;
        public boolean interested = false;
        public boolean ghost = false;
        public boolean stunned = false;
        public boolean stalking = false;
        public boolean croaking = false;
        public boolean jumpGoalJumping = false;
        public boolean jumping = false;
        public boolean delayedAttacking = false;
        public boolean avoidingBlock = false;
        public boolean avoidingMobs = false;
        public boolean searching = false;
        public boolean emoting = false;
        public boolean carryingBlock = false;
        public boolean facingRangeAttack = false;
        public boolean dashCooldown = false;
        public boolean allAnimationsFinished = false;
        public boolean anyAnimationFinished = false;
        public String entityName = "Steve";
        public String mainHandItem = "";
        public String offHandItem = "";
        public String headArmorItem = "";
        public String chestArmorItem = "";
        public String legsArmorItem = "";
        public String feetArmorItem = "";
        public String itemSlot = "main_hand";
        public String ownerIdentifier = "minecraft:player";
        public String ridingEntityIdentifier = "";
        public String biomeIdentifier = "minecraft:plains";
        public MolangValue owningEntity = MolangValue.ZERO;
        public BlockTagChecker blockTagChecker = null;

        protected final Map<String, MolangValue> boneTrs = new HashMap<>();
        protected final Map<String, float[]> boneOrigins = new HashMap<>();
        protected final Map<String, MolangValue> properties = new HashMap<>();
        protected final List<String> pendingSoundEffects = new ArrayList<>();
        protected final List<String[]> pendingParticleEffects = new ArrayList<>();

        @Override
        public void playSoundEffect(String effectKey) {
            if (effectKey != null && !effectKey.isEmpty()) {
                pendingSoundEffects.add(effectKey.toLowerCase(Locale.ROOT));
            }
        }

        @Override
        public void spawnParticleEffect(String effectKey, String locator) {
            if (effectKey != null && !effectKey.isEmpty()) {
                pendingParticleEffects.add(new String[]{effectKey.toLowerCase(Locale.ROOT), locator == null ? "" : locator});
            }
        }

        public List<String> drainPendingSoundEffects() {
            if (pendingSoundEffects.isEmpty()) return List.of();
            List<String> copy = new ArrayList<>(pendingSoundEffects);
            pendingSoundEffects.clear();
            return copy;
        }

        public List<String[]> drainPendingParticleEffects() {
            if (pendingParticleEffects.isEmpty()) return List.of();
            List<String[]> copy = new ArrayList<>(pendingParticleEffects);
            pendingParticleEffects.clear();
            return copy;
        }

        public void setBoneTrs(String bone, MolangValue trs) {
            if (bone != null && trs != null) {
                boneTrs.put(bone.toLowerCase(Locale.ROOT), trs);
            }
        }

        public void setBoneOrigin(String bone, float x, float y, float z) {
            if (bone != null) {
                boneOrigins.put(bone.toLowerCase(Locale.ROOT), new float[]{x, y, z});
            }
        }

        @Override
        public MolangValue query(String q, MolangValue[] args, MolangScope scope) {
            return switch (q) {
                case "all_animations_finished" -> MolangValue.of(allAnimationsFinished);
                case "any_animation_finished" -> MolangValue.of(anyAnimationFinished);
                case "anim_time" -> MolangValue.of(animTime);
                case "life_time", "life_span" -> MolangValue.of(lifeTime);
                case "state_time" -> MolangValue.of(stateTime);
                case "delta_time" -> MolangValue.of(deltaTime);
                case "time_stamp", "t" -> MolangValue.of(timeStamp);
                case "frame_alpha" -> MolangValue.of(frameAlpha);
                case "time_of_day" -> MolangValue.of(timeOfDay);
                case "moon_phase" -> MolangValue.of(moonPhase);
                case "any" -> {
                    if (args == null || args.length < 2) yield MolangValue.ZERO;
                    MolangValue target = args[0];
                    for (int i = 1; i < args.length; i++) {
                        if (target.looseEquals(args[i])) yield MolangValue.ONE;
                    }
                    yield MolangValue.ZERO;
                }
                case "in_range" -> {
                    double v = argD(args, 0);
                    double lo = argD(args, 1);
                    double hi = argD(args, 2);
                    yield MolangValue.of(v >= Math.min(lo, hi) && v <= Math.max(lo, hi));
                }
                case "health" -> MolangValue.of(health);
                case "max_health" -> MolangValue.of(maxHealth);
                case "hurt_time" -> MolangValue.of(hurtTime);
                case "hurt_direction" -> MolangValue.of(hurtDirection);
                case "death_ticks" -> MolangValue.of(deathTicks);
                case "invulnerable_ticks" -> MolangValue.of(invulnerableTicks);
                case "ground_speed" -> MolangValue.of(groundSpeed);
                case "vertical_speed" -> MolangValue.of(verticalSpeed);
                case "modified_distance_moved" -> MolangValue.of(modifiedDistanceMoved);
                case "modified_move_speed" -> MolangValue.of(modifiedMoveSpeed);
                case "walk_distance" -> MolangValue.of(walkDistance);
                case "yaw_speed" -> MolangValue.of(yawSpeed);
                case "head_x_rotation" -> MolangValue.of(headXRotation);
                case "head_y_rotation" -> MolangValue.of(headYRotation);
                case "body_x_rotation" -> MolangValue.of(bodyXRotation);
                case "body_y_rotation", "ride_body_y_rotation" -> MolangValue.of(bodyYRotation);
                case "target_x_rotation" -> MolangValue.of(targetXRotation);
                case "target_y_rotation" -> MolangValue.of(targetYRotation);
                case "model_scale" -> MolangValue.of(modelScale);
                case "standing_scale" -> MolangValue.of(standingScale);
                case "swell_amount" -> MolangValue.of(swellAmount);
                case "swelling_dir" -> MolangValue.of(swellingDir);
                case "fuse_time" -> MolangValue.of(fuseTime);
                case "sit_amount" -> MolangValue.of(sitAmount);
                case "lie_amount" -> MolangValue.of(lieAmount);
                case "tail_angle" -> MolangValue.of(tailAngle);
                case "variant" -> MolangValue.of(variant);
                case "mark_variant" -> MolangValue.of(markVariant);
                case "skin_id" -> MolangValue.of(skinId);
                case "trade_tier" -> MolangValue.of(tradeTier);
                case "equipment_count" -> MolangValue.of(equipmentCount);
                case "roll_counter" -> MolangValue.of(rollCounter);
                case "sneeze_counter" -> MolangValue.of(sneezeCounter);
                case "texture_frame_index", "get_animation_frame" -> MolangValue.of(textureFrameIndex);
                case "base_swing_duration", "modified_swing_duration" -> MolangValue.of(swingDuration);
                case "main_hand_item_use_duration" -> MolangValue.of(itemUseDuration);
                case "main_hand_item_max_duration" -> MolangValue.of(itemMaxDuration);
                case "cooldown_time" -> MolangValue.of(cooldownTime);
                case "cooldown_time_remaining" -> MolangValue.of(cooldownRemaining);
                case "heartbeat_phase" -> MolangValue.of((lifeTime * 1.4) % 1.0);
                case "ticks_since_last_vibration_detection", "time_since_last_vibration_detection" -> MolangValue.of(100.0);
                case "ticks_since_last_kinetic_weapon_hit" -> MolangValue.of(100.0);
                case "timer_flag_2", "timer_flag_3" -> MolangValue.ZERO;
                case "structural_integrity" -> MolangValue.ONE;
                case "distance_from_camera" -> MolangValue.of(distanceFromCamera);
                case "camera_distance_range_lerp" -> {
                    double lo = argD(args, 0);
                    double hi = argD(args, 1);
                    double span = hi - lo;
                    yield MolangValue.of(span == 0.0 ? 0.0 : MolangMath.clamp((distanceFromCamera - lo) / span, 0.0, 1.0));
                }
                case "camera_rotation", "rotation_to_camera" -> {
                    int idx = (int) argD(args, 0);
                    yield MolangValue.of(idx == 0 ? cameraRotX : cameraRotY);
                }
                case "position" -> {
                    int idx = (int) argD(args, 0);
                    yield MolangValue.of(idx == 0 ? posX : (idx == 1 ? posY : posZ));
                }
                case "y" -> MolangValue.of(posY);
                case "position_delta" -> {
                    int idx = (int) argD(args, 0);
                    yield MolangValue.of(idx == 0 ? deltaX : (idx == 1 ? deltaY : deltaZ));
                }
                case "movement_direction" -> {
                    double len = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                    int idx = (int) argD(args, 0);
                    if (len < 1e-6) yield MolangValue.ZERO;
                    yield MolangValue.of((idx == 0 ? deltaX : (idx == 1 ? deltaY : deltaZ)) / len);
                }
                case "bone_orientation_trs" -> {
                    String b = argS(args, 0).toLowerCase(Locale.ROOT);
                    yield boneTrs.getOrDefault(b, MolangValue.trs(0, 0, 0, 0, 0, 0, 1, 1, 1));
                }
                case "bone_origin" -> {
                    String b = argS(args, 0).toLowerCase(Locale.ROOT);
                    float[] origin = boneOrigins.get(b);
                    if (args != null && args.length >= 2) {
                        int axis = (int) argD(args, 1);
                        if (origin == null || axis < 0 || axis > 2) yield MolangValue.ZERO;
                        yield MolangValue.of(origin[axis]);
                    }
                    MolangValue st = MolangValue.newStruct();
                    if (origin != null) {
                        st.setMember("x", MolangValue.of(origin[0]));
                        st.setMember("y", MolangValue.of(origin[1]));
                        st.setMember("z", MolangValue.of(origin[2]));
                    } else {
                        st.setMember("x", MolangValue.ZERO);
                        st.setMember("y", MolangValue.ZERO);
                        st.setMember("z", MolangValue.ZERO);
                    }
                    yield st;
                }
                case "get_root_locator_offset" -> MolangValue.ZERO;
                case "get_name" -> MolangValue.of(entityName);
                case "is_name_any" -> matchesAny(entityName, args, 0);
                case "get_equipped_item_name" -> {
                    String slot = argS(args, 0);
                    yield MolangValue.of(itemForSlot(slot));
                }
                case "is_item_equipped" -> MolangValue.of(!itemForSlot(argS(args, 0)).isEmpty());
                case "is_item_name_any" -> matchesAny(itemForSlot(argS(args, 0)), args, 1);
                case "equipped_item_any_tag" -> checkItemTags(itemForSlot(argS(args, 0)), args, 1);
                case "item_slot_to_bone_name" -> MolangValue.of(mapSlotToBone(argS(args, 0)));
                case "has_armor_slot" -> MolangValue.of(!armorForSlot((int) argD(args, 0)).isEmpty());
                case "armor_texture_slot" -> MolangValue.of(armorTextureSlot((int) argD(args, 0)));
                case "armor_color_slot" -> MolangValue.ZERO;
                case "armor_damage_slot" -> MolangValue.ZERO;
                case "has_head_gear" -> MolangValue.of(!headArmorItem.isEmpty());
                case "has_cape" -> MolangValue.of(hasCape);
                case "is_alive" -> MolangValue.of(alive);
                case "is_moving" -> MolangValue.of(moving);
                case "is_on_ground" -> MolangValue.of(onGround);
                case "is_in_water" -> MolangValue.of(inWater);
                case "is_in_contact_with_water" -> MolangValue.of(inWaterContact || inWater);
                case "is_in_lava" -> MolangValue.of(inLava);
                case "head_is_in_water" -> MolangValue.of(headInWater);
                case "is_baby" -> MolangValue.of(baby);
                case "is_sneaking" -> MolangValue.of(sneaking);
                case "is_sprinting" -> MolangValue.of(sprinting);
                case "is_swimming" -> MolangValue.of(swimming);
                case "is_crawling" -> MolangValue.of(crawling);
                case "is_gliding" -> MolangValue.of(gliding);
                case "is_sleeping" -> MolangValue.of(sleeping);
                case "is_sitting" -> MolangValue.of(sitting);
                case "is_riding" -> MolangValue.of(riding);
                case "has_rider" -> MolangValue.of(hasRider);
                case "has_player_rider" -> MolangValue.of(hasPlayerRider);
                case "has_target" -> MolangValue.of(hasTarget);
                case "is_tamed" -> MolangValue.of(tamed);
                case "is_saddled" -> MolangValue.of(saddled);
                case "is_chested" -> MolangValue.of(chested);
                case "is_sheared" -> MolangValue.of(sheared);
                case "is_angry" -> MolangValue.of(angry);
                case "is_charged" -> MolangValue.of(charged);
                case "is_charging" -> MolangValue.of(charging);
                case "is_powered" -> MolangValue.of(powered);
                case "is_shield_powered" -> MolangValue.of(shieldPowered);
                case "is_on_fire" -> MolangValue.of(onFire);
                case "is_invisible" -> MolangValue.of(invisible);
                case "is_using_item" -> MolangValue.of(usingItem);
                case "blocking" -> MolangValue.of(blocking);
                case "item_is_charged" -> MolangValue.of(itemCharged);
                case "is_leashed" -> MolangValue.of(leashed);
                case "is_attached", "is_attached_to_entity" -> MolangValue.of(attached);
                case "is_local_player" -> MolangValue.of(localPlayer);
                case "is_on_screen" -> MolangValue.of(onScreen);
                case "is_in_ui" -> MolangValue.of(inUi);
                case "is_spectator" -> MolangValue.of(spectator);
                case "is_dancing" -> MolangValue.of(dancing);
                case "is_wall_climbing" -> MolangValue.of(wallClimbing);
                case "is_shaking" -> MolangValue.of(shaking);
                case "is_shaking_wetness" -> MolangValue.of(shakingWetness);
                case "is_roaring" -> MolangValue.of(roaring);
                case "is_sniffing" -> MolangValue.of(sniffing);
                case "is_digging" -> MolangValue.of(digging);
                case "is_emerging" -> MolangValue.of(emerging);
                case "is_grazing" -> MolangValue.of(grazing);
                case "is_resting" -> MolangValue.of(resting);
                case "is_playing_dead" -> MolangValue.of(playingDead);
                case "is_sonic_boom" -> MolangValue.of(sonicBoom);
                case "is_casting" -> MolangValue.of(casting);
                case "is_admiring" -> MolangValue.of(admiring);
                case "is_eating" -> MolangValue.of(eating);
                case "is_eating_mob" -> MolangValue.of(eatingMob);
                case "is_interested" -> MolangValue.of(interested);
                case "is_ghost" -> MolangValue.of(ghost);
                case "is_stunned" -> MolangValue.of(stunned);
                case "is_stalking" -> MolangValue.of(stalking);
                case "is_croaking" -> MolangValue.of(croaking);
                case "is_jump_goal_jumping" -> MolangValue.of(jumpGoalJumping);
                case "is_jumping" -> MolangValue.of(jumping);
                case "is_delayed_attacking" -> MolangValue.of(delayedAttacking);
                case "is_avoiding_block" -> MolangValue.of(avoidingBlock);
                case "is_avoiding_mobs" -> MolangValue.of(avoidingMobs);
                case "is_searching" -> MolangValue.of(searching);
                case "is_emoting" -> MolangValue.of(emoting);
                case "is_carrying_block" -> MolangValue.of(carryingBlock);
                case "facing_target_to_range_attack" -> MolangValue.of(facingRangeAttack);
                case "has_dash_cooldown" -> MolangValue.of(dashCooldown);
                case "is_persona_or_premium_skin" -> MolangValue.ZERO;
                case "is_owner_identifier_any" -> matchesAny(ownerIdentifier, args, 0);
                case "is_riding_any_entity_of_type" -> {
                    if (!riding || ridingEntityIdentifier == null || ridingEntityIdentifier.isEmpty()) {
                        yield MolangValue.ZERO;
                    }
                    yield matchesAny(ridingEntityIdentifier, args, 0);
                }
                case "has_any_leashed_entity_of_type" -> MolangValue.ZERO;
                case "graphics_mode_is_any" -> {
                    for (int i = 0; args != null && i < args.length; i++) {
                        String mode = argS(args, i).toLowerCase(Locale.ROOT);
                        if ("simple".equals(mode) || "fancy".equals(mode)) {
                            yield MolangValue.ONE;
                        }
                    }
                    yield MolangValue.ZERO;
                }
                case "is_pack_setting_selected" -> MolangValue.of(isPackSettingSelected(argS(args, 0), argS(args, 1)));
                case "is_pack_setting_enabled" -> MolangValue.of(isPackSettingEnabled(argS(args, 0)));
                case "entity_biome_has_any_identifier" -> matchesAny(biomeIdentifier, args, 0);
                case "entity_biome_has_any_tags" -> MolangValue.ZERO;
                case "block_has_any_tag", "relative_block_has_any_tag", "relative_block_has_all_tags" -> {
                    if (blockTagChecker == null || args == null || args.length < 4) {
                        yield MolangValue.ZERO;
                    }
                    boolean relative = !"block_has_any_tag".equals(q);
                    boolean requireAll = "relative_block_has_all_tags".equals(q);
                    int x = (int) Math.floor(argD(args, 0));
                    int y = (int) Math.floor(argD(args, 1));
                    int z = (int) Math.floor(argD(args, 2));
                    yield MolangValue.of(blockTagChecker.hasTags(relative, x, y, z, requireAll, args, 3));
                }
                case "has_property" -> MolangValue.of(properties.containsKey(argS(args, 0).toLowerCase(Locale.ROOT)));
                case "property" -> properties.getOrDefault(argS(args, 0).toLowerCase(Locale.ROOT), MolangValue.ZERO);
                case "surface_particle_color" -> MolangValue.ONE;
                case "surface_particle_texture_coordinate", "surface_particle_texture_size" -> MolangValue.ZERO;
                default -> MolangValue.ZERO;
            };
        }

        @Override
        public MolangValue context(String c, MolangScope scope) {
            return switch (c) {
                case "is_first_person" -> MolangValue.of(firstPerson);
                case "item_slot" -> MolangValue.of(itemSlot);
                case "owning_entity" -> owningEntity;
                case "player_offhand_arm_height" -> MolangValue.of(offHandItem.isEmpty() ? 0.0 : -0.5);
                case "r", "g", "b", "a", "t", "u", "v", "x", "y", "z" -> scope.getContextVar(c);
                default -> scope.getContextVar(c);
            };
        }

        protected String itemForSlot(String slot) {
            if (slot == null) return mainHandItem;
            return switch (slot.toLowerCase(Locale.ROOT)) {
                case "slot.weapon.offhand", "offhand" -> offHandItem;
                case "slot.armor.head", "head" -> headArmorItem;
                case "slot.armor.chest", "chest" -> chestArmorItem;
                case "slot.armor.legs", "legs" -> legsArmorItem;
                case "slot.armor.feet", "feet" -> feetArmorItem;
                default -> mainHandItem;
            };
        }

        protected String armorForSlot(int slot) {
            return switch (slot) {
                case 0 -> headArmorItem;
                case 1 -> chestArmorItem;
                case 2 -> legsArmorItem;
                case 3 -> feetArmorItem;
                default -> "";
            };
        }

        protected int armorTextureSlot(int slot) {
            String item = armorForSlot(slot).toLowerCase(Locale.ROOT);
            if (item.contains("leather")) return 0;
            if (item.contains("chain")) return 1;
            if (item.contains("iron")) return 2;
            if (item.contains("diamond")) return 3;
            if (item.contains("gold")) return 4;
            if (item.contains("netherite")) return 5;
            if (item.contains("turtle")) return 6;
            if (item.contains("elytra")) return 7;
            return -1;
        }

        private static MolangValue matchesAny(String val, MolangValue[] args, int startIdx) {
            if (val == null || args == null) return MolangValue.ZERO;
            String v = val.toLowerCase(Locale.ROOT);
            for (int i = startIdx; i < args.length; i++) {
                if (v.equals(argS(args, i).toLowerCase(Locale.ROOT))) {
                    return MolangValue.ONE;
                }
            }
            return MolangValue.ZERO;
        }

        private static MolangValue checkItemTags(String itemName, MolangValue[] args, int startIdx) {
            if (itemName == null || itemName.isEmpty() || args == null) return MolangValue.ZERO;
            String low = itemName.toLowerCase(Locale.ROOT);
            for (int i = startIdx; i < args.length; i++) {
                String tag = argS(args, i).toLowerCase(Locale.ROOT);
                if (tag.contains("sword") && low.contains("sword")) return MolangValue.ONE;
                if (tag.contains("pickaxe") && low.contains("pickaxe")) return MolangValue.ONE;
                if (tag.contains("axe") && low.contains("_axe")) return MolangValue.ONE;
                if (tag.contains("shovel") && low.contains("shovel")) return MolangValue.ONE;
                if (tag.contains("hoe") && low.contains("hoe")) return MolangValue.ONE;
                if (tag.contains("bow") && low.contains("bow")) return MolangValue.ONE;
                if (tag.contains("trident") && low.contains("trident")) return MolangValue.ONE;
                if (tag.contains("tool") && (low.contains("pickaxe") || low.contains("_axe") || low.contains("shovel") || low.contains("hoe"))) return MolangValue.ONE;
            }
            return MolangValue.ZERO;
        }

        private static String mapSlotToBone(String slot) {
            if (slot == null) return "rightitem";
            return switch (slot.toLowerCase(Locale.ROOT)) {
                case "slot.weapon.offhand", "offhand", "off_hand" -> "leftitem";
                case "slot.armor.head", "head" -> "head";
                case "slot.armor.chest", "chest", "torso" -> "body";
                case "slot.armor.legs", "legs" -> "leftleg";
                case "slot.armor.feet", "feet" -> "leftleg";
                default -> "rightitem";
            };
        }

        private static double argD(MolangValue[] args, int idx) {
            return (args != null && idx < args.length && args[idx] != null) ? args[idx].asDouble() : 0.0;
        }

        private static String argS(MolangValue[] args, int idx) {
            return (args != null && idx < args.length && args[idx] != null) ? args[idx].asString() : "";
        }
    }
}
