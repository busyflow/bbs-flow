package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EntityView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Stops crowd members from scanning the entities they are moving through.
 *
 * <p>Every moving entity asks the world which entities its path overlaps, and the answer is
 * worked out by walking every entity in the sections the path touches. A villager cannot be
 * collided with in the first place, so the answer for a crowd member is always an empty list -
 * but the walk still happens, and in a crowd packed tightly enough for a shot it walks hundreds
 * of neighbours per member per tick. That is the last quadratic cost in crowd movement, and at
 * five figures it is what holds the server below 20 TPS.</p>
 *
 * <p>Only crowd members skip it, and only for the entities they would collide with - blocks are
 * still collided with normally, so they keep walking on the ground and around walls. Members are
 * kept apart by the behaviour clip's own separation steering instead, which reads a bucketed
 * grid and costs a fixed amount per member.</p>
 *
 * <p>This is the interface that declares the method rather than the world classes that inherit
 * it, which is where it can actually be found.</p>
 */
@Mixin(EntityView.class)
public interface EntityViewCrowdCollisionMixin
{
    @Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
    private void bbs$skipCrowdEntityCollisions(@Nullable Entity entity, Box box, CallbackInfoReturnable<List<VoxelShape>> info)
    {
        if (entity != null && entity.getCommandTags().contains(CrowdUtils.INTERNAL_TAG))
        {
            info.setReturnValue(List.of());
        }
    }
}
