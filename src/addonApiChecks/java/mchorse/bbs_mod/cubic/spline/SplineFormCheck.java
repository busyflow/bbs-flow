package mchorse.bbs_mod.cubic.spline;

import mchorse.bbs_mod.forms.entities.ReplayEntity;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.clips.overwrite.SplineClip;
import mchorse.bbs_mod.camera.clips.misc.SplineClientClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.SplineFormRenderer;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.states.StatePlayer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UISplinePointsKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.utils.UISplinePointsEditor;
import mchorse.bbs_mod.ui.utils.SplineOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.AnchorRebase;

/** Real form/track/anchor/camera paths in the existing Fabric prelaunch harness, without a window. */
public final class SplineFormCheck
{
    private static int checks;

    public static void run() throws Exception
    {
        BBSMod.getForms().register(new Link("check", "spline"), SplineForm.class);
        FormUtilsClient.register(SplineForm.class, SplineFormRenderer::new);
        data();
        geometry();
        consumers();
        detach();
        if (Boolean.getBoolean("bbs.check.splineUI")) editor();
        System.out.println("SplineFormCheck: " + checks + " checks passed");
    }

    private static void data()
    {
        SplineForm form = new SplineForm();
        form.setId("path");
        check(form.points.getAllTyped().size() == 2, "Two distinct default controls");
        String id = form.points.getAllTyped().get(1).getId();
        near(form.position(id).translate.z, 2, "Path units are blocks");
        String address = FormUtils.getPropertyPath(form.points.get(id).position);
        check(address.equals("points/" + id + "/position"), "Point address uses stable ID");
        check(FormUtils.collectPropertyPaths(form).contains("curve"), "Compound curve is discoverable");
        check(!FormUtils.collectPropertyPaths(form).contains(address), "Bound coordinates are not duplicate tracks");
        form.closed.set(true);
        form.position(id).translate.set(3, 4, 5);
        BaseType saved = DataStorageUtils.readFromBytes(DataStorageUtils.writeToBytes(form.toData()));
        check(!saved.asMap().getList("points").get(1).asMap().has("position"), "Save has one coordinate store");
        SplineForm loaded = new SplineForm();
        loaded.fromData(saved);
        check(loaded.closed.get() && loaded.points.get(id) != null, "Roundtrip topology and closure");
        close(loaded.position(id).translate, new Vector3f(3, 4, 5), "Roundtrip bound coordinates");
        SplineForm copy = new SplineForm();
        copy.copy(form);
        close(copy.position(id).translate, new Vector3f(3, 4, 5), "Copy coordinates by ID");
        copy.position(id).translate.x = 99;
        near(form.position(id).translate.x, 3, "Copies do not share transforms");

        FormProperties film = new FormProperties("properties");
        KeyframeChannel<SplinePositions> channel = film.register(TrackId.property("", "curve"), KeyframeFactories.SPLINE_POINTS);
        SplinePositions a = form.curve.get().copy(), b = a.copy();
        a.point(id).translate.x = 0;
        b.point(id).translate.x = 10;
        channel.insert(0, a);
        channel.insert(10, b);
        SplinePoint added = new SplinePoint("");
        added.position.get().translate.set(7, 8, 9);
        form.points.add(0, added);
        Collections.swap(form.points.getAllTyped(), 1, 2);
        for (int tick : new int[] {5, 0, 10, 2, 5})
        {
            film.resetProperties(form);
            film.applyProperties(form, tick);
            near(form.position(id).translate.x, tick, "Seeking interpolates the same ID after reorder");
            close(form.position(added.getId()).translate, new Vector3f(7, 8, 9), "Old keys use authored defaults for new controls");
            near(form.curve.getOriginalValue().point(id).translate.x, 3, "Playback leaves authored coordinates intact");
        }
        film.resetProperties(form);
        AnimationState state = new AnimationState("");
        state.properties.fromData(film.toData());
        StatePlayer player = new StatePlayer(state);
        for (int i = 0; i < 5; i++) player.update();
        player.assignValues(form, 0);
        near(form.position(id).translate.x, 5, "State uses compound curve animation");
        player.resetValues(form);
        near(form.position(id).translate.x, 3, "Ending state restores coordinates");
        state.properties.applyProperties(form, 5, 0.5F);
        near(form.position(id).translate.x, 4, "State fade blends coordinates");
        state.properties.resetProperties(form);

        ValueGroup root = new ValueGroup("root");
        root.add(form);
        BaseType before = form.points.toData();
        form.points.getAllTyped().removeIf(point -> point.getId().equals(id));
        ValueChangeUndo undo = new ValueChangeUndo(form.points.getPath(), before, form.points.toData());
        undo.undo(root);
        check(form.points.get(id) != null, "Topology Undo restores stable ID");
        near(form.position(id).translate.x, 3, "Topology Undo restores bound value");
        undo.redo(root);
        check(form.points.get(id) == null, "Topology Redo deletes same point");
        undo.undo(root);
        film.applyProperties(form, 5);
        near(form.position(id).translate.x, 5, "Animation survives topology Undo");
        film.resetProperties(form);

        BodyPart part = new BodyPart("");
        SplineForm parent = new SplineForm();
        part.setForm(form);
        parent.parts.addBodyPart(part);
        check(FormUtils.getProperty(parent, FormUtils.getPropertyPath(form.curve)) == form.curve, "Nested curve property resolves");
    }

