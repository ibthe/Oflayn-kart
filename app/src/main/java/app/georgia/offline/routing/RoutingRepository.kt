package app.georgia.offline.routing

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/** Outcome of opening the bundled road graph: a ready [router] or a person-readable [error]. */
class RoutingLoadResult(val router: Router?, val error: String?)

/**
 * Opens `georgia-roads.bin` from the APK. The graph is optional: the build can succeed without it,
 * in which case routes are reported as unavailable instead of failing the app.
 */
object RoutingRepository {
    private const val ASSET_DIR = "offline"
    private const val ASSET_NAME = "georgia-roads.bin"
    private val lock = Any()
    private var router: Router? = null

    fun isBundled(context: Context): Boolean =
        context.assets.list(ASSET_DIR)?.contains(ASSET_NAME) == true

    /** Blocking (copies about tens of megabytes on first use and builds the snap grid): call off the main thread. */
    fun load(context: Context): RoutingLoadResult = synchronized(lock) {
        router?.let { return RoutingLoadResult(it, null) }
        if (!isBundled(context)) {
            return RoutingLoadResult(null, "Маршруты недоступны: граф дорог не встроен в эту сборку приложения.")
        }
        try {
            val file = install(context)
            val buffer = RandomAccessFile(file, "r").channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
            val created = Router(RoadGraph(buffer))
            created.prepare()
            router = created
            RoutingLoadResult(created, null)
        } catch (error: Exception) {
            RoutingLoadResult(null, "Маршруты недоступны: не удалось открыть граф дорог (${error.message}).")
        }
    }

    private fun install(context: Context): File {
        val directory = File(context.filesDir, "routing")
        val target = File(directory, ASSET_NAME)
        val stamp = File(directory, "installed.version")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = "${PackageInfoCompat.getLongVersionCode(info)}-${RoadGraph.FORMAT_VERSION}"
        if (!(target.isFile && stamp.isFile && stamp.readText() == version)) {
            directory.mkdirs()
            val temporary = File(directory, "$ASSET_NAME.tmp")
            context.assets.open("$ASSET_DIR/$ASSET_NAME").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            stamp.writeText(version)
        }
        return target
    }
}
