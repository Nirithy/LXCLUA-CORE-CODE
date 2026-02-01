package com.nirithy.luaeditor.lualanguage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Bundle;
import android.util.Log;
import com.nirithy.luaeditor.CompletionHelper;
import com.nirithy.luaeditor.CompletionName;
import com.nirithy.luaeditor.MyIdentifierAutoComplete;
import com.nirithy.luaeditor.MyPrefixChecker;
import com.nirithy.luaeditor.StringAutoComplete;
import com.nirithy.luaeditor.tools.parser.LuaLexer;
import com.nirithy.luaeditor.tools.parser.LuaParser;

import com.nirithy.luaeditor.tools.parser.Token;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem;


import com.difierline.lua.LuaUtil;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.QuickQuoteHandler;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem;
import io.github.rosemoe.sora.lang.completion.SnippetDescription;
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet;
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.StylesUtils;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.widget.SymbolPairMatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.io.File;

import com.nirithy.luaeditor.config.AutoCompletePackages;

import com.nirithy.luaeditor.format.LuaFormatter;

public class LuaLanguage implements Language {
    private MyIdentifierAutoComplete autoComplete;
    HashMap<String, HashMap<String, CompletionName>> baseMap;
    HashMap<String, List<String>> classMap;
    String[] androidClasses;
    private final LuaQuoteHandler javaQuoteHandler;
    private final LuaIncrementalAnalyzeManager manager;
    HashMap<String, String> map;
    private final NewlineHandler[] newlineHandlers;
    private static final CodeSnippet FOR_SNIPPET =
            CodeSnippetParser.parse("for i = 1,num do\n    $0\nend");
    private static final CodeSnippet STATIC_CONST_SNIPPET =
            CodeSnippetParser.parse(
                    "private final static ${1:type} ${2/(.*)/${1:/upcase}/} = ${3:value};");
    private static final CodeSnippet CLIPBOARD_SNIPPET =
            CodeSnippetParser.parse("${1:${CLIPBOARD}}");
    private boolean showFullParameterType = false;
    private boolean hexColorHighlightEnabled = false;

    private static final List<String> DEFAULT_KEYWORDS = AutoCompletePackages.DEFAULT_KEYWORDS;

    private static final Map<String, List<String>> DEFAULT_PACKAGES = AutoCompletePackages.DEFAULT_PACKAGES;

    public int getInterruptionLevel() {
        return 0;
    }

    public boolean useTab() {
        return false;
    }

    @Override
    public Formatter getFormatter() {
        return new LuaFormatter();
    }

    public void setNames(List<String> keywordList) {
        if (keywordList != null) {
            String[] keywords = keywordList.toArray(new String[0]);
            autoComplete.updateKeywords(keywords);
        }
    }

    public void setShowFullParameterType(boolean showFull) {
        this.showFullParameterType = showFull;
        if (autoComplete != null) {
            autoComplete.setShowFullParameterType(showFull);
        }
    }

    public void addPackage(String packageName, List<String> functions) {
        autoComplete.addKeyword(packageName);
        autoComplete.addPackage(packageName, functions);
    }

    public void setCompletionCaseSensitive(boolean caseSensitive) {
        if (autoComplete != null) {
            autoComplete.setCaseSensitive(caseSensitive);
        }
    }

    public void setHexColorHighlightEnabled(boolean enabled) {
        this.hexColorHighlightEnabled = enabled;
        if (manager != null) {
            manager.setHexColorHighlightEnabled(enabled);
        }
    }

    public void releaseMemory() {
        if (manager != null) {
            manager.releaseMemory();
        }
    }

    public LuaLanguage() {
        this.baseMap = new HashMap<>();
        this.classMap = new HashMap<>();
        this.javaQuoteHandler = new LuaQuoteHandler();
        this.map = new HashMap<>();
        this.newlineHandlers = new NewlineHandler[] {new BraceHandler(this)};

        this.autoComplete =
                new MyIdentifierAutoComplete(DEFAULT_KEYWORDS.toArray(new String[0]), baseMap);

        this.manager = new LuaIncrementalAnalyzeManager();
        manager.setHexColorHighlightEnabled(this.hexColorHighlightEnabled);

        for (Map.Entry<String, List<String>> entry : DEFAULT_PACKAGES.entrySet()) {
            addPackage(entry.getKey(), entry.getValue());
        }
    }

