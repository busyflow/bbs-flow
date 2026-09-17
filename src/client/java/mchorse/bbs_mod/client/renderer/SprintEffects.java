package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The block scuff under a sprinting replay.
 *
 * <p>Vanilla spawns these from {@code Entity#move}, and an actor never moves: the editor's actors
 * are stubs whose position is written straight from the keyframes each tick, so the code that would
 * have noticed the sprint never runs. The sprinting flag is set and everything else about it looks
 * right, which is why the particles were the one part that never appeared.</p>
 *
 * <p>Sits beside {@link ItemUseEffects} for the same reason that class exists - an effect vanilla
 * would have produced from movement the actor never performs, so the film has to produce it.</p>
 */
public class SprintEffects
{
    /** Below this, the actor is sprinting on the spot, and vanilla would not scuff either. */
    private static final double MOVED = 0.0025D;

    public static void tick(IEntity entity, int ticks)
    {
        if (!BBSSettings.sprintParticles.get() || entity == null)
        {
            return;
        }

        if (!entity.isSprinting() || !entity.isOnGround())
        {
            return;
        }

        World world = entity.getWorld();

        if (world == null || !world.isClient)
        {
            return;
        }

        /* Taken from the step just travelled rather than from the velocity: a stub is placed, not
         * moved, so it has none. The same source the limb animation and view bob already use. */
        double dx = entity.getX() - entity.getPrevX();
        double dz = entity.getZ() - entity.getPrevZ();

        if (dx * dx + dz * dz < MOVED)
        {
            return;
        }

        BlockPos below = BlockPos.ofFloored(entity.getX(), entity.getY() - 0.2D, entity.getZ());
        BlockState state = world.getBlockState(below);

        if (state.getRenderType() == BlockRenderType.INVISIBLE)
        {
            return;
        }

        double spread = 0.6D;

        world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
            entity.getX() + (world.random.nextDouble() - 0.5D) * spread,
            entity.getY() + 0.1D,
            entity.getZ() + (world.random.nextDouble() - 0.5D) * spread,
            dx * -4.0D, 1.5D, dz * -4.0D);
    }
}
