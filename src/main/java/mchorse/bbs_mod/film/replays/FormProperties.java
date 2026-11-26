package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.api.FormPropertyAliases;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviours;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mchorse.bbs_mod.film.replays.tracks.SolverBindings;
import mchorse.bbs_mod.film.replays.tracks.compatibility.LegacyTrackCompatibility;
import mchorse.bbs_mod.film.replays.tracks.compatibility.LegacySplineValues;

/**
 * Every track of one replay (or one animation state): a channel of keyframes per address.
 *
 * <p>The address is a {@link TrackId}, not a string — what a track animates is decided once, when
 * the track is made or read, instead of being re-derived from its id by every piece of code that
 * touches it.</p>
 */
public class FormProperties extends ValueGroup
{
    /** Id of the form property holding the whole pose — the track a form's bone tracks hang under. */
    public static final String POSE_PROPERTY = "pose";

    /**
     * Saved-data key of the track list. Its presence is what tells the new format from the old one,
     * which was a map of channel id to channel.
     */
    private static final String TRACKS = "tracks";

    public final Map<TrackId, KeyframeChannel> tracks = new LinkedHashMap<>();
    private final Map<Form, Set<TrackId>> appliedTracks = new java.util.WeakHashMap<>();
    private final Map<Form, Integer> appliedVersions = new java.util.WeakHashMap<>();

    private int tracksVersion;
    private int compiledVersion = -1;
    private List<Map.Entry<TrackId, KeyframeChannel>> compiledNonBone = java.util.Collections.emptyList();
    private List<Map.Entry<TrackId, KeyframeChannel>> compiledBone = java.util.Collections.emptyList();
    private List<String> compiledBoneResetPaths = java.util.Collections.emptyList();

    /**
     * Tracks that were read but could not be understood — an unknown kind, or a channel whose
     * value factory this build does not have. Both mean an addon that is not loaded.
     *
     * <p>They are kept verbatim and written back out on save. Dropping them was worse than it
     * looked: opening a film once without the addon and saving it took the animation with it,
     * and nothing said so.</p>
     */
    private final List<MapType> foreignTracks = new ArrayList<>();

    public FormProperties(String id)
    {
        super(id);
    }

    public void shift(float tick)
    {
        for (KeyframeChannel<?> value : this.tracks.values())
        {
            for (Keyframe<?> keyframe : value.getKeyframes())
            {
                keyframe.setTick(keyframe.getTick() + tick);
            }
        }
    }

    /* Access */

    public KeyframeChannel get(TrackId track)
    {
        return track == null ? null : this.tracks.get(track);
    }

    public boolean has(TrackId track)
    {
        return track != null && this.tracks.containsKey(track);
    }

    /**
     * The track at this address, made if it isn't there yet.
     *
     * @param root  form the addresses are relative to; only a {@link TrackKind#PROPERTY} track needs
     *              it, since a plain property keys whatever its own value keys
     */
    public KeyframeChannel getOrCreate(Form root, TrackId track)
    {
        if (track == null)
        {
            return null;
        }

        KeyframeChannel channel = this.tracks.get(track);

        if (channel != null)
        {
            return channel;
        }

        if (track.is(TrackKind.PROPERTY))
        {
            BaseValue property = FormUtils.getProperty(root, track.toKey());

            return property == null ? null : this.create(property);
        }

        return this.register(track, TrackBehaviours.factory(track));
    }

    /** Every kind but {@link TrackKind#PROPERTY}, whose factory has to be looked up on the form. */
    public KeyframeChannel getOrCreate(TrackId track)
    {
        return this.getOrCreate(null, track);
    }

    public KeyframeChannel getOrCreate(Form root, String key)
    {
        return this.getOrCreate(root, TrackId.parse(key));
    }

    /** Make the track of a form property, keyed by the path the property sits at. */
    public KeyframeChannel create(BaseValue property)
    {
        if (property instanceof BaseKeyframeFactoryValue<?> keyframeFactoryValue && keyframeFactoryValue.isAnimatable())
        {
            TrackId track = TrackId.parse(FormUtils.getPropertyPath(property));

            if (track == null)
            {
                return null;
            }

            return this.register(track, keyframeFactoryValue.getFactory());
        }

        return null;
    }

