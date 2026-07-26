package com.vvenv.tomrun

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 三角形网格：交错存放 位置(3) + 法线(3)。体素风格只需要立方体。
 */
class Mesh(data: FloatArray) {

    private val buffer: FloatBuffer = ByteBuffer
        .allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(data); position(0) }

    private val vertexCount = data.size / 6

    fun draw(posLoc: Int, normalLoc: Int) {
        buffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 24, buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }

    companion object {

        /** 单位立方体（中心在原点，边长 1），用 model 矩阵缩放成各种盒子 */
        fun cube(): Mesh {
            val f = 0.5f
            val faces = arrayOf(
                Face(floatArrayOf(0f, 0f, 1f), floatArrayOf(-f, -f, f, f, -f, f, f, f, f, -f, f, f)),
                Face(floatArrayOf(0f, 0f, -1f), floatArrayOf(f, -f, -f, -f, -f, -f, -f, f, -f, f, f, -f)),
                Face(floatArrayOf(-1f, 0f, 0f), floatArrayOf(-f, -f, -f, -f, -f, f, -f, f, f, -f, f, -f)),
                Face(floatArrayOf(1f, 0f, 0f), floatArrayOf(f, -f, f, f, -f, -f, f, f, -f, f, f, f)),
                Face(floatArrayOf(0f, 1f, 0f), floatArrayOf(-f, f, f, f, f, f, f, f, -f, -f, f, -f)),
                Face(floatArrayOf(0f, -1f, 0f), floatArrayOf(-f, -f, -f, f, -f, -f, f, -f, f, -f, -f, f))
            )
            val out = ArrayList<Float>(6 * 6 * 6)
            for (face in faces) {
                val c = face.corners
                val idx = intArrayOf(0, 1, 2, 0, 2, 3)
                for (i in idx) {
                    out.add(c[i * 3]); out.add(c[i * 3 + 1]); out.add(c[i * 3 + 2])
                    out.add(face.n[0]); out.add(face.n[1]); out.add(face.n[2])
                }
            }
            return Mesh(out.toFloatArray())
        }

        /**
         * 全屏渐变背景四边形：位置直接落在 NDC（配 identity MVP），
         * y ∈ [-1,1] 传给片元当竖直渐变坐标。法线未用，占位向上。
         */
        fun fullscreenQuad(): Mesh {
            val v = floatArrayOf(
                -1f, -1f, 0f, 0f, 1f, 0f,
                1f, -1f, 0f, 0f, 1f, 0f,
                1f, 1f, 0f, 0f, 1f, 0f,
                -1f, -1f, 0f, 0f, 1f, 0f,
                1f, 1f, 0f, 0f, 1f, 0f,
                -1f, 1f, 0f, 0f, 1f, 0f
            )
            return Mesh(v)
        }

        private class Face(val n: FloatArray, val corners: FloatArray)
    }
}
