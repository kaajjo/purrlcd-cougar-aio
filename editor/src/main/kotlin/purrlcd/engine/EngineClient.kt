package purrlcd.engine

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import purrlcd.model.Scene
import purrlcd.resources.Res
import purrlcd.resources.engine_connected
import purrlcd.resources.engine_not_found
import purrlcd.resources.engine_start_cancelled
import purrlcd.resources.engine_start_exit
import purrlcd.resources.engine_waiting_permission

class EngineClient(private val enginePath: File?, private val dataPath: File,
                   pipeName: String = "PurrLCD-${System.getProperty("user.name")}") : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val pipe = "\\\\.\\pipe\\$pipeName"
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "pipe-timeout").apply { isDaemon = true } }
    private val worker = Executors.newFixedThreadPool(1) { r -> Thread(r, "pipe-worker").apply { isDaemon = true } } as ThreadPoolExecutor
    private val closer = Executors.newSingleThreadExecutor { r -> Thread(r, "pipe-close").apply { isDaemon = true } }
    private val handles = java.util.concurrent.ConcurrentHashMap.newKeySet<RandomAccessFile>()
    private val closed = AtomicBoolean(false)
    private val requestLock = Mutex()

    suspend fun request(command: String, scene: Scene? = null): EngineReply = requestLock.withLock {
        check(!closed.get()) { "Editor is closed" }
        exchange(command, scene)
    }

    private suspend fun exchange(command: String, scene: Scene?): EngineReply = suspendCancellableCoroutine { continuation ->
        val handle = AtomicReference<RandomAccessFile?>()
        val done = AtomicBoolean(false)
        val future = AtomicReference<Future<*>?>()
        fun abort() {
            future.get()?.cancel(true)
            worker.purge()
            handle.getAndSet(null)?.let { file ->
                handles.remove(file)
                runCatching { closer.execute { runCatching { file.close() } } }
            }
        }
        val timeout = timer.schedule({
            if (done.compareAndSet(false, true)) {
                continuation.resumeWithException(SocketTimeoutException("Engine did not respond within 3 seconds"))
                abort()
            }
        }, 3000, TimeUnit.MILLISECONDS)
        continuation.invokeOnCancellation {
            if (done.compareAndSet(false, true)) { timeout.cancel(false); abort() }
        }
        val task = worker.submit {
            if (!done.get()) try {
                val openDeadline = System.nanoTime() + 500_000_000L
                var opened: RandomAccessFile? = null
                while (opened == null && !done.get() && !closed.get()) {
                    try { opened = RandomAccessFile(pipe, "rw") }
                    catch (error: FileNotFoundException) {
                        if (System.nanoTime() >= openDeadline) throw error
                        Thread.sleep(20)
                    }
                }
                val file = opened ?: throw IOException("Request cancelled")
                handle.set(file)
                handles.add(file)
                if (done.get() || closed.get()) { file.close(); error("Request cancelled") }
                val reply = file.use {
                    val body = json.encodeToString(EngineRequest(command, scene)).toByteArray(Charsets.UTF_8)
                    require(body.size <= 1_048_576)
                    it.writeInt(Integer.reverseBytes(body.size))
                    it.write(body)
                    val size = Integer.reverseBytes(it.readInt())
                    require(size in 1..1_048_576) { "Invalid engine response" }
                    val response = ByteArray(size)
                    it.readFully(response)
                    json.decodeFromString<EngineReply>(response.toString(Charsets.UTF_8))
                }
                if (done.compareAndSet(false, true)) continuation.resume(reply)
            } catch (error: Exception) {
                if (done.compareAndSet(false, true)) continuation.resumeWithException(error)
            } finally {
                timeout.cancel(false)
                handle.getAndSet(null)?.let { handles.remove(it); runCatching { it.close() } }
            }
        }
        future.set(task)
        if (done.get()) { task.cancel(true); worker.purge() }
    }

    suspend fun ensureStarted(): EngineStartup {
        if (runCatching { request("status").ok }.getOrDefault(false)) return EngineStartup(true, getString(Res.string.engine_connected))
        val executable = enginePath?.takeIf { it.isFile }
            ?: return EngineStartup(false, getString(Res.string.engine_not_found))
        val process = withContext(Dispatchers.IO) {
            dataPath.mkdirs()
            ProcessBuilder(executable.absolutePath, "--data", dataPath.absolutePath)
                .directory(executable.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(File(dataPath, "engine-start.log")))
                .start()
        }
        // The native launcher owns elevation. Keep this editor at the user's normal
        // integrity level and allow time for the user to answer Windows' UAC prompt.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (System.nanoTime() < deadline) {
            if (runCatching { request("status").ok }.getOrDefault(false)) return EngineStartup(true, getString(Res.string.engine_connected))
            if (!process.isAlive && process.exitValue() != 0) {
                return EngineStartup(false, if (process.exitValue() == 1223)
                    getString(Res.string.engine_start_cancelled)
                else getString(Res.string.engine_start_exit, process.exitValue()))
            }
            kotlinx.coroutines.delay(200)
        }
        return EngineStartup(false, getString(Res.string.engine_waiting_permission))
    }

    override fun close() {
        closed.set(true)
        handles.forEach { file -> runCatching { closer.execute { runCatching { file.close() } } } }
        handles.clear()
        worker.shutdownNow()
        timer.shutdownNow()
        closer.shutdown()
    }
}