    public KeyframeChannel register(TrackId track, IKeyframeFactory factory)
    {
        if (track == null || factory == null)
        {
            return null;
        }

        KeyframeChannel channel = this.tracks.get(track);

        if (channel == null)
        {
            channel = new KeyframeChannel(track.toKey(), factory);

            this.put(track, channel);
        }

        return channel;
    }

    /** Take a channel as this track, replacing whatever stood there. */
    public void put(TrackId track, KeyframeChannel channel)
    {
        if (track == null || channel == null)
        {
            return;
        }

        KeyframeChannel previous = this.tracks.put(track, channel);

        if (previous != null)
        {
            this.remove(previous);
        }

        this.add(channel);
        this.tracksVersion += 1;
    }

    public void remove(TrackId track)
    {
        KeyframeChannel channel = this.tracks.remove(track);

        if (channel != null)
        {
            this.remove(channel);
            this.tracksVersion += 1;
        }
    }

    /* Playback */

    public void applyProperties(Form form, float tick)
    {
        this.applyProperties(form, tick, 1F);
    }

    public void applyProperties(Form form, float tick, float blend)
    {
        this.apply(TrackContext.of(form), tick, blend);
    }

    /**
     * Lay every track over the form at the given tick.
     *
     * <p>Bone tracks go last, and behind a reset: a form whose pose is driven by bone tracks alone
     * gets its runtime pose dropped first, so the bones layer over the form's own pose rather than
     * over last frame's result. A form that also has a whole-pose track keeps it — that track writes
     * the runtime pose the bones add onto.</p>
     */
    private void ensureCompiled()
    {
        if (this.compiledVersion == this.tracksVersion)
        {
            return;
        }

        this.compiledVersion = this.tracksVersion;

        if (this.tracks.isEmpty())
        {
            this.compiledNonBone = java.util.Collections.emptyList();
            this.compiledBone = java.util.Collections.emptyList();
            this.compiledBoneResetPaths = java.util.Collections.emptyList();
            return;
        }

        List<Map.Entry<TrackId, KeyframeChannel>> nonBone = new ArrayList<>();
        List<Map.Entry<TrackId, KeyframeChannel>> bone = new ArrayList<>();
        Set<String> boneFormPaths = new java.util.LinkedHashSet<>();

        List<Map.Entry<TrackId, KeyframeChannel>> ordered = new ArrayList<>(this.tracks.entrySet());
        ordered.sort(java.util.Comparator.comparingInt(entry ->
            LegacyTrackCompatibility.order(entry.getKey())));

        for (Map.Entry<TrackId, KeyframeChannel> entry : ordered)
        {
            if (entry.getKey().is(TrackKind.BONE))
            {
                bone.add(entry);
                String formPath = entry.getKey().formPath();

                if (!this.has(TrackId.property(formPath, POSE_PROPERTY)))
                {
                    boneFormPaths.add(formPath);
                }
            }
            else
            {
                nonBone.add(entry);
            }
        }

        this.compiledNonBone = nonBone;
        this.compiledBone = bone;
        this.compiledBoneResetPaths = boneFormPaths.isEmpty() ? java.util.Collections.emptyList() : new ArrayList<>(boneFormPaths);
    }

