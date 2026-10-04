package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.film.replays.Replay;
import net.minecraft.world.World;

/** A scene entity linked to the replay it represents. Placement belongs to the replay. */
public class ReplayEntity extends StubEntity
{
    public final Replay replay;

    public ReplayEntity(World world, Replay replay)
    {
        super(world);
        this.replay = replay;
    }
}
