package mchorse.bbs_mod.settings.values.ui;

/**
 * The IK debug overlay's look, drawn by {@code ModelIKDebug}. The default is
 * minimal — only the goal and the pole marker, the chain wires, joints and the
 * effector left off — so the overlay reads as clean relationship handles.
 *
 * <p>Both are solid spheres: a green goal and an orange pole, the palette used by
 * quaIett's bbs-refreshed-addon (MIT). A ball reads as something to grab, where the
 * diamond and cube read as annotations on a diagram.</p>
 */
public class ValueIKDebug extends ValueModelDebug
{
    public final ValueDebugElement tip = this.element("tip", false, 0x4da3ff, 0.1F, ValueDebugElement.SHAPE_CROSS);
    /* Small by default: controller handles should remain easy to pick without
     * obscuring a compact player rig. The IK panel exposes one slider which
     * changes both together; the debug popover still permits separate sizes. */
    public final ValueDebugElement target = this.element("target", 0x38d68c, 0.08F, ValueDebugElement.SHAPE_SPHERE);
    public final ValueDebugElement pole = this.element("pole", 0xff8c26, 0.08F, ValueDebugElement.SHAPE_SPHERE);

    public ValueIKDebug(String id)
    {
        super(id, false, 0xe6ebf2, 0.05F, false, 0xe6ebf2, 0.07F);

        /* Solid rather than the shared half-transparent default: with only two markers left on
         * screen there is nothing to see through, and washing them out is what made them read as
         * debug scaffolding instead of handles. */
        this.opacity.set(1F);
    }
}
