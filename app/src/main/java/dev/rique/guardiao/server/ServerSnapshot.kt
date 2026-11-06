package dev.rique.guardiao.server

data class ContainerInfo(
    val name: String,
    val status: String,
    val cpuPercent: Double = 0.0,
    val memBytes: Long = 0,
    val uptime: String = "",
)

data class FsInfo(val mount: String, val percent: Double)

data class SensorInfo(val label: String, val celsius: Double, val warning: Double?, val critical: Double?)

data class ServerSnapshot(
    val containers: List<ContainerInfo>,
    val cpuPercent: Double,
    val fs: List<FsInfo>,
    val sensors: List<SensorInfo>,
    val memPercent: Double? = null,
    val uptime: String? = null,
)
