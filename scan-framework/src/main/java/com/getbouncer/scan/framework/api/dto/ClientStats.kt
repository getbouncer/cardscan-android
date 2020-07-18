package com.getbouncer.scan.framework.api.dto

import com.getbouncer.scan.framework.RepeatingTaskStats
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.TaskStats
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatsPayload(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("scan_id") val scanId: String?,
    @SerialName("payload_version") val payloadVersion: Int = 2,
    @SerialName("device") val device: ClientDevice,
    @SerialName("app") val app: AppInfo,
    @SerialName("scan_stats") val scanStats: ScanStatistics
)

@Serializable
data class ScanStatistics(
    @SerialName("tasks") val tasks: Map<String, List<TaskStatistics>>,
    @SerialName("repeating_tasks") val repeatingTasks: Map<String, RepeatingTaskStatistics>
) {
    companion object {
        fun fromStats(): ScanStatistics {
            return ScanStatistics(
                tasks = Stats.getTasks().mapValues { entry ->
                    entry.value.map { TaskStatistics.fromTaskStats(it) }
                },
                repeatingTasks = Stats.getRepeatingTasks().mapValues {
                    RepeatingTaskStatistics.fromRepeatingTaskStats(it.value)
                }
            )
        }
    }
}

@Serializable
data class TaskStatistics(
    @SerialName("started_at_ms") val startedAtMs: Long,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("result") val result: String?
) {
    companion object {
        fun fromTaskStats(taskStats: TaskStats) = TaskStatistics(
            startedAtMs = taskStats.started.toMillisecondsSinceEpoch(),
            durationMs = taskStats.duration.inMilliseconds.toLong(),
            result = taskStats.result
        )
    }
}

@Serializable
data class RepeatingTaskStatistics(
    @SerialName("executions") val executions: Int,
    @SerialName("start_time_ms") val startTimeMs: Long,
    @SerialName("total_duration_ms") val totalDurationMs: Long,
    @SerialName("total_cpu_duration_ms") val totalCpuDurationMs: Long,
    @SerialName("average_duration_ms") val averageDurationMs: Long,
    @SerialName("minimum_duration_ms") val minimumDurationMs: Long,
    @SerialName("maximum_duration_ms") val maximumDurationMs: Long,
    @SerialName("results") val results: Map<String, Int>
) {
    companion object {
        fun fromRepeatingTaskStats(repeatingTaskStats: RepeatingTaskStats) = RepeatingTaskStatistics(
            executions = repeatingTaskStats.executions,
            startTimeMs = repeatingTaskStats.startedAt.toMillisecondsSinceEpoch(),
            totalDurationMs = repeatingTaskStats.totalDuration.inMilliseconds.toLong(),
            totalCpuDurationMs = repeatingTaskStats.totalCpuDuration.inMilliseconds.toLong(),
            averageDurationMs = repeatingTaskStats.averageDuration().inMilliseconds.toLong(),
            minimumDurationMs = repeatingTaskStats.minimumDuration.inMilliseconds.toLong(),
            maximumDurationMs = repeatingTaskStats.maximumDuration.inMilliseconds.toLong(),
            results = repeatingTaskStats.results
        )
    }
}
