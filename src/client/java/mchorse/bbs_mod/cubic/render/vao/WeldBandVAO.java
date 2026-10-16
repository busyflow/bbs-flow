package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.GeometryCache;
import mchorse.bbs_mod.cubic.render.WeldMesh;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The welded band of a model on the GPU: texture coordinates and triangles uploaded once, positions,
 * normals and tangents re-uploaded only when the pose changes. Drawn per bone and material through the
 * same path as the VAOs, so a bent bone keeps its materials, overlays and translucency.
 */
public class WeldBandVAO
{
    private final WeldMesh mesh;

    private final int texCoords;
    private final int elements;

    private final FloatBuffer positions;
    private final FloatBuffer normals;
    private final FloatBuffer tangents;

    private final GeometryCache<Entry> cache = new GeometryCache<>(Entry::new);

    public WeldBandVAO(WeldMesh mesh)
    {
        this.mesh = mesh;

        int currentBuffer = GL30.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);

        this.texCoords = GL30.glGenBuffers();
        this.elements = GL30.glGenBuffers();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.texCoords);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, mesh.texCoords, GL30.GL_STATIC_DRAW);

        /* Through the array target: binding an element buffer now would rebind it in whatever VAO is current. */
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.elements);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, mesh.indices, GL30.GL_STATIC_DRAW);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, currentBuffer);

        this.positions = BufferUtils.createFloatBuffer(Math.max(mesh.positions.length, 1));
        this.normals = BufferUtils.createFloatBuffer(Math.max(mesh.normals.length, 1));
        this.tangents = mesh.tangents == null ? null : BufferUtils.createFloatBuffer(Math.max(mesh.tangents.length, 1));
    }

    /**
     * The band in the model's current pose: the buffer that already holds it, or one it is posed and
     * uploaded into now. {@code cacheable} false re-poses every time (the frame cache is off).
     */
    public Entry update(Model model, boolean cacheable)
    {
        long epoch = RenderFrame.getEpoch();
        long key = this.mesh.key();
        Entry entry = cacheable ? this.cache.find(key, epoch) : null;

        if (entry != null)
        {
            return entry;
        }

        BBSProfiler.begin(BBSProfiler.Timer.WELDS);

        this.mesh.deform(model);

        entry = this.cache.acquire(key, epoch);

        int currentBuffer = GL30.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);

        upload(entry.position, this.positions, this.mesh.positions);
        upload(entry.normal, this.normals, this.mesh.normals);

        if (this.tangents != null)
        {
            upload(entry.tangent, this.tangents, this.mesh.tangents);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, currentBuffer);

        BBSProfiler.end(BBSProfiler.Timer.WELDS);

        return entry;
    }

    private static void upload(int buffer, FloatBuffer staging, float[] data)
    {
        staging.clear();
        staging.put(data);
        staging.flip();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, buffer);
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, staging);
    }

    /** Forget every posed band (the cubes or welds changed) — buffers stay, the queue may still hold one. */
    public void invalidate()
    {
        this.cache.invalidate();
    }

    public void delete()
    {
        this.cache.delete();

        GL30.glDeleteBuffers(this.texCoords);
        GL30.glDeleteBuffers(this.elements);
    }

    /** One posed band: its own dynamic buffers over the shared static ones, and a draw per bone and material. */
    public class Entry extends GeometryCache.Entry
    {
        private final int vao;
        private final int vao2;
        private final int position;
        private final int normal;
        private final int tangent;

        private final Map<ModelGroup, List<Draw>> draws = new HashMap<>();

        private Entry()
        {
            WeldMesh mesh = WeldBandVAO.this.mesh;
            int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int currentBuffer = GL30.glGetInteger(GL30.GL_ARRAY_BUFFER_BINDING);

            this.vao = GL30.glGenVertexArrays();
            this.vao2 = GL30.glGenVertexArrays();
            this.position = buffer(mesh.positions.length);
            this.normal = buffer(mesh.normals.length);
            this.tangent = buffer(mesh.tangents == null ? 4 : mesh.tangents.length);

            ModelVAO.bindAttributes(this.vao, this.vao2, this.position, this.normal, WeldBandVAO.this.texCoords, this.tangent, WeldBandVAO.this.texCoords, WeldBandVAO.this.elements);

            GL30.glBindVertexArray(currentVAO);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, currentBuffer);

            for (WeldMesh.Draw draw : mesh.draws)
            {
                this.draws.computeIfAbsent(draw.group(), (k) -> new ArrayList<>()).add(new Draw(this, draw));
            }
        }

        private static int buffer(int floats)
        {
            int buffer = GL30.glGenBuffers();

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, buffer);
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, Math.max(floats, 1) * 4L, GL30.GL_DYNAMIC_DRAW);

            return buffer;
        }

        /** The bone's band draws, one per material; empty for a bone without a band. */
        public List<Draw> draws(ModelGroup group)
        {
            List<Draw> draws = this.draws.get(group);

            return draws == null ? Collections.emptyList() : draws;
        }

        @Override
        protected void close()
        {
            GL30.glDeleteVertexArrays(this.vao);
            GL30.glDeleteVertexArrays(this.vao2);
            GL30.glDeleteBuffers(this.position);
            GL30.glDeleteBuffers(this.normal);
            GL30.glDeleteBuffers(this.tangent);
        }
    }

    /** A bone's band in one material, drawn like a {@link ModelVAO}. */
    public static class Draw implements IModelVAO
    {
        private final Entry entry;
        private final int offset;
        private final int count;

        public final String material;

        private Draw(Entry entry, WeldMesh.Draw draw)
        {
            this.entry = entry;
            this.offset = draw.offset();
            this.count = draw.count();
            this.material = draw.material();
        }

        /** The translucent queue replays this draw at the end of the frame — the buffer must hold the pose until then. */
        public void lend()
        {
            this.entry.lend(RenderFrame.getEpoch());
        }

        @Override
        public void render(VertexFormat format, float r, float g, float b, float a, int light, int overlay)
        {
            ModelVAO.bindForDraw(this.entry.vao, this.entry.vao2, format, r, g, b, a, light, overlay);

            GL30.glDrawElements(GL30.GL_TRIANGLES, this.count, GL30.GL_UNSIGNED_INT, this.offset * 4L);
            GL30.glBindVertexArray(0);
        }
    }
}
