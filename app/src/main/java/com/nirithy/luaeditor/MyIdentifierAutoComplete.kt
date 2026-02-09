package com.nirithy.luaeditor

import com.nirithy.luaeditor.tools.ClassMethodScanner
import com.nirithy.luaeditor.tools.parser.LuaLexer
import com.nirithy.luaeditor.tools.parser.LuaParser
import com.difierline.lua.LuaUtil
import io.github.rosemoe.sora.lang.completion.*
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextUtils
import io.github.rosemoe.sora.util.MutableInt
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * Lua代码自动补全核心类，提供：
 * - 关键字补全
 * - 标识符补全
 * - 类/方法补全
 * - 导入语句补全
 * - 包函数补全
 */
class MyIdentifierAutoComplete {
    // 过时的默认比较器（按描述和标签排序）
    @Deprecated("")
    private val COMPARATOR = Comparator<CompletionItem> { item1, item2 ->
        val descCompare = asString(item1.desc).compareTo(asString(item2.desc))
        if (descCompare < 0) return@Comparator 1
        if (descCompare > 0) return@Comparator -1
        asString(item1.label).compareTo(asString(item2.label))
    }

    // 基础映射表：存储类名到其成员的映射
    var basemap: HashMap<String, HashMap<String, CompletionName>>? = null
    // 类映射表：存储类名到其描述的映射
    var classmap: HashMap<String, List<String>>? = null
    // 导入列表：存储导入的类/方法
    var importlist: HashMap<String, HashMap<String, CompletionName>>? = null
    // 导入映射表
    var importmap: HashMap<String, List<String>>? = null
    // 关键字映射表（用于快速查找）
    private var keywordMap: Map<String, Any>? = null
    // 当前关键字数组
    private var keywords: Array<String>? = null
    // 关键字是否小写存储
    private var keywordsAreLowCase = false
    // 特殊映射表（如activity映射）
    var mmap: HashMap<String, String> = HashMap()
    // 大小写敏感标志
    private var caseSensitive: Boolean = false

    // 包函数映射表：包名->函数列表
    private val packageMap: MutableMap<String, List<String>> = HashMap()
    
    // 控制是否显示完整参数类名
    private var showFullParameterType: Boolean = false
    private val classNameCache = HashMap<String, String>()

    /**
     * 标识符过滤接口
     */
    interface Identifiers {
        fun filterIdentifiers(prefix: String, results: MutableList<String>)
    }

    // 私有方法：将CharSequence转为String
    private fun asString(charSequence: CharSequence?): String {
        return charSequence?.toString() ?: ""
    }

    // 默认构造函数
    constructor() {
        // 默认不设置关键字，使用DEFAULT_LUA_KEYWORDS需手动调用setKeywords
    }

    // 带参数构造函数
    constructor(
        keywords: Array<String>?, 
        basemap: HashMap<String, HashMap<String, CompletionName>>?
    ) : this() {
        setKeywords(keywords, true)
        this.basemap = basemap
    }

    // 设置参数显示模式
    fun setShowFullParameterType(showFull: Boolean) {
        this.showFullParameterType = showFull
    }

    // 简化类名显示
    private fun simplifyClassName(fullClassName: String): String {
        if (showFullParameterType) return fullClassName
        
        // 从缓存中获取
        classNameCache[fullClassName]?.let { return it }
        
        // 简化类名逻辑
        val simpleName = when {
            fullClassName.contains('.') -> fullClassName.substringAfterLast('.')
            fullClassName.contains('$') -> fullClassName.substringAfterLast('$')
            else -> fullClassName
        }
        
        // 更新缓存
        classNameCache[fullClassName] = simpleName
        return simpleName
    }

    // 简化参数列表
    private fun simplifyParameters(parameters: String): String {
        if (showFullParameterType) return parameters
        
        return parameters.split(',')
            .joinToString(", ") { param ->
                param.trim().split(' ').map { part ->
                    if (part.contains('.') || part.contains('$')) {
                        simplifyClassName(part)
                    } else {
                        part
                    }
                }.joinToString(" ")
            }
    }

    /**
     * 设置大小写敏感性
     * @param caseSensitive 是否区分大小写
     */
    fun setCaseSensitive(caseSensitive: Boolean) {
        this.caseSensitive = caseSensitive
    }

    /**
     * 注册包函数
     * @param packageName 包名
     * @param functions 函数列表
     */
    fun addPackage(packageName: String, functions: List<String>) {
        packageMap[packageName] = functions
    }

