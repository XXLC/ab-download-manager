package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.part.DownloadPart
import java.io.File

/**
 * yt-dlp tự ghi tệp nên không có DestWriter. Tệp tạm và tệp dở của yt-dlp nằm trong [tempDirectory]
 * riêng của từng lượt tải (theo id), chỉ tệp hoàn chỉnh mới được chuyển sang [outputFile].
 * Nhờ vậy không phải đoán tệp nào thuộc lượt tải nào qua tên, và dọn dẹp chỉ đụng thư mục của chính nó.
 */
class YtDlpDestination(
    outputFile: File,
    val tempDirectory: File,
) : DownloadDestination(outputFile) {
    /** tên tệp không có phần mở rộng, yt-dlp tự thêm đuôi theo preset */
    val baseName: String = outputFile.nameWithoutExtension

    /** Tệp yt-dlp ghi đường dẫn kết quả cuối cùng vào, nằm trong thư mục tạm. */
    val resultFile: File get() = File(tempDirectory, RESULT_FILE_NAME)

    /** Dung lượng đã có trên đĩa, dùng để hiển thị tiến độ của tải xuống dở sau khi mở lại app. */
    fun sizeOnDisk(): Long {
        if (outputFile.exists()) return outputFile.length()
        return tempDirectory.walkTopDown()
            .filter { it.isFile && it != resultFile }
            .sumOf { it.length() }
    }

    override fun cleanUpJunkFiles() {
        tempDirectory.deleteRecursively()
    }

    fun prepareDestinationFolder() {
        prepareDestinationFolder(outputFile)
        tempDirectory.mkdirs()
    }

    override fun getWriterFor(part: DownloadPart): DestWriter {
        throw UnsupportedOperationException("yt-dlp writes the file itself")
    }

    override fun canGetFileWriter(): Boolean = false
    override suspend fun prepareFile(onProgressUpdate: (Int?) -> Unit) {}
    override suspend fun isDownloadedPartsIsValid(): Boolean = true
    override fun flush() {}

    private companion object {
        const val RESULT_FILE_NAME = "final-path.txt"
    }
}
