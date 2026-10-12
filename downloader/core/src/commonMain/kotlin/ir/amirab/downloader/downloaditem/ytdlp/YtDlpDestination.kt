package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.part.DownloadPart
import ir.amirab.util.tryAtomicMove
import java.io.File

/**
 * yt-dlp tự ghi tệp nên không có DestWriter. Lớp này chỉ quản lý tệp đầu ra và các tệp tạm của yt-dlp
 * (`Tên.f137.mp4`, `Tên.mp4.part`, `Tên.mp4.ytdl`, `Tên.temp.mp4`).
 */
class YtDlpDestination(
    outputFile: File,
) : DownloadDestination(outputFile) {
    val baseName: String = outputFile.nameWithoutExtension
    private val directory: File get() = outputFile.parentFile

    /** Tệp tạm do yt-dlp tạo cho lần tải này. Phân biệt bằng mẫu tên để không xóa nhầm tệp của người dùng. */
    fun findJunkFiles(): List<File> {
        val prefix = "$baseName."
        return directory.listFiles().orEmpty().filter { file ->
            file != outputFile && file.name.startsWith(prefix) && isYtDlpTemporary(file.name.removePrefix(prefix))
        }
    }

    /** Dung lượng đã có trên đĩa, dùng để hiển thị tiến độ của tải xuống dở sau khi mở lại app. */
    fun sizeOnDisk(): Long {
        if (outputFile.exists()) return outputFile.length()
        return findJunkFiles().sumOf { it.length() }
    }

    /**
     * Nếu yt-dlp đặt đuôi khác dự kiến thì trả về tệp thực tế để cập nhật tên của item.
     */
    fun findActualOutput(): File? {
        if (outputFile.exists()) return outputFile
        val prefix = "$baseName."
        return directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(prefix) && !isYtDlpTemporary(it.name.removePrefix(prefix)) }
            .maxByOrNull { it.lastModified() }
    }

    override fun cleanUpJunkFiles() {
        findJunkFiles().forEach { it.delete() }
    }

    override fun moveOutput(to: File) {
        val newBase = to.nameWithoutExtension
        val newDirectory = to.parentFile
        // chuyển cả tệp tạm để tải dở vẫn tiếp tục được ở vị trí mới
        findJunkFiles().forEach {
            val suffix = it.name.removePrefix(baseName)
            moveOrFail(it, File(newDirectory, newBase + suffix))
        }
        super.moveOutput(to)
    }

    private fun moveOrFail(from: File, to: File) {
        try {
            to.parentFile.mkdirs()
            from.tryAtomicMove(to)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to move ${from.name}: ${e.localizedMessage}", e)
        }
    }

    fun prepareDestinationFolder() {
        prepareDestinationFolder(outputFile)
    }

    override fun getWriterFor(part: DownloadPart): DestWriter {
        throw UnsupportedOperationException("yt-dlp writes the file itself")
    }

    override fun canGetFileWriter(): Boolean = false
    override suspend fun prepareFile(onProgressUpdate: (Int?) -> Unit) {}
    override suspend fun isDownloadedPartsIsValid(): Boolean = true
    override fun flush() {}

    companion object {
        private val formatStreamFile = Regex("""^f[\w-]+\.\w+$""")

        fun isYtDlpTemporary(suffix: String): Boolean {
            return suffix.endsWith(".part") ||
                    suffix.endsWith(".ytdl") ||
                    suffix.startsWith("temp.") ||
                    formatStreamFile.matches(suffix)
        }
    }
}
