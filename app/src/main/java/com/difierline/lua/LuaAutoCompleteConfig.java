package com.difierline.lua;

import com.luajava.*;
import com.nirithy.luaeditor.config.AutoCompletePackages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LuaAutoCompleteConfig extends JavaFunction {

    private LuaContext mLuaContext;

    public LuaAutoCompleteConfig(LuaContext luaContext, LuaState L) {
        super(L);
        mLuaContext = luaContext;
    }

    @Override
    public int execute() throws LuaException {
        int top = L.getTop();
        if (top < 2) {
            mLuaContext.sendMsg("complete_config: missing command");
            return 0;
        }

        String command = L.toString(2);

        switch (command) {
            case "add_keyword":
                return addKeyword();
            case "remove_keyword":
                return removeKeyword();
            case "add_package_function":
                return addPackageFunction();
            case "remove_package_function":
                return removePackageFunction();
            case "get_keywords":
                return getKeywords();
            case "get_packages":
                return getPackages();
            case "clear_package":
                return clearPackage();
            default:
                mLuaContext.sendMsg("complete_config: unknown command: " + command);
                return 0;
        }
    }

    private int addKeyword() throws LuaException {
        if (L.getTop() < 3) {
            mLuaContext.sendMsg("complete_config: add_keyword: missing keyword");
            return 0;
        }

        String keyword = L.toString(3);
        if (!AutoCompletePackages.DEFAULT_KEYWORDS.contains(keyword)) {
            AutoCompletePackages.DEFAULT_KEYWORDS.add(keyword);
        }

        L.pushBoolean(true);
        return 1;
    }

    private int removeKeyword() throws LuaException {
        if (L.getTop() < 3) {
            mLuaContext.sendMsg("complete_config: remove_keyword: missing keyword");
            return 0;
        }

        String keyword = L.toString(3);
        boolean removed = AutoCompletePackages.DEFAULT_KEYWORDS.remove(keyword);

        L.pushBoolean(removed);
        return 1;
    }

    private int addPackageFunction() throws LuaException {
        if (L.getTop() < 4) {
            mLuaContext.sendMsg("complete_config: add_package_function: missing package or function");
            return 0;
        }

        String packageName = L.toString(3);
        String functionName = L.toString(4);

        List<String> functions = AutoCompletePackages.DEFAULT_PACKAGES.get(packageName);
        if (functions == null) {
            functions = new ArrayList<>();
            AutoCompletePackages.DEFAULT_PACKAGES.put(packageName, functions);
        }

        if (!functions.contains(functionName)) {
            functions.add(functionName);
        }

        L.pushBoolean(true);
        return 1;
    }

    private int removePackageFunction() throws LuaException {
        if (L.getTop() < 4) {
            mLuaContext.sendMsg("complete_config: remove_package_function: missing package or function");
            return 0;
        }

        String packageName = L.toString(3);
        String functionName = L.toString(4);

        List<String> functions = AutoCompletePackages.DEFAULT_PACKAGES.get(packageName);
        boolean removed = false;
        if (functions != null) {
            removed = functions.remove(functionName);
        }

        L.pushBoolean(removed);
        return 1;
    }
    private int getKeywords() throws LuaException {
        L.newTable();
        List<String> keywords = AutoCompletePackages.DEFAULT_KEYWORDS;
        for (int i = 0; i < keywords.size(); i++) {
            L.pushString(keywords.get(i));
            L.setField(-2, String.valueOf(i + 1));
        }
        return 1;
    }

    private int getPackages() throws LuaException {
        L.newTable();
        Map<String, List<String>> packages = AutoCompletePackages.DEFAULT_PACKAGES;
        for (Map.Entry<String, List<String>> entry : packages.entrySet()) {
            String packageName = entry.getKey();
            List<String> functions = entry.getValue();

            L.newTable();
            for (int i = 0; i < functions.size(); i++) {
                L.pushString(functions.get(i));
                L.setField(-2, String.valueOf(i + 1));
            }
            L.setField(-2, packageName);
        }
        return 1;
    }
    private int clearPackage() throws LuaException {
        if (L.getTop() < 3) {
            mLuaContext.sendMsg("complete_config: clear_package: missing package name");
            return 0;
        }

        String packageName = L.toString(3);
        boolean removed = AutoCompletePackages.DEFAULT_PACKAGES.remove(packageName) != null;

        L.pushBoolean(removed);
        return 1;
    }
}