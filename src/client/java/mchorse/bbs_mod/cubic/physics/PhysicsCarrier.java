package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What an anchored form rides — its anchor's target frame (a car, a horse, a seat bone) — and how much of that
 * frame's motion the form's bone physics feels. The film lays it down each frame for the actor and its body
 * parts ({@link RenderFrame#setCarrier}); the solver carries the chains along with the share of the carrier's
 * motion they are not meant to feel, so a passenger's hair hangs still in a moving car and still swings to
 * whatever the passenger does inside it.
 *
 * @param position the carrier's origin, in the world space the chains live in
 * @param rotation the carrier's orientation, scale stripped
 * @param feel     share of the carrier's motion the physics feels: 0 rides the carrier fully, 1 is the plain world
 */
public record PhysicsCarrier(Vector3f position, Quaternionf rotation, float feel)
{
    public static PhysicsCarrier of(Matrix4f frame, float feel)
    {
        /* Unnormalized: the frame carries the target's scale (a resized car, a scaled anchor), which
         * getNormalizedRotation is not valid for and snaps on as the target turns — see the note in
         * CubicRenderer#collectPivotFrames. */
        return new PhysicsCarrier(frame.getTranslation(new Vector3f()), frame.getUnnormalizedRotation(new Quaternionf()), feel);
    }
}
