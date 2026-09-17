package mchorse.bbs_mod.ui.film.clips.area;

import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * The free-hand brush that paints a {@link Crowd}'s ground.
 *
 * <p>One brush exists at a time and belongs to whichever crowd is open in the editor — arming
 * it takes over left-drag in the viewport, which is why it disarms itself the moment the crowd
 * stops being the one being edited. It paints columns, not blocks: a stroke finds the surface under the cursor
 * and stamps a disc of surface columns around it, so a hillside gets painted as ground rather
 * than as the one block the ray happened to land on.</p>
 */
public class AreaBrush
{
    /** How far above and below the brushed surface a neighbouring column may follow it. */
    private static final int SURFACE_SPAN = 8;

    /**
     * How far a column may climb above the block the stroke is on.
     *
     * <p>One, so a stroke follows a step or a slope, and no further. The search used to begin a
     * whole {@link #SURFACE_SPAN} above the stroke, which is why painting under anything - a cave,
     * a room, the underside of a roof - climbed out and painted the daylight surface instead.</p>
     */
    private static final int STEP_UP = 1;

    private static Crowd crowd;
    private static boolean erasing;
    private static boolean painting;
    private static boolean strokeErase;

    /** The column the cursor is over this frame, for the brush's own preview ring. */
    private static BlockPos hovered;
    private static int hoveredRadius;

    public static Crowd getCrowd()
    {
        return crowd;
    }

    public static boolean isArmed()
    {
        return crowd != null;
    }

    public static boolean isErasing()
    {
        return erasing;
    }

    public static boolean isPainting()
    {
        return painting;
    }

    public static BlockPos getHovered()
    {
        return hovered;
    }

    public static int getHoveredRadius()
    {
        return hoveredRadius;
    }

    public static void arm(Crowd target, boolean erase)
    {
        crowd = target;
        erasing = erase;
        painting = false;
    }

    public static void disarm()
    {
        crowd = null;
        painting = false;
        hovered = null;
    }

    /** Drop the brush when the crowd it belongs to is no longer the one being edited. */
    public static void disarmUnless(Crowd target)
    {
        if (crowd != null && crowd != target)
        {
            disarm();
        }
    }

    public static Runnable onFinishStroke;

    public static void stopPainting()
    {
        if (painting)
        {
            painting = false;

            if (onFinishStroke != null)
            {
                onFinishStroke.run();
            }
        }
    }

    /**
     * Start a stroke, if the brush is armed and the click is a paint click.
     *
     * @return whether the click was consumed — true keeps it away from form picking and the orbit
     * camera, which both also want left-drag in the viewport.
     */
    public static boolean click(UIContext context, Area area, Camera camera)
    {
        if (crowd == null || context.mouseButton > 1)
        {
            return false;
        }

        /* Right-drag erases whatever the current mode is, so correcting a stroke doesn't mean
         * going back to the panel to flip a toggle and back. */
        held(context, area, camera, context.mouseButton == 1);

        return true;
    }

    /**
     * Paint from the raw button state, once per frame, for as long as a button is held.
     *
     * <p>The viewport's click event is not a reliable place to start a stroke — it is contested by
     * the orbit camera, the gizmos and form picking, and whichever of them the editor hands the
     * press to, the brush never hears about it. Polling the button instead means the brush works
     * the same whoever else wanted that click, and a stroke is naturally continuous: it is simply
     * "the button is down and the cursor is here", every frame.</p>
     */
    public static void held(UIContext context, Area area, Camera camera, boolean right)
    {
        if (crowd == null)
        {
            return;
        }

        if (!painting)
        {
            /* First frame of the stroke: fix paint-or-erase now so it can't change halfway. */
            strokeErase = erasing || right;
            painting = true;
        }

        stamp(context, area, camera, strokeErase);
    }

    /** Track the column under the cursor so the viewport can show where a stroke would land. */
    public static void hover(UIContext context, Area area, Camera camera)
    {
        if (crowd == null)
        {
            hovered = null;

            return;
        }

        hovered = trace(context, area, camera);
        hoveredRadius = crowd.brushSize.get();
    }

    private static void stamp(UIContext context, Area area, Camera camera, boolean erase)
    {
        BlockPos hit = trace(context, area, camera);

        if (hit == null)
        {
            return;
        }

        World world = MinecraftClient.getInstance().world;
        int radius = crowd.brushSize.get();

        /* Half a block of slack, so the rim cells whose centres sit just outside the radius are
         * still painted. Testing the centre alone leaves the corners bitten off, which at the
         * sizes a brush is actually used at reads as a diamond rather than a circle. */
        float radiusSquared = (radius + 0.5F) * (radius + 0.5F);

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if ((float) (dx * dx + dz * dz) > radiusSquared)
                {
                    continue;
                }

                int x = hit.getX() + dx;
                int z = hit.getZ() + dz;

                if (erase)
                {
                    crowd.erase(x, z);

                    continue;
                }

                int y = surfaceY(world, x, z, hit.getY());

                if (y != Integer.MIN_VALUE)
                {
                    crowd.paint(x, y, z);
                }
            }
        }
    }

    /**
     * The surface of one column, at or below the block the stroke is on.
     *
     * <p>Downwards from the stroke, with only {@link #STEP_UP} of room to climb. Whatever is
     * painted is therefore whatever was pointed at: a cave floor stays the cave floor, a room's
     * floor stays its floor, and a roof is painted by pointing at the roof. Searching from above
     * the stroke is what used to send every one of those out into the daylight.</p>
     */
    private static int surfaceY(World world, int x, int z, int around)
    {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = around + STEP_UP; y >= around - SURFACE_SPAN; y--)
        {
            if (!isGround(world, pos.set(x, y, z)))
            {
                continue;
            }

            if (isClear(world, pos.set(x, y + 1, z)))
            {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    /**
     * Whether a crowd member could stand on this block.
     *
     * <p>Asked as "has it anything solid to stand on" rather than "is it air", because grass,
     * flowers and the tall stuff that comes up from bone meal are not air and were counted as
     * ground - which is why a crowd painted over a meadow stood a block up, on top of the
     * flowers, instead of in them.</p>
     */
    private static boolean isGround(World world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        return !state.isAir() && !state.getCollisionShape(world, pos).isEmpty();
    }

    /** Room to stand: air, or anything that can be walked through, which is the same test. */
    private static boolean isClear(World world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        return state.isAir() || state.getCollisionShape(world, pos).isEmpty();
    }

    /** The block the mouse points at, through the editor's own camera rather than the player's. */
    private static BlockPos trace(UIContext context, Area area, Camera camera)
    {
        World world = MinecraftClient.getInstance().world;

        if (world == null || camera == null)
        {
            return null;
        }

        Vector3f rayOffset = new Vector3f();
        Vector3f rayDirection = CameraUtils.getMouseRay(camera.projection, camera.view, context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, rayOffset);

        BlockHitResult result = RayTracing.rayTrace(
            world,
            RayTracing.fromVector3d(new Vector3d(camera.position).add(rayOffset.x, rayOffset.y, rayOffset.z)),
            RayTracing.fromVector3f(rayDirection),
            512F
        );

        return result.getType() == HitResult.Type.MISS ? null : result.getBlockPos();
    }
}
