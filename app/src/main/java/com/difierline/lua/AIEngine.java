package com.difierline.lua;

import com.luajava.LuaState;
import com.luajava.LuaObject;
import com.luajava.LuaException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.Headers;

// 官方 MCP SDK 导入
import io.modelcontextprotocol.kotlin.sdk.client.Client;
import io.modelcontextprotocol.kotlin.sdk.types.Implementation;
import io.modelcontextprotocol.kotlin.sdk.types.Tool;



import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonNull;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 引擎包装类，用于将 AI 模型的功能暴露给 Lua
 * 提供 Lua 与 AI 模型之间的双向互操作
 */
public class AIEngine {
    // 对话角色枚举
    public enum MessageRole {
        USER, // 用户
        ASSISTANT, // 助手
        SYSTEM, // 系统
        TOOL // 工具
    }
    
    // 对话消息类
    public static class Message {
        private MessageRole role;
        private String content;
        private String toolCallId;
        private String toolName;
        private Map<String, Object> toolArguments;
        private Map<String, Object> toolResult;
        private boolean toolCall;
        private boolean toolResponse;
        
        public Message(MessageRole role, String content) {
            this.role = role;
            this.content = content;
            this.toolCall = false;
            this.toolResponse = false;
        }
        
        public Message(MessageRole role, String content, boolean toolCall, boolean toolResponse) {
            this.role = role;
            this.content = content;
            this.toolCall = toolCall;
            this.toolResponse = toolResponse;
        }
        
        // getter 和 setter 方法
        public MessageRole getRole() { return role; }
        public void setRole(MessageRole role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Map<String, Object> getToolArguments() { return toolArguments; }
        public void setToolArguments(Map<String, Object> toolArguments) { this.toolArguments = toolArguments; }
        public Map<String, Object> getToolResult() { return toolResult; }
        public void setToolResult(Map<String, Object> toolResult) { this.toolResult = toolResult; }
        public boolean isToolCall() { return toolCall; }
        public void setToolCall(boolean toolCall) { this.toolCall = toolCall; }
        public boolean isToolResponse() { return toolResponse; }
        public void setToolResponse(boolean toolResponse) { this.toolResponse = toolResponse; }
    }
    
    // 对话类
    public static class Conversation {
        private String id;
        private List<Message> messages;
        private String currentModelId;
        private String currentProvider;
        private long createdAt;
        private long updatedAt;
        private boolean isProcessing; // 标记对话是否正在处理中
        
        public Conversation(String id) {
            this.id = id;
            this.messages = new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
            this.isProcessing = false;
        }
        
        // getter 和 setter 方法
        public String getId() { return id; }
        public synchronized List<Message> getMessages() { return new ArrayList<>(messages); }
        public void setMessages(List<Message> messages) { this.messages = messages; }
        public String getCurrentModelId() { return currentModelId; }
        public void setCurrentModelId(String currentModelId) { this.currentModelId = currentModelId; }
        public String getCurrentProvider() { return currentProvider; }
        public void setCurrentProvider(String currentProvider) { this.currentProvider = currentProvider; }
        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        
        // 获取和设置对话处理状态
        public synchronized boolean isProcessing() { return isProcessing; }
        public synchronized void setProcessing(boolean processing) { this.isProcessing = processing; }
        
        // 添加消息
        public synchronized void addMessage(Message message) {
            this.messages.add(message);
            this.updatedAt = System.currentTimeMillis();
        }
        
        // 获取对话历史（最多返回最近的n条消息）
        public synchronized List<Message> getRecentMessages(int limit) {
            if (limit <= 0 || messages.size() <= limit) {
                return new ArrayList<>(messages);
            }
            return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
        }
    }
    
    private LuaContext luaContext;
    private String currentModelId;
    private String currentProvider;
    private String apiKey;
    private String baseUrl;
    private boolean sandboxMode;
    private Set<String> allowedProviders;
    private OkHttpClient httpClient;
    private Gson gson;
    private KeyRoulette keyRoulette;
    
    // 线程池，用于执行后台网络请求
    private java.util.concurrent.ExecutorService executorService;
    
    // 工作区设置
    private String workspacePath;
    private boolean workspaceEnabled;
    
    // MCP 配置
    private List<McpServerConfig> mcpServers;
    private boolean mcpEnabled;
    private Map<UUID, McpClient> mcpClients;
    private McpHelper mcpHelper;
    
    // 搜索配置
    private boolean webSearchEnabled;
    private List<Map<String, Object>> searchServices;
    private int searchServiceSelected;
    
    // 对话管理
    private Map<String, Conversation> conversations;
    private final ThreadLocal<String> currentConversationId = ThreadLocal.withInitial(() -> null);
    
    // 异步请求队列，按对话ID分组
    private final Map<String, java.util.Queue<AsyncRequest>> asyncRequestQueue = new ConcurrentHashMap<>();
    
    // 工具状态映射，用于存储工具是否正在被调用
    private final Map<String, Boolean> toolCallingStatus = new ConcurrentHashMap<>();
    
    // 异步请求类
    private static class AsyncRequest {
        String prompt;
        Map<String, Object> params;
        String conversationId;
        LuaObject callback;
        
        AsyncRequest(String prompt, Map<String, Object> params, String conversationId, LuaObject callback) {
            this.prompt = prompt;
            this.params = params;
            this.conversationId = conversationId;
            this.callback = callback;
        }
    }
    
    // MCP 工具注册表，用于存储工具名称和对应的工具函数
    private Map<String, McpToolFunction> toolRegistry;
    
    // 工具输入Schema注册表，用于存储工具名称和对应的输入Schema
    private Map<String, Map<String, Object>> toolSchemaRegistry;
    
    // 禁用的内置工具列表
    private Set<String> disabledBuiltinTools;
    
    // 工具描述映射，用于存储工具的详细描述
    private Map<String, String> toolDescriptions;
    
    // 内置工具集合，用于区分内置工具和Lua工具
    private Set<String> builtinTools;
    
    // AI 提供商配置
    private static final List<Map<String, String>> AI_PROVIDERS = new ArrayList<>();
    
    // AI 模型配置
    private static final List<Map<String, String>> AI_MODELS = new ArrayList<>();
    
    // 默认的 SiliconFlow API Keys
    private static final String DEFAULT_SILICONFLOW_KEYS = "sk-odxrokrvkdkugrmefqsqdgezhcbqawvdwzlnlgvdixihecvn,sk-jkrcsurmxiogqfdccdomecffhadukhqmtppzosikuesidbnl";
    
    // KeyRoulette 接口，用于管理多个 API 密钥
    private interface KeyRoulette {
        String next(String keys);
    }
    
    // 默认的 KeyRoulette 实现
    private static class DefaultKeyRoulette implements KeyRoulette {
        private final Random random;
        private final Pattern splitPattern;
        
        DefaultKeyRoulette() {
            this.random = new Random();
            this.splitPattern = Pattern.compile("[\\s,]+");
        }
        
        private List<String> splitKey(String keys) {
            List<String> keyList = new ArrayList<>();
            for (String key : splitPattern.split(keys)) {
                String trimmedKey = key.trim();
                if (!trimmedKey.isEmpty()) {
                    keyList.add(trimmedKey);
                }
            }
            return keyList;
        }
        
        @Override
        public String next(String keys) {
            List<String> keyList = splitKey(keys);
            if (!keyList.isEmpty()) {
                return keyList.get(random.nextInt(keyList.size()));
            }
            return keys;
        }
    }
    
    // MCP 相关内部类
    
    /**
     * MCP 工具函数接口
     */
    private interface McpToolFunction {
        Map<String, Object> call(Map<String, Object> params) throws Exception;
    }
    
    /**
     * MCP 服务器通用配置
     */
    private static class McpCommonOptions {
        boolean enable;
        String name;
        List<Map<String, String>> headers;
        List<McpTool> tools;
        
        McpCommonOptions() {
            this.enable = true;
            this.name = "";
            this.headers = new ArrayList<>();
            this.tools = new ArrayList<>();
        }
        
        McpCommonOptions(boolean enable, String name, List<Map<String, String>> headers, List<McpTool> tools) {
            this.enable = enable;
            this.name = name;
            this.headers = headers;
            this.tools = tools;
        }
        
        McpCommonOptions copy() {
            return new McpCommonOptions(
                this.enable,
                this.name,
                new ArrayList<>(this.headers),
                new ArrayList<>(this.tools)
            );
        }
    }
    
    /**
     * MCP 工具配置
     */
    private static class McpTool {
        boolean enable;
        String name;
        String description;
        Map<String, Object> inputSchema;
        
        McpTool() {
            this.enable = true;
            this.name = "";
            this.description = null;
            this.inputSchema = null;
        }
        
        McpTool(boolean enable, String name, String description, Map<String, Object> inputSchema) {
            this.enable = enable;
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }
        
        McpTool copy() {
            return new McpTool(
                this.enable,
                this.name,
                this.description,
                this.inputSchema != null ? new HashMap<>(this.inputSchema) : null
            );
        }
    }
    
    /**
     * MCP 服务器配置
     */
    private static abstract class McpServerConfig {
        UUID id;
        McpCommonOptions commonOptions;
        
        McpServerConfig() {
            this.id = UUID.randomUUID();
            this.commonOptions = new McpCommonOptions();
        }
        
        McpServerConfig(UUID id, McpCommonOptions commonOptions) {
            this.id = id;
            this.commonOptions = commonOptions;
        }
        
        abstract McpServerConfig copy();
        abstract McpServerConfig copy(UUID id, McpCommonOptions commonOptions);
    }
    
    /**
     * SSE 传输类型的 MCP 服务器配置
     */
    private static class SseTransportServer extends McpServerConfig {
        String url;
        
        SseTransportServer() {
            super();
            this.url = "";
        }
        
        SseTransportServer(UUID id, McpCommonOptions commonOptions, String url) {
            super(id, commonOptions);
            this.url = url;
        }
        
        @Override
        McpServerConfig copy() {
            return new SseTransportServer(
                this.id,
                this.commonOptions.copy(),
                this.url
            );
        }
        
        @Override
        McpServerConfig copy(UUID id, McpCommonOptions commonOptions) {
            return new SseTransportServer(id, commonOptions, this.url);
        }
    }
    
    /**
     * Streamable HTTP 传输类型的 MCP 服务器配置
     */
    private static class StreamableHTTPServer extends McpServerConfig {
        String url;
        
        StreamableHTTPServer() {
            super();
            this.url = "";
        }
        
        StreamableHTTPServer(UUID id, McpCommonOptions commonOptions, String url) {
            super(id, commonOptions);
            this.url = url;
        }
        
        @Override
        McpServerConfig copy() {
            return new StreamableHTTPServer(
                this.id,
                this.commonOptions.copy(),
                this.url
            );
        }
        
        @Override
        McpServerConfig copy(UUID id, McpCommonOptions commonOptions) {
            return new StreamableHTTPServer(id, commonOptions, this.url);
        }
    }
    
    /**
     * MCP 服务器状态
     */
    private static class McpStatus {
        String status;
        String message;
        
        private McpStatus(String status, String message) {
            this.status = status;
            this.message = message;
        }
        
        static McpStatus Idle() {
            return new McpStatus("IDLE", "空闲");
        }
        
        static McpStatus Connecting() {
            return new McpStatus("CONNECTING", "连接中");
        }
        
        static McpStatus Connected() {
            return new McpStatus("CONNECTED", "已连接");
        }
        
        static McpStatus Error(String message) {
            return new McpStatus("ERROR", message);
        }
    }
    
    /**
     * MCP 客户端包装类
     */
    private static class McpClient {
        McpServerConfig config;
        Object client; // 兼容旧的 EventSource 和新的官方 SDK Client
        McpStatus status;
        
        McpClient(McpServerConfig config) {
            this.config = config;
            this.status = McpStatus.Idle();
        }
    }
    
