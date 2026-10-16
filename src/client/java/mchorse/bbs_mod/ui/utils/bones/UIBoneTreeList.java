package mchorse.bbs_mod.ui.utils.bones;

import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bone list that understands hierarchy. The backing list still holds plain string ids
 * (bone keys or attachment paths), so every {@link UIStringList} caller keeps working;
 * this class only adds per-id display metadata, drawn as outliner-style tree branches:
 * a tee for a middle child, a corner for the last one, and pass-through verticals for
 * every ancestor level that continues below. Search results render flat with a full
 * label instead (an attachment bone keeps its form's track name to stay recognizable).
 *
 * <p>Both fill flavors build the same intermediate node tree — {@link #fillBones} from
 * a model's bone hierarchy, {@link #fillAttachments} from a form's attachment keys —
 * so the connector math lives in one place. {@link #setHierarchy} emits only the
 * metadata while the host keeps managing the list contents itself (the pose editor's
 * bone list, which refills on every search keystroke — the metadata survives because
 * {@code clear()} intentionally does not touch it).</p>
 *
 * <p>A host that {@link #roles describes} its bones gets them read back at a glance: a bone
 * with nothing on it is quieter than one that carries something, role marks sit at the
 * row's right edge, and a chain runs as a coloured line along the branches between its
 * bones.</p>
 *
 * <p>A deep rig can run wider than the panel, so the tree scrolls sideways once it doesn't
 * fit: Shift+wheel, the bar along the bottom, or holding the middle button and dragging the
 * list around in both directions. Only the branches and names move — the rows' highlight
 * and the role marks stay where they are.</p>
 */
public class UIBoneTreeList extends UIStringList
{
    public static final int INDENT = 8;

    private static final int MARK = 4;
    private static final int MARK_GAP = 2;

    /**
     * How much further a gray (disabled) row fades in a list that quiets untouched bones —
     * otherwise its gray would read just like an untouched bone.
     */
    private static final float QUIET_DISABLED = 0.5F;

    private final Map<String, Meta> metas = new HashMap<>();

    private Predicate<String> disabled;

    private Consumer<BoneRoles> describer;
    private final BoneRoles roles = new BoneRoles();

    /** Chain colours on the branches, per row, from the last {@link #describe()}. */
    private final Map<String, ChainGuides> chainGuides = new HashMap<>();

    /** The mark under the cursor in the last drawn frame, and its row — the tooltip's subject. */
    private BoneRole hoveredRole;
    private final Area hoveredRow = new Area();

    /** Hosts that filter by refilling the list (instead of {@link #filter}) set this
     *  while a query is active, so matches render flat like built-in filtering does. */
    private boolean flat;

    /** Sideways scroll over the rows' content; its area is the list's, less the vertical bar's lane. */
    private final Scroll horizontal = new Scroll(new Area());

    /** Middle button held over the list: the cursor drags the content along, both ways. */
    private boolean panning;
    private int panX;
    private int panY;

    public UIBoneTreeList(Consumer<List<String>> callback)
    {
        super(callback);

        this.horizontal.direction = ScrollDirection.HORIZONTAL;
        this.horizontal.cancelScrollEdge = true;
        this.horizontal.scrollSpeed = INDENT * 2;
    }

    /**
     * Mark ids the host refuses to accept (e.g. IK targets that would close a cycle).
     * They render gray and {@link #isDisabled} lets the picker ignore clicks on them.
     */
    public UIBoneTreeList disabled(Predicate<String> predicate)
    {
        this.disabled = predicate;

        return this;
    }

    public boolean isDisabled(String id)
    {
        return this.disabled != null && id != null && !id.isEmpty() && this.disabled.test(id);
    }

    /**
     * Let the host say what is on its bones (see {@link BoneRoles}). It is asked again on
     * every draw, so the list can't fall behind an undo, a preset or a playing film. From
     * then on a bone with nothing on it reads quieter than one that carries something, and
     * hovering a mark names it.
     */
    public UIBoneTreeList roles(Consumer<BoneRoles> describer)
    {
        this.describer = describer;
        this.tooltip(new RoleTooltip());

        return this;
    }

    public void flat(boolean flat)
    {
        this.flat = flat;
    }

    /** First row currently on screen (respecting the search filter) — the Enter pick. */
    public String getFirstVisible()
    {
        return this.getElementAt(0);
    }

    /** Insert a special entry (like "None") above the tree, outside of any hierarchy. */
    public void prepend(String id, String label)
    {
        this.list.add(0, id);
        this.metas.put(id, new Meta(0, 0, true, label, label, null));
        this.update();
    }

    /**
     * Set only the hierarchy metadata from a model, leaving the list contents to the
     * host. Passing a null model clears the metadata (every row renders flat).
     */
    public void setHierarchy(IBoneHierarchy model, Predicate<String> hidden)
    {
        this.metas.clear();

        if (model != null)
        {
            this.emit(boneNodes(model, model.getRootGroupKeys(), hidden), null, 0, 0, false);
        }
    }

    /**
     * Fill from a model's bone hierarchy, pre-order, skipping hidden bones. A hidden
     * bone's children stay visible and take over its depth, mirroring how the flat
     * lists used to just remove disabled bones from the hierarchy-ordered key list.
     */
    public void fillBones(IBoneHierarchy model, Collection<String> hidden)
    {
        this.clear();
        this.metas.clear();

        if (model != null)
        {
            Predicate<String> predicate = hidden == null ? null : hidden::contains;

            this.emit(boneNodes(model, model.getRootGroupKeys(), predicate), null, 0, 0, true);
        }

        this.update();
    }

    /**
     * Fill with a plain list of bone names, no hierarchy — the fallback for forms
     * whose bones don't come from a rig at all.
     */
    public void fillFlat(Collection<String> bones)
    {
        this.clear();
        this.metas.clear();

        this.list.addAll(bones);
        this.update();
    }

    /**
     * Fill from a form's attachment keys (see {@code FormRenderer.collectMatrices}):
     * every form in the body part tree becomes a header row, its model bones nest
     * under it in their own hierarchy order. The key set stays the source of truth —
     * only ids present in it are listed, so the picker can never offer an attachment
     * the matrix cache doesn't actually resolve.
     */
    public void fillAttachments(Form form, Collection<String> keys)
    {
        this.clear();
        this.metas.clear();

        Set<String> keySet = new HashSet<>(keys);

        if (form != null)
        {
            this.emit(formNodes(form, "", keySet), null, 0, 0, true);
        }

        /* Safety net: keys the static walk didn't reach (exotic renderers) go in flat,
         * so switching from the raw key list to the tree can't lose selectable values. */
        List<String> missed = new ArrayList<>();

        for (String key : keySet)
        {
            if (!this.list.contains(key))
            {
                missed.add(key);
            }
        }

        missed.sort(String::compareToIgnoreCase);
        this.list.addAll(missed);

        this.update();
    }

    /* Building the intermediate node tree */

    /** A bone (and its visible subtree); a hidden bone dissolves into its children in place. */
    private static List<Node> boneNodes(IBoneHierarchy model, Collection<String> bones, Predicate<String> hidden)
    {
        List<Node> nodes = new ArrayList<>();

        for (String bone : bones)
        {
            List<Node> children = boneNodes(model, model.getDirectChildrenKeys(bone), hidden);

            if (hidden == null || !hidden.test(bone))
            {
                Node node = new Node(bone, bone, bone);

                node.children.addAll(children);
                nodes.add(node);
            }
            else
            {
                nodes.addAll(children);
            }
        }

        return nodes;
    }

    /**
     * A form's representation at its parent's level: the form row (when its own key is
     * in the set) with its model bones and sub-forms nested under it — or, for a form
     * the matrix cache doesn't list, those children hoisted in place. The body part
     * index must advance for every part, even form-less ones — that is how
     * collectMatrices numbers the paths.
     */
    private static List<Node> formNodes(Form form, String path, Set<String> keys)
    {
        List<Node> children = new ArrayList<>();

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance instance = ModelFormRenderer.getModel(modelForm);

            if (instance != null && instance.model != null)
            {
                children.addAll(formBoneNodes(form, instance.model, instance.model.getRootGroupKeys(), path, keys));
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            Form child = part.getForm();

            if (child != null)
            {
                children.addAll(formNodes(child, StringUtils.combinePaths(path, part.getId()), keys));
            }
        }

        if (!keys.contains(path))
        {
            return children;
        }

        String trackName = form.getTrackName("");
        String label = trackName.isEmpty() ? form.getFormIdOrName() : trackName;
        Node node = new Node(path, label, label);

        node.children.addAll(children);

        return new ArrayList<>(List.of(node));
    }

    private static List<Node> formBoneNodes(Form owner, IBoneHierarchy model, Collection<String> bones, String formPath, Set<String> keys)
    {
        List<Node> nodes = new ArrayList<>();

        for (String bone : bones)
        {
            String key = StringUtils.combinePaths(formPath, bone);
            List<Node> children = formBoneNodes(owner, model, model.getDirectChildrenKeys(bone), formPath, keys);

            if (keys.contains(key))
            {
                Node node = new Node(key, bone, owner.getTrackName(key));

                node.children.addAll(children);
                nodes.add(node);
            }
            else
            {
                nodes.addAll(children);
            }
        }

        return nodes;
    }

    /**
     * Flatten the node tree into the list (pre-order) and compute each row's branch
     * drawing: {@code lines} carries which ancestor columns still run a vertical
     * (their node wasn't the last sibling), {@code last} picks corner over tee.
     */
    private void emit(List<Node> nodes, String parent, int depth, int lines, boolean fill)
    {
        for (int i = 0; i < nodes.size(); i++)
        {
            Node node = nodes.get(i);
            boolean last = i == nodes.size() - 1;

            this.metas.put(node.id, new Meta(depth, lines, last, node.treeLabel, node.fullLabel, parent));

            if (fill)
            {
                this.list.add(node.id);
            }

            /* This node's connector column keeps its vertical running through the
             * whole subtree unless the node closed the level as its last sibling.
             * Roots have no column, so nothing to continue. */
            int childLines = childGuideLines(lines, depth, last);

            this.emit(node.children, node.id, depth + 1, childLines, fill);
        }
    }

    @Override
    protected String elementToString(UIContext context, int i, String element)
    {
        Meta meta = this.metas.get(element);

        return meta == null ? element : meta.fullLabel;
    }

    /**
     * The row's metadata as shown: none while search results render flat — branches
     * without the parent rows above them are just a lie about structure.
     */
    private Meta shownMeta(String element)
    {
        return this.flat || this.isFiltering() ? null : this.metas.get(element);
    }

    /** The tree is always fully unfolded, so rows only step right; no branch ever folds. */
    @Override
    protected int indent(String element)
    {
        Meta meta = this.shownMeta(element);

        return meta == null ? 0 : meta.depth * INDENT;
    }

    @Override
    protected int indentStep()
    {
        return INDENT;
    }

    @Override
    public void render(UIContext context)
    {
        this.describe();
        this.measureWidth(context);
        this.hoveredRole = null;

        super.render(context);
    }

    /**
     * How wide the rows run — indent, name, and the marks at the right edge, which the name
     * must stop short of — against the room the list has; the difference is the sideways scroll.
     * Measured every frame over all rows, not only those on screen, so scrolling up and down
     * doesn't change how far it goes sideways.
     */
    private void measureWidth(UIContext context)
    {
        List<String> visible = this.visible();
        int width = 0;

        for (int i = 0; i < visible.size(); i++)
        {
            String element = visible.get(i);
            Meta meta = this.shownMeta(element);
            int cells = this.markCells(element, meta != null);
            int tail = cells == 0 ? ROW_PADDING : 2 + 3 + cells * (MARK + MARK_GAP);
            int label = context.batcher.getFont().getWidth(this.rowLabel(context, i, element, meta));

            width = Math.max(width, this.rowContentX(element) + label + tail);
        }

        this.horizontal.area.copy(this.area);
        this.horizontal.area.w -= this.scroll.hasScrollbar() ? this.scroll.getScrollbarWidth() : 0;
        this.horizontal.scrollSize = width;
        this.horizontal.clamp();
    }

    @Override
    protected void renderContent(UIContext context)
    {
        super.renderContent(context);

        this.horizontal.renderScrollbar(context.batcher);
    }

    /* Sideways scroll and the middle-button drag */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.horizontal.mouseClicked(context))
        {
            return true;
        }

        /* Only a list that has somewhere to go takes the middle button from what's behind it */
        boolean scrolls = this.scroll.hasScrollbar() || this.horizontal.hasScrollbar();

        if (context.mouseButton == 2 && scrolls && this.area.isInside(context))
        {
            this.panning = true;
            this.panX = context.mouseX;
            this.panY = context.mouseY;

            return true;
        }

        return super.subMouseClicked(context);
    }

    /** Shift turns the wheel sideways, the way the text editor's does. */
    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (Window.isShiftPressed() && this.horizontal.hasScrollbar() && this.horizontal.mouseScroll(context))
        {
            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.horizontal.mouseReleased(context);
        this.panning = false;

        return super.subMouseReleased(context);
    }

    @Override
    protected void updateGestures(UIContext context)
    {
        super.updateGestures(context);

        /* A release swallowed by another element must not leave the list stuck to the cursor */
        if (this.panning && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
        {
            this.panning = false;
        }

        if (this.panning)
        {
            /* Straight to the position, past the smoothing — the content stays under the cursor */
            this.scroll.setScroll(this.scroll.getScroll() + this.panY - context.mouseY);
            this.horizontal.setScroll(this.horizontal.getScroll() + this.panX - context.mouseX);

            this.panX = context.mouseX;
            this.panY = context.mouseY;
        }

        this.horizontal.drag(context);
    }

    /** Ask the host about its bones again and work out where its chains run in the tree. */
    private void describe()
    {
        this.roles.clear();
        this.chainGuides.clear();

        if (this.describer == null)
        {
            return;
        }

        this.describer.accept(this.roles);

        if (this.roles.getLinks().isEmpty())
        {
            return;
        }

        Map<String, Integer> rows = new HashMap<>();

        for (int i = 0; i < this.list.size(); i++)
        {
            rows.put(this.list.get(i), i);
        }

        /* Switched-off chains first, so where two chains share a branch the working one shows. */
        for (int pass = 0; pass < 2; pass++)
        {
            for (Map.Entry<String, BoneRoles.Link> entry : this.roles.getLinks().entrySet())
            {
                if (entry.getValue().role().isOff() == (pass == 0))
                {
                    this.paintBranch(entry.getKey(), entry.getValue(), rows);
                }
            }
        }
    }

    /**
     * Colour the branch from the bone's parent row down into the bone: the parent's child
     * column through every row between the two, then the connector into the bone itself.
     */
    private void paintBranch(String bone, BoneRoles.Link link, Map<String, Integer> rows)
    {
        Meta meta = this.metas.get(bone);

        if (meta == null || meta.parent == null || !link.chain().contains(meta.parent))
        {
            return;
        }

        Integer from = rows.get(meta.parent);
        Integer to = rows.get(bone);

        if (from == null || to == null || from >= to)
        {
            return;
        }

        int level = meta.depth - 1;
        int color = link.role().color;

        for (int i = from + 1; i < to; i++)
        {
            String row = this.list.get(i);
            Meta rowMeta = this.metas.get(row);

            if (rowMeta != null && rowMeta.depth > level)
            {
                this.chainGuides.computeIfAbsent(row, (k) -> new ChainGuides(rowMeta.depth)).through[level] = color;
            }
        }

        this.chainGuides.computeIfAbsent(bone, (k) -> new ChainGuides(meta.depth)).into = color;
    }

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
        Meta meta = this.shownMeta(element);
        int depth = meta == null ? 0 : meta.depth;
        int h = this.scroll.scrollItemSize;

        /* The branches and the name scroll sideways; the marks keep to the row's right edge */
        int sx = x - (int) this.horizontal.getScroll();
        int textX = sx + this.rowContentX(element);

        if (meta != null)
        {
            this.renderTreeGuides(context, sx, y, depth, meta.lines, meta.last, textX);
            this.renderChainGuides(context, element, sx, y, depth, textX);
        }

        String label = this.rowLabel(context, i, element, meta);
        boolean lit = hover || selected;
        int right = this.renderMarks(context, element, meta != null, x, y, h, hover);

        if (right < x + this.area.w)
        {
            label = context.batcher.getFont().limitToWidth(label, right - textX - 2);
        }

        context.batcher.textShadow(label, textX, y + (h - context.batcher.getFont().getHeight()) / 2, this.labelColor(element, lit));
    }

    /** What a row reads: the bare name in the tree, the full label when matches render flat. */
    private String rowLabel(UIContext context, int i, String element, Meta meta)
    {
        if (meta != null)
        {
            return meta.treeLabel;
        }

        return this.flat || this.isFiltering() ? this.elementToString(context, i, element) : element;
    }

    /**
     * A bone the host says nothing about reads quieter than one that carries something —
     * but only in a list whose host describes its bones; a picker has nothing to tell apart.
     */
    private int labelColor(String element, boolean lit)
    {
        boolean quiets = this.describer != null;

        if (this.isDisabled(element))
        {
            int color = RowStyle.textColor(lit, Colors.GRAY);

            return quiets && !lit ? Colors.mulA(color, QUIET_DISABLED) : color;
        }

        return quiets && !this.roles.isTouched(element) ? RowStyle.quietTextColor(lit) : RowStyle.textColor(lit);
    }

    /** The chains' colours laid over the plain branches {@link #renderTreeGuides} drew. */
    private void renderChainGuides(UIContext context, String element, int x, int y, int depth, int textX)
    {
        ChainGuides guides = this.chainGuides.get(element);

        if (guides == null || depth <= 0)
        {
            return;
        }

        int h = this.scroll.scrollItemSize;
        int mid = y + h / 2;

        for (int level = 0; level < Math.min(depth, guides.through.length); level++)
        {
            if (guides.through[level] != 0)
            {
                int lx = this.guideX(x, level);

                context.batcher.box(lx, y, lx + 1, y + h, guides.through[level]);
            }
        }

        if (guides.into != 0)
        {
            int lx = this.guideX(x, depth - 1);

            context.batcher.box(lx, y, lx + 1, mid + 1, guides.into);
            context.batcher.box(lx + 1, mid, textX - 2, mid + 1, guides.into);
        }
    }

    /**
     * Draws the row's role marks from the right edge inwards (past the scrollbar lane) and
     * returns the x the label must stop at. A link mark stays off where the tree already
     * draws its chain into the row.
     */
    private int renderMarks(UIContext context, String element, boolean tree, int x, int y, int h, boolean hover)
    {
        BoneRole[] marks = this.describer == null ? null : this.roles.getMarks(element);

        if (marks == null)
        {
            return x + this.area.w;
        }

        BoneRoles.Link link = this.drawnLink(element, tree);
        int right = x + this.area.w - 3 - this.scroll.getScrollbarArea().w;
        int top = y + (h - MARK) / 2;
        int used = 0;

        for (int slot = 0; slot < marks.length; slot++)
        {
            BoneRole role = marks[slot];

            if (!showsMark(role, slot, link))
            {
                continue;
            }

            int cell = right - (slot + 1) * (MARK + MARK_GAP) + MARK_GAP;

            renderMark(context, role, cell, top);
            used = slot + 1;

            if (hover && context.mouseX >= cell - MARK_GAP / 2 && context.mouseX < cell + MARK + MARK_GAP / 2)
            {
                this.hoveredRole = role;
                this.hoveredRow.set(context.globalX(x), context.globalY(y), this.area.w, h);
            }
        }

        return used == 0 ? x + this.area.w : right - used * (MARK + MARK_GAP);
    }

    /** How many mark cells the row takes from its right edge — a skipped slot still holds its place. */
    private int markCells(String element, boolean tree)
    {
        BoneRole[] marks = this.describer == null ? null : this.roles.getMarks(element);

        if (marks == null)
        {
            return 0;
        }

        BoneRoles.Link link = this.drawnLink(element, tree);
        int used = 0;

        for (int slot = 0; slot < marks.length; slot++)
        {
            if (showsMark(marks[slot], slot, link))
            {
                used = slot + 1;
            }
        }

        return used;
    }

    /** The link whose chain the tree already draws into the row, or null. */
    private BoneRoles.Link drawnLink(String element, boolean tree)
    {
        ChainGuides guides = tree ? this.chainGuides.get(element) : null;

        return guides != null && guides.into != 0 ? this.roles.getLink(element) : null;
    }

    /** A link mark stays off where the line into the row already says it. */
    private static boolean showsMark(BoneRole role, int slot, BoneRoles.Link link)
    {
        return role != null && !(slot == 0 && link != null && role == link.role());
    }

    private static void renderMark(UIContext context, BoneRole role, int x, int y)
    {
        switch (role.shape)
        {
            case FULL -> context.batcher.box(x, y, x + MARK, y + MARK, role.color);
            case SMALL -> context.batcher.box(x + 1, y + 1, x + MARK - 1, y + MARK - 1, role.color);
            case HOLLOW -> context.batcher.outline(x, y, x + MARK, y + MARK, role.color);
        }
    }

    /** Names the mark under the cursor, beside its row; nothing anywhere else in the list. */
    private class RoleTooltip implements ITooltip
    {
        private final LabelTooltip label = new LabelTooltip(IKey.EMPTY, Direction.LEFT);

        @Override
        public IKey getLabel()
        {
            BoneRole role = UIBoneTreeList.this.hoveredRole;

            return role == null ? IKey.EMPTY : role.label;
        }

        @Override
        public void renderTooltip(UIContext context)
        {
            BoneRole role = UIBoneTreeList.this.hoveredRole;

            if (role != null)
            {
                this.label.label = role.label;
                this.label.anchor = UIBoneTreeList.this.hoveredRow;
                this.label.renderTooltip(context);
            }
        }
    }

    /** One row's share of the chains drawn along the tree. */
    private static class ChainGuides
    {
        /** Per ancestor column: the colour of a chain whose branch passes this row, or 0. */
        public final int[] through;

        /** The colour of the chain whose branch ends in this row, or 0. */
        public int into;

        public ChainGuides(int depth)
        {
            this.through = new int[depth];
        }
    }

    private static class Node
    {
        public final String id;
        public final String treeLabel;
        public final String fullLabel;
        public final List<Node> children = new ArrayList<>();

        public Node(String id, String treeLabel, String fullLabel)
        {
            this.id = id;
            this.treeLabel = treeLabel;
            this.fullLabel = fullLabel;
        }
    }

    private static class Meta
    {
        public final int depth;

        /** Bit {@code i} — the vertical in ancestor column {@code i} runs through this row. */
        public final int lines;

        /** Last visible sibling — its connector is a corner instead of a tee. */
        public final boolean last;

        public final String treeLabel;
        public final String fullLabel;

        /** The row this one hangs from in the tree (a hidden bone's children skip it), or null. */
        public final String parent;

        public Meta(int depth, int lines, boolean last, String treeLabel, String fullLabel, String parent)
        {
            this.depth = depth;
            this.lines = lines;
            this.last = last;
            this.treeLabel = treeLabel;
            this.fullLabel = fullLabel;
            this.parent = parent;
        }
    }
}
