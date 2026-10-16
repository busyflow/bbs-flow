package mchorse.bbs_mod.importers.types;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.ImporterUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;

import java.io.File;

public class WAVImporter implements IImporter
{
    @Override
    public IKey getName()
    {
        return UIKeys.IMPORTER_WAV;
    }

    @Override
    public File getDefaultFolder()
    {
        return BBSMod.getAudioFolder();
    }

    @Override
    public boolean canImport(ImporterContext context)
    {
        return ImporterUtils.checkFileExtension(context.files, ".wav");
    }

    @Override
    public void importFiles(ImporterContext context)
    {
        for (File file : context.files)
        {
            ImporterUtils.toMonoWAV(file, context.getDestination(this));
        }
    }
}