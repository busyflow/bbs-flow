package mchorse.bbs_mod.fluid;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public abstract class PeeFluid extends FlowableFluid
{
    @Override
    public Fluid getFlowing()
    {
        return BBSMod.FLOWING_PEE;
    }

    @Override
    public Fluid getStill()
    {
        return BBSMod.STILL_PEE;
    }

    @Override
    public Item getBucketItem()
    {
        return BBSMod.PEE_BUCKET;
    }

    @Override
    protected boolean isInfinite(World world)
    {
        return false;
    }

    @Override
    protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state)
    {
        BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
        Block.dropStacks(state, world, pos, blockEntity);
    }

    @Override
    protected int getFlowSpeed(WorldView world)
    {
        return 4;
    }

    @Override
    protected int getLevelDecreasePerBlock(WorldView world)
    {
        return 1;
    }

    @Override
    public boolean matchesType(Fluid fluid)
    {
        return fluid == getStill() || fluid == getFlowing();
    }

    @Override
    public int getTickRate(WorldView world)
    {
        return 5;
    }

    @Override
    protected float getBlastResistance()
    {
        return 100.0F;
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockView world, BlockPos pos, Fluid fluid, Direction direction)
    {
        return direction == Direction.DOWN && !fluid.isIn(net.minecraft.registry.tag.FluidTags.WATER);
    }

    @Override
    protected BlockState toBlockState(FluidState state)
    {
        return BBSMod.PEE_BLOCK.getDefaultState().with(FluidBlock.LEVEL, getBlockStateLevel(state));
    }

    public static class Flowing extends PeeFluid
    {
        @Override
        protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder)
        {
            super.appendProperties(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getLevel(FluidState state)
        {
            return state.get(LEVEL);
        }

        @Override
        public boolean isStill(FluidState state)
        {
            return false;
        }
    }

    public static class Still extends PeeFluid
    {
        @Override
        public int getLevel(FluidState state)
        {
            return 8;
        }

        @Override
        public boolean isStill(FluidState state)
        {
            return true;
        }
    }
}
