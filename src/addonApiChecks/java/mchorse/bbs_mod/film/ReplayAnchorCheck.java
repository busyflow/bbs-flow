package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.entities.ReplayEntity;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.joml.Matrix4f;
import java.util.Map;

/** Replay ownership, legacy files and independent sampling without a game window. */
public class ReplayAnchorCheck
{
    private static int checks;

    public static void run()
    {
        Anchor a = new Anchor();
        a.transform.translate.x = 2;
        Anchor b = new Anchor();
        b.transform.translate.x = 8;
        KeyframeChannel<Anchor> channel = new KeyframeChannel<>("anchor", KeyframeFactories.ANCHOR);
        channel.insert(0, a);
        channel.insert(10, b);
        var form = new SplineForm();
        MapType oldForm = form.toData().asMap();
        oldForm.put("anchor", a.toData());

        for (boolean legacyMap : new boolean[] {false, true})
        {
            MapType data = new MapType();
            data.put("form", oldForm.copy());
            FormProperties properties = new FormProperties("properties");
            properties.put(TrackId.property("", "anchor"), channel);
            MapType oldProperties = new MapType();
            oldProperties.put("anchor", channel.toData());
            data.put("properties", legacyMap ? oldProperties : properties.toData());
            Replay replay = new Replay("actor");
            replay.fromData(data);
            check(replay.anchor.get().equals(a), "Static anchor migrated");
            check(replay.keyframes.anchor.getKeyframes().size() == 2, "Both track formats migrate");
            check(!replay.properties.has(TrackId.property("", "anchor")), "No old track remains");
            check(replay.form.get().get("anchor") == null, "Form no longer owns anchor");
            MapType saved = replay.toData().asMap();
            Replay loaded = new Replay("actor");
            loaded.fromData(saved);
            check(loaded.toData().equals(saved), "New format round trip is stable");

            replay.form.set(new SplineForm());
            check(replay.anchor.get().equals(a) && replay.keyframes.anchor.getKeyframes().size() == 2, "Changing form retains binding and keys");
            ReplayEntity first = new ReplayEntity(null, replay);
            replay.anchor.setRuntimeValue(replay.evaluateAnchor(0));
            Anchor sampledValue = replay.evaluateAnchor(10);
            check(sampledValue.transform.translate.x == 8 && replay.anchor.get().transform.translate.x == 2, "Sampling returns a value without changing current replay state");
            sampledValue.transform.translate.x = 99;
            check(replay.evaluateAnchor(10).transform.translate.x == 8, "Sampling returns an independent value");
            Replay sampledReplay = new Replay(replay.getId());
            sampledReplay.copy(replay);
            ReplayEntity second = new ReplayEntity(null, sampledReplay);
            replay.keyframes.apply(10, second);
            check(FilmMatrices.getAnchor(first).transform.translate.x == 2 && FilmMatrices.getAnchor(second).transform.translate.x == 8, "Scratch playback writes only its sampling replay");
            check(FilmMatrices.getAnchor(first) == replay.anchor.get(), "Scene reads the replay's single runtime value");
            check(replay.anchor.getOriginalValue().equals(a), "Evaluation retains authored default");
            check(FilmMatrices.getGizmoAnchorCompositeMatrix(Map.of(), first, replay, 0, 0, 0, 0).m30() == 2, "Anchor gizmo works without a form");

            data.put("anchor", b.toData());
            MapType newKeys = new MapType();
            newKeys.put("anchor", new KeyframeChannel<>("anchor", KeyframeFactories.ANCHOR).toData());
            data.put("keyframes", newKeys);
            Replay explicit = new Replay("explicit");
            explicit.fromData(data);
            check(explicit.anchor.get().equals(b) && explicit.keyframes.anchor.isEmpty(), "Explicit new data wins over legacy fields");
        }

        Replay parent = new Replay("parent");
        parent.anchor.set(a);
        Replay child = new Replay("child");
        Anchor attached = new Anchor();
        attached.replay = "parent";
        child.anchor.set(attached);
        ReplayEntity parentEntity = new ReplayEntity(null, parent);
        ReplayEntity childEntity = new ReplayEntity(null, child);
        Matrix4f world = FilmMatrices.getGizmoAnchorCompositeMatrix(Map.of("parent", parentEntity), childEntity, child, 0, 0, 0, 0);
        check(world.m30() == 2, "Anchor chains work through a parent without a form");
        System.out.println("ReplayAnchorCheck: " + checks + " checks passed");
    }

    private static void check(boolean result, String label)
    {
        checks++;
        if (!result) throw new AssertionError(label);
    }
}
