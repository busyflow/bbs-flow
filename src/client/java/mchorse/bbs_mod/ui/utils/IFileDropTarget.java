package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.ui.framework.UIContext;

import java.io.File;
import java.util.List;

/**
 * An element that takes files dragged in from outside the game when they are dropped onto it,
 * before the importers get them. Every one on screen is asked in turn, with the context's mouse at
 * the drop point; whether that point is its own is for the element to say.
 */
public interface IFileDropTarget
{
    /**
     * @return Whether the files were taken here, which keeps the importers away from them.
     */
    public boolean dropFiles(UIContext context, List<File> files);
}
