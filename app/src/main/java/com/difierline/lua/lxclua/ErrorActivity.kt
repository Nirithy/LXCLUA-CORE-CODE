package com.difierline.lua.lxclua

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.WindowManager
import android.view.Window
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.difierline.lua.LuaApplication
import com.difierline.lua.util.FileUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.difierline.lua.lxclua.databinding.ActivityErrorBinding
import com.difierline.lua.lxclua.utils.UiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import android.graphics.Color
import android.text.SpannableStringBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import android.view.MotionEvent
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.widget.Button
import android.widget.Toast
import android.util.JsonReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class NoInterceptBehavior<V : View> : CoordinatorLayout.Behavior<V> {

    constructor() : super()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        ev: MotionEvent
    ): Boolean = false      // 永远不拦截
}

class NestedWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 父布局永远别拦截我
        parent.requestDisallowInterceptTouchEvent(true)
        return super.onTouchEvent(event)
    }
}

class ErrorActivity : AppCompatActivity() {

    private lateinit var errorInformation: SpannableStringBuilder
    private var _binding: ActivityErrorBinding? = null
    private val binding: ActivityErrorBinding
        get() = checkNotNull(_binding)

    private val MENU_ITEM_CREATION = 1
    private val MENU_ITEM_SEND = 2
    private val MENU_ITEM_LINE = 3
    private val MENU_ITEM_MODEL = 4
    private val MENU_ITEM_PROMPT = 5

    // 钉钉机器人Webhook地址
    private val DINGDING_WEBHOOK = "https://oapi.dingtalk.com/robot/send?access_token=191b140b6f21250ad572f93c32df56583aae21c08f60d2eaa63386e395149c22"

    // 默认AI提示词模板
    private val DEFAULT_PROMPT = "请使用中文帮我分析以下崩溃信息以及崩溃原因（以 Markdown 格式输出）："

    // 硅基流动线路配置
    private val SILICONFLOW_LINES = listOf(
        mapOf(
            "name" to "一号线路",
            "API_KEY" to "sk-jkrcsurmxiogqfdccdomecffhadukhqmtppzosikuesidbnl",
            "API_URL" to "https://api.siliconflow.cn/v1/chat/completions"
        ),
        mapOf(
            "name" to "二号线路",
            "API_KEY" to "sk-odxrokrvkdkugrmefqsqdgezhcbqawvdwzlnlgvdixihecvn",
            "API_URL" to "https://api.siliconflow.cn/v1/chat/completions"
        )
    )

    // AI模型配置
    private val AI_MODELS = listOf(
        mapOf(
            "id" to "model1",
            "name" to "一号模型",
            "model" to "Qwen/Qwen2.5-Coder-7B-Instruct"
        ),
        mapOf(
            "id" to "model2",
            "name" to "二号模型",
            "model" to "BAAI/bge-m3"
        ),
        mapOf(
            "id" to "model3",
            "name" to "三号模型",
            "model" to "Qwen/Qwen2.5-7B-Instruct"
        ),
        mapOf(
            "id" to "model4",
            "name" to "四号模型",
            "model" to "internlm/internlm2_5-7b-chat"
        ),
        mapOf(
            "id" to "model5",
            "name" to "五号模型",
            "model" to "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B"
        )
    )

    // 当前选中的线路索引（默认使用一号线路）
    private var currentLineIndex: Int
        get() = getSharedPreferences("ai_config", MODE_PRIVATE).getInt("current_line", 0)
        set(value) = getSharedPreferences("ai_config", MODE_PRIVATE).edit().putInt("current_line", value).apply()

    // 当前选中的模型索引（默认使用一号模型）
    private var currentModelIndex: Int
        get() = getSharedPreferences("ai_config", MODE_PRIVATE).getInt("current_model", 0)
        set(value) = getSharedPreferences("ai_config", MODE_PRIVATE).edit().putInt("current_model", value).apply()

    // 获取当前选中的线路配置
    private val currentLineConfig: Map<String, String>
        get() = SILICONFLOW_LINES[currentLineIndex]

    // 获取当前选中的模型配置
    private val currentModelConfig: Map<String, String>
        get() = AI_MODELS[currentModelIndex]

    // 获取当前使用的完整模型ID
    private val currentModelId: String
        get() = currentModelConfig["model"] as String

