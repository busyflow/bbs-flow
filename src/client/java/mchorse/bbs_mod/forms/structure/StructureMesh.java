package mchorse.bbs_mod.forms.structure;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.render.vao.Attributes;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The opaque layers of a {@link BakedStructure}, uploaded to the GPU once and drawn with one call
 * per layer, so a frame costs the same whatever the size of the structure. McHorse's structure
 * renderer does the same with vanilla's {@code VertexBuffer}; ours is raw GL because the buffer
 * has to be read through two different attribute layouts, see below.
 *
 * <p>All layers share one vertex buffer in the baked {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}
 * layout, each layer a range of it. Two vertex arrays read that buffer: one laid out for the
 * terrain programs, one for the entity ones (the shaderpack path, the color overlay pass and
 * picking), which put light and normal at other locations. A draw takes the one matching the
 * program that ended up bound, so a hook that swaps the program (picking) needs nothing extra.</p>
 *
 * <p>What changes per draw stays out of the buffer. The form color goes in as the color modulator.
 * Light has to be {@code max(baked block light, context block light)} with the context's sky
 * light, per vertex: when no vertex was lit by the structure's own emitters (the common case) or
 * the context outshines them all, that is one constant; otherwise it comes from a small stream
 * built per light value, the last few kept.</p>
 */
public class StructureMesh
{
    /* Locations of the block format's attributes (terrain programs); the entity ones are in Attributes */
    private static final int TERRAIN_LIGHT = 3;
    private static final int TERRAIN_NORMAL = 4;

    /** Light values a mesh keeps streams for; a structure seen in more lights rebuilds the oldest. */
    private static final int LIGHT_STREAMS = 4;

    /** Bytes per vertex of the shaderpack extras: mid texture coordinate (2 floats) + tangent (4 bytes). */
    private static final int EXTRA_STRIDE = 12;

    private final List<Range> ranges;
    private final int vertexCount;

    private final int vertexBuffer;
    private final int extraBuffer;
    private final int terrainVao;
    private final int entityVao;

    /** Block light every vertex was baked with, null when none was lit (then light is a constant). */
    private final byte[] bakedBlock;
    private final int maxBakedBlock;
    private final Map<Integer, Integer> lightStreams = new LinkedHashMap<>(8, 0.75F, true);

    private record Range(RenderLayer layer, int first, int count) {}

    private StructureMesh(List<Range> ranges, int vertexCount, int vertexBuffer, int extraBuffer, int terrainVao, int entityVao, byte[] bakedBlock, int maxBakedBlock)
    {
        this.ranges = ranges;
        this.vertexCount = vertexCount;
        this.vertexBuffer = vertexBuffer;
        this.extraBuffer = extraBuffer;
        this.terrainVao = terrainVao;
        this.entityVao = entityVao;
        this.bakedBlock = bakedBlock;
        this.maxBakedBlock = maxBakedBlock;
    }

