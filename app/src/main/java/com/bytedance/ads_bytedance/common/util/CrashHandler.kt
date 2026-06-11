package com.bytedance.ads_bytedance.common.util

import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未处理异常捕获器
 *
 * ## 设计目的
 * 捕获所有未被 try-catch 处理的异常（即会导致 App 崩溃的异常），
 * 在进程终止前将堆栈信息写入 logcat 和内部存储文件，
 * 用于事后排查和崩溃率统计。
 *
 * ## 如何工作
 * 1. 安装：在 [com.bytedance.ads_bytedance.AdsApplication.onCreate] 中
 *    通过 `Thread.setDefaultUncaughtExceptionHandler` 替换默认处理器
 * 2. 捕获异常 → 写入日志 (logcat) → 写入崩溃文件 (内部存储) → 交给系统默认处理器 → 进程终止
 *
 * ## 为什么不能"防止崩溃"
 * App 进程状态已不可信（可能内存已损坏），安全做法是记录后让进程
 * 自然终止。Android 系统会重新启动进程（如果用户在最近任务中再次打开）。
 *
 * ## 参考
 * - [Thread.UncaughtExceptionHandler] JavaDoc
 * - tech.md §5.2 — 稳定性目标：Crash 率 0%
 */
class CrashHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"

        /** 崩溃日志文件名前缀 */
        private const val CRASH_FILE_PREFIX = "crash_"

        /** 崩溃日志目录名 */
        private const val CRASH_DIR_NAME = "crash_logs"
    }

    /**
     * 捕获未处理异常。
     *
     * 调用时机：任何线程抛出未被捕获的异常时，JVM 调用此方法。
     *
     * @param t 发生异常的线程
     * @param e 异常对象
     */
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            // 1. 写入 logcat（即时可见 / adb logcat 可查看）
            Log.e(TAG, "未处理异常 → 线程: ${t.name}", e)

            // 2. 写入内部存储文件（持久化，事后可通过文件管理器/文件提取获取）
            writeCrashToLogcat(e)

        } catch (_: Exception) {
            // 记录过程自身失败 → 不能再做任何事（可能进一步崩溃）
        } finally {
            // 3. 交给系统默认处理器（显示 ANR 对话框 / 终止进程）
            defaultHandler.uncaughtException(t, e)
        }
    }

    /**
     * 将异常堆栈写入 logcat（通过 Log.e 带时间戳）。
     *
     * 给 [Exception] 的 [printStackTrace] 包装一层，
     * 使堆栈通过 Android logcat 输出，可用 `adb logcat` 或 Logcat 面板查看。
     */
    private fun writeCrashToLogcat(e: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        Log.e(TAG, "崩溃时间: $timestamp\n${sw}")
    }
}