    // 静态初始化配置
    static {
        // 初始化 AI 提供商配置
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "openai");
            put("name", "OpenAI");
            put("baseUrl", "https://api.openai.com/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "google");
            put("name", "Google Gemini");
            put("baseUrl", "https://generativelanguage.googleapis.com/v1beta");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "claude");
            put("name", "Claude");
            put("baseUrl", "https://api.anthropic.com/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "aihubmix");
            put("name", "AiHubMix");
            put("baseUrl", "https://aihubmix.com/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "siliconflow");
            put("name", "硅基流动");
            put("baseUrl", "https://api.siliconflow.cn/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "deepseek");
            put("name", "DeepSeek");
            put("baseUrl", "https://api.deepseek.com/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "openrouter");
            put("name", "OpenRouter");
            put("baseUrl", "https://openrouter.ai/api/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "tokenpony");
            put("name", "小马算力");
            put("baseUrl", "https://api.tokenpony.cn/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "dashscope");
            put("name", "阿里云百炼");
            put("baseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "volces");
            put("name", "火山引擎");
            put("baseUrl", "https://ark.cn-beijing.volces.com/api/v3");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "moonshot");
            put("name", "月之暗面");
            put("baseUrl", "https://api.moonshot.cn/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "zhipu");
            put("name", "智谱AI开放平台");
            put("baseUrl", "https://open.bigmodel.cn/api/paas/v4");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "stepfun");
            put("name", "阶跃星辰");
            put("baseUrl", "https://api.stepfun.com/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "juheai");
            put("name", "JuheNext");
            put("baseUrl", "https://api.juheai.top/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "302ai");
            put("name", "302.AI");
            put("baseUrl", "https://api.302.ai/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "hunyuan");
            put("name", "腾讯Hunyuan");
            put("baseUrl", "https://api.hunyuan.cloud.tencent.com/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "xai");
            put("name", "xAI");
            put("baseUrl", "https://api.x.ai/v1");
        }});
        
        AI_PROVIDERS.add(new HashMap<String, String>() {{ 
            put("id", "ackai");
            put("name", "AckAI");
            put("baseUrl", "https://ackai.fun/v1");
        }});
        
        // 初始化 AI 模型配置
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "gpt-3.5-turbo");
            put("name", "GPT-3.5 Turbo");
            put("provider", "openai");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "gpt-4");
            put("name", "GPT-4");
            put("provider", "openai");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "gpt-4o");
            put("name", "GPT-4o");
            put("provider", "openai");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "gpt-4o-mini");
            put("name", "GPT-4o Mini");
            put("provider", "openai");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "claude-3-opus-20240229");
            put("name", "Claude 3 Opus");
            put("provider", "claude");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "claude-3-sonnet-20240229");
            put("name", "Claude 3 Sonnet");
            put("provider", "claude");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "claude-3-haiku-20240307");
            put("name", "Claude 3 Haiku");
            put("provider", "claude");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "gemini-1.5-pro");
            put("name", "Gemini 1.5 Pro");
            put("provider", "google");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "gemini-1.5-flash");
            put("name", "Gemini 1.5 Flash");
            put("provider", "google");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "gemini-1.0-pro");
            put("name", "Gemini 1.0 Pro");
            put("provider", "google");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "Qwen/Qwen3-8B");
            put("name", "通义千问Qwen/Qwen3-8B");
            put("provider", "siliconflow");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "Qwen/Qwen2.5-7B-Instruct");
            put("name", "通义千问2.5-7B");
            put("provider", "siliconflow");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "internlm/internlm2_5-7b-chat");
            put("name", "书生·浦语2.5-7B");
            put("provider", "siliconflow");
        }});
        
        AI_MODELS.add(new HashMap<String, String>() {{ 
            put("id", "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B");
            put("name", "深度求索R1");
            put("provider", "siliconflow");
        }});
    }

    /**
     * 构造函数
     * @param luaContext Lua 上下文
     */
    public AIEngine(LuaContext luaContext) {
        this.luaContext = luaContext;
        this.sandboxMode = false;
        this.allowedProviders = new HashSet<>();
        this.currentModelId = "Qwen/Qwen3-8B"; // 默认模型
        this.currentProvider = "siliconflow"; // 默认提供商
        init();
    }

    /**
     * 构造函数
     * @param luaContext Lua 上下文
     * @param sandboxMode 是否启用沙箱模式
     */
    public AIEngine(LuaContext luaContext, boolean sandboxMode) {
        this.luaContext = luaContext;
        this.sandboxMode = sandboxMode;
        this.allowedProviders = new HashSet<>();
        this.currentModelId = "Qwen/Qwen3-8B"; // 默认模型
        this.currentProvider = "siliconflow"; // 默认提供商
        init();
    }

    /**
     * 初始化 AI 引擎
     */
    private void init() {
        // 初始化 HTTP 客户端
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
        
        // 初始化 Gson
        gson = new Gson();
        
        // 初始化 KeyRoulette
        keyRoulette = new DefaultKeyRoulette();
        
        // 初始化线程池
        executorService = java.util.concurrent.Executors.newFixedThreadPool(4);
        
        // 初始化工作区设置
        this.workspacePath = "/sdcard/XCLUA/"; // 默认工作区路径
        this.workspaceEnabled = true; // 默认启用工作区
        
        // 初始化 MCP 配置
        initMcpConfig();
        
        // 初始化 MCP 辅助类
        mcpHelper = new McpHelper();
        
        // 初始化搜索配置
        initSearchConfig();
        
        // 初始化对话管理
        this.conversations = new ConcurrentHashMap<>();
        this.currentConversationId.set(null);
        
        // 初始化工具注册表
        this.toolRegistry = new ConcurrentHashMap<>();
        this.toolSchemaRegistry = new ConcurrentHashMap<>();
        this.disabledBuiltinTools = ConcurrentHashMap.newKeySet();
        this.toolDescriptions = new ConcurrentHashMap<>();
        this.builtinTools = ConcurrentHashMap.newKeySet();
        
        // 注册内置 MCP 工具
        registerBuiltinTools();
    }
    
    /**
     * 注册内置 MCP 工具
     */
    private void registerBuiltinTools() {
        // 文件操作工具
        registerBuiltinTool("read_file", 
            this::callReadFileTool, 
            buildReadFileSchema(), 
            "读取指定文件的内容");
        
        registerBuiltinTool("write_file", 
            this::callWriteFileTool, 
            buildWriteFileSchema(), 
            "向指定文件写入内容，支持覆盖或追加模式");
        
        registerBuiltinTool("list_files", 
            this::callListFilesTool, 
            buildListFilesSchema(), 
            "列出指定目录下的文件和子目录");
        
        registerBuiltinTool("search_files", 
            this::callSearchFilesTool, 
            buildSearchFilesSchema(), 
            "在指定目录下搜索符合条件的文件");
        
        registerBuiltinTool("read_file_lines", 
            this::callReadFileLinesTool, 
            buildReadFileLinesSchema(), 
            "按行读取指定文件的内容");
        
        registerBuiltinTool("write_file_lines", 
            this::callWriteFileLinesTool, 
            buildWriteFileLinesSchema(), 
            "写入或覆写文件的特定行内容");
        
        // 添加新的内置工具
        registerBuiltinTool("create_directory", 
            this::callCreateDirectoryTool, 
            buildCreateDirectorySchema(), 
            "创建指定目录，支持递归创建");
        
        registerBuiltinTool("delete_file", 
            this::callDeleteFileTool, 
            buildDeleteFileSchema(), 
            "删除指定文件");
        
        registerBuiltinTool("rename_file", 
            this::callRenameFileTool, 
            buildRenameFileSchema(), 
            "重命名或移动文件/目录");
        
        registerBuiltinTool("copy_file", 
            this::callCopyFileTool, 
            buildCopyFileSchema(), 
            "复制文件或目录到指定位置");
        
        registerBuiltinTool("get_file_info", 
            this::callGetFileInfoTool, 
            buildGetFileInfoSchema(), 
            "获取指定文件的详细信息");
        
        registerBuiltinTool("execute_command", 
            this::callExecuteCommandTool, 
            buildExecuteCommandSchema(), 
            "执行系统命令行命令");
        
        // 注册网络搜索工具
        registerBuiltinTool("search_web", 
            this::callSearchWebTool, 
            buildSearchWebSchema(), 
            "搜索网络获取最新基本00信息");
    }
    
    /**
     * 注册内置工具的辅助方法
     * @param name 工具名称
     * @param toolFunction 工具函数
     * @param inputSchema 工具输入Schema
     * @param description 工具描述
     */
    private void registerBuiltinTool(String name, McpToolFunction toolFunction, Map<String, Object> inputSchema, String description) {
        // 先添加描述，再注册工具，确保同步时能获取到描述
        toolDescriptions.put(name, description);
        builtinTools.add(name);
        registerTool(name, toolFunction, inputSchema);
    }
    
    /**
     * 公共方法：注册自定义 MCP 工具
     * @param toolName 工具名称
     * @param description 工具描述
     * @param inputSchema 工具输入Schema
     * @param toolFunction 工具函数
     */
    public void registerMCPTool(String toolName, String description, Map<String, Object> inputSchema, McpToolFunction toolFunction) {
        registerTool(toolName, toolFunction, inputSchema);
        // 将工具描述添加到toolDescriptions中
        if (description != null) {
            toolDescriptions.put(toolName, description);
        }
        
        // 将工具添加到所有 MCP 服务器配置中
        for (McpServerConfig serverConfig : mcpServers) {
            // 检查工具是否已存在
            boolean toolExists = serverConfig.commonOptions.tools.stream()
                .anyMatch(tool -> tool.name.equals(toolName));
            
            if (!toolExists) {
                // 添加工具到服务器配置
                McpTool newTool = new McpTool(true, toolName, description, inputSchema);
                serverConfig.commonOptions.tools.add(newTool);
            }
        }
    }
    
    /**
     * 将所有已注册的工具同步到指定的 MCP 服务器配置中
     * @param serverConfig MCP 服务器配置
     */
    private void syncRegisteredToolsToServer(McpServerConfig serverConfig) {
        for (String toolName : toolRegistry.keySet()) {
            // 检查工具是否已存在于服务器配置中
            boolean toolExists = serverConfig.commonOptions.tools.stream()
                .anyMatch(tool -> tool.name.equals(toolName));
            
            if (!toolExists) {
                // 获取工具的描述和Schema
                String description = toolDescriptions.getOrDefault(toolName, toolName);
                Map<String, Object> inputSchema = toolSchemaRegistry.get(toolName);
                
                // 添加工具到服务器配置
                McpTool newTool = new McpTool(true, toolName, description, inputSchema);
                serverConfig.commonOptions.tools.add(newTool);
            }
        }
    }
    
    /**
     * 注册 Lua 函数作为 MCP 工具
     * @param toolName 工具名称
     * @param description 工具描述
     * @param inputSchema 工具输入Schema
     * @param luaFunction Lua 函数对象
     */
    public void registerMCPToolFromLua(String toolName, String description, Map<String, Object> inputSchema, LuaObject luaFunction) {
        // 创建一个 McpToolFunction 包装器，调用 Lua 函数
        McpToolFunction toolFunction = params -> {
            try {
                // 使用同步块确保 LuaState 访问的线程安全
                synchronized (luaContext) {
                    LuaState L = luaContext.getLuaState();
                    
                    // 检查 luaFunction 是否有效
                    if (luaFunction == null) {
                        throw new IllegalArgumentException("Lua 函数对象不能为空");
                    }
                    
                    // 压入 Lua 函数
                    luaFunction.push();
                    
                    // 检查栈是否有值（防止 push() 失败）
                    if (L.isNil(-1)) {
                        L.pop(1);
                        throw new RuntimeException("无法压入 Lua 函数，函数对象无效");
                    }
                    
                    // 将 Java Map 转换为 Lua table 并压入栈
                    pushJavaObjectToLua(L, params);
                    
                    // 调用 Lua 函数，传入1个参数，期望返回1个结果
                    int pcallResult = L.pcall(1, 1, 0);
                    if (pcallResult != 0) {
                        // 调用出错，获取错误信息
                        String errorMsg = L.isString(-1) ? L.toString(-1) : "未知错误";
                        L.pop(1);
                        throw new RuntimeException("调用 Lua MCP 工具失败: " + errorMsg);
                    }
                    
                    // 获取返回结果
                    Object luaResult = convertLuaValueToJava(L, -1);
                    L.pop(1);
                    
                    // 确保返回结果是 Map 类型
                    Map<String, Object> resultMap = new HashMap<>();
                    if (luaResult instanceof Map) {
                        resultMap = (Map<String, Object>) luaResult;
                        // 确保结果包含 success 字段
                        if (!resultMap.containsKey("success")) {
                            resultMap.put("success", true);
                        }
                    } else {
                        // 转换为 Map
                        resultMap.put("result", luaResult);
                        resultMap.put("success", true);
                    }
                    
                    // 添加工具信息
                    resultMap.put("toolName", toolName);
                    resultMap.put("toolType", "lua");
                    
                    return resultMap;
                }
            } catch (Exception e) {
                // 捕获所有异常，避免闪退
                e.printStackTrace();
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "执行 Lua 工具失败: " + e.getMessage());
                errorResult.put("toolName", toolName);
                errorResult.put("toolType", "lua");
                errorResult.put("errorType", e.getClass().getSimpleName());
                return errorResult;
            }
        };
        
        // 注册工具
        registerMCPTool(toolName, description, inputSchema, toolFunction);
    }
    
    /**
     * 简化的 Lua 工具注册方法，自动生成 Schema
     * @param toolName 工具名称
     * @param description 工具描述
     * @param luaFunction Lua 函数对象
     */
    public void registerMCPToolFromLua(String toolName, String description, LuaObject luaFunction) {
        // 生成默认的输入 Schema，支持任意参数
        Map<String, Object> defaultSchema = new HashMap<>();
        defaultSchema.put("type", "object");
        defaultSchema.put("properties", new HashMap<>());
        defaultSchema.put("required", new ArrayList<>());
        defaultSchema.put("additionalProperties", true);
        
        registerMCPToolFromLua(toolName, description, defaultSchema, luaFunction);
    }
    
    /**
     * 获取所有已注册的 MCP 工具名称列表
     * @return 工具名称列表
     */
    public List<String> getRegisteredToolNames() {
        return new ArrayList<>(toolRegistry.keySet());
    }
    
    /**
     * 获取指定工具的详细信息
     * @param toolName 工具名称
     * @return 工具详细信息，包括描述、Schema等
     */
    public Map<String, Object> getToolDetails(String toolName) {
        Map<String, Object> toolDetails = new HashMap<>();
        
        if (toolRegistry.containsKey(toolName)) {
            boolean isBuiltin = builtinTools.contains(toolName);
            toolDetails.put("name", toolName);
            toolDetails.put("description", toolDescriptions.getOrDefault(toolName, (isBuiltin ? "内置工具: " : "Lua工具: ") + toolName));
            toolDetails.put("inputSchema", toolSchemaRegistry.get(toolName));
            toolDetails.put("disabled", isBuiltinToolDisabled(toolName));
            toolDetails.put("isCalling", toolCallingStatus.getOrDefault(toolName, false));
            String toolType = isBuiltin ? "builtin" : "lua";
            toolDetails.put("type", toolType);
            toolDetails.put("toolType", toolType); // 兼容旧版本
        } else {
            // 检查是否是远程工具
            for (McpServerConfig serverConfig : mcpServers) {
                if (serverConfig.commonOptions.enable) {
                    for (McpTool tool : serverConfig.commonOptions.tools) {
                        if (tool.name.equals(toolName)) {
                            toolDetails.put("name", tool.name);
                            toolDetails.put("description", tool.description);
                            toolDetails.put("inputSchema", tool.inputSchema);
                            toolDetails.put("disabled", !tool.enable);
                            toolDetails.put("isCalling", toolCallingStatus.getOrDefault(toolName, false));
                            toolDetails.put("type", "remote");
                            toolDetails.put("toolType", "remote"); // 兼容旧版本
                            toolDetails.put("serverName", serverConfig.commonOptions.name);
                            return toolDetails;
                        }
                    }
                }
            }
        }
        
        return toolDetails;
    }
    
    /**
     * 获取所有可用的 MCP 工具，包括内置工具、Lua工具和远程工具
     * @return 可用工具列表
     */
    public List<Map<String, Object>> getAllAvailableTools() {
        List<Map<String, Object>> availableTools = new ArrayList<>();
        
        // 添加内置工具和Lua工具
        for (String toolName : toolRegistry.keySet()) {
            Map<String, Object> toolDetails = getToolDetails(toolName);
            if (toolDetails.containsKey("name")) {
                // 检查工具是否被禁用
                boolean disabled = (boolean) toolDetails.getOrDefault("disabled", false);
                if (!disabled) {
                    availableTools.add(toolDetails);
                }
            }
        }
        
        // 添加远程工具
        for (McpServerConfig serverConfig : mcpServers) {
            if (serverConfig.commonOptions.enable) {
                for (McpTool tool : serverConfig.commonOptions.tools) {
                    if (tool.enable) {
                        Map<String, Object> toolDetails = new HashMap<>();
                        toolDetails.put("name", tool.name);
                        toolDetails.put("description", tool.description);
                        toolDetails.put("inputSchema", tool.inputSchema);
                        toolDetails.put("disabled", !tool.enable);
                        toolDetails.put("isCalling", toolCallingStatus.getOrDefault(tool.name, false));
                        toolDetails.put("type", "remote");
                        toolDetails.put("toolType", "remote");
                        toolDetails.put("serverName", serverConfig.commonOptions.name);
                        availableTools.add(toolDetails);
                    }
                }
            }
        }
        
        // 添加搜索工具（如果启用）
        if (webSearchEnabled) {
            Map<String, Object> searchTool = new HashMap<>();
            searchTool.put("name", "search_web");
            searchTool.put("description", "搜索网络获取最新信息");
            searchTool.put("disabled", false);
            searchTool.put("isCalling", toolCallingStatus.getOrDefault("search_web", false));
            searchTool.put("type", "builtin");
            searchTool.put("toolType", "builtin");
            
            // 构建搜索工具的输入Schema
            Map<String, Object> inputSchema = new HashMap<>();
            inputSchema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> queryProperty = new HashMap<>();
            queryProperty.put("type", "string");
            queryProperty.put("description", "搜索查询词");
            properties.put("query", queryProperty);
            inputSchema.put("properties", properties);
            List<String> required = new ArrayList<>();
            required.add("query");
            inputSchema.put("required", required);
            
            searchTool.put("inputSchema", inputSchema);
            availableTools.add(searchTool);
        }
        
        return availableTools;
    }
    
    /**
     * 获取所有 MCP 服务器配置
     * @return MCP 服务器配置列表
     */
    public List<Map<String, Object>> getMcpServerConfigs() {
        List<Map<String, Object>> serverConfigs = new ArrayList<>();
        
        for (McpServerConfig config : mcpServers) {
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("id", config.id.toString());
            serverInfo.put("name", config.commonOptions.name);
            serverInfo.put("enable", config.commonOptions.enable);
            serverInfo.put("transportType", config instanceof SseTransportServer ? "sse" : "streamable_http");
            
            if (config instanceof SseTransportServer) {
                serverInfo.put("url", ((SseTransportServer) config).url);
            } else if (config instanceof StreamableHTTPServer) {
                serverInfo.put("url", ((StreamableHTTPServer) config).url);
            }
            
            // 获取服务器状态
            McpClient client = mcpClients.get(config.id);
            serverInfo.put("status", client != null ? client.status.status : McpStatus.Idle().status);
            serverInfo.put("statusMessage", client != null ? client.status.message : McpStatus.Idle().message);
            
            // 添加工具列表
            List<Map<String, Object>> tools = new ArrayList<>();
            for (McpTool tool : config.commonOptions.tools) {
                Map<String, Object> toolInfo = new HashMap<>();
                toolInfo.put("name", tool.name);
                toolInfo.put("description", tool.description);
                toolInfo.put("enable", tool.enable);
                toolInfo.put("inputSchema", tool.inputSchema);
                tools.add(toolInfo);
            }
            serverInfo.put("tools", tools);
            
            serverConfigs.add(serverInfo);
        }
        
        return serverConfigs;
    }
    
    /**
     * 添加 MCP 服务器配置
     * @param config MCP 服务器配置
     */
    public void addMcpServerConfig(McpServerConfig config) {
        // 将所有已注册的工具添加到新服务器配置中
        syncRegisteredToolsToServer(config);
        mcpServers.add(config);
        // 如果服务器启用，自动连接并同步工具
        if (config.commonOptions.enable) {
            addMcpClient(config);
        }
    }
    
    /**
     * 更新 MCP 服务器配置
     * @param config 更新后的 MCP 服务器配置
     */
    public void updateMcpServerConfig(McpServerConfig config) {
        for (int i = 0; i < mcpServers.size(); i++) {
            if (mcpServers.get(i).id.equals(config.id)) {
                McpServerConfig oldConfig = mcpServers.get(i);
                mcpServers.set(i, config);
                
                // 处理服务器启用状态变化
                if (config.commonOptions.enable && !oldConfig.commonOptions.enable) {
                    // 服务器从禁用变为启用，连接客户端
                    addMcpClient(config);
                } else if (!config.commonOptions.enable && oldConfig.commonOptions.enable) {
                    // 服务器从启用变为禁用，断开客户端
                    removeMcpClient(config);
                } else if (config.commonOptions.enable) {
                    // 服务器已启用，重新连接以应用新配置
                    removeMcpClient(oldConfig);
                    addMcpClient(config);
                }
                break;
            }
        }
    }
    
    /**
     * 删除 MCP 服务器配置
     * @param config MCP 服务器配置
     */
    public void removeMcpServerConfig(McpServerConfig config) {
        mcpServers.remove(config);
        removeMcpClient(config);
    }
    
    /**
     * 添加 MCP 客户端
     * @param config MCP 服务器配置
     */
    private void addMcpClient(McpServerConfig config) {
        // 异步添加客户端，避免阻塞主线程
        executorService.submit(() -> {
            try {
                System.out.println("开始连接 MCP 服务器: " + config.commonOptions.name + "，URL: " + 
                    (config instanceof SseTransportServer ? ((SseTransportServer) config).url : ((StreamableHTTPServer) config).url));
                
                // 移除旧客户端（如果存在）
                removeMcpClient(config);
                
                // 创建新客户端
                McpClient clientWrapper = new McpClient(config);
                mcpClients.put(config.id, clientWrapper);
                
                // 更新状态为连接中
                clientWrapper.status = McpStatus.Connecting();
                System.out.println("MCP 客户端已创建，服务器: " + config.commonOptions.name + "，状态: 连接中");
                
                // 使用官方 SDK 创建客户端
                io.modelcontextprotocol.kotlin.sdk.client.Client client = McpSdkWrapper.INSTANCE.createClient(
                    config.commonOptions.name,
                    "1.0.0"
                );
                clientWrapper.client = client;
                
                // 创建传输层实现
                io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport transport;
                String transportType;
                if (config instanceof SseTransportServer) {
                    // 创建 SSE 传输
                    transport = com.difierline.lua.transport.TransportHelper.createSseTransport(
                        ((SseTransportServer) config).url
                    );
                    transportType = "SSE";
                } else if (config instanceof StreamableHTTPServer) {
                    // 创建 Streamable HTTP 传输
                    transport = com.difierline.lua.transport.TransportHelper.createStreamableHttpTransport(
                        ((StreamableHTTPServer) config).url
                    );
                    transportType = "Streamable HTTP";
                } else {
                    throw new IllegalArgumentException("未知的传输类型");
                }
                
                System.out.println("MCP 传输层已创建，类型: " + transportType);
                
                // 连接到服务器
                McpSdkWrapper.INSTANCE.connect(client, transport).join();
                
                System.out.println("MCP 客户端连接成功，开始同步工具列表");
                
                // 同步工具列表
                syncMcpTools(config);
                
                // 更新状态为已连接
                clientWrapper.status = McpStatus.Connected();
                System.out.println("MCP 客户端已连接，服务器: " + config.commonOptions.name + "，状态: 已连接");
                System.out.println("当前已注册工具数量: " + toolRegistry.size());
            } catch (Exception e) {
                e.printStackTrace();
                // 更新状态为错误
                McpClient client = mcpClients.get(config.id);
                if (client != null) {
                    client.status = McpStatus.Error(e.getMessage());
                    System.out.println("MCP 客户端连接失败，服务器: " + config.commonOptions.name + ", 错误: " + e.getMessage());
                } else {
                    System.out.println("MCP 客户端连接失败，服务器: " + config.commonOptions.name + ", 错误: 客户端实例已被销毁");
                }
            }
        });
    }
    
    /**
     * 移除 MCP 客户端
     * @param config MCP 服务器配置
     */
    private void removeMcpClient(McpServerConfig config) {
        McpClient client = mcpClients.remove(config.id);
        if (client != null) {
            // 关闭客户端连接
            if (client.client != null) {
                try {
                    // 检查 client 类型，兼容旧的 EventSource 和新的官方 SDK Client
                    if (client.client instanceof okhttp3.sse.EventSource) {
                        // 关闭旧的 EventSource
                        ((okhttp3.sse.EventSource) client.client).cancel();
                    } else if (client.client instanceof io.modelcontextprotocol.kotlin.sdk.client.Client) {
                        // 关闭官方 SDK Client
                        McpSdkWrapper.INSTANCE.close((io.modelcontextprotocol.kotlin.sdk.client.Client) client.client).join();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            client.status = McpStatus.Idle();
        }
    }
    
    /**
     * 同步 MCP 服务器工具列表
     * @param config MCP 服务器配置
     */
    private void syncMcpTools(McpServerConfig config) {
        try {
            System.out.println("开始同步 MCP 工具，服务器: " + config.commonOptions.name);
            
            // 1. 获取本地已注册的工具
            List<McpTool> localTools = new ArrayList<>();
            for (String toolName : toolRegistry.keySet()) {
                String description = toolDescriptions.getOrDefault(toolName, toolName);
                Map<String, Object> inputSchema = toolSchemaRegistry.get(toolName);
                localTools.add(new McpTool(true, toolName, description, inputSchema));
                System.out.println("本地工具: " + toolName + " - " + description);
            }
            
            // 2. 从服务器获取远程工具列表（可选，取决于服务器是否支持）
            List<McpTool> serverTools = new ArrayList<>();
            McpClient clientWrapper = mcpClients.get(config.id);
            if (clientWrapper != null && clientWrapper.client != null) {
                if (clientWrapper.client instanceof io.modelcontextprotocol.kotlin.sdk.client.Client) {
                    try {
                        // 使用官方 SDK 获取服务器工具列表
                        io.modelcontextprotocol.kotlin.sdk.client.Client sdkClient = 
                            (io.modelcontextprotocol.kotlin.sdk.client.Client) clientWrapper.client;
                        java.util.List<io.modelcontextprotocol.kotlin.sdk.types.Tool> serverToolsList = 
                            McpSdkWrapper.INSTANCE.listTools(sdkClient).join();
                        
                        // 将服务器工具转换为本地 MCP 工具格式
                        for (io.modelcontextprotocol.kotlin.sdk.types.Tool serverTool : serverToolsList) {
                            Map<String, Object> inputSchema = new HashMap<>();
                            inputSchema.put("type", "object");
                            
                            McpTool tool = new McpTool(
                                true, // 默认为启用
                                serverTool.getName(),
                                serverTool.getDescription(),
                                inputSchema
                            );
                            serverTools.add(tool);
                            System.out.println("服务器工具: " + serverTool.getName() + " - " + serverTool.getDescription());
                        }
                        System.out.println("从服务器获取到 " + serverTools.size() + " 个工具");
                    } catch (Exception e) {
                        System.out.println("获取服务器工具列表失败，跳过远程工具同步: " + e.getMessage());
                    }
                } else {
                    // 使用旧的实现，从配置中获取工具列表
                    System.out.println("使用旧的 MCP 客户端，跳过远程工具获取");
                }
            }
            
            // 3. 合并本地工具和远程工具（远程工具优先级更高）
            Map<String, McpTool> mergedTools = new HashMap<>();
            
            // 先添加本地工具
            for (McpTool tool : localTools) {
                mergedTools.put(tool.name, tool);
                System.out.println("已添加本地工具: " + tool.name);
            }
            
            // 再添加远程工具（覆盖本地工具）
            for (McpTool tool : serverTools) {
                mergedTools.put(tool.name, tool);
                System.out.println("远程工具优先级更高，覆盖本地工具: " + tool.name);
            }
            
            // 4. 添加服务器配置中已有的其他工具
            for (McpTool tool : config.commonOptions.tools) {
                if (!mergedTools.containsKey(tool.name)) {
                    mergedTools.put(tool.name, tool);
                    System.out.println("保留服务器配置中已有的工具: " + tool.name);
                }
            }
            
            // 5. 更新服务器配置的工具列表
            config.commonOptions.tools = new ArrayList<>(mergedTools.values());
            
            // 6. 日志记录
            System.out.println("同步 MCP 工具完成，服务器: " + config.commonOptions.name + ", 总工具数量: " + config.commonOptions.tools.size());
            System.out.println("同步的工具列表: ");
            for (McpTool tool : config.commonOptions.tools) {
                System.out.println("  - " + tool.name + " (" + tool.description + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("同步 MCP 工具失败，服务器: " + config.commonOptions.name + ", 错误: " + e.getMessage());
        }
    }
     
    /**
     * 同步所有 MCP 服务器工具列表
     */
    public void syncAllMcpTools() {
        for (McpServerConfig config : mcpServers) {
            if (config.commonOptions.enable) {
                syncMcpTools(config);
            }
        }
    }
    
    /**
     * 调用 MCP 工具
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 工具调用结果
     */

    
    /**
     * 调用搜索工具
     * @param params 搜索参数
     * @return 搜索结果
     */
    private Map<String, Object> callSearchWebTool(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String query = (String) params.get("query");
            if (query == null || query.isEmpty()) {
                throw new IllegalArgumentException("搜索查询词不能为空");
            }
            
            // 调用实际的搜索服务
            Map<String, Object> searchResult = performWebSearch(query);
            
            result.put("success", true);
            result.put("result", searchResult);
            result.put("toolName", "search_web");
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("error", "搜索失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 执行实际的网络搜索
     * @param query 搜索查询词
     * @return 搜索结果
     */
    private Map<String, Object> performWebSearch(String query) throws IOException {
        // 直接使用网页抓取方式，无需API密钥
        return performWebScrapingSearch(query);
    }
    
    /**
     * 执行网页抓取搜索（作为Bing API的备用方案）
     * @param query 搜索查询词
     * @return 搜索结果
     */
    private Map<String, Object> performWebScrapingSearch(String query) throws IOException {
        // 使用OkHttp进行网络请求
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // 使用Bing网页搜索（通过网页抓取）
        String url = "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8");
        
        // 使用当前系统语言构建Accept-Language头
        java.util.Locale locale = java.util.Locale.getDefault();
        String acceptLanguage = locale.getLanguage() + "-" + locale.getCountry() + "," + locale.getLanguage();
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", acceptLanguage)
                .addHeader("Accept-Encoding", "gzip, deflate, sdch")
                .addHeader("Accept-Charset", "utf-8")
                .addHeader("Connection", "keep-alive")
                .addHeader("Referrer", "https://www.bing.com/")
                .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("网络请求失败: " + response);
        }
        
        // 解析网页内容
        String html = response.body().string();
        
        // 使用Jsoup解析HTML，提取搜索结果
        List<Map<String, Object>> results = new ArrayList<>();
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        
        // 查找所有搜索结果项
        org.jsoup.select.Elements resultElements = doc.select("li.b_algo");
        
        // 遍历搜索结果，提取标题、链接和描述
        int count = 0;
        for (org.jsoup.nodes.Element element : resultElements) {
            if (count >= 10) break; // 限制最多返回10个结果
            
            // 提取标题和链接
            org.jsoup.nodes.Element titleElement = element.selectFirst("h2 > a");
            if (titleElement == null) continue;
            
            String title = titleElement.text();
            String link = titleElement.attr("href");
            
            // 提取描述文本
            org.jsoup.nodes.Element snippetElement = element.selectFirst(".b_caption p");
            String snippet = snippetElement != null ? snippetElement.text() : "";
            
            // 创建搜索结果项
            Map<String, Object> webResult = new HashMap<>();
            webResult.put("url", link);
            webResult.put("title", title);
            webResult.put("snippet", snippet);
            
            results.add(webResult);
            count++;
        }
        
        // 构建搜索结果
        Map<String, Object> searchResult = new HashMap<>();
        searchResult.put("query", query);
        searchResult.put("results", results);
        searchResult.put("total", results.size());
        searchResult.put("method", "web_scraping");
        
        return searchResult;
    }
    
    /**
     * 调用远程 MCP 工具
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 工具调用结果
     */
    private Map<String, Object> callRemoteMcpTool(String toolName, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 找到对应的 MCP 服务器
            McpServerConfig targetServer = null;
            for (McpServerConfig serverConfig : mcpServers) {
                if (serverConfig.commonOptions.enable) {
                    for (McpTool tool : serverConfig.commonOptions.tools) {
                        if (tool.name.equals(toolName) && tool.enable) {
                            targetServer = serverConfig;
                            break;
                        }
                    }
                    if (targetServer != null) {
                        break;
                    }
                }
            }
            
            if (targetServer == null) {
                throw new IllegalArgumentException("找不到可用的 MCP 服务器或工具: " + toolName);
            }
            
            // 找到对应的 MCP 客户端
            McpClient client = mcpClients.get(targetServer.id);
            if (client == null) {
                throw new RuntimeException("MCP 客户端未连接: " + targetServer.commonOptions.name);
            }
            
            // 调用远程工具，根据客户端类型选择调用方式
            if (client.client instanceof io.modelcontextprotocol.kotlin.sdk.client.Client) {
                // 使用官方 SDK 调用工具
                io.modelcontextprotocol.kotlin.sdk.client.Client sdkClient = 
                    (io.modelcontextprotocol.kotlin.sdk.client.Client) client.client;
                Object toolResult = McpSdkWrapper.INSTANCE.callTool(sdkClient, toolName, params).join();
                
                // 处理返回结果
                if (toolResult instanceof Map) {
                    result.putAll((Map<? extends String, ?>) toolResult);
                } else {
                    // 如果结果不是 Map，包装成 Map 格式
                    result.put("result", toolResult);
                }
                
                result.put("success", true);
                result.put("toolName", toolName);
                result.put("serverName", targetServer.commonOptions.name);
            } else {
                // 旧的实现，使用自定义 MCP 协议
                // 构建 MCP JSON-RPC 请求
                Map<String, Object> jsonRpcRequest = new HashMap<>();
                jsonRpcRequest.put("jsonrpc", "2.0");
                jsonRpcRequest.put("id", UUID.randomUUID().toString());
                jsonRpcRequest.put("method", "tool.call");
                
                Map<String, Object> requestParams = new HashMap<>();
                requestParams.put("name", toolName);
                requestParams.put("params", params);
                jsonRpcRequest.put("params", requestParams);
                
                // 序列化请求
                String requestBody = gson.toJson(jsonRpcRequest);
                
                // 获取服务器 URL
                String serverUrl = "";
                if (targetServer instanceof SseTransportServer) {
                    serverUrl = ((SseTransportServer) targetServer).url;
                } else if (targetServer instanceof StreamableHTTPServer) {
                    serverUrl = ((StreamableHTTPServer) targetServer).url;
                }
                
                // 创建 HTTP 请求
                Request.Builder requestBuilder = new Request.Builder()
                        .url(serverUrl)
                        .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                        .addHeader("Accept", "application/json");
                
                // 发送请求并获取响应
                Response response = httpClient.newCall(requestBuilder.build()).execute();
                
                if (response.isSuccessful()) {
                    // 解析响应
                    String responseBody = response.body().string();
                    Map<String, Object> responseMap = gson.fromJson(responseBody, new TypeToken<Map<String, Object>>(){}.getType());
                    result.putAll(responseMap);
                    result.put("success", true);
                    result.put("toolName", toolName);
                    result.put("serverName", targetServer.commonOptions.name);
                } else {
                    // 处理错误响应
                    result.put("success", false);
                    result.put("error", "HTTP 错误: " + response.code() + " " + response.message());
                    result.put("toolName", toolName);
                    result.put("serverName", targetServer.commonOptions.name);
                }
                
                response.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("error", "远程工具调用失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 移除已注册的 Lua MCP 工具
     * @param toolName 工具名称
     * @return 是否移除成功
     */
    public boolean removeMCPTool(String toolName) {
        if (toolRegistry.containsKey(toolName)) {
            toolRegistry.remove(toolName);
            toolSchemaRegistry.remove(toolName);
            toolDescriptions.remove(toolName);
            disabledBuiltinTools.remove(toolName);
            return true;
        }
        return false;
    }
    
    /**
     * 将 Lua 值转换为 Java 对象
     * @param L LuaState 对象
     * @param index 栈索引
     * @return 转换后的 Java 对象
     */
    private Object convertLuaValueToJava(LuaState L, int index) {
        try {
            switch (L.type(index)) {
                case LuaState.LUA_TNIL:
                    return null;
                case LuaState.LUA_TBOOLEAN:
                    return L.toBoolean(index);
                case LuaState.LUA_TNUMBER:
                    return L.toNumber(index);
                case LuaState.LUA_TSTRING:
                    return L.toString(index);
                case LuaState.LUA_TTABLE:
                    // 转换 Lua table 为 Java Map
                    Map<String, Object> map = new HashMap<>();
                    
                    try {
                        // 保存当前栈顶位置
                        int top = L.getTop();
                        
                        // 压入 nil 作为第一个键
                        L.pushNil();
                        
                        // 关键修复：当压入nil后，table的相对索引需要调整
                        // 如果index是相对索引，压入nil后需要减1；如果是绝对索引则不需要
                        int tableIndex = index;
                        if (index < 0) {
                            // 相对索引，压入nil后table的位置变为index-1
                            tableIndex = index - 1;
                        }
                        
                        // 迭代 table
                        while (L.next(tableIndex) != 0) {
                            // 此时栈顶是值，下面是键
                            String key;
                            if (L.isString(-2)) {
                                key = L.toString(-2);
                            } else if (L.isNumber(-2)) {
                                key = String.valueOf(L.toNumber(-2));
                            } else {
                                key = String.valueOf(L.toJavaObject(-2));
                            }
                            
                            // 递归转换值
                            Object value = convertLuaValueToJava(L, -1);
                            
                            map.put(key, value);
                            
                            // 弹出值，保留键用于下一次迭代
                            L.pop(1);
                        }
                    } catch (Exception e) {
                        System.err.println("迭代 Lua table 失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    return map;
                default:
                    // 其他类型转换为字符串或Java对象
                    return L.toJavaObject(index);
            }
        } catch (Exception e) {
            // 捕获所有异常，避免闪退
            e.printStackTrace();
            System.err.println("转换 Lua 值到 Java 对象失败: " + e.getMessage());
            return null;
        }
    }
    
    // 新的内置工具实现
    
    /**
     * 创建目录 MCP 工具
     * @param params 工具参数，包含 dirPath 字段
     * @return 操作结果
     */
    private Map<String, Object> callCreateDirectoryTool(Map<String, Object> params) throws IOException {
        String dirPath = (String) params.get("dirPath");
        if (dirPath == null || dirPath.isEmpty()) {
            throw new IllegalArgumentException("dirPath 参数不能为空");
        }
        
        String processedPath = getProcessedFilePath(dirPath);
        java.io.File dir = new java.io.File(processedPath);
        boolean success = dir.mkdirs();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("dirPath", processedPath);
        result.put("message", success ? "目录创建成功" : "目录已存在");
        return result;
    }
    
    /**
     * 删除文件 MCP 工具
     * @param params 工具参数，包含 filePath 和 recursive 字段
     * @return 操作结果
     */
    private Map<String, Object> callDeleteFileTool(Map<String, Object> params) throws IOException {
        String filePath = (String) params.get("filePath");
        boolean recursive = params.get("recursive") instanceof Boolean ? (Boolean) params.get("recursive") : false;
        
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath 参数不能为空");
        }
        
        String processedPath = getProcessedFilePath(filePath);
        java.io.File file = new java.io.File(processedPath);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("文件不存在: " + processedPath);
        }
        
        boolean success;
        if (file.isDirectory()) {
            if (!recursive) {
                throw new IOException("目标是目录，请设置 recursive=true 来删除目录");
            }
            
            // 递归删除目录
            success = deleteDirectory(file);
        } else {
            // 删除单个文件
            success = file.delete();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("filePath", processedPath);
        result.put("recursive", recursive);
        result.put("message", success ? (file.isDirectory() ? "目录删除成功" : "文件删除成功") : (file.isDirectory() ? "目录删除失败" : "文件删除失败"));
        return result;
    }
    
    /**
     * 递归删除目录
     * @param dir 要删除的目录
     * @return 是否删除成功
     */
    private boolean deleteDirectory(java.io.File dir) {
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return dir.delete();
    }
    
    /**
     * 重命名文件 MCP 工具
     * @param params 工具参数，包含 oldFilePath 和 newFilePath 字段
     * @return 操作结果
     */
    private Map<String, Object> callRenameFileTool(Map<String, Object> params) throws IOException {
        String oldFilePath = (String) params.get("oldFilePath");
        String newFilePath = (String) params.get("newFilePath");
        
        if (oldFilePath == null || oldFilePath.isEmpty()) {
            throw new IllegalArgumentException("oldFilePath 参数不能为空");
        }
        if (newFilePath == null || newFilePath.isEmpty()) {
            throw new IllegalArgumentException("newFilePath 参数不能为空");
        }
        
        String oldProcessedPath = getProcessedFilePath(oldFilePath);
        String newProcessedPath = getProcessedFilePath(newFilePath);
        
        java.io.File oldFile = new java.io.File(oldProcessedPath);
        if (!oldFile.exists()) {
            throw new java.io.FileNotFoundException("文件不存在: " + oldProcessedPath);
        }
        
        boolean success = oldFile.renameTo(new java.io.File(newProcessedPath));
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("oldFilePath", oldProcessedPath);
        result.put("newFilePath", newProcessedPath);
        result.put("message", success ? "文件重命名成功" : "文件重命名失败");
        return result;
    }
    
    /**
     * 复制文件 MCP 工具
     * @param params 工具参数，包含 sourceFilePath、targetFilePath 和 recursive 字段
     * @return 操作结果
     */
    private Map<String, Object> callCopyFileTool(Map<String, Object> params) throws IOException {
        String sourceFilePath = (String) params.get("sourceFilePath");
        String targetFilePath = (String) params.get("targetFilePath");
        boolean recursive = params.get("recursive") instanceof Boolean ? (Boolean) params.get("recursive") : false;
        
        if (sourceFilePath == null || sourceFilePath.isEmpty()) {
            throw new IllegalArgumentException("sourceFilePath 参数不能为空");
        }
        if (targetFilePath == null || targetFilePath.isEmpty()) {
            throw new IllegalArgumentException("targetFilePath 参数不能为空");
        }
        
        String sourceProcessedPath = getProcessedFilePath(sourceFilePath);
        String targetProcessedPath = getProcessedFilePath(targetFilePath);
        
        java.io.File sourceFile = new java.io.File(sourceProcessedPath);
        if (!sourceFile.exists()) {
            throw new java.io.FileNotFoundException("源文件不存在: " + sourceProcessedPath);
        }
        
        java.io.File targetFile = new java.io.File(targetProcessedPath);
        
        if (sourceFile.isDirectory()) {
            if (!recursive) {
                throw new IOException("源文件是目录，请设置 recursive=true 来复制目录");
            }
            
            // 递归复制目录
            copyDirectory(sourceFile, targetFile);
        } else {
            // 复制单个文件
            java.nio.file.Files.copy(sourceFile.toPath(), targetFile.toPath(), 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sourceFilePath", sourceProcessedPath);
        result.put("targetFilePath", targetProcessedPath);
        result.put("recursive", recursive);
        result.put("message", sourceFile.isDirectory() ? "目录复制成功" : "文件复制成功");
        return result;
    }
    
    /**
     * 递归复制目录
     * @param sourceDir 源目录
     * @param targetDir 目标目录
     * @throws IOException 如果复制过程中发生错误
     */
    private void copyDirectory(java.io.File sourceDir, java.io.File targetDir) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        java.io.File[] files = sourceDir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                java.io.File targetFile = new java.io.File(targetDir, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, targetFile);
                } else {
                    java.nio.file.Files.copy(file.toPath(), targetFile.toPath(), 
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
    
    /**
     * 获取文件信息 MCP 工具
     * @param params 工具参数，包含 filePath 字段
     * @return 文件信息
     */
    private Map<String, Object> callGetFileInfoTool(Map<String, Object> params) throws IOException {
        String filePath = (String) params.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath 参数不能为空");
        }
        
        String processedPath = getProcessedFilePath(filePath);
        java.io.File file = new java.io.File(processedPath);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("文件不存在: " + processedPath);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("filePath", processedPath);
        result.put("name", file.getName());
        result.put("size", file.length());
        result.put("isDirectory", file.isDirectory());
        result.put("lastModified", file.lastModified());
        result.put("canRead", file.canRead());
        result.put("canWrite", file.canWrite());
        result.put("canExecute", file.canExecute());
        return result;
    }
    
    /**
     * 执行命令 MCP 工具
     * @param params 工具参数，包含 command 字段
     * @return 命令执行结果
     */
    private Map<String, Object> callExecuteCommandTool(Map<String, Object> params) throws Exception {
        String command = (String) params.get("command");
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command 参数不能为空");
        }
        
        // 执行命令
        Process process = Runtime.getRuntime().exec(command);
        
        // 读取命令输出
        String stdout = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))
            .lines().collect(java.util.stream.Collectors.joining("\n"));
        
        String stderr = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))
            .lines().collect(java.util.stream.Collectors.joining("\n"));
        
        int exitCode = process.waitFor();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", exitCode == 0);
        result.put("command", command);
        result.put("exitCode", exitCode);
        result.put("stdout", stdout);
        result.put("stderr", stderr);
        result.put("message", exitCode == 0 ? "命令执行成功" : "命令执行失败");
        return result;
    }
    
    // 新的工具输入Schema构建方法
    
    /**
     * 构建 create_directory 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildCreateDirectorySchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> dirPathSchema = new HashMap<>();
        dirPathSchema.put("type", "string");
        dirPathSchema.put("description", "要创建的目录路径");
        dirPathSchema.put("required", true);
        properties.put("dirPath", dirPathSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("dirPath"));
        return schema;
    }
    
    /**
     * 构建 delete_file 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildDeleteFileSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> filePathSchema = new HashMap<>();
        filePathSchema.put("type", "string");
        filePathSchema.put("description", "要删除的文件路径");
        filePathSchema.put("required", true);
        properties.put("filePath", filePathSchema);
        
        Map<String, Object> recursiveSchema = new HashMap<>();
        recursiveSchema.put("type", "boolean");
        recursiveSchema.put("description", "是否递归删除目录，默认为false");
        recursiveSchema.put("required", false);
        properties.put("recursive", recursiveSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("filePath"));
        return schema;
    }
    
    /**
     * 构建 rename_file 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildRenameFileSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> oldFilePathSchema = new HashMap<>();
        oldFilePathSchema.put("type", "string");
        oldFilePathSchema.put("description", "旧文件路径");
        oldFilePathSchema.put("required", true);
        properties.put("oldFilePath", oldFilePathSchema);
        
        Map<String, Object> newFilePathSchema = new HashMap<>();
        newFilePathSchema.put("type", "string");
        newFilePathSchema.put("description", "新文件路径");
        newFilePathSchema.put("required", true);
        properties.put("newFilePath", newFilePathSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("oldFilePath", "newFilePath"));
        return schema;
    }
    
    /**
     * 构建 copy_file 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildCopyFileSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> sourceFilePathSchema = new HashMap<>();
        sourceFilePathSchema.put("type", "string");
        sourceFilePathSchema.put("description", "源文件路径");
        sourceFilePathSchema.put("required", true);
        properties.put("sourceFilePath", sourceFilePathSchema);
        
        Map<String, Object> targetFilePathSchema = new HashMap<>();
        targetFilePathSchema.put("type", "string");
        targetFilePathSchema.put("description", "目标文件路径");
        targetFilePathSchema.put("required", true);
        properties.put("targetFilePath", targetFilePathSchema);
        
        Map<String, Object> recursiveSchema = new HashMap<>();
        recursiveSchema.put("type", "boolean");
        recursiveSchema.put("description", "是否递归复制目录，默认为false");
        recursiveSchema.put("required", false);
        properties.put("recursive", recursiveSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("sourceFilePath", "targetFilePath"));
        return schema;
    }
    
    /**
     * 构建 get_file_info 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildGetFileInfoSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> filePathSchema = new HashMap<>();
        filePathSchema.put("type", "string");
        filePathSchema.put("description", "要获取信息的文件路径");
        filePathSchema.put("required", true);
        properties.put("filePath", filePathSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("filePath"));
        return schema;
    }
    
    /**
     * 构建 execute_command 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildExecuteCommandSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> commandSchema = new HashMap<>();
        commandSchema.put("type", "string");
        commandSchema.put("description", "要执行的命令");
        commandSchema.put("required", true);
        properties.put("command", commandSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("command"));
        return schema;
    }
    
    /**
     * 构建 search_web 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildSearchWebSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> querySchema = new HashMap<>();
        querySchema.put("type", "string");
        querySchema.put("description", "搜索查询词");
        querySchema.put("required", true);
        properties.put("query", querySchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }
    
    /**
     * 注册 MCP 工具
     * @param name 工具名称
     * @param toolFunction 工具函数
     * @param inputSchema 工具输入Schema
     */
    private void registerTool(String name, McpToolFunction toolFunction, Map<String, Object> inputSchema) {
        toolRegistry.put(name, toolFunction);
        toolSchemaRegistry.put(name, inputSchema);
        
        // 注册成功后，同步到所有已连接的 MCP 服务器
        syncToolToAllServers(name, toolDescriptions.getOrDefault(name, name), inputSchema);
    }
    
    /**
     * 构建 read_file 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildReadFileSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> filePathSchema = new HashMap<>();
        filePathSchema.put("type", "string");
        filePathSchema.put("description", "要读取的文件路径");
        filePathSchema.put("required", true);
        properties.put("filePath", filePathSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("filePath"));
        return schema;
    }
    
    /**
     * 构建 write_file 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildWriteFileSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> filePathSchema = new HashMap<>();
        filePathSchema.put("type", "string");
        filePathSchema.put("description", "要写入的文件路径");
        filePathSchema.put("required", true);
        properties.put("filePath", filePathSchema);
        
        Map<String, Object> contentSchema = new HashMap<>();
        contentSchema.put("type", "string");
        contentSchema.put("description", "要写入的文件内容");
        contentSchema.put("required", true);
        properties.put("content", contentSchema);
        
        Map<String, Object> appendSchema = new HashMap<>();
        appendSchema.put("type", "boolean");
        appendSchema.put("description", "是否以追加模式写入，默认为false（覆盖模式）");
        appendSchema.put("required", false);
        properties.put("append", appendSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("filePath", "content"));
        return schema;
    }
    
    /**
     * 构建 list_files 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildListFilesSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> dirPathSchema = new HashMap<>();
        dirPathSchema.put("type", "string");
        dirPathSchema.put("description", "要列出的目录路径");
        dirPathSchema.put("required", true);
        properties.put("dirPath", dirPathSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("dirPath"));
        return schema;
    }
    
    /**
     * 构建 search_files 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildSearchFilesSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> searchTermSchema = new HashMap<>();
        searchTermSchema.put("type", "string");
        searchTermSchema.put("description", "要搜索的内容");
        searchTermSchema.put("required", true);
        properties.put("searchTerm", searchTermSchema);
        
        Map<String, Object> dirPathSchema = new HashMap<>();
        dirPathSchema.put("type", "string");
        dirPathSchema.put("description", "搜索目录，默认为工作区根目录");
        dirPathSchema.put("required", false);
        properties.put("dirPath", dirPathSchema);
        
        Map<String, Object> recursiveSchema = new HashMap<>();
        recursiveSchema.put("type", "boolean");
        recursiveSchema.put("description", "是否递归搜索子目录，默认为true");
        recursiveSchema.put("required", false);
        properties.put("recursive", recursiveSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("searchTerm"));
        return schema;
    }
    
    /**
     * 构建 read_file_lines 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildReadFileLinesSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> filePathSchema = new HashMap<>();
        filePathSchema.put("type", "string");
        filePathSchema.put("description", "要读取的文件路径");
        filePathSchema.put("required", true);
        properties.put("filePath", filePathSchema);
        
        Map<String, Object> startLineSchema = new HashMap<>();
        startLineSchema.put("type", "integer");
        startLineSchema.put("description", "起始行号，默认为1");
        startLineSchema.put("required", false);
        properties.put("startLine", startLineSchema);
        
        Map<String, Object> endLineSchema = new HashMap<>();
        endLineSchema.put("type", "integer");
        endLineSchema.put("description", "结束行号，默认为文件末尾");
        endLineSchema.put("required", false);
        properties.put("endLine", endLineSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("filePath"));
        return schema;
    }
    
    /**
     * 构建 write_file_lines 工具的输入Schema
     * @return 输入Schema
     */
    private Map<String, Object> buildWriteFileLinesSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> filePathSchema = new HashMap<>();
        filePathSchema.put("type", "string");
        filePathSchema.put("description", "要写入的文件路径");
        filePathSchema.put("required", true);
        properties.put("filePath", filePathSchema);
        
        Map<String, Object> startLineSchema = new HashMap<>();
        startLineSchema.put("type", "integer");
        startLineSchema.put("description", "起始行号，默认为1");
        startLineSchema.put("required", false);
        properties.put("startLine", startLineSchema);
        
        Map<String, Object> linesSchema = new HashMap<>();
        linesSchema.put("type", "array");
        linesSchema.put("description", "要写入的行内容列表");
        linesSchema.put("items", Map.of("type", "string"));
        linesSchema.put("required", false);
        properties.put("lines", linesSchema);
        
        Map<String, Object> contentSchema = new HashMap<>();
        contentSchema.put("type", "string");
        contentSchema.put("description", "要写入的文本内容，将按换行符分割为行");
        contentSchema.put("required", false);
        properties.put("content", contentSchema);
        
        schema.put("properties", properties);
        schema.put("required", List.of("filePath"));
        return schema;
    }
    
    /**
     * 初始化 MCP 配置
     */
    private void initMcpConfig() {
        this.mcpEnabled = true; // 默认启用 MCP 功能，允许 AI 自动调用 MCP 工具
        this.mcpServers = new ArrayList<>();
        this.mcpClients = new HashMap<>();
        
        // 创建默认 MCP 服务器配置，确保 AI 能够使用所有注册的工具
        McpServerConfig defaultServer = new StreamableHTTPServer();
        defaultServer.commonOptions.name = "默认 MCP 服务器";
        defaultServer.commonOptions.enable = true;
        mcpServers.add(defaultServer);
        
        // 可以从配置文件或数据库加载 MCP 服务器配置
    }
    
    /**
     * 初始化搜索配置
     */
    private void initSearchConfig() {
        this.webSearchEnabled = false; // 默认禁用网络搜索
        this.searchServices = new ArrayList<>();
        this.searchServiceSelected = 0;
        
        // 初始化默认搜索服务
        Map<String, Object> defaultSearchService = new HashMap<>();
        defaultSearchService.put("id", "default");
        defaultSearchService.put("name", "默认搜索引擎");
        defaultSearchService.put("enabled", true);
        searchServices.add(defaultSearchService);
    }
    
    /**
     * 启用或禁用 MCP
     * @param enabled 是否启用
     */
    public void enableMCP(boolean enabled) {
        this.mcpEnabled = enabled;
        if (!enabled) {
            // 禁用时关闭所有 MCP 客户端连接
            closeAllMcpClients();
        }
    }
    
    /**
     * 获取 MCP 是否启用
     * @return 是否启用
     */
    public boolean isMCPEnabled() {
        return this.mcpEnabled;
    }
    
    /**
     * 设置工作区路径
     * @param path 工作区路径
     */
    public void setWorkspace(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("工作区路径不能为空");
        }
        this.workspacePath = path;
        // 确保工作区路径存在
        java.io.File workspaceDir = new java.io.File(path);
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
    }
    
    /**
     * 获取工作区路径
     * @return 工作区路径
     */
    public String getWorkspace() {
        return this.workspacePath;
    }
    
    /**
     * 启用或禁用工作区
     * @param enabled 是否启用
     */
    public void enableWorkspace(boolean enabled) {
        this.workspaceEnabled = enabled;
    }
    
    /**
     * 获取工作区是否启用
     * @return 是否启用
     */
    public boolean isWorkspaceEnabled() {
        return this.workspaceEnabled;
    }
    
    // 搜索相关方法
    
    /**
     * 启用或禁用网络搜索
     * @param enabled 是否启用
     */
    public void enableWebSearch(boolean enabled) {
        this.webSearchEnabled = enabled;
    }
    
    /**
     * 获取网络搜索是否启用
     * @return 是否启用
     */
    public boolean isWebSearchEnabled() {
        return this.webSearchEnabled;
    }
    
    /**
     * 设置当前选中的搜索服务
     * @param index 搜索服务索引
     */
    public void setSearchServiceSelected(int index) {
        if (index >= 0 && index < searchServices.size()) {
            this.searchServiceSelected = index;
        }
    }
    
    /**
     * 获取当前选中的搜索服务索引
     * @return 搜索服务索引
     */
    public int getSearchServiceSelected() {
        return this.searchServiceSelected;
    }
    
    /**
     * 添加搜索服务
     * @param searchService 搜索服务配置
     */
    public void addSearchService(Map<String, Object> searchService) {
        this.searchServices.add(searchService);
    }
    
    /**
     * 获取所有搜索服务
     * @return 搜索服务列表
     */
    public List<Map<String, Object>> getSearchServices() {
        return new ArrayList<>(this.searchServices);
    }
    
    // 对话管理方法
    
    /**
     * 创建新对话
     * @return 对话ID
     */
    public synchronized String createConversation() {
        String conversationId = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(conversationId);
        conversations.put(conversationId, conversation);
        currentConversationId.set(conversationId);
        return conversationId;
    }
    
    /**
     * 切换到指定对话
     * @param conversationId 对话ID
     */
    public synchronized void switchConversation(String conversationId) {
        if (!conversations.containsKey(conversationId)) {
            throw new IllegalArgumentException("对话不存在: " + conversationId);
        }
        currentConversationId.set(conversationId);
    }
    
    /**
     * 清空当前对话的上下文
     */
    public synchronized void clearCurrentContext() {
        String conversationId = currentConversationId.get();
        if (conversationId != null && conversations.containsKey(conversationId)) {
            Conversation conversation = conversations.get(conversationId);
            conversation.setMessages(new ArrayList<>());
            conversation.setUpdatedAt(System.currentTimeMillis());
        }
    }
    
    /**
     * 清空指定对话的上下文
     * @param conversationId 对话ID
     */
    public synchronized void clearContext(String conversationId) {
        if (conversations.containsKey(conversationId)) {
            Conversation conversation = conversations.get(conversationId);
            conversation.setMessages(new ArrayList<>());
            conversation.setUpdatedAt(System.currentTimeMillis());
        }
    }
    
    /**
     * 获取所有对话
     * @return 对话列表
     */
    public synchronized List<Map<String, Object>> getAllConversations() {
        List<Map<String, Object>> conversationList = new ArrayList<>();
        for (Conversation conversation : conversations.values()) {
            Map<String, Object> conversationInfo = new HashMap<>();
            conversationInfo.put("id", conversation.getId());
            conversationInfo.put("createdAt", conversation.getCreatedAt());
            conversationInfo.put("updatedAt", conversation.getUpdatedAt());
            conversationInfo.put("modelId", conversation.getCurrentModelId());
            conversationInfo.put("provider", conversation.getCurrentProvider());
            conversationList.add(conversationInfo);
        }
        // 按更新时间降序排序
        conversationList.sort((a, b) -> Long.compare((Long) b.get("updatedAt"), (Long) a.get("updatedAt")));
        return conversationList;
    }
    
    /**
     * 删除指定对话
     * @param conversationId 对话ID
     */
    public synchronized void deleteConversation(String conversationId) {
        if (conversations.containsKey(conversationId)) {
            // 如果删除的是当前对话，重置当前对话ID
            if (conversationId.equals(currentConversationId.get())) {
                currentConversationId.set(null);
            }
            conversations.remove(conversationId);
        }
    }
    
    /**
     * 获取当前对话
     * @return 当前对话
     */
    public synchronized Conversation getCurrentConversation() {
        String convId = currentConversationId.get();
        if (convId == null || !conversations.containsKey(convId)) {
            // 如果没有当前对话，创建一个新对话
            return conversations.get(createConversation());
        }
        return conversations.get(convId);
    }
    
    /**
     * 根据ID获取对话
     * @param conversationId 对话ID
     * @return 对话对象
     */
    public synchronized Conversation getConversation(String conversationId) {
        if (!conversations.containsKey(conversationId)) {
            throw new IllegalArgumentException("对话不存在: " + conversationId);
        }
        return conversations.get(conversationId);
    }
    
    /**
     * 发送消息到当前对话
     * @param content 消息内容
     * @return 对话ID
     */
    public synchronized String sendMessage(String content) {
        return sendMessage(content, null);
    }
    
    /**
     * 发送消息到指定对话
     * @param content 消息内容
     * @param conversationId 对话ID，null表示使用当前对话
     * @return 对话ID
     */
    public synchronized String sendMessage(String content, String conversationId) {
        Conversation conversation;
        if (conversationId != null && conversations.containsKey(conversationId)) {
            conversation = conversations.get(conversationId);
            // 更新当前对话ID为指定的对话ID
            currentConversationId.set(conversationId);
        } else {
            conversation = getCurrentConversation();
        }
        Message message = new Message(MessageRole.USER, content);
        conversation.addMessage(message);
        return conversation.getId();
    }
    
    /**
     * 发送系统消息到当前对话
     * @param content 系统消息内容
     */
    public synchronized void sendSystemMessage(String content) {
        sendSystemMessage(content, null);
    }
    
    /**
     * 发送系统消息到指定对话
     * @param content 系统消息内容
     * @param conversationId 对话ID，null表示使用当前对话
     */
    public synchronized void sendSystemMessage(String content, String conversationId) {
        Conversation conversation;
        if (conversationId != null && conversations.containsKey(conversationId)) {
            conversation = conversations.get(conversationId);
            // 更新当前对话ID为指定的对话ID
            currentConversationId.set(conversationId);
        } else {
            conversation = getCurrentConversation();
        }
        Message message = new Message(MessageRole.SYSTEM, content);
        conversation.addMessage(message);
    }
    
    /**
     * 处理AI响应，包括工具调用
     * @param responseJson AI响应JSON
     * @param conversation 目标对话对象
     * @return 处理结果
     */
    public synchronized Map<String, Object> processAIResponse(JsonObject responseJson, Conversation conversation) {
        Map<String, Object> result = new HashMap<>();
        List<Message> messages = new ArrayList<>();
        boolean hasToolCall = false;
        
        try {
            // 解析AI响应
            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject messageObj = choice.getAsJsonObject("message");
                
                if (messageObj != null) {
                    // 创建助手消息
                    Message assistantMessage = new Message(MessageRole.ASSISTANT, "");
                    
                    // 检查是否有内容
                    if (messageObj.has("content")) {
                        JsonElement contentElement = messageObj.get("content");
                        if (!contentElement.isJsonNull()) {
                            assistantMessage.setContent(contentElement.getAsString());
                        }
                    }
                    
                    // 检查是否有工具调用
                    if (messageObj.has("tool_calls")) {
                        JsonArray toolCalls = messageObj.getAsJsonArray("tool_calls");
                        if (toolCalls != null && toolCalls.size() > 0) {
                            hasToolCall = true;
                            
                            // 处理每个工具调用
                            for (JsonElement toolCallElement : toolCalls) {
                                JsonObject toolCall = toolCallElement.getAsJsonObject();
                                String toolCallId = toolCall.get("id").getAsString();
                                JsonObject function = toolCall.getAsJsonObject("function");
                                String toolName = function.get("name").getAsString();
                                String argumentsJson = function.get("arguments").getAsString();
                                
                                // 解析工具参数
                                Map<String, Object> arguments = gson.fromJson(
                                    argumentsJson, 
                                    new TypeToken<Map<String, Object>>() {}.getType()
                                );
                                
                                // 创建工具调用消息
                                Message toolCallMessage = new Message(
                                    MessageRole.ASSISTANT, 
                                    "", 
                                    true, 
                                    false
                                );
                                toolCallMessage.setToolCallId(toolCallId);
                                toolCallMessage.setToolName(toolName);
                                toolCallMessage.setToolArguments(arguments);
                                messages.add(toolCallMessage);
                                
                                // 执行工具调用
                                Map<String, Object> toolResult = executeToolCall(toolName, arguments);
                                
                                // 创建工具响应消息
                                Message toolResponseMessage = new Message(
                                    MessageRole.TOOL, 
                                    "", 
                                    false, 
                                    true
                                );
                                toolResponseMessage.setToolCallId(toolCallId);
                                toolResponseMessage.setToolResult(toolResult);
                                messages.add(toolResponseMessage);
                            }
                        }
                    }
                    
                    // 如果助手消息有内容，添加到消息列表
                    if (!assistantMessage.getContent().isEmpty()) {
                        messages.add(assistantMessage);
                    }
                }
            }
            
            // 将消息添加到指定对话
            if (!messages.isEmpty()) {
                for (Message message : messages) {
                    conversation.addMessage(message);
                }
            }
            
            result.put("success", true);
            result.put("messages", messages);
            result.put("hasToolCall", hasToolCall);
            result.put("conversationId", conversation.getId());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("hasToolCall", false);
        }
        
        return result;
    }
    
    /**
     * 执行工具调用
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 工具执行结果
     */
    public Map<String, Object> executeToolCall(String toolName, Map<String, Object> params) {
        // 如果工具名称以 "mcp__" 开头，移除前缀
        if (toolName.startsWith("mcp__")) {
            toolName = toolName.substring(5);
        }
        
        System.out.println("开始执行工具调用: " + toolName + "，参数: " + params);
        
        // 标记工具正在被调用
        toolCallingStatus.put(toolName, true);
        
        Map<String, Object> result;
        try {
            // 调用 MCP 工具
            result = callMCPTool(toolName, params);
            System.out.println("工具调用成功: " + toolName + "，结果: " + result);
        } catch (Exception e) {
            e.printStackTrace();
            // 处理异常
            result = new HashMap<>();
            result.put("success", false);
            result.put("error", "工具调用失败: " + e.getMessage());
            result.put("toolName", toolName);
            result.put("params", params);
            result.put("toolType", "unknown");
            System.out.println("工具调用失败: " + toolName + "，错误: " + e.getMessage());
        } finally {
            // 标记工具调用完成
            toolCallingStatus.put(toolName, false);
        }
        
        return result;
    }
    
    /**
     * 添加 MCP 服务器，用于 Java 调用
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param config Map 格式的服务器配置
     */
    public void addMCPServer(String type, Map<String, Object> config) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("服务器类型不能为空");
        }
        
        if (config == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }
        
        addMCPServerInternal(type, config);
    }
    
    /**
     * 添加 MCP 服务器，用于 Lua 调用
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param luaTableConfig Lua table 格式的服务器配置
     */
    public void addMCPServer(String type, LuaObject luaTableConfig) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("服务器类型不能为空");
        }
        
        if (luaTableConfig == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }
        
        try {
            synchronized (luaContext) {
                LuaState L = luaContext.getLuaState();
                
                // 获取配置字段
                String url = getLuaStringField(L, luaTableConfig, "url", "");
                String name = getLuaStringField(L, luaTableConfig, "name", "");
                boolean enable = getLuaBooleanField(L, luaTableConfig, "enable", true);
                
                // 创建服务器配置
                McpServerConfig mcpConfig;
                if ("sse".equals(type)) {
                    mcpConfig = new SseTransportServer(UUID.randomUUID(), new McpCommonOptions(), url);
                } else if ("streamable_http".equals(type)) {
                    mcpConfig = new StreamableHTTPServer(UUID.randomUUID(), new McpCommonOptions(), url);
                } else {
                    throw new IllegalArgumentException("不支持的 MCP 服务器类型: " + type);
                }
                
                // 设置通用配置
                mcpConfig.commonOptions.name = name;
                mcpConfig.commonOptions.enable = enable;
                
                // 添加到服务器列表
                this.mcpServers.add(mcpConfig);
                
                // 如果启用了 MCP 且服务器已启用，创建客户端连接
                if (mcpEnabled && mcpConfig.commonOptions.enable) {
                    createMcpClient(mcpConfig);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("添加 MCP 服务器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 添加 MCP 服务器（新方法名），支持 Lua 调用，避免方法重载冲突
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param url 服务器 URL
     */
    public void registerMCPServer(String type, String url) {
        registerMCPServer(type, url, "", true);
    }
    
    /**
     * 添加 MCP 服务器（新方法名），支持 Lua 调用，避免方法重载冲突
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param url 服务器 URL
     * @param name 服务器名称
     */
    public void registerMCPServer(String type, String url, String name) {
        registerMCPServer(type, url, name, true);
    }
    
    /**
     * 添加 MCP 服务器（新方法名），支持 Lua 调用，避免方法重载冲突
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param url 服务器 URL
     * @param name 服务器名称
     * @param enable 是否启用服务器
     */
    public void registerMCPServer(String type, String url, String name, boolean enable) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("服务器类型不能为空");
        }
        
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("服务器 URL 不能为空");
        }
        
        try {
            // 创建服务器配置
            McpServerConfig mcpConfig;
            if ("sse".equals(type)) {
                mcpConfig = new SseTransportServer(UUID.randomUUID(), new McpCommonOptions(), url);
            } else if ("streamable_http".equals(type)) {
                mcpConfig = new StreamableHTTPServer(UUID.randomUUID(), new McpCommonOptions(), url);
            } else {
                throw new IllegalArgumentException("不支持的 MCP 服务器类型: " + type);
            }
            
            // 设置通用配置
            mcpConfig.commonOptions.name = name != null ? name : "";
            mcpConfig.commonOptions.enable = enable;
            
            // 添加到服务器列表
            this.mcpServers.add(mcpConfig);
            
            // 如果启用了 MCP 且服务器已启用，创建客户端连接
            if (mcpEnabled && mcpConfig.commonOptions.enable) {
                // 使用新的 addMcpClient 方法（使用官方 SDK）
                addMcpClient(mcpConfig);
            }
        } catch (Exception e) {
            throw new RuntimeException("添加 MCP 服务器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 添加 MCP 服务器，支持 Java 调用，接受 Map 配置
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param config 服务器配置 Map
     */
    public void registerMCPServerWithConfig(String type, Map<String, Object> config) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("服务器类型不能为空");
        }
        
        if (config == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }
        
        // 从 Map 中提取配置值
        String url = config.getOrDefault("url", "").toString();
        String name = config.getOrDefault("name", "").toString();
        boolean enable = (boolean) config.getOrDefault("enable", true);
        
        // 调用基本方法
        registerMCPServer(type, url, name, enable);
    }
    
    /**
     * 异步注册 MCP 服务器，支持回调函数
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param url 服务器 URL
     * @param name 服务器名称
     * @param enable 是否启用
     * @param callback 回调函数，用于通知注册和工具同步结果
     */
    public void registerMCPServerAsync(String type, String url, String name, boolean enable, LuaObject callback) {
        executorService.submit(() -> {
            try {
                // 创建服务器配置
                McpServerConfig mcpConfig;
                if ("sse".equals(type)) {
                    mcpConfig = new SseTransportServer(UUID.randomUUID(), new McpCommonOptions(), url);
                } else if ("streamable_http".equals(type)) {
                    mcpConfig = new StreamableHTTPServer(UUID.randomUUID(), new McpCommonOptions(), url);
                } else {
                    throw new IllegalArgumentException("不支持的 MCP 服务器类型: " + type);
                }
                
                // 设置通用配置
                mcpConfig.commonOptions.name = name != null ? name : "";
                mcpConfig.commonOptions.enable = enable;
                
                // 添加到服务器列表
                this.mcpServers.add(mcpConfig);
                int serverIndex = this.mcpServers.size() - 1;
                
                // 如果启用了 MCP 且服务器已启用，创建客户端连接
                if (mcpEnabled && mcpConfig.commonOptions.enable) {
                    // 使用新的 addMcpClient 方法（使用官方 SDK）
                    addMcpClient(mcpConfig);
                }
                
                // 构建成功结果
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("serverName", mcpConfig.commonOptions.name);
                result.put("serverType", type);
                result.put("serverUrl", url);
                result.put("serverIndex", serverIndex);
                result.put("syncedTools", new ArrayList<>()); // 暂时返回空列表
                result.put("toolCount", 0); // 暂时返回0
                
                // 调用回调函数，通知注册成功
                invokeCallback(callback, result, null);
            } catch (Exception e) {
                // 调用回调函数，通知注册失败
                invokeCallback(callback, null, "注册 MCP 服务器失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 异步注册 MCP 服务器，简化版，使用默认参数
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param url 服务器 URL
     * @param callback 回调函数，用于通知注册和工具同步结果
     */
    public void registerMCPServerAsync(String type, String url, LuaObject callback) {
        registerMCPServerAsync(type, url, "", true, callback);
    }
    
    /**
     * 异步注册 MCP 服务器，简化版，支持自定义名称
     * @param type 服务器类型（"sse" 或 "streamable_http"）
     * @param url 服务器 URL
     * @param name 服务器名称
     * @param callback 回调函数，用于通知注册和工具同步结果
     */
    public void registerMCPServerAsync(String type, String url, String name, LuaObject callback) {
        registerMCPServerAsync(type, url, name, true, callback);
    }
    
    /**
     * 辅助方法：从 Lua table 中获取字符串字段
     * @param L LuaState
     * @param luaTable Lua table 对象
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    private String getLuaStringField(LuaState L, LuaObject luaTable, String field, String defaultValue) {
        try {
            Object value = luaTable.getField(field);
            if (value instanceof String) {
                return (String) value;
            } else if (value != null) {
                return value.toString();
            }
        } catch (Exception e) {
            // 忽略异常，返回默认值
        }
        return defaultValue;
    }
    
    /**
     * 辅助方法：从 Lua table 中获取布尔字段
     * @param L LuaState
     * @param luaTable Lua table 对象
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    private boolean getLuaBooleanField(LuaState L, LuaObject luaTable, String field, boolean defaultValue) {
        try {
            Object value = luaTable.getField(field);
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
        } catch (Exception e) {
            // 忽略异常，返回默认值
        }
        return defaultValue;
    }
    
    /**
     * 内部方法：实际执行 MCP 服务器添加逻辑
     * @param type 服务器类型
     * @param config 服务器配置 Map
     */
    private void addMCPServerInternal(String type, Map<String, Object> config) {
        McpServerConfig mcpConfig;
        if ("sse".equals(type)) {
            String url = config.getOrDefault("url", "").toString();
            mcpConfig = new SseTransportServer(UUID.randomUUID(), new McpCommonOptions(), url);
        } else if ("streamable_http".equals(type)) {
            String url = config.getOrDefault("url", "").toString();
            mcpConfig = new StreamableHTTPServer(UUID.randomUUID(), new McpCommonOptions(), url);
        } else {
            throw new IllegalArgumentException("不支持的 MCP 服务器类型: " + type);
        }
        
        // 设置通用配置
        if (config.containsKey("name")) {
            mcpConfig.commonOptions.name = config.get("name").toString();
        }
        if (config.containsKey("enable")) {
            mcpConfig.commonOptions.enable = (boolean) config.get("enable");
        }
        if (config.containsKey("headers")) {
            List<Map<String, String>> headers = (List<Map<String, String>>) config.get("headers");
            mcpConfig.commonOptions.headers.addAll(headers);
        }
        
        this.mcpServers.add(mcpConfig);
        
        // 如果启用了 MCP 且服务器已启用，创建客户端连接
        if (mcpEnabled && mcpConfig.commonOptions.enable) {
            addMcpClient(mcpConfig);
        }
    }
    
    /**
     * 删除 MCP 服务器
     * @param index 服务器索引
     */
    public void removeMCPServer(int index) {
        if (index < 0 || index >= mcpServers.size()) {
            throw new IndexOutOfBoundsException("无效的 MCP 服务器索引");
        }
        
        McpServerConfig config = mcpServers.remove(index);
        // 关闭并移除对应的客户端连接
        closeMcpClient(config.id);
    }
    
    /**
     * 根据 ID 删除 MCP 服务器
     * @param serverId 服务器 ID
     */
    public void removeMCPServerById(String serverId) {
        UUID id = UUID.fromString(serverId);
        mcpServers.removeIf(config -> config.id.equals(id));
        // 关闭并移除对应的客户端连接
        closeMcpClient(id);
    }
    
    /**
     * 获取 MCP 服务器列表
     * @return MCP 服务器列表
     */
    public List<Map<String, Object>> getMCPServers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (McpServerConfig config : mcpServers) {
            Map<String, Object> serverMap = new HashMap<>();
            serverMap.put("id", config.id.toString());
            serverMap.put("name", config.commonOptions.name);
            serverMap.put("enable", config.commonOptions.enable);
            serverMap.put("url", config instanceof SseTransportServer ? ((SseTransportServer) config).url : ((StreamableHTTPServer) config).url);
            serverMap.put("type", config instanceof SseTransportServer ? "sse" : "streamable_http");
            serverMap.put("headers", config.commonOptions.headers);
            
            // 转换工具列表
            List<Map<String, Object>> toolsList = new ArrayList<>();
            for (McpTool tool : config.commonOptions.tools) {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("name", tool.name);
                toolMap.put("enable", tool.enable);
                toolMap.put("description", tool.description);
                toolMap.put("inputSchema", tool.inputSchema);
                toolsList.add(toolMap);
            }
            serverMap.put("tools", toolsList);
            
            // 添加服务器状态
            McpClient client = mcpClients.get(config.id);
            if (client != null) {
                serverMap.put("status", client.status.status);
                serverMap.put("statusMessage", client.status.message);
            } else {
                serverMap.put("status", "IDLE");
                serverMap.put("statusMessage", "未连接");
            }
            
            result.add(serverMap);
        }
        return result;
    }
    

    
    /**
     * 获取 MCP 服务器详情，包括服务器配置、状态和工具列表
     * @param serverId 服务器 ID
     * @return 服务器详情，包含工具列表
     */
    public Map<String, Object> getMCPServerDetails(String serverId) {
        UUID id = UUID.fromString(serverId);
        Map<String, Object> serverDetails = new HashMap<>();
        
        // 查找指定 ID 的服务器
        for (McpServerConfig config : mcpServers) {
            if (config.id.equals(id)) {
                serverDetails.put("id", config.id.toString());
                serverDetails.put("name", config.commonOptions.name);
                serverDetails.put("enable", config.commonOptions.enable);
                serverDetails.put("url", config instanceof SseTransportServer ? ((SseTransportServer) config).url : ((StreamableHTTPServer) config).url);
                serverDetails.put("type", config instanceof SseTransportServer ? "sse" : "streamable_http");
                serverDetails.put("headers", config.commonOptions.headers);
                
                // 转换工具列表
                List<Map<String, Object>> toolsList = new ArrayList<>();
                for (McpTool tool : config.commonOptions.tools) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", tool.name);
                    toolMap.put("enable", tool.enable);
                    toolMap.put("description", tool.description);
                    toolMap.put("inputSchema", tool.inputSchema);
                    toolsList.add(toolMap);
                }
                serverDetails.put("tools", toolsList);
                
                // 添加服务器状态
                McpClient client = mcpClients.get(config.id);
                if (client != null) {
                    serverDetails.put("status", client.status.status);
                    serverDetails.put("statusMessage", client.status.message);
                } else {
                    serverDetails.put("status", "IDLE");
                    serverDetails.put("statusMessage", "未连接");
                }
                
                break;
            }
        }
        
        return serverDetails;
    }
    
    /**
     * 获取所有 MCP 工具，包括服务器和连接信息
     * @return 所有 MCP 工具列表
     */
    public List<Map<String, Object>> getAllMCPTools() {
        List<Map<String, Object>> allTools = new ArrayList<>();
        
        // 添加本地工具
        for (Map.Entry<String, McpToolFunction> entry : toolRegistry.entrySet()) {
            String toolName = entry.getKey();
            boolean isBuiltin = builtinTools.contains(toolName);
            
            Map<String, Object> toolInfo = new HashMap<>();
            toolInfo.put("name", toolName);
            toolInfo.put("type", isBuiltin ? "builtin" : "lua");
            toolInfo.put("description", toolDescriptions.getOrDefault(toolName, isBuiltin ? "内置工具" : "Lua工具"));
            toolInfo.put("inputSchema", toolSchemaRegistry.get(toolName));
            toolInfo.put("enabled", !disabledBuiltinTools.contains(toolName));
            toolInfo.put("connectionType", "local");
            toolInfo.put("serverName", "本地");
            toolInfo.put("serverStatus", "connected");
            
            allTools.add(toolInfo);
        }
        
        // 添加远程工具
        for (McpServerConfig serverConfig : mcpServers) {
            if (!serverConfig.commonOptions.enable) continue;
            
            for (McpTool tool : serverConfig.commonOptions.tools) {
                if (!tool.enable) continue;
                
                Map<String, Object> toolInfo = new HashMap<>();
                toolInfo.put("name", tool.name);
                toolInfo.put("type", "remote");
                toolInfo.put("description", tool.description);
                toolInfo.put("inputSchema", tool.inputSchema);
                toolInfo.put("enabled", tool.enable);
                String connectionType = serverConfig instanceof SseTransportServer ? "sse" : "streamable_http";
                toolInfo.put("connectionType", connectionType);
                toolInfo.put("connectionTypeName", connectionType);
                toolInfo.put("serverName", serverConfig.commonOptions.name);
                toolInfo.put("serverId", serverConfig.id.toString());
                
                // 添加服务器状态
                McpClient client = mcpClients.get(serverConfig.id);
                if (client != null) {
                    toolInfo.put("serverStatus", client.status.status);
                } else {
                    toolInfo.put("serverStatus", "IDLE");
                }
                
                allTools.add(toolInfo);
            }
        }
        
        return allTools;
    }
    
    /**
     * 设置 MCP 服务器列表
     * @param servers MCP 服务器列表
     */
    public void setMCPServers(List<Map<String, Object>> servers) {
        if (servers == null) {
            this.mcpServers = new ArrayList<>();
        } else {
            this.mcpServers = new ArrayList<>();
            for (Map<String, Object> server : servers) {
                String type = server.getOrDefault("type", "sse").toString();
                addMCPServer(type, server);
            }
        }
    }
    
    /**
     * 更新 MCP 服务器配置
     * @param serverId 服务器 ID
     * @param config 更新后的配置
     */
    public void updateMCPServer(String serverId, Map<String, Object> config) {
        UUID id = UUID.fromString(serverId);
        int index = -1;
        for (int i = 0; i < mcpServers.size(); i++) {
            if (mcpServers.get(i).id.equals(id)) {
                index = i;
                break;
            }
        }
        
        if (index == -1) {
            throw new IllegalArgumentException("找不到指定的 MCP 服务器: " + serverId);
        }
        
        // 删除旧服务器
        removeMCPServer(index);
        // 添加新配置的服务器
        String type = config.getOrDefault("type", "sse").toString();
        addMCPServer(type, config);
    }
    
    /**
     * 获取 MCP 服务器状态
     * @param serverId 服务器 ID
     * @return 服务器状态
     */
    public Map<String, Object> getMCPServerStatus(String serverId) {
        UUID id = UUID.fromString(serverId);
        McpClient client = mcpClients.get(id);
        Map<String, Object> status = new HashMap<>();
        
        if (client != null) {
            status.put("status", client.status.status);
            status.put("message", client.status.message);
        } else {
            status.put("status", "IDLE");
            status.put("message", "未连接");
        }
        
        return status;
    }
    
    /**
     * 根据服务器 ID 获取该服务器的 MCP 工具列表
     * @param serverId 服务器 ID
     * @return 工具列表
     */
    public List<Map<String, Object>> getToolsByServerId(String serverId) {
        UUID id = UUID.fromString(serverId);
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 查找指定 ID 的服务器
        for (McpServerConfig serverConfig : mcpServers) {
            if (serverConfig.id.equals(id)) {
                // 转换工具列表
                for (McpTool tool : serverConfig.commonOptions.tools) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", tool.name);
                    toolMap.put("enable", tool.enable);
                    toolMap.put("description", tool.description);
                    toolMap.put("inputSchema", tool.inputSchema);
                    toolMap.put("serverName", serverConfig.commonOptions.name);
                    toolMap.put("serverId", serverId);
                    toolMap.put("connectionType", serverConfig instanceof SseTransportServer ? "sse" : "streamable_http");
                    tools.add(toolMap);
                }
                break;
            }
        }
        
        return tools;
    }
    
    /**
     * 根据连接类型获取 MCP 工具列表
     * @param connectionType 连接类型（"sse" 或 "streamable_http"）
     * @return 工具列表
     */
    public List<Map<String, Object>> getToolsByConnectionType(String connectionType) {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        for (McpServerConfig serverConfig : mcpServers) {
            // 检查服务器类型是否匹配
            boolean isSse = serverConfig instanceof SseTransportServer;
            boolean isStreamableHttp = serverConfig instanceof StreamableHTTPServer;
            
            if (("sse".equals(connectionType) && isSse) || ("streamable_http".equals(connectionType) && isStreamableHttp)) {
                // 转换工具列表
                for (McpTool tool : serverConfig.commonOptions.tools) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", tool.name);
                    toolMap.put("enable", tool.enable);
                    toolMap.put("description", tool.description);
                    toolMap.put("inputSchema", tool.inputSchema);
                    toolMap.put("serverName", serverConfig.commonOptions.name);
                    toolMap.put("serverId", serverConfig.id.toString());
                    toolMap.put("connectionType", connectionType);
                    tools.add(toolMap);
                }
            }
        }
        
        return tools;
    }
    
    /**
     * 获取所有连接的 MCP 服务器的工具列表
     * @return 工具列表
     */
    public List<Map<String, Object>> getConnectedServerTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        for (Map.Entry<UUID, McpClient> entry : mcpClients.entrySet()) {
            McpClient client = entry.getValue();
            // 只返回已连接的服务器工具
            if (client.status == McpStatus.Connected()) {
                // 转换工具列表
                for (McpTool tool : client.config.commonOptions.tools) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", tool.name);
                    toolMap.put("enable", tool.enable);
                    toolMap.put("description", tool.description);
                    toolMap.put("inputSchema", tool.inputSchema);
                    toolMap.put("serverName", client.config.commonOptions.name);
                    toolMap.put("serverId", client.config.id.toString());
                    toolMap.put("connectionType", client.config instanceof SseTransportServer ? "sse" : "streamable_http");
                    toolMap.put("serverStatus", client.status.status);
                    tools.add(toolMap);
                }
            }
        }
        
        return tools;
    }
    
    /**
     * 获取所有 MCP 服务器的工具列表，按服务器分组
     * @return 按服务器分组的工具列表
     */
    public Map<String, List<Map<String, Object>>> getToolsGroupedByServer() {
        Map<String, List<Map<String, Object>>> groupedTools = new HashMap<>();
        
        for (McpServerConfig serverConfig : mcpServers) {
            String serverKey = serverConfig.commonOptions.name + " (" + (serverConfig instanceof SseTransportServer ? "SSE" : "Streamable HTTP") + ")";
            List<Map<String, Object>> serverTools = new ArrayList<>();
            
            // 转换工具列表
            for (McpTool tool : serverConfig.commonOptions.tools) {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("name", tool.name);
                toolMap.put("enable", tool.enable);
                toolMap.put("description", tool.description);
                toolMap.put("inputSchema", tool.inputSchema);
                serverTools.add(toolMap);
            }
            
            groupedTools.put(serverKey, serverTools);
        }
        
        return groupedTools;
    }
    
    /**
     * 获取所有可用的MCP工具列表，按工具类型分组
     * @return 按工具类型分组的工具列表
     */
    public Map<String, List<Map<String, Object>>> getToolsGroupedByType() {
        Map<String, List<Map<String, Object>>> groupedTools = new HashMap<>();
        
        // 初始化分组
        groupedTools.put("builtin", new ArrayList<>());
        groupedTools.put("lua", new ArrayList<>());
        groupedTools.put("remote", new ArrayList<>());
        
        // 获取所有可用工具
        List<Map<String, Object>> allTools = getAvailableTools();
        
        // 按类型分组
        for (Map<String, Object> tool : allTools) {
            String type = (String) tool.get("type");
            groupedTools.getOrDefault(type, new ArrayList<>()).add(tool);
        }
        
        return groupedTools;
    }
    
    /**
     * 将单个工具同步到所有已连接的 MCP 服务器
     * @param toolName 工具名称
     * @param description 工具描述
     * @param inputSchema 工具输入Schema
     */
    private void syncToolToAllServers(String toolName, String description, Map<String, Object> inputSchema) {
        for (McpServerConfig serverConfig : mcpServers) {
            if (serverConfig.commonOptions.enable) {
                // 检查工具是否已存在
                boolean toolExists = serverConfig.commonOptions.tools.stream()
                    .anyMatch(tool -> tool.name.equals(toolName));
                
                if (!toolExists) {
                    // 添加工具到服务器配置
                    McpTool newTool = new McpTool(true, toolName, description, inputSchema);
                    serverConfig.commonOptions.tools.add(newTool);
                    System.out.println("已将工具 " + toolName + " 同步到 MCP 服务器: " + serverConfig.commonOptions.name);
                }
            }
        }
    }
    
    /**
     * 获取处理后的文件路径，确保在启用工作区时，所有文件操作都在工作区内进行
     * @param path 原始文件路径
     * @return 处理后的文件路径
     */
    private String getProcessedFilePath(String path) {
        if (workspaceEnabled) {
            // 如果是绝对路径，确保它在工作区内
            java.io.File file = new java.io.File(path);
            if (file.isAbsolute()) {
                // 检查文件是否在工作区内
                String absolutePath = file.getAbsolutePath();
                String absoluteWorkspacePath = new java.io.File(workspacePath).getAbsolutePath();
                if (absolutePath.startsWith(absoluteWorkspacePath)) {
                    return absolutePath;
                } else {
                    // 否则，将其视为工作区内的相对路径
                    return new java.io.File(workspacePath, file.getName()).getAbsolutePath();
                }
            } else {
                // 如果是相对路径，直接在工作区内构建
                return new java.io.File(workspacePath, path).getAbsolutePath();
            }
        } else {
            // 工作区未启用，直接返回原始路径
            return path;
        }
    }
    
    /**
     * 调用 MCP 工具
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 工具调用结果
     */
    public Map<String, Object> callMCPTool(String toolName, Map<String, Object> params) {
        if (!mcpEnabled) {
            throw new IllegalStateException("MCP 未启用");
        }
        
        try {
            // 首先检查本地工具注册表
            if (toolRegistry.containsKey(toolName)) {
                // 调用本地工具
                McpToolFunction toolFunction = toolRegistry.get(toolName);
                Map<String, Object> result = toolFunction.call(params);
                
                // 增强结果，添加工具信息
                result.put("toolName", toolName);
                result.put("toolType", builtinTools.contains(toolName) ? "builtin" : "lua");
                return result;
            }
            
            // 如果本地没有该工具，查找 MCP 服务器工具
            McpClient targetClient = null;
            McpTool targetTool = null;
            for (McpClient client : mcpClients.values()) {
                for (McpTool tool : client.config.commonOptions.tools) {
                    if (tool.enable && toolName.equals(tool.name)) {
                        targetClient = client;
                        targetTool = tool;
                        break;
                    }
                }
                if (targetClient != null) {
                    break;
                }
            }
            
            if (targetClient == null) {
                throw new IllegalArgumentException("找不到可用的 MCP 工具: " + toolName);
            }
            
            // 使用官方 SDK 调用远程 MCP 工具
            if (targetClient.client == null) {
                throw new IllegalStateException("MCP 客户端未初始化");
            }
            
            Map<String, Object> result;
            // 检查客户端类型，决定调用方式
            if (targetClient.client instanceof io.modelcontextprotocol.kotlin.sdk.client.Client) {
                try {
                    // 使用官方 SDK 调用工具
                    io.modelcontextprotocol.kotlin.sdk.client.Client sdkClient = 
                        (io.modelcontextprotocol.kotlin.sdk.client.Client) targetClient.client;
                    Object toolResult = McpSdkWrapper.INSTANCE.callTool(sdkClient, toolName, params).join();
                    
                    // 处理返回结果
                    if (toolResult instanceof Map) {
                        result = (Map<String, Object>) toolResult;
                    } else {
                        // 如果结果不是 Map，包装成 Map 格式
                        result = new HashMap<>();
                        result.put("result", toolResult);
                    }
                    
                    // 增强结果，添加工具信息
                    result.put("success", true);
                    result.put("toolName", toolName);
                    result.put("toolType", "remote");
                    result.put("server", targetClient.config.commonOptions.name);
                } catch (Exception e) {
                    e.printStackTrace();
                    // 处理调用异常
                    result = new HashMap<>();
                    result.put("success", false);
                    result.put("error", "工具调用失败: " + e.getMessage());
                    result.put("toolName", toolName);
                    result.put("params", params);
                    result.put("toolType", "remote");
                    result.put("server", targetClient.config.commonOptions.name);
                }
            } else {
                // 旧的实现，使用自定义 MCP 协议
                // 构建 MCP JSON-RPC 请求
                Map<String, Object> jsonRpcRequest = new HashMap<>();
                jsonRpcRequest.put("jsonrpc", "2.0");
                jsonRpcRequest.put("id", UUID.randomUUID().toString());
                jsonRpcRequest.put("method", "tool.call");
                
                Map<String, Object> requestParams = new HashMap<>();
                requestParams.put("name", toolName);
                requestParams.put("params", params);
                jsonRpcRequest.put("params", requestParams);
                
                // 序列化请求
                String requestBody = gson.toJson(jsonRpcRequest);
                
                // 获取服务器 URL
                String serverUrl = "";
                if (targetClient.config instanceof SseTransportServer) {
                    serverUrl = ((SseTransportServer) targetClient.config).url;
                } else if (targetClient.config instanceof StreamableHTTPServer) {
                    serverUrl = ((StreamableHTTPServer) targetClient.config).url;
                }
                
                // 创建 HTTP 请求
                Request.Builder requestBuilder = new Request.Builder()
                        .url(serverUrl)
                        .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                        .addHeader("Accept", "application/json");
                
                // 发送请求并获取响应
                Response response = httpClient.newCall(requestBuilder.build()).execute();
                
                if (response.isSuccessful()) {
                    // 解析响应
                    String responseBody = response.body().string();
                    result = gson.fromJson(responseBody, new TypeToken<Map<String, Object>>(){}.getType());
                    
                    // 增强结果，添加工具信息
                    result.put("toolName", toolName);
                    result.put("toolType", "remote");
                    result.put("server", targetClient.config.commonOptions.name);
                } else {
                    // 处理错误响应
                    result = new HashMap<>();
                    result.put("success", false);
                    result.put("error", "HTTP 错误: " + response.code() + " " + response.message());
                    result.put("toolName", toolName);
                    result.put("params", params);
                    result.put("server", targetClient.config.commonOptions.name);
                    result.put("toolType", "remote");
                }
                
                response.close();
            }
            
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            // 处理异常
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "工具调用失败: " + e.getMessage());
            result.put("toolName", toolName);
            result.put("params", params);
            result.put("toolType", "remote");
            return result;
        }
    }
    
    /**
     * 同步 MCP 工具列表
     * @param serverIndex 服务器索引
     * @return 工具列表
     */
    public List<Map<String, Object>> syncMCPTools(int serverIndex) {
        if (serverIndex < 0 || serverIndex >= mcpServers.size()) {
            throw new IndexOutOfBoundsException("无效的 MCP 服务器索引");
        }
        
        McpServerConfig config = mcpServers.get(serverIndex);
        
        // 委托给新的 syncMcpTools 方法
        syncMcpTools(config);
        
        // 从配置中获取同步后的工具列表
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpTool tool : config.commonOptions.tools) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("name", tool.name);
            toolMap.put("description", tool.description);
            toolMap.put("enable", tool.enable);
            toolMap.put("inputSchema", tool.inputSchema);
            tools.add(toolMap);
        }
        
        return tools;
    }
    
    /**
     * 同步所有 MCP 服务器的工具列表
     */
    public void syncAllMCPTools() {
        for (int i = 0; i < mcpServers.size(); i++) {
            syncMCPTools(i);
        }
    }
    
    /**
     * 创建 MCP 客户端连接
     * @param config MCP 服务器配置
     */
    private void createMcpClient(McpServerConfig config) {
        if (!config.commonOptions.enable) {
            return;
        }
        
        // 关闭已存在的客户端连接
        closeMcpClient(config.id);
        
        // 创建新的客户端
        McpClient client = new McpClient(config);
        client.status = McpStatus.Connecting();
        mcpClients.put(config.id, client);
        
        // 使用 OkHttp 实现实际的 MCP 客户端连接逻辑
        executorService.submit(() -> {
            try {
                if (config instanceof SseTransportServer) {
                    // 实现 SSE 连接
                    String url = ((SseTransportServer) config).url;
                    
                    // 创建请求构建器
                    Request.Builder requestBuilder = new Request.Builder()
                            .url(url)
                            .get()
                            .addHeader("Accept", "text/event-stream");
                    
                    // 添加自定义头
                    for (Map<String, String> header : config.commonOptions.headers) {
                        for (Map.Entry<String, String> entry : header.entrySet()) {
                            requestBuilder.addHeader(entry.getKey(), entry.getValue());
                        }
                    }
                    
                    // 创建 EventSourceListener
                    okhttp3.sse.EventSourceListener listener = new okhttp3.sse.EventSourceListener() {
                        @Override
                        public void onOpen(okhttp3.sse.EventSource eventSource, Response response) {
                            client.status = McpStatus.Connected();
                            System.out.println("MCP SSE 连接成功: " + config.commonOptions.name);
                            // 触发 open 事件
                            processMcpStreamEvent("open", "", client);
                            // 连接成功后自动同步工具列表
                            int serverIndex = mcpServers.indexOf(config);
                            if (serverIndex != -1) {
                                syncMCPTools(serverIndex);
                            }
                        }
                        
                        @Override
                        public void onEvent(okhttp3.sse.EventSource eventSource, String id, String type, String data) {
                            // 处理 SSE 事件，使用与流 HTTP 相同的处理机制
                            System.out.println("MCP SSE 事件: " + type + " - " + data);
                            processMcpStreamEvent(type, data, client);
                        }
                        
                        @Override
                        public void onClosed(okhttp3.sse.EventSource eventSource) {
                            client.status = McpStatus.Idle();
                            System.out.println("MCP SSE 连接关闭: " + config.commonOptions.name);
                            // 触发 close 事件
                            processMcpStreamEvent("close", "", client);
                        }
                        
                        @Override
                        public void onFailure(okhttp3.sse.EventSource eventSource, Throwable t, Response response) {
                            String errorMessage = t.getMessage() != null ? t.getMessage() : "连接失败";
                            client.status = McpStatus.Error(errorMessage);
                            System.err.println("MCP SSE 连接失败: " + config.commonOptions.name + ", 错误: " + errorMessage);
                            // 触发 error 事件
                            processMcpStreamEvent("error", errorMessage, client);
                        }
                    };
                    
                    // 创建 EventSource
                    okhttp3.sse.EventSource.Factory factory = okhttp3.sse.EventSources.createFactory(httpClient);
                    okhttp3.sse.EventSource eventSource = factory.newEventSource(requestBuilder.build(), listener);
                    
                    // 保存 EventSource 到客户端对象
                    client.client = eventSource;
                } else if (config instanceof StreamableHTTPServer) {
                    // 实现流 HTTP 连接
                    String url = ((StreamableHTTPServer) config).url;
                    
                    // 对于流 HTTP，我们使用常规的 POST 请求进行初始化
                    Request.Builder requestBuilder = new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(new byte[0], MediaType.parse("application/json")))
                            .addHeader("Accept", "application/json, text/event-stream");
                    
                    // 添加自定义头
                    for (Map<String, String> header : config.commonOptions.headers) {
                        for (Map.Entry<String, String> entry : header.entrySet()) {
                            requestBuilder.addHeader(entry.getKey(), entry.getValue());
                        }
                    }
                    
                    // 发送初始请求
                    Response response = httpClient.newCall(requestBuilder.build()).execute();
                    
                    if (response.isSuccessful()) {
                        client.status = McpStatus.Connected();
                        System.out.println("MCP 流 HTTP 连接成功: " + config.commonOptions.name);
                        
                        // 连接成功后自动同步工具列表
                        int serverIndex = mcpServers.indexOf(config);
                        if (serverIndex != -1) {
                            syncMCPTools(serverIndex);
                        }
                        
                        // 检查响应类型
                        String contentType = response.header("Content-Type");
                        if (contentType != null && contentType.contains("text/event-stream")) {
                            // 如果是 SSE 响应，处理流数据
                            handleStreamResponse(response, client);
                        } else {
                            // 否则，关闭响应
                            response.close();
                        }
                    } else {
                        client.status = McpStatus.Error("连接失败: " + response.code() + " " + response.message());
                        response.close();
                    }
                }
            } catch (Exception e) {
                client.status = McpStatus.Error(e.getMessage() != null ? e.getMessage() : "连接失败");
                System.err.println("MCP 连接失败: " + config.commonOptions.name + ", 错误: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 处理流响应
     * @param response HTTP 响应
     * @param client MCP 客户端
     */
    private void handleStreamResponse(Response response, McpClient client) {
        executorService.submit(() -> {
            try {
                okio.BufferedSource source = response.body().source();
                StringBuilder dataBuilder = new StringBuilder();
                String lastEventId = null;
                String eventType = null;
                
                while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line == null) break;
                    
                    if (line.isEmpty()) {
                        // 空行表示事件结束，处理完整事件
                        if (dataBuilder.length() > 0) {
                            String data = dataBuilder.toString();
                            System.out.println("MCP 流 HTTP 事件: " + eventType + " - " + data);
                            
                            // 根据 MCP 协议处理事件
                            processMcpStreamEvent(eventType, data, client);
                            
                            dataBuilder.setLength(0);
                        }
                        eventType = null;
                    } else if (line.startsWith("id: ")) {
                        lastEventId = line.substring(4).trim();
                    } else if (line.startsWith("event: ")) {
                        eventType = line.substring(7).trim();
                    } else if (line.startsWith("data: ")) {
                        dataBuilder.append(line.substring(6));
                    }
                }
                
                // 处理最后一个事件
                if (dataBuilder.length() > 0) {
                    String data = dataBuilder.toString();
                    System.out.println("MCP 流 HTTP 事件: " + eventType + " - " + data);
                    processMcpStreamEvent(eventType, data, client);
                }
                
                response.close();
                client.status = McpStatus.Idle();
            } catch (Exception e) {
                client.status = McpStatus.Error(e.getMessage() != null ? e.getMessage() : "流处理失败");
                System.err.println("MCP 流处理失败: " + client.config.commonOptions.name + ", 错误: " + e.getMessage());
                e.printStackTrace();
                response.close();
            }
        });
    }
    
    /**
     * 处理 MCP 流事件
     * @param eventType 事件类型
     * @param data 事件数据
     * @param client MCP 客户端
     */
    private void processMcpStreamEvent(String eventType, String data, McpClient client) {
        try {
            // 根据事件类型处理
            if (eventType == null || eventType.isEmpty() || "message".equals(eventType)) {
                // 处理消息事件，可能包含工具调用或响应
                JsonObject eventData = gson.fromJson(data, JsonObject.class);
                
                // 根据 MCP 协议处理不同类型的消息
                if (eventData.has("type")) {
                    String messageType = eventData.get("type").getAsString();
                    switch (messageType) {
                        case "tool_call":
                            // 处理工具调用请求
                            handleToolCallRequest(eventData, client);
                            break;
                        case "tool_response":
                            // 处理工具响应
                            handleToolResponse(eventData, client);
                            break;
                        case "status":
                            // 处理状态更新
                            updateClientStatus(eventData, client);
                            break;
                        default:
                            // 未知消息类型
                            System.out.println("未知的 MCP 消息类型: " + messageType);
                            break;
                    }
                }
            } else if ("open".equals(eventType)) {
                // 连接打开事件
                client.status = McpStatus.Connected();
                System.out.println("MCP 流 HTTP 连接已打开: " + client.config.commonOptions.name);
            } else if ("close".equals(eventType)) {
                // 连接关闭事件
                client.status = McpStatus.Idle();
                System.out.println("MCP 流 HTTP 连接已关闭: " + client.config.commonOptions.name);
            } else if ("error".equals(eventType)) {
                // 错误事件
                client.status = McpStatus.Error(data);
                System.err.println("MCP 流 HTTP 错误: " + client.config.commonOptions.name + " - " + data);
            }
        } catch (Exception e) {
            System.err.println("处理 MCP 流事件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理工具调用请求
     * @param eventData 事件数据
     * @param client MCP 客户端
     */
    private void handleToolCallRequest(JsonObject eventData, McpClient client) {
        // 这里实现工具调用请求的处理逻辑
        // 根据 MCP 协议，可能需要调用本地工具或转发到其他服务
        System.out.println("收到工具调用请求: " + eventData);
    }
    
    /**
     * 处理工具响应
     * @param eventData 事件数据
     * @param client MCP 客户端
     */
    private void handleToolResponse(JsonObject eventData, McpClient client) {
        // 这里实现工具响应的处理逻辑
        System.out.println("收到工具响应: " + eventData);
    }
    
    /**
     * 更新客户端状态
     * @param eventData 事件数据
     * @param client MCP 客户端
     */
    private void updateClientStatus(JsonObject eventData, McpClient client) {
        // 这里实现客户端状态更新的逻辑
        if (eventData.has("status")) {
            String status = eventData.get("status").getAsString();
            switch (status) {
                case "connected":
                    client.status = McpStatus.Connected();
                    break;
                case "disconnected":
                    client.status = McpStatus.Idle();
                    break;
                case "error":
                    String errorMessage = eventData.has("message") ? eventData.get("message").getAsString() : "未知错误";
                    client.status = McpStatus.Error(errorMessage);
                    break;
                default:
                    System.out.println("未知的客户端状态: " + status);
                    break;
            }
        }
    }
    
    /**
     * 关闭 MCP 客户端连接
     * @param serverId 服务器 ID
     */
    private void closeMcpClient(UUID serverId) {
        McpClient client = mcpClients.remove(serverId);
        if (client != null) {
            // 实现实际的 MCP 客户端关闭逻辑
            if (client.client != null) {
                if (client.client instanceof okhttp3.sse.EventSource) {
                    // 关闭 SSE EventSource
                    ((okhttp3.sse.EventSource) client.client).cancel();
                    System.out.println("MCP 客户端已关闭: " + client.config.commonOptions.name);
                } else if (client.client instanceof io.modelcontextprotocol.kotlin.sdk.client.Client) {
                    // 关闭官方 SDK Client
                    try {
                        McpSdkWrapper.INSTANCE.close((io.modelcontextprotocol.kotlin.sdk.client.Client) client.client).join();
                        System.out.println("MCP SDK 客户端已关闭: " + client.config.commonOptions.name);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            client.status = McpStatus.Idle();
        }
    }
    
    /**
     * 关闭所有 MCP 客户端连接
     */
    private void closeAllMcpClients() {
        for (UUID serverId : new ArrayList<>(mcpClients.keySet())) {
            closeMcpClient(serverId);
        }
    }
    
    /**
     * 异步连接到指定的 HTTP URL，获取原始响应
     * @param url 要连接的 URL
     * @param callback 连接成功或失败时调用的回调函数
     */
    public void fetchUrlAsync(String url, LuaObject callback) {
        // 使用线程池执行异步 HTTP 请求
        executorService.submit(() -> {
            try {
                // 创建 HTTP 请求
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Accept", "application/json")
                        .build();
                
                // 执行同步 HTTP 请求（在后台线程中）
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        // 连接成功，准备回调参数
                        Map<String, Object> result = new HashMap<>();
                        result.put("success", true);
                        result.put("url", url);
                        result.put("statusCode", response.code());
                        result.put("statusMessage", response.message());
                        
                        // 读取响应体
                        String responseBody = response.body() != null ? response.body().string() : "";
                        result.put("responseBody", responseBody);
                        
                        // 调用成功回调
                        invokeCallback(callback, result, null);
                    } else {
                        // 连接失败，调用错误回调
                        String errorMsg = String.format("HTTP 连接失败: %d %s", response.code(), response.message());
                        invokeCallback(callback, null, errorMsg);
                    }
                }
            } catch (Exception e) {
                // 处理异常，调用错误回调
                String errorMsg = String.format("HTTP 连接异常: %s", e.getMessage());
                e.printStackTrace();
                invokeCallback(callback, null, errorMsg);
            }
        });
    }
    
    /**
     * 异步连接到 MCP 服务器并获取工具列表
     * @param url 服务器 URL
     * @param callback 回调函数，返回工具列表或错误信息
     */
    public void connectMcpServerAsync(String url, LuaObject callback) {
        // 使用线程池执行异步 MCP 连接
        executorService.submit(() -> {
            try {
                // 创建新的 MCP 服务器配置
                StreamableHTTPServer serverConfig = new StreamableHTTPServer(
                    UUID.randomUUID(), 
                    new McpCommonOptions(), 
                    url
                );
                serverConfig.commonOptions.name = "动态连接的 MCP 服务器";
                serverConfig.commonOptions.enable = true;
                
                // 将新创建的服务器配置添加到mcpServers列表中
                mcpServers.add(serverConfig);
                
                // 获取服务器 ID
                UUID serverId = serverConfig.id;
                
                // 直接调用同步方法获取工具列表，而不是依赖异步的addMcpClient
                // 这样可以确保工具同步完成后再返回结果
                addMcpClientSync(serverConfig);
                
                // 获取工具列表
                McpClient clientWrapper = mcpClients.get(serverId);
                List<Map<String, Object>> toolList = new ArrayList<>();
                int toolCount = 0;
                
                if (clientWrapper != null) {
                    System.out.println("MCP 客户端状态: " + clientWrapper.status.status + "，服务器: " + clientWrapper.config.commonOptions.name);
                    
                    // 从服务器配置中获取工具列表
                    for (McpTool tool : clientWrapper.config.commonOptions.tools) {
                        Map<String, Object> toolInfo = new HashMap<>();
                        toolInfo.put("name", tool.name);
                        toolInfo.put("description", tool.description);
                        toolInfo.put("inputSchema", tool.inputSchema);
                        toolInfo.put("enable", tool.enable);
                        toolList.add(toolInfo);
                    }
                    toolCount = toolList.size();
                    
                    System.out.println("MCP 服务器连接成功，工具列表已同步到系统，工具数量: " + toolCount);
                } else {
                    System.out.println("MCP 服务器连接成功，但未找到客户端实例");
                }
                
                // 准备回调结果
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("url", url);
                result.put("tools", toolList);
                result.put("toolCount", toolCount);
                result.put("serverId", serverId.toString());
                
                // 调用成功回调
                invokeCallback(callback, result, null);
            } catch (Exception e) {
                // 处理异常，调用错误回调
                String errorMsg = String.format("MCP 连接失败: %s", e.getMessage());
                e.printStackTrace();
                invokeCallback(callback, null, errorMsg);
            }
        });
    }
    
    /**
     * 同步添加 MCP 客户端，等待连接和工具同步完成
     * @param config MCP 服务器配置
     */
    private void addMcpClientSync(McpServerConfig config) throws Exception {
        System.out.println("开始连接 MCP 服务器: " + config.commonOptions.name + "，URL: " + 
            (config instanceof SseTransportServer ? ((SseTransportServer) config).url : ((StreamableHTTPServer) config).url));
        
        // 移除旧客户端（如果存在）
        removeMcpClient(config);
        
        // 创建新客户端
        McpClient clientWrapper = new McpClient(config);
        mcpClients.put(config.id, clientWrapper);
        
        // 更新状态为连接中
        clientWrapper.status = McpStatus.Connecting();
        System.out.println("MCP 客户端已创建，服务器: " + config.commonOptions.name + "，状态: 连接中");
        
        // 使用官方 SDK 创建客户端
        io.modelcontextprotocol.kotlin.sdk.client.Client client = McpSdkWrapper.INSTANCE.createClient(
            config.commonOptions.name,
            "1.0.0"
        );
        clientWrapper.client = client;
        
        // 创建传输层实现
        io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport transport;
        String transportType;
        if (config instanceof SseTransportServer) {
            // 创建 SSE 传输
            transport = com.difierline.lua.transport.TransportHelper.createSseTransport(
                ((SseTransportServer) config).url
            );
            transportType = "SSE";
        } else if (config instanceof StreamableHTTPServer) {
            // 创建 Streamable HTTP 传输
            transport = com.difierline.lua.transport.TransportHelper.createStreamableHttpTransport(
                ((StreamableHTTPServer) config).url
            );
            transportType = "Streamable HTTP";
        } else {
            throw new IllegalArgumentException("未知的传输类型");
        }
        
        System.out.println("MCP 传输层已创建，类型: " + transportType);
        
        // 连接到服务器
        McpSdkWrapper.INSTANCE.connect(client, transport).join();
        
        System.out.println("MCP 客户端连接成功，开始同步工具列表");
        
        // 同步工具列表
        syncMcpTools(config);
        
        // 更新状态为已连接
        clientWrapper.status = McpStatus.Connected();
        System.out.println("MCP 客户端已连接，服务器: " + config.commonOptions.name + "，状态: 已连接");
        System.out.println("当前已注册工具数量: " + toolRegistry.size());
    }
    


    /**
     * 设置沙箱模式
     * @param enabled 是否启用沙箱模式
     * @param providers 允许访问的 AI 提供商列表
     */
    public void setSandbox(boolean enabled, String... providers) {
        this.sandboxMode = enabled;
        this.allowedProviders.clear();
        for (String provider : providers) {
            allowedProviders.add(provider);
        }
    }

    /**
     * 列出可用的 AI 提供商
     * @return 提供商列表
     */
    public List<Map<String, String>> listProviders() {
        if (sandboxMode) {
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, String> provider : AI_PROVIDERS) {
                if (allowedProviders.contains(provider.get("id"))) {
                    result.add(provider);
                }
            }
            return result;
        }
        return AI_PROVIDERS;
    }

    /**
     * 列出可用的 AI 模型
     * @return 模型列表
     */
    public List<Map<String, String>> listModels() {
        if (sandboxMode) {
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, String> model : AI_MODELS) {
                if (allowedProviders.contains(model.get("provider"))) {
                    result.add(model);
                }
            }
            return result;
        }
        return AI_MODELS;
    }

    /**
     * 列出特定提供商的 AI 模型
     * @param providerId 提供商 ID
     * @return 模型列表
     */
    public List<Map<String, String>> listModelsByProvider(String providerId) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> model : AI_MODELS) {
            if (model.get("provider").equals(providerId)) {
                result.add(model);
            }
        }
        return result;
    }

    /**
     * 设置当前使用的 AI 模型
     * @param modelId 模型 ID
     */
    public void setModel(String modelId) {
        boolean modelExists = false;
        for (Map<String, String> model : AI_MODELS) {
            if (model.get("id").equals(modelId)) {
                modelExists = true;
                break;
            }
        }
        if (!modelExists) {
            throw new IllegalArgumentException("无效的模型 ID: " + modelId);
        }
        this.currentModelId = modelId;
    }

    /**
     * 获取当前使用的 AI 模型
     * @return 模型 ID
     */
    public String getModel() {
        return currentModelId;
    }

    /**
     * 设置 AI 提供商
     * @param providerId 提供商 ID
     */
    public void setProvider(String providerId) {
        boolean providerExists = false;
        for (Map<String, String> provider : AI_PROVIDERS) {
            if (provider.get("id").equals(providerId)) {
                providerExists = true;
                break;
            }
        }
        if (!providerExists) {
            throw new IllegalArgumentException("无效的提供商 ID: " + providerId);
        }
        this.currentProvider = providerId;
    }

    /**
     * 获取当前 AI 提供商
     * @return 提供商 ID
     */
    public String getProvider() {
        return currentProvider;
    }

    /**
     * 设置 API 密钥
     * @param apiKey API 密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 获取 API 密钥
     * @return API 密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置 API 基础 URL
     * @param baseUrl API 基础 URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取 API 基础 URL
     * @return API 基础 URL
     */
    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        // 根据提供商返回默认 URL
        for (Map<String, String> provider : AI_PROVIDERS) {
            if (provider.get("id").equals(currentProvider)) {
                return provider.get("baseUrl");
            }
        }
        return "https://api.siliconflow.cn/v1"; // 默认 URL
    }

    /**
     * 生成文本
     * @param prompt 提示词
     * @return 生成的文本
     */
    public String generate(String prompt) {
        return generate(prompt, new HashMap<>());
    }
    
    /**
     * 生成文本，支持多轮对话
     * @param prompt 提示词
     * @param params 生成参数
     * @return 生成的文本
     */
    public String generate(String prompt, Map<String, Object> params) {
        return generate(prompt, params, null);
    }
    
    /**
     * 生成文本，支持多轮对话
     * @param prompt 提示词
     * @param params 生成参数
     * @param conversationId 对话ID，null表示使用当前对话
     * @return 生成的文本
     */
    public String generate(String prompt, Map<String, Object> params, String conversationId) {
        if (prompt == null || prompt.isEmpty()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        
        // 将用户消息添加到指定对话或当前对话
        String finalConversationId = sendMessage(prompt, conversationId);
        
        // 处理参数
        Map<String, Object> validatedParams = validateParams(params);
        
        // 获取目标对话
        Conversation conversation = getConversation(finalConversationId);
        
        // 使用Future来执行网络请求，确保在后台线程中执行
        try {
            // 提交网络请求任务到类级别的线程池
            java.util.concurrent.Future<String> future = executorService.submit(() -> {
                try {
                    // 保存当前对话ID，以便在生成完成后恢复
                    String originalConversationId = currentConversationId.get();
                    try {
                        // 切换到目标对话
                        if (finalConversationId != null) {
                            switchConversation(finalConversationId);
                        }
                        
                        // 标记对话为正在处理中
                        conversation.setProcessing(true);
                        
                        try {
                            // 根据提供商调用不同的API
                            if ("openai".equals(currentProvider) || "siliconflow".equals(currentProvider)) {
                                return generateOpenAICompatibleWithHistory(prompt, validatedParams);
                            } else {
                                throw new UnsupportedOperationException("不支持的AI提供商: " + currentProvider);
                            }
                        } finally {
                            // 标记对话为处理完成
                            conversation.setProcessing(false);
                        }
                    } finally {
                        // 恢复原始对话ID
                        if (originalConversationId != null) {
                            switchConversation(originalConversationId);
                        }
                    }
                } catch (IOException e) {
                    // 确保无论发生什么错误，都要标记对话为处理完成
                    conversation.setProcessing(false);
                    throw new RuntimeException("API调用失败: " + e.getMessage(), e);
                }
            });
            
            // 等待任务完成并获取结果
            String result = future.get();
            
            return result;
        } catch (java.util.concurrent.ExecutionException e) {
            // 确保无论发生什么错误，都要标记对话为处理完成
            conversation.setProcessing(false);
            
            // 提取原始异常并重新抛出
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new RuntimeException("生成文本失败: " + e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            // 确保无论发生什么错误，都要标记对话为处理完成
            conversation.setProcessing(false);
            
            // 处理线程中断异常
            Thread.currentThread().interrupt();
            throw new RuntimeException("生成过程被中断", e);
        }
    }
    
    /**
     * 生成文本，使用对话历史作为上下文
     * @param prompt 提示词
     * @param params 生成参数
     * @return 生成的文本
     */
    private String generateOpenAICompatibleWithHistory(String prompt, Map<String, Object> params) throws IOException {
        // 获取当前对话
        Conversation conversation = getCurrentConversation();
        
        // 构建请求体
        JsonObject requestBody = new JsonObject();
        
        // 设置模型
        requestBody.addProperty("model", currentModelId);
        
        // 构建消息列表，包含对话历史
        JsonArray messages = new JsonArray();
        
        // 添加系统消息（如果有的话）
        String systemPrompt = params.containsKey("systemPrompt") ? (String) params.get("systemPrompt") : "";
        
        // 添加MCP服务器连接信息到系统提示
        StringBuilder serverInfo = new StringBuilder();
        if (!mcpServers.isEmpty()) {
            serverInfo.append("\n\n当前可用的MCP服务器连接：");
            for (McpServerConfig server : mcpServers) {
                if (server.commonOptions.enable) {
                    serverInfo.append(String.format("\n- 名称：%s，类型：%s，URL：%s，状态：%s", 
                        server.commonOptions.name, 
                        server instanceof SseTransportServer ? "sse" : "streamable_http",
                        server instanceof SseTransportServer ? ((SseTransportServer) server).url : ((StreamableHTTPServer) server).url,
                        mcpClients.containsKey(server.id) ? "已连接" : "未连接"));
                }
            }
        }
        
        // 合并系统提示和服务器信息
        String finalSystemPrompt = systemPrompt + serverInfo.toString();
        
        if (!finalSystemPrompt.isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", finalSystemPrompt);
            messages.add(systemMessage);
        }
        
        // 添加对话历史
        List<Message> recentMessages = conversation.getMessages(); // 使用完整的对话历史
        for (Message message : recentMessages) {
            if (message.isToolCall()) {
                // 处理工具调用消息
                JsonObject toolCallMessage = new JsonObject();
                toolCallMessage.addProperty("role", "assistant");
                
                // 创建工具调用数组
                JsonArray toolCalls = new JsonArray();
                JsonObject toolCall = new JsonObject();
                toolCall.addProperty("id", message.getToolCallId());
                toolCall.addProperty("type", "function");
                
                JsonObject function = new JsonObject();
                function.addProperty("name", "mcp__" + message.getToolName());
                function.addProperty("arguments", gson.toJson(message.getToolArguments()));
                
                toolCall.add("function", function);
                toolCalls.add(toolCall);
                
                toolCallMessage.add("tool_calls", toolCalls);
                messages.add(toolCallMessage);
            } else if (message.isToolResponse()) {
                // 处理工具响应消息
                JsonObject toolResponseMessage = new JsonObject();
                toolResponseMessage.addProperty("role", "tool");
                toolResponseMessage.addProperty("tool_call_id", message.getToolCallId());
                toolResponseMessage.addProperty("name", "mcp__" + message.getToolResult().get("toolName"));
                toolResponseMessage.addProperty("content", gson.toJson(message.getToolResult()));
                messages.add(toolResponseMessage);
            } else {
                // 普通消息
                JsonObject aiMessage = new JsonObject();
                aiMessage.addProperty("role", message.getRole().name().toLowerCase());
                aiMessage.addProperty("content", message.getContent());
                messages.add(aiMessage);
            }
        }
        
        requestBody.add("messages", messages);
        
        // 设置生成参数
        if (params.containsKey("temperature")) {
            requestBody.addProperty("temperature", (double) params.get("temperature"));
        }
        if (params.containsKey("max_tokens")) {
            requestBody.addProperty("max_tokens", (int) params.get("max_tokens"));
        }
        if (params.containsKey("top_p")) {
            requestBody.addProperty("top_p", (double) params.get("top_p"));
        }
        if (params.containsKey("frequency_penalty")) {
            requestBody.addProperty("frequency_penalty", (double) params.get("frequency_penalty"));
        }
        if (params.containsKey("presence_penalty")) {
            requestBody.addProperty("presence_penalty", (double) params.get("presence_penalty"));
        }
        
        // 如果启用了MCP，添加工具列表
        if (mcpEnabled) {
            JsonArray tools = new JsonArray();
            List<Map<String, Object>> allTools = getAvailableTools();
            for (Map<String, Object> tool : allTools) {
                JsonObject toolJson = new JsonObject();
                toolJson.addProperty("type", "function");
                
                JsonObject function = new JsonObject();
                function.addProperty("name", "mcp__" + tool.get("name"));
                
                // 在工具描述中添加工具类型和连接类型信息，确保AI能够区分内置工具、Lua工具和远程工具，以及工具的连接方式
                String description = (String) tool.get("description");
                String toolType = (String) tool.get("type");
                String typeName = "远程工具";
                if ("builtin".equals(toolType)) {
                    typeName = "内置工具";
                } else if ("lua".equals(toolType)) {
                    typeName = "Lua工具";
                }
                
                // 添加连接类型信息（仅远程工具）
                String connectionInfo = "";
                if ("remote".equals(toolType)) {
                    // 添加连接类型描述
                    String connectionTypeDesc = (String) tool.getOrDefault("connectionTypeDesc", "");
                    // 添加连接类型名称（如streamable_http），确保AI能直接识别
                    String connectionTypeName = (String) tool.getOrDefault("connectionTypeName", "");
                    
                    if (!connectionTypeDesc.isEmpty()) {
                        connectionInfo = "，连接方式：" + connectionTypeDesc;
                    }
                    
                    // 明确添加连接类型名称，确保AI能识别streamable_http类型
                    if (!connectionTypeName.isEmpty()) {
                        connectionInfo += "（连接类型：" + connectionTypeName + "）";
                    }
                }
                
                String finalDescription = description + "（工具类型：" + typeName + connectionInfo + "）";
                function.addProperty("description", finalDescription);
                
                // 添加工具参数schema
                JsonObject parameters = new JsonObject();
                parameters.addProperty("type", "object");
                parameters.add("properties", gson.toJsonTree(tool.get("inputSchema")));
                parameters.add("required", new JsonArray());
                
                function.add("parameters", parameters);
                toolJson.add("function", function);
                tools.add(toolJson);
            }
            requestBody.add("tools", tools);
            requestBody.addProperty("tool_choice", "auto");
        }
        
        // 构建请求
        String url = getBaseUrl() + "/chat/completions";
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(requestBody.toString(), mediaType);
        
        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json");
        
        // 获取API密钥
        String selectedApiKey = apiKey;
        if (selectedApiKey == null || selectedApiKey.isEmpty()) {
            // 如果没有设置API密钥，使用默认的SiliconFlow密钥
            selectedApiKey = DEFAULT_SILICONFLOW_KEYS;
        }
        
        // 使用KeyRoulette选择一个API密钥
        String apiKeyToUse = keyRoulette.next(selectedApiKey);
        
        // 添加API密钥头
        requestBuilder.addHeader("Authorization", "Bearer " + apiKeyToUse);
        
        // 发送请求
        Request request = requestBuilder.build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API调用失败: " + response.code() + " " + response.body().string());
            }
            
            // 解析响应
            String responseBody = response.body().string();
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            
            // 处理AI响应，包括工具调用
            Map<String, Object> processResult = processAIResponse(responseJson, conversation);
            
            // 提取最终生成的文本
            if ((boolean) processResult.get("success")) {
                List<Message> messagesAdded = (List<Message>) processResult.get("messages");
                for (Message message : messagesAdded) {
                    if (message.getRole() == MessageRole.ASSISTANT && !message.isToolCall()) {
                        return message.getContent();
                    }
                }
            }
            
            // 如果没有直接的助手消息，返回处理结果的字符串表示
            return gson.toJson(processResult);
        }
    }
    
    /**
     * 获取可用的MCP工具列表
     * @return 工具列表，包含内置工具、Lua工具和MCP服务器工具
     */
    public List<Map<String, Object>> getAvailableTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 添加内置工具和Lua工具，跳过被禁用的工具
        for (Map.Entry<String, McpToolFunction> entry : toolRegistry.entrySet()) {
            String toolName = entry.getKey();
            
            // 跳过被禁用的工具
            if (disabledBuiltinTools.contains(toolName)) {
                continue;
            }
            
            Map<String, Object> inputSchema = toolSchemaRegistry.get(toolName);
            boolean isBuiltin = builtinTools.contains(toolName);
            
            Map<String, Object> toolInfo = new HashMap<>();
            toolInfo.put("name", toolName);
            toolInfo.put("description", toolDescriptions.getOrDefault(toolName, (isBuiltin ? "内置工具: " : "Lua工具: ") + toolName));
            toolInfo.put("inputSchema", inputSchema);
            toolInfo.put("type", isBuiltin ? "builtin" : "lua");
            toolInfo.put("enabled", true);
            tools.add(toolInfo);
        }
        
        // 添加MCP服务器工具，包含完整的连接信息
        for (McpServerConfig serverConfig : mcpServers) {
            if (serverConfig.commonOptions.enable) {
                for (McpTool tool : serverConfig.commonOptions.tools) {
                    if (tool.enable) {
                        Map<String, Object> toolInfo = new HashMap<>();
                        toolInfo.put("name", tool.name);
                        toolInfo.put("description", tool.description);
                        toolInfo.put("inputSchema", tool.inputSchema);
                        toolInfo.put("type", "remote");
                        toolInfo.put("serverName", serverConfig.commonOptions.name);
                        toolInfo.put("serverId", serverConfig.id.toString());
                        
                        // 添加连接类型信息，让AI知道工具是通过SSE还是流HTTP连接的
                        String connectionType = serverConfig instanceof SseTransportServer ? "sse" : "streamable_http";
                        toolInfo.put("connectionType", connectionType);
                        
                        // 添加连接类型描述，让AI更容易理解
                        String connectionTypeDesc = serverConfig instanceof SseTransportServer ? "SSE连接" : "流HTTP连接";
                        toolInfo.put("connectionTypeDesc", connectionTypeDesc);
                        
                        // 保存原始连接类型名称，确保AI能够直接识别streamable_http类型
                        toolInfo.put("connectionTypeName", connectionType);
                        
                        toolInfo.put("enabled", tool.enable);
                        
                        // 检查服务器是否已连接
                        McpClient client = mcpClients.get(serverConfig.id);
                        if (client != null) {
                            toolInfo.put("serverStatus", client.status.status);
                        } else {
                            toolInfo.put("serverStatus", "IDLE");
                        }
                        
                        tools.add(toolInfo);
                    }
                }
            }
        }
        
        return tools;
    }
    
    /**
     * 禁用指定的MCP工具（包括内置工具和从Lua注册的工具）
     * @param toolName 工具名称
     */
    public void disableMCPTool(String toolName) {
        if (toolRegistry.containsKey(toolName)) {
            disabledBuiltinTools.add(toolName);
        }
    }
    
    /**
     * 启用指定的MCP工具（包括内置工具和从Lua注册的工具）
     * @param toolName 工具名称
     */
    public void enableMCPTool(String toolName) {
        disabledBuiltinTools.remove(toolName);
    }
    
    /**
     * 禁用所有MCP工具（包括内置工具和从Lua注册的工具）
     */
    public void disableAllMCPTools() {
        disabledBuiltinTools.addAll(toolRegistry.keySet());
    }
    
    /**
     * 启用所有MCP工具（包括内置工具和从Lua注册的工具）
     */
    public void enableAllMCPTools() {
        disabledBuiltinTools.clear();
    }
    
    /**
     * 禁用指定的内置MCP工具
     * @param toolName 工具名称
     */
    public void disableBuiltinTool(String toolName) {
        disableMCPTool(toolName);
    }
    
    /**
     * 启用指定的内置MCP工具
     * @param toolName 工具名称
     */
    public void enableBuiltinTool(String toolName) {
        enableMCPTool(toolName);
    }
    
    /**
     * 禁用所有内置MCP工具
     */
    public void disableAllBuiltinTools() {
        disableAllMCPTools();
    }
    
    /**
     * 启用所有内置MCP工具
     */
    public void enableAllBuiltinTools() {
        enableAllMCPTools();
    }
    
    /**
     * 禁用所有文件操作类工具
     */
    public void disableFileOperationTools() {
        // 文件操作工具列表
        String[] fileTools = {"read_file", "write_file", "list_files", "search_files", "read_file_lines", 
                             "create_directory", "delete_file", "rename_file", "copy_file", "get_file_info"};
        for (String tool : fileTools) {
            if (toolRegistry.containsKey(tool)) {
                disabledBuiltinTools.add(tool);
            }
        }
    }
    
    /**
     * 启用所有文件操作类工具
     */
    public void enableFileOperationTools() {
        // 文件操作工具列表
        String[] fileTools = {"read_file", "write_file", "list_files", "search_files", "read_file_lines", 
                             "create_directory", "delete_file", "rename_file", "copy_file", "get_file_info"};
        for (String tool : fileTools) {
            disabledBuiltinTools.remove(tool);
        }
    }
    
    /**
     * 禁用命令行执行工具
     */
    public void disableCommandExecutionTools() {
        if (toolRegistry.containsKey("execute_command")) {
            disabledBuiltinTools.add("execute_command");
        }
    }
    
    /**
     * 启用命令行执行工具
     */
    public void enableCommandExecutionTools() {
        disabledBuiltinTools.remove("execute_command");
    }
    
    /**
     * 检查内置工具是否被禁用
     * @param toolName 工具名称
     * @return 是否被禁用
     */
    public boolean isBuiltinToolDisabled(String toolName) {
        return disabledBuiltinTools.contains(toolName);
    }
    
    /**
     * 调用兼容OpenAI API的提供商（如OpenAI、硅基流动）
     * @param prompt 提示词
     * @param params 生成参数
     * @return 生成的文本
     * @throws IOException
     */
    private String generateOpenAICompatible(String prompt, Map<String, Object> params) throws IOException {
        // 构建请求体
        JsonObject requestBody = new JsonObject();
        
        // 设置模型
        requestBody.addProperty("model", currentModelId);
        
        // 设置消息
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);
        
        // 设置生成参数
        if (params.containsKey("temperature")) {
            requestBody.addProperty("temperature", (double) params.get("temperature"));
        }
        if (params.containsKey("max_tokens")) {
            requestBody.addProperty("max_tokens", (int) params.get("max_tokens"));
        }
        if (params.containsKey("top_p")) {
            requestBody.addProperty("top_p", (double) params.get("top_p"));
        }
        if (params.containsKey("frequency_penalty")) {
            requestBody.addProperty("frequency_penalty", (double) params.get("frequency_penalty"));
        }
        if (params.containsKey("presence_penalty")) {
            requestBody.addProperty("presence_penalty", (double) params.get("presence_penalty"));
        }
        
        // 构建请求
        String url = getBaseUrl() + "/chat/completions";
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(requestBody.toString(), mediaType);
        
        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json");
        
        // 获取API密钥
        String selectedApiKey = apiKey;
        if (selectedApiKey == null || selectedApiKey.isEmpty()) {
            // 如果没有设置API密钥，使用默认的SiliconFlow密钥
            selectedApiKey = DEFAULT_SILICONFLOW_KEYS;
        }
        
        // 使用KeyRoulette选择一个API密钥
        String apiKeyToUse = keyRoulette.next(selectedApiKey);
        
        // 添加API密钥头
        requestBuilder.addHeader("Authorization", "Bearer " + apiKeyToUse);
        
        // 发送请求
        Request request = requestBuilder.build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API调用失败: " + response.code() + " " + response.body().string());
            }
            
            // 解析响应
            String responseBody = response.body().string();
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            
            // 提取生成的文本
            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject messageObj = choice.getAsJsonObject("message");
                return messageObj.get("content").getAsString();
            } else {
                throw new RuntimeException("API返回为空");
            }
        }
    }

    /**
     * 异步生成文本（使用回调函数通知结果）
     * @param prompt 提示词
     * @param params 生成参数
     * @param callback 回调函数，用于通知生成结果
     */
    public void generateAsync(String prompt, Map<String, Object> params, LuaObject callback) {
        generateAsync(prompt, params, null, callback);
    }
    
    /**
     * 异步生成文本（使用回调函数通知结果）
     * @param prompt 提示词
     * @param params 生成参数
     * @param conversationId 对话ID，null表示使用当前对话
     * @param callback 回调函数，用于通知生成结果
     */
    public void generateAsync(String prompt, Map<String, Object> params, String conversationId, LuaObject callback) {
        if (prompt == null || prompt.isEmpty()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        
        // 处理参数
        Map<String, Object> validatedParams = validateParams(params);
        
        // 获取最终的对话ID
        String finalConversationId = sendMessage(prompt, conversationId);
        
        // 创建异步请求
        AsyncRequest request = new AsyncRequest(prompt, validatedParams, finalConversationId, callback);
        
        // 获取或创建对话的请求队列
        asyncRequestQueue.computeIfAbsent(finalConversationId, k -> new java.util.LinkedList<>());
        
        // 将请求加入队列
        java.util.Queue<AsyncRequest> queue = asyncRequestQueue.get(finalConversationId);
        queue.offer(request);
        
        // 如果这是队列中的第一个请求，立即处理
        synchronized (queue) {
            if (queue.size() == 1) {
                processNextAsyncRequest(finalConversationId, queue);
            }
        }
    }
    
    /**
     * 处理队列中的下一个异步请求
     * @param conversationId 对话ID
     * @param queue 请求队列
     */
    private void processNextAsyncRequest(String conversationId, java.util.Queue<AsyncRequest> queue) {
        // 从队列中取出下一个请求
        AsyncRequest request = queue.poll();
        if (request == null) {
            // 队列为空，清理队列
            asyncRequestQueue.remove(conversationId);
            return;
        }
        
        // 使用类级别的线程池执行 AI 生成
        executorService.submit(() -> {
            try {
                // 获取目标对话
                Conversation conversation = getConversation(conversationId);
                
                // 保存当前对话ID，以便在生成完成后恢复
                String originalConversationId = null;
                synchronized (AIEngine.this) {
                    originalConversationId = currentConversationId.get();
                    // 切换到目标对话
                    if (conversationId != null) {
                        switchConversation(conversationId);
                    }
                }
                
                try {
                    // 标记对话为正在处理中
                    conversation.setProcessing(true);
                    
                    try {
                        // 直接调用 generateOpenAICompatibleWithHistory 方法，确保异步处理工具调用
                        String resultJson = generateOpenAICompatibleWithHistory(request.prompt, request.params);
                        
                        // 解析 JSON 字符串为 Map 对象
                        Map<String, Object> resultMap = new HashMap<>();
                        try {
                            // 尝试将结果解析为 Map
                            resultMap = gson.fromJson(resultJson, new TypeToken<Map<String, Object>>(){}.getType());
                        } catch (Exception e) {
                            // 如果解析失败，说明返回的是直接的文本内容，而不是 JSON 对象
                            resultMap.put("content", resultJson);
                            resultMap.put("success", true);
                            resultMap.put("hasToolCall", false);
                        }
                        
                        // 添加对话ID到结果中
                        resultMap.put("conversationId", conversationId);
                        
                        // 生成成功，调用回调函数，直接传递 Map 对象
                        invokeCallback(request.callback, resultMap, null);
                    } finally {
                        // 标记对话为处理完成
                        conversation.setProcessing(false);
                    }
                } finally {
                    // 恢复原始对话ID
                    synchronized (AIEngine.this) {
                        if (originalConversationId != null) {
                            switchConversation(originalConversationId);
                        }
                    }
                }
            } catch (Exception e) {
                // 确保无论发生什么错误，都要标记对话为处理完成
                try {
                    if (conversationId != null) {
                        Conversation conversation = getConversation(conversationId);
                        conversation.setProcessing(false);
                    }
                } catch (Exception ex) {
                    // 忽略嵌套异常，专注于处理原始异常
                }
                
                // 生成失败，调用回调函数并传递错误信息
                invokeCallback(request.callback, null, e.getMessage());
            } finally {
                // 处理队列中的下一个请求
                synchronized (queue) {
                    if (!queue.isEmpty()) {
                        processNextAsyncRequest(conversationId, queue);
                    } else {
                        // 队列为空，清理队列
                        asyncRequestQueue.remove(conversationId);
                    }
                }
            }
        });
    }
    
    /**
     * 检查对话是否正在处理中
     * @param conversationId 对话ID，null表示使用当前对话
     * @return 如果对话正在处理中，返回true；否则返回false
     */
    public boolean isConversationProcessing(String conversationId) {
        Conversation conversation;
        if (conversationId != null && conversations.containsKey(conversationId)) {
            conversation = conversations.get(conversationId);
        } else {
            conversation = getCurrentConversation();
        }
        return conversation.isProcessing();
    }
    
    /**
     * 等待对话处理完成
     * @param conversationId 对话ID，null表示使用当前对话
     * @param timeoutMs 超时时间，单位为毫秒，0表示无限等待
     * @return 如果对话处理完成，返回true；如果超时，返回false
     */
    public boolean waitForConversationCompletion(String conversationId, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long elapsedTime = 0;
        
        while (true) {
            // 检查对话是否正在处理中
            if (!isConversationProcessing(conversationId)) {
                return true;
            }
            
            // 检查是否超时
            if (timeoutMs > 0) {
                elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime >= timeoutMs) {
                    return false;
                }
            }
            
            // 等待一段时间后再次检查
            try {
                Thread.sleep(100); // 每100毫秒检查一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
    
    /**
     * 异步调用 MCP 工具（使用回调函数通知结果）
     * @param toolName 工具名称
     * @param params 工具参数
     * @param callback 回调函数，用于通知调用结果
     */
    public void callMCPToolAsync(String toolName, Map<String, Object> params, LuaObject callback) {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        
        // 确保参数不为 null
        final Map<String, Object> finalParams = params != null ? params : new HashMap<>();
        
        // 使用类级别的线程池执行 MCP 工具调用
        executorService.submit(() -> {
            try {
                Map<String, Object> result = callMCPTool(toolName, finalParams);
                // 调用成功，调用回调函数
                invokeCallback(callback, result, null);
            } catch (Exception e) {
                // 调用失败，构造详细的错误信息
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "调用 MCP 工具失败: " + e.getMessage());
                errorResult.put("toolName", toolName);
                errorResult.put("errorType", e.getClass().getSimpleName());
                errorResult.put("params", finalParams);
                
                // 调用回调函数，传递详细错误信息
                invokeCallback(callback, errorResult, e.getMessage());
            }
        });
    }
    
    /**
     * 简化的异步工具调用方法，支持直接传入 Lua table 参数
     * @param toolName 工具名称
     * @param luaTableParams Lua table 参数
     * @param callback 回调函数
     */
    public void callMCPToolAsync(String toolName, LuaObject luaTableParams, LuaObject callback) {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        
        // 将 Lua table 转换为 Java Map
        Map<String, Object> javaParams = new HashMap<>();
        if (luaTableParams != null) {
            try {
                synchronized (luaContext) {
                    LuaState L = luaContext.getLuaState();
                    luaTableParams.push();
                    javaParams = (Map<String, Object>) convertLuaValueToJava(L, -1);
                    L.pop(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "参数转换失败: " + e.getMessage());
                errorResult.put("toolName", toolName);
                errorResult.put("errorType", "ParamConversionError");
                invokeCallback(callback, errorResult, e.getMessage());
                return;
            }
        }
        
        // 调用标准的异步工具调用方法
        callMCPToolAsync(toolName, javaParams, callback);
    }
    
    /**
     * 简化的工具调用方法，自动处理参数转换
     * @param toolName 工具名称
     * @param params 工具参数，可以是 Map 或 LuaObject
     * @return 工具调用结果
     */
    public Map<String, Object> callMCPTool(String toolName, Object params) {
        Map<String, Object> javaParams;
        
        if (params == null) {
            javaParams = new HashMap<>();
        } else if (params instanceof Map) {
            javaParams = (Map<String, Object>) params;
        } else if (params instanceof LuaObject) {
            // 将 LuaObject 转换为 Java Map
            try {
                synchronized (luaContext) {
                    LuaState L = luaContext.getLuaState();
                    ((LuaObject) params).push();
                    javaParams = (Map<String, Object>) convertLuaValueToJava(L, -1);
                    L.pop(1);
                }
            } catch (Exception e) {
                throw new RuntimeException("参数转换失败: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("参数类型不支持: " + params.getClass().getName());
        }
        
        return callMCPTool(toolName, javaParams);
    }
    
    /**
     * 异步同步 MCP 工具列表（使用回调函数通知结果）
     * @param serverIndex 服务器索引
     * @param callback 回调函数，用于通知同步结果
     */
    public void syncMCPToolsAsync(int serverIndex, LuaObject callback) {
        // 使用类级别的线程池执行 MCP 工具同步
        executorService.submit(() -> {
            try {
                List<Map<String, Object>> result = syncMCPTools(serverIndex);
                // 同步成功，调用回调函数
                invokeCallback(callback, result, null);
            } catch (Exception e) {
                // 同步失败，调用回调函数并传递错误信息
                invokeCallback(callback, null, e.getMessage());
            }
        });
    }
    
    /**
     * 异步同步所有 MCP 服务器的工具列表（使用回调函数通知结果）
     * @param callback 回调函数，用于通知同步结果
     */
    public void syncAllMCPToolsAsync(LuaObject callback) {
        // 在新线程中执行所有 MCP 工具同步
        new Thread(() -> {
            try {
                syncAllMCPTools();
                // 同步成功，调用回调函数
                invokeCallback(callback, "所有 MCP 工具同步完成", null);
            } catch (Exception e) {
                // 同步失败，调用回调函数并传递错误信息
                invokeCallback(callback, null, e.getMessage());
            }
        }).start();
    }
    
    /**
     * 调用 LUA 回调函数
     * @param callback 回调函数
     * @param result 生成结果，可以是 String、Map、List 等类型
     * @param error 错误信息
     */
    private void invokeCallback(LuaObject callback, Object result, String error) {
        try {
            // 检查回调函数是否有效
            if (callback == null) {
                System.err.println("回调函数为 null，无法调用");
                return;
            }
            
            // 使用同步块确保 LuaState 访问的线程安全
            synchronized (luaContext) {
                LuaState L = luaContext.getLuaState();
                
                // 压入回调函数
                callback.push();
                
                // 检查栈是否有值（防止 push() 失败）
                if (L.isNil(-1)) {
                    L.pop(1);
                    System.err.println("无法压入回调函数，函数对象无效");
                    return;
                }
                
                if (error != null) {
                    // 生成失败，第一个参数为错误信息，第二个参数为 null
                    L.pushString(error);
                    L.pushNil();
                } else {
                    // 生成成功，第一个参数为 null，第二个参数为结果
                    L.pushNil();
                    
                    // 根据结果类型，转换为 Lua 能够处理的类型
                    if (result == null) {
                        L.pushNil();
                    } else if (result instanceof String) {
                        L.pushString((String) result);
                    } else if (result instanceof Map || result instanceof List) {
                        // 将 Map 或 List 直接转换为 Lua table
                        pushJavaObjectToLua(L, result);
                    } else {
                        // 其他类型转换为字符串
                        L.pushString(result.toString());
                    }
                }
                
                // 调用回调函数，传入两个参数
                int callResult = L.pcall(2, 0, 0);
                if (callResult != 0) {
                    // 回调函数调用出错，获取错误信息
                    String errorMsg = L.isString(-1) ? L.toString(-1) : "未知错误";
                    L.pop(1);
                    System.err.println("调用回调函数失败: " + errorMsg);
                }
            }
        } catch (Exception e) {
            // 捕获所有异常，避免闪退
            e.printStackTrace();
            System.err.println("调用回调函数失败: " + e.getMessage());
        }
    }
    
    /**
     * 将 Java 对象转换为 Lua 值并压入栈中
     * @param L LuaState 对象
     * @param obj 要转换的 Java 对象
     */
    private void pushJavaObjectToLua(LuaState L, Object obj) {
        try {
            if (obj == null) {
                L.pushNil();
            } else if (obj instanceof Boolean) {
                L.pushBoolean((Boolean) obj);
            } else if (obj instanceof Number) {
                Number num = (Number) obj;
                if (num instanceof Integer || num instanceof Long) {
                    L.pushInteger(num.longValue());
                } else {
                    L.pushNumber(num.doubleValue());
                }
            } else if (obj instanceof String) {
                L.pushString((String) obj);
            } else if (obj instanceof Map) {
                // 特别处理 Map，确保转换为 Lua table
                Map<?, ?> map = (Map<?, ?>) obj;
                
                // 创建 Lua table
                L.newTable();
                
                // 保存当前栈顶位置（table 的索引）
                int tableIndex = L.getTop();
                
                // 遍历 Map，将键值对转换为 table 的键值对
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    // 转换键
                    Object keyObj = entry.getKey();
                    if (keyObj instanceof String) {
                        L.pushString((String) keyObj);
                    } else if (keyObj instanceof Number) {
                        Number keyNum = (Number) keyObj;
                        if (keyNum instanceof Integer || keyNum instanceof Long) {
                            L.pushInteger(keyNum.longValue());
                        } else {
                            L.pushNumber(keyNum.doubleValue());
                        }
                    } else {
                        L.pushString(String.valueOf(keyObj));
                    }
                    
                    // 转换值（递归调用）
                    pushJavaObjectToLua(L, entry.getValue());
                    
                    // 将键值对添加到 table 中
                    L.setTable(tableIndex);
                }
            } else if (obj instanceof List) {
                // 特别处理 List，确保转换为 Lua table（标准连续数组）
                List<?> list = (List<?>) obj;
                int listSize = list.size();
                
                // 创建 Lua table，预分配空间（nar: 数组部分大小, nrec: 记录部分大小）
                L.createTable(listSize, 0);
                
                // 保存当前栈顶位置（table 的索引）
                int tableIndex = L.getTop();
                
                // 遍历 List，将元素转换为 table 的元素
                for (int i = 0; i < listSize; i++) {
                    // 转换元素（递归调用）
                    pushJavaObjectToLua(L, list.get(i));
                    
                    // 使用 rawSetI 方法设置数组元素，确保生成标准连续数组
                    // Lua 数组索引从 1 开始
                    L.rawSetI(tableIndex, i + 1);
                }
                
                // 确保 table 是标准连续数组，设置长度字段
                L.pushString("n");
                L.pushInteger(listSize);
                L.setTable(tableIndex);
            } else {
                // 对于其他 Java 对象，尝试使用内置方法转换
                try {
                    L.pushObjectValue(obj);
                } catch (Exception e) {
                    // 如果内置方法失败，转换为字符串
                    L.pushString(obj.toString());
                }
            }
        } catch (Exception e) {
            // 捕获所有异常，避免闪退
            System.err.println("转换 Java 对象到 Lua 值失败: " + e.getMessage());
            e.printStackTrace();
            L.pushString("转换失败: " + e.getMessage());
        }
    }

    /**
     * 流式生成文本
     * @param prompt 提示词
     * @param params 生成参数
     * @param callback 回调函数
     */
    public void generateStream(String prompt, Map<String, Object> params, LuaObject callback) {
        // 流式生成实现，通过回调函数返回结果
        try {
            LuaState L = luaContext.getLuaState();
            callback.push();
            L.pushString("流式生成结果：" + prompt);
            L.call(1, 0);
        } catch (Exception e) {
            throw new RuntimeException("流式生成失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检查生成参数的有效性
     * @param params 生成参数
     * @return 处理后的参数
     */
    private Map<String, Object> validateParams(Map<String, Object> params) {
        Map<String, Object> validatedParams = new HashMap<>();
        
        // 设置默认参数
        validatedParams.put("temperature", 0.7);
        validatedParams.put("max_tokens", 1024);
        validatedParams.put("top_p", 1.0);
        validatedParams.put("frequency_penalty", 0.0);
        validatedParams.put("presence_penalty", 0.0);
        
        // 合并用户参数
        if (params != null) {
            validatedParams.putAll(params);
        }
        
        return validatedParams;
    }

    /**
     * 获取引擎信息
     * @return 引擎信息
     */
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("version", "1.0.0");
        info.put("currentModel", currentModelId);
        info.put("currentProvider", currentProvider);
        info.put("sandbox", sandboxMode);
        info.put("availableProviders", listProviders());
        info.put("availableModels", listModels());
        return info;
    }

    /**
     * 重置引擎状态
     */
    public void reset() {
        this.currentModelId = "Qwen/Qwen2.5-7B-Instruct";
        this.currentProvider = "siliconflow";
        this.apiKey = null;
        this.baseUrl = null;
    }

    /**
     * 获取当前使用的模型信息
     * @return 模型信息
     */
    public Map<String, String> getCurrentModelInfo() {
        for (Map<String, String> model : AI_MODELS) {
            if (model.get("id").equals(currentModelId)) {
                return model;
            }
        }
        return null;
    }

    /**
     * 获取当前使用的提供商信息
     * @return 提供商信息
     */
    public Map<String, String> getCurrentProviderInfo() {
        for (Map<String, String> provider : AI_PROVIDERS) {
            if (provider.get("id").equals(currentProvider)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 添加自定义模型
     * @param modelInfo 模型信息
     */
    public void addCustomModel(Map<String, String> modelInfo) {
        if (modelInfo == null || !modelInfo.containsKey("id") || !modelInfo.containsKey("name") || !modelInfo.containsKey("provider")) {
            throw new IllegalArgumentException("无效的模型信息");
        }
        AI_MODELS.add(modelInfo);
    }

    /**
     * 添加自定义提供商
     * @param providerInfo 提供商信息
     */
    public void addCustomProvider(Map<String, String> providerInfo) {
        if (providerInfo == null || !providerInfo.containsKey("id") || !providerInfo.containsKey("name") || !providerInfo.containsKey("baseUrl")) {
            throw new IllegalArgumentException("无效的提供商信息");
        }
        AI_PROVIDERS.add(providerInfo);
    }

    /**
     * 移除自定义模型
     * @param modelId 模型 ID
     */
    public void removeCustomModel(String modelId) {
        AI_MODELS.removeIf(model -> model.get("id").equals(modelId));
    }

    /**
     * 移除自定义提供商
     * @param providerId 提供商 ID
     */
    public void removeCustomProvider(String providerId) {
        AI_PROVIDERS.removeIf(provider -> provider.get("id").equals(providerId));
    }
    
    /**
     * 读取文件内容 MCP 工具
     * @param params 工具参数，包含 filePath 字段
     * @return 读取结果
     */
    private Map<String, Object> callReadFileTool(Map<String, Object> params) throws IOException {
        // 获取文件路径参数
        String filePath = (String) params.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath 参数不能为空");
        }
        
        // 处理文件路径，确保在工作区内
        String processedPath = getProcessedFilePath(filePath);
        
        // 读取文件内容
        java.io.File file = new java.io.File(processedPath);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("文件不存在: " + processedPath);
        }
        
        String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("content", content);
        result.put("filePath", processedPath);
        result.put("size", file.length());
        return result;
    }
    
    /**
     * 写入文件内容 MCP 工具
     * @param params 工具参数，包含 filePath、content 和 append 字段
     * @return 写入结果
     */
    private Map<String, Object> callWriteFileTool(Map<String, Object> params) throws IOException {
        // 获取文件路径和内容参数
        String filePath = (String) params.get("filePath");
        String content = (String) params.get("content");
        boolean append = params.get("append") instanceof Boolean ? (Boolean) params.get("append") : false;
        
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath 参数不能为空");
        }
        if (content == null) {
            content = "";
        }
        
        // 处理文件路径，确保在工作区内
        String processedPath = getProcessedFilePath(filePath);
        
        // 写入文件内容
        java.io.File file = new java.io.File(processedPath);
        // 确保父目录存在
        file.getParentFile().mkdirs();
        
        if (append) {
            // 追加模式
            java.nio.file.Files.write(file.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } else {
            // 覆盖模式
            java.nio.file.Files.write(file.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("filePath", processedPath);
        result.put("size", file.length());
        result.put("append", append);
        return result;
    }
    
    /**
     * 列出文件 MCP 工具
     * @param params 工具参数，包含 dirPath 字段
     * @return 列出结果
     * @throws IOException 如果发生 I/O 错误
     */
    private Map<String, Object> callListFilesTool(Map<String, Object> params) throws IOException {
        // 获取目录路径参数
        String dirPath = (String) params.get("dirPath");
        if (dirPath == null || dirPath.isEmpty()) {
            throw new IllegalArgumentException("dirPath 参数不能为空");
        }
        
        // 处理目录路径，确保在工作区内
        String processedPath = getProcessedFilePath(dirPath);
        
        // 列出目录中的文件
        java.io.File dir = new java.io.File(processedPath);
        if (!dir.exists()) {
            throw new java.io.FileNotFoundException("目录不存在: " + processedPath);
        }
        
        java.io.File[] files = dir.listFiles();
        if (files == null) {
            files = new java.io.File[0];
        }
        
        // 构建文件列表
        List<Map<String, Object>> fileList = new ArrayList<>();
        for (java.io.File file : files) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", file.getName());
            fileInfo.put("path", file.getAbsolutePath());
            fileInfo.put("isDirectory", file.isDirectory());
            fileInfo.put("size", file.length());
            fileInfo.put("lastModified", file.lastModified());
            fileList.add(fileInfo);
        }
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("dirPath", processedPath);
        result.put("files", fileList);
        result.put("count", fileList.size());
        return result;
    }
    
    /**
     * 搜索文件 MCP 工具
     * @param params 工具参数，包含 searchTerm（搜索内容）和 recursive（是否递归搜索）字段
     * @return 搜索结果
     */
    private Map<String, Object> callSearchFilesTool(Map<String, Object> params) throws IOException {
        // 获取搜索参数
        String searchTerm = (String) params.get("searchTerm");
        boolean recursive = params.get("recursive") instanceof Boolean ? (Boolean) params.get("recursive") : true;
        
        if (searchTerm == null || searchTerm.isEmpty()) {
            throw new IllegalArgumentException("searchTerm 参数不能为空");
        }
        
        // 获取搜索目录，默认为工作区根目录
        String dirPath = (String) params.getOrDefault("dirPath", workspacePath);
        String processedPath = getProcessedFilePath(dirPath);
        
        // 执行搜索
        List<Map<String, Object>> matchedFiles = new ArrayList<>();
        searchFiles(new java.io.File(processedPath), searchTerm, recursive, matchedFiles);
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("searchTerm", searchTerm);
        result.put("dirPath", processedPath);
        result.put("recursive", recursive);
        result.put("matchedFiles", matchedFiles);
        result.put("count", matchedFiles.size());
        return result;
    }
    
    /**
     * 递归搜索文件
     * @param dir 搜索目录
     * @param searchTerm 搜索内容
     * @param recursive 是否递归搜索
     * @param matchedFiles 匹配的文件列表
     */
    private void searchFiles(java.io.File dir, String searchTerm, boolean recursive, List<Map<String, Object>> matchedFiles) throws IOException {
        if (!dir.isDirectory()) {
            return;
        }
        
        java.io.File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        for (java.io.File file : files) {
            if (file.isDirectory()) {
                if (recursive) {
                    searchFiles(file, searchTerm, recursive, matchedFiles);
                }
            } else {
                // 读取文件内容并检查是否包含搜索词
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    if (content.contains(searchTerm)) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("path", file.getAbsolutePath());
                        fileInfo.put("size", file.length());
                        fileInfo.put("lastModified", file.lastModified());
                        matchedFiles.add(fileInfo);
                    }
                } catch (IOException e) {
                    // 跳过无法读取的文件
                    continue;
                }
            }
        }
    }
    
    /**
     * 读取文件特定行内容 MCP 工具
     * @param params 工具参数，包含 filePath（文件路径）、startLine（起始行号）和 endLine（结束行号）字段
     * @return 读取结果
     */
    private Map<String, Object> callReadFileLinesTool(Map<String, Object> params) throws IOException {
        // 获取文件路径参数
        String filePath = (String) params.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath 参数不能为空");
        }
        
        // 处理文件路径，确保在工作区内
        String processedPath = getProcessedFilePath(filePath);
        
        // 获取行号参数
        int startLine = params.getOrDefault("startLine", 1) instanceof Number ? ((Number) params.get("startLine")).intValue() : 1;
        int endLine = params.getOrDefault("endLine", Integer.MAX_VALUE) instanceof Number ? ((Number) params.get("endLine")).intValue() : Integer.MAX_VALUE;
        
        // 确保行号有效
        if (startLine < 1) {
            startLine = 1;
        }
        if (endLine < startLine) {
            endLine = startLine;
        }
        
        // 读取文件内容
        java.io.File file = new java.io.File(processedPath);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("文件不存在: " + processedPath);
        }
        
        // 读取特定行
        List<String> lines = java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        int actualEndLine = Math.min(endLine, lines.size());
        List<String> resultLines = lines.subList(startLine - 1, actualEndLine);
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("filePath", processedPath);
        result.put("startLine", startLine);
        result.put("endLine", actualEndLine);
        result.put("totalLines", lines.size());
        result.put("lines", resultLines);
        result.put("count", resultLines.size());
        return result;
    }
    
    /**
     * 写入文件特定行内容 MCP 工具
     * @param params 工具参数，包含 filePath（文件路径）、startLine（起始行号）、lines（要写入的行内容列表）
     * @return 写入结果
     */
    private Map<String, Object> callWriteFileLinesTool(Map<String, Object> params) throws IOException {
        // 获取文件路径参数
        String filePath = (String) params.get("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath 参数不能为空");
        }
        
        // 获取起始行号
        int startLine = params.getOrDefault("startLine", 1) instanceof Number ? ((Number) params.get("startLine")).intValue() : 1;
        if (startLine < 1) {
            startLine = 1;
        }
        
        // 获取要写入的行内容
        List<String> linesToWrite;
        if (params.get("lines") instanceof List) {
            linesToWrite = (List<String>) params.get("lines");
        } else if (params.get("content") instanceof String) {
            // 支持直接传入字符串内容，按换行符分割为行
            String content = (String) params.get("content");
            linesToWrite = List.of(content.split("\\n"));
        } else {
            throw new IllegalArgumentException("lines 或 content 参数不能为空");
        }
        
        if (linesToWrite.isEmpty()) {
            throw new IllegalArgumentException("要写入的内容不能为空");
        }
        
        // 处理文件路径，确保在工作区内
        String processedPath = getProcessedFilePath(filePath);
        java.io.File file = new java.io.File(processedPath);
        
        List<String> originalLines = new ArrayList<>();
        if (file.exists()) {
            // 读取原始文件内容
            originalLines = java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        }
        
        // 创建新的行列表
        List<String> newLines = new ArrayList<>();
        int endLine = startLine + linesToWrite.size() - 1;
        int originalLineCount = originalLines.size();
        
        // 1. 添加起始行之前的内容
        for (int i = 0; i < startLine - 1 && i < originalLineCount; i++) {
            newLines.add(originalLines.get(i));
        }
        
        // 2. 添加要写入的行
        newLines.addAll(linesToWrite);
        
        // 3. 添加起始行+写入行数之后的内容
        for (int i = endLine; i < originalLineCount; i++) {
            newLines.add(originalLines.get(i));
        }
        
        // 写入新内容
        java.nio.file.Files.write(file.toPath(), String.join("\n", newLines).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("filePath", processedPath);
        result.put("startLine", startLine);
        result.put("endLine", endLine);
        result.put("totalLines", newLines.size());
        result.put("writtenLines", linesToWrite.size());
        result.put("message", "文件行写入成功");
        return result;
    }
    
    /**
     * 关闭 AI 引擎，释放资源
     */
    public void close() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                // 等待线程池关闭，最多等待30秒
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    // 如果等待超时，强制关闭
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                // 处理线程中断异常
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭所有 MCP 客户端连接
        closeAllMcpClients();
    }
    
    /**
     * 获取所有可用方法的帮助信息
     * @return 方法帮助信息列表
     */
    public Map<String, Object> help() {
        Map<String, Object> helpInfo = new HashMap<>();
        
        // 生成相关方法
        helpInfo.put("gen", Map.of(
            "fullName", "generate",
            "description", "生成文本",
            "params", List.of(
                Map.of("name", "prompt", "type", "string", "required", true, "description", "提示词"),
                Map.of("name", "params", "type", "map", "required", false, "description", "生成参数"),
                Map.of("name", "conversationId", "type", "string", "required", false, "description", "对话ID，null表示使用当前对话")
            ),
            "returns", "string",
            "example", "ai.gen('你好')\nai.gen('你叫什么名字？', {}, convId)"
        ));
        
        helpInfo.put("genAsync", Map.of(
            "fullName", "generateAsync",
            "description", "异步生成文本",
            "params", List.of(
                Map.of("name", "prompt", "type", "string", "required", true, "description", "提示词"),
                Map.of("name", "params", "type", "map", "required", false, "description", "生成参数"),
                Map.of("name", "conversationId", "type", "string", "required", false, "description", "对话ID，null表示使用当前对话"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数")
            ),
            "returns", "void",
            "example", "ai.genAsync('你好', {}, function(error, result) print(result) end)\nai.genAsync('你能做什么？', {}, convId, function(error, result) print(result) end)"
        ));
        
        helpInfo.put("genStream", Map.of(
            "fullName", "generateStream",
            "description", "流式生成文本",
            "params", List.of(
                Map.of("name", "prompt", "type", "string", "required", true, "description", "提示词"),
                Map.of("name", "params", "type", "map", "required", false, "description", "生成参数"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数")
            ),
            "returns", "void",
            "example", "ai.genStream('你好', {}, function(result) print(result) end)"
        ));
        
        // MCP工具相关方法
        helpInfo.put("callTool", Map.of(
            "fullName", "callMCPTool",
            "description", "调用MCP工具",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称"),
                Map.of("name", "params", "type", "map", "required", false, "description", "工具参数")
            ),
            "returns", "map",
            "example", "ai.callTool('read_file', { filePath = 'test.txt' })"
        ));
        
        helpInfo.put("callToolAsync", Map.of(
            "fullName", "callMCPToolAsync",
            "description", "异步调用MCP工具",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称"),
                Map.of("name", "params", "type", "map", "required", false, "description", "工具参数"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数")
            ),
            "returns", "void",
            "example", "ai.callToolAsync('list_files', { dirPath = '.' }, function(error, result) print(result) end)"
        ));
        
        helpInfo.put("regTool", Map.of(
            "fullName", "registerMCPToolFromLua",
            "description", "注册Lua函数作为MCP工具",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称"),
                Map.of("name", "description", "type", "string", "required", true, "description", "工具描述"),
                Map.of("name", "inputSchema", "type", "map", "required", false, "description", "输入Schema"),
                Map.of("name", "luaFunction", "type", "function", "required", true, "description", "Lua函数")
            ),
            "returns", "void",
            "example", "ai.regTool('add', '加法工具', function(params) return { result = params.a + params.b } end)"
        ));
        
        helpInfo.put("rmTool", Map.of(
            "fullName", "removeMCPTool",
            "description", "移除已注册的MCP工具",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称")
            ),
            "returns", "boolean",
            "example", "ai.rmTool('add')"
        ));
        
        helpInfo.put("listTools", Map.of(
            "fullName", "getRegisteredToolNames",
            "description", "获取所有已注册的工具名称",
            "params", List.of(),
            "returns", "list",
            "example", "ai.listTools()"
        ));
        
        helpInfo.put("toolInfo", Map.of(
            "fullName", "getToolDetails",
            "description", "获取指定工具的详细信息",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称")
            ),
            "returns", "map",
            "example", "ai.toolInfo('read_file')"
        ));
        
        // 对话管理相关方法
        helpInfo.put("createConv", Map.of(
            "fullName", "createConversation",
            "description", "创建新对话",
            "params", List.of(),
            "returns", "string",
            "example", "local convId = ai.createConv()"
        ));
        
        helpInfo.put("switchConv", Map.of(
            "fullName", "switchConversation",
            "description", "切换到指定对话",
            "params", List.of(
                Map.of("name", "conversationId", "type", "string", "required", true, "description", "对话ID")
            ),
            "returns", "void",
            "example", "ai.switchConv(convId)"
        ));
        
        helpInfo.put("clearConv", Map.of(
            "fullName", "clearCurrentContext",
            "description", "清空当前对话的上下文",
            "params", List.of(),
            "returns", "void",
            "example", "ai.clearConv()"
        ));
        
        helpInfo.put("clearConvById", Map.of(
            "fullName", "clearContext",
            "description", "清空指定对话的上下文",
            "params", List.of(
                Map.of("name", "conversationId", "type", "string", "required", true, "description", "对话ID")
            ),
            "returns", "void",
            "example", "ai.clearConvById(convId)"
        ));
        
        helpInfo.put("deleteConv", Map.of(
            "fullName", "deleteConversation",
            "description", "删除指定对话",
            "params", List.of(
                Map.of("name", "conversationId", "type", "string", "required", true, "description", "对话ID")
            ),
            "returns", "void",
            "example", "ai.deleteConv(convId)"
        ));
        
        helpInfo.put("sendMsg", Map.of(
            "fullName", "sendMessage",
            "description", "发送消息到指定对话或当前对话",
            "params", List.of(
                Map.of("name", "content", "type", "string", "required", true, "description", "消息内容"),
                Map.of("name", "conversationId", "type", "string", "required", false, "description", "对话ID，null表示使用当前对话")
            ),
            "returns", "string",
            "example", "ai.sendMsg('你好')\nai.sendMsg('你叫什么名字？', convId)"
        ));
        
        helpInfo.put("sendSysMsg", Map.of(
            "fullName", "sendSystemMessage",
            "description", "发送系统消息到当前对话",
            "params", List.of(
                Map.of("name", "content", "type", "string", "required", true, "description", "系统消息内容")
            ),
            "returns", "void",
            "example", "ai.sendSysMsg('你是一个智能助手')"
        ));
        
        helpInfo.put("getConv", Map.of(
            "fullName", "getConversation",
            "description", "获取指定对话",
            "params", List.of(
                Map.of("name", "conversationId", "type", "string", "required", true, "description", "对话ID")
            ),
            "returns", "conversation",
            "example", "local conv = ai.getConv(convId)"
        ));
        
        helpInfo.put("getAllConv", Map.of(
            "fullName", "getAllConversations",
            "description", "获取所有对话",
            "params", List.of(),
            "returns", "list",
            "example", "local convs = ai.getAllConv()"
        ));
        
        // MCP 工具查询方法
        helpInfo.put("getMcpServers", Map.of(
            "fullName", "getMCPServers",
            "description", "获取所有 MCP 服务器列表，包含工具和状态信息",
            "params", List.of(),
            "returns", "list",
            "example", "local servers = ai.getMcpServers()"
        ));
        
        helpInfo.put("getMcpServerDetails", Map.of(
            "fullName", "getMCPServerDetails",
            "description", "获取指定 MCP 服务器的详细信息，包含工具列表",
            "params", List.of(
                Map.of("name", "serverId", "type", "string", "required", true, "description", "服务器 ID")
            ),
            "returns", "map",
            "example", "local server = ai.getMcpServerDetails(serverId)"
        ));
        
        helpInfo.put("getAllMcpTools", Map.of(
            "fullName", "getAllMCPTools",
            "description", "获取所有 MCP 工具，包括本地工具和远程工具",
            "params", List.of(),
            "returns", "list",
            "example", "local allTools = ai.getAllMcpTools()"
        ));
        
        helpInfo.put("getMcpServerTools", Map.of(
            "fullName", "getMCPServerTools",
            "description", "获取 MCP 服务器工具列表，支持按连接类型和状态过滤",
            "params", List.of(
                Map.of("name", "connectionType", "type", "string", "required", false, "description", "连接类型（'sse' 或 'streamable_http'），null表示所有类型"),
                Map.of("name", "onlyConnected", "type", "boolean", "required", false, "description", "是否只返回已连接的服务器，默认false")
            ),
            "returns", "list",
            "example", "local sseTools = ai.getMcpServerTools('sse', true)"
        ));
        
        // 搜索功能相关方法
        helpInfo.put("enableSearch", Map.of(
            "fullName", "enableWebSearch",
            "description", "启用或禁用网络搜索功能",
            "params", List.of(
                Map.of("name", "enabled", "type", "boolean", "required", true, "description", "是否启用")
            ),
            "returns", "void",
            "example", "ai.enableSearch(true)"
        ));
        
        helpInfo.put("isSearchEnabled", Map.of(
            "fullName", "isWebSearchEnabled",
            "description", "检查网络搜索功能是否启用",
            "params", List.of(),
            "returns", "boolean",
            "example", "local enabled = ai.isSearchEnabled()"
        ));
        
        helpInfo.put("addSearchService", Map.of(
            "fullName", "addSearchService",
            "description", "添加搜索服务",
            "params", List.of(
                Map.of("name", "searchService", "type", "map", "required", true, "description", "搜索服务配置")
            ),
            "returns", "void",
            "example", "ai.addSearchService({id = 'custom', name = '自定义搜索引擎', enabled = true})"
        ));
        
        helpInfo.put("setSearchService", Map.of(
            "fullName", "setSearchServiceSelected",
            "description", "设置当前选中的搜索服务",
            "params", List.of(
                Map.of("name", "index", "type", "number", "required", true, "description", "搜索服务索引")
            ),
            "returns", "void",
            "example", "ai.setSearchService(0)"
        ));
        
        helpInfo.put("getSearchServices", Map.of(
            "fullName", "getSearchServices",
            "description", "获取所有搜索服务",
            "params", List.of(),
            "returns", "list",
            "example", "local services = ai.getSearchServices()"
        ));
        
        // MCP 工具增强功能相关方法
        helpInfo.put("getAllAvailableTools", Map.of(
            "fullName", "getAllAvailableTools",
            "description", "获取所有可用的 MCP 工具，包括内置工具、Lua工具和远程工具",
            "params", List.of(),
            "returns", "list",
            "example", "local tools = ai.getAllAvailableTools()"
        ));
        
        helpInfo.put("addMcpServer", Map.of(
            "fullName", "addMcpServerConfig",
            "description", "添加 MCP 服务器配置",
            "params", List.of(
                Map.of("name", "config", "type", "map", "required", true, "description", "MCP 服务器配置")
            ),
            "returns", "void",
            "example", "ai.addMcpServer({name = 'My MCP Server', enable = true, url = 'http://localhost:8080'})"
        ));
        
        helpInfo.put("updateMcpServer", Map.of(
            "fullName", "updateMcpServerConfig",
            "description", "更新 MCP 服务器配置",
            "params", List.of(
                Map.of("name", "config", "type", "map", "required", true, "description", "更新后的 MCP 服务器配置")
            ),
            "returns", "void",
            "example", "ai.updateMcpServer({id = '123', name = 'Updated Server', enable = true})"
        ));
        
        helpInfo.put("removeMcpServer", Map.of(
            "fullName", "removeMcpServerConfig",
            "description", "删除 MCP 服务器配置",
            "params", List.of(
                Map.of("name", "config", "type", "map", "required", true, "description", "MCP 服务器配置")
            ),
            "returns", "void",
            "example", "ai.removeMcpServer({id = '123'})"
        ));
        
        helpInfo.put("syncAllMcpTools", Map.of(
            "fullName", "syncAllMcpTools",
            "description", "同步所有 MCP 服务器的工具列表",
            "params", List.of(),
            "returns", "void",
            "example", "ai.syncAllMcpTools()"
        ));
        
        helpInfo.put("fetchUrlAsync", Map.of(
            "fullName", "fetchUrlAsync",
            "description", "异步获取指定 URL 的原始响应，连接成功或失败时调用回调函数",
            "params", List.of(
                Map.of("name", "url", "type", "string", "required", true, "description", "要连接的 URL"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数，参数为 result 和 error")
            ),
            "returns", "void",
            "example", "ai.fetchUrlAsync('http://127.0.0.1:8080/messages', function(result, error) if error then print('连接失败:', error) else print('连接成功:', result.statusCode) end end)"
        ));
        
        helpInfo.put("connectMcpServerAsync", Map.of(
            "fullName", "connectMcpServerAsync",
            "description", "异步连接到 MCP 服务器并获取工具列表，连接成功或失败时调用回调函数",
            "params", List.of(
                Map.of("name", "url", "type", "string", "required", true, "description", "MCP 服务器 URL"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数，参数为 result 和 error")
            ),
            "returns", "void",
            "example", "ai.connectMcpServerAsync('http://127.0.0.1:8080/messages', function(result, error) if error then print('连接失败:', error) else print('获取到工具数量:', result.toolCount) end end)"
        ));
        
        helpInfo.put("registerMCPServer", Map.of(
            "fullName", "registerMCPServer",
            "description", "注册 MCP 服务器",
            "params", List.of(
                Map.of("name", "type", "type", "string", "required", true, "description", "服务器类型（'sse' 或 'streamable_http'）"),
                Map.of("name", "url", "type", "string", "required", true, "description", "服务器 URL"),
                Map.of("name", "name", "type", "string", "required", false, "description", "服务器名称，默认为空"),
                Map.of("name", "enable", "type", "boolean", "required", false, "description", "是否启用，默认为true")
            ),
            "returns", "void",
            "example", "ai.registerMCPServer('streamable_http', 'http://127.0.0.1:8080/messages', '星辰服务器', true)"
        ));
        
        helpInfo.put("removeMCPServer", Map.of(
            "fullName", "removeMCPServer",
            "description", "删除 MCP 服务器",
            "params", List.of(
                Map.of("name", "index", "type", "number", "required", true, "description", "服务器索引")
            ),
            "returns", "void",
            "example", "ai.removeMCPServer(0)"
        ));
        
        helpInfo.put("removeMCPServerById", Map.of(
            "fullName", "removeMCPServerById",
            "description", "根据 ID 删除 MCP 服务器",
            "params", List.of(
                Map.of("name", "serverId", "type", "string", "required", true, "description", "服务器 ID")
            ),
            "returns", "void",
            "example", "ai.removeMCPServerById('123e4567-e89b-12d3-a456-426614174000')"
        ));
        
        helpInfo.put("syncMCPTools", Map.of(
            "fullName", "syncMCPTools",
            "description", "同步指定 MCP 服务器的工具列表",
            "params", List.of(
                Map.of("name", "serverIndex", "type", "number", "required", true, "description", "服务器索引")
            ),
            "returns", "list",
            "example", "local tools = ai.syncMCPTools(0)"
        ));
        
        helpInfo.put("registerMCPServerAsync", Map.of(
            "fullName", "registerMCPServerAsync",
            "description", "异步注册 MCP 服务器，自动同步工具列表并返回结果",
            "params", List.of(
                Map.of("name", "type", "type", "string", "required", true, "description", "服务器类型（'sse' 或 'streamable_http'）"),
                Map.of("name", "url", "type", "string", "required", true, "description", "服务器 URL"),
                Map.of("name", "name", "type", "string", "required", false, "description", "服务器名称，默认为空"),
                Map.of("name", "enable", "type", "boolean", "required", false, "description", "是否启用，默认为true"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数，返回注册结果和同步的工具列表")
            ),
            "returns", "void",
            "example", "ai.registerMCPServerAsync('streamable_http', 'http://127.0.0.1:8080/messages', '星辰服务器', true, function(error, result) print('注册成功', result.serverName, '工具数量:', result.toolCount) end)"
        ));
        
        helpInfo.put("registerMCPServerAsyncSimple", Map.of(
            "fullName", "registerMCPServerAsync",
            "description", "异步注册 MCP 服务器（简化版），自动同步工具列表",
            "params", List.of(
                Map.of("name", "type", "type", "string", "required", true, "description", "服务器类型（'sse' 或 'streamable_http'）"),
                Map.of("name", "url", "type", "string", "required", true, "description", "服务器 URL"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数，返回注册结果和同步的工具列表")
            ),
            "returns", "void",
            "example", "ai.registerMCPServerAsync('streamable_http', 'http://127.0.0.1:8080/messages', function(error, result) print('注册成功，工具数量:', result.toolCount) end)"
        ));
        
        // 配置相关方法
        helpInfo.put("setMod", Map.of(
            "fullName", "setModel",
            "description", "设置当前使用的AI模型",
            "params", List.of(
                Map.of("name", "modelId", "type", "string", "required", true, "description", "模型ID")
            ),
            "returns", "void",
            "example", "ai.setMod('gpt-3.5-turbo')"
        ));
        
        helpInfo.put("getMod", Map.of(
            "fullName", "getModel",
            "description", "获取当前使用的AI模型",
            "params", List.of(),
            "returns", "string",
            "example", "local model = ai.getMod()"
        ));
        
        helpInfo.put("setProv", Map.of(
            "fullName", "setProvider",
            "description", "设置AI提供商",
            "params", List.of(
                Map.of("name", "providerId", "type", "string", "required", true, "description", "提供商ID")
            ),
            "returns", "void",
            "example", "ai.setProv('openai')"
        ));
        
        helpInfo.put("getProv", Map.of(
            "fullName", "getProvider",
            "description", "获取当前AI提供商",
            "params", List.of(),
            "returns", "string",
            "example", "local provider = ai.getProv()"
        ));
        
        helpInfo.put("setKey", Map.of(
            "fullName", "setApiKey",
            "description", "设置API密钥",
            "params", List.of(
                Map.of("name", "apiKey", "type", "string", "required", true, "description", "API密钥")
            ),
            "returns", "void",
            "example", "ai.setKey('sk-xxx')"
        ));
        
        helpInfo.put("setWork", Map.of(
            "fullName", "setWorkspace",
            "description", "设置工作区路径",
            "params", List.of(
                Map.of("name", "path", "type", "string", "required", true, "description", "工作区路径")
            ),
            "returns", "void",
            "example", "ai.setWork('/sdcard/mywork/')"
        ));
        
        helpInfo.put("getWork", Map.of(
            "fullName", "getWorkspace",
            "description", "获取工作区路径",
            "params", List.of(),
            "returns", "string",
            "example", "local workPath = ai.getWork()"
        ));
        
        helpInfo.put("enableWork", Map.of(
            "fullName", "enableWorkspace",
            "description", "启用或禁用工作区",
            "params", List.of(
                Map.of("name", "enabled", "type", "boolean", "required", true, "description", "是否启用")
            ),
            "returns", "void",
            "example", "ai.enableWork(true)"
        ));
        
        // 工具禁用相关方法
        helpInfo.put("disableTool", Map.of(
            "fullName", "disableMCPTool",
            "description", "禁用指定MCP工具（包括内置工具和从Lua注册的工具）",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称")
            ),
            "returns", "void",
            "example", "ai.disableTool('execute_command')"
        ));
        
        helpInfo.put("enableTool", Map.of(
            "fullName", "enableMCPTool",
            "description", "启用指定MCP工具（包括内置工具和从Lua注册的工具）",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称")
            ),
            "returns", "void",
            "example", "ai.enableTool('execute_command')"
        ));
        
        helpInfo.put("disableAllTools", Map.of(
            "fullName", "disableAllMCPTools",
            "description", "禁用所有MCP工具（包括内置工具和从Lua注册的工具）",
            "params", List.of(),
            "returns", "void",
            "example", "ai.disableAllTools()"
        ));
        
        helpInfo.put("enableAllTools", Map.of(
            "fullName", "enableAllMCPTools",
            "description", "启用所有MCP工具（包括内置工具和从Lua注册的工具）",
            "params", List.of(),
            "returns", "void",
            "example", "ai.enableAllTools()"
        ));
        
        helpInfo.put("disableFileTools", Map.of(
            "fullName", "disableFileOperationTools",
            "description", "禁用所有文件操作工具",
            "params", List.of(),
            "returns", "void",
            "example", "ai.disableFileTools()"
        ));
        
        helpInfo.put("enableFileTools", Map.of(
            "fullName", "enableFileOperationTools",
            "description", "启用所有文件操作工具",
            "params", List.of(),
            "returns", "void",
            "example", "ai.enableFileTools()"
        ));
        
        helpInfo.put("disableCmdTool", Map.of(
            "fullName", "disableCommandExecutionTools",
            "description", "禁用命令行执行工具",
            "params", List.of(),
            "returns", "void",
            "example", "ai.disableCmdTool()"
        ));
        
        helpInfo.put("enableCmdTool", Map.of(
            "fullName", "enableCommandExecutionTools",
            "description", "启用命令行执行工具",
            "params", List.of(),
            "returns", "void",
            "example", "ai.enableCmdTool()"
        ));
        
        helpInfo.put("enableMCP", Map.of(
            "fullName", "enableMCP",
            "description", "启用或禁用MCP功能",
            "params", List.of(
                Map.of("name", "enabled", "type", "boolean", "required", true, "description", "是否启用")
            ),
            "returns", "void",
            "example", "ai.enableMCP(true)"
        ));
        
        helpInfo.put("isMCPEnabled", Map.of(
            "fullName", "isMCPEnabled",
            "description", "检查MCP功能是否启用",
            "params", List.of(),
            "returns", "boolean",
            "example", "local enabled = ai.isMCPEnabled()"
        ));
        
        helpInfo.put("setWorkspace", Map.of(
            "fullName", "setWorkspace",
            "description", "设置工作区路径",
            "params", List.of(
                Map.of("name", "path", "type", "string", "required", true, "description", "工作区路径")
            ),
            "returns", "void",
            "example", "ai.setWorkspace('/sdcard/XCLUA/')"
        ));
        
        helpInfo.put("getWorkspace", Map.of(
            "fullName", "getWorkspace",
            "description", "获取当前工作区路径",
            "params", List.of(),
            "returns", "string",
            "example", "local path = ai.getWorkspace()"
        ));
        
        helpInfo.put("enableWorkspace", Map.of(
            "fullName", "enableWorkspace",
            "description", "启用或禁用工作区",
            "params", List.of(
                Map.of("name", "enabled", "type", "boolean", "required", true, "description", "是否启用")
            ),
            "returns", "void",
            "example", "ai.enableWorkspace(true)"
        ));
        
        helpInfo.put("isWorkspaceEnabled", Map.of(
            "fullName", "isWorkspaceEnabled",
            "description", "检查工作区是否启用",
            "params", List.of(),
            "returns", "boolean",
            "example", "local enabled = ai.isWorkspaceEnabled()"
        ));
        
        helpInfo.put("executeToolCall", Map.of(
            "fullName", "executeToolCall",
            "description", "执行工具调用",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称"),
                Map.of("name", "params", "type", "map", "required", false, "description", "工具参数")
            ),
            "returns", "map",
            "example", "local result = ai.executeToolCall('read_file', { filePath = 'test.txt' })"
        ));
        
        helpInfo.put("addMCPServer", Map.of(
            "fullName", "addMCPServer",
            "description", "添加MCP服务器",
            "params", List.of(
                Map.of("name", "type", "type", "string", "required", true, "description", "服务器类型（'sse' 或 'streamable_http'）"),
                Map.of("name", "config", "type", "map", "required", true, "description", "MCP服务器配置")
            ),
            "returns", "void",
            "example", "ai.addMCPServer('streamable_http', { url = 'http://127.0.0.1:8080/messages', name = '星辰服务器', enable = true })"
        ));
        
        helpInfo.put("callMCPToolAsync", Map.of(
            "fullName", "callMCPToolAsync",
            "description", "异步调用MCP工具",
            "params", List.of(
                Map.of("name", "toolName", "type", "string", "required", true, "description", "工具名称"),
                Map.of("name", "params", "type", "map", "required", false, "description", "工具参数"),
                Map.of("name", "callback", "type", "function", "required", true, "description", "回调函数，参数为 result 和 error")
            ),
            "returns", "void",
            "example", "ai.callMCPToolAsync('read_file', { filePath = 'test.txt' }, function(result, error) if error then print('调用失败:', error) else print('调用成功:', result) end end)"
        ));
        
        // 其他方法
        helpInfo.put("info", Map.of(
            "fullName", "info",
            "description", "获取引擎信息",
            "params", List.of(),
            "returns", "map",
            "example", "local info = ai.info()"
        ));
        
        helpInfo.put("reset", Map.of(
            "fullName", "reset",
            "description", "重置引擎状态",
            "params", List.of(),
            "returns", "void",
            "example", "ai.reset()"
        ));
        
        helpInfo.put("close", Map.of(
            "fullName", "close",
            "description", "关闭AI引擎，释放资源",
            "params", List.of(),
            "returns", "void",
            "example", "ai.close()"
        ));
        
        // 添加提供商和模型信息
        helpInfo.put("_providers", new ArrayList<>(AI_PROVIDERS));
        helpInfo.put("_models", new ArrayList<>(AI_MODELS));
        
        return helpInfo;
    }
    
    // 生成相关简写方法
    
    /**
     * generate 的简写版本
     */
    public String gen(String prompt) {
        return generate(prompt);
    }
    
    /**
     * generate 的简写版本
     */
    public String gen(String prompt, Map<String, Object> params) {
        return generate(prompt, params);
    }
    
    /**
     * generateAsync 的简写版本
     */
    public void genAsync(String prompt, Map<String, Object> params, LuaObject callback) {
        generateAsync(prompt, params, callback);
    }
    
    /**
     * generateAsync 的简写版本，支持指定对话ID
     */
    public void genAsync(String prompt, Map<String, Object> params, String conversationId, LuaObject callback) {
        generateAsync(prompt, params, conversationId, callback);
    }
    
    /**
     * generateStream 的简写版本
     */
    public void genStream(String prompt, Map<String, Object> params, LuaObject callback) {
        generateStream(prompt, params, callback);
    }
    
    // MCP工具相关简写方法
    
    /**
     * callMCPTool 的简写版本
     */
    public Map<String, Object> callTool(String toolName, Object params) {
        return callMCPTool(toolName, params);
    }
    
    /**
     * callMCPToolAsync 的简写版本
     */
    public void callToolAsync(String toolName, Map<String, Object> params, LuaObject callback) {
        callMCPToolAsync(toolName, params, callback);
    }
    
    /**
     * callMCPToolAsync 的简写版本
     */
    public void callToolAsync(String toolName, LuaObject luaTableParams, LuaObject callback) {
        callMCPToolAsync(toolName, luaTableParams, callback);
    }
    
    /**
     * registerMCPToolFromLua 的简写版本
     */
    public void regTool(String toolName, String description, Map<String, Object> inputSchema, LuaObject luaFunction) {
        registerMCPToolFromLua(toolName, description, inputSchema, luaFunction);
    }
    
    /**
     * registerMCPToolFromLua 的简写版本
     */
    public void regTool(String toolName, String description, LuaObject luaFunction) {
        registerMCPToolFromLua(toolName, description, luaFunction);
    }
    
    /**
     * removeMCPTool 的简写版本
     */
    public boolean rmTool(String toolName) {
        return removeMCPTool(toolName);
    }
    
    /**
     * getRegisteredToolNames 的简写版本
     */
    public List<String> listTools() {
        return getRegisteredToolNames();
    }
    
    /**
     * getToolDetails 的简写版本
     */
    public Map<String, Object> toolInfo(String toolName) {
        return getToolDetails(toolName);
    }
    
    // 对话管理相关简写方法
    
    /**
     * createConversation 的简写版本
     */
    public String createConv() {
        return createConversation();
    }
    
    /**
     * switchConversation 的简写版本
     */
    public void switchConv(String conversationId) {
        switchConversation(conversationId);
    }
    
    /**
     * deleteConversation 的简写版本
     */
    public void deleteConv(String conversationId) {
        deleteConversation(conversationId);
    }
    
    /**
     * sendMessage 的简写版本
     */
    public String sendMsg(String content) {
        return sendMessage(content);
    }
    
    /**
     * sendSystemMessage 的简写版本
     */
    public void sendSysMsg(String content) {
        sendSystemMessage(content);
    }
    
    // 配置相关简写方法
    
    /**
     * setModel 的简写版本
     */
    public void setMod(String modelId) {
        setModel(modelId);
    }
    
    /**
     * getModel 的简写版本
     */
    public String getMod() {
        return getModel();
    }
    
    // 工具禁用相关简写方法
    
    /**
     * disableAllMCPTools 的简写版本
     */
    public void disableAllTools() {
        disableAllMCPTools();
    }
    
    /**
     * enableAllMCPTools 的简写版本
     */
    public void enableAllTools() {
        enableAllMCPTools();
    }
    
    /**
     * setProvider 的简写版本
     */
    public void setProv(String providerId) {
        setProvider(providerId);
    }
    
    /**
     * getProvider 的简写版本
     */
    public String getProv() {
        return getProvider();
    }
    
    /**
     * setApiKey 的简写版本
     */
    public void setKey(String apiKey) {
        setApiKey(apiKey);
    }
    
    /**
     * setWorkspace 的简写版本
     */
    public void setWork(String path) {
        setWorkspace(path);
    }
    
    /**
     * getWorkspace 的简写版本
     */
    public String getWork() {
        return getWorkspace();
    }
    
    /**
     * enableWorkspace 的简写版本
     */
    public void enableWork(boolean enabled) {
        enableWorkspace(enabled);
    }
    
    /**
     * isWorkspaceEnabled 的简写版本
     */
    public boolean isWorkEnabled() {
        return isWorkspaceEnabled();
    }
    
    // 工具禁用相关简写方法
    
    /**
     * disableBuiltinTool 的简写版本，现在也支持禁用从Lua注册的工具
     */
    public void disableTool(String toolName) {
        disableMCPTool(toolName);
    }
    
    /**
     * enableBuiltinTool 的简写版本，现在也支持启用从Lua注册的工具
     */
    public void enableTool(String toolName) {
        enableMCPTool(toolName);
    }
    
    /**
     * disableFileOperationTools 的简写版本
     */
    public void disableFileTools() {
        disableFileOperationTools();
    }
    
    /**
     * enableFileOperationTools 的简写版本
     */
    public void enableFileTools() {
        enableFileOperationTools();
    }
    
    /**
     * disableCommandExecutionTools 的简写版本
     */
    public void disableCmdTool() {
        disableCommandExecutionTools();
    }
    
    /**
     * enableCommandExecutionTools 的简写版本
     */
    public void enableCmdTool() {
        enableCommandExecutionTools();
    }
}