    public void apply(TrackContext context, float tick, float blend)
    {
        Form root = context.root();

        if (root == null)
        {
            return;
        }

        Integer lastVersion = this.appliedVersions.get(root);
        boolean tracksUnchanged = lastVersion != null && lastVersion == this.tracksVersion;

        /* Fast path when no tracks exist and nothing previously applied needs resetting. */
        if (this.tracks.isEmpty() && (tracksUnchanged || !this.appliedTracks.containsKey(root)))
        {
            this.appliedVersions.put(root, this.tracksVersion);
            SolverBindings.apply(context);
            return;
        }

        this.ensureCompiled();

        float clampedBlend = MathUtils.clamp(blend, 0F, 1F);
        Set<TrackId> affected;

        if (tracksUnchanged)
        {
            affected = this.tracks.keySet();
        }
        else
        {
            Set<TrackId> previous = this.appliedTracks.put(root, new HashSet<>(this.tracks.keySet()));
            this.appliedVersions.put(root, this.tracksVersion);

            if (previous != null)
            {
                affected = new HashSet<>(this.tracks.keySet());
                affected.addAll(previous);
            }
            else
            {
                affected = this.tracks.keySet();
            }

            if (previous != null)
            {
                for (TrackId track : previous)
                {
                    if (!this.tracks.containsKey(track) && TrackBehaviours.of(track) != null)
                    {
                        TrackBehaviours.of(track).reset(root, track);
                    }
                }
            }
        }

        var legacySplines = LegacySplineValues.begin(root, affected);

        for (int i = 0, size = this.compiledNonBone.size(); i < size; i++)
        {
            Map.Entry<TrackId, KeyframeChannel> entry = this.compiledNonBone.get(i);
            apply(context, entry.getKey(), entry.getValue(), tick, clampedBlend);
        }

        for (int i = 0, size = this.compiledBoneResetPaths.size(); i < size; i++)
        {
            if (FormUtils.getForm(root, this.compiledBoneResetPaths.get(i)) instanceof IPosedForm posedForm)
            {
                posedForm.getPose().setRuntimeValue(null);
            }
        }

        for (int i = 0, size = this.compiledBone.size(); i < size; i++)
        {
            Map.Entry<TrackId, KeyframeChannel> entry = this.compiledBone.get(i);
            apply(context, entry.getKey(), entry.getValue(), tick, clampedBlend);
        }

        LegacySplineValues.finish(legacySplines);
        SolverBindings.apply(context);
    }

    private static void apply(TrackContext context, TrackId track, KeyframeChannel channel, float tick, float blend)
    {
        TrackBehaviour behaviour = TrackBehaviours.of(track);

        if (behaviour != null)
        {
            behaviour.apply(context, track, channel, tick, blend);
        }
    }

    /** Let go of every track, so the form shows what it shows on its own again. */
    public void resetProperties(Form form)
    {
        if (form == null)
        {
            return;
        }

        Set<TrackId> reset = new HashSet<>(this.tracks.keySet());
        Set<TrackId> previous = this.appliedTracks.remove(form);
        this.appliedVersions.remove(form);
        if (previous != null) reset.addAll(previous);
        var legacySplines = LegacySplineValues.begin(form, reset);
        for (TrackId track : reset)
        {
            TrackBehaviour behaviour = TrackBehaviours.of(track);

            if (behaviour != null)
            {
                behaviour.reset(form, track);
            }
        }
        LegacySplineValues.finish(legacySplines);
    }

    public void cleanUp()
    {
        Iterator<Map.Entry<TrackId, KeyframeChannel>> it = this.tracks.entrySet().iterator();

        while (it.hasNext())
        {
            KeyframeChannel next = it.next().getValue();

            if (next.isEmpty())
            {
                it.remove();
                this.remove(next);
                this.tracksVersion += 1;
            }
        }
    }

    /* Serialisation */

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (Map.Entry<TrackId, KeyframeChannel> entry : this.tracks.entrySet())
        {
            KeyframeChannel channel = entry.getValue();

            if (channel.isEmpty())
            {
                continue;
            }

            TrackId track = entry.getKey();
            MapType data = new MapType();

            data.putString("kind", track.kind().key);

            if (!track.formPath().isEmpty())
            {
                data.putString("form", track.formPath());
            }

            if (!track.subject().isEmpty())
            {
                data.putString("subject", track.subject());
            }

            if (!track.property().isEmpty())
            {
                data.putString("prop", track.property());
            }

            data.put("channel", channel.toData());
            list.add(data);
        }

        for (MapType foreign : this.foreignTracks)
        {
            list.add(foreign.copy());
        }

        MapType map = new MapType();

