package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.overwrite.POVClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;

public class UIPOVClip extends UIClip<POVClip>
{
    public UIButton selector;
    public UIPointModule point;
    public UIPointModule angle;
    public UITrackpad fov;

    public UIToggle hideActor;
    public UIToggle showHands;
    public UIToggle showHud;
    public UIToggle showCrosshair;
    public UIToggle dynamicCrosshair;
    public UIToggle showHotbar;
    public UIToggle showHealth;
    public UIToggle showHunger;
    public UIToggle showExperience;
    public UIToggle showArmor;

    public UIToggle customStats;
    public UITrackpad hp;
    public UITrackpad hunger;
    public UITrackpad xpLevel;
    public UITrackpad xpProgress;

    public UIPOVClip(POVClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.selector = new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);

        this.bind(this.selector, () ->
        {
            String target = this.clip.selector.get();
            this.selector.label = target.isEmpty() ? UIKeys.CAMERA_PANELS_TARGET_TITLE : IKey.raw(target);
        });

        this.point = this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu(), () -> this.point.fill(this.clip.offset));
        this.angle = this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_ANGLE).contextMenu(), () -> this.angle.fill(this.clip.angle));
        this.fov = this.trackpad(this.clip.fov);
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);

        this.hideActor = this.toggle(UIKeys.CAMERA_PANELS_POV_HIDE_ACTOR, this.clip.hideActor);
        this.showHands = this.toggle(UIKeys.CAMERA_PANELS_POV_SHOW_HANDS, this.clip.showHands);
        this.showHud = this.toggle(UIKeys.CAMERA_PANELS_POV_SHOW_HUD, this.clip.showHud);
        this.showCrosshair = this.toggle(UIKeys.CAMERA_PANELS_POV_CROSSHAIR, this.clip.showCrosshair);
        this.dynamicCrosshair = this.toggle(UIKeys.CAMERA_PANELS_POV_DYNAMIC_CROSSHAIR, this.clip.dynamicCrosshair);
        this.showHotbar = this.toggle(UIKeys.CAMERA_PANELS_POV_HOTBAR, this.clip.showHotbar);
        this.showHealth = this.toggle(UIKeys.CAMERA_PANELS_POV_HEALTH, this.clip.showHealth);
        this.showHunger = this.toggle(UIKeys.CAMERA_PANELS_POV_HUNGER, this.clip.showHunger);
        this.showExperience = this.toggle(UIKeys.CAMERA_PANELS_POV_EXPERIENCE, this.clip.showExperience);
        this.showArmor = this.toggle(UIKeys.CAMERA_PANELS_POV_ARMOR, this.clip.showArmor);

        this.customStats = this.toggle(UIKeys.CAMERA_PANELS_POV_CUSTOM_STATS, this.clip.customStats);
        this.hp = this.trackpad(this.clip.hp);
        this.hp.tooltip(UIKeys.CAMERA_PANELS_POV_HEALTH);
        this.hunger = this.trackpad(this.clip.hunger);
        this.hunger.tooltip(UIKeys.CAMERA_PANELS_POV_HUNGER);
        this.xpLevel = this.trackpad(this.clip.xpLevel);
        this.xpLevel.tooltip(UIKeys.CAMERA_PANELS_POV_EXPERIENCE);
        this.xpProgress = this.trackpad(this.clip.xpProgress);
        this.xpProgress.tooltip(UIKeys.CAMERA_PANELS_POV_EXPERIENCE);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector));
        this.panels.add(this.point);
        this.panels.add(this.angle);
        this.panels.add(this.fov);

        this.panels.add(this.hideActor);
        this.panels.add(this.showHands);
        this.panels.add(this.showHud);
        this.panels.add(this.showCrosshair);
        this.panels.add(this.dynamicCrosshair);
        this.panels.add(this.showHotbar);
        this.panels.add(this.showHealth);
        this.panels.add(this.showHunger);
        this.panels.add(this.showExperience);
        this.panels.add(this.showArmor);

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_POV_CUSTOM_STATS_TITLE, this.customStats, this.hp, this.hunger, this.xpLevel, this.xpProgress));
    }
}
