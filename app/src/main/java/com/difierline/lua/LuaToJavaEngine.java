package com.difierline.lua;

import com.luajava.LuaState;
import com.luajava.LuaObject;
import com.luajava.LuaException;
import com.luajava.LuaToJava;
import com.luajava.CompiledFunction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * LuaToJava 引擎包装类，用于将 Lua 代码编译为 Java 代码的功能暴露给 Lua
 * 提供 Lua 代码到 Java 代码的转换和编译功能
 */
public class LuaToJavaEngine {
    private LuaContext luaContext;
    private LuaToJava converter;
    private Map<String, Object> options;

    /**
     * 构造函数
     * @param luaContext Lua 上下文
     */
    public LuaToJavaEngine(LuaContext luaContext) {
        this.luaContext = luaContext;
        this.converter = new LuaToJava();
        this.options = new HashMap<>();
        initDefaultOptions();
    }

    /**
     * 构造函数（带选项）
     * @param luaContext Lua 上下文
     * @param options 配置选项表
     */
    public LuaToJavaEngine(LuaContext luaContext, LuaObject options) {
        this.luaContext = luaContext;
        this.converter = new LuaToJava();
        this.options = new HashMap<>();
        initDefaultOptions();
        if (options != null) {
            applyOptions(options);
        }
    }

    /**
     * 初始化默认选项
     */
    private void initDefaultOptions() {
        options.put("className", "LuaCompiled");
        options.put("moduleName", "luacompiled");
        options.put("usePureJava", false);
        options.put("obfuscate", false);
        options.put("stringEncryption", false);
        options.put("debugMode", true);
    }

