package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorMode;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

public class UICrowdBehaviorActionClip extends UIActionClip<CrowdBehaviorActionClip>
{
    private UILabel crowdTag;
    private UIButton target;
    private UIButton mode;
    private UIToggle pause;
    private UIToggle sprint;
    private UITrackpad speed;
    private UITrackpad moveEase;
    private UITrackpad stopDistance;
    private UITrackpad targetSpread;
    private UITrackpad disperseRadius;
    private UITrackpad wanderInterval;
    private UITrackpad lookAroundTicks;
    private UITrackpad range;
    private UITrackpad pathRefresh;
    private UITrackpad seed;
    private UITrackpad wanderRadius;
    private UITrackpad separation;
    private UITrackpad maxStepHeight;
    private UIToggle autoJump;
    private UIToggle crouch;
    private UIToggle zigZag;
    private UIToggle randomJump;
    private UITrackpad jumpRate;
    private UIToggle armSwing;
    private UITrackpad armSwingRate;
    private UIToggle headMotion;
    private UITrackpad energy;
    private UIToggle lookAtTarget;
    private UITrackpad lookEase;
    private UITrackpad headYawLimit;
    private UIToggle lookBodyYaw;
    private UIToggle lookHeadYaw;
    private UIToggle lookHeadPitch;
    private UIButton enemyReplay;
    private UITrackpad fightDamage;
    private UITrackpad attackRate;
    private UITrackpad engagementDistance;
    private UITrackpad fightRadius;
    private UITrackpad retargetTicks;
    private UITrackpad fightRandomness;
    private UIToggle shoot;
    private UITrackpad shootRate;
    private UIItemStack shootItem;
    private UINestedEdit projectileModel;
    private UITrackpad projectileSpeed;
    private UITrackpad projectileLifeSpan;
    private UINestedEdit impactModel;
    private UITrackpad impactBounces;
    private UITrackpad impactBounceDamping;
    private UIToggle impactVanish;
    private UITrackpad impactDamage;
    private UITrackpad impactKnockback;
    private UIToggle impactCollideBlocks;
    private UIToggle impactCollideEntities;
    private UITextbox cmdFiring;
    private UITextbox cmdImpact;
    private UITextbox cmdVanish;
    private UITextbox cmdTicking;
    private UITrackpad ticking;

