package mchorse.bbs_mod.ui.framework.elements.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import sun.misc.Unsafe;

public class UITimelinePanelTest
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
        testEmptyStateLifecycle();
        testRenderEmptyStateWithNullSupplierDoesNotThrow();
        testRenderEmptyStateWithOtherVisibleChildDoesNotRender();
        System.out.println("UITimelinePanelTest: all checks passed!");
    }

    private static void testRenderEmptyStateWithOtherVisibleChildDoesNotRender() throws Exception
    {
        Class<?> elementClass = Class.forName("mchorse.bbs_mod.ui.framework.elements.UIElement");
        Class<?> timelinePanelClass = Class.forName("mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel");

        Object target = elementClass.getConstructor().newInstance();
        Object panel = createTimelinePanel();

        Method setTarget = timelinePanelClass.getMethod("setTarget", elementClass);
        Method setEmptyState = timelinePanelClass.getMethod("setEmptyState", Supplier.class);
        Method renderEmptyState = timelinePanelClass.getDeclaredMethod("renderEmptyState", Class.forName("mchorse.bbs_mod.ui.framework.UIContext"));
        renderEmptyState.setAccessible(true);

        boolean[] supplierCalled = new boolean[] {false};
        Supplier<?> supplier = () -> {
            supplierCalled[0] = true;
            return null;
        };

        setTarget.invoke(panel, target);
        setEmptyState.invoke(panel, supplier);

        // When target only has emptyState, supplier is queried
        supplierCalled[0] = false;
        renderEmptyState.invoke(panel, new Object[] {null});
        check(supplierCalled[0], "emptyLabel supplier should be queried when target has no other visible UIElement");

        // When another visible UIElement (like another editor's properties panel) is added to target:
        Object otherPanel = elementClass.getConstructor().newInstance();
        Method add = elementClass.getMethod("add", Class.forName("mchorse.bbs_mod.ui.framework.elements.IUIElement"));
        add.invoke(target, otherPanel);

        supplierCalled[0] = false;
        renderEmptyState.invoke(panel, new Object[] {null});
        check(!supplierCalled[0], "emptyLabel supplier should NOT be queried when another visible UIElement is present in target");

        // If that other element is hidden, emptyLabel supplier should be queried again
        Method setVisible = elementClass.getMethod("setVisible", boolean.class);
        setVisible.invoke(otherPanel, false);

        supplierCalled[0] = false;
        renderEmptyState.invoke(panel, new Object[] {null});
        check(supplierCalled[0], "emptyLabel supplier should be queried when the other UIElement is invisible");
    }

    private static Object createTimelinePanel() throws Exception
    {
        // UIClipsPanel extends UITimelinePanel and implements getPropertiesPanel() and getTimeline().
        // Using allocateInstance bypasses any Minecraft client / HUD initialization.
        Class<?> panelClass = Class.forName("mchorse.bbs_mod.ui.film.UIClipsPanel");
        Object panel = getUnsafe().allocateInstance(panelClass);

        Field propVis = Class.forName("mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel").getDeclaredField("propertiesVisible");
        propVis.setAccessible(true);
        propVis.setBoolean(panel, true);

        return panel;
    }

    private static void testEmptyStateLifecycle() throws Exception
    {
        Class<?> elementClass = Class.forName("mchorse.bbs_mod.ui.framework.elements.UIElement");
        Class<?> timelinePanelClass = Class.forName("mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel");

        Object target = elementClass.getConstructor().newInstance();
        Object panel = createTimelinePanel();

        Method setTarget = timelinePanelClass.getMethod("setTarget", elementClass);
        Method setEmptyState = timelinePanelClass.getMethod("setEmptyState", Supplier.class);
        Method getChildren = elementClass.getMethod("getChildren");

        // Initially no children in target
        check(((List<?>) getChildren.invoke(target)).isEmpty(), "Target should initially be empty");

        // setTarget with emptyLabel still null
        setTarget.invoke(panel, target);
        check(((List<?>) getChildren.invoke(target)).isEmpty(), "Target should remain empty when emptyLabel is null");

        // setEmptyState with non-null supplier
        Supplier<?> dummySupplier = () -> null;
        setEmptyState.invoke(panel, dummySupplier);
        List<?> children = (List<?>) getChildren.invoke(target);
        check(children.size() == 1, "Target should have 1 child (emptyState) after setEmptyState with supplier");

        // setEmptyState(null) MUST remove emptyState from target!
        setEmptyState.invoke(panel, new Object[] {null});
        children = (List<?>) getChildren.invoke(target);
        check(children.isEmpty(), "Target should have 0 children after setEmptyState(null)");

        // setEmptyState again
        setEmptyState.invoke(panel, dummySupplier);
        check(((List<?>) getChildren.invoke(target)).size() == 1, "Target should have 1 child after re-setting supplier");

        // Moving target to newTarget
        Object newTarget = elementClass.getConstructor().newInstance();
        setTarget.invoke(panel, newTarget);
        check(((List<?>) getChildren.invoke(target)).isEmpty(), "Old target should have 0 children after setTarget(newTarget)");
        check(((List<?>) getChildren.invoke(newTarget)).size() == 1, "New target should have 1 child after setTarget(newTarget)");

        // setTarget(null)
        setTarget.invoke(panel, new Object[] {null});
        check(((List<?>) getChildren.invoke(newTarget)).isEmpty(), "New target should have 0 children after setTarget(null)");
    }

    private static void testRenderEmptyStateWithNullSupplierDoesNotThrow() throws Exception
    {
        Class<?> timelinePanelClass = Class.forName("mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel");
        Object panel = createTimelinePanel();

        // renderEmptyState is private in UITimelinePanel
        Method renderEmptyState = timelinePanelClass.getDeclaredMethod("renderEmptyState", Class.forName("mchorse.bbs_mod.ui.framework.UIContext"));
        renderEmptyState.setAccessible(true);

        // Ensure emptyLabel is null
        Field emptyLabelField = timelinePanelClass.getDeclaredField("emptyLabel");
        emptyLabelField.setAccessible(true);
        emptyLabelField.set(panel, null);

        // Even if emptyStateHost is set:
        Field hostField = timelinePanelClass.getDeclaredField("emptyStateHost");
        hostField.setAccessible(true);
        Class<?> elementClass = Class.forName("mchorse.bbs_mod.ui.framework.elements.UIElement");
        hostField.set(panel, elementClass.getConstructor().newInstance());

        // Calling renderEmptyState must NOT throw NullPointerException when emptyLabel is null
        try
        {
            renderEmptyState.invoke(panel, new Object[] {null});
        }
        catch (Exception e)
        {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new AssertionError("renderEmptyState threw exception when emptyLabel is null: " + cause, cause);
        }
    }
}