    private static void detach()
    {
        SplineForm source = curve(List.of(new Vector3f(), new Vector3f(4, 3, 8)));
        source.transform.get().scale.set(2);
        Map<String, IEntity> entities = Map.of("path", entity(source, 10, 2, -3));
        var replay = new mchorse.bbs_mod.film.replays.Replay("follower");
        IEntity follower = entity(new SplineForm(), 20, 5, 8);

        for (boolean spline : new boolean[] {false, true})
        {
            for (int flags = 0; flags < 8; flags++)
            {
                Anchor from = anchor(35);
                from.spline = spline;
                from.inheritPosition = (flags & 1) != 0;
                from.inheritRotation = (flags & 2) != 0;
                from.inheritScale = (flags & 4) != 0;
                from.transform.translate.set(1, -2, 3);
                from.transform.rotate.set(0.2F, -0.4F, 0.1F);
                from.transform.scale.set(1, 2, 3);
                Matrix4f before = FilmMatrices.getAnchorMatrix(entities, follower, replay, 0, 0, 0, 0, from);
                Vector3d position = new Vector3d();
                Anchor detached = AnchorRebase.detach(entities, follower, replay, 0, from, position);
                check(detached != null && !detached.hasTarget() && !detached.spline && detached.attachment.isEmpty(), "Detach clears actor, bone and spline target");
                check(detached.transform.translate.lengthSquared() == 0, "Detach stores translation in replay coordinates");
                IEntity moved = entity(new SplineForm(), position.x, position.y, position.z);
                matrix(FilmMatrices.getAnchorMatrix(entities, moved, replay, 0, 0, 0, 0, detached), before, "Detach preserves world placement with each inheritance combination");
                check(from.hasTarget(), "Detach does not mutate the sampled anchor");
            }
        }

        replay.relative.set(true);
        check(AnchorRebase.detach(entities, follower, replay, 0, anchor(35), new Vector3d()) == null, "Relative replay cannot detach");
        check(AnchorRebase.detach(entities, null, replay, 0, anchor(35), new Vector3d()) == null, "Missing entity cannot detach");
    }

