package ir.amirab.downloader.downloaditem.ytdlp

/**
 * Chất lượng tải được chọn trong giao diện, ánh xạ sang tham số của yt-dlp.
 * Chỉ [id] được lưu xuống đĩa nên có thể đổi tên/tham số mà không làm hỏng dữ liệu cũ.
 */
enum class YtDlpPreset(
    val id: String,
    val title: String,
    val extension: String,
    val ytDlpArgs: List<String>,
) {
    Best(
        id = "best",
        title = "Best quality (video + audio)",
        extension = "mp4",
        ytDlpArgs = videoArgs(selector = "bv*+ba/b"),
    ),
    P1080(
        id = "1080",
        title = "Up to 1080p",
        extension = "mp4",
        ytDlpArgs = videoArgs(selector = "bv*[height<=1080]+ba/b[height<=1080]"),
    ),
    P720(
        id = "720",
        title = "Up to 720p",
        extension = "mp4",
        ytDlpArgs = videoArgs(selector = "bv*[height<=720]+ba/b[height<=720]"),
    ),
    P480(
        id = "480",
        title = "Up to 480p",
        extension = "mp4",
        ytDlpArgs = videoArgs(selector = "bv*[height<=480]+ba/b[height<=480]"),
    ),
    AudioMp3(
        id = "audio",
        title = "Audio only (MP3)",
        extension = "mp3",
        ytDlpArgs = listOf("-f", "ba/b", "-x", "--audio-format", "mp3"),
    );

    companion object {
        val Default = Best

        fun fromId(id: String?): YtDlpPreset {
            return entries.firstOrNull { it.id == id } ?: Default
        }
    }
}

// Ưu tiên độ phân giải cao nhất, cùng độ phân giải thì chọn H.264/AAC để tệp phát được trên mọi thiết bị
// (AV1 chiếm ưu tiên mặc định của yt-dlp nhưng nhiều trình phát không hỗ trợ)
private fun videoArgs(selector: String) = listOf(
    "-f", selector,
    "-S", "res,vcodec:h264,acodec:aac",
    "--merge-output-format", "mp4",
)