    /**
     * 应用 Lua 表中的选项
     * @param optionsTable Lua 选项表
     */
    private void applyOptions(LuaObject optionsTable) {
        try {
            LuaState L = optionsTable.getLuaState();
            optionsTable.push();
            
            if (L.type(-1) != LuaState.LUA_TTABLE) {
                L.pop(1);
                return;
            }

            L.pushString("className");
            L.getTable(-2);
            if (L.type(-1) == LuaState.LUA_TSTRING) {
                options.put("className", L.toString(-1));
                converter.setClassName(L.toString(-1));
            }
            L.pop(1);

            L.pushString("moduleName");
            L.getTable(-2);
            if (L.type(-1) == LuaState.LUA_TSTRING) {
                options.put("moduleName", L.toString(-1));
                converter.setModuleName(L.toString(-1));
            }
            L.pop(1);

            L.pushString("usePureJava");
            L.getTable(-2);
            if (L.type(-1) == LuaState.LUA_TBOOLEAN) {
                boolean usePureJava = L.toBoolean(-1);
                options.put("usePureJava", usePureJava);
                converter.setUsePureJava(usePureJava);
            }
            L.pop(1);

            L.pushString("obfuscate");
            L.getTable(-2);
            if (L.type(-1) == LuaState.LUA_TBOOLEAN) {
                boolean obfuscate = L.toBoolean(-1);
                options.put("obfuscate", obfuscate);
                converter.setObfuscate(obfuscate);
            }
            L.pop(1);

            L.pushString("stringEncryption");
            L.getTable(-2);
            if (L.type(-1) == LuaState.LUA_TBOOLEAN) {
                boolean stringEncryption = L.toBoolean(-1);
                options.put("stringEncryption", stringEncryption);
                converter.setStringEncryption(stringEncryption);
            }
            L.pop(1);

            L.pushString("debugMode");
            L.getTable(-2);
            if (L.type(-1) == LuaState.LUA_TBOOLEAN) {
                options.put("debugMode", L.toBoolean(-1));
            }
            L.pop(1);

            L.pop(1);
        } catch (Exception e) {
            throw new RuntimeException("应用选项失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置生成的 Java 类名
     * @param className 类名
     */
    public void setClassName(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("类名不能为空");
        }
        options.put("className", className);
        converter.setClassName(className);
    }

    /**
     * 设置生成的模块名
     * @param moduleName 模块名
     */
    public void setModuleName(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) {
            throw new IllegalArgumentException("模块名不能为空");
        }
        options.put("moduleName", moduleName);
        converter.setModuleName(moduleName);
    }

    /**
     * 设置是否使用纯 Java 模式（不使用 JNI）
     * @param usePureJava 是否使用纯 Java
     */
    public void setUsePureJava(boolean usePureJava) {
        options.put("usePureJava", usePureJava);
        converter.setUsePureJava(usePureJava);
    }

    /**
     * 设置是否混淆生成的代码
     * @param obfuscate 是否混淆
     */
    public void setObfuscate(boolean obfuscate) {
        options.put("obfuscate", obfuscate);
        converter.setObfuscate(obfuscate);
    }

    /**
     * 设置是否加密字符串常量
     * @param encrypt 是否加密
     */
    public void setStringEncryption(boolean encrypt) {
        options.put("stringEncryption", encrypt);
        converter.setStringEncryption(encrypt);
    }

    /**
     * 设置调试模式
     * @param debug 是否启用调试模式
     */
    public void setDebugMode(boolean debug) {
        options.put("debugMode", debug);
    }

    /**
     * 编译 Lua 代码为 Java 代码
     * @param luaCode Lua 源代码
     * @return 生成的 Java 代码
     */
    public String compile(String luaCode) {
        if (luaCode == null || luaCode.isEmpty()) {
            throw new IllegalArgumentException("Lua 代码不能为空");
        }
        try {
            return converter.compile(luaCode);
        } catch (Exception e) {
            throw new RuntimeException("编译 Lua 代码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 编译 Lua 文件为 Java 代码
     * @param filePath Lua 文件路径（相对于 Lua 脚本目录）
     * @return 生成的 Java 代码
     */
    public String compileFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        try {
            String fullPath = luaContext.getLuaDir() + "/" + filePath;
            File file = new File(fullPath);
            
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(bytes);
            }
            String luaCode = new String(bytes, StandardCharsets.UTF_8);
            
            return converter.compile(luaCode);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + filePath, e);
        } catch (Exception e) {
            throw new RuntimeException("编译 Lua 文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 编译 Lua 代码并返回完整信息
     * @param luaCode Lua 源代码
     * @return 包含编译结果的 Map
     */
    public Map<String, Object> compileWithInfo(String luaCode) {
        if (luaCode == null || luaCode.isEmpty()) {
            throw new IllegalArgumentException("Lua 代码不能为空");
        }
        
        Map<String, Object> result = new HashMap<>();
        try {
            String javaCode = converter.compile(luaCode);
            
            result.put("success", true);
            result.put("javaCode", javaCode);
            result.put("className", options.get("className"));
            result.put("moduleName", options.get("moduleName"));
            result.put("options", new HashMap<>(options));
            
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 编译 Lua 代码并保存到文件
     * @param luaCode Lua 源代码
     * @param outputPath 输出文件路径（相对于 Lua 脚本目录）
     * @return 保存的文件路径
     */
    public String compileToFile(String luaCode, String outputPath) {
        if (luaCode == null || luaCode.isEmpty()) {
            throw new IllegalArgumentException("Lua 代码不能为空");
        }
        if (outputPath == null || outputPath.isEmpty()) {
            throw new IllegalArgumentException("输出路径不能为空");
        }
        
        try {
            String javaCode = converter.compile(luaCode);
            String fullPath = luaContext.getLuaDir() + "/" + outputPath;
            
            File outputFile = new File(fullPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            java.io.FileWriter writer = new java.io.FileWriter(outputFile);
            writer.write(javaCode);
            writer.close();
            
            return fullPath;
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败: " + outputPath, e);
        } catch (Exception e) {
            throw new RuntimeException("编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取当前配置选项
     * @return 配置选项的 Map
     */
    public Map<String, Object> getOptions() {
        return new HashMap<>(options);
    }

    /**
     * 获取引擎版本
     * @return 版本信息
     */
    public String version() {
        return "LuaToJava 1.0.0";
    }

    /**
     * 获取引擎信息
     * @return 引擎信息 Map
     */
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("version", version());
        info.put("options", getOptions());
        return info;
    }

    /**
     * 重置引擎状态和选项
     */
    public void reset() {
        this.converter = new LuaToJava();
        this.options = new HashMap<>();
        initDefaultOptions();
    }

    /**
     * 批量编译多个 Lua 代码片段
     * @param codeTable Lua 表，包含多个代码片段
     * @return 编译结果列表
     */
    public List<Map<String, Object>> compileBatch(LuaObject codeTable) {
        if (codeTable == null) {
            throw new IllegalArgumentException("代码表不能为空");
        }
        
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            LuaState L = codeTable.getLuaState();
            codeTable.push();
            
            if (L.type(-1) != LuaState.LUA_TTABLE) {
                L.pop(1);
                throw new IllegalArgumentException("参数必须是表类型");
            }

            int len = L.objLen(-1);
            for (int i = 1; i <= len; i++) {
                L.pushNumber(i);
                L.getTable(-2);
                
                if (L.type(-1) == LuaState.LUA_TSTRING) {
                    String code = L.toString(-1);
                    results.add(compileWithInfo(code));
                }
                L.pop(1);
            }
            
            L.pop(1);
            return results;
        } catch (Exception e) {
            throw new RuntimeException("批量编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分析 Lua 代码结构
     * @param luaCode Lua 源代码
     * @return 代码结构信息
     */
    public Map<String, Object> analyze(String luaCode) {
        if (luaCode == null || luaCode.isEmpty()) {
            throw new IllegalArgumentException("Lua 代码不能为空");
        }
        
        Map<String, Object> analysis = new HashMap<>();
        try {
            List<String> functions = new ArrayList<>();
            List<String> variables = new ArrayList<>();
            List<String> tables = new ArrayList<>();
            
            java.util.regex.Pattern funcPattern = java.util.regex.Pattern.compile(
                "(?:local\\s+)?function\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*\\("
            );
            java.util.regex.Matcher funcMatcher = funcPattern.matcher(luaCode);
            while (funcMatcher.find()) {
                functions.add(funcMatcher.group(1));
            }

            java.util.regex.Pattern varPattern = java.util.regex.Pattern.compile(
                "(?:local\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*="
            );
            java.util.regex.Matcher varMatcher = varPattern.matcher(luaCode);
            while (varMatcher.find()) {
                String varName = varMatcher.group(1);
                if (!functions.contains(varName)) {
                    variables.add(varName);
                }
            }

            java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile(
                "(?:local\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*\\{"
            );
            java.util.regex.Matcher tableMatcher = tablePattern.matcher(luaCode);
            while (tableMatcher.find()) {
                tables.add(tableMatcher.group(1));
            }
            
            analysis.put("success", true);
            analysis.put("functions", functions);
            analysis.put("variables", variables);
            analysis.put("tables", tables);
            analysis.put("lineCount", luaCode.split("\n").length);
            analysis.put("charCount", luaCode.length());
            
            return analysis;
        } catch (Exception e) {
            analysis.put("success", false);
            analysis.put("error", e.getMessage());
            return analysis;
        }
    }

    /**
     * 验证 Lua 代码语法
     * @param luaCode Lua 源代码
     * @return 验证结果
     */
    public Map<String, Object> validate(String luaCode) {
        Map<String, Object> result = new HashMap<>();
        
        if (luaCode == null || luaCode.isEmpty()) {
            result.put("valid", false);
            result.put("error", "代码不能为空");
            return result;
        }
        
        try {
            LuaState L = luaContext.getLuaState();
            int loadResult = L.LloadString(luaCode);
            
            if (loadResult == 0) {
                L.pop(1);
                result.put("valid", true);
                result.put("error", null);
            } else {
                String error = L.toString(-1);
                L.pop(1);
                result.put("valid", false);
                result.put("error", error);
            }
            
            return result;
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取 Lua 上下文
     * @return Lua 上下文
     */
    public LuaContext getLuaContext() {
        return luaContext;
    }

    /**
     * 获取底层转换器
     * @return LuaToJava 转换器实例
     */
    public LuaToJava getConverter() {
        return converter;
    }
}
