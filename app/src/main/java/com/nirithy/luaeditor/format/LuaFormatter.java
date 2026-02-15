package com.nirithy.luaeditor.format;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nirithy.luaeditor.tools.tokenizer.LuaLexer;
import com.nirithy.luaeditor.tools.tokenizer.LuaTokenTypes;

import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.TextRange;

import java.io.IOException;

public class LuaFormatter extends AsyncFormatter {

    private static final String MARKER = "\"__CURSOR_MARKER__\"";
    private static final String MARKER_REGEX = "\\Q" + MARKER + "\\E";

    @Override
    public TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange) {
        return formatInternal(text, null, cursorRange);
    }

    @Override
    public TextRange formatRegionAsync(
            @NonNull Content text,
            @NonNull TextRange rangeToFormat,
            @NonNull TextRange cursorRange) {
        return formatInternal(text, rangeToFormat, cursorRange);
    }

    private TextRange formatInternal(
            @NonNull Content text,
            @Nullable TextRange rangeToFormat,
            @NonNull TextRange cursorRange) {
        try {
            // 1. 插入光标标记
            int markerIndex = insertMarkerAtPosition(text, cursorRange.getStart());

            // 2. 获取要格式化的文本
            String input;
            int formatStartIndex = 0;
            int formatEndIndex = text.length();

            if (rangeToFormat != null) {
                CharPosition start = rangeToFormat.getStart();
                CharPosition end = rangeToFormat.getEnd();
                formatStartIndex = text.getIndexer().getCharIndex(start.line, start.column);
                formatEndIndex = text.getIndexer().getCharIndex(end.line, end.column);
                input = text.toString().substring(formatStartIndex, formatEndIndex);
            } else {
                input = text.toString();
            }

            // 3. 执行格式化
            String formatted = this.format(input);

            // 4. 替换文本
            if (rangeToFormat != null) {
                CharPosition start = rangeToFormat.getStart();
                CharPosition end = rangeToFormat.getEnd();
                text.replace(start.line, start.column, end.line, end.column, formatted);
            } else {
                text.replace(
                        0,
                        0,
                        text.getLineCount() - 1,
                        text.getColumnCount(text.getLineCount() - 1),
                        formatted);
            }

            // 5. 根据标记恢复光标位置
            return findMarkerPosition(text, markerIndex);

        } catch (Exception e) {
            e.printStackTrace();
            return cursorRange;
        }
    }

    private int insertMarkerAtPosition(Content text, CharPosition pos) {
        int idx = text.getIndexer().getCharIndex(pos.line, pos.column);
        text.insert(pos.line, pos.column, MARKER);
        return idx;
    }

    private TextRange findMarkerPosition(Content text, int originalIndex) {
        String full = text.toString();
        int markerStart = full.indexOf(MARKER);
        if (markerStart >= 0) {
            String cleaned = full.replaceFirst(MARKER_REGEX, "");
            text.replace(
                    0,
                    0,
                    text.getLineCount() - 1,
                    text.getColumnCount(text.getLineCount() - 1),
                    cleaned);
            CharPosition pos = text.getIndexer().getCharPosition(markerStart);
            return new TextRange(pos, pos);
        }
        return new TextRange(new CharPosition(0, 0, 0), new CharPosition(0, 0, 0));
    }

    /**
     * 根据 token 类型返回对应的缩进变化量。
     * 注意：IF/FOR/WHILE 不直接增加缩进，因为有两种语法：
     * - 传统语法 if...then...end / for...do...end: 由 THEN/DO 增加缩进，END 减少
     * - 花括号语法 if [ ] { } / for [ ] { }: 由 LCURLY 增加缩进，RCURLY 减少
     * OOP关键字:
     * - oclass/interface: 类定义，增加缩进，由 END 结束
     */
    private static int indent(LuaTokenTypes type) {
        switch (type) {
            case FUNCTION:
            case THEN:      // if 的传统语法由 THEN 增加缩进
            case DO:        // for/while 的传统语法由 DO 增加缩进
            case REPEAT:
            case LCURLY:
            case SWITCH:
            case TRY:
            case CLASS:     // 类定义增加缩进
            case INTERFACE: // 接口定义增加缩进
            case STRUCT:    // 结构体定义增加缩进
            case SUPERSTRUCT: // 超级结构体定义增加缩进
            case CONCEPT:   // 概念定义增加缩进
            case NAMESPACE: // 命名空间定义增加缩进
                return 1;

            case UNTIL:
            case END:
            case RCURLY:
                return -1;

            case KEYWORD:        // 动态关键字，增加缩进
            case OPERATOR_KW:    // 运算符重载，增加缩进
                return 1;

            case DOLLAR:         // 美元符号，不改变缩进
                return 0;

            default:
                return 0;
        }
    }

    /**
     * 将 Lua 源码按给定缩进宽度重新格式化。
     * 支持 OOP 语法：class, interface, extends, implements 等
     *
     * @param source 源码
     * @return 格式化后的源码
     */
    public String format(String source) {
        StringBuilder sb = new StringBuilder();
        boolean bol = true; // beginning of line
        LuaLexer lexer = new LuaLexer(source);
        int level = 0;
        int indentWidth = 2;
        
        // 用于跟踪块结构的栈，存储块类型
        java.util.ArrayDeque<LuaTokenTypes> blockStack = new java.util.ArrayDeque<>();
        
        // 跟踪是否刚遇到 abstract 关键字（用于判断抽象方法声明）
        boolean afterAbstract = false;
        
        // 跟踪是否刚遇到 async 关键字（用于判断异步函数）
        boolean afterAsync = false;
        
        while (true) {
            LuaTokenTypes token;
            try {
                token = lexer.advance();
            } catch (IOException e) {
                e.printStackTrace();
                token = null;
            }

            if (token == null) {
                break;
            }

            // 获取token文本，对于长字符串和块注释使用getBlockContent()
            String tokenText = getTokenText(lexer, token);

            if (token == LuaTokenTypes.NEW_LINE) {
                // 删除行尾多余空格
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == ' ') {
                    sb.deleteCharAt(len - 1);
                }
                sb.append('\n');
                bol = true;
                afterAbstract = false;  // 换行时重置 abstract 标志
                afterAsync = false;  // 换行时重置 async 标志
                level = Math.max(0, level);
            } else if (bol) {
                // 行首逻辑
                switch (token) {
                    case UNTIL:
                    case END:
                    case RCURLY:
                        level--;
                        // 弹出块栈
                        if (!blockStack.isEmpty()) {
                            blockStack.pop();
                        }
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        bol = false;
                        break;

                    case WHITE_SPACE:
                        // 跳过行首空白，稍后由我们统一插入
                        break;

                    case CATCH:
                    case FINALLY:
                    case ELSE:
                    case ELSEIF:
                    case CASE:
                    case DEFAULT:
                        // catch/finally/else/elseif/case/default 比块内代码少半个缩进
                        sb.append(createIndent(level * indentWidth - indentWidth / 2));
                        sb.append(tokenText);
                        bol = false;
                        break;

                    // OOP 访问修饰符：与块内代码相同缩进，不改变缩进级别
                    case PUBLIC:
                    case PRIVATE:
                    case PROTECTED:
                    case STATIC:
                    case FINAL:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        bol = false;
                        break;

                    // OOP abstract 关键字：可能修饰 class 或 function
                    case ABSTRACT:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        afterAbstract = true;  // 标记遇到了 abstract
                        bol = false;
                        break;

                    // async 关键字：修饰函数，后面需要空格
                    case ASYNC:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        sb.append(' ');  // async 后面加空格
                        afterAsync = true;  // 标记遇到了 async
                        bol = false;
                        break;

                    case DOUBLE_COLON:
                    case AT:
                        sb.append(tokenText);
                        bol = false;
                        break;

                    // 注释：保持当前缩进
                    case SHORT_COMMENT:
                    case DOC_COMMENT:
                    case BLOCK_COMMENT:
                    case REGION:
                    case ENDREGION:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        bol = false;
                        break;

                    // OOP 类/接口/结构体/概念/命名空间定义
                    case CLASS:
                    case INTERFACE:
                    case STRUCT:
                    case SUPERSTRUCT:
                    case CONCEPT:
                    case NAMESPACE:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        level++;
                        blockStack.push(token);
                        bol = false;
                        break;

                    // 函数定义：需要检查是否在 interface 内部或是抽象方法声明
                    case FUNCTION:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        // 抽象方法声明或接口内的方法声明不增加缩进（没有函数体）
                        if (!afterAbstract && (blockStack.isEmpty() || blockStack.peek() != LuaTokenTypes.INTERFACE)) {
                            level++;
                            blockStack.push(token);
                        }
                        afterAbstract = false;  // 重置标志
                        bol = false;
                        break;

                    // 动态关键字和运算符重载：增加缩进级别
                    case KEYWORD:
                    case OPERATOR_KW:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        level++;
                        blockStack.push(token);
                        bol = false;
                        break;

                    default:
                        sb.append(createIndent(level * indentWidth));
                        sb.append(tokenText);
                        int indentChange = indent(token);
                        level += indentChange;
                        // 对于其他增加缩进的块，也入栈
                        if (indentChange > 0) {
                            blockStack.push(token);
                        }
                        bol = false;
                        break;
                }
            } else {
                /* ---------- 非行首逻辑 ---------- */
                if (token == LuaTokenTypes.WHITE_SPACE) {
                    // 如果前一个 token 是 require 或 import，且下一个可见字符是 '('，则丢弃空格
                    int next = lexer.yychar() + lexer.yylength();
                    while (next < source.length() && source.charAt(next) == ' ') next++;

                    if (next < source.length() && source.charAt(next) == '(') {
                        String prev = sb.toString();
                        if (prev.endsWith("require") || prev.endsWith("import")) {
                            continue;   // 丢弃空格
                        }
                    }

                    // 普通空格：最多保留一个
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                        sb.append(' ');
                    }
                } else {
                    // 其他 token：直接追加原文本
                    sb.append(tokenText);
                    
                    // 非行首时的特殊处理
                    if (token == LuaTokenTypes.CLASS || token == LuaTokenTypes.INTERFACE) {
                        // abstract class / abstract interface 的情况
                        // CLASS/INTERFACE 在非行首（被 abstract 修饰），仍需增加缩进
                        level++;
                        blockStack.push(token);
                        afterAbstract = false;  // 重置标志
                    } else if (token == LuaTokenTypes.FUNCTION) {
                        // 非行首的 function（可能被 abstract/public/private 等修饰）
                        // 抽象方法声明或接口内的方法声明不增加缩进
                        // async function 也不增加缩进（因为 function 前面已经有 async 处理）
                        if (!afterAbstract && !afterAsync && (blockStack.isEmpty() || blockStack.peek() != LuaTokenTypes.INTERFACE)) {
                            level++;
                            blockStack.push(token);
                        }
                        afterAbstract = false;  // 重置标志
                        afterAsync = false;  // 重置标志
                    } else if (token == LuaTokenTypes.ABSTRACT) {
                        // 非行首遇到 abstract（比如在修饰符之后）
                        afterAbstract = true;
                    } else if (token == LuaTokenTypes.ASYNC) {
                        // async 关键字：后面不加缩进（不是块开始）
                        afterAsync = true;  // 标记遇到了 async
                    } else if (token == LuaTokenTypes.KEYWORD || token == LuaTokenTypes.OPERATOR_KW) {
                        // 动态关键字和运算符重载：增加缩进级别
                        level++;
                        blockStack.push(token);
                    } else {
                        // 其他 token 的缩进处理
                        int indentChange = indent(token);
                        if (indentChange > 0) {
                            level += indentChange;
                            blockStack.push(token);
                        } else if (indentChange < 0) {
                            level += indentChange;
                            if (!blockStack.isEmpty()) {
                                blockStack.pop();
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 获取token的文本内容。
     * 对于长字符串(LONG_STRING)和块注释(BLOCK_COMMENT)，使用getBlockContent()获取完整内容。
     * 对于其他token，使用yytext()获取。
     *
     * @param lexer 词法分析器
     * @param token token类型
     * @return token的文本内容
     */
    private String getTokenText(LuaLexer lexer, LuaTokenTypes token) {
        if (token == LuaTokenTypes.LONG_STRING || token == LuaTokenTypes.BLOCK_COMMENT) {
            return lexer.getBlockContent();
        }
        return lexer.yytext();
    }

    private static char[] createIndent(int size) {
        if (size <= 0) {
            return new char[0];
        }
        char[] spaces = new char[size];
        for (int i = 0; i < size; i++) {
            spaces[i] = ' ';
        }
        return spaces;
    }
}
