package com.alsaeeddev.recapp.data.model

enum class ResolutionOption(val label: String, val maxDimension: Int) {
    RES_NATIVE("Native Screen Resolution", 0),
    RES_1080P("1080p (Full HD)", 1920),
    RES_720P("720p (HD)", 1280),
    RES_480P("480p (SD)", 854),
    RES_4K("4K (UHD)", 3840)
}

enum class FpsOption(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS")
}

enum class BitrateOption(val label: String, val bps: Int) {
    AUTO("Auto", 8_000_000),
    LOW("Low (4 Mbps)", 4_000_000),
    MEDIUM("Medium (8 Mbps)", 8_000_000),
    HIGH("High (12 Mbps)", 12_000_000),
    ULTRA("Ultra (16 Mbps)", 16_000_000)
}

enum class AudioSourceOption(val label: String) {
    INTERNAL_AND_MIC("Internal + Mic"),
    INTERNAL_ONLY("Internal Audio"),
    MIC_ONLY("Microphone"),
    MUTE("No Audio (Mute)")
}

enum class OrientationOption(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class EncoderOption(val label: String) {
    HEVC("HEVC (H.265)"),
    AVC("AVC (H.264)")
}

enum class VideoFormatOption(val label: String, val extension: String) {
    MP4("MP4", "mp4"),
    MKV("MKV", "mkv")
}

enum class RecordingRegionOption(val label: String, val description: String) {
    FULL_SCREEN("Full Screen", "Capture entire screen display"),
    CUSTOM_AREA("Selective Area", "Select custom region to record")
}

data class RecordingSettings(
    val recordingRegion: RecordingRegionOption = RecordingRegionOption.FULL_SCREEN,
    val resolution: ResolutionOption = ResolutionOption.RES_1080P,
    val fps: FpsOption = FpsOption.FPS_60,
    val bitrate: BitrateOption = BitrateOption.AUTO,
    val audioSource: AudioSourceOption = AudioSourceOption.MIC_ONLY,
    val orientation: OrientationOption = OrientationOption.AUTO,
    val encoder: EncoderOption = EncoderOption.HEVC,
    val format: VideoFormatOption = VideoFormatOption.MP4,
    val countdownSeconds: Int = 3,
    val showFloatingBubble: Boolean = false,
    val showNotificationControls: Boolean = true,
    val filenamePrefix: String = "REC",
    val maxDurationMinutes: Int = 0, // 0 means unlimited
    val isDarkMode: Boolean = false
)
