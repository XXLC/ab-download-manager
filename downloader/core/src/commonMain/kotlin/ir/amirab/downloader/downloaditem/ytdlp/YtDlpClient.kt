package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.connection.IResponseInfo
import java.io.File
import java.io.IOException

/**
 * Cầu nối tới yt-dlp. Phần chạy tiến trình nằm ở desktopMain vì cần API JDK mà Android không có.
 */
interface YtDlpClient {
    /** Phiên bản yt-dlp đang cài, hoặc null nếu không tìm thấy yt-dlp. */
    suspend fun installedVersion(): String?

    /** Lấy thông tin video mà không tải. */
    suspend fun fetchInfo(link: String, preset: YtDlpPreset): YtDlpMediaInfo

    /**
     * Tải và chạy đến khi xong. Hủy coroutine sẽ dừng yt-dlp (kèm cả tiến trình con).
     * [onProgress] nhận tổng số byte đã tải của mọi luồng (video + audio) cộng dồn.
     * Trả về tệp cuối cùng mà yt-dlp báo, hoặc null nếu không đọc được.
     */
    suspend fun download(
        request: YtDlpDownloadRequest,
        onProgress: suspend (YtDlpProgress) -> Unit,
    ): File?
}

data class YtDlpMediaInfo(
    val title: String,
    val durationSeconds: Double?,
    val sizeBytes: Long?,
)

data class YtDlpProgress(
    val downloadedBytes: Long,
)

data class YtDlpDownloadRequest(
    val link: String,
    val preset: YtDlpPreset,
    val outputDirectory: File,
    /** thư mục riêng của lượt tải này cho tệp tạm và tệp dở của yt-dlp */
    val tempDirectory: File,
    /** tệp để yt-dlp ghi đường dẫn kết quả cuối cùng vào, nằm trong [tempDirectory] */
    val resultFile: File,
    /** tên tệp không có phần mở rộng, yt-dlp sẽ tự thêm đuôi theo [preset] */
    val baseName: String,
    val connections: Int,
    /** byte/giây, 0 là không giới hạn */
    val speedLimit: Long,
)

class YtDlpException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class YtDlpResponseInfo(
    val mediaInfo: YtDlpMediaInfo,
) : IResponseInfo {
    override val isSuccessFul: Boolean get() = true
    override val requiresAuth: Boolean get() = false
    override val requireBasicAuth: Boolean get() = false
    override val resumeSupport: Boolean get() = true
    override val isWebPage: Boolean get() = false
    override val unsuccessFullException: Throwable? get() = null
}
