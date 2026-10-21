package mchorse.bbs_mod.cubic.physics;

/**
 * Empty on purpose: the physics debug overlay is gone (2026-10-20), but IRLights 1.2.0 names this
 * class in its BBS bridge's static initializer, and without the class that addon's model shadows
 * fail to load. Delete once IRLights stops naming it.
 */
public final class ModelPhysicsDebug
{
    private ModelPhysicsDebug()
    {
    }
}
