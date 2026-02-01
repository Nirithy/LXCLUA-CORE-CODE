package com.nirithy.luaeditor.lualanguage;

import android.os.Bundle;
import androidx.annotation.NonNull;
import com.nirithy.luaeditor.MyIdentifierAutoComplete;

import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager;
import io.github.rosemoe.sora.lang.brackets.SimpleBracketsCollector;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.styling.CodeBlock;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.SpanFactory;
import io.github.rosemoe.sora.lang.styling.TextStyle;
import io.github.rosemoe.sora.lang.styling.color.EditorColor;
import io.github.rosemoe.sora.lang.styling.span.SpanClickableUrl;
import io.github.rosemoe.sora.lang.styling.span.SpanConstColorResolver;
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Arrays;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.luajava.LuaState;
import com.luajava.LuaStateFactory;

public class LuaIncrementalAnalyzeManager
        extends AsyncIncrementalAnalyzeManager<State, HighlightToken> {
    public DiagnosticsContainer diagnosticsContainer;
    private static final int STATE_INCOMPLETE_COMMENT = 1;
    private static final int STATE_INCOMPLETE_LONG_STRING = 2;

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
    private final ThreadLocal<LuaTextTokenizer> tokenizerProvider = new ThreadLocal<>();
    protected MyIdentifierAutoComplete.SyncIdentifiers identifiers =
            new MyIdentifierAutoComplete.SyncIdentifiers();
    private boolean hexColorHighlightEnabled = false;

    private Set<String> androidClasses; // 修改字段类型
    private int classNamesVersion = 0; // 版本标记

    private boolean nextIsLocal = false;
    boolean inLocalDeclaration = false;

    // 用于代码分析的LuaState实例
    private LuaState analysisLuaState;
    
    // Lua错误信息的正则表达式，匹配格式如: [string "..."]:行号: 错误信息
    private static final Pattern LUA_ERROR_PATTERN = 
            Pattern.compile("\\[string \"[^\"]*\"\\]:(\\d+):\\s*(.*)");

    // 添加单例访问
    private static volatile LuaIncrementalAnalyzeManager instance;

    public static LuaIncrementalAnalyzeManager getInstance() {
        return instance;
    }

    public LuaIncrementalAnalyzeManager() {
        instance = this;
        // 初始化诊断容器
        this.diagnosticsContainer = new DiagnosticsContainer();
    }
    
    /**
     * 获取或创建用于代码分析的LuaState实例
     * @return LuaState实例，如果创建失败则返回null
     */
    private synchronized LuaState getAnalysisLuaState() {
        if (analysisLuaState == null) {
            try {
                analysisLuaState = LuaStateFactory.newLuaState();
                if (analysisLuaState != null) {
                    analysisLuaState.openLibs();
                }
            } catch (Exception e) {
                // 忽略创建失败的情况
            }
        }
        return analysisLuaState;
    }
    
    /**
     * 使用Lua C层分析代码，检测潜在的运行时错误
     * 不是语法错误（那是红色警告），而是真正的代码问题：
     * - 使用了未定义的全局变量
     * - 调用了可能不存在的函数
     * - 未使用的局部变量
     * @param text 要分析的代码文本
     * @return 是否有错误
     */
    public boolean analyzeCodeWithLua(Content text) {
        if (text == null) {
            return false;
        }
        
        // 重置诊断容器
        diagnosticsContainer.reset();
        
        try {
            String code = text.toString();
            
            // 进行静态代码分析，检测潜在的运行时问题
            analyzeUndefinedVariables(text, code);
            
            // 如果有诊断结果，通知编辑器更新显示
            withReceiver(r -> {
                r.setDiagnostics(this, diagnosticsContainer);
            });
            
            return true;
        } catch (Exception e) {
            // 忽略分析过程中的异常
        }
        
        return false;
    }
    
    /**
     * 静态分析：检测使用了未定义的全局变量
     * 这些变量在运行时可能导致nil错误
     * @param text 文本内容
     * @param code 代码字符串
     */
    private void analyzeUndefinedVariables(Content text, String code) {
        // Lua标准库和常用全局变量
        Set<String> knownGlobals = new HashSet<>(Arrays.asList(
            // 基本类型和函数
            "nil", "true", "false", "print", "type", "tostring", "tonumber",
            "pairs", "ipairs", "next", "select", "unpack", "pcall", "xpcall",
            "error", "assert", "collectgarbage", "dofile", "getmetatable",
            "setmetatable", "rawget", "rawset", "rawequal", "rawlen",
            "require", "load", "loadfile", "loadstring",
            // 标准库
            "string", "table", "math", "io", "os", "debug", "coroutine", "package",
            "utf8", "bit32",
            // 常用全局对象
            "_G", "_VERSION", "_ENV", "arg",
            // 你项目中的全局对象
            "activity", "this", "luajava", "import", "new", "tointeger",
            "task", "thread", "timer", "call", "dump", "each", "enum",
            "loadbitmap", "loadlayout", "loadmenu"
        ));
        
        // 收集代码中定义的局部变量和函数
        Set<String> definedLocals = new HashSet<>();
        Pattern localPattern = Pattern.compile("\\blocal\\s+(\\w+)");
        Matcher localMatcher = localPattern.matcher(code);
        while (localMatcher.find()) {
            definedLocals.add(localMatcher.group(1));
        }
        
        // 收集函数定义
        Pattern funcPattern = Pattern.compile("\\bfunction\\s+(\\w+)");
        Matcher funcMatcher = funcPattern.matcher(code);
        while (funcMatcher.find()) {
            definedLocals.add(funcMatcher.group(1));
        }
        
        // 检测可能未定义的变量使用
        // 匹配独立的标识符（不在local后面，不在function后面，不是字符串内）
        Pattern usagePattern = Pattern.compile("(?<!\\.)\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
        Matcher usageMatcher = usagePattern.matcher(code);
        
        while (usageMatcher.find()) {
            String varName = usageMatcher.group(1);
            
            // 跳过已知的全局变量、关键字、已定义的局部变量
            if (knownGlobals.contains(varName) || 
                definedLocals.contains(varName) ||
                isLuaKeyword(varName)) {
                continue;
            }
            
            // 找到调用位置
            int matchStart = usageMatcher.start(1);
            
            // 计算行号和列号
            int line = 0;
            int col = 0;
            int pos = 0;
            for (int i = 0; i < text.getLineCount() && pos <= matchStart; i++) {
                int lineLen = text.getColumnCount(i) + 1; // +1 for newline
                if (pos + lineLen > matchStart) {
                    line = i;
                    col = matchStart - pos;
                    break;
                }
                pos += lineLen;
            }
            
            // 计算字符索引
            int startIndex = text.getCharIndex(line, col);
            int endIndex = startIndex + varName.length();
            
            // 创建黑色警告诊断区域
            DiagnosticRegion region = new DiagnosticRegion(
                    startIndex, 
                    endIndex, 
                    DiagnosticRegion.SEVERITY_HINT
            );
            
            diagnosticsContainer.addDiagnostic(region);
        }
    }
    
    /**
     * 检查是否为Lua关键字
     * @param word 要检查的单词
     * @return 是否为关键字
     */
    private boolean isLuaKeyword(String word) {
        Set<String> keywords = new HashSet<>(Arrays.asList(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or",
            "repeat", "return", "then", "true", "until", "while"
        ));
        return keywords.contains(word);
    }
    
    /**
     * 释放分析用的LuaState资源
     */
    public synchronized void releaseAnalysisLuaState() {
        if (analysisLuaState != null) {
            try {
                analysisLuaState.close();
            } catch (Exception e) {
                // 忽略关闭异常
            }
            analysisLuaState = null;
        }
    }

    public boolean isHexColorHighlightEnabled() {
        return hexColorHighlightEnabled;
    }

    public void setHexColorHighlightEnabled(boolean enabled) {
        this.hexColorHighlightEnabled = enabled;
    }

    public void releaseMemory() {
        // 清空所有缓存的 tokens
        this.identifiers.clear();
        // 通知 tokenizer 释放
        LuaTextTokenizer tokenizer = tokenizerProvider.get();
        if (tokenizer != null) {
            tokenizer.releaseTokens();
        }
        // 释放代码分析用的LuaState
        releaseAnalysisLuaState();
        // 清空诊断容器
        if (diagnosticsContainer != null) {
            diagnosticsContainer.reset();
        }
        // 通知 GC
        System.gc();
    }

    // 更新类名集合
    public void setClassMap(Set<String> androidClasses) {
        this.androidClasses = androidClasses;
        classNamesVersion++; // 每次更新时增加版本号

        // 更新所有分词器的类名
        updateAllTokenizersClassNames();
    }

    // 更新所有分词器的类名
    private void updateAllTokenizersClassNames() {
        LuaTextTokenizer tokenizer = obtainTokenizer();
        tokenizer.setClassNames(androidClasses);
        tokenizer.setClassNamesVersion(classNamesVersion); // 设置版本号
    }

    // 获取所有tokenizer实例
    private List<LuaTextTokenizer> getAllTokenizers() {
        List<LuaTextTokenizer> list = new ArrayList<>();
        // 实际实现需要根据tokenizerProvider获取所有实例
        // 伪代码：遍历ThreadLocal存储的所有实例
        return list;
    }

    public void updateClassNamesInTokenizer(Set<String> classNames) {
        LuaTextTokenizer tokenizer = obtainTokenizer();
        tokenizer.setClassNames(classNames); // 这会触发缓存构建

        // 确保所有实例同步更新
        synchronized (tokenizerProvider) {
            for (LuaTextTokenizer t : getAllTokenizers()) {
                if (t != tokenizer) {
                    t.setClassNames(classNames);
                }
            }
        }
    }

    private synchronized LuaTextTokenizer obtainTokenizer() {
        LuaTextTokenizer res = this.tokenizerProvider.get();
        if (res == null) {
            res = new LuaTextTokenizer("", new State());
            this.tokenizerProvider.set(res);
        }
        return res;
    }

    public List<CodeBlock> computeBlocks(
            Content text,
            AsyncIncrementalAnalyzeManager<State, HighlightToken>.CodeBlockAnalyzeDelegate
                    delegate) {
        Stack<CodeBlock> stack = new Stack<>();
        ArrayList<CodeBlock> blocks = new ArrayList<>();
        int maxSwitch = 0;
        int currSwitch = 0;
        SimpleBracketsCollector brackets = new SimpleBracketsCollector();
        Stack<Long> bracketsStack = new Stack<>();
        for (int i = 0;
                i < text.getLineCount() && delegate.isNotCancelled();
                i += STATE_INCOMPLETE_COMMENT) {
            IncrementalAnalyzeManager.LineTokenizeResult<State, HighlightToken> state = getState(i);
            boolean checkForIdentifiers =
                    (state.state).state == 0
                            || ((state.state).state == STATE_INCOMPLETE_COMMENT
                                    && state.tokens.size() > STATE_INCOMPLETE_COMMENT);
            if ((state.state).hasBraces || checkForIdentifiers) {
                for (int i1 = 0; i1 < state.tokens.size(); i1 += STATE_INCOMPLETE_COMMENT) {
                    HighlightToken tokenRecord = state.tokens.get(i1);
                    Tokens token = tokenRecord.token;
                    if (token == Tokens.LBRACE
                            || token == Tokens.FUNCTION
                            || token == Tokens.FOR
                            || token == Tokens.WHILE
                            || token == Tokens.IF
                            || token == Tokens.CONTINUE
                            || token == Tokens.REPEAT
                            || token == Tokens.SWITCH) {
                        int offset = tokenRecord.offset;
                        if (stack.isEmpty()) {
                            if (currSwitch > maxSwitch) {
                                maxSwitch = currSwitch;
                            }
                            currSwitch = 0;
                        }
                        currSwitch += STATE_INCOMPLETE_COMMENT;
                        CodeBlock block = new CodeBlock();
                        block.startLine = i;
                        block.startColumn = offset;
                        stack.push(block);
                    } else if (token == Tokens.RBRACE || token == Tokens.END) {
                        int offset2 = tokenRecord.offset;
                        if (!stack.isEmpty()) {
                            CodeBlock block2 = stack.pop();
                            block2.endLine = i;
                            block2.endColumn = offset2;
                            if (block2.startLine != block2.endLine) {
                                blocks.add(block2);
                            }
                        }
                    }
                    int type = getType(token);
                    if (type > 0) {
                        if (isStart(token)) {
                            bracketsStack.push(
                                    Long.valueOf(
                                            IntPair.pack(
                                                    type,
                                                    text.getCharIndex(i, tokenRecord.offset))));
                        } else if (!bracketsStack.isEmpty()) {
                            Long record = bracketsStack.pop();
                            int typeRecord = IntPair.getFirst(record.longValue());
                            if (typeRecord == type) {
                                brackets.add(
                                        IntPair.getSecond(record.longValue()),
                                        text.getCharIndex(i, tokenRecord.offset));
                            }
                        }
                    }
                }
            }
        }
        if (delegate.isNotCancelled()) {
            withReceiver(
                    r -> {
                        r.updateBracketProvider(this, brackets);
                    });
        }
        return blocks;
    }

    private static int getType(Tokens token) {
        if (token == Tokens.LBRACE || token == Tokens.RBRACE) {
            return 3;
        }
        if (token == Tokens.LBRACK || token == Tokens.RBRACK) {
            return 2;
        }
        if (token == Tokens.LPAREN || token == Tokens.RPAREN) {
            return STATE_INCOMPLETE_COMMENT;
        }
        return 0;
    }

    private static boolean isStart(Tokens token) {
        return token == Tokens.LBRACE || token == Tokens.LBRACK || token == Tokens.LPAREN;
    }

    @NonNull
    @Override
    public State getInitialState() {
        return new State();
    }

    public boolean stateEquals(@NonNull State state, @NonNull State another) {
        return state.equals(another);
    }

    public void onAddState(State state) {
        if (state.identifiers != null) {
            for (String identifier : state.identifiers) {
                this.identifiers.identifierIncrease(identifier);
            }
        }
    }

    public void onAbandonState(State state) {
        if (state.identifiers != null) {
            for (String identifier : state.identifiers) {
                this.identifiers.identifierDecrease(identifier);
            }
        }
    }

    public void reset(@NonNull ContentReference content, @NonNull Bundle extraArguments) {
        super.reset(content, extraArguments);
        this.identifiers.clear();
    }

    public IncrementalAnalyzeManager.LineTokenizeResult<State, HighlightToken> tokenizeLine(
            CharSequence line, State state, int lineIndex) {
        ArrayList<HighlightToken> tokens = new ArrayList<>();
        int newState = 0;
        State stateObj = new State();
        stateObj.longCommentEqualCount = state.longCommentEqualCount;
        stateObj.longStringEqualCount = state.longStringEqualCount;
        stateObj.inLongString = state.inLongString;

        LuaTextTokenizer tokenizer = obtainTokenizer();
        tokenizer.reset(line, stateObj);

        // 根据状态进行不同的词法分析
        if (state.state == 0) {
            newState = tokenizeNormal(line, 0, tokens, stateObj);
        } else if (state.state == STATE_INCOMPLETE_COMMENT) {
            // 处理不完整的长注释
            tokenizer.offset = 0;
            Tokens token = tokenizer.scanLongComment(true);
            int tokenLength = tokenizer.getTokenLength(); // 获取token的长度
            tokens.add(new HighlightToken(token, 0));
            if (token == Tokens.LONG_COMMENT_INCOMPLETE) {
                newState = STATE_INCOMPLETE_COMMENT;
                stateObj.longCommentEqualCount = tokenizer.getLongCommentEqualCount();
            } else if (token == Tokens.LONG_COMMENT_COMPLETE) {
                // 注释在行中结束，处理剩余内容
                int remainingOffset = tokenLength;
                if (remainingOffset < line.length()) {
                    // 从剩余位置开始正常token化
                    newState = tokenizeNormal(line, remainingOffset, tokens, stateObj);
                } else {
                    newState = 0; // 行结束，状态重置
                }
            }
        } else if (state.state == STATE_INCOMPLETE_LONG_STRING) {
            tokenizer.offset = 0;
            // 关键修复：从stateObj恢复分词器状态
            tokenizer.setLongStringEqualCount(stateObj.longStringEqualCount);
            tokenizer.setInLongString(stateObj.inLongString);

            Tokens token = tokenizer.scanLongString(true);
            int tokenLength = tokenizer.getTokenLength();
            tokens.add(new HighlightToken(token, 0));

            // 更新全局状态
            stateObj.longStringEqualCount = tokenizer.getLongStringEqualCount();
            stateObj.inLongString = tokenizer.isInLongString();

            if (token == Tokens.LONG_STRING_INCOMPLETE) {
                newState = STATE_INCOMPLETE_LONG_STRING;
            } else if (token == Tokens.LONG_STRING) {
                stateObj.inLongString = false;
                // 处理剩余内容
                int remainingOffset = tokenLength;
                if (remainingOffset < line.length()) {
                    newState = tokenizeNormal(line, remainingOffset, tokens, stateObj);
                } else {
                    newState = 0;
                }
            } else {
                stateObj.inLongString = false;
                newState = 0;
            }
        }

        // 确保当前分词器使用最新类名集合
        if (tokenizer.getClassNamesVersion() != this.classNamesVersion) {
            tokenizer.setClassNames(androidClasses);
            tokenizer.setClassNamesVersion(this.classNamesVersion);
        }

        if (tokens.isEmpty()) {
            tokens.add(new HighlightToken(Tokens.UNKNOWN, 0));
        }
        stateObj.state = newState;
        return new IncrementalAnalyzeManager.LineTokenizeResult<>(stateObj, tokens);
    }

    private long tryFillIncompleteComment(CharSequence line, List<HighlightToken> tokens) {
        int offset = 0;
        while (offset < line.length() && line.charAt(offset) != ']') {
            offset += STATE_INCOMPLETE_COMMENT;
        }
        int closeOffset = offset + STATE_INCOMPLETE_COMMENT;
        int closeEqCount = 0;
        while (closeOffset < line.length() && line.charAt(closeOffset) == '=') {
            closeEqCount += STATE_INCOMPLETE_COMMENT;
            closeOffset += STATE_INCOMPLETE_COMMENT;
        }
        if (closeOffset < line.length() && line.charAt(closeOffset) == ']') {
            tokens.add(new HighlightToken(Tokens.LONG_COMMENT_COMPLETE, 0));
            return IntPair.pack(0, closeOffset + STATE_INCOMPLETE_COMMENT);
        }
        tokens.add(new HighlightToken(Tokens.LONG_COMMENT_INCOMPLETE, 0));
        return IntPair.pack(STATE_INCOMPLETE_COMMENT, line.length());
    }

    private int tokenizeNormal(
            CharSequence text, int offset, List<HighlightToken> tokens, State st) {
        LuaTextTokenizer tokenizer = obtainTokenizer();
        tokenizer.reset(text);
        tokenizer.offset = offset;
        int state = 0;
        while (true) {
            Tokens token = tokenizer.nextToken();
            if (token == Tokens.EOF) {
                break;
            } else if (token == Tokens.LONG_STRING || token == Tokens.LONG_STRING_INCOMPLETE) {
                tokens.add(new HighlightToken(token, tokenizer.offset));
                if (token == Tokens.LONG_STRING_INCOMPLETE) {
                    st.longStringEqualCount = tokenizer.getLongStringEqualCount();
                    st.inLongString = true;
                    return STATE_INCOMPLETE_LONG_STRING; // 返回新状态
                }
            } else if (tokenizer.getTokenLength() < 1000
                    && (token == Tokens.STRING
                            || token == Tokens.LONG_COMMENT_COMPLETE
                            || token == Tokens.LONG_COMMENT_INCOMPLETE
                            || token == Tokens.LINE_COMMENT)) {
                detectHighlightColors(tokenizer.getTokenText(), tokenizer.offset, token, tokens);
                if (token == Tokens.LONG_COMMENT_INCOMPLETE) {
                    state = STATE_INCOMPLETE_COMMENT;
                    break;
                }
            } else {
                // 修复：为独立色值设置text属性
                if (token == Tokens.HEX_COLOR) {
                    HighlightToken ht = new HighlightToken(token, tokenizer.offset);
                    ht.text = tokenizer.getTokenText().toString(); // 设置色值字符串
                    tokens.add(ht);
                } else {
                    tokens.add(new HighlightToken(token, tokenizer.offset));
                }
                if (token == Tokens.LBRACE || token == Tokens.RBRACE) {
                    st.hasBraces = true;
                }
                if (token == Tokens.IDENTIFIER) {
                    st.addIdentifier(tokenizer.getTokenText());
                }
                if (token == Tokens.LONG_COMMENT_INCOMPLETE) {
                    state = STATE_INCOMPLETE_COMMENT;
                    break;
                }
            }
        }
        return state;
    }

    private void detectHighlightColors(
            CharSequence tokenText, int offset, Tokens token, List<HighlightToken> tokens) {
        int lastIndex = 0;
        int i = 0;
        while (i < tokenText.length()) {
            boolean colorFound = false;
            int colorStart = i;
            int colorEnd = i;

            if (tokenText.charAt(i) == '#') {
                colorEnd = i + 1;
                while (colorEnd < tokenText.length() && isHexDigit(tokenText.charAt(colorEnd))) {
                    colorEnd++;
                }
                int hexLength = colorEnd - i - 1;
                if (hexLength == 6 || hexLength == 8) {
                    colorFound = true;
                }
            } else if (i + 1 < tokenText.length()
                    && tokenText.charAt(i) == '0'
                    && (tokenText.charAt(i + 1) == 'x' || tokenText.charAt(i + 1) == 'X')) {
                colorEnd = i + 2;
                while (colorEnd < tokenText.length() && isHexDigit(tokenText.charAt(colorEnd))) {
                    colorEnd++;
                }
                int hexLength = colorEnd - i - 2;
                if (hexLength == 6 || hexLength == 8) {
                    colorFound = true;
                }
            }

            if (colorFound) {
                if (colorStart > lastIndex) {
                    tokens.add(new HighlightToken(token, offset + lastIndex));
                }
                HighlightToken colorToken =
                        new HighlightToken(Tokens.HEX_COLOR, offset + colorStart);
                if (hexColorHighlightEnabled && (colorEnd - colorStart) <= 10) {
                    colorToken.text = tokenText.subSequence(colorStart, colorEnd).toString();
                }
                tokens.add(colorToken);
                i = colorEnd;
                lastIndex = colorEnd;
            } else {
                i++;
            }
        }

        if (lastIndex < tokenText.length()) {
            tokens.add(new HighlightToken(token, offset + lastIndex));
        }
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isHighContrast(String str) {
        String substring;
        int parseInt;
        if (str == null
                || str.equalsIgnoreCase("0x00000000")
                || str.equalsIgnoreCase("#00000000")) {
            return false;
        }
        if (str.startsWith("#")) {
            substring = str.substring(STATE_INCOMPLETE_COMMENT);
        } else {
            if (!str.startsWith("0x") && !str.startsWith("0X")) {
                return false;
            }
            substring = str.substring(STATE_INCOMPLETE_LONG_STRING);
        }
        try {
            int length = substring.length();
            if (length == 8) {
                parseInt = Integer.parseInt(substring.substring(STATE_INCOMPLETE_LONG_STRING), 16);
            } else {
                if (length != 6) {
                    return false;
                }
                parseInt = Integer.parseInt(substring, 16);
            }
            return (((((double) ((parseInt >> 16) & 255)) * 0.299d)
                                            + (((double) ((parseInt >> 8) & 255)) * 0.587d))
                                    + (((double) (parseInt & 255)) * 0.114d))
                            / 255.0d
                    < 0.65d;
        } catch (Exception unused) {
            return false;
        }
    }

    /**
     * 解析十六进制颜色字符串为整数颜色值
     * 支持格式: #RRGGBB, #AARRGGBB, 0xRRGGBB, 0xAARRGGBB
     * @param str 十六进制颜色字符串
     * @return 解析后的颜色值（ARGB格式），解析失败返回0
     */
    private int parseHexColor(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        try {
            String hexPart;
            if (str.startsWith("#")) {
                hexPart = str.substring(1);
            } else if (str.startsWith("0x") || str.startsWith("0X")) {
                hexPart = str.substring(2);
            } else {
                return 0;
            }
            
            int length = hexPart.length();
            if (length == 6) {
                // #RRGGBB 或 0xRRGGBB - 添加完全不透明的alpha
                return (int) (0xFF000000L | Long.parseLong(hexPart, 16));
            } else if (length == 8) {
                // #AARRGGBB 或 0xAARRGGBB
                return (int) Long.parseLong(hexPart, 16);
            } else if (length == 3) {
                // #RGB - 扩展为 #RRGGBB
                char r = hexPart.charAt(0);
                char g = hexPart.charAt(1);
                char b = hexPart.charAt(2);
                String expanded = "" + r + r + g + g + b + b;
                return (int) (0xFF000000L | Long.parseLong(expanded, 16));
            } else if (length == 4) {
                // #ARGB - 扩展为 #AARRGGBB
                char a = hexPart.charAt(0);
                char r = hexPart.charAt(1);
                char g = hexPart.charAt(2);
                char b = hexPart.charAt(3);
                String expanded = "" + a + a + r + r + g + g + b + b;
                return (int) Long.parseLong(expanded, 16);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Span> generateSpansForLine(LineTokenizeResult<State, HighlightToken> lineResult) {
        var spans = new ArrayList<Span>();
        var tokens = lineResult.tokens;
        Tokens previous = Tokens.UNKNOWN;
        boolean classNamePrevious = false;

        // 行内局部变量状态（与全局状态隔离，避免跨行污染）
        boolean nextIsLocal = false;
        boolean inLocalDeclaration = false;

        for (int i = 0; i < tokens.size(); i++) {
            var tokenRecord = tokens.get(i);
            var token = tokenRecord.token;
            int offset = tokenRecord.offset;
            Span span;

            /* ---------- 1. 状态机：控制局部变量识别 ---------- */
            switch (token) {
                case LOCAL:
                    nextIsLocal = true;
                    inLocalDeclaration = true;
                    break;
                case COMMA:
                    if (inLocalDeclaration) {
                        nextIsLocal = true; // 继续识别下一标识符
                    }
                    break;
                case EQ:
                case NEWLINE:
                    // 出现赋值或换行，结束当前局部变量声明
                    inLocalDeclaration = false;
                    nextIsLocal = false;
                    break;
                default:
                    break;
            }

            switch (token) {
                case WHITESPACE:
                case NEWLINE:
                case EQ:
                    span =
                            SpanFactory.obtain(
                                    offset, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL));
                    break;

                case STRING:
                case LONG_STRING:
                case LONG_STRING_INCOMPLETE:
                case CHARACTER_LITERAL:
                    classNamePrevious = false;
                    // 字符串内允许触发补全（用于 import/require 等字符串补全）
                    span =
                            SpanFactory.obtain(
                                    offset, TextStyle.makeStyle(EditorColorScheme.LITERAL, false));
                    break;
                case COLLECTGARBAGE:
                case COMPILE:
                case COROUTINE:
                case ASSERT:
                case ERROR:
                case IPAIRS:
                case PAIRS:
                case NEXT:
                case PRINT:
                case RAWEQUAL:
                case RAWGET:
                case RAWSET:
                case SELECT:
                case SETMETATABLE:
                case GETMETATABLE:
                case TONUMBER:
                case TOSTRING:
                case TYPE:
                case UNPACK:
                case _G:
                case CALL:
                case PCALL:
                case XPCALL:
                case LOAD:
                case LOADSTRING:
                case DOFILE:
                case LOADFILE:
                case RAWLEN:
                case DEBUG:
                case PACKAGE:
                    classNamePrevious = false;
                    span =
                            SpanFactory.obtain(
                                    offset,
                                    TextStyle.makeStyle(
                                            EditorColorScheme.OPERATOR, 0, false, false, false));
                    break;

                case NUMBER:
                    classNamePrevious = false;
                    span =
                            SpanFactory.obtain(
                                    offset,
                                    TextStyle.makeStyle(
                                            EditorColorScheme.OPERATOR, 0, true, false, false));
                    break;

                case TRUE:
                case FALSE:
                    classNamePrevious = false;
                    span =
                            SpanFactory.obtain(
                                    offset,
                                    TextStyle.makeStyle(
                                            EditorColorScheme.OPERATOR, 0, true, false, false));
                    break;

                case IF:
                case THEN:
                case ELSE:
                case ELSEIF:
                case END:
                case ENUM:
                case FOR:
                case IN:
                case IS:
                case REPEAT:
                case RETURN:
                case BREAK:
                case UNTIL:
                case WHILE:
                case WHEN:
                case DO:
                case FUNCTION:
                case GOTO:
                case NIL:
                case NOT:
                case IMPORT:
                case REQUIRE:
                case SWITCH:
                case LAMBDA:
                case CONTINUE:
                case COMMAND:
                case DEFAULT:
                case CASE:
                case TRY:
                case CATCH:
                case FINALLY:
                case WITH:
                case GLOBAL:
                case CONST:
                case KEYWORD:
                case OPERATOR_KW:
                case DOLLAR:
                case ABSTRACT:
                case CLASS:
                case EXTENDS:
                case FINAL:
                case IMPLEMENTS:
                case INTERFACE:
                case NEW:
                case PRIVATE:
                case PROTECTED:
                case PUBLIC:
                case STATIC:
                case SUPER:
                    classNamePrevious = false;
                    span =
                            SpanFactory.obtain(
                                    offset,
                                    TextStyle.makeStyle(
                                            EditorColorScheme.KEYWORD, 0, true, false, false));
                    break;

                case LOCAL:
                    nextIsLocal = true;
                    classNamePrevious = false;
                    span =
                            SpanFactory.obtain(
                                    offset,
                                    TextStyle.makeStyle(
                                            EditorColorScheme.KEYWORD, 0, true, false, false));
                    break;

                case IDENTIFIER:
                    {
                        int type = EditorColorScheme.TEXT_NORMAL;

                        if (nextIsLocal) {
                            type = EditorColorScheme.LOCAL_VARIABLE;
                        } else {
                            if (classNamePrevious) {
                                type = EditorColorScheme.IDENTIFIER_VAR;
                                classNamePrevious = false;
                            } else if (previous == Tokens.DOT) {
                                type = EditorColorScheme.IDENTIFIER_VAR;
                            } else if (previous == Tokens.AT) {
                                type = EditorColorScheme.ANNOTATION;
                            } else {
                                // 向后看一个非空白 token 判断函数名或类属性
                                int j = i + 1;
                                var next = Tokens.UNKNOWN;
                                while (j < tokens.size()) {
                                    next = tokens.get(j).token;
                                    if (next != Tokens.WHITESPACE
                                            && next != Tokens.NEWLINE
                                            && next != Tokens.LONG_COMMENT_INCOMPLETE
                                            && next != Tokens.LONG_COMMENT_COMPLETE
                                            && next != Tokens.LINE_COMMENT) {
                                        break;
                                    }
                                    j++;
                                }
                                if (next == Tokens.LPAREN) {
                                    type = EditorColorScheme.FUNCTION_NAME;
                                } else if (next == Tokens.DOT) {
                                    type = EditorColorScheme.IDENTIFIER_VAR;
                                    classNamePrevious = true;
                                } else if (tokenRecord.token == Tokens.IDENTIFIER
                                        && androidClasses != null
                                        && androidClasses.contains(
                                                tokenRecord.text != null ? tokenRecord.text : "")) {
                                    type = EditorColorScheme.CLASS_NAME;
                                }
                            }
                        }
                        span = SpanFactory.obtain(offset, TextStyle.makeStyle(type));
                        break;
                    }

                case LBRACE:
                case RBRACE:
                    span =
                            SpanFactory.obtain(
                                    offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR));
                    break;

                case LINE_COMMENT:
                case LONG_COMMENT_COMPLETE:
                case LONG_COMMENT_INCOMPLETE:
                    span =
                            SpanFactory.obtain(
                                    offset,
                                    TextStyle.makeStyle(
                                            EditorColorScheme.COMMENT,
                                            0,
                                            false,
                                            true,
                                            false,
                                            true));
                    break;

                case HEX_COLOR:
                    if (hexColorHighlightEnabled && tokenRecord.text != null) {
                        // 解析十六进制颜色值
                        int parsedColor = parseHexColor(tokenRecord.text);
                        if (parsedColor != 0) {
                            // 根据背景色亮度选择前景色（白色或黑色）
                            int foregroundColor = isHighContrast(tokenRecord.text) 
                                    ? 0xFFFFFFFF  // 白色前景
                                    : 0xFF000000; // 黑色前景
                            // 使用 SpanConstColorResolver 设置自定义颜色
                            span = SpanFactory.obtain(
                                    offset, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL, 0, true, false, false));
                            span.setSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER, 
                                    new SpanConstColorResolver(foregroundColor, parsedColor));
                        } else {
                            span = SpanFactory.obtain(
                                    offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR, 0, true, false, false));
                        }
                    } else {
                        span = SpanFactory.obtain(
                                offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR, 0, true, false, false));
                    }
                    break;

                case CLASS_NAME:
                    span =
                            SpanFactory.obtain(
                                    offset, TextStyle.makeStyle(EditorColorScheme.CLASS_NAME));
                    break;

                default:
                    if (isOperator(token)) {
                        span =
                                SpanFactory.obtain(
                                        offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR));
                    } else {
                        span =
                                SpanFactory.obtain(
                                        offset, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL));
                    }
            }

            if (tokenRecord.url != null) {
                span.setSpanExt(
                        SpanExtAttrs.EXT_INTERACTION_INFO, new SpanClickableUrl(tokenRecord.url));
                span.setUnderlineColor(new EditorColor(span.getForegroundColorId()));
            }

            spans.add(span);

            // 仅当遇到 EQ 或 NEWLINE 时才重置状态，逗号不重置
            if (token == Tokens.EQ || token == Tokens.NEWLINE) {
                nextIsLocal = false;
            }

            // 更新 previous
            switch (token) {
                case LINE_COMMENT:
                case LONG_COMMENT_COMPLETE:
                case LONG_COMMENT_INCOMPLETE:
                case WHITESPACE:
                case NEWLINE:
                    break;
                default:
                    previous = token;
            }
        }
        return spans;
    }

    private boolean isOperator(Tokens token) {
        switch (token) {
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
            case POW:
            case NEQ:
            case LT:
            case GT:
            case LEQ:
            case GEQ:
            case AT:
            case XOR:
            case QUESTION:
            case EQEQ:
            case LTEQ:
            case GTEQ:
            case DOTEQ:
            case LTLT:
            case LTGT:
            case CLT:
            case AEQ:
            case GTGT:
            case ARROW:
            case ARROW_LEFT_LONG:
            case ARROW_RIGHT_LONG:
            case SPACESHIP:
            case DOT_DOT_EQ:
            case DOT_DOT_LT:
            case QUESTION_DOT_DOT:
            case NOT_NOT:
            case NULL_COALESCING:
            case STAR_STAR:
            case TILDE_TILDE:
            case CARET_CARET:
            case HASH_HASH:
            case AT_AT:
            case DOLLAR_DOLLAR:
            case COLON_EQ:
            case EQ_COLON:
            case QUESTION_DOT:
            case QUESTION_COLON:
            case QUESTION_EQ:
            case QUESTION_MINUS:
            case QUESTION_PLUS:
            case ARROW_LEFT:
            case TILDE_EQ:
            case EQEQEQ:
            case NEQEQ:
            case FAT_ARROW:
            case PLUS_PLUS:
            case PLUS_EQ:
            case MINUS_EQ:
            case STAR_EQ:
            case SLASH_EQ:
            case PERCENT_EQ:
            case AMP_EQ:
            case BAR_EQ:
            case CARET_EQ:
            case EQ_LT:
            case COLON_COLON:
            case DOT_DOT:
            case SLASH_SLASH:
            case BACKSLASH_BACKSLASH:
            case SLASH_STAR:
            case STAR_SLASH:
            case SLASH_STAR_STAR:
            case HASH_HASH_HASH:
            case MINUS_BAR:
            case BAR_GT:
            case LT_BAR:
            case AND:
            case OR:
            case OP:
                return true;
            default:
                return false;
        }
    }
}
