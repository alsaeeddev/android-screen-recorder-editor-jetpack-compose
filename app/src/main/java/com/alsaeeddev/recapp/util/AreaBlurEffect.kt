package com.alsaeeddev.recapp.util

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.SingleFrameGlShaderProgram

enum class BlurType {
    GAUSSIAN,
    PIXELATE
}

enum class BlurShape {
    RECTANGLE,
    OVAL
}

@OptIn(UnstableApi::class)
class AreaBlurEffect(
    val blurXNorm: Float,
    val blurYNorm: Float,
    val blurWidthNorm: Float,
    val blurHeightNorm: Float,
    val blurRadius: Float = 12f,
    val blurType: BlurType = BlurType.GAUSSIAN,
    val blurShape: BlurShape = BlurShape.RECTANGLE,
    val startMs: Long = -1L,
    val endMs: Long = -1L
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return AreaBlurShaderProgram(
            context = context,
            useHdr = useHdr,
            blurXNorm = blurXNorm,
            blurYNorm = blurYNorm,
            blurWidthNorm = blurWidthNorm,
            blurHeightNorm = blurHeightNorm,
            blurRadius = blurRadius,
            blurType = blurType,
            blurShape = blurShape,
            startMs = startMs,
            endMs = endMs
        )
    }
}

@OptIn(UnstableApi::class)
class AreaBlurShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val blurXNorm: Float,
    private val blurYNorm: Float,
    private val blurWidthNorm: Float,
    private val blurHeightNorm: Float,
    private val blurRadius: Float,
    private val blurType: BlurType,
    private val blurShape: BlurShape,
    private val startMs: Long,
    private val endMs: Long
) : SingleFrameGlShaderProgram(useHdr) {

    private val glProgram: GlProgram

    init {
        val vertexShader = """
            attribute vec4 aFramePosition;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexSampler;
            uniform vec4 uBlurRect; // x, y, width, height in normalized [0,1]
            uniform float uBlurRadius;
            uniform int uBlurType; // 0 = GAUSSIAN, 1 = PIXELATE
            uniform int uBlurShape; // 0 = RECTANGLE, 1 = OVAL
            uniform vec2 uTexSize;
            uniform int uActive; // 1 = active for current frame, 0 = inactive

            void main() {
                if (uActive == 0) {
                    gl_FragColor = texture2D(uTexSampler, vTexCoord);
                    return;
                }

                // Check if inside region in OpenGL Texture Space (where Y=0 is bottom and Y=1 is top)
                float left = uBlurRect.x;
                float width = uBlurRect.z;
                float height = uBlurRect.w;
                float right = left + width;

                // UI y=0 is top, y=1 is bottom. OpenGL y=1 is top, y=0 is bottom.
                float glTop = 1.0 - uBlurRect.y;
                float glBottom = 1.0 - (uBlurRect.y + height);

                bool inRegion = false;
                if (vTexCoord.x >= left && vTexCoord.x <= right && vTexCoord.y >= glBottom && vTexCoord.y <= glTop) {
                    if (uBlurShape == 0) { // RECTANGLE
                        inRegion = true;
                    } else { // OVAL
                        float centerX = left + width * 0.5;
                        float centerY = glBottom + height * 0.5;
                        float rx = width * 0.5;
                        float ry = height * 0.5;
                        if (rx > 0.0 && ry > 0.0) {
                            float dx = (vTexCoord.x - centerX) / rx;
                            float dy = (vTexCoord.y - centerY) / ry;
                            if (dx * dx + dy * dy <= 1.0) {
                                inRegion = true;
                            }
                        }
                    }
                }

                if (!inRegion) {
                    gl_FragColor = texture2D(uTexSampler, vTexCoord);
                    return;
                }

                if (uBlurType == 1) { // PIXELATE (MOSAIC)
                    float blockSizeX = max(4.0, uBlurRadius * 2.0) / uTexSize.x;
                    float blockSizeY = max(4.0, uBlurRadius * 2.0) / uTexSize.y;
                    
                    vec2 coord = vec2(
                        floor((vTexCoord.x - left) / blockSizeX) * blockSizeX + left + blockSizeX * 0.5,
                        floor((vTexCoord.y - glBottom) / blockSizeY) * blockSizeY + glBottom + blockSizeY * 0.5
                    );
                    coord.x = clamp(coord.x, left, right);
                    coord.y = clamp(coord.y, glBottom, glTop);
                    gl_FragColor = texture2D(uTexSampler, coord);
                } else { // GAUSSIAN / BOX HEAVY BLUR
                    vec4 colorSum = vec4(0.0);
                    float totalWeight = 0.0;
                    float stepX = max(1.0, uBlurRadius) / uTexSize.x;
                    float stepY = max(1.0, uBlurRadius) / uTexSize.y;

                    for (int x = -4; x <= 4; x++) {
                        for (int y = -4; y <= 4; y++) {
                            float fx = float(x);
                            float fy = float(y);
                            float weight = 1.0 / (1.0 + 0.3 * (fx * fx + fy * fy));
                            vec2 sampleCoord = vec2(
                                clamp(vTexCoord.x + fx * stepX * 0.6, left, right),
                                clamp(vTexCoord.y + fy * stepY * 0.6, glBottom, glTop)
                            );
                            colorSum += texture2D(uTexSampler, sampleCoord) * weight;
                            totalWeight += weight;
                        }
                    }
                    gl_FragColor = colorSum / max(0.001, totalWeight);
                }
            }
        """.trimIndent()

        glProgram = try {
            GlProgram(vertexShader, fragmentShader)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        glProgram.setFloatsUniform("uTexSize", floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTextureId: Int, presentationTimeUs: Long) {
        val currentMs = presentationTimeUs / 1000L
        val isActive = if (startMs >= 0 && endMs > startMs) {
            currentMs in startMs..endMs
        } else {
            true
        }

        try {
            glProgram.use()
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                4
            )
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTextureId, 0)
            glProgram.setFloatsUniform("uBlurRect", floatArrayOf(blurXNorm, blurYNorm, blurWidthNorm, blurHeightNorm))
            glProgram.setFloatUniform("uBlurRadius", blurRadius)
            glProgram.setIntUniform("uBlurType", if (blurType == BlurType.GAUSSIAN) 0 else 1)
            glProgram.setIntUniform("uBlurShape", if (blurShape == BlurShape.RECTANGLE) 0 else 1)
            glProgram.setIntUniform("uActive", if (isActive) 1 else 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }
}
