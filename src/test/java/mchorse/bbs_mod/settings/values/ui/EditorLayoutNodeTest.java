package mchorse.bbs_mod.settings.values.ui;

import java.util.HashSet;
import java.util.Set;

public class EditorLayoutNodeTest
{
    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args)
    {
        testInsertSplitAtBottom();
        testRemovePanel();
        testRootDirectPanelHealing();
        testDualTimelineFilmLayoutCollapse();
        System.out.println("EditorLayoutNodeTest: all checks passed!");
    }

    private static void testInsertSplitAtBottom()
    {
        EditorLayoutNode root = new EditorLayoutNode.PanelNode("main");
        EditorLayoutNode updated = EditorLayoutNode.copyWithInsertSplitAt(root, "main", "poseTimeline", EditorLayoutNode.EDGE_BOTTOM);

        check(updated instanceof EditorLayoutNode.SplitterNode, "Expected root to be SplitterNode");
        EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) updated;
        check(splitter.isHorizontal(), "EDGE_BOTTOM should create a horizontal splitter");
        check(splitter.getFirst() instanceof EditorLayoutNode.PanelNode, "First child should be PanelNode");
        check(((EditorLayoutNode.PanelNode) splitter.getFirst()).getPanelId().equals("main"), "First child should be 'main'");
        check(splitter.getSecond() instanceof EditorLayoutNode.PanelNode, "Second child should be PanelNode");
        check(((EditorLayoutNode.PanelNode) splitter.getSecond()).getPanelId().equals("poseTimeline"), "Second child should be 'poseTimeline'");

        Set<String> ids = new HashSet<>();
        EditorLayoutNode.collectPanelIds(updated, ids);
        check(ids.contains("main") && ids.contains("poseTimeline"), "Both panel IDs should be collected");
    }

    private static void testRemovePanel()
    {
        EditorLayoutNode root = new EditorLayoutNode.SplitterNode(true, 0.5F,
            new EditorLayoutNode.PanelNode("main"),
            new EditorLayoutNode.PanelNode("poseTimeline")
        );
        EditorLayoutNode updated = EditorLayoutNode.copyWithRemovedPanel(root, "poseTimeline");
        check(updated instanceof EditorLayoutNode.PanelNode, "Removing one child of 2-child splitter should collapse to PanelNode");
        check(((EditorLayoutNode.PanelNode) updated).getPanelId().equals("main"), "Remaining panel should be 'main'");
    }

    private static void testRootDirectPanelHealing()
    {
        // Root split where right side is poseTimeline (the bug scenario where it was appended at root)
        EditorLayoutNode root = new EditorLayoutNode.SplitterNode(false, 0.75F,
            new EditorLayoutNode.SplitterNode(true, 0.5F,
                new EditorLayoutNode.PanelNode("editArea"),
                new EditorLayoutNode.PanelNode("main")
            ),
            new EditorLayoutNode.PanelNode("poseTimeline")
        );

        check(EditorLayoutNode.containsPanel(root, "poseTimeline"), "Root contains poseTimeline");
        // Removing poseTimeline from root directly collapses root to the inner splitter
        EditorLayoutNode repaired = EditorLayoutNode.copyWithRemovedPanel(root, "poseTimeline");
        check(repaired instanceof EditorLayoutNode.SplitterNode, "Repaired root should be SplitterNode");
        check(!EditorLayoutNode.containsPanel(repaired, "poseTimeline"), "Repaired root does not contain poseTimeline");

        // Now re-inserting poseTimeline splitting 'main' at EDGE_BOTTOM
        EditorLayoutNode proper = EditorLayoutNode.copyWithInsertSplitAt(repaired, "main", "poseTimeline", EditorLayoutNode.EDGE_BOTTOM);
        check(EditorLayoutNode.containsPanel(proper, "poseTimeline"), "Proper root contains poseTimeline");
        check(proper instanceof EditorLayoutNode.SplitterNode, "Proper root is SplitterNode");
        EditorLayoutNode.SplitterNode properRoot = (EditorLayoutNode.SplitterNode) proper;
        check(!(properRoot.getFirst() instanceof EditorLayoutNode.PanelNode && ((EditorLayoutNode.PanelNode) properRoot.getFirst()).getPanelId().equals("poseTimeline")) &&
              !(properRoot.getSecond() instanceof EditorLayoutNode.PanelNode && ((EditorLayoutNode.PanelNode) properRoot.getSecond()).getPanelId().equals("poseTimeline")),
              "poseTimeline should NOT be a direct child of the root splitter");
    }

    private static void testDualTimelineFilmLayoutCollapse()
    {
        EditorLayoutNode layout = EditorLayoutNode.dualTimelineFilmLayout();
        check(EditorLayoutNode.containsPanel(layout, "cameraTimeline"), "Layout contains cameraTimeline");
        check(EditorLayoutNode.containsPanel(layout, "main"), "Layout contains main");
        check(EditorLayoutNode.containsPanel(layout, "poseTimeline"), "Layout contains poseTimeline");

        // When poseTimeline is removed (separateForm is disabled)
        EditorLayoutNode noPose = EditorLayoutNode.copyWithRemovedPanel(layout, "poseTimeline");
        check(!EditorLayoutNode.containsPanel(noPose, "poseTimeline"), "noPose does not contain poseTimeline");
        check(EditorLayoutNode.containsPanel(noPose, "main"), "noPose still contains main");
        check(EditorLayoutNode.containsPanel(noPose, "cameraTimeline"), "noPose still contains cameraTimeline");

        // When cameraTimeline is removed (separateCamera is disabled)
        EditorLayoutNode noCamera = EditorLayoutNode.copyWithRemovedPanel(layout, "cameraTimeline");
        check(!EditorLayoutNode.containsPanel(noCamera, "cameraTimeline"), "noCamera does not contain cameraTimeline");
        check(EditorLayoutNode.containsPanel(noCamera, "main"), "noCamera still contains main");
        check(EditorLayoutNode.containsPanel(noCamera, "poseTimeline"), "noCamera still contains poseTimeline");

        // When both are removed
        EditorLayoutNode mainOnly = EditorLayoutNode.copyWithRemovedPanel(noPose, "cameraTimeline");
        check(!EditorLayoutNode.containsPanel(mainOnly, "cameraTimeline"), "mainOnly does not contain cameraTimeline");
        check(!EditorLayoutNode.containsPanel(mainOnly, "poseTimeline"), "mainOnly does not contain poseTimeline");
        check(EditorLayoutNode.containsPanel(mainOnly, "main"), "mainOnly still contains main");
    }
}
