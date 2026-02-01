package com.nirithy.luaeditor

import io.github.rosemoe.sora.lang.completion.*
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import java.util.*

/**
 * 字符串内容自动补全类
 * 当用户在字符串内（引号内）输入时提供补全建议
 * 
 * 功能：
 * - 检测是否在字符串内输入
 * - 提供 import 路径补全
 * - 提供常用字符串模板补全
 */
class StringAutoComplete {
    
    // 可导入的包/类列表
    private val importablePaths = mutableListOf<String>()
    
    // 常用字符串模板
    private val stringTemplates = mutableListOf<StringTemplate>()
    
    // 缓存已提取的字符串（避免每次都重新扫描）
    private var cachedStrings: Set<String>? = null
    private var cachedContentHash: Int = 0
    
    // 文件大小限制（超过此大小不进行字符串扫描，避免卡顿）
    private val MAX_CONTENT_SIZE = 50000 // 50KB
    
    /**
     * 字符串模板数据类
     * @param template 模板内容
     * @param description 描述
     * @param category 分类
     */
    data class StringTemplate(
        val template: String,
        val description: String,
        val category: String = "Template"
    )
    
    init {
        // 初始化默认模板
        initDefaultTemplates()
        // 初始化默认的可导入模块
        initDefaultImportablePaths()
    }
    
    /**
     * 初始化默认字符串模板
     * 添加常用的字符串模板用于自动补全
     */
    private fun initDefaultTemplates() {
        // 可以在这里添加默认的字符串模板
        // 例如常用的 import 路径等
    }
    
    /**
     * 初始化默认的可导入模块路径
     * 从 resources/lua 目录下的 .lua 文件中提取模块名
     * 用户输入 import("") 或 require("") 时会自动联想这些模块
     */
    private fun initDefaultImportablePaths() {
        // 内置 Lua 模块列表（来自 app/src/main/resources/lua/ 目录）
        val builtinModules = listOf(
            // 核心模块
            "import",
            "lazyimport",
            "loadlayout",
            "loadbitmap",
            "loadmenu",
            
            // 数据处理
            "json",
            "xml",
            "lon",
            "hex",
            
            // 网络相关
            "http",
            "ftp",
            "smtp",
            "socket",
            "ltn12",
            "mime",
            "mbox",
            
            // UI 相关
            "Colors",
            "IconDrawable",
            "LuaRecyclerAdapter",
            
            // 工具模块
            "AndLua",
            "AndLua2",
            "LuaModuleUtil",
            "Reflection",
            "functional",
            "middleclass",
            "rx",
            
            // 调试相关
            "debugger",
            "console",
            "check",
            
            // 其他
            "bmob",
            "permission",
            "qq",
            "su",
            "system",
            "options",
            "jpairs",
            "luajit",
            "luamini",
            "xcore",
            
            // 原生 SO 模块（去掉 lib 前缀和 .so 后缀）
            "lsqlite3",
            "sensor",
            "LuaBoost",
            "socket",
            "ao",
            "luv",
            "bson",
            "md5",
            "tcc",
            "md6",
            "tensor",
            "canvas",
            "memhack",
            "termux",
            "cjson",
            "memory",
            "time",
            "crypt",
            "unix",
            "extratool",
            "mmkv",
            "vmp",
            "ffi",
            "native",
            "gl",
            "network",
            "xxtea",
            "pb",
            "yyjson",
            "lfs",
            "physics",
            "zip",
            "lpeglabel",
            "rawio",
            "zlib",
            "lposix",
            "root",
            
            // 常用 Java 包前缀（方便快速导入）
            "android.widget.*",
            "android.view.*",
            "android.content.*",
            "android.graphics.*",
            "android.os.*",
            "android.app.*",
            "android.util.*",
            "android.net.*",
            "android.media.*",
            "android.animation.*",
            
            // AndroidX 常用包
            "androidx.appcompat.app.*",
            "androidx.recyclerview.widget.*",
            "androidx.viewpager2.widget.*",
            "androidx.fragment.app.*",
            "androidx.core.content.*",
            "androidx.core.view.*",
            
            // Java 常用类
            "java.io.*",
            "java.util.*",
            "java.lang.*",
            "java.net.*",
            "java.text.*",
            
            // 常用单个类
            "android.widget.LinearLayout",
            "android.widget.TextView",
            "android.widget.Button",
            "android.widget.ImageView",
            "android.widget.EditText",
            "android.widget.ListView",
            "android.widget.ScrollView",
            "android.widget.Toast",
            "android.view.View",
            "android.view.ViewGroup",
            "android.graphics.Color",
            "android.graphics.Paint",
            "android.graphics.Canvas",
            "android.graphics.Bitmap",
            "android.graphics.drawable.Drawable",
            "android.content.Intent",
            "android.content.Context",
            "android.os.Handler",
            "android.os.Bundle",
            "android.util.Log",
            "android.app.Activity",
            "android.app.AlertDialog"
        )
        
        importablePaths.addAll(builtinModules)
    }
    
