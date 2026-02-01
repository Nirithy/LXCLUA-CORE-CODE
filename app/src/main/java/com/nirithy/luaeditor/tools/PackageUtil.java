package com.nirithy.luaeditor.tools;

import android.content.Context;
import com.nirithy.luaeditor.CompletionName;
import com.difierline.lua.lxclua.R;
import dalvik.system.DexFile;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

public final class PackageUtil {
    private static JSONObject packages;
    public static final PackageUtil INSTANCE = new PackageUtil();
    private static final HashMap<String, List<String>> classMap = new HashMap<>();
    private static List<String> classNames = new ArrayList<>();
    private static final Map<String, List<CompletionName>> cacheClassed = new LinkedHashMap<>();

    private PackageUtil() {
    }

    public final List<String> getClassNames() {
        return classNames;
    }

    public final void setClassNames(List<String> list) {
        classNames = list;
    }

    public static HashMap<String, List<String>> load(Context context) {
        if (packages != null) {
            return classMap;
        }
        try {
            INSTANCE.loadFromCache(context);
        } catch (Exception e) {
            INSTANCE.loadFromRawResource(context);
        }
        return classMap;
    }

    public static void load(Context context, String str) {
        if (packages != null) {
            return;
        }
        try {
            String content = readFileContent(new File(str));
            INSTANCE.initializePackages(context, content);
        } catch (Exception e) {
            load(context);
        }
    }

    private static String readFileContent(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }

    private void loadFromCache(Context context) throws Exception {
        File file = new File(context.getCacheDir(), "package_cache.json");
        if (!file.exists()) {
            throw new FileNotFoundException("Cache file not found");
        }
        String content = readFileContent(file);
        initializePackages(context, content);
    }

    private void loadFromRawResource(Context context) {
        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.android);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            inputStream.close();

            initializePackages(context, content.toString());
            
            // 保存到缓存文件
            File cacheFile = new File(context.getCacheDir(), "package_cache.json");
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8);
            writer.write(content.toString());
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializePackages(Context context, String str) {
        try {
            packages = new JSONObject(str);
            DexFile dexFile = new DexFile(context.getPackageCodePath());
            Enumeration<String> entries = dexFile.entries();
            
            while (entries.hasMoreElements()) {
                String className = entries.nextElement();
                JSONObject current = packages;
                String[] parts = className.split("\\.");
                
                for (String part : parts) {
                    if (current.has(part)) {
                        current = current.getJSONObject(part);
                    } else {
                        JSONObject newObj = new JSONObject();
                        current.put(part, newObj);
                        current = newObj;
                    }
                }
            }
            
            buildImports(packages, "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void buildImports(JSONObject jsonObject, String prefix) {
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                JSONObject child = jsonObject.getJSONObject(key);
                if (Character.isUpperCase(key.charAt(0))) {
                    List<String> list = classMap.computeIfAbsent(key, k -> new ArrayList<>());
                    list.add(prefix + key);
                }
                if (child.length() == 0) {
                    classNames.add(prefix + key);
                } else {
                    buildImports(child, prefix + key + ".");
                }
            } catch (Exception e) {
                // 忽略异常
            }
        }
    }

    public static List<String> fix(String name) {
        return classMap.get(name);
    }

    public static List<CompletionName> filter(String name) {
        if (packages == null) {
            return Collections.emptyList();
        }
        
        // 简化的过滤逻辑
        List<CompletionName> result = new ArrayList<>();
        // 实际过滤逻辑需要根据业务实现
        return result;
    }

    public static List<CompletionName> filterPackage(String name, String current) {
        if (packages == null) {
            return Collections.emptyList();
        }
        
        // 简化的过滤逻辑
        List<CompletionName> result = new ArrayList<>();
        // 实际过滤逻辑需要根据业务实现
        return result;
    }

    private List<CompletionName> getJavaMethods(Class<?> cls) {
        List<CompletionName> result = new ArrayList<>();
        for (Method method : cls.getMethods()) {
            String name = method.getName();
            if (name.contains("lambda")) {
                continue;
            }
            
            result.add(new CompletionName(
                method.getName(), 
                CompletionItemKind.Method, 
                " :method", 
                ClassMethodScanner.getParameterTypesAsString(method)
            ));
            
            Parameter[] parameters = method.getParameters();
            if (parameters.length == 0 && name.startsWith("get")) {
                String propName = name.substring(3);
                if (!propName.isEmpty()) {
                    propName = Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
                    result.add(new CompletionName(
                        propName, 
                        CompletionItemKind.Property, 
                        " :property", 
                        ClassMethodScanner.getParameterTypesAsString(method))
                    );
                }
            }
            
            if (parameters.length == 1 && name.startsWith("set")) {
                String propName = name.substring(3);
                if (propName.endsWith("Listener")) {
                    propName = propName.substring(0, propName.length() - 8);
                }
                if (!propName.isEmpty()) {
                    propName = Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
                    result.add(new CompletionName(
                        propName, 
                        CompletionItemKind.Field, 
                        " :field", 
                        ""
                    ));
                }
            }
        }
        return result;
    }

    private List<CompletionName> getJavaFields(Class<?> cls) {
        List<CompletionName> result = new ArrayList<>();
        for (Field field : cls.getFields()) {
            result.add(new CompletionName(
                field.getName(), 
                CompletionItemKind.Field, 
                " :field", 
                ""
            ));
        }
        return result;
    }
}