    public LuaLanguage(
            HashMap<String, HashMap<String, CompletionName>> hashMap,
            HashMap<String, List<String>> hashMap2) {

        this.javaQuoteHandler = new LuaQuoteHandler();
        this.map = new HashMap<>();
        this.newlineHandlers = new NewlineHandler[] {new BraceHandler(this)};

        this.autoComplete =
                new MyIdentifierAutoComplete(DEFAULT_KEYWORDS.toArray(new String[0]), hashMap);
        this.manager = new LuaIncrementalAnalyzeManager();
        manager.setHexColorHighlightEnabled(this.hexColorHighlightEnabled);

        this.baseMap = hashMap;
        this.classMap = hashMap2;

        for (Map.Entry<String, List<String>> entry : DEFAULT_PACKAGES.entrySet()) {
            addPackage(entry.getKey(), entry.getValue());
        }
    }

    public LuaLanguage(
            HashMap<String, HashMap<String, CompletionName>> hashMap,
            HashMap<String, List<String>> hashMap2,
            String[] androidClasses) {

        this.javaQuoteHandler = new LuaQuoteHandler();
        this.map = new HashMap<>();
        this.newlineHandlers = new NewlineHandler[] {new BraceHandler(this)};

        this.autoComplete =
                new MyIdentifierAutoComplete(DEFAULT_KEYWORDS.toArray(new String[0]), hashMap);
        this.manager = new LuaIncrementalAnalyzeManager();
        manager.setHexColorHighlightEnabled(this.hexColorHighlightEnabled);

        this.baseMap = hashMap;
        this.classMap = hashMap2;

        for (Map.Entry<String, List<String>> entry : DEFAULT_PACKAGES.entrySet()) {
            addPackage(entry.getKey(), entry.getValue());
        }

        Set<String> classNameSet =
                (androidClasses != null) ? new HashSet<>(Arrays.asList(androidClasses)) : null;

        this.manager.setClassMap(classNameSet); // 设置类名集合
    }

    public AnalyzeManager getAnalyzeManager() {
        return this.manager;
    }

    public QuickQuoteHandler getQuickQuoteHandler() {
        return this.javaQuoteHandler;
    }

    public void destroy() {
        this.autoComplete = null;
    }

    /**
     * 字符串补全实例
     */
    private StringAutoComplete stringAutoComplete = StringAutoComplete.Companion.getInstance();
    
    /**
     * 设置可导入的路径列表（用于字符串补全）
     * @param paths 路径列表
     */
    public void setImportablePaths(java.util.Collection<String> paths) {
        if (stringAutoComplete != null && paths != null) {
            stringAutoComplete.setImportablePaths(paths);
        }
    }