    /**
     * 获取保存的提示词，如果未保存则使用默认提示词
     */
    private fun getPromptTemplate(): String {
        return getSharedPreferences("ai_config", MODE_PRIVATE)
            .getString("prompt_template", DEFAULT_PROMPT) ?: DEFAULT_PROMPT
    }

    /**
     * 保存提示词到配置
     */
    private fun savePromptTemplate(prompt: String) {
        getSharedPreferences("ai_config", MODE_PRIVATE)
            .edit()
            .putString("prompt_template", prompt)
            .apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Material3_Blue_NoActionBar)
        super.onCreate(savedInstanceState)

        _binding = ActivityErrorBinding.inflate(layoutInflater)

        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val crashDir = LuaApplication.getInstance().getLuaExtDir("crash")

        val file = File(crashDir, "crash_" + dateFormat.format(currentDate)
            .replace(" ", "_")
            .replace(":", "_")
            .replace("-", "_") + ".txt")

        FileUtil.createDirectory(crashDir.toString())

        val window = this.window
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setTitle("应用崩溃了")
        binding.toolbar.subtitle = "复制内容/截图发送给开发者来定位问题"
 
        binding.toolbar.post {
            (binding.toolbar.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                topMargin = UiUtil.getStatusBarHeight(this@ErrorActivity)
                binding.toolbar.layoutParams = this
            }
        }

        val crashDetails = CrashManager.getAllErrorDetailsFromIntent(this, intent)
        errorInformation = crashDetails
        FileUtil.write(file.toString(), crashDetails.toString())

        binding.emessage.setText(crashDetails, TextView.BufferType.SPANNABLE)

        binding.errfab.setOnClickListener {
            copy(this, crashDetails.toString())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_ITEM_CREATION, Menu.NONE, "AI")
            .setIcon(R.drawable.ic_creation)
            .setShowAsAction(2)
        
        menu.add(Menu.NONE, MENU_ITEM_SEND, Menu.NONE, "发送日志")
            .setIcon(R.drawable.ic_send)
            .setShowAsAction(2)

        menu.add(Menu.NONE, MENU_ITEM_LINE, Menu.NONE, "线路: ${currentLineConfig["name"]}")
            .setShowAsAction(0)
        
        menu.add(Menu.NONE, MENU_ITEM_MODEL, Menu.NONE, "模型: ${currentModelConfig["name"]}")
            .setShowAsAction(0)

