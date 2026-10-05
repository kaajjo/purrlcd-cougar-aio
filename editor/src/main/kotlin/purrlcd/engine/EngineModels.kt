package purrlcd.engine

import kotlinx.serialization.Serializable
import purrlcd.model.Scene

@Serializable
data class EngineStatus(
    val connected: Boolean = false,
    val stockRunning: Boolean = false,
    val cpuTemp: Double? = null,
    val gpuTemp: Double? = null,
    val cpuUsage: Double = 0.0,
    val ramPercent: Double = 0.0,
    val message: String = "",
    val sensorStatus: String = "",
    val connecting: Boolean = false
)

@Serializable
data class EngineReply(
    val ok: Boolean = false,
    val scene: Scene? = null,
    val status: EngineStatus? = null,
    val previewPath: String? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class EngineRequest(val command: String, val scene: Scene? = null)

data class EngineStartup(val ready: Boolean, val message: String)
