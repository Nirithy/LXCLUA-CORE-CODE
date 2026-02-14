package com.nirithy.luaeditor.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lua自动补全包配置
 * 定义所有内置库的函数列表和关键字
 */
public final class AutoCompletePackages {

    private AutoCompletePackages() {
    }

    public static final List<String> DEFAULT_KEYWORDS = Arrays.asList(
            "and", "break", "case", "catch", "const", "continue", "default", "defer", "do",
            "else", "elseif", "end", "enum", "false", "finally", "for", "function", "global", "goto",
            "if", "in", "keyword", "lambda", "local", "nil", "not", "operator", "or", "repeat", "return", "switch",
            "then", "true", "try", "until", "when", "while", "is", "self", "__add", "__band",
            "__bnot", "__bor", "__bxor", "__call", "__close", "__concat", "__div", "__eq",
            "__gc", "__idiv", "__index", "__le", "__len", "__lt", "__mod", "__mul", "__newindex",
            "__pow", "__shl", "__shr", "__sub", "__tostring", "__unm", "_ENV", "_G", "assert",
            "collectgarbage", "dofile", "error", "getfenv", "getmetatable", "ipairs", "load",
            "loadfile","loadsfile", "loadstring", "module", "next", "pairs", "pcall","proc", "print", "rawequal",
            "rawget", "rawlen", "rawset", "require", "select", "self", "setfenv", "setmetatable",
            "tointeger", "tonumber", "tostring","toasc2i", "type", "unpack","wymd5", "xpcall", "activity", "call",
             "dump","grand", "each", "fsleep", "fwake", "import", "loadbitmap", "loadlayout", "loadmenu","match", "onDestroy", "onActivityResult", "onResult",
            "onCreateOptionsMenu", "onOptionsItemSelected", "onClick", "onTouch", "onLongClick",
            "onItemClick", "onItemLongClick", "java", "async", "await",
            // OOP 面向对象关键字
            "abstract", "class", "extends", "final", "implements", "interface", "new",
            "super", "private", "protected", "public", "static", "struct", "concept", "namespace",
            // 类型关键字
            "bool", "char", "double", "float", "int", "long", "void", "asm", "using", "requires");