    /**
     * 添加可导入的路径
     * @param paths 路径列表
     */
    fun addImportablePaths(paths: Collection<String>) {
        importablePaths.addAll(paths)
    }
    
    /**
     * 设置可导入的路径（替换现有列表）
     * @param paths 路径列表
     */
    fun setImportablePaths(paths: Collection<String>) {
        importablePaths.clear()
        // 重新初始化内置模块
        initDefaultImportablePaths()
        // 添加用户自定义路径
        importablePaths.addAll(paths)
    }
    
    /**
     * 清空并重置为默认模块列表
     */
    fun resetToDefaultPaths() {
        importablePaths.clear()
        initDefaultImportablePaths()
    }
    
    /**
     * 从文件列表中添加模块名（自动去除 .lua 后缀）
     * @param luaFiles lua 文件名列表
     */
    fun addLuaModulesFromFiles(luaFiles: Collection<String>) {
        for (file in luaFiles) {
            val moduleName = if (file.endsWith(".lua")) {
                file.removeSuffix(".lua")
            } else {
                file
            }
            if (moduleName.isNotEmpty() && !importablePaths.contains(moduleName)) {
                importablePaths.add(moduleName)
            }
        }
    }
    
    /**
     * 添加字符串模板
     * @param template 模板
     */
    fun addTemplate(template: StringTemplate) {
        stringTemplates.add(template)
    }
    
    /**
     * 添加字符串模板
     * @param content 模板内容
     * @param description 描述
     * @param category 分类
     */
    fun addTemplate(content: String, description: String, category: String = "Template") {
        stringTemplates.add(StringTemplate(content, description, category))
    }
    
    /**
     * 检查当前位置是否在字符串内
     * 正确处理转义字符，包括连续反斜杠的情况
     * @param content 内容引用
     * @param position 位置
     * @return 如果在字符串内返回字符串起始引号字符，否则返回 null
     */
    fun isInsideString(content: ContentReference, position: CharPosition): Char? {
        val line = content.getLine(position.line)
        val col = position.column
        
        // 边界检查
        if (line.length == 0 || col <= 0) {
            return null
        }
        
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0
        
        // 遍历到光标位置之前的所有字符
        val endPos = minOf(col, line.length)
        while (i < endPos) {
            val ch = line[i]
            
            // 计算当前位置前面连续反斜杠的数量
            // 只有奇数个反斜杠才是有效转义
            var backslashCount = 0
            var j = i - 1
            while (j >= 0 && line[j] == '\\') {
                backslashCount++
                j--
            }
            val isEscaped = backslashCount % 2 == 1
            
            when {
                ch == '"' && !isEscaped && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                ch == '\'' && !isEscaped && !inDoubleQuote -> inSingleQuote = !inSingleQuote
            }
            i++
        }
        
        return when {
            inDoubleQuote -> '"'
            inSingleQuote -> '\''
            else -> null
        }
    }
    
