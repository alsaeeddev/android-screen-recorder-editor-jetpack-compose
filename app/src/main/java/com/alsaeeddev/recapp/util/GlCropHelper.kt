package com.alsaeeddev.recapp.util

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GlCropHelper(
    private val outputSurface: Surface,
    @Volatile private var realWidth: Int,
    @Volatile private var realHeight: Int,
    @Volatile private var cropX: Int,
    @Volatile private var cropY: Int,
    @Volatile private var cropW: Int,
    @Volatile private var cropH: Int
) : SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "GlCropHelper"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        // --- DEBUG TOGGLES ---
        // The exact orientation fix depends on what SurfaceTexture.getTransformMatrix()
        // returns for THIS device/Android version when fed by MediaProjection's VirtualDisplay.
        // Rather than guess blindly, flip these and rebuild to quickly find the correct
        // combination, or check logcat (tag "GlCropHelper") for the one-time STMatrix dump
        // and crop-rect values printed on the first rendered frame.
        private const val FLIP_CROP_Y = true
        private const val FLIP_CROP_X = false

        private val VERTEX_SHADER: String = run {
            val xExpr = if (FLIP_CROP_X)
                "uCropRect.x + (1.0 - aTextureCoord.x) * uCropRect.z"
            else
                "uCropRect.x + aTextureCoord.x * uCropRect.z"

            val yExpr = if (FLIP_CROP_Y)
                "(1.0 - uCropRect.y - uCropRect.w) + aTextureCoord.y * uCropRect.w"
            else
                "uCropRect.y + aTextureCoord.y * uCropRect.w"

            """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            uniform mat4 uSTMatrix;
            uniform vec4 uCropRect; // (cropLeftNorm, cropTopNorm, cropWidthNorm, cropHeightNorm)
                                     // cropTopNorm/cropLeftNorm are TOP-LEFT-origin screen fractions.

            void main() {
                gl_Position = aPosition;
                vec2 cropCoord = vec2(
                    $xExpr,
                    $yExpr
                );
                vTextureCoord = (uSTMatrix * vec4(cropCoord, 0.0, 1.0)).xy;
            }
            """
        }

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;

            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f, // V0: Bottom-Left
             1.0f, -1.0f, // V1: Bottom-Right
            -1.0f,  1.0f, // V2: Top-Left
             1.0f,  1.0f  // V3: Top-Right
        )

        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
            0.0f, 0.0f, // V0: Bottom-Left
            1.0f, 0.0f, // V1: Bottom-Right
            0.0f, 1.0f, // V2: Top-Left
            1.0f, 1.0f  // V3: Top-Right
        )
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var textureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    var inputSurface: Surface? = null
        private set

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private val stMatrix = FloatArray(16)
    private var program: Int = 0
    private var maPositionHandle: Int = 0
    private var maTextureHandle: Int = 0
    private var muSTMatrixHandle: Int = 0
    private var muCropRectHandle: Int = 0
    private var hasLoggedFirstFrame = false

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(FULL_RECTANGLE_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULL_RECTANGLE_COORDS)
            position(0)
        }

    private val texBuffer: FloatBuffer = ByteBuffer.allocateDirect(FULL_RECTANGLE_TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(FULL_RECTANGLE_TEX_COORDS)
            position(0)
        }

    fun start() {
        val thread = HandlerThread("GlCropRenderThread").apply { start() }
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler

        val latch = CountDownLatch(1)
        handler.post {
            try {
                initEgl()
                initGl()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing GlCropHelper", e)
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Timeout waiting for GlCropHelper init", e)
        }
    }

    fun updateCropRegion(cX: Int, cY: Int, cW: Int, cH: Int, rWidth: Int = realWidth, rHeight: Int = realHeight) {
        realWidth = rWidth
        realHeight = rHeight
        cropX = cX
        cropY = cY
        cropW = cW
        cropH = cH
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        var attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0 || configs[0] == null) {
            attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        }
        val eglConfig = configs[0] ?: throw RuntimeException("Unable to find suitable EGLConfig")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, outputSurface, surfaceAttribs, 0)
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed: " + EGL14.eglGetError())
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed: " + EGL14.eglGetError())
        }
    }

    private fun initGl() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) {
            throw RuntimeException("Failed creating GL program")
        }

        maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        maTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        muSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        muCropRectHandle = GLES20.glGetUniformLocation(program, "uCropRect")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(realWidth, realHeight)
            setOnFrameAvailableListener(this@GlCropHelper, renderHandler)
        }

        inputSurface = Surface(surfaceTexture)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        renderHandler?.post {
            try {
                if (eglDisplay == EGL14.EGL_NO_DISPLAY || surfaceTexture == null || eglSurface == EGL14.EGL_NO_SURFACE) return@post

                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)

                if (!hasLoggedFirstFrame) {
                    hasLoggedFirstFrame = true
                    Log.d(TAG, "First frame debug: realWidth=$realWidth realHeight=$realHeight " +
                        "cropX=$cropX cropY=$cropY cropW=$cropW cropH=$cropH " +
                        "FLIP_CROP_X=$FLIP_CROP_X FLIP_CROP_Y=$FLIP_CROP_Y " +
                        "STMatrix=${stMatrix.joinToString()}")
                }

                val currentW = cropW
                val currentH = cropH

                GLES20.glViewport(0, 0, currentW, currentH)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                GLES20.glUseProgram(program)

                vertexBuffer.position(0)
                GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
                GLES20.glEnableVertexAttribArray(maPositionHandle)

                texBuffer.position(0)
                GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, texBuffer)
                GLES20.glEnableVertexAttribArray(maTextureHandle)

                GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, stMatrix, 0)

                val cropLeftNorm = cropX.toFloat() / realWidth.coerceAtLeast(1).toFloat()
                val cropTopNorm = cropY.toFloat() / realHeight.coerceAtLeast(1).toFloat()
                val cropWidthNorm = currentW.toFloat() / realWidth.coerceAtLeast(1).toFloat()
                val cropHeightNorm = currentH.toFloat() / realHeight.coerceAtLeast(1).toFloat()

                GLES20.glUniform4f(muCropRectHandle, cropLeftNorm, cropTopNorm, cropWidthNorm, cropHeightNorm)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, surfaceTexture?.timestamp ?: System.nanoTime())
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering cropped frame", e)
            }
        }
    }

    fun release() {
        val latch = CountDownLatch(1)
        renderHandler?.post {
            try {
                if (program != 0) {
                    GLES20.glDeleteProgram(program)
                    program = 0
                }
                if (textureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                    textureId = 0
                }
                surfaceTexture?.release()
                surfaceTexture = null
                inputSurface?.release()
                inputSurface = null

                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
                eglDisplay = EGL14.EGL_NO_DISPLAY
                eglContext = EGL14.EGL_NO_CONTEXT
                eglSurface = EGL14.EGL_NO_SURFACE
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing GlCropHelper resources", e)
            } finally {
                latch.countDown()
                renderThread?.quitSafely()
            }
        }
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Timeout waiting for release", e)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) return 0

        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, pixelShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $shaderType: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }
}