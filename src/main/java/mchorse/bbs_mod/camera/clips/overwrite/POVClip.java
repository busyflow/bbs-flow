package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.ArrayList;
import java.util.List;

/**
 * POV Camera Clip
 *
 * Locks the camera to the first-person point-of-view of the replay that has
 * first person playback enabled (or a selected replay), with continuous
 * sub-tick interpolation for smooth motion, and captures HUD rendering data.
 */
public class POVClip extends CameraClip
{
    public final ValueString selector = new ValueString("selector", "");
    public final ValuePoint offset = new ValuePoint("offset", new Point(0, 0, 0));
    public final ValuePoint angle = new ValuePoint("angle", new Point(0, 0, 0));
    public final ValueFloat fov = new ValueFloat("fov", 70F);

    public final ValueBoolean hideActor = new ValueBoolean("hide_actor", true);
    public final ValueBoolean showHands = new ValueBoolean("show_hands", true);
    public final ValueBoolean armDrift = new ValueBoolean("arm_drift", false);
    public final ValueBoolean showHud = new ValueBoolean("show_hud", true);
    public final ValueBoolean showCrosshair = new ValueBoolean("show_crosshair", true);
    public final ValueBoolean dynamicCrosshair = new ValueBoolean("dynamic_crosshair", true);
    public final ValueBoolean showHotbar = new ValueBoolean("show_hotbar", true);
    public final ValueBoolean showHealth = new ValueBoolean("show_health", true);
    public final ValueBoolean showHunger = new ValueBoolean("show_hunger", true);
    public final ValueBoolean showExperience = new ValueBoolean("show_experience", true);
    public final ValueBoolean showArmor = new ValueBoolean("show_armor", true);

    public final ValueBoolean customStats = new ValueBoolean("custom_stats", false);
    public final ValueFloat hp = new ValueFloat("hp", 20F);
    public final ValueFloat hunger = new ValueFloat("hunger", 20F);
    public final ValueInt xpLevel = new ValueInt("xp_level", 0);
    public final ValueFloat xpProgress = new ValueFloat("xp_progress", 0F);

    public POVClip()
    {
        super();

        this.add(this.selector);
        this.add(this.offset);
        this.add(this.angle);
        this.add(this.fov);

        this.add(this.hideActor);
        this.add(this.showHands);
        this.add(this.armDrift);
        this.add(this.showHud);
        this.add(this.showCrosshair);
        this.add(this.dynamicCrosshair);
        this.add(this.showHotbar);
        this.add(this.showHealth);
        this.add(this.showHunger);
        this.add(this.showExperience);
        this.add(this.showArmor);

        this.add(this.customStats);
        this.add(this.hp);
        this.add(this.hunger);
        this.add(this.xpLevel);
        this.add(this.xpProgress);
    }

    public Replay findReplay(Film film)
    {
        if (film == null)
        {
            return null;
        }

        String target = this.selector.get();

        if (!target.isEmpty())
        {
            for (Replay replay : film.replays.getList())
            {
                if (replay.getId().equals(target))
                {
                    return replay;
                }
            }
        }

        Replay fpReplay = film.getFirstPersonReplay();

        if (fpReplay != null)
        {
            return fpReplay;
        }

        return film.replays.getList().isEmpty() ? null : film.replays.getList().get(0);
    }

    public boolean tracksReplay(Replay replay, Film film)
    {
        if (replay == null || film == null)
        {
            return false;
        }

        return this.findReplay(film) == replay;
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        if (!(context instanceof CameraClipContext cameraContext))
        {
            return;
        }

        Film film = cameraContext.film;
        Replay replay = this.findReplay(film);

        if (replay == null)
        {
            return;
        }

        float exactTick = replay.getTick(context.ticks) + context.transition;

        double x = replay.keyframes.x.interpolate(exactTick);
        double y = replay.keyframes.y.interpolate(exactTick);
        double z = replay.keyframes.z.interpolate(exactTick);

        boolean sneaking = EntityState.isOn(replay.keyframes.sneaking.interpolate(exactTick));
        boolean swimming = EntityState.isOn(replay.keyframes.state(EntityState.SWIMMING).interpolate(exactTick));
        boolean gliding = EntityState.isOn(replay.keyframes.state(EntityState.GLIDING).interpolate(exactTick));

        double eyeHeight = (gliding || swimming) ? 0.4D : (sneaking ? 1.27D : 1.62D);
        IEntity entity = cameraContext.entities.get(replay.getId());

        if (entity != null && !sneaking && !swimming && !gliding)
        {
            eyeHeight = entity.getEyeHeight();
        }

        y += eyeHeight;

        Point offset = this.offset.get();
        x += offset.x;
        y += offset.y;
        z += offset.z;

        position.point.set(x, y, z);

        float headYaw = replay.keyframes.headYaw.interpolate(exactTick).floatValue();
        float pitch = replay.keyframes.pitch.interpolate(exactTick).floatValue();
        float roll = replay.keyframes.roll.interpolate(exactTick).floatValue();

        Point angleOffset = this.angle.get();

        position.angle.yaw = headYaw + 180F + (float) angleOffset.y;
        position.angle.pitch = pitch + (float) angleOffset.x;
        position.angle.roll = roll + (float) angleOffset.z;
        position.angle.fov = this.fov.get();

        float factor = this.envelope.factorEnabled(this.duration.get(), context.relativeTick + context.transition);

        context.clipData.get("pov_hud", ArrayList::new).add(new PovHudData(this, replay, film, exactTick, factor));
    }

    public static List<PovHudData> getPovHuds(ClipContext context)
    {
        return context.clipData.get("pov_hud", ArrayList::new);
    }

    @Override
    protected Clip create()
    {
        return new POVClip();
    }
}
