package mchorse.bbs_mod.importers;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Models dragged into the game. A folder with a model somewhere inside it goes into the assets'
 * models folder as it is, subfolders and all; a model's own files (its geometry and whatever came
 * along — textures, config, animations) go there into a folder named after the geometry file.
 * Next to the models that are already there rather than over one of them, so dropping a model
 * again makes a second copy instead of losing the first.
 *
 * <p>Not one of the {@link Importers}: those convert a type of file into the folder at hand and
 * show it in the file browser, while a model goes to one place only and shows up in the model
 * lists by itself.</p>
 */
public class ModelImporter
{
    /** What a model can be read from (see the model loaders); config and animations ride along. */
    private static final String[] GEOMETRY = {".bbs.json", ".geo.json", ".bobj", ".obj", ".jem", ".vox"};

    /**
     * @return The ids of the models the files became, empty when copying failed, or null when the
     * files aren't a model at all.
     */
    public static List<String> importFiles(List<File> files)
    {
        List<File> folders = new ArrayList<>();
        List<File> loose = new ArrayList<>();

        for (File file : files)
        {
            (file.isDirectory() ? folders : loose).add(file);
        }

        File geometry = loose.stream().filter(ModelImporter::isGeometry).findFirst().orElse(null);

        /* Loose files are a model only with its geometry among them, folders only with one inside */
        if (files.isEmpty() || (!loose.isEmpty() && geometry == null) || !folders.stream().allMatch(ModelImporter::hasModel))
        {
            return null;
        }

        File models = BBSMod.getAssetsPath("models");
        List<String> ids = new ArrayList<>();

        try
        {
            for (File folder : folders)
            {
                File target = freeFolder(models, folder.getName());

                copyFolder(folder.toPath(), target.toPath());
                ids.add(target.getName());
            }

            if (geometry != null)
            {
                File target = freeFolder(models, geometryName(geometry));

                target.mkdirs();

                for (File file : loose)
                {
                    Files.copy(file.toPath(), new File(target, file.getName()).toPath());
                }

                ids.add(target.getName());
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if (!ids.isEmpty())
        {
            BBSResources.markAssetsChanged();
        }

        return ids;
    }

    private static boolean isGeometry(File file)
    {
        String name = file.getName().toLowerCase();

        for (String extension : GEOMETRY)
        {
            if (name.endsWith(extension))
            {
                return true;
            }
        }

        return false;
    }

    /** Whether the folder holds a model's geometry, at any depth. */
    private static boolean hasModel(File folder)
    {
        try (Stream<Path> walk = Files.walk(folder.toPath()))
        {
            return walk.anyMatch((path) -> isGeometry(path.toFile()));
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /** The geometry file's name without its extension: "pig.bbs.json" is the model "pig". */
    private static String geometryName(File file)
    {
        String name = file.getName();
        String lower = name.toLowerCase();

        for (String extension : GEOMETRY)
        {
            if (lower.endsWith(extension))
            {
                return name.substring(0, name.length() - extension.length());
            }
        }

        return name;
    }

    /**
     * The name itself, or with _1, _2... after it: one that neither a folder in the models folder
     * nor a model from elsewhere (the mod, a world, a pack) answers to already, as a world's model
     * would hide the new one.
     */
    private static File freeFolder(File models, String name)
    {
        List<String> taken = BBSModClient.getModels().getAvailableKeys();
        String candidate = name;
        int i = 1;

        while (new File(models, candidate).exists() || isTaken(taken, candidate))
        {
            candidate = name + "_" + i;
            i += 1;
        }

        return new File(models, candidate);
    }

    private static boolean isTaken(List<String> taken, String id)
    {
        for (String key : taken)
        {
            if (key.equals(id) || key.startsWith(id + "/"))
            {
                return true;
            }
        }

        return false;
    }

    private static void copyFolder(Path from, Path to) throws IOException
    {
        try (Stream<Path> walk = Files.walk(from))
        {
            for (Path path : (Iterable<Path>) walk::iterator)
            {
                Path target = to.resolve(from.relativize(path).toString());

                if (Files.isDirectory(path))
                {
                    Files.createDirectories(target);
                }
                else
                {
                    Files.copy(path, target);
                }
            }
        }
    }
}