    private static void geometry()
    {
        List<Vector3f> controls = List.of(new Vector3f(), new Vector3f(0.1F, 0, 0), new Vector3f(2, 4, 0), new Vector3f(6, 4, 2));
        Matrix4f affine = new Matrix4f().translation(3, 8, -2).rotateY(0.7F).scale(5, 0.5F, -2);
        SplineForm form = curve(controls);
        SplinePath path = SplinePath.create(form, affine);
        List<Vector3f> dense = SplineCurve.sample(controls.stream().map(v -> affine.transformPosition(new Vector3f(v))).toList(), 4096);
        float[] distance = SplineMath.distances(dense);
        for (int i = 0; i <= 20; i++)
        {
            float t = i / 20F;
            Vector3f expected = SplineMath.sampleDistance(dense, distance, new Vector3f(0, 0, 1), new Vector3f(0, 0, 1), distance[distance.length - 1] * t, 1).point();
            check(path.frame(100 * t, false).getTranslation(new Vector3f()).distance(expected) < 0.012F, "World arc length agrees with dense independent sampling under nonuniform/mirrored scale");
            check(path.frame(100 * t, false).isFinite(), "Frame finite along full curve");
        }
        matrix(path.frame(-200, false), path.frame(0, false), "Open start clamps");
        matrix(path.frame(200, false), path.frame(100, false), "Open end clamps");
        form.closed.set(true);
        path = SplinePath.create(form, affine);
        matrix(path.frame(-30, false), path.frame(70, false), "Closed negative progress wraps");
        matrix(path.frame(270, false), path.frame(70, false), "Closed multiple laps wrap");
        matrix(path.frame(0, false), path.frame(100, false), "Exact closed seam");
        check(path.frame(99.999F, false).equals(path.frame(0.001F, false), 0.004F), "Closed seam position and orientation continuous");
        Matrix4f remembered = path.frame(37, false);
        path.frame(80, false);
        path.frame(-20, false);
        matrix(path.frame(37, false), remembered, "Frames depend on position, never previous tick");
        SplinePath vertical = new SplinePath(List.of(new Vector3f(), new Vector3f(0, 10, 0)), false);
        check(vertical.frame(30, true).isFinite(), "Horizontal mode handles vertical tangent");
        near(vertical.frame(30, true).m11(), 1, "Horizontal mode retains world up");
        check(SplinePath.create(form, new Matrix4f().scaling(0)).frame(50, false).isFinite(), "Collapsed scale is finite");
        check(new SplinePath(List.of(new Vector3f(1, 2, 3)), false).frame(50, false).isFinite(), "Single point is a finite stationary frame");
        check(new SplinePath(List.of(), false).frame(50, false) == null, "Empty path does not invent a target");
    }

