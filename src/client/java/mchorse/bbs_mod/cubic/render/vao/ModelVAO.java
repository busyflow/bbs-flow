package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL30;

public class ModelVAO implements IModelVAO
{
    private int vao;
    private int vao2;
    private int[] buffers;
    private int count;

    public ModelVAO(ModelVAOData data)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        this.upload(data);

        GL30.glBindVertexArray(currentVAO);
    }

    public void delete()
    {
        GL30.glDeleteVertexArrays(this.vao);
        GL30.glDeleteVertexArrays(this.vao2);
        /* Deleting a VAO doesn't delete the buffers it points at - without this every
         * model reload left its whole geometry in video memory */
        GL30.glDeleteBuffers(this.buffers);
    }

    public void upload(ModelVAOData data)
    {
        this.vao = GL30.glGenVertexArrays();
        this.vao2 = GL30.glGenVertexArrays();

        int vertexBuffer = GL30.glGenBuffers();
        int normalBuffer = GL30.glGenBuffers();
        int tangentsBuffer = GL30.glGenBuffers();
        int texCoordBuffer = GL30.glGenBuffers();
        int midTexCoordBuffer = GL30.glGenBuffers();

        this.buffers = new int[] {vertexBuffer, normalBuffer, tangentsBuffer, texCoordBuffer, midTexCoordBuffer};

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vertexBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.vertices(), GL30.GL_STATIC_DRAW);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, normalBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.normals(), GL30.GL_STATIC_DRAW);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, texCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.texCoords(), GL30.GL_STATIC_DRAW);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, tangentsBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.tangents(), GL30.GL_STATIC_DRAW);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, midTexCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.texCoords(), GL30.GL_STATIC_DRAW);

        bindAttributes(this.vao, this.vao2, vertexBuffer, normalBuffer, texCoordBuffer, tangentsBuffer, midTexCoordBuffer, 0);

        this.count = data.vertices().length / 3;
    }

    @Override
    public void render(VertexFormat format, float r, float g, float b, float a, int light, int overlay)
    {
        bindForDraw(this.vao, this.vao2, format, r, g, b, a, light, overlay);

        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);

        if (!ModelVAORenderer.isInPass())
        {
            GL30.glBindVertexArray(0);
        }
    }

    /**
     * Lay out a model VAO pair over its attribute buffers: {@code vao} in the full model format (the
     * BBS model shader, Iris), {@code vao2} in {@code POSITION_TEXTURE_LIGHT_COLOR}. Colour, overlay and
     * light are per-draw constants (see {@link #bindForDraw}), never arrays. A non-zero
     * {@code elements} buffer makes both VAOs indexed. Leaves {@code vao2} bound.
     */
    public static void bindAttributes(int vao, int vao2, int position, int normal, int texCoord, int tangents, int midTexCoord, int elements)
    {
        GL30.glBindVertexArray(vao);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, position);
        GL30.glVertexAttribPointer(Attributes.POSITION, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, normal);
        GL30.glVertexAttribPointer(Attributes.NORMAL, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, texCoord);
        GL30.glVertexAttribPointer(Attributes.TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, tangents);
        GL30.glVertexAttribPointer(Attributes.TANGENTS, 4, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, midTexCoord);
        GL30.glVertexAttribPointer(Attributes.MID_TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glEnableVertexAttribArray(Attributes.POSITION);
        GL30.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glEnableVertexAttribArray(Attributes.NORMAL);

        GL30.glDisableVertexAttribArray(Attributes.COLOR);
        GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);
        GL30.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);
        GL30.glDisableVertexAttribArray(Attributes.TANGENTS);
        GL30.glDisableVertexAttribArray(Attributes.MID_TEXTURE_UV);

        if (elements != 0)
        {
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, elements);
        }

        /* VertexFormats.POSITION_TEXTURE_LIGHT_COLOR */
        GL30.glBindVertexArray(vao2);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, position);
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, texCoord);
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glEnableVertexAttribArray(0);
        GL30.glEnableVertexAttribArray(1);
        GL30.glDisableVertexAttribArray(2);
        GL30.glDisableVertexAttribArray(3);

        if (elements != 0)
        {
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, elements);
        }
    }

    /** Bind the VAO of the pair that matches the shader's format and set the draw's colour, light and overlay. */
    public static void bindForDraw(int vao, int vao2, VertexFormat format, float r, float g, float b, float a, int light, int overlay)
    {
        boolean hasShaders = isShadersEnabled();
        int bound = hasShaders || format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL ? vao : vao2;

        GL30.glBindVertexArray(bound);

        if (bound == vao)
        {
            GL30.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
            GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & '\uffff', overlay >> 16 & '\uffff');
            GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, light & '\uffff', light >> 16 & '\uffff');
        }
        else
        {
            GL30.glVertexAttribI2i(2, light & '\uffff', light >> 16 & '\uffff');
            GL30.glVertexAttrib4f(3, r, g, b, a);
        }

        if (hasShaders) GL30.glEnableVertexAttribArray(Attributes.MID_TEXTURE_UV);
        else GL30.glDisableVertexAttribArray(Attributes.MID_TEXTURE_UV);

        if (hasShaders) GL30.glEnableVertexAttribArray(Attributes.TANGENTS);
        else GL30.glDisableVertexAttribArray(Attributes.TANGENTS);
    }

    public static boolean isShadersEnabled()
    {
        return BBSRendering.isIrisShadersEnabled();
    }
}
