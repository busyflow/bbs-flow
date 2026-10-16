package mchorse.bbs_mod.importers;

import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.File;
import java.util.List;

public class ImporterUtils
{
    public static boolean checkFileExtension(List<File> files, String... extensions)
    {
        for (File file : files)
        {
            String name = file.getName().toLowerCase();
            boolean hasAny = false;

            for (String extension : extensions)
            {
                if (name.endsWith(extension))
                {
                    hasAny = true;
                }
            }

            if (!hasAny)
            {
                return false;
            }
        }

        return true;
    }

    public static String getName(File directory, String name)
    {
        return IOUtils.findNonExistingFile(new File(directory, name)).getName();
    }

    /**
     * Turn a sound FFmpeg can read into a WAV in the given folder, under a name of its own rather
     * than over a file that is already there.
     *
     * @return The WAV that was written, or null when FFmpeg couldn't do it.
     */
    public static File toMonoWAV(File file, File folder)
    {
        String name = getName(folder, StringUtils.removeExtension(file.getName()) + ".wav");

        folder.mkdirs();

        /* Force the audio to be mono */
        if (!FFMpegUtils.execute(folder, "-y", "-i", file.getAbsolutePath(), "-ac", "1", name))
        {
            return null;
        }

        return new File(folder, name);
    }
}