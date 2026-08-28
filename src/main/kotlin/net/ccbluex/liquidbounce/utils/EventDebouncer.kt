package net.ccbluex.liquidbounce.utils

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 事件防抖工具，用于合并短时间内的多次相同事件，减少前端渲染压力。
 */
object EventDebouncer {
    private val debounceMap = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 防抖调用
     * @param key 事件唯一标识，如 "scaleFactor"
     * @param delayMillis 延迟时间（毫秒）
     * @param action 实际执行的动作
     */
    fun debounce(key: String, delayMillis: Long = 100L, action: suspend () -> Unit) {
        debounceMap[key]?.cancel()
        debounceMap[key] = scope.launch {
            delay(delayMillis)
            action()
            debounceMap.remove(key)
        }
    }

    /**
     * 取消某个事件的防抖任务
     */
    fun cancel(key: String) {
        debounceMap[key]?.cancel()
        debounceMap.remove(key)
    }
}
