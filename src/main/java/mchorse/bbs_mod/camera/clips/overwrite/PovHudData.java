package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;

public class PovHudData
{
    public final POVClip clip;
    public final Replay replay;
    public final Film film;
    public final float exactTick;
    public final float factor;

    public PovHudData(POVClip clip, Replay replay, Film film, float exactTick, float factor)
    {
        this.clip = clip;
        this.replay = replay;
        this.film = film;
        this.exactTick = exactTick;
        this.factor = factor;
    }
}