    public UICrowdBehaviorActionClip(CrowdBehaviorActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.crowdTag = UI.label(IKey.EMPTY);
        this.crowdTag.tooltip(IKey.constant("The crowd this behaviour drives: the one belonging to the replay the clip sits on.\n\nTo drive a different crowd, put the clip on that crowd's replay."));
        this.target = new UIButton(IKey.EMPTY, (b) -> this.openTargetPicker());
        this.mode = new UIButton(IKey.EMPTY, (b) -> this.openModeMenu());
        this.pause = new UIToggle(IKey.constant("Pause"), (b) -> this.editor.editMultiple(this.clip.pause, (value) -> value.set(b.getValue())));
        this.sprint = new UIToggle(IKey.constant("Sprint"), (b) -> this.editor.editMultiple(this.clip.sprint, (value) -> value.set(b.getValue())));
        this.speed = new UITrackpad((value) -> this.editor.editMultiple(this.clip.speed, (v) -> v.set(value.floatValue())));
        this.speed.limit(this.clip.speed).values(0.05D, 0.01D, 0.5D);
        this.moveEase = new UITrackpad((value) -> this.editor.editMultiple(this.clip.moveEase, (v) -> v.set(value.intValue())));
        this.moveEase.limit(this.clip.moveEase).integer();
        this.stopDistance = new UITrackpad((value) -> this.editor.editMultiple(this.clip.stopDistance, (v) -> v.set(value.floatValue())));
        this.stopDistance.limit(this.clip.stopDistance).values(0.1D);
        this.targetSpread = new UITrackpad((value) -> this.editor.editMultiple(this.clip.targetSpread, (v) -> v.set(value.floatValue())));
        this.targetSpread.limit(this.clip.targetSpread).values(0.1D);
        this.disperseRadius = new UITrackpad((value) -> this.editor.editMultiple(this.clip.disperseRadius, (v) -> v.set(value.floatValue())));
        this.disperseRadius.limit(this.clip.disperseRadius).values(0.25D, 0.05D, 1D);
        this.wanderInterval = new UITrackpad((value) -> this.editor.editMultiple(this.clip.wanderInterval, (v) -> v.set(value.intValue())));
        this.wanderInterval.limit(this.clip.wanderInterval).integer();
        this.lookAroundTicks = new UITrackpad((value) -> this.editor.editMultiple(this.clip.lookAroundTicks, (v) -> v.set(value.intValue())));
        this.lookAroundTicks.limit(this.clip.lookAroundTicks).integer();
        this.range = new UITrackpad((value) -> this.editor.editMultiple(this.clip.range, (v) -> v.set(value.floatValue())));
        this.range.limit(this.clip.range).values(1D);
        this.pathRefresh = new UITrackpad((value) -> this.editor.editMultiple(this.clip.pathRefresh, (v) -> v.set(value.intValue())));
        this.pathRefresh.limit(this.clip.pathRefresh).integer();
        this.seed = new UITrackpad((value) -> this.editor.editMultiple(this.clip.seed, (v) -> v.set(value.intValue())));
        this.seed.integer();
        this.wanderRadius = new UITrackpad((value) -> this.editor.editMultiple(this.clip.wanderRadius, (v) -> v.set(value.floatValue())));
        this.wanderRadius.limit(this.clip.wanderRadius).values(0.25D, 0.05D, 1D);
        this.separation = new UITrackpad((value) -> this.editor.editMultiple(this.clip.separation, (v) -> v.set(value.floatValue())));
        this.separation.limit(this.clip.separation).values(0.05D, 0.01D, 0.25D);
        this.maxStepHeight = new UITrackpad((value) -> this.editor.editMultiple(this.clip.maxStepHeight, (v) -> v.set(value.floatValue())));
        this.maxStepHeight.limit(this.clip.maxStepHeight).values(0.05D, 0.01D, 0.1D);
        this.autoJump = new UIToggle(IKey.constant("Auto jump"), (b) -> this.editor.editMultiple(this.clip.autoJump, (value) -> value.set(b.getValue())));
        this.autoJump.tooltip(IKey.constant("Hop over obstacles a step cannot climb, such as a single block of terrain, instead of piling up against them."));
        this.crouch = new UIToggle(IKey.constant("Crouch"), (b) -> this.editor.editMultiple(this.clip.crouch, (value) -> value.set(b.getValue())));
        this.zigZag = new UIToggle(IKey.constant("Zig zag"), (b) -> this.editor.editMultiple(this.clip.zigZag, (value) -> value.set(b.getValue())));
        this.randomJump = new UIToggle(IKey.constant("Jump"), (b) -> this.editor.editMultiple(this.clip.randomJump, (value) -> value.set(b.getValue())));
        this.jumpRate = new UITrackpad((value) -> this.editor.editMultiple(this.clip.jumpRate, (v) -> v.set(value.floatValue())));
        this.jumpRate.limit(this.clip.jumpRate).values(0.05D, 0.01D, 0.25D);
        this.armSwing = new UIToggle(IKey.constant("Arm swipe"), (b) -> this.editor.editMultiple(this.clip.armSwing, (value) -> value.set(b.getValue())));
        this.armSwingRate = new UITrackpad((value) -> this.editor.editMultiple(this.clip.armSwingRate, (v) -> v.set(value.floatValue())));
        this.armSwingRate.limit(this.clip.armSwingRate).values(0.05D, 0.01D, 0.25D);
        this.headMotion = new UIToggle(IKey.constant("Head motion"), (b) -> this.editor.editMultiple(this.clip.headMotion, (value) -> value.set(b.getValue())));
        this.energy = new UITrackpad((value) -> this.editor.editMultiple(this.clip.energy, (v) -> v.set(value.floatValue())));
        this.energy.limit(this.clip.energy).values(0.05D, 0.01D, 0.25D);
        this.lookAtTarget = new UIToggle(IKey.constant("Look"), (b) -> this.editor.editMultiple(this.clip.lookAtTarget, (value) -> value.set(b.getValue())));
        this.lookEase = new UITrackpad((value) -> this.editor.editMultiple(this.clip.lookEase, (v) -> v.set(value.intValue())));
        this.lookEase.limit(this.clip.lookEase).integer();
        this.headYawLimit = new UITrackpad((value) -> this.editor.editMultiple(this.clip.headYawLimit, (v) -> v.set(value.floatValue())));
        this.headYawLimit.limit(this.clip.headYawLimit).values(1D, 0.25D, 5D);
        this.lookBodyYaw = new UIToggle(IKey.constant("Body yaw"), (b) -> this.editor.editMultiple(this.clip.lookBodyYaw, (value) -> value.set(b.getValue())));
        this.lookHeadYaw = new UIToggle(IKey.constant("Head yaw"), (b) -> this.editor.editMultiple(this.clip.lookHeadYaw, (value) -> value.set(b.getValue())));
        this.lookHeadPitch = new UIToggle(IKey.constant("Pitch"), (b) -> this.editor.editMultiple(this.clip.lookHeadPitch, (value) -> value.set(b.getValue())));
        this.enemyReplay = new UIButton(IKey.EMPTY, (b) -> this.openEnemyPicker());
        this.enemyReplay.tooltip(IKey.constant("The crowd replay this crowd fights.\n\nPick the other crowd's replay to set the two on each other."));
        this.fightDamage = new UITrackpad((value) -> this.editor.editMultiple(this.clip.fightDamage, (v) -> v.set(value.floatValue())));
        this.fightDamage.limit(this.clip.fightDamage).values(0.25D, 0.05D, 1D);
        this.attackRate = new UITrackpad((value) -> this.editor.editMultiple(this.clip.attackRate, (v) -> v.set(value.floatValue())));
        this.attackRate.limit(this.clip.attackRate).values(0.05D, 0.01D, 0.25D);
        this.engagementDistance = new UITrackpad((value) -> this.editor.editMultiple(this.clip.engagementDistance, (v) -> v.set(value.floatValue())));
        this.engagementDistance.limit(this.clip.engagementDistance).values(0.05D, 0.01D, 0.25D);
        this.fightRadius = new UITrackpad((value) -> this.editor.editMultiple(this.clip.fightRadius, (v) -> v.set(value.floatValue())));
        this.fightRadius.limit(this.clip.fightRadius).values(0.25D, 0.05D, 1D);
        this.retargetTicks = new UITrackpad((value) -> this.editor.editMultiple(this.clip.retargetTicks, (v) -> v.set(value.intValue())));
        this.retargetTicks.limit(this.clip.retargetTicks).integer();
        this.fightRandomness = new UITrackpad((value) -> this.editor.editMultiple(this.clip.fightRandomness, (v) -> v.set(value.floatValue())));
        this.fightRandomness.limit(this.clip.fightRandomness).values(0.1D, 0.01D, 0.5D);
        this.shoot = new UIToggle(IKey.constant("Shoot"), (b) -> this.editor.editMultiple(this.clip.shoot, (value) -> value.set(b.getValue())));
        this.shootRate = new UITrackpad((value) -> this.editor.editMultiple(this.clip.shootRate, (v) -> v.set(value.floatValue())));
        this.shootRate.limit(this.clip.shootRate).values(0.05D, 0.01D, 0.25D);
        this.shootItem = new UIItemStack((stack) -> this.editor.editMultiple(this.clip.shootItem, (value) -> value.set(stack.copy())));
        this.projectileModel = new UINestedEdit((edit) -> this.openFormPicker(this.clip.projectileModel, this.projectileModel, edit)).keybinds();
        this.projectileSpeed = new UITrackpad((value) -> this.editor.editMultiple(this.clip.projectileSpeed, (v) -> v.set(value.floatValue())));
        this.projectileSpeed.limit(this.clip.projectileSpeed).values(0.1D, 0.01D, 0.5D);
        this.projectileLifeSpan = new UITrackpad((value) -> this.editor.editMultiple(this.clip.projectileLifeSpan, (v) -> v.set(value.intValue())));
        this.projectileLifeSpan.limit(this.clip.projectileLifeSpan).integer();
        this.impactModel = new UINestedEdit((edit) -> this.openFormPicker(this.clip.impactModel, this.impactModel, edit)).keybinds();
        this.impactBounces = new UITrackpad((value) -> this.editor.editMultiple(this.clip.impactBounces, (v) -> v.set(value.intValue())));
        this.impactBounces.limit(this.clip.impactBounces).integer();
        this.impactBounceDamping = new UITrackpad((value) -> this.editor.editMultiple(this.clip.impactBounceDamping, (v) -> v.set(value.floatValue())));
        this.impactBounceDamping.limit(this.clip.impactBounceDamping).values(0.05D, 0.01D, 0.1D);
        this.impactVanish = new UIToggle(IKey.constant("Vanish"), (b) -> this.editor.editMultiple(this.clip.impactVanish, (value) -> value.set(b.getValue())));
        this.impactDamage = new UITrackpad((value) -> this.editor.editMultiple(this.clip.impactDamage, (v) -> v.set(value.floatValue())));
        this.impactDamage.limit(this.clip.impactDamage).values(0.25D, 0.05D, 1D);
        this.impactKnockback = new UITrackpad((value) -> this.editor.editMultiple(this.clip.impactKnockback, (v) -> v.set(value.floatValue())));
        this.impactKnockback.limit(this.clip.impactKnockback).values(0.1D, 0.01D, 0.5D);
        this.impactCollideBlocks = new UIToggle(IKey.constant("Blocks"), (b) -> this.editor.editMultiple(this.clip.impactCollideBlocks, (value) -> value.set(b.getValue())));
        this.impactCollideEntities = new UIToggle(IKey.constant("Entities"), (b) -> this.editor.editMultiple(this.clip.impactCollideEntities, (value) -> value.set(b.getValue())));
        this.cmdFiring = new UITextbox(10000, (text) -> this.editor.editMultiple(this.clip.cmdFiring, (value) -> value.set(text)));
        this.cmdImpact = new UITextbox(10000, (text) -> this.editor.editMultiple(this.clip.cmdImpact, (value) -> value.set(text)));
        this.cmdVanish = new UITextbox(10000, (text) -> this.editor.editMultiple(this.clip.cmdVanish, (value) -> value.set(text)));
        this.cmdTicking = new UITextbox(10000, (text) -> this.editor.editMultiple(this.clip.cmdTicking, (value) -> value.set(text)));
        this.ticking = new UITrackpad((value) -> this.editor.editMultiple(this.clip.ticking, (v) -> v.set(value.intValue())));
        this.ticking.limit(this.clip.ticking).integer();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(
            this.section("Crowd",
                this.row("Crowd", this.crowdTag),
                this.row("Target", this.target),
                this.row("Preset", this.mode),
                this.row("Seed", this.seed)
            ),
            this.section("Movement",
                UI.row(2, this.pause, this.sprint),
                this.row("Speed", this.speed),
                this.row("Move ease", this.moveEase),
                this.row("Stop", this.stopDistance),
                this.row("Spread", this.targetSpread),
                this.row("Disperse", this.disperseRadius),
                this.row("Wander", this.wanderInterval),
                this.row("Pause", this.lookAroundTicks),
                this.row("Range", this.range),
                this.row("Path", this.pathRefresh),
                this.row("Spacing", this.separation),
                this.row("Soft step", this.maxStepHeight),
                UI.row(2, this.autoJump)
            ),
            this.section("Action Area",
                this.row("Wander radius", this.wanderRadius)
            ),
            this.section("Performance",
                UI.row(2, this.crouch, this.zigZag),
                UI.row(2, this.randomJump, this.armSwing),
                this.row("Jump rate", this.jumpRate),
                this.row("Swipe rate", this.armSwingRate),
                UI.row(2, this.headMotion),
                this.row("Energy", this.energy)
            ),
            this.section("Look",
                UI.row(2, this.lookAtTarget, this.lookBodyYaw),
                UI.row(2, this.lookHeadYaw, this.lookHeadPitch),
                this.row("Ease", this.lookEase),
                this.row("Head limit", this.headYawLimit)
            ),
            this.section("Fight",
                this.row("Enemy", this.enemyReplay),
                this.row("Damage", this.fightDamage),
                this.row("Attack rate", this.attackRate),
                this.row("Engage", this.engagementDistance),
                this.row("Radius", this.fightRadius),
                this.row("Retarget", this.retargetTicks),
                this.row("Random", this.fightRandomness)
            ),
            this.section("Shoot",
                this.shoot,
                this.row("Shoot rate", this.shootRate),
                this.row("Item", this.shootItem),
                this.row("Model", this.projectileModel),
                this.row("Speed", this.projectileSpeed),
                this.row("Life", this.projectileLifeSpan),
                this.row("Damage", this.impactDamage),
                this.row("Knockback", this.impactKnockback),
                this.row("Bounces", this.impactBounces),
                this.row("Damping", this.impactBounceDamping),
                UI.row(2, this.impactVanish, this.impactCollideBlocks, this.impactCollideEntities),
                this.row("Impact", this.impactModel),
                this.row("Fire cmd", this.cmdFiring),
                this.row("Hit cmd", this.cmdImpact),
                this.row("Gone cmd", this.cmdVanish),
                this.row("Tick cmd", this.cmdTicking),
                this.row("Tick rate", this.ticking)
            )
        );
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.refreshCrowdLabel();
        this.refreshTargetLabel();
        this.refreshModeLabel();
        this.pause.setValue(this.clip.pause.get());
        this.sprint.setValue(this.clip.sprint.get());
        this.speed.setValue(this.clip.speed.get());
        this.moveEase.setValue(this.clip.moveEase.get());
        this.stopDistance.setValue(this.clip.stopDistance.get());
        this.targetSpread.setValue(this.clip.targetSpread.get());
        this.disperseRadius.setValue(this.clip.disperseRadius.get());
        this.wanderInterval.setValue(this.clip.wanderInterval.get());
        this.lookAroundTicks.setValue(this.clip.lookAroundTicks.get());
        this.range.setValue(this.clip.range.get());
        this.pathRefresh.setValue(this.clip.pathRefresh.get());
        this.seed.setValue(this.clip.seed.get());
        this.wanderRadius.setValue(this.clip.wanderRadius.get());
        this.separation.setValue(this.clip.separation.get());
        this.maxStepHeight.setValue(this.clip.maxStepHeight.get());
        this.autoJump.setValue(this.clip.autoJump.get());
        this.crouch.setValue(this.clip.crouch.get());
        this.zigZag.setValue(this.clip.zigZag.get());
        this.randomJump.setValue(this.clip.randomJump.get());
        this.jumpRate.setValue(this.clip.jumpRate.get());
        this.armSwing.setValue(this.clip.armSwing.get());
        this.armSwingRate.setValue(this.clip.armSwingRate.get());
        this.headMotion.setValue(this.clip.headMotion.get());
        this.energy.setValue(this.clip.energy.get());
        this.lookAtTarget.setValue(this.clip.lookAtTarget.get());
        this.lookEase.setValue(this.clip.lookEase.get());
        this.headYawLimit.setValue(this.clip.headYawLimit.get());
        this.lookBodyYaw.setValue(this.clip.lookBodyYaw.get());
        this.lookHeadYaw.setValue(this.clip.lookHeadYaw.get());
        this.lookHeadPitch.setValue(this.clip.lookHeadPitch.get());
        this.refreshEnemyLabel();
        this.fightDamage.setValue(this.clip.fightDamage.get());
        this.attackRate.setValue(this.clip.attackRate.get());
        this.engagementDistance.setValue(this.clip.engagementDistance.get());
        this.fightRadius.setValue(this.clip.fightRadius.get());
        this.retargetTicks.setValue(this.clip.retargetTicks.get());
        this.fightRandomness.setValue(this.clip.fightRandomness.get());
        this.shoot.setValue(this.clip.shoot.get());
        this.shootRate.setValue(this.clip.shootRate.get());
        this.shootItem.setStack(this.clip.shootItem.get());
        this.projectileModel.setForm(this.clip.projectileModel.get());
        this.projectileSpeed.setValue(this.clip.projectileSpeed.get());
        this.projectileLifeSpan.setValue(this.clip.projectileLifeSpan.get());
        this.impactModel.setForm(this.clip.impactModel.get());
        this.impactBounces.setValue(this.clip.impactBounces.get());
        this.impactBounceDamping.setValue(this.clip.impactBounceDamping.get());
        this.impactVanish.setValue(this.clip.impactVanish.get());
        this.impactDamage.setValue(this.clip.impactDamage.get());
        this.impactKnockback.setValue(this.clip.impactKnockback.get());
        this.impactCollideBlocks.setValue(this.clip.impactCollideBlocks.get());
        this.impactCollideEntities.setValue(this.clip.impactCollideEntities.get());
        this.cmdFiring.setText(this.clip.cmdFiring.get());
        this.cmdImpact.setText(this.clip.cmdImpact.get());
        this.cmdVanish.setText(this.clip.cmdVanish.get());
        this.cmdTicking.setText(this.clip.cmdTicking.get());
        this.ticking.setValue(this.clip.ticking.get());
    }

