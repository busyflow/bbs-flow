package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.utils.MathUtils;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pre-tesselated structure geometry: all blocks + fluids are run through the vanilla renderer
 * ONCE (smooth AO and biome tint get baked into vertex colors). The opaque layers then live on
 * the GPU ({@link StructureMesh}), one draw call each per frame whatever the size of the
 * structure. The translucent layers stay here and are replayed into the provider every frame:
 * glass and water have to be sorted back to front against everything else, and a static buffer
 * can't be.
 *
 * <p>Bakes are shared, one per (structure, biome) pair however many forms show it, and kept by
 * {@link #get} until nobody drew one for {@link #EXPIRE_MS}: a form has no end of life to free
 * GPU memory on, so the cache does it. All of them are dropped when resources reload
 * ({@link #invalidateAll()}, hooked to Fabric's render-state invalidation: baked sprite UVs go
 * stale when atlases rebuild) and when {@link StructureManager} drops its own cache.</p>
 */
public class BakedStructure
{
    /**
     * Where the color overlay pass draws. What eliminated the alternatives, in order:
     *
     * <ul>
     * <li>its shader must read the overlay channel. The terrain shaders the structure normally
     * draws through have no such channel at all, and neither does {@code entity_translucent_cull},
     * the layer its own translucent blocks go to;</li>
     * <li>its name must not contain "translucent", or the provider defers it into the frame's
     * sorted translucent queue — which draws it long after the overlay texture was unbound;</li>
     * <li>it must cut out on the raw texel alpha, so leaves and plants keep their shape at any
     * overlay strength (a cutout shader tests the texel before the vertex color reaches it).</li>
     * </ul>
     *
     * <p>Blending and back-face culling are turned back on for the draw by the renderer, through
     * the same per-layer hook that binds the overlay texture: the pass composites rather than
     * replaces, and a plant's double-sided cross must not be painted twice.</p>
     */
    public static final RenderLayer OVERLAY_LAYER = RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);

    /** How long a bake nobody draws stays on the GPU. */
    private static final long EXPIRE_MS = 60_000L;

    private static final Map<Key, BakedStructure> CACHE = new HashMap<>();

    private static int generation;
    private static int cacheGeneration = -1;
    private static int cacheManagerGeneration = -1;
    private static long lastSweep;

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Scratch builder reused across bakes (grows once and stays). */
    private static BufferBuilder scratch;

    /** The opaque layers on the GPU; null when the structure has none. */
    private final StructureMesh mesh;

    /** The translucent layers, replayed (and so sorted) every frame. */
    private final List<BakedLayer> translucent;

    /** Sprites referenced by the baked geometry — marked active for Sodium every frame. */
    private final Set<Sprite> sprites;

    private long lastUsed;

    record BakedLayer(RenderLayer layer, ByteBuffer data, int vertexCount) {}

    /** Structure data by identity: parts of addons carry their own, the manager hands out one per id. */
    private record Key(StructureRenderData data, String biome) {}

    private BakedStructure(StructureMesh mesh, List<BakedLayer> translucent, Set<Sprite> sprites)
    {
        this.mesh = mesh;
        this.translucent = translucent;
        this.sprites = sprites;
    }

    public static void invalidateAll()
    {
        generation += 1;
    }

    /** The bake of this structure in this biome, made now if nobody has it. Render thread only. */
    public static BakedStructure get(StructureRenderData data, String biome)
    {
        int managerGeneration = StructureManager.getGeneration();

        if (generation != cacheGeneration || managerGeneration != cacheManagerGeneration)
        {
            cacheGeneration = generation;
            cacheManagerGeneration = managerGeneration;

            for (BakedStructure baked : CACHE.values())
            {
                baked.delete();
            }

            CACHE.clear();
        }

        long now = Util.getMeasuringTimeMs();

        sweep(now);

        Key key = new Key(data, biome);
        BakedStructure baked = CACHE.get(key);

        if (baked == null)
        {
            baked = bake(data, new StructureRenderWorld(data, biome));
            CACHE.put(key, baked);
        }

        baked.lastUsed = now;

        return baked;
    }

    /** Free the bakes nobody drew for a while (checked once a second). */
    private static void sweep(long now)
    {
        if (now - lastSweep < 1000L)
        {
            return;
        }

        lastSweep = now;

        Iterator<BakedStructure> it = CACHE.values().iterator();

        while (it.hasNext())
        {
            BakedStructure baked = it.next();

            if (now - baked.lastUsed > EXPIRE_MS)
            {
                baked.delete();
                it.remove();
            }
        }
    }

    private void delete()
    {
        if (this.mesh != null)
        {
            this.mesh.delete();
        }
    }

    private static BakedStructure bake(StructureRenderData data, StructureRenderWorld world)
    {
        BlockRenderManager manager = MinecraftClient.getInstance().getBlockRenderManager();
        Random random = Random.create();
        MatrixStack matrices = new MatrixStack();
        TransformingVertexConsumer fluidConsumer = new TransformingVertexConsumer(new Matrix4f(), new Matrix3f());
        Set<Sprite> sprites = new HashSet<>();
        List<BakedLayer> opaque = new ArrayList<>();
        List<BakedLayer> translucent = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockState> e : data.getBlocks().entrySet())
        {
            collectSprites(sprites, manager, world, e.getKey(), e.getValue(), random);
        }

        for (RenderLayer layer : RenderLayer.getBlockLayers())
        {
            BufferBuilder builder = beginBuffer(layer.getDrawMode(), layer.getVertexFormat());

            for (Map.Entry<BlockPos, BlockState> e : data.getBlocks().entrySet())
            {
                BlockPos pos = e.getKey();
                BlockState state = e.getValue();
                FluidState fluid = state.getFluidState();

                if (!fluid.isEmpty() && RenderLayers.getFluidLayer(fluid) == layer)
                {
                    fluidConsumer.target(builder, pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
                    manager.renderFluid(pos, world, fluidConsumer, state, fluid);
                }

                if (state.getRenderType() == BlockRenderType.MODEL && RenderLayers.getBlockLayer(state) == layer)
                {
                    matrices.push();
                    matrices.translate(pos.getX(), pos.getY(), pos.getZ());
                    manager.renderBlock(state, pos, world, matrices, builder, true, random);
                    matrices.pop();
                }
            }

            /* End + repack into a tight POSITION_COLOR_TEXTURE_LIGHT_NORMAL buffer. */
            BakedBuffer baked = endAndNormalize(builder);

            if (baked == null)
            {
                continue;
            }

            if (baked.data() == null)
            {
                /* Format missed standard block attributes — skip the layer */
                continue;
            }

            ByteBuffer copy = baked.data();
            int vertexCount = baked.vertexCount();

            if (isTranslucent(layer))
            {
                translucent.add(new BakedLayer(layer, copy, vertexCount));
            }
            else
            {
                /* Re-impose vanilla's opaque-block invariant (see forceOpaque) */
                forceOpaque(copy, vertexCount);
                opaque.add(new BakedLayer(layer, copy, vertexCount));
            }
        }

        return new BakedStructure(opaque.isEmpty() ? null : StructureMesh.upload(opaque), translucent, sprites);
    }

    /** Remember which sprites the block/fluid at this position uses (for Sodium animation). */
    private static void collectSprites(Set<Sprite> sprites, BlockRenderManager manager, StructureRenderWorld world, BlockPos pos, BlockState state, Random random)
    {
        if (state.getRenderType() == BlockRenderType.MODEL)
        {
            BakedModel model = manager.getModel(state);

            random.setSeed(state.getRenderingSeed(pos));

            for (Direction direction : DIRECTIONS)
            {
                for (BakedQuad quad : model.getQuads(state, direction, random))
                {
                    sprites.add(quad.getSprite());
                }
            }

            for (BakedQuad quad : model.getQuads(state, null, random))
            {
                sprites.add(quad.getSprite());
            }
        }

        FluidState fluid = state.getFluidState();

        if (!fluid.isEmpty())
        {
            FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getFluid());

            if (handler != null)
            {
                for (Sprite sprite : handler.getFluidSprites(world, pos, fluid))
                {
                    if (sprite != null)
                    {
                        sprites.add(sprite);
                    }
                }
            }
        }
    }

    /** Re-impose vanilla's opaque-block invariant: set every vertex's color alpha to {@code 0xFF}.
     *
     * <p>Vanilla's {@code BlockModelRenderer} always writes alpha 1.0 there, but with Continuity
     * installed the bake is serviced by an FRAPI renderer (Indium), and under Iris with separate-AO
     * that path stuffs the AO coefficient into the alpha byte instead of opacity. A faded draw,
     * which blends, would otherwise read that as transparency and the whole structure turns
     * see-through. The form/film tint alpha is applied separately at draw time, so resetting the
     * baked alpha here is safe (opaque layers carry no meaningful per-vertex alpha anyway).</p> */
    private static void forceOpaque(ByteBuffer copy, int count)
    {
        for (int i = 0; i < count; i++)
        {
            copy.put(i * BakedBuffer.STRIDE + 15, (byte) 0xFF);
        }
    }

    /** Layers whose per-vertex alpha is real opacity; everything else is opaque (alpha ignorable). */
    private static boolean isTranslucent(RenderLayer layer)
    {
        return layer == RenderLayer.getTranslucent() || layer == RenderLayer.getTripwire();
    }

    /**
     * Whether the structure's opaque geometry is drawn through an entity layer instead of the
     * terrain ones (see {@link #render}). Asked of what is shading this very draw rather than of
     * whether a pack is loaded: in the form editor's preview or inside a framebuffer form the pack
     * stops shading, and the plain entity programs there would re-shade the structure.
     *
     * <p>The color overlay follows from it: an entity layer's shader mixes the bound overlay into
     * the fragment in place, so {@link #renderOverlay} would double it there, while the terrain
     * layers have no overlay channel at all and the pass is the only way to get one.</p>
     */
    public static boolean usesEntityLayers()
    {
        return BBSRendering.isIrisWorldShadersEnabled();
    }

    /**
     * Draw the structure, its geometry transformed by the given matrices and its colors multiplied
     * by {@code tint} (ARGB; the form/film color).
     *
     * <p>Transparency only needs the right draw ORDER against the shared depth buffer: opaque must
     * land (writing depth) before translucent draws over it. The opaque layers draw right away from
     * the GPU, through the terrain programs — they apply no directional diffuse, so the smooth AO /
     * face shade baked into the vertex colors shows as-is (entity layers would re-shade and darken
     * the whole structure). The translucent ones go to BBS's KEYED entity translucent-cull layer,
     * which the provider draws later, so glass/water/ice composite over the blocks behind them.
     * Under a shaderpack Iris owns the terrain pipeline and relights everything itself, so the
     * opaque layers go through the entity cutout program instead (no double diffuse there).</p>
     *
     * <p>Light: the sky component comes from {@code contextLight} (the form's world/entity
     * light, already modulated by the {@code lighting} form property), the block component is
     * the max of context and baked light — so the structure darkens in caves/at night like any
     * other form, while baked emitters (glowstone, lamps) keep glowing. UI previews pass
     * {@code MAX_LIGHT_COORDINATE} which makes everything full-bright.</p>
     *
     * @param hook runs once a layer has applied its own state, right before it draws: the caller's
     *        per-layer setup (fade blending, color overlay, picking program). The GPU layers call it
     *        directly; the replayed ones get it from the provider, so the caller hands the same one
     *        to {@link CustomVertexConsumerProvider#hijackVertexFormat} as well. May be null.
     */
    public void render(MatrixStack.Entry entry, CustomVertexConsumerProvider consumers, int contextLight, int tint, Consumer<RenderLayer> hook)
    {
        /* Sodium only animates sprites it saw this frame — baked geometry bypasses it */
        SodiumSpriteHook.markActive(this.sprites);

        if (this.mesh != null)
        {
            if (usesEntityLayers())
            {
                this.mesh.draw(TexturedRenderLayers.getEntityCutout(), entry, contextLight, tint, hook);
            }
            else
            {
                this.mesh.drawLayers(entry, contextLight, tint, hook);
            }
        }

        if (!this.translucent.isEmpty())
        {
            VertexConsumer out = consumers.getBuffer(TexturedRenderLayers.getEntityTranslucentCull());

            for (BakedLayer baked : this.translucent)
            {
                replay(out, baked, entry, contextLight, tint);
            }
        }
    }

    /**
     * The color overlay pass: the same geometry drawn once more through {@link #OVERLAY_LAYER},
     * where the fragment takes its color entirely from the overlay texture the caller bound (at
     * full strength) and {@code strength} rides in the alpha — so the pass composites into
     * {@code mix(structure, overlay, strength)} over what {@link #render} just drew.
     *
     * <p>A second pass is what the overlay costs here: the terrain shaders have no overlay
     * channel to mix the color in place, and rerouting the structure to an entity layer to get
     * one would re-shade the whole thing (see {@link #render}). The pass itself is immune to that
     * re-shading — the overlay texture overwrites the fragment's RGB, so the entity layer's
     * directional diffuse never reaches it. Only alpha survives, which is why the baked per-vertex
     * alpha (real opacity on the translucent layer) still scales the strength.</p>
     *
     * @param hook see {@link #render}
     */
    public void renderOverlay(MatrixStack.Entry entry, CustomVertexConsumerProvider consumers, int contextLight, float strength, Consumer<RenderLayer> hook)
    {
        int alpha = (int) (MathUtils.clamp(strength, 0F, 1F) * 255F);

        if (alpha <= 0)
        {
            return;
        }

        SodiumSpriteHook.markActive(this.sprites);

        /* White RGB: this pass carries the strength and nothing else — the color arrives through
         * the bound overlay texture, which the layer's shader writes over the fragment */
        int tint = (alpha << 24) | 0xFFFFFF;

        if (this.mesh != null)
        {
            this.mesh.draw(OVERLAY_LAYER, entry, contextLight, tint, hook);
        }

        if (!this.translucent.isEmpty())
        {
            VertexConsumer out = consumers.getBuffer(OVERLAY_LAYER);

            for (BakedLayer baked : this.translucent)
            {
                replay(out, baked, entry, contextLight, tint);
            }
        }
    }

    private static void replay(VertexConsumer out, BakedLayer baked, MatrixStack.Entry entry, int contextLight, int tint)
    {
        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        int contextBlock = contextLight & 0xFFFF;
        int contextSky = (contextLight >> 16) & 0xFFFF;
        Vector4f position = new Vector4f();
        Vector3f normal = new Vector3f();
        ByteBuffer buf = baked.data();
        int count = baked.vertexCount();

        int tintA = tint >>> 24;
        int tintR = (tint >> 16) & 0xFF;
        int tintG = (tint >> 8) & 0xFF;
        int tintB = tint & 0xFF;

        for (int i = 0; i < count; i++)
        {
            int base = i * BakedBuffer.STRIDE;

            position.set(buf.getFloat(base), buf.getFloat(base + 4), buf.getFloat(base + 8), 1F);
            pose.transform(position);

            int r = (buf.get(base + 12) & 0xFF) * tintR / 255;
            int g = (buf.get(base + 13) & 0xFF) * tintG / 255;
            int b = (buf.get(base + 14) & 0xFF) * tintB / 255;
            int a = (buf.get(base + 15) & 0xFF) * tintA / 255;

            float u = buf.getFloat(base + 16);
            float v = buf.getFloat(base + 20);

            int bakedBlock = buf.getInt(base + 24) & 0xFFFF;

            normal.set(buf.get(base + 28) / 127F, buf.get(base + 29) / 127F, buf.get(base + 30) / 127F);
            normalMatrix.transform(normal);

            emitVertex(out, position.x, position.y, position.z, r, g, b, a, u, v,
                OverlayTexture.DEFAULT_UV, Math.max(bakedBlock, contextBlock), contextSky, normal.x, normal.y, normal.z);
        }
    }

    /** Begin a scratch vertex buffer for the given draw mode + format (a single reused builder). */
    private static BufferBuilder beginBuffer(VertexFormat.DrawMode mode, VertexFormat format)
    {
        if (scratch == null)
        {
            scratch = new BufferBuilder(786432);
        }

        scratch.begin(mode, format);

        return scratch;
    }

    /** End the builder and copy its vertices into a tight {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}
     *  ({@link BakedBuffer#STRIDE}-byte) template. Returns null if the builder was empty; a
     *  {@link BakedBuffer} with null data if the format misses standard block attributes. */
    private static BakedBuffer endAndNormalize(BufferBuilder builder)
    {
        BufferBuilder.BuiltBuffer built = builder.endNullable();

        if (built == null)
        {
            return null;
        }

        BufferBuilder.DrawParameters parameters = built.getParameters();
        int count = parameters.vertexCount();
        ByteBuffer copy = normalize(built.getVertexBuffer(), parameters.format(), count);

        built.release();

        return new BakedBuffer(copy, count);
    }

    /**
     * Copy the built vertex data into a tightly packed {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}
     * template. With an Iris shaderpack active the builder's actual format is EXTENDED (bigger
     * stride, extra attributes appended), so the vanilla attributes are extracted by their real
     * offsets; returns null if the format misses any of them.
     *
     * <p>The copy lives on the heap: the opaque layers only pass through it on their way to the
     * GPU, and the translucent ones are read with absolute {@code get}s, which a heap buffer
     * serves just as well as an off-heap one, without a second memory budget to exhaust.</p>
     */
    private static ByteBuffer normalize(ByteBuffer source, VertexFormat format, int count)
    {
        int stride = format.getVertexSizeByte();
        int base = source.position();
        ByteBuffer copy = ByteBuffer.allocate(count * BakedBuffer.STRIDE).order(ByteOrder.nativeOrder());

        if (stride == BakedBuffer.STRIDE && VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.equals(format))
        {
            copy.put(0, source, base, count * BakedBuffer.STRIDE);

            return copy;
        }

        int posOffset = -1;
        int colorOffset = -1;
        int uvOffset = -1;
        int lightOffset = -1;
        int normalOffset = -1;
        int offset = 0;

        for (VertexFormatElement element : format.getElements())
        {
            if (element == VertexFormats.POSITION_ELEMENT) posOffset = offset;
            else if (element == VertexFormats.COLOR_ELEMENT) colorOffset = offset;
            else if (element == VertexFormats.TEXTURE_ELEMENT) uvOffset = offset;
            else if (element == VertexFormats.LIGHT_ELEMENT) lightOffset = offset;
            else if (element == VertexFormats.NORMAL_ELEMENT) normalOffset = offset;

            offset += element.getByteLength();
        }

        if (posOffset < 0 || colorOffset < 0 || uvOffset < 0 || lightOffset < 0 || normalOffset < 0)
        {
            return null;
        }

        for (int i = 0; i < count; i++)
        {
            int src = base + i * stride;
            int dst = i * BakedBuffer.STRIDE;

            copy.put(dst, source, src + posOffset, 12);
            copy.put(dst + 12, source, src + colorOffset, 4);
            copy.put(dst + 16, source, src + uvOffset, 8);
            copy.put(dst + 24, source, src + lightOffset, 4);
            copy.put(dst + 28, source, src + normalOffset, 3);
        }

        return copy;
    }

    /** Emit one fully-specified vertex (the explicit {@code next()} terminator finishes it). */
    private static void emitVertex(VertexConsumer out, float x, float y, float z, int r, int g, int b, int a,
        float u, float v, int overlay, int blockLight, int skyLight, float nx, float ny, float nz)
    {
        out.vertex(x, y, z)
            .color(r, g, b, a)
            .texture(u, v)
            .overlay(overlay)
            .light(blockLight, skyLight)
            .normal(nx, ny, nz)
            .next();
    }
}
