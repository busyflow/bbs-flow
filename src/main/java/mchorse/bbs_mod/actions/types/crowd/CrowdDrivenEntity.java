package mchorse.bbs_mod.actions.types.crowd;

/**
 * The small amount of per-member state the crowd behaviour clip has to remember between ticks.
 *
 * <p>Mixed into {@code LivingEntity} so it lives on the member and dies with it. A table keyed by
 * entity would have to be swept, and at ten thousand members sweeping is the part that costs.</p>
 */
public interface CrowdDrivenEntity
{
    /** Claim the body yaw for the tick about to happen. */
    void bbs$driveBodyYaw();

    /**
     * Whether a claim is outstanding, taking it if so.
     *
     * <p>Taken rather than cleared, so one write buys exactly one skipped turn and a clip that
     * stops driving hands the body back without anyone having to say so.</p>
     */
    boolean bbs$takeBodyYawDrive();

    /** The last film tick this member's look was driven on, or {@link Integer#MIN_VALUE}. */
    int bbs$getCrowdLookTick();

    void bbs$setCrowdLookTick(int tick);

    /**
     * This member's number in its crowd, or {@link Integer#MIN_VALUE} before it has been read.
     *
     * <p>The number itself lives in a command tag, which means finding it costs a walk of the
     * entity's tags, a substring and a parse. It is wanted several times per member per tick -
     * to place it, to seed its jump, to pick its texture - and it never changes once spawned,
     * so it is worked out once and kept here.</p>
     */
    int bbs$getCrowdIndex();

    void bbs$setCrowdIndex(int index);
}