    private static void consumers()
    {
        SplineForm source = curve(List.of(new Vector3f(), new Vector3f(0, 0, 10)));
        source.transform.get().translate.set(2, 3, 4);
        source.transform.get().scale.set(2, 3, 4);
        IEntity entity = entity(source, 10, 0, 0);
        Map<String, IEntity> entities = new HashMap<>(Map.of("path", entity));
        Matrix4f matrix = FilmMatrices.getPathMatrix(entities, "path", "", 25, false, 0, 0, 0, 0);
        check(matrix != null, "Source is evaluated without render traversal");
        close(matrix.getTranslation(new Vector3f()), new Vector3f(12, 3, 14), "Source placement, form transform and progress compose once");
        matrix(FilmMatrices.getPathMatrix(entities, "path", "", 25, false, 100, 20, -30, 0), new Matrix4f(matrix).translateLocal(-100, -20, 30), "Camera origin does not change path length");
        Anchor first = anchor(25), second = anchor(75);
        Matrix4f firstFrame = FilmMatrices.getEntityMatrix(entities, 0, 0, 0, first, new Matrix4f(), 0, 0);
        Matrix4f secondFrame = FilmMatrices.getEntityMatrix(entities, 0, 0, 0, second, new Matrix4f(), 0, 0);
        near(secondFrame.m32() - firstFrame.m32(), 20, "Consumers have independent progress");
        matrix(firstFrame, matrix, "Actor and camera share path sample");
        Anchor old = new Anchor();
        old.transform.translate.set(7, 3, -2);
        Anchor kept = second.copy();
        IEntity follower = entity(new SplineForm(), 20, 5, 8);
        Matrix4f oldWorld = FilmMatrices.getAnchorMatrix(entities, follower, null, 0, 0, 0, 0, old);
        check(AnchorRebase.keepWorldTransform(entities, follower, null, 0, old, kept), "Keep-view can invert path frame");
        matrix(FilmMatrices.getAnchorMatrix(entities, follower, null, 0, 0, 0, 0, kept), oldWorld, "Retarget keeps world transform");
        Anchor blend = KeyframeFactories.ANCHOR.interpolate(first, first, second, second, Interpolations.LINEAR, 0.5F);
        near(blend.progress, 50, "Anchor progress interpolates");
        Anchor copied = new Anchor();
        copied.fromData(second.toData());
        check(copied.spline && copied.equals(second), "Path binding saves and loads");

        SplineClip camera = new SplineClip();
        camera.selector.set("path");
        camera.duration.set(10);
        camera.angle.set(new Point(10, 20, 30));
        for (int mode = 0; mode < 3; mode++)
        {
            camera.pathRotation.set(mode);
            for (int tick : new int[] {8, 0, 5, 2, 10, 5})
            {
                TrackerFrame frame = TrackerFrame.resolvePath(entities, camera, tick, 20, 10, 0, 0);
                check(frame != null, "Camera resolves direct path");
                Point offset = new Point(1, 2, -3);
                Vector3d position = frame.position(offset);
                Point solved = frame.solveOffset(position.x, position.y, position.z);
                check(Math.abs(solved.x - offset.x) + Math.abs(solved.y - offset.y) + Math.abs(solved.z - offset.z) < 1E-4, "Camera offset forward/inverse agree");
                Angle view = frame.angles(camera.angle.get());
                Angle restored = frame.angles(frame.solveAngles(view.yaw, view.pitch, view.roll));
                check(Math.abs(view.pitch - restored.pitch) + Math.abs(view.roll - restored.roll) < 0.001F, "Camera angle forward/inverse agree");
                near((float) Math.IEEEremainder(view.yaw - restored.yaw, 360), 0, "Camera yaw forward/inverse agrees");
                near((float) frame.position(new Point(0, 0, 0)).z, 4 + tick * 4, "Camera animates its own progress");
            }
        }
        SplineClip loaded = new SplineClip();
        loaded.fromData(camera.toData());
        near(loaded.progress(5), 50, "Camera progress endpoints roundtrip");
        near((float) loaded.angle.get().y, 20, "Camera angle offset roundtrip");
        SplineClientClip motion = new SplineClientClip();
        motion.copy(camera);
        motion.active.set(0b0000111);
        Position view = new Position(0, 0, 0, 15, 20);
        view.angle.fov = 83;
        CameraClipContext context = new CameraClipContext();
        context.entities = entities;
        motion.apply(context.setup(5, 5, 0, 0), view);
        near((float) view.point.z, 24, "Actual camera clip samples progress");
        near(view.angle.yaw, 15, "Position-only mask preserves own yaw");
        near(view.angle.fov, 83, "Disabled FOV mask preserves lens");
        TrackerClientClip look = new TrackerClientClip();
        entities.put("subject", entity(new SplineForm(), 30, 5, 40));
        look.selector.set("subject");
        look.lookAt.set(true);
        look.active.set(0b0111000);
        Vector3d travel = new Vector3d(view.point.x, view.point.y, view.point.z);
        look.apply(context.setup(5, 5, 0, 0), view);
        check(travel.distance(view.point.x, view.point.y, view.point.z) < 1E-6, "Look-at layer retains path camera position");
        Angle targetAngle = Angle.angle(30 - view.point.x, 5 - view.point.y, 40 - view.point.z);
        near(view.angle.yaw, targetAngle.yaw, "Look-at layer follows independently selected actor");
        near(view.angle.pitch, targetAngle.pitch, "Look-at layer pitches toward actor");
        near(view.angle.fov, 83, "Look-at layer preserves disabled FOV");
        source.curve.setRuntimeValue(source.curve.getOriginalValue().copy());
        for (Transform point : source.curve.get().values()) point.translate.x += 2;
        near(FilmMatrices.getPathMatrix(entities, "path", "", 25, false, 0, 0, 0, 0).m30(), 16, "Consumer reads animated curve before rendering source");
        source.curve.setRuntimeValue(null);
        source.transform.get().scale.set(0);
        check(FilmMatrices.getPathMatrix(entities, "path", "", 25, false, 0, 0, 0, 0).isFinite(), "Collapsed source is safe for consumers");
        source.transform.get().scale.set(1);
        check(FilmMatrices.getPathMatrix(entities, "missing", "", 25, false, 0, 0, 0, 0) == null, "Missing replay is unresolved");
        check(FilmMatrices.getPathMatrix(entities, "path", "missing", 25, false, 0, 0, 0, 0) == null, "Missing path is unresolved");
        setAnchor(entity, anchor(0));
        check(FilmMatrices.getPathMatrix(entities, "path", "", 25, false, 0, 0, 0, 0) == null, "Self cycle is unresolved");
        setAnchor(entity, new Anchor());
        SplineForm other = new SplineForm();
        entities.put("other", entity(other, 0, 0, 0));
        Anchor throughOther = anchor(0);
        throughOther.replay = "other";
        setAnchor(entity, throughOther);
        setAnchor(entities.get("other"), anchor(0));
        check(FilmMatrices.getPathMatrix(entities, "path", "", 25, false, 0, 0, 0, 0) == null, "Indirect cycle is unresolved");
        setAnchor(entity, new Anchor());
        setAnchor(entities.get("other"), new Anchor());
        BodyPart part = new BodyPart("");
        part.setForm(source);
        other.parts.addBodyPart(part);
        entities.put("nested", entity(other, 0, 0, 0));
        check(FilmMatrices.getPathMatrix(entities, "nested", FormUtils.getPath(source), 25, false, 0, 0, 0, 0) != null, "Nested independent source resolves");
    }

