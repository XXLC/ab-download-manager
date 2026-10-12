package ir.amirab.downloader.downloaditem.ytdlp

import java.io.File

internal object YtDlpLocator {
    private const val OVERRIDE_ENV = "ABDM_YTDLP"
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** PATH hiện tại của app, cộng thêm PATH hệ thống/người dùng đọc lại từ registry trên Windows. */
    private val path: String by lazy {
        val current = System.getenv("PATH").orEmpty()
        if (!isWindows) return@lazy current
        val fresh = listOf(
            readRegistryPath("HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"),
            readRegistryPath("HKCU\\Environment"),
        )
        (current.split(File.pathSeparator) + fresh.flatMap { it.split(File.pathSeparator) })
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(File.pathSeparator)
    }

    fun freshPath(): String = path

    fun locate(): File? {
        System.getenv(OVERRIDE_ENV)?.takeIf { it.isNotBlank() }?.let {
            File(it).takeIf(File::isFile)?.let { file -> return file }
        }
        val names = if (isWindows) listOf("yt-dlp.exe") else listOf("yt-dlp")
        return path.split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { dir -> names.map { File(dir, it) } }
            .firstOrNull { it.isFile }
    }

    private fun readRegistryPath(key: String): String {
        return runCatching {
            val process = ProcessBuilder("reg", "query", key, "/v", "Path").redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            // dòng có dạng:    Path    REG_EXPAND_SZ    giá_trị
            val value = Regex("""Path\s+REG_(?:EXPAND_)?SZ\s+(.*)""").find(text)?.groupValues?.get(1)?.trim().orEmpty()
            expandEnvironmentVariables(value)
        }.getOrDefault("")
    }

    private fun expandEnvironmentVariables(value: String): String {
        return Regex("%([^%]+)%").replace(value) { match ->
            System.getenv(match.groupValues[1]) ?: match.value
        }
    }
}
