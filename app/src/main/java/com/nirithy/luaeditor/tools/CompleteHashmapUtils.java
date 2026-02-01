package com.nirithy.luaeditor.tools;

import android.content.Context;
import com.nirithy.luaeditor.CompletionName;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompleteHashmapUtils {

    /* ===============================
     * 版本1：HashMap<String, HashMap<String, CompletionName>>
     * =============================== */

    public static void saveHashMapToFile(
            Context context,
            HashMap<String, HashMap<String, CompletionName>> hashMap,
            String fileName) {
        File dir = context.getExternalCacheDir();
        if (dir == null) return;
        File file = new File(dir, fileName);
        try (DataOutputStream dos =
                new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {

            dos.writeInt(hashMap.size());
            for (Map.Entry<String, HashMap<String, CompletionName>> outer : hashMap.entrySet()) {
                dos.writeUTF(outer.getKey());
                HashMap<String, CompletionName> inner = outer.getValue();
                dos.writeInt(inner.size());
                for (Map.Entry<String, CompletionName> e : inner.entrySet()) {
                    dos.writeUTF(e.getKey());
                    CompletionName cn = e.getValue();
                    dos.writeUTF(cn.getName());
                    dos.writeUTF(cn.getType().name());
                    dos.writeUTF(cn.getDescription());
                    dos.writeUTF(cn.getGeneric()); // ✅ 保存参数类型
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, HashMap<String, CompletionName>> loadHashMapFromFile(
            Context context, String fileName) {
        File dir = context.getExternalCacheDir();
        if (dir == null) return null;
        File file = new File(dir, fileName);
        if (!file.exists()) return null;

        HashMap<String, HashMap<String, CompletionName>> result = new HashMap<>();
        try (DataInputStream dis =
                new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {

            int outerSize = dis.readInt();
            for (int i = 0; i < outerSize; i++) {
                String outerKey = dis.readUTF();
                int innerSize = dis.readInt();
                HashMap<String, CompletionName> innerMap = new HashMap<>();
                for (int j = 0; j < innerSize; j++) {
                    String innerKey = dis.readUTF();
                    String name = dis.readUTF();
                    CompletionItemKind type = CompletionItemKind.valueOf(dis.readUTF());
                    String desc = dis.readUTF();
                    String generic = dis.readUTF(); // ✅ 读取参数类型
                    innerMap.put(innerKey, new CompletionName(name, type, desc, generic));
                }
                result.put(outerKey, innerMap);
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /* ===============================
     * 版本2：HashMap<String, List<String>>
     * =============================== */

    public static void saveHashMapToFile2(
            Context context, HashMap<String, List<String>> hashMap, String fileName) {
        File dir = context.getExternalCacheDir();
        if (dir == null) return;
        File file = new File(dir, fileName);
        try (DataOutputStream dos =
                new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {

            dos.writeInt(hashMap.size());
            for (Map.Entry<String, List<String>> e : hashMap.entrySet()) {
                dos.writeUTF(e.getKey());
                List<String> list = e.getValue();
                dos.writeInt(list.size());
                for (String s : list) {
                    dos.writeUTF(s);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, List<String>> loadHashMapFromFile2(
            Context context, String fileName) {
        File dir = context.getExternalCacheDir();
        if (dir == null) return null;
        File file = new File(dir, fileName);
        if (!file.exists()) return null;

        HashMap<String, List<String>> result = new HashMap<>();
        try (DataInputStream dis =
                new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {

            int size = dis.readInt();
            for (int i = 0; i < size; i++) {
                String key = dis.readUTF();
                int listSize = dis.readInt();
                List<String> list = new ArrayList<>(listSize);
                for (int j = 0; j < listSize; j++) {
                    list.add(dis.readUTF());
                }
                result.put(key, list);
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
