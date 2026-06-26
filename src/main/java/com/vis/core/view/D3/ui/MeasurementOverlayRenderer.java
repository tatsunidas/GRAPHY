package com.vis.core.view.D3.ui;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

import java.nio.FloatBuffer;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * Draws 3D measurement lines and point markers in GL render space.
 * Text labels are rendered by the caller via Java2D in drawOverlay().
 */
public class MeasurementOverlayRenderer {

    private int programId;
    private int vaoId, vboId;
    private int uMvpLoc, uColorLoc;

    private static final String VERT =
        "#version 330 core\n" +
        "layout(location=0) in vec3 aPos;\n" +
        "uniform mat4 uMVP;\n" +
        "void main(){\n" +
        "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
        "    gl_PointSize = 8.0;\n" +
        "}";

    private static final String FRAG =
        "#version 330 core\n" +
        "uniform vec4 uColor;\n" +
        "out vec4 FragColor;\n" +
        "void main(){ FragColor = uColor; }";

    public void init() {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, VERT);
        glCompileShader(vs);
        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, FRAG);
        glCompileShader(fs);
        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);
        glDeleteShader(vs);
        glDeleteShader(fs);
        uMvpLoc   = glGetUniformLocation(programId, "uMVP");
        uColorLoc = glGetUniformLocation(programId, "uColor");

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /**
     * @param mvp    The scene MVP matrix (proj * view * model).
     * @param points Measurement points in GL render space.
     */
    public void render(Matrix4f mvp, List<Vector3f> points) {
        if (programId <= 0 || points == null || points.size() < 1) return;

        float[] mat = new float[16];
        mvp.get(mat);

        upload(points);

        boolean depthOn = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_PROGRAM_POINT_SIZE);

        glUseProgram(programId);
        glUniformMatrix4fv(uMvpLoc, false, mat);
        glBindVertexArray(vaoId);

        if (points.size() >= 2) {
            glUniform4f(uColorLoc, 1.0f, 1.0f, 0.0f, 1.0f); // yellow line
            glLineWidth(2.0f);
            glDrawArrays(GL_LINE_STRIP, 0, points.size());
            glLineWidth(1.0f);
        }

        glUniform4f(uColorLoc, 1.0f, 1.0f, 1.0f, 1.0f); // white points
        glDrawArrays(GL_POINTS, 0, points.size());

        glBindVertexArray(0);
        glUseProgram(0);
        glDisable(GL_PROGRAM_POINT_SIZE);
        if (depthOn) glEnable(GL_DEPTH_TEST);
    }

    private void upload(List<Vector3f> points) {
        float[] data = new float[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            Vector3f p = points.get(i);
            data[i*3]   = p.x;
            data[i*3+1] = p.y;
            data[i*3+2] = p.z;
        }
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(data.length);
            buf.put(data).flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
        }
        glBindVertexArray(0);
    }
}
