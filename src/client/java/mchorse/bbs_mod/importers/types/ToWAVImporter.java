package mchorse.bbs_mod.importers.types;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.ImporterUtils;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.io.File;

public class ToWAVImporter implements IImporter
{
    private final IKey name;
    private final String[] extensions;

    public ToWAVImporter(IKey name, String... extensions)
    {
        this.name = name;
        this.extensions = extensions;
    }

    @Override
    public IKey getName()
    {
        return this.name;
    }

    @Override
    public File getDefaultFolder()
    {
        return BBSMod.getAudioFolder();
    }

    @Override
    public boolean canImport(ImporterContext context)
    {
        return ImporterUtils.checkFileExtension(context.files, this.extensions);
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