    // 更新关键字列表
    fun updateKeywords(keywords: Array<String>?) {
        setKeywords(keywords, true)
    }

    /**
     * 添加单个关键字
     * @param keyword 要添加的关键字
     */
    fun addKeyword(keyword: String) {
        if (keywordMap?.containsKey(keyword) == true) {
            return
        }

        val newKeywords = ArrayList<String>()
        keywords?.let { newKeywords.addAll(it) }

        if (!newKeywords.contains(keyword)) {
            newKeywords.add(keyword)
        }

        setKeywords(newKeywords.toTypedArray(), keywordsAreLowCase)
    }

    /**
     * 设置关键字列表
     * @param keywords 关键字数组
     * @param lowerCase 是否转换为小写存储
     */
    fun setKeywords(keywords: Array<String>?, lowerCase: Boolean) {
        this.keywords = keywords
        this.keywordsAreLowCase = lowerCase
        val map = HashMap<String, Any>()
        
        keywords?.forEach { keyword ->
            map[keyword] = true
        }
        this.keywordMap = map
    }

    // 获取当前关键字列表
    fun getKeywords(): Array<String>? = keywords

    // 辅助方法：写入文件
    fun write(path: String, content: String) {
        try {
            FileOutputStream(path, true).use { fos ->
                fos.write(content.toByteArray())
                fos.flush()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /**
     * 主补全方法（带内容引用和位置信息）
     */
    fun requireAutoComplete(
        contentRef: ContentReference,
        position: CharPosition,
        prefix: String,
        publisher: CompletionPublisher,
        identifiers: Identifiers?
    ) {
        val items = createCompletionItemList(prefix, identifiers)
        
        val comparator = Comparator<CompletionItem> { a, b ->
            fun getGroupOrder(item: CompletionItem): Int {
                return when (item.kind) {
                    CompletionItemKind.Identifier -> 0
                    CompletionItemKind.Keyword -> 1
                    else -> 2
                }
            }
            
            val groupA = getGroupOrder(a)
            val groupB = getGroupOrder(b)
            
            if (groupA != groupB) {
                groupA - groupB
            } else {
                a.label.toString().compareTo(b.label.toString(), ignoreCase = true)
            }
        }
        
        publisher.addItems(items)
        publisher.setComparator(comparator)
    }

    /**
     * 核心方法：创建补全项列表
     */
    fun createCompletionItemList(prefix: String, identifiers: Identifiers?): List<CompletionItem> {
        if (prefix.isEmpty()) return emptyList()

        // 辅助匹配函数
        fun String.matchesWithCase(other: String): Boolean {
            return if (caseSensitive) {
                this.startsWith(other)
            } else {
                this.lowercase(Locale.ROOT).startsWith(other.lowercase(Locale.ROOT))
            }
        }

        val keywordItems = ArrayList<CompletionItem>()
        val identifierItems = ArrayList<CompletionItem>()
        val otherItems = ArrayList<CompletionItem>()
        
        val hasMultipleDollars = fun(s: String): Boolean {
            return s.count { it == '$' } >= 2
        }

        /* 关键字补全 */
        keywords?.let { kwds ->
            val added = HashSet<String>()
            for (keyword in kwds) {
                if (hasMultipleDollars(keyword)) continue
                
                if (keyword.matchesWithCase(prefix) && !added.contains(keyword)) {
                    val info = getKeywordInfo(keyword)
                    keywordItems.add(
                        SimpleCompletionItem(keyword, info.description, prefix.length, keyword)
                            .kind(info.kind)
                    )
                    added.add(keyword)
                }
            }
        }

        /* 标识符补全 */
        if (identifiers != null) {
            val idList = ArrayList<String>()
            identifiers.filterIdentifiers(prefix, idList)
            
            for (id in idList) {
                if (hasMultipleDollars(id)) continue
                
                if (keywordMap == null || !keywordMap!!.containsKey(
                        if (caseSensitive) id else id.lowercase(Locale.ROOT))) {
                    identifierItems.add(
                        SimpleCompletionItem(id, "Identifier", prefix.length, id)
                            .kind(CompletionItemKind.Identifier)
                    )
                }
            }
        }

        /* 导入列表补全 */
        var hasImports = false
        importlist?.let { imports ->
            for (fullImport in imports.keys) {
                if (hasMultipleDollars(fullImport)) continue
                
                val className = fullImport.split("\\.".toRegex()).last()
                if (className.matchesWithCase(prefix)) {
                    otherItems.add(
                        SimpleCompletionItem(className, ":import", prefix.length, className)
                            .kind(CompletionItemKind.Class)
                    )
                    hasImports = true
                }
            }
        }

        /* 类名补全 */
        if (!hasImports) {
            classmap?.let { classes ->
                val addedClasses = HashSet<String>()
                for ((className, _) in classes) {
                    if (hasMultipleDollars(className)) continue
                    
                    if (className.matchesWithCase(prefix) && !className.matches(".*\\.\\d+$".toRegex())) {
                        if (!addedClasses.contains(className)) {
                            val desc = classes[className]?.firstOrNull() ?: ":class"
                            otherItems.add(
                                SimpleCompletionItem(className, desc, prefix.length, className)
                                    .kind(CompletionItemKind.Class)
                            )
                            addedClasses.add(className)
                        }
                    }
                }
            }
        }

        /* 包函数补全 */
        if (prefix.contains(".")) {
            val parts = prefix.split("\\.".toRegex())
            if (parts.isNotEmpty()) {
                val pkgName = parts[0]
                val showAll = prefix.endsWith(".")
                val addedFuncs = HashSet<String>()

                packageMap[if (caseSensitive) pkgName else pkgName.lowercase(Locale.ROOT)]?.let { funcs ->
                    val funcPrefix = if (parts.size > 1) parts.last() else ""
                    
                    for (func in funcs) {
                        if (hasMultipleDollars(func)) continue
                        
                        if (showAll || func.matchesWithCase(funcPrefix)) {
                            val fullPath = "$pkgName.$func"
                            if (!addedFuncs.contains(fullPath)) {
                                otherItems.add(
                                    SimpleCompletionItem(
                                        func, 
                                        "Package function: $fullPath", 
                                        prefix.length, 
                                        fullPath
                                    ).kind(CompletionItemKind.Function)
                                )
                                addedFuncs.add(fullPath)
                            }
                        }
                    }
                }
            }
        }

        // 特殊映射
        mmap["activity"] = "com.difierline.lua.LuaActivity"
        mmap["this"] = "com.difierline.lua.LuaActivity"
        mmap["R"] = "com.difierline.lua.lxclua.R"
        mmap["material"] = "com.google.android.material.R"
        mmap["androidx"] = "androidx.appcompat.R"
        
        try {
            val filtered = LuaParser(LuaLexer(prefix).tokenize()).filterParentheses(prefix)
            val returnType = ClassMethodScanner.getReturnType(
                classmap, basemap, filtered, mmap, null
            ) ?: "nullclass"

            /* 类成员补全 */
            if (filtered.isNotEmpty() && returnType != "nullclass" && returnType != "void" && filtered.contains(".")) {
                val lastDot = prefix.lastIndexOf('.')
                val afterDot = if (lastDot >= 0) prefix.substring(lastDot + 1) else ""

                basemap?.get(returnType)?.let { typeMap ->
                    val addedMembers = HashSet<String>()
                    
                    for ((member, cn) in typeMap) {
                        if (hasMultipleDollars(member)) continue
                        
                        if (afterDot.isEmpty() || member.matchesWithCase(afterDot)) {
                        val display = when (cn.type) {
    CompletionItemKind.Method -> {
        val params = if (cn.generic.isNullOrEmpty()) "()" else "(${simplifyParameters(cn.generic)})"
        "$member$params"
    }
    else -> member
}
                             if (!addedMembers.contains(member)) {
                                otherItems.add(
                                    SimpleCompletionItem(
                                        display,
                                        cn.description ?: "Member",
                                        afterDot.length,
                                        member
                                    ).kind(cn.type ?: CompletionItemKind.Method)
                                )
                                addedMembers.add(member)
                            }
                        }
                    }
                }
            }

            /* nullclass特殊处理 */
            if (returnType == "nullclass" && filtered.isNotEmpty()) {
                val split = filtered.split("\\.".toRegex())
                val inputSplit = prefix.split("\\.".toRegex())
                val context = StringBuilder()
                val inputContext = StringBuilder()
                
                for (i in 0 until split.size - 1) {
                    context.append(split[i]).append('.')
                    inputContext.append(inputSplit[i]).append('.')
                }

                val lastDot = prefix.lastIndexOf('.')
                val afterDot = if (lastDot >= 0) prefix.substring(lastDot + 1) else ""

                val parentType = ClassMethodScanner.getReturnType(
                    classmap, basemap, context.toString(), mmap, null
                ) ?: "nullclass"
                
                if (parentType != "nullclass" && parentType != "void") {
                    basemap?.get(parentType)?.let { parentMap ->
                        val addedFields = HashSet<String>()
                        
                        for ((field, cn) in parentMap) {
                            if (field.matchesWithCase(afterDot)) {
                        val display = cn.generic?.let { 
                            if (cn.type == CompletionItemKind.Method) {
                                "$field(${simplifyParameters(it)})"
                            } else {
                                field
                            }
                        } ?: field
                                if (!addedFields.contains(field)) {
                                    otherItems.add(
                                        SimpleCompletionItem(
                                            display,
                                            cn.description ?: "Field",
                                            afterDot.length,
                                            field
                                        ).kind(cn.type ?: CompletionItemKind.Field)
                                    )
                                    addedFields.add(field)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.message ?: "Unknown error")
        }

        return arrayListOf<CompletionItem>().apply {
            addAll(identifierItems)
            addAll(keywordItems)
            addAll(otherItems)
        }
    }

    // 过时的补全方法
    @Deprecated("")
    @Suppress("DEPRECATION")
    fun requireAutoComplete(prefix: String, publisher: CompletionPublisher, identifiers: Identifiers?) {
        publisher.setComparator(COMPARATOR)
        publisher.setUpdateThreshold(0)
        publisher.addItems(createCompletionItemList(prefix, identifiers))
    }

    /**
     * 一次性标识符提供器
     */
    class DisposableIdentifiers : Identifiers {
        private val SIGN = Any()
        private var cache: HashMap<String, Any>? = null
        private val identifiers = ArrayList<String>(128)

        fun addIdentifier(id: String) {
            if (cache == null) throw IllegalStateException("必须先调用beginBuilding()")
            if (cache?.put(id, SIGN) == SIGN) return
            identifiers.add(id)
        }

        fun beginBuilding() {
            cache = HashMap()
        }

        fun finishBuilding() {
            cache?.clear()
            cache = null
        }

        override fun filterIdentifiers(prefix: String, results: MutableList<String>) {
            if (prefix.isEmpty()) return
            
            for (id in identifiers) {
                if (id.equals(prefix, ignoreCase = true)) continue
                if (id.startsWith(prefix, ignoreCase = true)) {
                    results.add(id)
                }
            }
        }
    }

    /**
     * 线程安全的标识符提供器
     */
    class SyncIdentifiers : Identifiers {
        private val lock: Lock = ReentrantLock(true)
        private val identifierMap = HashMap<String, MutableInt>()

        fun clear() {
            lock.lock()
            try {
                identifierMap.clear()
            } finally {
                lock.unlock()
            }
        }

        fun identifierIncrease(id: String) {
            lock.lock()
            try {
                identifierMap.computeIfAbsent(id) { MutableInt(0) }.increase()
            } finally {
                lock.unlock()
            }
        }

        fun identifierDecrease(id: String) {
            lock.lock()
            try {
                identifierMap[id]?.let { counter ->
                    if (counter.decreaseAndGet() <= 0) {
                        identifierMap.remove(id)
                    }
                }
            } finally {
                lock.unlock()
            }
        }

        override fun filterIdentifiers(prefix: String, results: MutableList<String>) {
            filterIdentifiers(prefix, results, false)
        }

        fun filterIdentifiers(prefix: String, results: MutableList<String>, block: Boolean) {
            var locked = false
            try {
                locked = if (block) lock.tryLock(3, TimeUnit.MILLISECONDS) else lock.tryLock()
                
                if (locked) {
                    try {
                        for (id in identifierMap.keys) {
                            if (id.equals(prefix, ignoreCase = true)) continue
                            if (id.length > prefix.length && id.startsWith(prefix, ignoreCase = true)) {
                                results.add(id)
                            }
                        }
                    } finally {
                        lock.unlock()
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    companion object {
        /**
         * 关键字信息数据类
         * @param keyword 关键字
         * @param kind 补全类型
         * @param description 描述
         */
        data class KeywordInfo(
            val keyword: String,
            val kind: CompletionItemKind,
            val description: String
        )
        
        /**
         * 获取关键字的补全类型和描述
         * @param keyword 关键字
         * @return KeywordInfo 包含类型和描述
         */
        fun getKeywordInfo(keyword: String): KeywordInfo {
            return KEYWORD_INFO_MAP[keyword] ?: KeywordInfo(keyword, CompletionItemKind.Keyword, "Keyword")
        }
        
        // 关键字信息映射表
        private val KEYWORD_INFO_MAP: Map<String, KeywordInfo> = mapOf(
            // 控制流关键字
            "if" to KeywordInfo("if", CompletionItemKind.Keyword, "条件判断"),
            "then" to KeywordInfo("then", CompletionItemKind.Keyword, "条件体开始"),
            "else" to KeywordInfo("else", CompletionItemKind.Keyword, "否则分支"),
            "elseif" to KeywordInfo("elseif", CompletionItemKind.Keyword, "否则如果"),
            "for" to KeywordInfo("for", CompletionItemKind.Keyword, "循环"),
            "while" to KeywordInfo("while", CompletionItemKind.Keyword, "条件循环"),
            "repeat" to KeywordInfo("repeat", CompletionItemKind.Keyword, "重复循环"),
            "until" to KeywordInfo("until", CompletionItemKind.Keyword, "循环终止条件"),
            "do" to KeywordInfo("do", CompletionItemKind.Keyword, "执行块"),
            "end" to KeywordInfo("end", CompletionItemKind.Keyword, "块结束"),
            "break" to KeywordInfo("break", CompletionItemKind.Keyword, "跳出循环"),
            "continue" to KeywordInfo("continue", CompletionItemKind.Keyword, "继续下一次循环"),
            "return" to KeywordInfo("return", CompletionItemKind.Keyword, "返回值"),
            "goto" to KeywordInfo("goto", CompletionItemKind.Keyword, "跳转"),
            "switch" to KeywordInfo("switch", CompletionItemKind.Keyword, "分支选择"),
            "case" to KeywordInfo("case", CompletionItemKind.Keyword, "分支条件"),
            "default" to KeywordInfo("default", CompletionItemKind.Keyword, "默认分支"),
            "when" to KeywordInfo("when", CompletionItemKind.Keyword, "条件触发"),
            // 逻辑运算符
            "and" to KeywordInfo("and", CompletionItemKind.Operator, "逻辑与"),
            "or" to KeywordInfo("or", CompletionItemKind.Operator, "逻辑或"),
            "not" to KeywordInfo("not", CompletionItemKind.Operator, "逻辑非"),
            "in" to KeywordInfo("in", CompletionItemKind.Operator, "迭代运算符"),
            
            // 常量值
            "true" to KeywordInfo("true", CompletionItemKind.Constant, "布尔真"),
            "false" to KeywordInfo("false", CompletionItemKind.Constant, "布尔假"),
            "nil" to KeywordInfo("nil", CompletionItemKind.Constant, "空值"),
            
            // 声明关键字
            "local" to KeywordInfo("local", CompletionItemKind.Keyword, "局部变量"),
            "function" to KeywordInfo("function", CompletionItemKind.Function, "函数定义"),
            "lambda" to KeywordInfo("lambda", CompletionItemKind.Function, "匿名函数"),
            
            // 内置函数
            "print" to KeywordInfo("print", CompletionItemKind.Function, "打印输出"),
            "require" to KeywordInfo("require", CompletionItemKind.Function, "导入模块"),
            "import" to KeywordInfo("import", CompletionItemKind.Function, "导入类"),
            
            // OOP 面向对象关键字 - 类定义
            "class" to KeywordInfo("oclass", CompletionItemKind.Class, "定义类"),
            "interface" to KeywordInfo("interface", CompletionItemKind.Interface, "定义接口"),
            "extends" to KeywordInfo("oextends", CompletionItemKind.Keyword, "继承父类"),
            "implements" to KeywordInfo("oimplements", CompletionItemKind.Keyword, "实现接口"),
            "new" to KeywordInfo("new", CompletionItemKind.Constructor, "创建实例"),
            "super" to KeywordInfo("super", CompletionItemKind.Keyword, "调用父类"),
            
            // OOP 访问控制
            "private" to KeywordInfo("private", CompletionItemKind.Keyword, "私有成员"),
            "protected" to KeywordInfo("protected", CompletionItemKind.Keyword, "受保护成员"),
            "public" to KeywordInfo("public", CompletionItemKind.Keyword, "公开成员"),
            "static" to KeywordInfo("static", CompletionItemKind.Keyword, "静态成员")
        )
        
        // 默认Lua关键字列表
        val DEFAULT_LUA_KEYWORDS = arrayOf(
            "and", "break", "case", "continue", "default", "do", "else", "elseif",
            "end", "false", "for", "function", "goto", "if", "in", "local",
            "nil", "not", "or", "repeat", "return", "switch", "then", "true",
            "until", "while","when", "print", "async", "await",
            // OOP 面向对象关键字
            "class", "extends", "implements", "interface", "new",
            "private", "protected", "public", "static", "super"
        )
    }
}