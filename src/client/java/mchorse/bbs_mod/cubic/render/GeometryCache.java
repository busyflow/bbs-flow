package mchorse.bbs_mod.cubic.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pose-keyed GPU buffers of one model, reused between draws.
 *
 * <p>What a model computes on the CPU every pose (the welded band, the shape-keyed groups) is a
 * pure function of a key — the pose and whatever else shapes the vertices — and is computed in the
 * model's root frame, so the same buffer serves every pass of a frame (world, stencil, Iris shadow)
 * and every frame nothing moved in, drawn with the pass's own model-view.</p>
 *
 * <p>Several entries, not one: the model instance is shared by every actor using the model, so a
 * frame draws as many poses as there are actors, and a fresh pose takes the entry least recently
 * used rather than the first one — otherwise two still actors would overwrite each other's buffer
 * and miss every frame. An entry lent to the translucent queue keeps its buffer until the
 * end-of-frame flush, so it is never rebuilt in the frame it was lent in.</p>
 */
public class GeometryCache<E extends GeometryCache.Entry>
{
    /** Entries kept for poses drawn in different frames; past it the least recently used one is rebuilt. */
    private static final int MAX_ENTRIES = 16;

    private final Supplier<E> factory;
    private final List<E> entries = new ArrayList<>();

    public static abstract class Entry
    {
        /** The bake this buffer holds; {@code valid} false means nothing usable. */
        public long key;
        public boolean valid;

        long usedEpoch = -1L;
        long lentEpoch = -1L;

        /** The translucent queue holds this buffer until the end of the frame. */
        public void lend(long epoch)
        {
            this.lentEpoch = epoch;
        }

        protected abstract void close();
    }

    public GeometryCache(Supplier<E> factory)
    {
        this.factory = factory;
    }

    public E find(long key, long epoch)
    {
        for (E entry : this.entries)
        {
            if (entry.valid && entry.key == key)
            {
                entry.usedEpoch = epoch;

                return entry;
            }
        }

        return null;
    }

    /**
     * An entry to bake {@code key} into: a free one, else a new one while every other was drawn this
     * frame (another actor's pose), else the least recently used. Never one lent out this frame — when
     * every entry is, a new one is added past the cap.
     */
    public E acquire(long key, long epoch)
    {
        E pick = null;

        for (E entry : this.entries)
        {
            if (entry.lentEpoch == epoch)
            {
                continue;
            }

            if (!entry.valid)
            {
                pick = entry;

                break;
            }

            if (pick == null || entry.usedEpoch < pick.usedEpoch)
            {
                pick = entry;
            }
        }

        if (pick == null || (pick.valid && pick.usedEpoch == epoch && this.entries.size() < MAX_ENTRIES))
        {
            pick = this.factory.get();
            this.entries.add(pick);
        }

        pick.key = key;
        pick.valid = true;
        pick.usedEpoch = epoch;

        return pick;
    }

    /** Forget every bake — buffers stay allocated for reuse, since the queue may still hold one. */
    public void invalidate()
    {
        for (E entry : this.entries)
        {
            entry.valid = false;
        }
    }

    public void delete()
    {
        for (E entry : this.entries)
        {
            entry.close();
        }

        this.entries.clear();
    }
}