    public static final Map<String, List<String>> DEFAULT_PACKAGES = new HashMap<String, List<String>>() {
        {
            put("coroutine", Arrays.asList(
                    "create", "isyieldable", "resume", "running", "status", "wrap", "yield"));
            put("debug", Arrays.asList(
                    "debug", "gethook", "getinfo", "getlocal", "getmetatable", "getregistry",
                    "getupvalue", "getuservalue", "sethook", "setlocal", "setmetatable",
                    "setupvalue", "setuservalue", "traceback", "upvalueid", "upvaluejoin"));
            put("io", Arrays.asList(
                     "close", "flush", "info", "input", "isdir", "linecount", "lines", "ls", "mkdir", "mmap", "munmap", "open",
                     "output", "popen", "read", "readall", "readline", "readlines", "stderr", "stdin", "stdout", "tmpfile",
                     "type", "write", "writeline", "writelines"));
            put("luajava", Arrays.asList(
                    "astable", "bindClass", "clear", "coding", "createArray", "createProxy",
                    "getContext", "instanceof", "loadLib", "loaded", "luapath", "new", "newArray",
                    "newInstance", "override", "package", "tostring"));
            put("math", Arrays.asList(
                    "abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "cosh", "deg", "exp",
                    "floor", "fmod", "frexp", "huge", "ldexp", "log", "log10", "max", "maxinteger",
                    "min", "mininteger", "modf", "pi", "pow", "rad", "random", "randomseed", "sin",
                    "sinh", "sqrt", "tan", "tanh", "tointeger", "type", "ult"));
            put("os", Arrays.asList(
                    "aname", "arg0", "clock", "date", "difftime", "execute", "exit", "fsuid", "getenv", "getpid", 
                    "getppid", "libs", "mtime", "prctl", "procname", "randbytes", "remove", "rename", 
                    "seccomp", "setlocale", "sleep", "stacksize", "syscall", "tickcount", "tid", "time", 
                    "tracerpid", "tmpname", "usleep"));
            put("package", Arrays.asList(
                    "config", "cpath", "loaded", "loaders", "loadlib", "path", "preload",
                    "searchers", "searchpath", "seeall"));
            put("string", Arrays.asList(
             // 原有
             "byte", "char", "data", "data2png", "dump", "file", "file2png", "find", "format",
             "gfind", "gmatch", "gsub", "len", "lower", "match", "png2data", "png2file",
             "rep", "reverse", "sub", "upper",
             // 新增（上面缺少的）
             "aes_decrypt", "aes_encrypt", "contains", "crc32", "endswith", "escape",
             "fromhex", "hex", "imageresize", "ltrim", "pack", "packsize", "rtrim",
             "sha256", "split", "startswith", "trim", "unpack","envelop"
            ));

            put("table", Arrays.asList(
                    "add","clear","create", "clone", "concat", "const", "find","fill", "foreach", "foreachi", "gfind",
                    "keys","insert", "maxn", "move", "pack", "remove", "size", "sort", "unpack","vals"));
            put("utf8", Arrays.asList(
                    "byte", "char", "charpattern", "charpos", "codepoint", "codes", "escape",
                    "find", "fold", "gfind", "gmatch", "gsub", "insert", "len", "lower", "match",
                    "ncasecmp", "next", "offset", "remove", "reverse", "sub", "title", "upper",
                    "width", "widthindex"));

            put("fs", Arrays.asList(
                    "ls", "isdir", "isfile", "mkdir", "rm", "exists", "stat",
                    "currentdir", "chdir", "abs", "basename", "dirname", "set_permissions"));

            put("ptr", Arrays.asList(
                    "new", "addr", "add", "inc", "dec", "sub", "read", "write",
                    "get", "set", "malloc", "free", "string", "copy", "move",
                    "fill", "compare", "of", "null", "is_null", "equal", "tohex"));

            put("smgr", Arrays.asList(
                    "getuserid", "hasshareduserid", "getdatadir", "readfile", "writefile",
                    "deletefile", "listfiles", "fileexists", "getfilesize", "copyfile",
                    "renamefile", "getpackagename", "mkdir", "find"));
            put("bool", Arrays.asList(
                    "tostring", "tonumber", "not", "and", "or", "xor", "eq", "is", "toexpr"));
            put("bit", Arrays.asList(
                    "arshift", "band", "bnot", "bor", "bxor", "btest", "extract",
                    "lrotate", "lshift", "replace", "rrotate", "rshift"));
            put("bit32", Arrays.asList(
                    "arshift", "band", "bnot", "bor", "bxor", "btest", "extract",
                    "lrotate", "lshift", "replace", "rrotate", "rshift"));
            put("userdata", Arrays.asList(
                    "isuserdata", "islight", "type", "equals", "tostring", "address", "fromany"));
            put("thread", Arrays.asList(
                    "create", "createx"));
            put("vm", Arrays.asList(
                    "execute", "concat", "objlen", "equal", "lt", "le", "tonumber", "tointeger",
                    "gcinfo", "gettop", "memory", "gcstep", "gccollect", "newthread", "status",
                    "resume", "yield", "currentthread", "typename", "getci", "getstack", "gcstop",
                    "gcstart", "gcsetpause", "gcsetstepmul", "gcinc", "getregistry", "getglobalenv",
                    "setglobalenv", "isfunction", "isnil", "isboolean", "isnumber", "isstring",
                    "istable", "isuserdata", "isthread", "iscfunction", "rawget", "rawset",
                    "rawlen", "createtable", "newuserdata", "getmetatable", "setmetatable",
                    "error", "assert", "traceback"));
            put("translator", Arrays.asList("paser", "get"));
            put("logtable", Arrays.asList(
                    "onlog", "getlogpath", "setfilter", "clearfilter", "addinkey", "exckey",
                    "addinval", "exczval", "addinop", "exczop", "keyrange", "valrange",
                    "setdedup", "setunique", "resetdedup", "addinkeytype", "exckeytype",
                    "addinvaltype", "exczvaltype", "setintelligent", "getintelligent",
                    "setjnienv", "getjnienv", "setuserdata", "getuserdata"));
            put("libc", Arrays.asList(
                    /* 字符串函数 */
                    "strlen", "strcpy", "strncpy", "strcat", "strncat", "strcmp", "strncmp",
                    "strchr", "strrchr", "strstr", "strtok", "tolower", "toupper", "strlwr", "strupr",
                    "strspn", "strcspn", "strpbrk", "strdup", "strndup",

                    /* 字符串转换函数 */
                    "atoi", "atol", "atof", "strtol", "strtoul", "strtod",

                    /* 错误处理函数 */
                    "errno", "seterrno", "perror", "strerror",

                    /* 时间函数 */
                    "time", "gmtime", "localtime", "mktime", "asctime", "strftime",

                    /* 输入/输出函数 */
                    "getchar", "putchar", "printf", "scanf", "sscanf",

                    /* 文件操作函数 */
                    "fopen", "fclose", "fread", "fwrite", "fseek", "ftell", "rewind",

                    /* 文件系统函数 */
                    "mkdir", "rmdir", "chmod", "chown", "unlink", "rename", "stat",

                    /* 进程控制函数 */
                    "fork", "execve", "wait", "waitpid", "exit",

                    /* 信号处理函数 */
                    "signal", "kill", "raise",

                    /* 内存函数 */
                    "memset", "memcpy", "memmove", "memcmp", "memchr", "memcpyfast", "memmem", "memcmpfast",

                    /* 动态内存分配 */
                    "malloc", "calloc", "realloc", "free",

                    /* 数学函数 */
                    "abs", "labs", "llabs", "div", "ldiv", "lldiv", "sqrt", "pow", "sin", "cos", "tan",
                    "floor", "ceil", "round",

                    /* 工具函数 */
                    "qsort", "bsearch",

                    /* 随机函数 */
                    "rand", "srand",

                    /* CRC32和Hash函数 */
                    "crc32", "hash",

                    /* 位操作函数 */
                    "band", "bor", "bxor", "bnot", "bleft", "bright", "bswap", "btest", "bset", "bclear",

                    /* ARM特定函数 */
                    "getcpuid", "getpid", "gettid"));

            put("js", Arrays.asList(
                    /* 执行 */
                    "eval", "evalFile", "safeEval",

                    /* 全局变量 */
                    "set", "get",

                    /* 函数调用 */
                    "call",

                    /* 创建对象/数组 */
                    "newObject", "newArray", "createObject", "createArray",

                    /* 类型转换 */
                    "luaToJs", "jsToLua", "wrapJavaObject", "convertToJava", "toJavaList",

                    /* Java 类 */
                    "importClass",

                    /* JSON */
                    "toJson", "fromJson",

                    /* 正则表达式 */
                    "createRegExp", "testRegExp", "matchRegExp",

                    /* 对象属性 */
                    "setObjectProperty", "getObjectProperty",

                    /* 模块 */
                    "loadModule", "defineModule",

                    /* 时间 */
                    "getCurrentDate", "getCurrentTimeMillis", "getCurrentTimeSeconds",

                    /* 引擎配置 */
                    "setLanguageVersion", "setOptimizationLevel",

                    /* 引擎信息 */
                    "getVersion", "getEnvironmentInfo",

                    /* 生命周期 */
                    "close", "reset",

                    /* 函数注册 */
                    "registerLuaFunction", "regJsFun"));

            put("activity", Arrays.asList(
                /* 生命周期 */
                "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
                "onActivityResult", "onResult", "onError",

                /* 事件回调 */
                "onClick", "onTouch", "onLongClick", "onKeyDown", "onKeyUp", "onKeyLongPress",
                "onCreateOptionsMenu", "onOptionsItemSelected", "onItemClick", "onItemLongClick",

                /* 页面导航 */
                "finish", "recreate", "result",

                /* 启动Activity */
                "startActivity", "startActivityForResult", "newActivity",

                /* 布局 */
                "setContentView", "setTitle",

                /* 弹窗与提示 */
                "showToast", "toast", "showDialog",

                /* 线程与异步 */
                "runOnUiThread", "task", "thread", "timer", "newTask", "newThread", "newTimer",

                /* 数据存储 */
                "setSharedData", "getSharedData",

                /* 路径与目录 */
                "getLuaDir", "getLocalDir", "getExternalDir", "getCacheDir", "getFilesDir",
                "getIntent", "getPathFromUri",

                /* 资源加载 */
                "loadApp", "loadDex", "loadResources",

                /* 运行函数 */
                "runFunc", "sendError",

                /* 其他 */
                "getActionBar", "getSupportActionBar", "getWindow", "getResources",
                "getSystemService", "getApplicationContext"));

        // AI引擎相关方法
        put("ai", Arrays.asList(
                /* 生成文本相关方法 */
                "generate", "generateAsync", "generateStream", "gen", "genAsync", "genStream",

                /* MCP工具相关方法 */
                "registerMCPTool", "registerMCPToolFromLua", "getRegisteredToolNames", "getToolDetails",
                "removeMCPTool", "callMCPTool", "callMCPToolAsync", "syncMCPTools", "syncAllMCPTools",
                "syncMCPToolsAsync", "callTool", "callToolAsync", "regTool", "rmTool", "listTools", "toolInfo",

                /* 对话管理方法 */
                "createConversation", "switchConversation", "getCurrentConversation", "getConversation",
                "getAllConversations", "deleteConversation", "sendMessage", "sendSystemMessage",
                "createConv", "switchConv", "deleteConv", "sendMsg", "sendSysMsg",

                /* 配置相关方法 */
                "setSandbox", "listProviders", "listModels", "listModelsByProvider", "setModel", "getModel",
                "setProvider", "getProvider", "setApiKey", "getApiKey", "setBaseUrl", "getBaseUrl",
                "setMod", "getMod", "setProv", "getProv", "setKey",

                /* 工作区相关方法 */
                "setWorkspace", "getWorkspace", "enableWorkspace", "isWorkspaceEnabled",
                "setWork", "getWork", "enableWork",

                /* MCP配置方法 */
                "enableMCP", "isMCPEnabled", "addMCPServer", "removeMCPServer", "removeMCPServerById",
                "getMCPServers", "setMCPServers", "updateMCPServer", "getMCPServerStatus",

                /* 工具管理方法 */
                "disableBuiltinTool", "enableBuiltinTool", "disableAllBuiltinTools", "enableAllBuiltinTools",
                "disableFileOperationTools", "enableFileOperationTools", "disableCommandExecutionTools",
                "enableCommandExecutionTools", "isBuiltinToolDisabled", "disableTool", "enableTool",
                "disableAllTools", "enableAllTools", "disableFileTools", "enableFileTools", "disableCmdTool", "enableCmdTool",

                /* 其他方法 */
                "processAIResponse", "executeToolCall", "info", "reset", "close", "help"));
        }
    };
}
