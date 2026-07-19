package com.vvenv.tomrun

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 程序化音效：启动时用代码合成 PCM 波形写成 WAV，SoundPool 播放。
 * 零音频素材文件。
 */
class SoundFx(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<Int, Int>()

    init {
        load(context, Game.EV_JUMP, "jump", tone(380f, 760f, 0.12f, 0.45f))
        load(context, Game.EV_SLIDE, "slide", tone(520f, 160f, 0.18f, 0.40f))
        load(
            context, Game.EV_COIN, "coin",
            concat(tone(1250f, 1250f, 0.05f, 0.45f), tone(1650f, 1650f, 0.09f, 0.40f, decay = 2f))
        )
        load(
            context, Game.EV_POWER, "power",
            concat(
                tone(523f, 523f, 0.07f, 0.45f), tone(659f, 659f, 0.07f, 0.45f),
                tone(784f, 784f, 0.14f, 0.50f, decay = 1.6f)
            )
        )
        load(context, Game.EV_SHIELD, "shield", noise(0.12f, 0.5f))
        load(
            context, Game.EV_DIE, "die",
            concat(tone(240f, 70f, 0.38f, 0.55f, square = true), noise(0.10f, 0.35f))
        )
        load(context, Game.EV_ZIP, "zip", tone(420f, 1400f, 0.26f, 0.40f))
        load(
            context, Game.EV_RECORD, "record",
            concat(
                tone(660f, 660f, 0.09f, 0.5f), tone(660f, 660f, 0.07f, 0.4f),
                tone(990f, 990f, 0.24f, 0.55f, decay = 1.4f)
            )
        )
        // 连击升级：上行琶音
        load(
            context, Game.EV_COMBO, "combo",
            concat(
                tone(880f, 880f, 0.05f, 0.45f),
                tone(1100f, 1100f, 0.05f, 0.45f),
                tone(1320f, 1320f, 0.12f, 0.55f, decay = 1.5f)
            )
        )
        // 冲刺启动：嗖的一声扫频
        load(context, Game.EV_BOOST, "boost", tone(200f, 1600f, 0.28f, 0.50f))
        // 撞碎障碍：噪声 + 低频
        load(
            context, Game.EV_SMASH, "smash",
            concat(noise(0.08f, 0.55f), tone(180f, 90f, 0.12f, 0.45f, square = true))
        )
        // 任务完成：短号角
        load(
            context, Game.EV_QUEST, "quest",
            concat(
                tone(523f, 523f, 0.08f, 0.5f),
                tone(659f, 659f, 0.08f, 0.5f),
                tone(784f, 1046f, 0.22f, 0.55f, decay = 1.3f)
            )
        )
        // 成就解锁：更华丽的号角
        load(
            context, Game.EV_ACHIEVE, "achieve",
            concat(
                tone(523f, 523f, 0.07f, 0.5f),
                tone(659f, 659f, 0.07f, 0.5f),
                tone(784f, 784f, 0.07f, 0.5f),
                tone(1046f, 1046f, 0.28f, 0.6f, decay = 1.2f)
            )
        )
    }

    fun play(event: Int) {
        val id = ids[event] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()

    companion object {
        private const val SR = 22050

        private fun tone(
            f0: Float, f1: Float, dur: Float, vol: Float,
            square: Boolean = false, decay: Float = 1f
        ): ShortArray {
            val n = (SR * dur).toInt()
            val out = ShortArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toFloat() / n
                val f = f0 + (f1 - f0) * t
                phase += 2.0 * PI * f / SR
                var s = sin(phase).toFloat()
                if (square) s = if (s >= 0) 0.6f else -0.6f
                val attack = min(1f, i / (SR * 0.004f))
                val env = attack * Math.pow((1.0 - t), decay.toDouble()).toFloat()
                out[i] = (s * env * vol * 32767f).toInt().toShort()
            }
            return out
        }

        private fun noise(dur: Float, vol: Float): ShortArray {
            val n = (SR * dur).toInt()
            val out = ShortArray(n)
            for (i in 0 until n) {
                val t = i.toFloat() / n
                val env = (1f - t) * (1f - t)
                out[i] = ((Random.nextFloat() * 2f - 1f) * env * vol * 32767f).toInt().toShort()
            }
            return out
        }

        private fun concat(vararg parts: ShortArray): ShortArray {
            val total = parts.sumOf { it.size }
            val out = ShortArray(total)
            var pos = 0
            for (p in parts) {
                System.arraycopy(p, 0, out, pos, p.size)
                pos += p.size
            }
            return out
        }
    }

    private fun load(context: Context, event: Int, name: String, pcm: ShortArray) {
        val f = File(context.cacheDir, "sfx_$name.wav")
        writeWav(f, pcm)
        ids[event] = pool.load(f.path, 1)
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val dataSize = pcm.size * 2
        BufferedOutputStream(FileOutputStream(f)).use { o ->
            o.write("RIFF".toByteArray())
            o.writeIntLE(36 + dataSize)
            o.write("WAVE".toByteArray())
            o.write("fmt ".toByteArray())
            o.writeIntLE(16)
            o.writeShortLE(1)
            o.writeShortLE(1)
            o.writeIntLE(SR)
            o.writeIntLE(SR * 2)
            o.writeShortLE(2)
            o.writeShortLE(16)
            o.write("data".toByteArray())
            o.writeIntLE(dataSize)
            for (s in pcm) o.writeShortLE(s.toInt())
        }
    }

    private fun OutputStream.writeIntLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }

    private fun OutputStream.writeShortLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }
}