    /**
     * 获取字符串内的前缀（从引号开始到当前位置）
     * @param content 内容引用
     * @param position 位置
     * @return 字符串内的前缀
     */
    fun getStringPrefix(content: ContentReference, position: CharPosition): String {
        val line = content.getLine(position.line)
        val col = position.column
        
        // 边界检查
        if (line.length == 0 || col <= 0) {
            return ""
        }
        
        // 向前查找最近的未转义引号
        var start = minOf(col, line.length) - 1
        while (start >= 0) {
            val ch = line[start]
            
            // 计算当前引号前面连续反斜杠的数量
            var backslashCount = 0
            var j = start - 1
            while (j >= 0 && line[j] == '\\') {
                backslashCount++
                j--
            }
            val isEscaped = backslashCount % 2 == 1
            
            if ((ch == '"' || ch == '\'') && !isEscaped) {
                break
            }
            start--
        }
        
        // 返回引号后到光标位置的内容
        return if (start >= 0 && start < col - 1) {
            line.subSequence(start + 1, minOf(col, line.length)).toString()
        } else if (start >= 0 && start == col - 1) {
            // 光标紧跟在引号后面，返回空字符串（但仍然触发补全）
            ""
        } else {
            ""
        }
    }
    
    /**
     * 获取字符串补全建议
     * @param prefix 前缀
     * @param contextKeyword 上下文关键字（如 import, require 等）
     * @param fileContent 当前文件内容（用于提取已有字符串）
     * @return 补全项列表
     */
    fun getCompletionItems(prefix: String, contextKeyword: String?, fileContent: String? = null): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()
        
        // 根据上下文提供不同的补全
        when (contextKeyword?.lowercase(Locale.ROOT)) {
            "import", "require" -> {
                // import/require: 提供模块补全
                addImportCompletions(items, prefix)
            }
            "dofile", "loadfile" -> {
                // dofile/loadfile: 只提供文件路径补全
                addFilePathCompletions(items, prefix)
            }
            else -> {
                // 其他函数: 从当前文件提取已有字符串进行补全
                if (fileContent != null) {
                    addExistingStringCompletions(items, prefix, fileContent)
                }
            }
        }
        
