package ir.amirab.downloader.downloaditem.ytdlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Chạy yt-dlp như một tiến trình con. Cần `yt-dlp` (và `ffmpeg` để ghép video + audio) trong PATH,
 * hoặc đặt biến môi trường `ABDM_YTDLP` trỏ thẳng tới tệp thực thi.
 */
class ProcessYtDlpClient : YtDlpClient {

    override suspend fun installedVersion(): String? {
        val exe = YtDlpLocator.locate() ?: return null
        return runToEnd(listOf(exe.path, "--version")).trim().ifEmpty { null }
    }

    override suspend fun fetchInfo(link: String, preset: YtDlpPreset): YtDlpMediaInfo {
        val command = buildList {
            add(locateYtDlp().path)
            addAll(listOf("-J", "--no-playlist", "--no-warnings"))
            addAll(preset.ytDlpArgs)
            add("--")
            add(link)
        }
        val output = runToEnd(command)
        val json = Json.parseToJsonElement(output).jsonObject
        val title = json.string("title") ?: json.string("id") ?: link
        return YtDlpMediaInfo(
            title = title,
            durationSeconds = json.number("duration"),
            sizeBytes = estimateSize(json),
        )
    }

    override suspend fun download(
        request: YtDlpDownloadRequest,
        onProgress: suspend (YtDlpProgress) -> Unit,
    ): File? {
        // % trong tên phải được escape vì yt-dlp coi toàn bộ -o là mẫu định dạng
        val template = request.baseName.replace("%", "%%") + ".%(ext)s"
        request.tempDirectory.mkdirs()
        request.resultFile.delete()
        val command = buildList {
            add(locateYtDlp().path)
            addAll(listOf("--newline", "--no-playlist", "--no-warnings", "--no-mtime"))
            addAll(listOf("--progress-template", "download:$PROGRESS_PREFIX%(progress.downloaded_bytes)s"))
            addAll(listOf("-N", request.connections.coerceAtLeast(1).toString()))
            if (request.speedLimit > 0) {
                addAll(listOf("--limit-rate", request.speedLimit.toString()))
            }
            addAll(request.preset.ytDlpArgs)
            // tệp dở và tệp tạm nằm riêng trong thư mục của lượt tải, tệp hoàn chỉnh mới được chuyển ra thư mục đích
            addAll(listOf("-P", "home:${request.outputDirectory.path}"))
            addAll(listOf("-P", "temp:${request.tempDirectory.path}"))
            addAll(listOf("-o", template))
            // yt-dlp tự ghi đường dẫn tệp cuối cùng để khỏi phải đoán theo tên trong thư mục
            addAll(listOf("--print-to-file", "after_move:filepath", request.resultFile.path))
            add("--")
            add(request.link)
        }
        coroutineScope {
            val process = startProcess(command)
            val stderr = drainStderr(process)
            val reader = launch(Dispatchers.IO) {
                var finishedStreamsBytes = 0L
                var lastCurrent = 0L
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val current = parseProgress(line) ?: continue
                        // downloaded_bytes bắt đầu lại từ 0 mỗi khi chuyển sang luồng khác (video -> audio)
                        if (current < lastCurrent) {
                            finishedStreamsBytes += lastCurrent
                        }
                        lastCurrent = current
                        onProgress(YtDlpProgress(finishedStreamsBytes + current))
                    }
                }
            }
            try {
                reader.join()
                val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
                if (exitCode != 0) {
                    throw YtDlpException(errorMessage(exitCode, stderr))
                }
            } finally {
                // khi bị hủy thì phải giết tiến trình để reader đang chặn ở readLine thoát ra được
                killTree(process)
            }
        }
        return readFinalFile(request.resultFile)
    }

    private fun readFinalFile(resultFile: File): File? {
        return runCatching {
            resultFile.readLines().lastOrNull { it.isNotBlank() }?.trim()?.let(::File)
        }.getOrNull()
    }

    /**
     * Chạy lệnh ngắn (lấy thông tin, phiên bản) và trả về stdout. Việc đọc chạy ở coroutine con để hủy
     * hoặc quá hạn thì [killTree] trong finally giải phóng được lần đọc đang chặn, tiến trình không bị bỏ lại.
     */
    private suspend fun runToEnd(
        command: List<String>,
        timeoutMs: Long = METADATA_TIMEOUT_MS,
    ): String = coroutineScope {
        val process = startProcess(command)
        val stderr = drainStderr(process)
        try {
            withTimeout(timeoutMs) {
                val output = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader().readText()
                }
                val text = output.await()
                val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
                if (exitCode != 0) {
                    throw YtDlpException(errorMessage(exitCode, stderr))
                }
                text
            }
        } catch (e: TimeoutCancellationException) {
            throw YtDlpException("yt-dlp did not answer within ${timeoutMs / 1000}s", e)
        } finally {
            killTree(process)
        }
    }

    private fun startProcess(command: List<String>): Process {
        val builder = ProcessBuilder(command)
        // PATH của app có thể cũ (vừa cài yt-dlp/ffmpeg/deno bằng winget) nên nạp lại từ hệ thống
        builder.environment()[pathVariableName(builder)] = YtDlpLocator.freshPath()
        return try {
            builder.start()
        } catch (e: Exception) {
            throw YtDlpException("Can't start yt-dlp: ${e.localizedMessage}", e)
        }
    }

    private fun pathVariableName(builder: ProcessBuilder): String {
        return builder.environment().keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
    }

    private fun drainStderr(process: Process): ConcurrentLinkedDeque<String> {
        val lastLines = ConcurrentLinkedDeque<String>()
        Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine {
                    if (it.isNotBlank()) {
                        lastLines.addLast(it)
                        while (lastLines.size > STDERR_LINES_TO_KEEP) lastLines.pollFirst()
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "yt-dlp-stderr"
            start()
        }
        return lastLines
    }

    private fun errorMessage(exitCode: Int, stderr: Collection<String>): String {
        val reason = stderr.lastOrNull { it.startsWith("ERROR") } ?: stderr.lastOrNull()
        return reason ?: "yt-dlp exited with code $exitCode"
    }

    private fun killTree(process: Process) {
        // yt-dlp.exe (PyInstaller) sinh tiến trình con, giết mỗi tiến trình cha thì tiến trình con vẫn tải tiếp
        runCatching { process.toHandle().descendants().forEach { it.destroyForcibly() } }
        if (process.isAlive) process.destroyForcibly()
    }

    private fun locateYtDlp(): File {
        return YtDlpLocator.locate()
            ?: throw YtDlpException(
                "yt-dlp was not found. Install it (winget install yt-dlp.yt-dlp) or set ABDM_YTDLP to its path."
            )
    }

    private fun parseProgress(line: String): Long? {
        if (!line.startsWith(PROGRESS_PREFIX)) return null
        return line.removePrefix(PROGRESS_PREFIX).trim().toDoubleOrNull()?.toLong()
    }

    private fun estimateSize(json: JsonObject): Long? {
        val streams = json["requested_formats"]?.jsonArray?.map { it.jsonObject } ?: listOf(json)
        val sizes = streams.map { it.number("filesize") ?: it.number("filesize_approx") }
        // chỉ tin tổng khi mọi luồng đều có số liệu
        if (sizes.isEmpty() || sizes.any { it == null }) return null
        return sizes.sumOf { it!! }.toLong().takeIf { it > 0 }
    }

    private fun JsonObject.string(key: String): String? {
        val element: JsonElement = this[key] ?: return null
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun JsonObject.number(key: String): Double? {
        return (this[key] as? JsonPrimitive)?.doubleOrNull
    }

    private companion object {
        const val PROGRESS_PREFIX = "ABDM|"
        const val METADATA_TIMEOUT_MS = 60_000L
        const val STDERR_LINES_TO_KEEP = 20
    }
}
