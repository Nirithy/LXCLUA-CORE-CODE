package com.nirithy.luaeditor.tools

import android.content.Context
import android.util.Log
import com.difierline.lua.LuaActivity
import com.nirithy.luaeditor.CompletionName
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

object ScanUtil {
    private const val TAG = "ScanUtil"
    private val ANONYMOUS_CLASS_REGEX = ".*\\$\\d+$".toRegex()

    interface ProgressCallback {
        fun onStart()
        fun onProgress(message: String, progress: Int)
        fun onFinish()
        fun onError(error: String)
    }

    @JvmStatic
    fun scanLibsDirectory(
        context: LuaActivity,
        mdir: String?,
        callback: ProgressCallback
    ) {
        if (mdir == null) {
            safeCallback(context, callback) { it.onError("mdir is null") }
            return
        }

        val libsDir = File(mdir, "libs").takeIf { it.exists() && it.isDirectory }
            ?: run {
                safeCallback(context, callback) { 
                    it.onError("libs directory not found: ${File(mdir, "libs").absolutePath}") 
                }
                return
            }

        val dexFiles = libsDir.listFiles { _, name -> 
            name.endsWith(".dex") || name.endsWith(".jar") 
        }?.takeIf { it.isNotEmpty() }
            ?: run {
                safeCallback(context, callback) { it.onFinish() }  // 没有文件直接完成
                return
            }

        Thread {
            try {
                // 加载已有的扫描进度
                val scannedFiles = loadScannedFilesList(context).toMutableSet()
                val (tmpClassMap, tmpBase) = copyExistingData(context)
                val scanner = ClassMethodScanner()

                val total = dexFiles.size
                var scanned = 0
                var hasNewFiles = false

                dexFiles.forEachIndexed { index, file ->
                    val fileId = "${file.absolutePath}_${file.lastModified()}"

                    if (scannedFiles.contains(fileId)) {
                        // 已扫描过，跳过
                        Log.d(TAG, "Skipping already scanned: ${file.name}")
                    } else {
                        // 新文件，需要扫描
                        hasNewFiles = true
                        val classNames = ClassMethodScanner.getClassNames(file.absolutePath)
                            .asSequence()
                            .filterNot { it.contains("$") }
                            .map { it.replace('$', '.') }
                            .toList()

                        if (classNames.isNotEmpty()) {
                            classNames.forEach { cls ->
                                val simple = getSimpleName(cls)
                                tmpClassMap.getOrPut(simple) { mutableListOf() }.add(cls)
                            }
                            tmpBase.putAll(scanner.scanClassesAndMethods(classNames, file.absolutePath))
                        }

                        // 记录已扫描
                        scannedFiles.add(fileId)
                    }

                    scanned++
                    val progress = (100f * scanned / total).toInt()
                    safeCallback(context, callback) { 
                        it.onProgress("Scanning ${file.name}", progress) 
                    }
                }

                // 如果有新文件才保存
                if (hasNewFiles) {
                    saveResults(context, tmpBase, tmpClassMap)
                    saveScannedFilesList(context, scannedFiles)
                }

                safeCallback(context, callback) { it.onFinish() }
            } catch (e: Throwable) {
                Log.e(TAG, "scanLibsDirectory error", e)
                safeCallback(context, callback) { 
                    it.onError(e.message ?: "Unknown error") 
                }
            }
        }.start()
    }

