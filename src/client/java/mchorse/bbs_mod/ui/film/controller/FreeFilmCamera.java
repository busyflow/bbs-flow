package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;

import java.util.HashMap;
import java.util.Map;

/**
 * The free mode's own place, one per film. Free mode used to leave the view wherever the last
 * thing to touch it had put it — the actor's head after first person, the player's eyes after
 * the dashboard opened — so a view lined up by flying was gone after any mode switch or any trip
 * out of the editor. Now the place stays until it is flown somewhere else, and going back to the
 * player is asked for explicitly ({@link UIFilmController#cameraToPlayer()}).
 *
 * <p>Kept for as long as the editor lives: the dashboard is rebuilt with every world, and a
 * place from another world would point at nothing.</p>
 */
public class FreeFilmCamera
{
    private final UIFilmController controller;
    private final Map<String, Position> places = new HashMap<>();

    public FreeFilmCamera(UIFilmController controller)
    {
        this.controller = controller;
    }

    /**
     * Free mode's turn at the camera. A flight carries the place along; otherwise the place is put
     * back into the view. A film seen in free mode for the first time takes the view as it finds
     * it, so that first switch looks the way it always did.
     */
    public void setup(Camera camera)
    {
        Film film = this.controller.panel.getData();

        if (film == null)
        {
            return;
        }

        Position place = this.places.get(film.getId());

        if (place == null)
        {
            this.places.put(film.getId(), new Position(camera));
        }
        else if (this.controller.panel.isFlying())
        {
            place.set(camera);
        }
        else
        {
            place.apply(camera);
        }
    }

    /** Moves the current film's place to where the given camera is. */
    public void moveTo(Camera camera)
    {
        Film film = this.controller.panel.getData();

        if (film != null)
        {
            this.places.computeIfAbsent(film.getId(), (id) -> new Position()).set(camera);
        }
    }
}
