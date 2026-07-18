package com.vvenv.tomrun

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * 简单三角形网格：交错存放 位置(3) + 法线(3)。
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
                // normal, 4 corners (逆时针)
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

        private class Face(val n: FloatArray, val corners: FloatArray)

        /** 单位球（半径 0.5） */
        fun sphere(stacks: Int = 10, slices: Int = 14): Mesh {
            val out = ArrayList<Float>()
            val r = 0.5f
            for (i in 0 until stacks) {
                val phi0 = Math.PI * i / stacks
                val phi1 = Math.PI * (i + 1) / stacks
                for (j in 0 until slices) {
                    val th0 = 2 * Math.PI * j / slices
                    val th1 = 2 * Math.PI * (j + 1) / slices
                    val p00 = pt(r, phi0, th0); val p01 = pt(r, phi0, th1)
                    val p10 = pt(r, phi1, th0); val p11 = pt(r, phi1, th1)
                    addTri(out, p00, p10, p11)
                    addTri(out, p00, p11, p01)
                }
            }
            return Mesh(out.toFloatArray())
        }

        /** 圆柱（半径 0.5，高 1，沿 Y 轴），topR=0 时为圆锥 */
        fun cylinder(slices: Int = 14, topR: Float = 0.5f): Mesh {
            val out = ArrayList<Float>()
            val r = 0.5f
            val h = 0.5f
            for (j in 0 until slices) {
                val a0 = (2 * Math.PI * j / slices).toFloat()
                val a1 = (2 * Math.PI * (j + 1) / slices).toFloat()
                val b0 = floatArrayOf(r * cos(a0), -h, r * sin(a0))
                val b1 = floatArrayOf(r * cos(a1), -h, r * sin(a1))
                val t0 = floatArrayOf(topR * cos(a0), h, topR * sin(a0))
                val t1 = floatArrayOf(topR * cos(a1), h, topR * sin(a1))
                // 侧面（法线取径向）
                val n0 = floatArrayOf(cos(a0), 0f, sin(a0))
                val n1 = floatArrayOf(cos(a1), 0f, sin(a1))
                addV(out, b0, n0); addV(out, t0, n0); addV(out, t1, n1)
                addV(out, b0, n0); addV(out, t1, n1); addV(out, b1, n1)
                // 底盖
                val dn = floatArrayOf(0f, -1f, 0f)
                addV(out, floatArrayOf(0f, -h, 0f), dn); addV(out, b1, dn); addV(out, b0, dn)
                // 顶盖
                if (topR > 0f) {
                    val un = floatArrayOf(0f, 1f, 0f)
                    addV(out, floatArrayOf(0f, h, 0f), un); addV(out, t0, un); addV(out, t1, un)
                }
            }
            return Mesh(out.toFloatArray())
        }

        private fun pt(r: Float, phi: Double, th: Double): FloatArray {
            val x = (r * sin(phi) * cos(th)).toFloat()
            val y = (r * cos(phi)).toFloat()
            val z = (r * sin(phi) * sin(th)).toFloat()
            return floatArrayOf(x, y, z)
        }

        private fun addTri(out: ArrayList<Float>, a: FloatArray, b: FloatArray, c: FloatArray) {
            // 球面：法线即归一化位置
            addV(out, a, norm(a)); addV(out, b, norm(b)); addV(out, c, norm(c))
        }

        private fun norm(v: FloatArray): FloatArray {
            val len = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
        }

        private fun addV(out: ArrayList<Float>, p: FloatArray, n: FloatArray) {
            out.add(p[0]); out.add(p[1]); out.add(p[2])
            out.add(n[0]); out.add(n[1]); out.add(n[2])
        }
    }
}
