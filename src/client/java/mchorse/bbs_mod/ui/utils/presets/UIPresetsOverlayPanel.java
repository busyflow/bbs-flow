package mchorse.bbs_mod.ui.utils.presets;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIPromptPopup;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.presets.PresetManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean, minimalistic, categorized presets overlay (Ctrl+Shift+V).
 * Displays category tabs for top-level preset folders (e.g. Shakes, Transitions)
 * alongside instant search filtering and one-click paste.
 */
public class UIPresetsOverlayPanel extends UIListOverlayPanel
{
    private String cwd = "";
    private String selectedCategory = "";
    private final UICopyPasteController controller;
    private final int mouseX;
    private final int mouseY;
    private final UIElement categoriesBar;

    public UIPresetsOverlayPanel(UICopyPasteController controller, int mouseX, int mouseY)
    {
        super(UIKeys.PRESETS_TITLE, null);
        this.w(280);

        this.controller = controller;
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        this.categoriesBar = new UIElement();
        this.categoriesBar.relative(this.content).x(6).y(6).w(1F, -12).h(20).row(4);
        this.content.add(this.categoriesBar);

        this.list.relative(this.content).x(6).y(30).w(1F, -12).h(1F, -36);

        this.callback = (l) ->
        {
            if (l.isEmpty())
            {
                return;
            }

            String pick = l.get(0);

            if ("..".equals(pick))
            {
                this.goUp();
                this.refreshList();

                return;
            }

            if (pick.endsWith("/"))
            {
                String name = pick.substring(0, pick.length() - 1);
                this.cwd = PresetManager.joinRelative(this.cwd, name);
                this.refreshList();

                return;
            }

            String id = this.selectedCategory.isEmpty() && this.cwd.isEmpty()
                ? pick
                : PresetManager.joinRelative(this.cwd.isEmpty() ? this.selectedCategory : this.cwd, pick);
            MapType load = this.controller.manager.load(id);

            if (load != null)
            {
                this.controller.getConsumer().paste(load, this.mouseX, this.mouseY);
                this.close();
            }
        };

        this.rebuildCategories();
        this.refreshList();

        UIIcon save = new UIIcon(Icons.SAVED, (b) ->
        {
            MapType type = this.controller.getSupplier().get();

            if (type != null)
            {
                UIPromptPopup pane = new UIPromptPopup(UIKeys.PRESETS_SAVE_TITLE, UIKeys.PRESETS_SAVE_DESCRIPTION, (t) ->
                {
                    String target = this.cwd.isEmpty()
                        ? (this.selectedCategory.isEmpty() ? t : PresetManager.joinRelative(this.selectedCategory, t))
                        : PresetManager.joinRelative(this.cwd, t);
                    this.controller.manager.save(target, type);
                    this.rebuildCategories();
                    this.refreshList();
                });

                pane.verb(UIKeys.GENERAL_SAVE);
                pane.text.filename();
                this.getContext().replaceContextMenu(pane);
            }
        });

        save.setEnabled(controller.canCopy());

        UIIcon folder = new UIIcon(Icons.FOLDER, (b) ->
        {
            UIUtils.openFolder(this.controller.manager.getFolder());
        });

        save.tooltip(UIKeys.PRESETS_SAVE);
        folder.tooltip(UIKeys.PRESETS_OPEN);
        this.icons.add(save, folder);
    }

    private void rebuildCategories()
    {
        this.categoriesBar.removeAll();

        List<String> rootItems = this.controller.manager.listDirectory("");
        List<String> folders = new ArrayList<>();

        for (String item : rootItems)
        {
            if (item.endsWith("/"))
            {
                folders.add(item.substring(0, item.length() - 1));
            }
        }

        if (folders.isEmpty())
        {
            this.categoriesBar.setVisible(false);
            this.list.relative(this.content).x(6).y(6).w(1F, -12).h(1F, -12);
            this.content.resize();

            return;
        }

        this.categoriesBar.setVisible(true);
        this.list.relative(this.content).x(6).y(30).w(1F, -12).h(1F, -36);

        this.addCategoryButton("All", "");

        for (String folder : folders)
        {
            this.addCategoryButton(folder, folder);
        }

        this.content.resize();
    }

    private void addCategoryButton(String label, String category)
    {
        UIButton button = new UIButton(IKey.constant(label), (b) ->
        {
            this.selectedCategory = category;
            this.cwd = "";
            this.refreshList();
        })
        {
            @Override
            public void render(UIContext context)
            {
                boolean active = category.equals(UIPresetsOverlayPanel.this.selectedCategory);

                if (active)
                {
                    this.area.render(context.batcher, BBSSettings.primaryColor(Colors.A75));
                }
                else if (this.area.isInside(context))
                {
                    this.area.render(context.batcher, Colors.setA(Colors.WHITE, 0.1F));
                }
                else
                {
                    this.area.render(context.batcher, Colors.setA(Colors.WHITE, 0.04F));
                }

                context.batcher.text(
                    this.label.get(),
                    this.area.mx(context.batcher.getFont().getWidth(this.label.get())),
                    this.area.my(context.batcher.getFont().getHeight()),
                    active ? Colors.WHITE : Colors.LIGHTEST_GRAY,
                    false
                );
            }
        };

        button.h(20);
        this.categoriesBar.add(button);
    }

    private void goUp()
    {
        if (this.cwd.isEmpty())
        {
            return;
        }

        int i = this.cwd.lastIndexOf('/');

        this.cwd = i < 0 ? "" : this.cwd.substring(0, i);
    }

    private void refreshList()
    {
        this.list.list.clear();

        if (!this.cwd.isEmpty())
        {
            this.addValues(this.controller.manager.listDirectory(this.cwd));

            return;
        }

        if (!this.selectedCategory.isEmpty())
        {
            List<String> items = new ArrayList<>();

            for (String item : this.controller.manager.listDirectory(this.selectedCategory))
            {
                if (!"..".equals(item))
                {
                    items.add(item);
                }
            }

            this.addValues(items);

            return;
        }

        /* "All" view: list root presets plus categorized presets as "Category/Name" for instant search. */
        List<String> all = new ArrayList<>();
        List<String> rootItems = this.controller.manager.listDirectory("");

        for (String item : rootItems)
        {
            if (item.endsWith("/"))
            {
                String cat = item.substring(0, item.length() - 1);

                for (String sub : this.controller.manager.listDirectory(cat))
                {
                    if (!"..".equals(sub) && !sub.endsWith("/"))
                    {
                        all.add(PresetManager.joinRelative(cat, sub));
                    }
                }
            }
            else if (!"..".equals(item))
            {
                all.add(item);
            }
        }

        this.addValues(all);
    }
}
