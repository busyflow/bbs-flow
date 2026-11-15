package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.KeyframeType;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.ui.utils.Marquee;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

public class KeyframeSelectionDragTest
{
    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static Unsafe getUnsafe() throws Exception
    {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    public static void main(String[] args) throws Exception
    {
        if (BBSSettings.timelineSectionSummary == null)
        {
            BBSSettings.timelineSectionSummary = new ValueBoolean("timeline_section_summary", true);
        }
        if (BBSSettings.editorLayoutSettings == null)
        {
            BBSSettings.editorLayoutSettings = new ValueEditorLayout("editor_layout_settings");
        }
        if (BBSSettings.editorSnapToTicks == null)
        {
            BBSSettings.editorSnapToTicks = new ValueBoolean("editor_snap_to_ticks", true);
        }
        KeyframeFactories.FACTORIES.putIfAbsent("boolean", KeyframeFactories.BOOLEAN);
        KeyframeFactories.FACTORIES.putIfAbsent("double", KeyframeFactories.DOUBLE);

        testKeyframeSelectionUsesIdentityBeforeEquals();
        testDopeSheetPrefersSelectedKeyframeOverCloserUnselectedNeighbor();
        testDopeSheetPrefersSelectedSummaryMarkOverCloserUnselectedMark();
        testGraphPrefersSelectedKeyframeOverCloserUnselectedNeighbor();
        testClickAndDragOnMultiSelectionKeepsAndMovesAllSelectedKeyframes();
        System.out.println("KeyframeSelectionDragTest: all checks passed!");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void testKeyframeSelectionUsesIdentityBeforeEquals()
    {
        KeyframeChannel<Boolean> channel = new KeyframeChannel<>("paused", KeyframeFactories.BOOLEAN);
        int i0 = channel.insert(10F, false);
        int i1 = channel.insert(20F, false);
        Keyframe first = channel.get(i0);
        Keyframe second = channel.get(i1);
        second.setTick(10F);

        check(first != second, "Two distinct Keyframe objects expected");
        check(first.equals(second), "Duplicate tick+value boolean keyframes compare equal by value");

        KeyframeSelection selection = new KeyframeSelection(channel);
        selection.add(second);

        check(!selection.has(i0), "Index 0 must not be selected");
        check(selection.has(i1), "Index 1 must be selected");
        check(!selection.has(first), "Identity check must not treat first as selected just because it equals second");
        check(selection.has(second), "Identity check must find second selected");
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static UIKeyframes createKeyframes() throws Exception
    {
        UIKeyframes keyframes = (UIKeyframes) getUnsafe().allocateInstance(UIKeyframes.class);
        Area area = new Area();
        area.set(0, 0, 400, 200);
        setField(UIElement.class, keyframes, "area", area);
        setField(UITimelineCanvas.class, keyframes, "xAxis", new Scale(area, ScrollDirection.HORIZONTAL));
        setField(UITimelineCanvas.class, keyframes, "marquee", new Marquee());
        setField(UIKeyframes.class, keyframes, "dragging", -1);
        setField(UIKeyframes.class, keyframes, "keyframeDoubleClick", new DoubleClick<Keyframe>(true));
        setField(UIKeyframes.class, keyframes, "transformKeys", new IdentityHashMap<>());
        setField(UIKeyframes.class, keyframes, "graphArea", new Area());
        setField(UIKeyframes.class, keyframes, "topMargin", IUIKeyframeGraph.TOP_MARGIN);
        setField(UIKeyframes.class, keyframes, "sheets", new ArrayList<>());
        setField(UIKeyframes.class, keyframes, "graphTracks", new ArrayList<>());
        setField(UIKeyframes.class, keyframes, "valueSelection", new HashMap<>());
        setField(UIKeyframes.class, keyframes, "callback", (Runnable) () -> {});
        setField(UIKeyframes.class, keyframes, "loops", new UIKeyframeLoops(keyframes));
        setField(UIKeyframes.class, keyframes, "motionShift", new UIKeyframeMotionShift(keyframes));

        UIKeyframeDopeSheet dopeSheet = new UIKeyframeDopeSheet(keyframes);
        UIKeyframeGraph curveGraph = new UIKeyframeGraph(keyframes);
        setField(UIKeyframes.class, keyframes, "dopeSheet", dopeSheet);
        setField(UIKeyframes.class, keyframes, "curveGraph", curveGraph);
        setField(UIKeyframes.class, keyframes, "currentGraph", dopeSheet);

        keyframes.getXAxis().set(0F, 2F);
        keyframes.getDopeSheet().setTrackHeight(20);
        keyframes.getDopeSheet().getYAxis().setScroll(0);
        return keyframes;
    }

    private static void testDopeSheetPrefersSelectedKeyframeOverCloserUnselectedNeighbor() throws Exception
    {
        UIKeyframes keyframes = createKeyframes();
        UIKeyframeDopeSheet dopeSheet = keyframes.getDopeSheet();
        KeyframeChannel<Double> channel = new KeyframeChannel<>("velocity", KeyframeFactories.DOUBLE);
        int aIdx = channel.insert(10F, 1.0D);
        int bIdx = channel.insert(11F, 2.0D);
        Keyframe selectedKey = channel.get(aIdx);
        Keyframe neighborKey = channel.get(bIdx);

        UIKeyframeSheet sheet = new UIKeyframeSheet("velocity", IKey.constant("Velocity"), 0xFFFFFF, channel, null);
        sheet.selection.add(aIdx);
        keyframes.addSheet(sheet);

        int y = dopeSheet.getDopeSheetY(sheet) + dopeSheet.getTrackHeight(sheet) / 2;
        int selectedX = keyframes.toGraphX(selectedKey.getTick());
        int neighborX = keyframes.toGraphX(neighborKey.getTick());

        check(Math.abs(neighborX - selectedX) <= 3, "Neighbors must overlap within click radius for this test");

        /* Cursor sits right on neighbor, while selectedKey is still within hit radius */
        Pair<Keyframe, KeyframeType> hit = dopeSheet.findKeyframe(neighborX, y);
        check(hit != null && hit.a == selectedKey,
            "Selected keyframe within hit radius must take priority over closer unselected neighbor");
    }

    private static void testDopeSheetPrefersSelectedSummaryMarkOverCloserUnselectedMark() throws Exception
    {
        UIKeyframes keyframes = createKeyframes();
        UIKeyframeDopeSheet dopeSheet = keyframes.getDopeSheet();
        KeyframeChannel<Boolean> channel = new KeyframeChannel<>("paused", KeyframeFactories.BOOLEAN);
        int aIdx = channel.insert(10F, false);
        channel.insert(11F, true);
        Keyframe selectedKey = channel.get(aIdx);

        UIKeyframeSheet sheet = new UIKeyframeSheet("paused", IKey.constant("Paused"), 0xFFFFFF, channel, null);
        sheet.section = new UIKeyframeSheet.Section("appearance", IKey.constant("Appearance"), Icons.NONE, 0xFFFFFF);
        sheet.selection.add(aIdx);
        keyframes.addSheet(sheet);

        /* Section heading is at row 0; first sheet is right below it */
        int sectionY = dopeSheet.getDopeSheetY() + (int) dopeSheet.getTrackHeight() / 2;
        int neighborX = keyframes.toGraphX(11F);

        Pair<Keyframe, KeyframeType> hit = dopeSheet.findKeyframe(neighborX, sectionY);
        check(hit != null && hit.b == KeyframeType.SUMMARY && hit.a == selectedKey,
            "Selected summary mark within hit radius must take priority over closer unselected summary mark");
    }

    private static void testGraphPrefersSelectedKeyframeOverCloserUnselectedNeighbor() throws Exception
    {
        UIKeyframes keyframes = createKeyframes();
        KeyframeChannel<Double> channel = new KeyframeChannel<>("velocity", KeyframeFactories.DOUBLE);
        int aIdx = channel.insert(10F, 50.0D);
        int bIdx = channel.insert(11F, 50.0D);
        Keyframe selectedKey = channel.get(aIdx);
        Keyframe neighborKey = channel.get(bIdx);

        UIKeyframeSheet sheet = new UIKeyframeSheet("velocity", IKey.constant("Velocity"), 0xFFFFFF, channel, null);
        sheet.selection.add(aIdx);
        keyframes.addSheet(sheet);

        Field curveGraphField = UIKeyframes.class.getDeclaredField("curveGraph");
        curveGraphField.setAccessible(true);
        UIKeyframeGraph graph = (UIKeyframeGraph) curveGraphField.get(keyframes);
        setField(UIKeyframes.class, keyframes, "currentGraph", graph);
        Field plotAreaField = UIKeyframeGraph.class.getDeclaredField("plotArea");
        plotAreaField.setAccessible(true);
        ((Area) plotAreaField.get(graph)).set(0, 0, 400, 200);
        Field yAxisField = UIKeyframeGraph.class.getDeclaredField("yAxis");
        yAxisField.setAccessible(true);
        ((Scale) yAxisField.get(graph)).view(0D, 100D);
        graph.getSheets().clear();
        graph.getSheets().add(sheet);

        int neighborX = keyframes.toGraphX(neighborKey.getTick());
        int y = graph.toGraphY(50.0D);

        Pair<Keyframe, KeyframeType> hit = graph.findKeyframe(neighborX, y);
        check(hit != null && hit.a == selectedKey,
            "Curve graph must prefer already-selected keyframe within hit radius over closer unselected neighbor (hit="
                + (hit == null ? "null" : hit.a) + ", y=" + y + ")");
    }

    @SuppressWarnings({"unchecked"})
    private static void testClickAndDragOnMultiSelectionKeepsAndMovesAllSelectedKeyframes() throws Exception
    {
        UIKeyframes keyframes = createKeyframes();
        UIKeyframeDopeSheet dopeSheet = keyframes.getDopeSheet();
        KeyframeChannel<Boolean> paused = new KeyframeChannel<>("paused", KeyframeFactories.BOOLEAN);
        int p0 = paused.insert(10F, false);
        int p1 = paused.insert(20F, false);
        KeyframeChannel<Double> velocity = new KeyframeChannel<>("velocity", KeyframeFactories.DOUBLE);
        int v0 = velocity.insert(20F, 1.0D);
        KeyframeChannel<Double> count = new KeyframeChannel<>("count", KeyframeFactories.DOUBLE);
        int c0 = count.insert(20F, 5.0D);
        int c1 = count.insert(40F, 5.0D);

        UIKeyframeSheet.Section appearance = new UIKeyframeSheet.Section("appearance", IKey.constant("Appearance"), Icons.NONE, 0xFFFFFF);
        UIKeyframeSheet pausedSheet = new UIKeyframeSheet("paused", IKey.constant("Paused"), 0xFFFFFF, paused, null);
        UIKeyframeSheet velocitySheet = new UIKeyframeSheet("velocity", IKey.constant("Velocity"), 0xFFFFFF, velocity, null);
        UIKeyframeSheet countSheet = new UIKeyframeSheet("count", IKey.constant("Count"), 0xFFFFFF, count, null);
        pausedSheet.section = appearance;
        velocitySheet.section = appearance;
        countSheet.section = appearance;

        pausedSheet.selection.add(p0);
        pausedSheet.selection.add(p1);
        velocitySheet.selection.add(v0);
        countSheet.selection.add(c0);
        countSheet.selection.add(c1);

        keyframes.addSheet(pausedSheet);
        keyframes.addSheet(velocitySheet);
        keyframes.addSheet(countSheet);

        UIContext context = (UIContext) getUnsafe().allocateInstance(UIContext.class);
        Method pickOrStart = UIKeyframes.class.getDeclaredMethod("pickOrStartSelectingKeyframes", UIContext.class);
        pickOrStart.setAccessible(true);
        Method subMouseReleased = UIKeyframes.class.getDeclaredMethod("subMouseReleased", UIContext.class);
        subMouseReleased.setAccessible(true);
        Field initialXField = UITimelineCanvas.class.getDeclaredField("initialX");
        Field initialYField = UITimelineCanvas.class.getDeclaredField("initialY");
        Field draggingField = UIKeyframes.class.getDeclaredField("dragging");
        Field draggingDataField = UIKeyframes.class.getDeclaredField("draggingData");
        Field originalTField = UIKeyframes.class.getDeclaredField("originalT");
        Field originalVField = UIKeyframes.class.getDeclaredField("originalV");
        initialXField.setAccessible(true);
        initialYField.setAccessible(true);
        draggingField.setAccessible(true);
        draggingDataField.setAccessible(true);
        originalTField.setAccessible(true);
        originalVField.setAccessible(true);

        /* 1. Plain click + release on one of the 5 selected keyframes must keep all 5 selected */
        int clickX = keyframes.toGraphX(40F);
        int clickY = dopeSheet.getDopeSheetY(countSheet) + dopeSheet.getTrackHeight(countSheet) / 2;
        context.mouseX = clickX;
        context.mouseY = clickY;
        context.mouseButton = 0;
        initialXField.setInt(keyframes, clickX);
        initialYField.setInt(keyframes, clickY);

        pickOrStart.invoke(keyframes, context);
        subMouseReleased.invoke(keyframes, context);

        check(pausedSheet.selection.has(p0) && pausedSheet.selection.has(p1), "Paused keyframes must stay selected after plain click");
        check(velocitySheet.selection.has(v0), "Velocity keyframe must stay selected after plain click");
        check(countSheet.selection.has(c0) && countSheet.selection.has(c1), "Count keyframes must stay selected after plain click");

        /* 2. Fast pan on empty space (> 2px before render updates dragging) must keep selection */
        context.mouseX = 300;
        context.mouseY = 180;
        initialXField.setInt(keyframes, 300);
        initialYField.setInt(keyframes, 180);
        pickOrStart.invoke(keyframes, context);
        context.mouseX = 320;
        subMouseReleased.invoke(keyframes, context);

        check(pausedSheet.selection.has(p0) && pausedSheet.selection.has(p1), "Fast empty pan must not clear Paused selection");
        check(velocitySheet.selection.has(v0), "Fast empty pan must not clear Velocity selection");
        check(countSheet.selection.has(c0) && countSheet.selection.has(c1), "Fast empty pan must not clear Count selection");

        /* 3. Dragging the clicked keyframe must move all 5 selected keyframes by the same tick delta */
        context.mouseX = clickX;
        context.mouseY = clickY;
        initialXField.setInt(keyframes, clickX);
        initialYField.setInt(keyframes, clickY);
        pickOrStart.invoke(keyframes, context);
        draggingField.setInt(keyframes, 1);

        int dragTargetX = keyframes.toGraphX(50F);
        context.mouseX = dragTargetX;
        context.mouseY = clickY;
        Pair<Keyframe, KeyframeType> draggingData = (Pair<Keyframe, KeyframeType>) draggingDataField.get(keyframes);
        dopeSheet.dragKeyframes(
            context,
            draggingData,
            clickX,
            clickY,
            originalTField.getFloat(keyframes),
            originalVField.get(keyframes)
        );
        subMouseReleased.invoke(keyframes, context);

        check(Math.abs(paused.get(p0).getTick() - 20F) < 0.01F, "Paused[0] should move from 10 to 20, was " + paused.get(p0).getTick());
        check(Math.abs(paused.get(p1).getTick() - 30F) < 0.01F, "Paused[1] should move from 20 to 30, was " + paused.get(p1).getTick());
        check(Math.abs(velocity.get(v0).getTick() - 30F) < 0.01F, "Velocity[0] should move from 20 to 30, was " + velocity.get(v0).getTick());
        check(Math.abs(count.get(c0).getTick() - 30F) < 0.01F, "Count[0] should move from 20 to 30, was " + count.get(c0).getTick());
        check(Math.abs(count.get(c1).getTick() - 50F) < 0.01F, "Count[1] should move from 40 to 50, was " + count.get(c1).getTick());
        check(pausedSheet.selection.has(p0) && pausedSheet.selection.has(p1), "Paused keyframes must stay selected after drag");
        check(velocitySheet.selection.has(v0), "Velocity keyframe must stay selected after drag");
        check(countSheet.selection.has(c0) && countSheet.selection.has(c1), "Count keyframes must stay selected after drag");

        /* 4. Plain click and drag on a summary mark must keep and move all 5 selected keyframes too */
        int summaryY = dopeSheet.getDopeSheetY() + (int) dopeSheet.getTrackHeight() / 2;
        int summaryX = keyframes.toGraphX(30F);
        context.mouseX = summaryX;
        context.mouseY = summaryY;
        initialXField.setInt(keyframes, summaryX);
        initialYField.setInt(keyframes, summaryY);
        pickOrStart.invoke(keyframes, context);
        subMouseReleased.invoke(keyframes, context);

        check(pausedSheet.selection.has(p0) && pausedSheet.selection.has(p1) && countSheet.selection.has(c1),
            "Plain click on summary mark must not drop multi-selection");

        context.mouseX = summaryX;
        context.mouseY = summaryY;
        initialXField.setInt(keyframes, summaryX);
        initialYField.setInt(keyframes, summaryY);
        pickOrStart.invoke(keyframes, context);
        draggingField.setInt(keyframes, 1);
        context.mouseX = keyframes.toGraphX(35F);
        @SuppressWarnings("unchecked")
        Pair<Keyframe, KeyframeType> summaryDrag = (Pair<Keyframe, KeyframeType>) draggingDataField.get(keyframes);
        dopeSheet.dragKeyframes(
            context,
            summaryDrag,
            summaryX,
            summaryY,
            originalTField.getFloat(keyframes),
            originalVField.get(keyframes)
        );
        subMouseReleased.invoke(keyframes, context);

        check(Math.abs(paused.get(p0).getTick() - 25F) < 0.01F, "Paused[0] should move from 20 to 25 via summary drag, was " + paused.get(p0).getTick());
        check(Math.abs(count.get(c1).getTick() - 55F) < 0.01F, "Count[1] should move from 50 to 55 via summary drag, was " + count.get(c1).getTick());

        /* 5. Clicking empty space without moving (<= 2px) still clears selection */
        context.mouseX = 350;
        context.mouseY = 180;
        initialXField.setInt(keyframes, 350);
        initialYField.setInt(keyframes, 180);
        pickOrStart.invoke(keyframes, context);
        subMouseReleased.invoke(keyframes, context);

        check(!pausedSheet.selection.hasAny() && !velocitySheet.selection.hasAny() && !countSheet.selection.hasAny(),
            "Plain click on empty space must clear selection");
    }
}