        map.put(TRACKS, list);

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.removeAll();
        this.tracks.clear();
        this.foreignTracks.clear();
        this.tracksVersion += 1;

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        if (map.has(TRACKS) && map.get(TRACKS).isList())
        {
            this.readTracks(map.getList(TRACKS));
        }
        else
        {
            this.readLegacy(map);
        }
    }

    private void readTracks(ListType list)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (!list.get(i).isMap())
            {
                continue;
            }

            MapType data = list.get(i).asMap();
            TrackKind kind = TrackKind.byKey(data.getString("kind"));

            if (kind == null)
            {
                this.foreignTracks.add((MapType) data.copy());

                continue;
            }

            TrackId original = new TrackId(kind, data.getString("form"), data.getString("subject"), data.getString("prop"));
            TrackId track = FormPropertyAliases.resolve(original);
            if (!track.equals(original) && hasCanonicalTrack(list, track)) continue;
            KeyframeChannel channel = new KeyframeChannel(track.toKey(), null);

            channel.fromData(data.getMap("channel"));

            /* The factory is written next to the keyframes; a channel saved with one this build no
             * longer knows can't be read at all, so the track is kept as data rather than as a
             * half-alive channel. */
            if (channel.getFactory() == null)
            {
                MapType foreign = (MapType) data.copy();
                if (!track.equals(original)) foreign.putString("subject", track.subject());
                this.foreignTracks.add(foreign);

                continue;
            }

            this.put(track, channel);
        }
    }

    private static boolean hasCanonicalTrack(ListType list, TrackId track)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (!list.get(i).isMap()) continue;
            MapType data = list.get(i).asMap();
            if (track.kind().key.equals(data.getString("kind")) && track.formPath().equals(data.getString("form"))
                && track.subject().equals(data.getString("subject")) && track.property().equals(data.getString("prop"))) return true;
        }
        return false;
    }

    /** The saved shape of one track, for a channel that could not be turned into one. */
    private static MapType toForeignTrack(TrackId track, MapType channel)
    {
        MapType data = new MapType();

        data.putString("kind", track.kind().key);

        if (!track.formPath().isEmpty())
        {
            data.putString("form", track.formPath());
        }

        if (!track.subject().isEmpty())
        {
            data.putString("subject", track.subject());
        }

        if (!track.property().isEmpty())
        {
            data.putString("prop", track.property());
        }

        data.put("channel", channel.copy());

        return data;
    }

    /**
     * Read the pre-track format, where the map key was the channel id and the kind of track had to be
     * parsed back out of it. Kept for every film saved before the kind became part of the data.
     */
    private void readLegacy(MapType map)
    {
        for (String key : map.keys())
        {
            MapType mapType = map.getMap(key);

            if (mapType.isEmpty())
            {
                continue;
            }

            TrackId original = TrackId.parse(key);
            TrackId track = FormPropertyAliases.resolve(original);
            if (track != null && !track.equals(original) && map.has(track.toKey())) continue;

            if (track == null)
            {
                continue;
            }

            KeyframeChannel channel = new KeyframeChannel(track.toKey(), null);

            channel.fromData(mapType);

            /* Patch 1.1.1 changes to lighting property */
            if (key.endsWith("lighting") && channel.getFactory() == KeyframeFactories.BOOLEAN)
            {
                KeyframeChannel newChannel = new KeyframeChannel(track.toKey(), KeyframeFactories.FLOAT);

                for (Object keyframe : channel.getKeyframes())
                {
                    Keyframe kf = (Keyframe) keyframe;
                    Boolean v = (Boolean) kf.getValue();

                    newChannel.insert(kf.getTick(), v ? 1F : 0F);
                }

                channel = newChannel;
            }

            /* Convert transform to pose_transform for bone tracks */
            if (channel.getFactory() == KeyframeFactories.TRANSFORM && track.is(TrackKind.BONE))
            {
                KeyframeChannel newChannel = new KeyframeChannel(track.toKey(), KeyframeFactories.POSE_TRANSFORM);

                for (Object o : channel.getKeyframes())
                {
                    Keyframe kf = (Keyframe) o;
                    Object value = kf.getValue();
                    PoseTransform newValue = new PoseTransform();

                    if (value instanceof Transform)
                    {
                        newValue.copy((Transform) value);
                    }

                    Keyframe newKf = new Keyframe(kf.getId(), KeyframeFactories.POSE_TRANSFORM, kf.getTick(), newValue);

                    newKf.getInterpolation().copy(kf.getInterpolation());
                    newChannel.add(newKf);
                }

                newChannel.sort();

                channel = newChannel;
            }

            if (channel.getFactory() == null)
            {
                /* The legacy format has no place to keep an unreadable channel, so the track
                 * is written out in the current one instead — the address parsed fine, it is
                 * only the value type that this build has no factory for. */
                this.foreignTracks.add(toForeignTrack(track, mapType));

                continue;
            }

            this.put(track, channel);
        }
    }
}