    private UIElement row(String label, UIElement element)
    {
        return UI.row(4, this.label(label, 78), element);
    }

    private UIElement section(String label, UIElement... elements)
    {
        return UI.column(3, 0, UI.label(IKey.constant(label)), UI.column(3, 0, elements)).marginTop(4);
    }

    private UIElement label(String label, int width)
    {
        return UI.label(IKey.constant(label)).w(width);
    }

    private void openFormPicker(ValueForm value, UINestedEdit element, boolean editing)
    {
        UIFormPalette.open(this, editing, value.get(), (form) ->
        {
            this.editor.editMultiple(value, (v) -> v.set(FormUtils.copy(form)));
            element.setForm(form);
        });
    }

    private void openTargetPicker()
    {
        Film film = this.editor.getFilm();

        if (film == null)
        {
            return;
        }

        List<Replay> replays = film.replays.getList();

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, IKey.constant("None (stay at spawn)"), () -> this.applyTarget(CrowdBehaviorActionClip.TARGET_NONE));
            menu.action(Icons.REMOVE, IKey.constant("(clip replay)"), () -> this.applyTarget(CrowdBehaviorActionClip.TARGET_SELF));

            for (int i = 0; i < replays.size(); i++)
            {
                final int index = i;
                Replay replay = replays.get(i);

                menu.action(Icons.USER, IKey.constant(replay.getName()), () -> this.applyTarget(index));
            }
        });
    }

    /**
     * The replay this clip lives on, found by looking for the timeline holding it.
     *
     * <p>Nothing hands the clip editor its replay, and this is the only thing here that needs it,
     * so it is looked up rather than threaded through the delegate for one caller.</p>
     */
    private Replay findOwnReplay()
    {
        Film film = this.editor.getFilm();

        if (film == null)
        {
            return null;
        }

        for (Replay replay : film.replays.getList())
        {
            if (replay.actions.get().contains(this.clip))
            {
                return replay;
            }
        }

        return null;
    }

    /**
     * Show which crowd this drives. There is nothing to choose: it is the crowd of the replay the
     * clip sits on, because that replay owns exactly one.
     */
    private void refreshCrowdLabel()
    {
        Film film = this.editor.getFilm();
        String tag = this.clip.resolveCrowdTag(this.findOwnReplay());
        Crowd crowd = film == null ? null : film.crowds.byTag(tag);

        /* A behaviour on a replay that drives no crowd says so, rather than looking settled while
         * driving nobody. */
        this.crowdTag.label = IKey.constant(crowd != null ? crowd.getDisplayName() : "(no crowd on this replay)");
    }

    /**
     * Pick the crowd to fight by picking its replay.
     *
     * <p>Only replays that actually drive a crowd are offered, and the clip's own is left out -
     * a crowd set on itself is every member attacking the member beside them.</p>
     */
    private void openEnemyPicker()
    {
        Film film = this.editor.getFilm();

        if (film == null)
        {
            return;
        }

        List<Replay> replays = film.replays.getList();
        Replay own = this.findOwnReplay();

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, IKey.constant("None"), () -> this.applyEnemy(CrowdBehaviorActionClip.ENEMY_NONE));

            for (int i = 0; i < replays.size(); i++)
            {
                final int index = i;
                Replay replay = replays.get(i);

                if (replay == own || CrowdUtils.replayCrowdTag(replay) == null)
                {
                    continue;
                }

                menu.action(Icons.CHICKEN, IKey.constant(replay.getName()), () -> this.applyEnemy(index));
            }
        });
    }

    private void applyEnemy(int index)
    {
        this.editor.editMultiple(this.clip.enemyReplay, (value) -> value.set(index));
        /* Clear the old typed tag alongside, or it would keep steering the fight the moment the
         * new pick is set back to None. */
        this.editor.editMultiple(this.clip.enemyGroup, (value) -> value.set(""));
        this.refreshEnemyLabel();
    }

    private void refreshEnemyLabel()
    {
        this.enemyReplay.label = IKey.constant(this.enemyName());
    }

    private String enemyName()
    {
        Film film = this.editor.getFilm();
        int index = this.clip.enemyReplay.get();

        if (index < 0)
        {
            /* An old film's typed tag still drives the fight, so it is shown rather than reported
             * as "None" while the crowd is plainly fighting something. */
            String group = this.clip.enemyGroup.get();

            return group == null || group.trim().isEmpty() ? "None" : group.trim();
        }

        Replay replay = CrowdUtils.getReplay(film, index);

        return replay == null ? "#" + index + " (missing)" : replay.getName();
    }

    private void openModeMenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            for (CrowdBehaviorMode mode : CrowdBehaviorMode.PRESETS)
            {
                menu.action(Icons.ALL_DIRECTIONS, IKey.constant(mode.title), () ->
                {
                    this.editor.editMultiple(this.clip.mode, (value) -> value.set(mode.ordinal()));
                    this.refreshModeLabel();
                });
            }
        });
    }

    private void applyTarget(int index)
    {
        this.editor.editMultiple(this.clip.target, (target) -> target.set(index));
        this.refreshTargetLabel();
    }

    private void refreshTargetLabel()
    {
        this.target.label = IKey.constant(this.targetName(this.clip.target.get()));
    }

    private void refreshModeLabel()
    {
        this.mode.label = IKey.constant(CrowdBehaviorMode.get(this.clip.mode.get()).title);
    }

    private String targetName(int index)
    {
        if (index == CrowdBehaviorActionClip.TARGET_NONE)
        {
            return "None";
        }

        if (index < 0)
        {
            return "(clip replay)";
        }

        Film film = this.editor.getFilm();

        if (film == null)
        {
            return "#" + index;
        }

        List<Replay> replays = film.replays.getList();

        if (index >= replays.size())
        {
            return "#" + index + " (missing)";
        }

        return replays.get(index).getName();
    }
}