    private fun loadScannedFilesList(context: LuaActivity): Set<String> {
        return try {
            val file = File(context.cacheDir, "scanned_files.txt")
            if (file.exists()) {
                file.readLines().toSet()
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading scanned files list", e)
            emptySet()
        }
    }

    private fun saveScannedFilesList(context: LuaActivity, files: Set<String>) {
        try {
            val file = File(context.cacheDir, "scanned_files.txt")
            file.writeText(files.joinToString("\n"))
        } catch (e: Exception) {
            Log.w(TAG, "Error saving scanned files list", e)
        }
    }

    private fun getSimpleName(fullName: String): String {
        return fullName.substringAfterLast('.', fullName)
    }

    @JvmStatic
    fun generateCompleteData(context: LuaActivity, callback: ProgressCallback) {
        safeCallback(context, callback) { it.onStart() }
        Thread {
            try {
                val tracker = ProgressTracker(context, callback)
                tracker.updateProgress("Initializing", 0)

                val safeLoad = PackageUtil.load(context)?.toMutableMap() 
                    ?: throw IllegalStateException("PackageUtil.load() returned null")
                
                // 添加Android样式
                safeLoad.getOrPut("R\$style") { mutableListOf() }
                    .addIfNotContains("android.R\$style")

                tracker.updateProgress("Package loaded", 10)
                
                expandInnerClasses(safeLoad, tracker)
                val classMap2 = buildClassMap(safeLoad, tracker)
                val classList = safeLoad.values.flatten()
                val baseMap = scanClassMethods(classList, tracker)

                saveResults(context, baseMap, classMap2)
                tracker.updateProgress("Complete", 100)
                safeCallback(context, callback) { it.onFinish() }
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                Log.e(TAG, "Error generating data", e)
                safeCallback(context, callback) { it.onError("Error: $errorMsg") }
            }
        }.start()
    }

    // region Helper Functions
    private fun copyExistingData(context: LuaActivity): 
        Pair<MutableMap<String, MutableList<String>>, MutableMap<String, HashMap<String, CompletionName>>> {
        
        val tmpClassMap = mutableMapOf<String, MutableList<String>>()
        val tmpBase = mutableMapOf<String, HashMap<String, CompletionName>>()

        try {
            LuaActivity::class.java.getDeclaredField("classMap2").apply {
                isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (get(context) as? Map<String, List<String>>)?.forEach { (k, v) ->
                    tmpClassMap[k] = v.toMutableList()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error copying classMap2", e)
        }


        try {
            LuaActivity::class.java.getDeclaredField("base").apply {
                isAccessible = true
                (get(context) as? Map<*, *>)?.forEach { (k, v) ->
                    val key = k as? String ?: return@forEach
                    val value = v as? HashMap<String, CompletionName> ?: return@forEach
                    tmpBase[key] = HashMap(value)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error copying base", e)
        }


        return Pair(tmpClassMap, tmpBase)
    }

    private fun saveResults(
        context: LuaActivity,
        baseMap: Map<String, HashMap<String, CompletionName>>,
        classMap: Map<String, List<String>>
    ) {
        val baseToSave = HashMap(baseMap)
        val classMapToSave = HashMap(classMap.mapValues { it.value.toList() })

        try {
            LuaActivity::class.java.getDeclaredField("base").apply {
                isAccessible = true
                set(context, baseToSave)
            }
            LuaActivity::class.java.getDeclaredField("classMap2").apply {
                isAccessible = true
                set(context, classMapToSave)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving results via reflection", e)
        }

        CompleteHashmapUtils.saveHashMapToFile(context, baseToSave, "complete.base")
        CompleteHashmapUtils.saveHashMapToFile2(context, classMapToSave, "complete2.base")
    }

    private fun expandInnerClasses(
        load: MutableMap<String, MutableList<String>>,
        tracker: ProgressTracker
    ) {
        val keys = load.keys.toList()
        val total = keys.size
        val skipClasses = setOf("RSASecurity", "LuaApplication", "axmleditor")

        keys.forEachIndexed { index, key ->
            val classList = load[key] ?: mutableListOf()
            
            // 使用临时集合存储要添加的新类
            val newClasses = mutableListOf<String>()
            
            // 创建副本进行迭代（避免ConcurrentModificationException）
            classList.toList().forEach { className ->
                if (skipClasses.any { className.contains(it) }) return@forEach
                try {
                    safeLoadClass(className)
                    Class.forName(className).classes.forEach { innerClass ->
                        val fullInnerName = "$className$${innerClass.simpleName}"
                        // 检查是否已存在（包括原始列表和新添加的）
                        if (!classList.contains(fullInnerName) && !newClasses.contains(fullInnerName)) {
                            newClasses.add(fullInnerName)
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Skipping class: $className", e)
                }
            }

            // 批量添加新类
            classList.addAll(newClasses)

            val progress = 10 + (30f * (index + 1) / total).toInt()
            tracker.updateProgress("Scanning $key", progress)
        }
    }

    @Throws(ClassNotFoundException::class)
    private fun safeLoadClass(className: String) {
        ScanUtil::class.java.classLoader?.loadClass(className)
            ?: Class.forName(className, false, null)
    }

    private fun buildClassMap(
        load: Map<String, List<String>>,
        tracker: ProgressTracker
    ): Map<String, List<String>> {
        val classMap2 = mutableMapOf<String, MutableList<String>>()
        val keys = load.keys.toList()
        val total = keys.size

        keys.forEachIndexed { index, key ->
            val processedList = mutableListOf<String>()
            load[key]?.forEach { originalName ->
                // 不再替换$字符，保持原始类名
                processedList.add(originalName)
                
                // 仅对R类特殊处理
                if (originalName.startsWith("com.difierline.lua.lxclua.R")) {
                    val rKey = originalName.replace("com.difierline.lua.lxclua.R.", "R.")
                    classMap2.getOrPut(rKey) { mutableListOf() }.add(originalName)
                }
            }

            val normalizedKey = key.replace('$', '.')
            classMap2[normalizedKey] = processedList

            val progress = 40 + (25f * (index + 1) / total).toInt()
            tracker.updateProgress("Building $key", progress)
        }
        
        return classMap2
    }

    private fun scanClassMethods(
        classList: List<String>,
        tracker: ProgressTracker
    ): Map<String, HashMap<String, CompletionName>> {
        val baseMap = mutableMapOf<String, HashMap<String, CompletionName>>()
        val total = classList.size
        val batchSize = max(1, total.coerceAtMost(1000))  // 限制批处理大小
        
        if (total == 0) return baseMap

        val scanner = ClassMethodScanner()
        var processed = 0

        while (processed < total) {
            val end = min(processed + batchSize, total)
            val batch = classList.subList(processed, end)
            
            try {
                baseMap.putAll(scanner.scanClassesAndMethods(batch, null))
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning batch", e)
            }
            
            processed = end
            val progress = 65 + (25f * processed / total).toInt()
            tracker.updateProgress("Extracting ($processed/$total)", progress)
        }
        
        return baseMap
    }
    // endregion

    // region Extensions
    private fun <T> MutableCollection<T>.addIfNotContains(element: T) {
        if (!contains(element)) add(element)
    }
    // endregion

    private class ProgressTracker(
        private val context: LuaActivity,
        private val callback: ProgressCallback
    ) {
        private var lastProgress = -1
        
        fun updateProgress(message: String, progress: Int) {
            val normalized = progress.coerceIn(0, 100)
            if (normalized != lastProgress) {
                lastProgress = normalized
                runOnUiThreadSafe {
                    try {
                        callback.onProgress(message, normalized)
                    } catch (e: Exception) {
                        Log.e(TAG, "Progress callback error", e)
                    }
                }
            }
        }
        
        private fun runOnUiThreadSafe(action: () -> Unit) {
            if (!context.isFinishing && !context.isDestroyed) {
                context.runOnUiThread(action)
            } else {
                Log.w(TAG, "Activity destroyed, skipping UI callback")
            }
        }
    }

    // 安全执行回调的辅助方法
    private fun safeCallback(
        context: LuaActivity,
        callback: ProgressCallback,
        action: (ProgressCallback) -> Unit
    ) {
        if (!context.isFinishing && !context.isDestroyed) {
            context.runOnUiThread {
                try {
                    action(callback)
                } catch (e: Exception) {
                    Log.e(TAG, "Callback error", e)
                }
            }
        } else {
            Log.w(TAG, "Activity destroyed, skipping callback")
        }
    }
}