package com.vvenv.tomrun

import android.app.Activity
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {

    private lateinit var glView: GLSurfaceView
    private val game = Game()
    private var soundFx: SoundFx? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        game.attachPrefs(getSharedPreferences("tomrun", MODE_PRIVATE))
        soundFx = SoundFx(this)
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        game.onEvent = { event ->
            soundFx?.play(event)
            // 事件自带 hapticPulse；再按事件补一档保底
            val pulse = when (event) {
                Game.EV_COIN, Game.EV_JUMP, Game.EV_SLIDE -> Game.HAPTIC_LIGHT
                Game.EV_POWER, Game.EV_COMBO, Game.EV_BOOST, Game.EV_SMASH,
                Game.EV_QUEST, Game.EV_ACHIEVE, Game.EV_ZIP, Game.EV_RECORD -> Game.HAPTIC_MED
                Game.EV_SHIELD, Game.EV_DIE -> Game.HAPTIC_HEAVY
                else -> 0
            }
            val fromGame = game.consumeHaptic()
            vibrate(maxOf(pulse, fromGame))
        }

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(MsaaConfigChooser())
            setRenderer(GameRenderer(game))
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        val root = FrameLayout(this)
        root.addView(glView)
        root.addView(HudView(this, game))
        setContentView(root)
        hideSystemUi()
    }

    private fun vibrate(level: Int) {
        if (level <= 0) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val ms = when (level) {
            Game.HAPTIC_LIGHT -> 12L
            Game.HAPTIC_MED -> 28L
            else -> 55L
        }
        val amp = when (level) {
            Game.HAPTIC_LIGHT -> 40
            Game.HAPTIC_MED -> 110
            else -> 220
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) {
            // 部分设备可能拒绝振动，忽略即可
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        game.onEvent = null
        soundFx?.release()
        soundFx = null
    }

    private class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
        override fun chooseConfig(
            egl: javax.microedition.khronos.egl.EGL10,
            display: javax.microedition.khronos.egl.EGLDisplay
        ): javax.microedition.khronos.egl.EGLConfig {
            val configs = arrayOfNulls<javax.microedition.khronos.egl.EGLConfig>(1)
            val num = IntArray(1)
            val msaa = intArrayOf(
                javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE, 16,
                javax.microedition.khronos.egl.EGL10.EGL_RENDERABLE_TYPE, 4,
                javax.microedition.khronos.egl.EGL10.EGL_SAMPLE_BUFFERS, 1,
                javax.microedition.khronos.egl.EGL10.EGL_SAMPLES, 4,
                javax.microedition.khronos.egl.EGL10.EGL_NONE
            )
            if (egl.eglChooseConfig(display, msaa, configs, 1, num) && num[0] > 0) {
                return configs[0]!!
            }
            val plain = intArrayOf(
                javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE, 16,
                javax.microedition.khronos.egl.EGL10.EGL_RENDERABLE_TYPE, 4,
                javax.microedition.khronos.egl.EGL10.EGL_NONE
            )
            egl.eglChooseConfig(display, plain, configs, 1, num)
            return configs[0]!!
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}
