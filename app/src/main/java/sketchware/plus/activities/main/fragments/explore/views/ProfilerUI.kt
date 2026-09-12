package sketchware.plus.activities.main.fragments.explore.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sketchware.plus.utility.BuildStatsManager
import java.util.*

@Composable
fun ProfilerUI(showBuildStats: Boolean = true) {
    var memoryUsage by remember { mutableStateOf(getMemoryUsage()) }
    val lastBuildStats = BuildStatsManager.getLastBuildStats()
    val totalTime = BuildStatsManager.getLastBuildTotalTime()
    val peakMemory = BuildStatsManager.getPeakMemoryMB()
    val buildResult = BuildStatsManager.getLastBuildResult()

    LaunchedEffect(Unit) {
        while (true) {
            memoryUsage = getMemoryUsage()
            delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Memory Usage", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = memoryUsage.percent,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (memoryUsage.percent > 0.8f) Color.Red else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Used: ${memoryUsage.usedMB}MB / Total: ${memoryUsage.totalMB}MB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showBuildStats) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Last Build Stats", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            buildResult,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (buildResult == "Success") Color(0xFF4CAF50) else if (buildResult == "Running") Color(0xFF2196F3) else Color.Red
                        )
                    }
                    
                    if (totalTime > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Time: ${totalTime}ms", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (peakMemory > 0) {
                                Text("Peak Memory: ${peakMemory}MB", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (lastBuildStats.isEmpty()) {
                        Text("No build data available yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        lastBuildStats.entries.sortedByDescending { it.value }.forEach { (name, time) ->
                            BuildStepItem(name, time)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BuildStepItem(name: String, timeMs: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, fontSize = 12.sp)
        Text("${timeMs}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

data class MemoryInfo(val usedMB: Long, val totalMB: Long, val percent: Float)

fun getMemoryUsage(): MemoryInfo {
    val runtime = Runtime.getRuntime()
    val max = runtime.maxMemory() / (1024 * 1024)
    val total = runtime.totalMemory() / (1024 * 1024)
    val free = runtime.freeMemory() / (1024 * 1024)
    val used = total - free
    return MemoryInfo(used, max, used.toFloat() / max.toFloat())
}

object ProfilerUIHelper {
    @JvmStatic
    fun setContent(view: androidx.compose.ui.platform.ComposeView) {
        view.setContent {
            ProfilerUI(showBuildStats = true)
        }
    }

    @JvmStatic
    fun setContent(view: androidx.compose.ui.platform.ComposeView, showBuildStats: Boolean) {
        view.setContent {
            ProfilerUI(showBuildStats = showBuildStats)
        }
    }
}