        menu.add(Menu.NONE, MENU_ITEM_PROMPT, Menu.NONE, "编辑提示词")
            .setShowAsAction(0)
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_ITEM_CREATION -> {
                handleCreationMenu()
                true
            }
            MENU_ITEM_SEND -> {
                sendToDingding()
                true
            }
            MENU_ITEM_LINE -> {
                showLineSelectionDialog()
                true
            }
            MENU_ITEM_MODEL -> {
                showModelSelectionDialog()
                true
            }
            MENU_ITEM_PROMPT -> {
                showPromptEditDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 显示线路选择对话框
     */
    private fun showLineSelectionDialog() {
        val lineNames = SILICONFLOW_LINES.map { it["name"] as String }.toTypedArray()
        val currentIndex = currentLineIndex

        MaterialAlertDialogBuilder(this)
            .setTitle("选择AI线路")
            .setSingleChoiceItems(lineNames, currentIndex) { dialog, which ->
                currentLineIndex = which
                dialog.dismiss()
                invalidateOptionsMenu()
                Toast.makeText(this, "已切换到：${lineNames[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示提示词编辑对话框
     */
    private fun showPromptEditDialog() {
        val editText = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(getPromptTemplate())
            hint = "在此输入AI提示词模板，使用{error}表示崩溃信息插入位置"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("编辑AI提示词")
            .setMessage("提示：使用 {error} 表示崩溃信息插入位置\n例如：分析以下错误：{error}")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newPrompt = editText.text?.toString()?.trim() ?: DEFAULT_PROMPT
                if (newPrompt.isNotEmpty()) {
                    savePromptTemplate(newPrompt)
                    Toast.makeText(this, "提示词已保存", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "提示词不能为空，已恢复默认值", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复默认") { _, _ ->
                savePromptTemplate(DEFAULT_PROMPT)
                Toast.makeText(this, "已恢复默认提示词", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /**
     * 显示模型选择对话框
     */
    private fun showModelSelectionDialog() {
        val modelNames = AI_MODELS.map { it["name"] as String }.toTypedArray()
        val currentIndex = currentModelIndex

        MaterialAlertDialogBuilder(this)
            .setTitle("选择AI模型")
            .setSingleChoiceItems(modelNames, currentIndex) { dialog, which ->
                currentModelIndex = which
                dialog.dismiss()
                invalidateOptionsMenu()
                Toast.makeText(this, "已切换到：${modelNames[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleCreationMenu() {
    val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)

    // 根布局：CoordinatorLayout
    val coordinator = androidx.coordinatorlayout.widget.CoordinatorLayout(this).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    // WebView：指定自定义 Behavior
    val webView = NestedWebView(this).apply {
        layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            com.difierline.lua.lxclua.utils.UiUtil.getScreenHeight(this@ErrorActivity)
        ).apply {
            behavior = NoInterceptBehavior<NestedWebView>()
        }
        visibility = View.GONE
    }

    // 关闭按钮 FAB
    val closeFab = com.google.android.material.floatingactionbutton.FloatingActionButton(this).apply {
        layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
            setMargins(0, 0, 16.dpToPx(), 16.dpToPx())
        }
        setImageResource(R.drawable.ic_chevron_down)
        visibility = View.GONE // 初始隐藏
        setOnClickListener {
            bottomSheetDialog.dismiss()
        }
    }

    // 加载状态布局
    val loadingLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        gravity = android.view.Gravity.CENTER
        setPadding(32.dpToPx(), 32.dpToPx(), 32.dpToPx(), 32.dpToPx())
    }
    val progress = com.google.android.material.progressindicator.CircularProgressIndicator(this).apply {
        setIndicatorSize(50.dpToPx())
        isIndeterminate = true
    }
    val loadingText = TextView(this).apply {
        text = "正在请求 AI 分析错误信息..."
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        gravity = android.view.Gravity.CENTER
        setPadding(0.dpToPx(), 16.dpToPx(), 0.dpToPx(), 0.dpToPx())
    }
    loadingLayout.addView(progress)
    loadingLayout.addView(loadingText)

    // 顺序添加：先 loading，再 WebView，最后 FAB
    coordinator.addView(loadingLayout)
    coordinator.addView(webView)
    coordinator.addView(closeFab)

    bottomSheetDialog.setContentView(coordinator)

    // 保留拖拽关闭功能（对 WebView 以外的区域有效）
    bottomSheetDialog.behavior.isDraggable = true
    bottomSheetDialog.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.7).toInt()
    bottomSheetDialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

    bottomSheetDialog.show()

    configureWebView(webView)

    // 协程请求 AI 并展示结果
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val response = makeAIRequest()
            withContext(Dispatchers.Main) {
                loadingLayout.visibility = View.GONE
                webView.visibility = View.VISIBLE
                closeFab.visibility = View.VISIBLE // 显示关闭按钮
                displayAIResponse(webView, response)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                loadingLayout.visibility = View.GONE
                webView.loadData(
                    """
                    <html>
                        <body style="padding: 20px; text-align: center;">
                            <h3 style="color: #d32f2f;">请求失败</h3>
                            <p style="color: #666;">${e.message}</p>
                            <p style="color: #999; font-size: 14px;">请检查网络连接后重试</p>
                        </body>
                    </html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8"
                )
                webView.visibility = View.VISIBLE
                closeFab.visibility = View.VISIBLE // 显示关闭按钮
            }
        }
    }
}

    private fun configureWebView(webView: WebView) {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webView.webViewClient = WebViewClient()
    }

    private suspend fun makeAIRequest(): String {
        return withContext(Dispatchers.IO) {
            val url = URL(currentLineConfig["API_URL"])
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Authorization", "Bearer ${currentLineConfig["API_KEY"]}")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val requestBody = createRequestBody()
                val outputStream = connection.outputStream
                outputStream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                outputStream.flush()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    inputStream.close()
                    parseAIResponse(response)
                } else {
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw IOException("HTTP error $responseCode: $errorResponse")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun createRequestBody(): JSONObject {
        val userInput = binding.emessage.text.toString()
        val promptTemplate = getPromptTemplate()
        val promptContent = promptTemplate.replace("{error}", userInput)
        
        return JSONObject().apply {
            put("model", currentModelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", promptContent)
                })
            })
            put("stream", false)
        }
    }

    private fun parseAIResponse(response: String): String {
        val jsonResponse = JSONObject(response)
        val choices = jsonResponse.getJSONArray("choices")
        if (choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            return message.getString("content")
        }
        throw Exception("No response content found")
    }

    private fun displayAIResponse(webView: WebView, aiResponse: String) {
        val escapedResponse = aiResponse
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val htmlContent = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Markdown预览</title>
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <link href="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/themes/prism-okaidia.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/plugins/line-numbers/prism-line-numbers.min.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/prism.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/components/prism-lua.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/plugins/line-numbers/prism-line-numbers.min.js"></script>
    <style>
        :root {
            --light-bg: #ffffff;
            --light-text: #2d3748;
            --light-preview-bg: #F2F3FA;
            --light-border: #e2e8f0;
            --light-accent: #3182ce;
        }

        * { margin: 0; padding: 0; box-sizing: border-box; transition: background-color 0.3s, color 0.3s, border-color 0.3s; }
        html, body { height: 100%; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif; }

        body {
            background-color: var(--light-bg);
            color: var(--light-text);
        }

        #md-preview {
            width: 100%;
            height: 100vh;
            padding: 32px;
            overflow-y: auto;
            background-color: var(--light-preview-bg);
            line-height: 1.8;
        }

        #md-preview h1, #md-preview h2, #md-preview h3, #md-preview h4 {
            margin: 1.8em 0 0.8em;
            font-weight: 600;
            color: var(--light-accent);
        }

        #md-preview h1 {
            font-size: 1.8em;
            border-bottom: 2px solid var(--light-border);
            padding-bottom: 0.5em;
        }

        #md-preview h2 { font-size: 1.5em; }
        #md-preview h3 { font-size: 1.2em; }
        #md-preview h4 { font-size: 1em; }

        #md-preview p { margin: 1em 0; }

        #md-preview blockquote {
            border-left: 4px solid var(--light-accent);
            padding: 12px 16px;
            margin: 1.5em 0;
            border-radius: 0 4px 4px 0;
            background-color: rgba(49, 130, 206, 0.05);
            color: var(--light-text);
        }

        #md-preview ul, #md-preview ol {
            padding-left: 1.8em;
            margin: 1em 0;
        }
        #md-preview li { margin: 0.5em 0; }

        #md-preview a {
            color: var(--light-accent);
            text-decoration: none;
            border-bottom: 1px solid transparent;
        }
        #md-preview a:hover {
            border-bottom-color: var(--light-accent);
        }

        #md-preview pre {
            margin: 1.5em 0;
            border-radius: 8px;
            overflow: hidden;
            position: relative;
        }
        #md-preview pre code {
            font-family: 'Fira Code', 'Roboto Mono', monospace;
            font-size: 0.9em;
            line-height: 1.6;
        }
        .prismjs.line-numbers {
            padding-left: 2.8em !important;
        }

        #md-preview code {
            padding: 2px 6px;
            border-radius: 4px;
            font-size: 0.9em;
            background-color: rgba(0, 0, 0, 0.05);
        }

        #md-preview table {
            width: 100%;
            border-collapse: collapse;
            margin: 1.5em 0;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        }

        #md-preview th, #md-preview td {
            border: 1px solid var(--light-border);
            padding: 12px 16px;
            text-align: left;
        }

        #md-preview th {
            background-color: rgba(49, 130, 206, 0.05);
            font-weight: 600;
        }

        #md-preview tr:nth-child(even) {
            background-color: rgba(0, 0, 0, 0.02);
        }

        #md-preview hr {
            border: none;
            height: 1px;
            margin: 2em 0;
            background-color: var(--light-border);
        }

        ::-webkit-scrollbar { width: 6px; height: 6px; }
        ::-webkit-scrollbar-track { background: transparent; border-radius: 3px; }
        ::-webkit-scrollbar-thumb { background: #c1c1c1; border-radius: 3px; }
        ::-webkit-scrollbar-thumb:hover { background: #a1a1a1; }

        @media (max-width: 768px) {
            #md-preview {
                padding: 16px;
            }
        }

        .copy-btn {
            position: absolute;
            top: 8px;
            right: 8px;
            padding: 4px 8px;
            border: none;
            border-radius: 4px;
            background-color: rgba(255, 255, 255, 0.8);
            color: #2d3748;
            font-size: 0.8em;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        .copy-btn:hover {
            background-color: #ffffff;
        }
    </style>
</head>
<body>
    <div id="md-preview"></div>

    <script>
        marked.setOptions({
            highlight: (code, lang) => {
                const prismLang = Prism.languages[lang] || Prism.languages.markup;
                return Prism.highlight(code, prismLang, lang);
            },
            breaks: true,
            gfm: true,
            tables: true,
            taskLists: true,
            strikethrough: true,
            headerIds: true,
            mangle: false,
            sanitize: false
        });

        const previewElement = document.getElementById('md-preview');

        const markdownContent = `$escapedResponse`;

        function renderMarkdown() {
            const html = marked.parse(markdownContent);
            previewElement.innerHTML = html;
            Prism.highlightAllUnder(previewElement);
            
            const preElements = previewElement.querySelectorAll('pre');
            preElements.forEach(pre => {
                const copyBtn = document.createElement('button');
                copyBtn.className = 'copy-btn';
                copyBtn.textContent = '复制代码';
                copyBtn.addEventListener('click', () => {
                    const code = pre.querySelector('code').textContent;
                    navigator.clipboard.writeText(code).then(() => {
                        copyBtn.textContent = '复制成功';
                        setTimeout(() => {
                            copyBtn.textContent = '复制代码';
                        }, 1500);
                    });
                });
                pre.appendChild(copyBtn);
            });
        }

        renderMarkdown();
    </script>
</body>
</html>

        """.trimIndent()

        webView.loadDataWithBaseURL(
            "https://unpkg.com/",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun Int.dpToPx(): Int {
        val scale = resources.displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }

    private fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("crash_log", text)
        clipboard.setPrimaryClip(clip)
    }
    
    private fun applyEdgeToEdgePreference(window: Window) {
        runCatching {
            // 1. 判断 SDK_INT >= 29
            val buildClazz = Class.forName("android.os.Build\$VERSION")
            val sdkInt = buildClazz.getDeclaredField("SDK_INT").getInt(null)
            val enabled = sdkInt >= 29

            // 2. 调用 EdgeToEdgeUtils.applyEdgeToEdge(Window,boolean)
            val utilsClazz = Class.forName("com.google.android.material.internal.EdgeToEdgeUtils")
            val method = utilsClazz.getDeclaredMethod(
                "applyEdgeToEdge",
                Window::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(null, window, enabled)
        }.onFailure { it.printStackTrace() }
    }

    /**
     * 发送崩溃日志到钉钉
     */
    private fun sendToDingding() {
        val crashLog = errorInformation.toString()
        CoroutineScope(Dispatchers.IO).launch {
            val isSuccess = sendDingdingRequest(crashLog)
            withContext(Dispatchers.Main) {
                if (isSuccess) {
                    Toast.makeText(this@ErrorActivity, "发送成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ErrorActivity, "发送失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 发送钉钉请求的具体实现
     */
    private fun sendDingdingRequest(crashLog: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(DINGDING_WEBHOOK)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val jsonBody = buildJsonBody(crashLog)
            val outputStream: OutputStream = connection.outputStream
            outputStream.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = parseResponse(connection.inputStream)
                return response != null && response.errcode == 0
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 构建JSON请求体
     * @param feedback 崩溃日志内容
     * @return JSON字符串
     */
    private fun buildJsonBody(feedback: String): String {
        return """{"msgtype":"text","text":{"content":"收到了用户崩溃报告\n${escapeJson(feedback)}"},"at":{"isAtAll":true}}"""
    }

    /**
     * JSON特殊字符转义
     * @param input 原始字符串
     * @return 转义后的字符串
     */
    private fun escapeJson(input: String?): String {
        if (input == null) return ""
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * 解析钉钉响应
     * @param inputStream 响应流
     * @return 响应对象，解析失败返回null
     */
    private fun parseResponse(inputStream: InputStream): DingdingResponse? {
        return try {
            val jsonReader = JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val response = DingdingResponse()
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val key = jsonReader.nextName()
                when (key) {
                    "errcode" -> response.errcode = jsonReader.nextInt()
                    "errmsg" -> response.errmsg = jsonReader.nextString()
                    else -> jsonReader.skipValue()
                }
            }
            jsonReader.endObject()
            jsonReader.close()
            response
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 钉钉响应数据类
     */
    private data class DingdingResponse(
        var errcode: Int = 0,
        var errmsg: String = ""
    )

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}