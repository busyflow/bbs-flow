package mchorse.bbs_mod.actions.crowd;

/**
 * Backward-compatible payload stored in the existing string keyframe channel.
 * Plain replay IDs remain valid and mean that all four look axes are enabled.
 */
public record CrowdLookTarget(String replayId, boolean yaw, boolean pitch, boolean bodyYaw, boolean headYaw)
{
    private static final String MARKER = "\u001fcrowd-look:";
    private static final int ALL = 15;

    public static CrowdLookTarget parse(String value)
    {
        String safe = value == null ? "" : value;
        int marker = safe.lastIndexOf(MARKER);

        if (marker < 0)
        {
            return new CrowdLookTarget(safe, true, true, true, true);
        }

        int flags;

        try
        {
            flags = Integer.parseInt(safe.substring(marker + MARKER.length()));
        }
        catch (NumberFormatException exception)
        {
            return new CrowdLookTarget(safe, true, true, true, true);
        }

        return new CrowdLookTarget(
            safe.substring(0, marker),
            (flags & 1) != 0,
            (flags & 2) != 0,
            (flags & 4) != 0,
            (flags & 8) != 0
        );
    }

    public String encode()
    {
        int flags = (this.yaw ? 1 : 0)
            | (this.pitch ? 2 : 0)
            | (this.bodyYaw ? 4 : 0)
            | (this.headYaw ? 8 : 0);

        return flags == ALL ? this.replayId : this.replayId + MARKER + flags;
    }
}