    private static void editor() throws Exception
    {
        mchorse.bbs_mod.BBSSettings.register(new mchorse.bbs_mod.settings.SettingsBuilder(null, "check", null));
        /* Opt-in desktop layout/input checks, not a visual game test. A hidden, context-less
         * GLFW surface supplies modifier-key state; font metrics are a stand-in. */
        if (!org.lwjgl.glfw.GLFW.glfwInit()) throw new IllegalStateException("GLFW input is unavailable");
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_VISIBLE, org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_CLIENT_API, org.lwjgl.glfw.GLFW.GLFW_NO_API);
        long handle = org.lwjgl.glfw.GLFW.glfwCreateWindow(16, 16, "Spline input checks", 0, 0);
        if (handle == 0) throw new IllegalStateException("Cannot create hidden input surface");
        var clientField = net.minecraft.client.MinecraftClient.class.getDeclaredField("instance");
        clientField.setAccessible(true);
        Object oldClient = clientField.get(null);
        var fontField = mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D.class.getDeclaredField("fontRenderer");
        fontField.setAccessible(true);
        Object oldFont = fontField.get(null);
        Object client = mchorse.bbs_mod.utils.UnsafeUtils.getUnsafe().allocateInstance(net.minecraft.client.MinecraftClient.class);
        Object window = mchorse.bbs_mod.utils.UnsafeUtils.getUnsafe().allocateInstance(net.minecraft.client.util.Window.class);
        var handleField = net.minecraft.client.util.Window.class.getDeclaredField("handle");
        handleField.setAccessible(true);
        handleField.setLong(window, handle);
        var windowField = net.minecraft.client.MinecraftClient.class.getDeclaredField("window");
        windowField.setAccessible(true);
        windowField.set(client, window);
        clientField.set(null, client);
        fontField.set(null, new mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer()
        {
            @Override public void setRenderer(net.minecraft.client.font.TextRenderer renderer) {}
            @Override public int getWidth(String text) { return text.length() * 6; }
            @Override public int getHeight() { return 7; }
        });
        try
        {
            editorWithMetrics();
        }
        finally
        {
            clientField.set(null, oldClient);
            fontField.set(null, oldFont);
            org.lwjgl.glfw.GLFW.glfwDestroyWindow(handle);
            org.lwjgl.glfw.GLFW.glfwTerminate();
        }
    }

    private static void editorWithMetrics()
    {
        SplineForm form = new SplineForm();
        String first = form.points.getAllTyped().get(0).getId(), second = form.points.getAllTyped().get(1).getId();
        KeyframeChannel<SplinePositions> channel = new KeyframeChannel<>("curve", KeyframeFactories.SPLINE_POINTS);
        SplinePositions a = form.curve.get().copy(), b = a.copy();
        b.point(first).translate.x = 10;
        b.point(second).translate.x = 20;
        channel.insert(0, a);
        channel.insert(10, b);
        UIKeyframes timeline = new UIKeyframes(null)
        {
            @Override public Float getAutoKeyframeTick() { return 5F; }
            @Override public float getTick() { return 5F; }
        };
        UIKeyframeSheet sheet = new UIKeyframeSheet(0x5599FF, channel, form.curve).form(form);
        timeline.addSheet(sheet);
        sheet.selection.add(0);
        UISplinePointsKeyframeFactory factory = new UISplinePointsKeyframeFactory(new UITrackValue<>(sheet, timeline), timeline);
        factory.pointEditor().select(first);
        factory.pointEditor().selectInViewport(second, true);
        factory.pointEditor().position.setT(Axis.X, 13, 0, 2);
        check(channel.getKeyframes().size() == 3, "Coordinate edit creates an autokey at playhead");
        near(channel.get(1).getValue().point(first).translate.x, 8, "Autokey moves selected first point by same delta");
        near(channel.get(1).getValue().point(second).translate.x, 13, "Autokey edits visible primary point");
        near(channel.get(0).getValue().point(first).translate.x, 0, "Autokey leaves old key intact");
        near(form.position(first).translate.x, 0, "Autokey leaves authored form intact");
        SplineForm runtime = new SplineForm();
        runtime.copy(form);
        check(factory.selectedPoints(runtime).size() == 2, "Viewport selection resolves runtime form copy by stable path");
        int[] notifications = new int[2];
        form.preCallback((value, flag) -> notifications[0]++);
        form.postCallback((value, flag) -> notifications[1]++);
        UISplinePointsEditor points = new UISplinePointsEditor(() -> form, id -> form.position(id), new UIDeltaPropTransform()
        {
            @Override protected void applyToSelection(java.util.function.Consumer<Transform> edit) {}
        }, Vector3f::new);
        points.refresh();
        points.insert(0, new Vector3f(1, 2, 3));
        check(notifications[0] == 1 && notifications[1] == 1, "Insertion is one before/after history transaction");
        check(form.points.getAllTyped().size() == 3 && points.pointId().equals(form.points.getAllTyped().get(1).getId()), "Insertion selects the new point");
        points.removeSelected();
        check(form.points.getAllTyped().size() == 2, "Shared delete changes topology");
        for (int i = 0; i < 40; i++) form.points.add(new SplinePoint(""));
        points.refresh();
        for (int width : new int[] {120, 180, 300})
        {
            points.xy(0, 0).w(width).resize();
            check(points.points.area.h == points.points.rowHeight() * 6, "Long lists retain six scrollable rows at width " + width);
            check(points.position.tx.area.w > 0 && points.position.tz.area.ex() <= points.area.ex(), "Coordinate fields fit narrow panel width " + width);
        }
        SplineForm closed = curve(List.of(new Vector3f(-0.5F, -0.5F, 0), new Vector3f(0.5F, -0.5F, 0), new Vector3f(0.5F, 0.5F, 0), new Vector3f(-0.5F, 0.5F, 0)));
        closed.closed.set(true);
        List<Vector3f> controls = closed.points.getAllTyped().stream().map(point -> point.position.get().translate).toList();
        SplineOverlay.CurveHit hit = SplineOverlay.findCurveHit(closed, controls, new Matrix4f(), new Area(0, 0, 400, 400), 75, 200);
        check(hit != null && hit.after() == 3, "Insertion can pick the closing segment in 3D");
    }

    private static Anchor anchor(float progress)
    {
        Anchor anchor = new Anchor();
        anchor.replay = "path";
        anchor.spline = true;
        anchor.progress = progress;
        anchor.inheritScale = false;
        return anchor;
    }

    private static SplineForm curve(List<Vector3f> points)
    {
        SplineForm form = new SplineForm();
        form.points.getAllTyped().clear();
        for (Vector3f point : points)
        {
            SplinePoint control = new SplinePoint("");
            control.position.get().translate.set(point);
            form.points.add(control);
        }
        return form;
    }

    private static IEntity entity(Form form, double x, double y, double z)
    {
        var replay = new mchorse.bbs_mod.film.replays.Replay("fixture");
        var entity = new ReplayEntity(null, replay);
        entity.setForm(form);
        entity.setPosition(x, y, z);
        entity.setPrevX(x);
        entity.setPrevY(y);
        entity.setPrevZ(z);
        return entity;
    }

    private static void setAnchor(IEntity entity, Anchor anchor)
    {
        var actor = (ReplayEntity) entity;
        actor.replay.anchor.set(anchor);
        actor.replay.anchor.setRuntimeValue(null);
    }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < 0.001F, message + ": " + actual + " != " + expected); }
    private static void close(Vector3f actual, Vector3f expected, String message) { check(actual.distance(expected) < 0.001F, message + ": " + actual + " != " + expected); }
    private static void matrix(Matrix4f actual, Matrix4f expected, String message) { check(actual.equals(expected, 0.001F), message); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