        return items
    }
    
    /**
     * 从文件内容中提取已有字符串并添加到补全列表
     * 使用缓存机制避免重复扫描，并限制文件大小防止卡顿
     * @param items 补全项列表
     * @param prefix 用户输入的前缀
     * @param fileContent 文件内容
     */
    private fun addExistingStringCompletions(items: MutableList<CompletionItem>, prefix: String, fileContent: String) {
        // 文件太大时跳过字符串扫描，避免卡顿
        if (fileContent.length > MAX_CONTENT_SIZE) {
            return
        }
        
        val prefixLower = prefix.lowercase(Locale.ROOT)
        
        // 使用缓存：如果文件内容没变，直接使用缓存的字符串集合
        val contentHash = fileContent.hashCode()
        val existingStrings: Set<String> = if (contentHash == cachedContentHash && cachedStrings != null) {
            cachedStrings!!
        } else {
            // 重新扫描并缓存
            val strings = extractStringsFromContent(fileContent)
            cachedStrings = strings
            cachedContentHash = contentHash
            strings
        }
        
        // 过滤并排序
        val startsWithList = mutableListOf<String>()
        val containsList = mutableListOf<String>()
        
        for (str in existingStrings) {
            val strLower = str.lowercase(Locale.ROOT)
            when {
                strLower.startsWith(prefixLower) -> startsWithList.add(str)
                prefixLower.isNotEmpty() && strLower.contains(prefixLower) -> containsList.add(str)
                prefixLower.isEmpty() -> startsWithList.add(str)
            }
        }
        
        // 添加补全项（限制数量）
        for (str in startsWithList.sorted().take(20)) {
            items.add(
                SimpleCompletionItem(str, "字符串", prefix.length, str)
                    .kind(CompletionItemKind.Text)
            )
        }
        
        for (str in containsList.sorted().take(10)) {
            items.add(
                SimpleCompletionItem(str, "字符串", prefix.length, str)
                    .kind(CompletionItemKind.Text)
            )
        }
    }
    
    /**
     * 从文件内容中提取所有字符串字面量
     * @param fileContent 文件内容
     * @return 字符串集合
     */
    private fun extractStringsFromContent(fileContent: String): Set<String> {
        val existingStrings = mutableSetOf<String>()
        
        // 匹配双引号字符串
        val doubleQuotePattern = Regex("\"([^\"\\\\]|\\\\.)*\"")
        // 匹配单引号字符串
        val singleQuotePattern = Regex("'([^'\\\\]|\\\\.)*'")
        
        // 提取双引号字符串
        doubleQuotePattern.findAll(fileContent).forEach { match ->
            val str = match.value
            if (str.length > 2) {
                val content = str.substring(1, str.length - 1)
                if (content.isNotEmpty() && content.length <= 100) {
                    existingStrings.add(content)
                }
            }
        }
        
        // 提取单引号字符串
        singleQuotePattern.findAll(fileContent).forEach { match ->
            val str = match.value
            if (str.length > 2) {
                val content = str.substring(1, str.length - 1)
                if (content.isNotEmpty() && content.length <= 100) {
                    existingStrings.add(content)
                }
            }
        }
        
        return existingStrings
    }
    
    /**
     * 添加文件路径补全项（用于 dofile/loadfile）
     * @param items 补全项列表
     * @param prefix 用户输入的前缀
     */
    private fun addFilePathCompletions(items: MutableList<CompletionItem>, prefix: String) {
        // 常用文件路径前缀
        val filePaths = listOf(
            "/sdcard/",
            "/sdcard/XCLUA/",
            "/sdcard/Download/",
            "/data/data/",
            "/storage/emulated/0/"
        )
        
        val prefixLower = prefix.lowercase(Locale.ROOT)
        
        for (path in filePaths) {
            if (path.lowercase(Locale.ROOT).startsWith(prefixLower) || prefixLower.isEmpty()) {
                items.add(
                    SimpleCompletionItem(path, "路径", prefix.length, path)
                        .kind(CompletionItemKind.File)
                )
            }
        }
    }
    
    /**
     * 添加导入路径补全项
     * 匹配逻辑：优先匹配以前缀开头的项，然后是包含前缀的项
     * @param items 补全项列表
     * @param prefix 用户输入的前缀
     */
    private fun addImportCompletions(items: MutableList<CompletionItem>, prefix: String) {
        val prefixLower = prefix.lowercase(Locale.ROOT)
        
        // 分离：以前缀开头的 vs 仅包含前缀的
        val startsWithList = mutableListOf<String>()
        val containsList = mutableListOf<String>()
        
        for (path in importablePaths) {
            val pathLower = path.lowercase(Locale.ROOT)
            when {
                pathLower.startsWith(prefixLower) -> startsWithList.add(path)
                prefixLower.isNotEmpty() && pathLower.contains(prefixLower) -> containsList.add(path)
                prefixLower.isEmpty() -> startsWithList.add(path) // 空前缀显示所有
            }
        }
        
        // 先添加以前缀开头的项（更相关）
        for (path in startsWithList.sorted()) {
            val (label, kind) = getModuleTypeInfo(path)
            items.add(
                SimpleCompletionItem(path, label, prefix.length, path)
                    .kind(kind)
            )
        }
        
        // 再添加仅包含前缀的项
        for (path in containsList.sorted()) {
            val (label, kind) = getModuleTypeInfo(path)
            items.add(
                SimpleCompletionItem(path, label, prefix.length, path)
                    .kind(kind)
            )
        }
    }
    
    /**
     * 根据模块路径判断其类型，返回对应的标签和补全项类型
     * @param path 模块路径
     * @return Pair<标签文本, CompletionItemKind>
     */
    private fun getModuleTypeInfo(path: String): Pair<String, CompletionItemKind> {
        return when {
            // Java/Android 通配符包 (以 .* 结尾)
            path.endsWith(".*") -> {
                when {
                    path.startsWith("android.") -> Pair("Android 包", CompletionItemKind.Module)
                    path.startsWith("androidx.") -> Pair("AndroidX 包", CompletionItemKind.Module)
                    path.startsWith("java.") -> Pair("Java 包", CompletionItemKind.Module)
                    else -> Pair("Java 包", CompletionItemKind.Module)
                }
            }
            // 完整的 Java/Android 类路径 (包含点号且不以 .* 结尾)
            path.contains(".") && !path.endsWith(".*") -> {
                when {
                    path.startsWith("android.widget.") -> Pair("Android 控件", CompletionItemKind.Class)
                    path.startsWith("android.view.") -> Pair("Android 视图", CompletionItemKind.Class)
                    path.startsWith("android.graphics.") -> Pair("Android 图形", CompletionItemKind.Class)
                    path.startsWith("android.content.") -> Pair("Android 内容", CompletionItemKind.Class)
                    path.startsWith("android.os.") -> Pair("Android 系统", CompletionItemKind.Class)
                    path.startsWith("android.app.") -> Pair("Android 应用", CompletionItemKind.Class)
                    path.startsWith("android.util.") -> Pair("Android 工具", CompletionItemKind.Class)
                    path.startsWith("android.") -> Pair("Android 类", CompletionItemKind.Class)
                    path.startsWith("androidx.") -> Pair("AndroidX 类", CompletionItemKind.Class)
                    path.startsWith("java.") -> Pair("Java 类", CompletionItemKind.Class)
                    else -> Pair("Java 类", CompletionItemKind.Class)
                }
            }
            // Lua 内置模块 (无点号的简单名称)
            else -> Pair("Lua 模块", CompletionItemKind.Module)
        }
    }
    
    /**
     * 添加模板补全项
     */
    private fun addTemplateCompletions(items: MutableList<CompletionItem>, prefix: String) {
        val prefixLower = prefix.lowercase(Locale.ROOT)
        
        for (template in stringTemplates) {
            if (template.template.lowercase(Locale.ROOT).startsWith(prefixLower) || prefixLower.isEmpty()) {
                items.add(
                    SimpleCompletionItem(
                        template.template, 
                        template.description, 
                        prefix.length, 
                        template.template
                    ).kind(CompletionItemKind.Text)
                )
            }
        }
    }
    
    /**
     * 检测行中最近的关键字（用于判断上下文）
     * 支持以下格式：
     * - require("...") / import("...")
     * - require "..." / import "..."
     * - require"..." / import"..."
     * - require('...') / import('...')
     * - require '...' / import '...'
     * - require'...' / import'...'
     * @param line 行内容
     * @param column 列位置
     * @return 最近的关键字，如果没有则返回 null
     */
    fun detectContextKeyword(line: CharSequence, column: Int): String? {
        val lineStr = line.toString().substring(0, minOf(column, line.length))
        
        // 查找常见的上下文关键字
        val keywords = listOf("import", "require", "dofile", "loadfile")
        
        for (keyword in keywords) {
            // 匹配: keyword 后面可选空格、可选括号、可选空格，然后是引号
            // 例如: require("  require "  require"  require('  require '  require'
            val pattern = Regex("\\b$keyword\\s*\\(?\\s*[\"']")
            if (pattern.containsMatchIn(lineStr)) {
                return keyword
            }
        }
        
        return null
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: StringAutoComplete? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): StringAutoComplete {
            return instance ?: synchronized(this) {
                instance ?: StringAutoComplete().also { instance = it }
            }
        }
    }
}