    public static StructureMesh upload(List<BakedStructure.BakedLayer> layers)
    {
        int total = 0;

        for (BakedStructure.BakedLayer layer : layers)
        {
            total += layer.vertexCount();
        }

        /* Built whenever Iris is around: a pack can be switched on at any time, and the bake stays */
        boolean extras = BBSRendering.isIrisLoaded();
        ByteBuffer vertices = MemoryUtil.memAlloc(total * BakedBuffer.STRIDE);
        ByteBuffer extra = extras ? MemoryUtil.memAlloc(total * EXTRA_STRIDE) : null;

        try
        {
            List<Range> ranges = new ArrayList<>();
            int first = 0;

            for (BakedStructure.BakedLayer layer : layers)
            {
                vertices.put(first * BakedBuffer.STRIDE, layer.data(), 0, layer.vertexCount() * BakedBuffer.STRIDE);
                ranges.add(new Range(layer.layer(), first, layer.vertexCount()));

                first += layer.vertexCount();
            }

            byte[] bakedBlock = new byte[total];
            int maxBakedBlock = 0;

            for (int i = 0; i < total; i++)
            {
                int block = Math.min(vertices.getShort(i * BakedBuffer.STRIDE + 24) & 0xFFFF, 255);

                bakedBlock[i] = (byte) block;
                maxBakedBlock = Math.max(maxBakedBlock, block);
            }

            if (extra != null)
            {
                fillExtras(vertices, extra, total);
            }

            int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

            int vertexBuffer = GL15.glGenBuffers();
            int extraBuffer = 0;

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);

            if (extra != null)
            {
                extraBuffer = GL15.glGenBuffers();

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, extraBuffer);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, extra, GL15.GL_STATIC_DRAW);
            }

            int terrainVao = GL30.glGenVertexArrays();

            GL30.glBindVertexArray(terrainVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            setupCommon();
            GL20.glVertexAttribPointer(TERRAIN_NORMAL, 3, GL11.GL_BYTE, true, BakedBuffer.STRIDE, 28L);
            GL20.glEnableVertexAttribArray(TERRAIN_NORMAL);

            int entityVao = GL30.glGenVertexArrays();

            GL30.glBindVertexArray(entityVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            setupCommon();
            GL20.glVertexAttribPointer(Attributes.NORMAL, 3, GL11.GL_BYTE, true, BakedBuffer.STRIDE, 28L);
            GL20.glEnableVertexAttribArray(Attributes.NORMAL);

            if (extraBuffer != 0)
            {
                /* Laid out the way Iris' own entity format declares them, which is what the
                 * replayed structure used to get from its buffer builder */
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, extraBuffer);
                GL20.glVertexAttribPointer(Attributes.MID_TEXTURE_UV, 2, GL11.GL_FLOAT, false, EXTRA_STRIDE, 0L);
                GL20.glVertexAttribPointer(Attributes.TANGENTS, 4, GL11.GL_BYTE, false, EXTRA_STRIDE, 8L);
                GL20.glEnableVertexAttribArray(Attributes.MID_TEXTURE_UV);
                GL20.glEnableVertexAttribArray(Attributes.TANGENTS);
            }

            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);

            return new StructureMesh(ranges, total, vertexBuffer, extraBuffer, terrainVao, entityVao, maxBakedBlock > 0 ? bakedBlock : null, maxBakedBlock);
        }
        finally
        {
            MemoryUtil.memFree(vertices);

            if (extra != null)
            {
                MemoryUtil.memFree(extra);
            }
        }
    }

    /** Position, color and texture: the same locations in both layouts. */
    private static void setupCommon()
    {
        GL20.glVertexAttribPointer(Attributes.POSITION, 3, GL11.GL_FLOAT, false, BakedBuffer.STRIDE, 0L);
        GL20.glVertexAttribPointer(Attributes.COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, BakedBuffer.STRIDE, 12L);
        GL20.glVertexAttribPointer(Attributes.TEXTURE_UV, 2, GL11.GL_FLOAT, false, BakedBuffer.STRIDE, 16L);
        GL20.glEnableVertexAttribArray(Attributes.POSITION);
        GL20.glEnableVertexAttribArray(Attributes.COLOR);
        GL20.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
    }

    /**
     * What Iris' buffer builder adds to an entity quad: the mid texture coordinate, the face normal
     * from the positions (written over the baked one) and the tangent for it. Shaderpacks read
     * them for normal and parallax maps.
     */
    private static void fillExtras(ByteBuffer vertices, ByteBuffer extra, int count)
    {
        float[] x = new float[4];
        float[] y = new float[4];
        float[] z = new float[4];
        float[] u = new float[4];
        float[] v = new float[4];

        for (int quad = 0; quad + 3 < count; quad += 4)
        {
            float midU = 0F;
            float midV = 0F;

            for (int i = 0; i < 4; i++)
            {
                int base = (quad + i) * BakedBuffer.STRIDE;

                x[i] = vertices.getFloat(base);
                y[i] = vertices.getFloat(base + 4);
                z[i] = vertices.getFloat(base + 8);
                u[i] = vertices.getFloat(base + 16);
                v[i] = vertices.getFloat(base + 20);

                midU += u[i] / 4F;
                midV += v[i] / 4F;
            }

            /* Cross of the diagonals, as Iris computes a quad's face normal */
            float ax = x[2] - x[0], ay = y[2] - y[0], az = z[2] - z[0];
            float bx = x[3] - x[1], by = y[3] - y[1], bz = z[3] - z[1];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            int first = quad * BakedBuffer.STRIDE;

            if (length > 0F)
            {
                nx /= length;
                ny /= length;
                nz /= length;
            }
            else
            {
                nx = vertices.get(first + 28) / 127F;
                ny = vertices.get(first + 29) / 127F;
                nz = vertices.get(first + 30) / 127F;
            }

            int tangent = BBSRendering.computeTangent(nx, ny, nz,
                x[0], y[0], z[0], u[0], v[0],
                x[1], y[1], z[1], u[1], v[1],
                x[2], y[2], z[2], u[2], v[2]);

            for (int i = 0; i < 4; i++)
            {
                int base = (quad + i) * BakedBuffer.STRIDE;
                int extraBase = (quad + i) * EXTRA_STRIDE;

                vertices.put(base + 28, (byte) (nx * 127F));
                vertices.put(base + 29, (byte) (ny * 127F));
                vertices.put(base + 30, (byte) (nz * 127F));

                extra.putFloat(extraBase, midU);
                extra.putFloat(extraBase + 4, midV);
                extra.putInt(extraBase + 8, tangent);
            }
        }
    }

    /** Draw every range through one layer: the entity cutout under a shaderpack, the overlay pass. */
    public void draw(RenderLayer layer, MatrixStack.Entry entry, int light, int tint, Consumer<RenderLayer> hook)
    {
        this.draw(layer, 0, this.vertexCount, entry, light, tint, hook);
    }

    /** Draw each range through the block layer it was baked for (vanilla terrain shading). */
    public void drawLayers(MatrixStack.Entry entry, int light, int tint, Consumer<RenderLayer> hook)
    {
        for (Range range : this.ranges)
        {
            this.draw(range.layer(), range.first(), range.count(), entry, light, tint, hook);
        }
    }

    private void draw(RenderLayer layer, int first, int count, MatrixStack.Entry entry, int light, int tint, Consumer<RenderLayer> hook)
    {
        layer.startDrawing();

        try
        {
            if (hook != null)
            {
                hook.accept(layer);
            }

            ShaderProgram shader = RenderSystem.getShader();

            if (shader == null)
            {
                return;
            }

            boolean entity = shader.getFormat().getElements().contains(VertexFormats.OVERLAY_ELEMENT);
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(entry.getPositionMatrix());

            ModelVAORenderer.setupUniforms(shader, modelView, entry.getNormalMatrix());

            if (shader.colorModulator != null)
            {
                shader.colorModulator.set((tint >> 16 & 0xFF) / 255F, (tint >> 8 & 0xFF) / 255F, (tint & 0xFF) / 255F, (tint >>> 24) / 255F);
            }

            if (shader.chunkOffset != null)
            {
                shader.chunkOffset.set(0F, 0F, 0F);
            }

            int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
            int indexCount = count / 4 * 6;

            shader.bind();
            GL30.glBindVertexArray(entity ? this.entityVao : this.terrainVao);
            this.bindLight(entity ? Attributes.LIGHTMAP_UV : TERRAIN_LIGHT, light);

            if (entity)
            {
                GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, OverlayTexture.DEFAULT_UV & 0xFFFF, OverlayTexture.DEFAULT_UV >> 16 & 0xFFFF);
            }

            /* Binding the shared quad index buffer here stores it in our vertex array */
            indices.bindAndGrow(indexCount);
            GL32.glDrawElementsBaseVertex(GL11.GL_TRIANGLES, indexCount, indices.getIndexType().glType, 0L, first);

            shader.unbind();
            GL30.glBindVertexArray(previousVao);
        }
        finally
        {
            layer.endDrawing();
        }
    }

    private void bindLight(int location, int light)
    {
        int block = light & 0xFFFF;
        int sky = light >> 16 & 0xFFFF;

        if (this.bakedBlock == null || block >= this.maxBakedBlock)
        {
            GL20.glDisableVertexAttribArray(location);
            GL30.glVertexAttribI2i(location, block, sky);

            return;
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.getLightStream(block, sky));
        GL30.glVertexAttribIPointer(location, 2, GL11.GL_SHORT, 4, 0L);
        GL20.glEnableVertexAttribArray(location);
    }

    private int getLightStream(int block, int sky)
    {
        Integer key = block << 16 | sky;
        Integer stream = this.lightStreams.get(key);

        if (stream != null)
        {
            return stream;
        }

        if (this.lightStreams.size() >= LIGHT_STREAMS)
        {
            Iterator<Integer> it = this.lightStreams.values().iterator();

            GL15.glDeleteBuffers(it.next());
            it.remove();
        }

        ByteBuffer data = MemoryUtil.memAlloc(this.vertexCount * 4);

        try
        {
            for (int i = 0; i < this.vertexCount; i++)
            {
                data.putShort(i * 4, (short) Math.max(this.bakedBlock[i] & 0xFF, block));
                data.putShort(i * 4 + 2, (short) sky);
            }

            int buffer = GL15.glGenBuffers();

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            this.lightStreams.put(key, buffer);

            return buffer;
        }
        finally
        {
            MemoryUtil.memFree(data);
        }
    }

    public void delete()
    {
        GL30.glDeleteVertexArrays(this.terrainVao);
        GL30.glDeleteVertexArrays(this.entityVao);
        GL15.glDeleteBuffers(this.vertexBuffer);

        if (this.extraBuffer != 0)
        {
            GL15.glDeleteBuffers(this.extraBuffer);
        }

        for (int stream : this.lightStreams.values())
        {
            GL15.glDeleteBuffers(stream);
        }

        this.lightStreams.clear();
    }
}