    public void requireAutoComplete(
            ContentReference contentReference,
            CharPosition charPosition,
            CompletionPublisher completionPublisher,
            Bundle bundle) {
        try {
            // 检查是否在字符串内
            Character quoteChar = stringAutoComplete.isInsideString(contentReference, charPosition);
            if (quoteChar != null) {
                // 在字符串内，使用字符串补全
                String stringPrefix = stringAutoComplete.getStringPrefix(contentReference, charPosition);
                String line = contentReference.getLine(charPosition.line).toString();
                String contextKeyword = stringAutoComplete.detectContextKeyword(line, charPosition.column);
                
                // 获取文件内容用于提取已有字符串
                String fileContent = contentReference.toString();
                
                List<io.github.rosemoe.sora.lang.completion.CompletionItem> items = 
                    stringAutoComplete.getCompletionItems(stringPrefix, contextKeyword, fileContent);
                
                for (io.github.rosemoe.sora.lang.completion.CompletionItem item : items) {
                    completionPublisher.addItem(item);
                }
                return;
            }
            
            // 正常代码补全
            String computePrefix =
                    CompletionHelper.computePrefix(
                            contentReference, charPosition, new MyPrefixChecker());
            MyIdentifierAutoComplete.SyncIdentifiers syncIdentifiers = this.manager.identifiers;
            if (syncIdentifiers != null && !computePrefix.equals("")) {
                try {
                    try {
                        LuaParser luaParser =
                                new LuaParser(new LuaLexer(contentReference.toString()).tokenize());
                        luaParser.parseAssignments();
                        HashMap hashMap = new HashMap();
                        HashSet<String> hashSet = new HashSet(this.baseMap.keySet());
                        for (String str : luaParser.parseImport()) {
                            String replace = str.replace(".*", "");
                            for (String str2 : hashSet) {
                                if (str2.startsWith(replace)) {
                                    hashMap.put(str2, this.baseMap.get(str2));
                                }
                            }
                        }
                        this.autoComplete.setMmap(luaParser.getMap());
                        this.autoComplete.setClassmap(this.classMap);
                        this.autoComplete.setBasemap(this.baseMap);
                    } catch (Exception e) {
                        LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
                    }
                } catch (Exception e2) {
                    LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e2.getMessage());
                }
                this.autoComplete.requireAutoComplete(
                        contentReference,
                        charPosition,
                        computePrefix,
                        completionPublisher,
                        syncIdentifiers);
            }
            if (!"fori".startsWith(computePrefix) || computePrefix.length() <= 0) {
                return;
            }
            completionPublisher.addItem(
                    new SimpleSnippetCompletionItem(
                            "fori",
                            "Snippet - For loop on index",
                            new SnippetDescription(computePrefix.length(), FOR_SNIPPET, true)));
        } catch (Exception e3) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e3.getMessage());
        }
    }
    
    public int getIndentAdvance(ContentReference contentReference, int i, int i2) {
        return getIndentAdvance(contentReference.getLine(i).substring(0, i2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getIndentAdvance(String str) {
        LuaTextTokenizer luaTextTokenizer = new LuaTextTokenizer(str, new State());
        int i = 0;
        while (true) {
            Tokens nextToken = luaTextTokenizer.nextToken();
            if (nextToken != Tokens.EOF) {
                if (nextToken == Tokens.FUNCTION
                        || nextToken == Tokens.FOR
                        || nextToken == Tokens.SWITCH
                        || nextToken == Tokens.REPEAT
                        // || nextToken == Tokens.CASE
                        || nextToken == Tokens.WHILE
                        || nextToken == Tokens.IF
                        // OOP 关键字缩进
                        || nextToken == Tokens.CLASS
                        || nextToken == Tokens.INTERFACE
                // || nextToken == Tokens.UNTIL
                /*|| nextToken == Tokens.DO*/ ) {
                    i++;
                } else if (nextToken == Tokens.END
                        || nextToken == Tokens.RETURN
                        || nextToken == Tokens.BREAK) {
                    i--;
                }
            } else {
                return Math.max(0, i) * 2;
            }
        }
    }

    public SymbolPairMatch getSymbolPairs() {
        return new SymbolPairMatch.DefaultSymbolPairs();
    }

    public NewlineHandler[] getNewlineHandlers() {
        return this.newlineHandlers;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getNonEmptyTextBefore(CharSequence charSequence, int i, int i2) {
        while (i > 0 && Character.isWhitespace(charSequence.charAt(i - 1))) {
            i--;
        }
        return charSequence.subSequence(Math.max(0, i - i2), i).toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getNonEmptyTextAfter(CharSequence charSequence, int i, int i2) {
        while (i < charSequence.length() && Character.isWhitespace(charSequence.charAt(i))) {
            i++;
        }
        return charSequence.subSequence(i, Math.min(i2 + i, charSequence.length())).toString();
    }

    /* loaded from: 20250524065410b276ab97-ac80-4356-ab65-110e2a6ec142.jar:com/yan/luaeditor/LuaLanguage/LuaLanguage$BraceHandler.class */
    class BraceHandler implements NewlineHandler {
        final LuaLanguage this$0;

        BraceHandler(LuaLanguage outer) {
            this.this$0 = outer;
        }

        @Override
        public boolean matchesRequirement(
                Content content, CharPosition charPosition, Styles styles) {
            ContentLine line = content.getLine(charPosition.line);
            if (StylesUtils.checkNoCompletion(styles, charPosition)) {
                return false;
            }

            String before = LuaLanguage.getNonEmptyTextBefore(line, charPosition.column, 8);
            String trimmedBefore = before.trim();

            // 换行时下一个无缩进
            return "end".equals(trimmedBefore) || "until".equals(trimmedBefore);
            /*|| "while".equals(trimmedBefore)
            || "if".equals(trimmedBefore)
            || "repeat".equals(trimmedBefore);*/
        }

        @Override
        public NewlineHandleResult handleNewline(
                Content content, CharPosition charPosition, Styles styles, int tabSize) {
            ContentLine line = content.getLine(charPosition.line);
            int column = charPosition.column;
            String before = line.subSequence(0, column).toString();

            int baseIndent = TextUtils.countLeadingSpaceCount(before, tabSize);
            int indentLevel = this.this$0.getIndentAdvance(before);

            StringBuilder sb = new StringBuilder("\n");
            String indentStr =
                    TextUtils.createIndent(baseIndent + indentLevel, tabSize, this.this$0.useTab());
            sb.append(indentStr);

            return new NewlineHandleResult(sb, indentStr.length());
        }
    }
}
