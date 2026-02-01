package com.nirithy.luaeditor.tools;

import com.nirithy.luaeditor.CompletionName;
import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class ClassMethodScanner {
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:75:0x0287 -> B:53:0x01d8). Please submit an issue!!! */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:77:0x028c -> B:59:0x0235). Please submit an issue!!! */
    public HashMap<String, HashMap<String, CompletionName>> scanClassesAndMethods(List<String> list, String str) {
        Method[] methods;
        HashMap<String, HashMap<String, CompletionName>> hashMap = new HashMap<>();
        ClassLoader classLoader = getClassLoader(str);
        for (String str2 : list) {
            try {
                Class<?> loadClass = classLoader.loadClass(str2);
                HashMap<String, CompletionName> hashMap2 = new HashMap<>();
                for (Method method : loadClass.getMethods()) {
                    // 修改这里：将固定的":method"改为实际的返回类型
                    String returnType = method.getReturnType().getSimpleName();
                    hashMap2.put(method.getName(), new CompletionName(method.getReturnType().getName(), CompletionItemKind.Method, returnType, getParameterTypesAsString(method)));
                }
                Field[] fields = loadClass.getFields();
                for (Field field : fields) {
                    hashMap2.put(field.getName(), new CompletionName(field.getType().getName(), CompletionItemKind.Field, " :field", ""));
                }
                for (Field field : fields) {
                    String name = field.getName();
                    String str3 = name.substring(0, 1).toUpperCase() + name.substring(1);
                    Method getter = null;
                    // Try to get the getter: getX
                    try {
                        getter = loadClass.getMethod("get" + str3, new Class[0]);
                    } catch (NoSuchMethodException e) {
                        // If it's boolean, try isX
                        if (field.getType() == Boolean.TYPE) {
                            try {
                                getter = loadClass.getMethod("is" + str3, new Class[0]);
                            } catch (NoSuchMethodException e2) {
                                // Ignore
                            }
                        }
                    }
                    if (getter != null) {
                        // We found a getter, use it to create the property
                        String returnType = getter.getReturnType().getSimpleName();
                        hashMap2.put(name, new CompletionName(getter.getReturnType().getName(), CompletionItemKind.Property, returnType, getParameterTypesAsString(getter)));
                    } else {
                        // Try setter
                        try {
                            Method setter = loadClass.getMethod("set" + str3, field.getType());
                            String returnType = setter.getReturnType().getSimpleName();
                            hashMap2.put(name, new CompletionName(setter.getReturnType().getName(), CompletionItemKind.Property, returnType, getParameterTypesAsString(setter)));
                        } catch (NoSuchMethodException e) {
                            // Ignore, no setter either -> skip
                        }
                    }
                }
                hashMap.put(str2, hashMap2);
            } catch (ClassNotFoundException | IllegalAccessError | NoClassDefFoundError | NoSuchMethodError e5) {
                System.err.println("Failed to load class: " + str2);
                e5.printStackTrace();
            }
        }
        return hashMap;
    }

    // 其余代码保持不变...
    private ClassLoader getClassLoader(String str) {
        if (str == null || str.isEmpty()) {
            return getClass().getClassLoader();
        }
        File file = new File("/data/data/com.difierline.lua.lxclua/dex");
        file.mkdirs();
        return new DexClassLoader(str, file.getAbsolutePath(), null, getClass().getClassLoader());
    }

    public static String getParameterTypesAsString(Method method) {
        ArrayList arrayList = new ArrayList();
        for (Type type : method.getGenericParameterTypes()) {
            arrayList.add(typeToString(type));
        }
        return String.join(", ", arrayList);
    }

    private static String typeToString(Type type) {
        if (type instanceof Class) {
            return ((Class) type).getName();
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            StringBuilder sb = new StringBuilder();
            sb.append(((Class) parameterizedType.getRawType()).getName());
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length > 0) {
                sb.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    sb.append(typeToString(actualTypeArguments[i]));
                    if (i < actualTypeArguments.length - 1) {
                        sb.append(", ");
                    }
                }
                sb.append(">");
            }
            return sb.toString();
        } else if (type instanceof GenericArrayType) {
            return typeToString(((GenericArrayType) type).getGenericComponentType()) + "[]";
        } else if (type instanceof TypeVariable) {
            return ((TypeVariable) type).getName();
        } else {
            if (type instanceof WildcardType) {
                return "?";
            }
            return type.toString();
        }
    }

    // 修复后的方法：添加递归深度限制
    public static String getReturnType(HashMap<String, List<String>> hashMap, HashMap<String, HashMap<String, CompletionName>> hashMap2, String str, Map<String, String> map, HashMap<String, List<String>> hashMap3) {
        return getReturnType(hashMap, hashMap2, str, map, hashMap3, 0, new HashSet<>());
    }

    private static String getReturnType(HashMap<String, List<String>> hashMap, HashMap<String, HashMap<String, CompletionName>> hashMap2, String str, Map<String, String> map, HashMap<String, List<String>> hashMap3, int depth, Set<String> visited) {
        // 限制递归深度，防止无限递归
        if (depth > 50) {
            return "nullclass";
        }
        
        // 检查循环引用
        if (visited.contains(str)) {
            return "nullclass";
        }
        
        String str2;
        int i;
        String str3;
        HashMap<String, CompletionName> hashMap4;
        String[] split = str.split("\\.");
        int i2 = 1;
        int i3 = 0;
        String str4 = null;
        
        while (true) {
            str2 = null;
            str3 = str4;
            if (i2 > split.length) {
                break;
            }
            StringBuilder sb = new StringBuilder(split[0]);
            for (int i4 = 1; i4 < i2; i4++) {
                sb.append(".");
                sb.append(split[i4]);
            }
            String sb2 = sb.toString();
            if (hashMap.get(sb2) != null) {
                str4 = hashMap.get(sb2).get(0);
                if (sb2.startsWith("R.")) {
                    str4 = "com.difierline.lua.lxclua." + sb2;
                }
            } else if (map.get(sb2) != null) {
                try {
                    // 添加当前字符串到已访问集合
                    Set<String> newVisited = new HashSet<>(visited);
                    newVisited.add(str);
                    str4 = getReturnType(hashMap, hashMap2, map.get(sb2), map, hashMap3, depth + 1, newVisited);
                } catch (Exception e) {
                    if (hashMap2.get(map.get(sb2)) != null) {
                        str4 = map.get(sb2);
                    }
                }
            } else {
                if (hashMap2.get(sb2) != null) {
                    i3 = i2;
                    str4 = sb2;
                }
                i2++;
            }
            i3 = i2;
            i2++;
        }
        
        for (i = i3; i < split.length; i++) {
            if (str3 == null || (hashMap4 = hashMap2.get(str3)) == null || !hashMap4.containsKey(split[i])) {
                return "nullclass";
            }
            str2 = split[i];
            if (!str2.startsWith("set") || !hashMap4.get(str2).getName().equals("void")) {
                str3 = hashMap4.get(str2).getName();
            }
        }
        
        return (str2 == null && str3 == null) ? "nullclass" : str3;
    }

    public String getReturnType(String str, HashMap<String, Object> hashMap, HashMap<String, String> hashMap2, HashMap<String, String> hashMap3) {
        String str2;
        String str3;
        HashMap hashMap4;
        String[] split = str.split("\\.");
        int i = 0;
        int i2 = 0;
        if (hashMap2 != null && hashMap2.containsKey(split[0])) {
            str2 = hashMap2.get(split[0]);
        } else {
            str2 = (hashMap3 == null || !hashMap3.containsKey(split[0])) ? "nullclass" : hashMap3.get(split[0]);
        }
        HashMap hashMap5 = null;
        String str4 = str2;
        if (!str2.equals("nullclass")) {
            HashMap hashMap6 = null;
            if (hashMap.get(str2) != null) {
                hashMap6 = null;
                if (hashMap.get(str2) instanceof HashMap) {
                    hashMap6 = (HashMap) hashMap.get(str2);
                }
            }
            int i3 = 1;
            while (true) {
                i = i2;
                hashMap5 = hashMap6;
                str4 = str2;
                if (i3 > str.length()) {
                    break;
                }
                int i4 = i2;
                HashMap hashMap7 = hashMap6;
                String str5 = str2;
                if (hashMap6.get(split[i3]) != null) {
                    i4 = i2;
                    hashMap7 = hashMap6;
                    str5 = str2;
                    if (hashMap6.get(split[i3]) instanceof HashMap) {
                        hashMap7 = (HashMap) hashMap6.get(split[i3]);
                        str5 = str2 + "$" + split[i3];
                        i4 = i3;
                    }
                }
                i3++;
                i2 = i4;
                hashMap6 = hashMap7;
                str2 = str5;
            }
        }
        String str6 = str4;
        if (str4 != null) {
            while (true) {
                str6 = str4;
                if (i >= str.length()) {
                    break;
                }
                if (hashMap5.containsKey(split[i])) {
                    hashMap4 = hashMap5;
                    str3 = str4;
                    if (!str4.equals("void")) {
                        hashMap4 = hashMap5;
                        str3 = str4;
                        if (hashMap5.get(split[i]) instanceof CompletionName) {
                            str3 = ((CompletionName) getMap(hashMap, str4.split("\\$")).get(split[i])).getName();
                            hashMap4 = getMap(hashMap, str3.split("\\$"));
                        }
                    }
                } else {
                    str3 = "nullclass";
                    hashMap4 = hashMap5;
                }
                i++;
                hashMap5 = hashMap4;
                str4 = str3;
            }
        }
        return str6;
    }

    public static List<String> getClassNames(String str) {
        ArrayList arrayList = new ArrayList();
        try {
            Enumeration<String> entries = new DexFile(str).entries();
            while (entries.hasMoreElements()) {
                arrayList.add(entries.nextElement());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return arrayList;
    }

    private HashMap getMap(HashMap hashMap, String[] strArr) {
        Object obj;
        for (int i = 0; i < strArr.length; i++) {
            if (hashMap.containsKey(strArr[i])) {
                obj = hashMap.get(strArr[i]);
            } else {
                hashMap.put(strArr[i], new HashMap());
                obj = hashMap.get(strArr[i]);
            }
            hashMap = (HashMap) obj;
        }
        return hashMap;
    }
}
