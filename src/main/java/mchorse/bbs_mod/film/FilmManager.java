package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.migration.FilmStableIds;
import mchorse.bbs_mod.data.migration.IDataMigration;
import mchorse.bbs_mod.film.crowds.CrowdMigration;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.manager.BaseManager;
import mchorse.bbs_mod.utils.manager.storage.CompressedDataStorage;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public class FilmManager extends BaseManager<Film>
{
    public FilmManager(Supplier<File> folder)
    {
        super(folder);

        this.backUps = true;
        this.storage = new CompressedDataStorage();
    }

    @Override
    protected List<IDataMigration> getMigrations()
    {
        return List.of(new FilmStableIds());
    }

    @Override
    protected Film createData(String id, MapType mapType)
    {
        Film film = new Film();

        if (mapType != null)
        {
            film.fromData(mapType);

            /* Films written before crowds were things the film owns carry them as spawn clips
             * on a replay's timeline instead. Converting on load means opening the film is the
             * whole of the upgrade, and that nothing downstream ever has to handle both shapes. */
            CrowdMigration.migrate(film);
        }

        return film;
    }

    @Override
    protected String getExtension()
    {
        return ".dat";
    }
}