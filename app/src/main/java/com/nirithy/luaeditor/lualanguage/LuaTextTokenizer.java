package com.nirithy.luaeditor.lualanguage;

import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.util.TrieTree;

import java.util.ArrayList;
import java.util.List;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LuaTextTokenizer {
    private static TrieTree<Tokens> keywords;
    private CharSequence source;
    protected int bufferLen;
    private int line;
    private int column;
    private int index;
    protected int offset;
    protected int length;
    private Tokens currToken;
    private boolean lcCal;
    private int eqCount = 0;
    private int closeEqCount = 0;
    protected static String[] sKeywords;
    private State state;
    private int longCommentEqualCount = 0;
    private int longStringEqualCount = 0; // 长字符串等号计数
    private boolean inLongString = false; // 是否在长字符串中

    private Set<String> classNames = new HashSet<>();

    // 短类名缓存（类名 -> 是否存在）
    private Map<String, Boolean> shortNameMap = null;

    // 缓存版本标记
    private int cacheVersion = -1;
    private static int globalCacheVersion = 0;

    private int classNamesVersion = -1; // 初始化为-1，表示未设置

    private List<HighlightToken> tokens = new ArrayList<>();

    static {
        doStaticInit();
    }

    public static TrieTree<Tokens> getTree() {
        return keywords;
    }

    public LuaTextTokenizer(CharSequence src, State state) {
        if (src == null) {
            throw new IllegalArgumentException("src can not be null");
        }
        this.source = src;
        this.state = state;
        init();
        this.tokens = new ArrayList<>();
    }

    private void init() {
        this.line = 0;
        this.column = 0;
        this.length = 0;
        this.index = 0;
        this.currToken = Tokens.WHITESPACE;
        this.lcCal = false;
        this.bufferLen = this.source.length();
    }

    public void setClassNames(Collection<String> classNames) {
        this.classNames.clear();
        if (classNames != null) {
            this.classNames.addAll(classNames);

            // 构建短类名缓存
            shortNameMap = new HashMap<>();
            for (String fullName : classNames) {
                int lastDot = fullName.lastIndexOf('.');
                if (lastDot != -1) {
                    String shortName = fullName.substring(lastDot + 1);
                    shortNameMap.put(shortName, true);
                }
            }

            // 更新缓存版本
            cacheVersion = ++globalCacheVersion;
        } else {
            shortNameMap = null;
        }
    }

    public void setLongStringEqualCount(int count) {
        this.longStringEqualCount = count;
    }

    public void setInLongString(boolean inLongString) {
        this.inLongString = inLongString;
    }

    public boolean isInLongString() {
        return this.inLongString;
    }

    // 获取长注释等号计数
    public int getLongCommentEqualCount() {
        return longCommentEqualCount;
    }

    // 获取长字符串等号计数
    public int getLongStringEqualCount() {
        return longStringEqualCount;
    }

    // 设置类名版本
    public void setClassNamesVersion(int version) {
        this.classNamesVersion = version;
    }

    // 获取类名版本
    public int getClassNamesVersion() {
        return this.classNamesVersion;
    }

    public void setCalculateLineColumn(boolean cal) {
        this.lcCal = cal;
    }

    public void pushBack(int length) {
        if (length > getTokenLength()) {
            throw new IllegalArgumentException("pushBack length too large");
        }
        this.length -= length;
    }

    private boolean isIdentifierPart(char ch) {
        return MyCharacter.isJavaIdentifierPart(ch);
    }

    private boolean isIdentifierStart(char ch) {
        return MyCharacter.isJavaIdentifierStart(ch);
    }

    public CharSequence getTokenText() {
        return this.source.subSequence(this.offset, this.offset + this.length);
    }

    public int getTokenLength() {
        return this.length;
    }

    public int getLine() {
        return this.line;
    }

    public int getColumn() {
        return this.column;
    }

    public int getIndex() {
        return this.index;
    }

    public Tokens getToken() {
        return this.currToken;
    }

    private char charAt(int i) {
        return this.source.charAt(i);
    }

    private char charAt() {
        return this.source.charAt(this.offset + this.length);
    }

    public Tokens nextToken() {
        Tokens nextTokenInternal = nextTokenInternal();
        this.currToken = nextTokenInternal;
        return nextTokenInternal;
    }

    // 创建Token
    private Tokens createToken(Tokens tokenType, int length) {
        this.length = length;
        return tokenType;
    }

    public Tokens nextTokenInternal() {
        tokens.clear();
        if (this.lcCal) {
            boolean r = false;
            for (int i = this.offset; i < this.offset + this.length; i++) {
                char ch = charAt(i);
                if (ch == '\r') {
                    r = true;
                    this.line++;
                    this.column = 0;
                } else if (ch == '\n') {
                    if (r) {
                        r = false;
                    } else {
                        this.line++;
                        this.column = 0;
                    }
                } else {
                    r = false;
                    this.column++;
                }
            }
        }
        this.index += this.length;
        this.offset += this.length;
        if (this.offset >= this.bufferLen) {
            return Tokens.EOF;
        }
        char ch2 = this.source.charAt(this.offset);
        this.length = 1;
        if (ch2 == '\n') {
            return Tokens.NEWLINE;
        }
        if (ch2 == '\r') {
            scanNewline();
            return Tokens.NEWLINE;
        } else if (isWhitespace(ch2)) {
            while (this.offset + this.length < this.bufferLen) {
                char chLocal = charAt(this.offset + this.length);
                if (!isWhitespace(chLocal) || chLocal == '\r' || chLocal == '\n') {
                    break;
                }
                this.length++;
            }
            return Tokens.WHITESPACE;
        } else if (isIdentifierStart(ch2)) {
            return scanIdentifier(ch2);
        } else {
            char nextch = 0;
            if (this.offset + 1 < this.bufferLen) {
                nextch = this.source.charAt(this.offset + 1);
            }

            // 处理三字符运算符
            if (this.offset + 2 < this.bufferLen) {
                String threeCharSeq = source.subSequence(offset, offset + 3).toString();
                switch (threeCharSeq) {
                    case "<--":
                        this.length = 3;
                        return Tokens.ARROW_LEFT_LONG;
                    case "-->":
                        this.length = 3;
                        return Tokens.ARROW_RIGHT_LONG;
                    case "<=>":
                        this.length = 3;
                        return Tokens.SPACESHIP;
                    case "..=":
                        this.length = 3;
                        return Tokens.DOT_DOT_EQ;
                    case "..<":
                        this.length = 3;
                        return Tokens.DOT_DOT_LT;
                    case "?..":
                        this.length = 3;
                        return Tokens.QUESTION_DOT_DOT;
                    case "===":
                        this.length = 3;
                        return Tokens.EQEQEQ;
                    case "!==":
                        this.length = 3;
                        return Tokens.NEQEQ;
                    case "/**":
                        this.length = 3;
                        return Tokens.SLASH_STAR_STAR;
                    case "###":
                        this.length = 3;
                        return Tokens.HASH_HASH_HASH;
                }
            }

            // 处理两字符运算符
            if (this.offset + 1 < this.bufferLen) {
                String twoCharSeq = source.subSequence(offset, offset + 2).toString();
                switch (twoCharSeq) {
                    case "->":
                        this.length = 2;
                        return Tokens.ARROW;
                    case "<-":
                        this.length = 2;
                        return Tokens.ARROW_LEFT;
                    case "=>":
                        this.length = 2;
                        return Tokens.FAT_ARROW;
                    case "==":
                        this.length = 2;
                        return Tokens.EQEQ;
                    case "!=":
                        this.length = 2;
                        return Tokens.NEQ;
                    case "~=":
                        this.length = 2;
                        return Tokens.TILDE_EQ;
                    case "<=":
                        this.length = 2;
                        return Tokens.LEQ;
                    case ">=":
                        this.length = 2;
                        return Tokens.GEQ;
                    case "++":
                        this.length = 2;
                        return Tokens.PLUS_PLUS;
                    case "+=":
                        this.length = 2;
                        return Tokens.PLUS_EQ;
                    case "-=":
                        this.length = 2;
                        return Tokens.MINUS_EQ;
                    case "*=":
                        this.length = 2;
                        return Tokens.STAR_EQ;
                    case "/=":
                        this.length = 2;
                        return Tokens.SLASH_EQ;
                    case "%=":
                        this.length = 2;
                        return Tokens.PERCENT_EQ;
                    case "&=":
                        this.length = 2;
                        return Tokens.AMP_EQ;
                    case "|=":
                        this.length = 2;
                        return Tokens.BAR_EQ;
                    case "^=":
                        this.length = 2;
                        return Tokens.CARET_EQ;
                    case "=<":
                        this.length = 2;
                        return Tokens.EQ_LT;
                    case "::":
                        this.length = 2;
                        return Tokens.COLON_COLON;
                    case "..":
                        {
                            // 如果后面还有更多的点，也统一算作 DOT_DOT
                            int dotCount = 2;
                            while (offset + dotCount < bufferLen
                                    && charAt(offset + dotCount) == '.') {
                                dotCount++;
                            }
                            this.length = dotCount;
                            return Tokens.DOT_DOT;
                        }
                    case "//":
                        this.length = 2;
                        return Tokens.SLASH_SLASH;
                    case "\\\\":
                        this.length = 2;
                        return Tokens.BACKSLASH_BACKSLASH;
                    case "/*":
                        this.length = 2;
                        return Tokens.SLASH_STAR;
                    case "*/":
                        this.length = 2;
                        return Tokens.STAR_SLASH;
                    case "~~":
                        this.length = 2;
                        return Tokens.TILDE_TILDE;
                    case "^^":
                        this.length = 2;
                        return Tokens.CARET_CARET;
                    case "##":
                        this.length = 2;
                        return Tokens.HASH_HASH;
                    case "$$":
                        this.length = 2;
                        return Tokens.DOLLAR_DOLLAR;
                    case "@@":
                        this.length = 2;
                        return Tokens.AT_AT;
                    case ":=":
                        this.length = 2;
                        return Tokens.COLON_EQ;
                    case "=:":
                        this.length = 2;
                        return Tokens.EQ_COLON;
                    case "?.":
                        this.length = 2;
                        return Tokens.QUESTION_DOT;
                    case "?:":
                        this.length = 2;
                        return Tokens.QUESTION_COLON;
                    case "?=":
                        this.length = 2;
                        return Tokens.QUESTION_EQ;
                    case "?-":
                        this.length = 2;
                        return Tokens.QUESTION_MINUS;
                    case "?+":
                        this.length = 2;
                        return Tokens.QUESTION_PLUS;
                    case "!!":
                        this.length = 2;
                        return Tokens.NOT_NOT;
                    case "??":
                        this.length = 2;
                        return Tokens.NULL_COALESCING;
                    case "**":
                        this.length = 2;
                        return Tokens.STAR_STAR;
                    case "-|":
                        this.length = 2;
                        return Tokens.MINUS_BAR;
                    case "|>":
                        this.length = 2;
                        return Tokens.BAR_GT;
                    case "<|":
                        this.length = 2;
                        return Tokens.LT_BAR;
                    case ">>":
                        this.length = 2;
                        return Tokens.GTGT;
                    case "<<":
                        this.length = 2;
                        return Tokens.LTLT;
                }
            }

            // 修改 1：处理 #RRGGBB
            if (ch2 == '#') {
                int len = 1;
                while (offset + len < bufferLen && isHexDigit(charAt(offset + len))) {
                    len++;
                }
                int hexLength = len - 1;
                if (hexLength == 6 || hexLength == 8) {
                    this.length = len;
                    HighlightToken token = new HighlightToken(Tokens.HEX_COLOR, this.offset);
                    if (LuaIncrementalAnalyzeManager.getInstance() != null
                            && LuaIncrementalAnalyzeManager.getInstance()
                                    .isHexColorHighlightEnabled()
                            && len <= 10) {
                        token.text =
                                this.source.subSequence(this.offset, this.offset + len).toString();
                    }
                    tokens.add(token);
                    return Tokens.HEX_COLOR;
                }
            }

            // 修改 2：处理 0xRRGGBB
            if (ch2 == '0' && nextch == 'x') {
                int len = 2;
                while (offset + len < bufferLen && isHexDigit(charAt(offset + len))) {
                    len++;
                }
                int hexLength = len - 2;
                if (hexLength == 6 || hexLength == 8) {
                    this.length = len;
                    HighlightToken token = new HighlightToken(Tokens.HEX_COLOR, this.offset);
                    if (LuaIncrementalAnalyzeManager.getInstance() != null
                            && LuaIncrementalAnalyzeManager.getInstance()
                                    .isHexColorHighlightEnabled()
                            && len <= 10) {
                        token.text =
                                this.source.subSequence(this.offset, this.offset + len).toString();
                    }
                    tokens.add(token);
                    return Tokens.HEX_COLOR;
                }
            }

            // 在switch语句前增加对长字符串的识别
            if (ch2 == '[') {
                // 检查是否是长字符串开始
                int nextIndex = offset + 1;
                int eqCount = 0;

                // 计算等号数量
                while (nextIndex < bufferLen && charAt(nextIndex) == '=') {
                    eqCount++;
                    nextIndex++;
                }

                // 检查是否有匹配的']'
                if (nextIndex < bufferLen && charAt(nextIndex) == '[') {
                    // 是长字符串开始
                    this.length = nextIndex - offset + 1;
                    return scanLongString(false);
                }
            }

            if (isPrimeDigit(ch2)) {
                return scanNumber();
            }

            switch (ch2) {
                case ';':
                    return Tokens.SEMICOLON;
                case '(':
                    return Tokens.LPAREN;
                case ')':
                    return Tokens.RPAREN;
                case ':':
                    return Tokens.COLON;
                case '<':
                    return scanLT();
                case '>':
                    return scanGT();
                case '!':
                    if (nextch == '=') {
                        this.length = 2;
                        return Tokens.NEQ;
                    }
                    return Tokens.NOT;
                case '\"':
                case '\'':
                    scanStringLiteral();
                    return Tokens.STRING;
                case '#':
                case '$':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                case 'G':
                case 'H':
                case 'I':
                case 'J':
                case 'K':
                case 'L':
                case 'M':
                case 'N':
                case 'O':
                case 'P':
                case 'Q':
                case 'R':
                case 'S':
                case 'T':
                case 'U':
                case 'V':
                case 'W':
                case 'X':
                case 'Y':
                case 'Z':
                case '\\':
                case '_':
                case '`':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                case 'g':
                case 'h':
                case 'i':
                case 'j':
                case 'k':
                case 'l':
                case 'm':
                case 'n':
                case 'o':
                case 'p':
                case 'q':
                case 'r':
                case 's':
                case 't':
                case 'u':
                case 'v':
                case 'w':
                case 'x':
                case 'y':
                case 'z':
                default:
                    return Tokens.UNKNOWN;
                case '%':
                    return scanOperatorTwo(Tokens.MOD);
                case '&':
                    return scanOperatorTwo(Tokens.AND);
                case '*':
                    return scanOperatorTwo(Tokens.MUL);
                case '+':
                    if (nextch == '=') {
                        this.length = 2;
                        return Tokens.AEQ;
                    }
                    return scanOperatorTwo(Tokens.ADD);
                case ',':
                    return Tokens.COMMA;
                case '-':
                    if (nextch == '>') {
                        this.length = 2;
                        return Tokens.ARROW;
                    }
                    return scanDIV();
                case '.':
                    if (nextch == '=') {
                        this.length = 2;
                        return scanOperatorTwo(Tokens.DOTEQ);
                    }
                    return Tokens.DOT;
                case '/':
                    return scanDIV();
                case '=':
                    if (nextch == '=') {
                        this.length = 2;
                        return scanOperatorTwo(Tokens.EQEQ);
                    } else if (nextch == '>') {
                        this.length = 2;
                        return Tokens.OP;
                    } else {
                        return scanOperatorTwo(Tokens.EQ);
                    }
                case '?':
                    return Tokens.QUESTION;
                case '@':
                    return Tokens.AT;
                case '[':
                    return Tokens.LBRACK;
                case '^':
                    return scanOperatorTwo(Tokens.POW);
                case '{':
                    return Tokens.LBRACE;
                case '|':
                    if (nextch == '>') {
                        this.length = 2;
                        return Tokens.OP;
                    }
                    return scanOperatorTwo(Tokens.OR);
                case '}':
                    return Tokens.RBRACE;
                case '~':
                    if (nextch == '=') {
                        this.length = 2;
                        return Tokens.NEQ;
                    }
                    return Tokens.XOR;
            }
        }
    }

    protected final void throwIfNeeded() {
        if (this.offset + this.length >= this.bufferLen) {
            throw new RuntimeException("Token too long");
        }
    }

    protected void scanNewline() {
        if (this.offset + this.length < this.bufferLen
                && charAt(this.offset + this.length) == '\n') {
            this.length++;
        }
    }

    protected Tokens scanIdentifier(char c) {
        TrieTree.Node node = (TrieTree.Node) keywords.root.map.get(c);
        int startOffset = this.offset; // 保存起始位置
        int identifierStart = startOffset; // 标识符开始位置

        // 扫描标识符
        while (true) {
            int currentOffset = this.offset;
            int currentLength = this.length;

            // 检查是否超出边界
            if (currentOffset + currentLength >= this.bufferLen) {
                break;
            }

            // 获取下一个字符
            char nextChar = charAt(currentOffset + currentLength);

            // 检查是否是标识符的一部分
            if (!isIdentifierPart(nextChar)) {
                break;
            }

            // 增加长度并更新节点
            this.length++;
            node = node == null ? null : (TrieTree.Node) node.map.get(nextChar);
        }

        // 获取标识符文本
        String identifier =
                this.source.subSequence(identifierStart, identifierStart + this.length).toString();

        // 优化点1：使用HashSet的O(1)查找
        if (shortNameMap != null && shortNameMap.containsKey(identifier)) {
            return Tokens.CLASS_NAME;
        }

        // 3. 关键词匹配
        if (node != null && node.token != null) {
            return (Tokens) node.token;
        }

        // 默认返回标识符
        return Tokens.IDENTIFIER;
    }

    protected void scanTrans() {
        throwIfNeeded();
        char ch = charAt();
        if (ch == '\\'
                || ch == 't'
                || ch == 'f'
                || ch == 'n'
                || ch == 'r'
                || ch == '0'
                || ch == '\"'
                || ch == '\''
                || ch == 'b') {
            this.length++;
        } else if (ch == 'u') {
            this.length++;
            for (int i = 0; i < 4; i++) {
                throwIfNeeded();
                if (!isDigit(charAt(this.offset + this.length))) {
                    return;
                }
                this.length++;
            }
        }
    }

    // 扫描长字符串
    public Tokens scanLongString(boolean isContinuation) {
        int startOffset = this.offset;
        int eqCount = 0;

        if (!isContinuation) {
            // 解析开始标记
            int i = offset + 1; // 跳过第一个'['
            while (i < bufferLen && charAt(i) == '=') {
                eqCount++;
                i++;
            }

            if (i < bufferLen && charAt(i) == '[') {
                this.length = i - offset + 1;
            } else {
                // 不是有效的长字符串，回退到普通括号
                this.length = 1;
                return Tokens.LBRACK;
            }
        } else {
            // 继续上一行的长字符串，使用状态中的等号计数
            eqCount = this.longStringEqualCount;
        }

        // 保存等号计数用于状态管理
        this.longStringEqualCount = eqCount;

        // 扫描直到找到匹配的结束标记
        while (offset + length < bufferLen) {
            char ch = charAt(offset + length);

            if (ch == ']') {
                // 检查是否是结束标记
                int endIndex = offset + length + 1;
                int endEqCount = 0;

                // 计算结束标记的等号数量
                while (endIndex < bufferLen && charAt(endIndex) == '=') {
                    endEqCount++;
                    endIndex++;
                }

                // 检查是否有匹配的']'
                if (endIndex < bufferLen && charAt(endIndex) == ']' && endEqCount == eqCount) {
                    // 找到匹配的结束标记
                    length = endIndex - offset + 1;
                    return Tokens.LONG_STRING;
                }
            }

            length++;
        }

        // 没有找到结束标记，返回不完整的长字符串
        return Tokens.LONG_STRING_INCOMPLETE;
    }

    protected void scanStringLiteral() {
        if (this.offset + 1 >= this.bufferLen) {
            return;
        }

        char quote = charAt(this.offset); // 记录起始引号字符：" 或 '
        int start = this.offset + 1; // 跳过起始引号

        while (this.offset + this.length < this.bufferLen) {
            char ch = charAt(this.offset + this.length);

            // 转义字符处理
            if (ch == '\\') {
                this.length++;
                scanTrans();
                continue;
            }

            // 字符串结束
            if (ch == quote) {
                this.length++; // 包含结束引号
                return;
            }

            // 未闭合字符串（遇到换行）
            if (ch == '\n') {
                return;
            }

            // 十六进制颜色识别（仅处理 #RRGGBB / 0xRRGGBB）
            if (ch == '#'
                    || (ch == '0'
                            && this.offset + this.length + 1 < this.bufferLen
                            && charAt(this.offset + this.length + 1) == 'x')) {

                // 保存当前普通字符串片段
                if (this.offset + this.length > start) {
                    tokens.add(new HighlightToken(Tokens.STRING, start));
                }

                // 记录颜色起始位置
                int colorStart = this.offset + this.length;
                int len = (ch == '#') ? 1 : 2; // # 开头跳过1，0x 开头跳过2
                int hexOffset = len;

                // 扫描十六进制字符
                while (this.offset + this.length + len < this.bufferLen
                        && isHexDigit(charAt(this.offset + this.length + len))) {
                    len++;
                }

                int hexLength = len - hexOffset;
                if (hexLength == 6 || hexLength == 8) { // 有效颜色长度
                    this.length += len;
                    HighlightToken colorToken = new HighlightToken(Tokens.HEX_COLOR, colorStart);
                    if (LuaIncrementalAnalyzeManager.getInstance() != null
                            && LuaIncrementalAnalyzeManager.getInstance()
                                    .isHexColorHighlightEnabled()
                            && len <= 10) {
                        colorToken.text =
                                this.source.subSequence(colorStart, colorStart + len).toString();
                    }
                    tokens.add(colorToken);
                    start = this.offset + this.length; // 更新后续字符串起始位置
                    continue;
                }
            }

            // 普通字符
            this.length++;
        }
    }

    protected Tokens scanNumber() {
        if (this.offset + this.length == this.bufferLen) {
            return Tokens.NUMBER;
        }
        boolean flag = false;
        // 修改：支持带下划线的数字 (如 1_000, 1_11, 12_____12)
        // 允许数字中包含下划线，但必须确保开头是数字
        if (this.offset < this.bufferLen && isDigit(charAt())) {
            // 第一个字符必须是数字
            this.length++;
            
            while (this.offset + this.length < this.bufferLen) {
                char currentChar = charAt();
                if (isDigit(currentChar)) {
                    this.length++;
                } else if (currentChar == '_') {
                    // 允许下划线，但下划线后必须是数字
                    this.length++;
                    // 检查下划线后是否还有数字字符
                    if (this.offset + this.length < this.bufferLen) {
                        char nextChar = charAt();
                        if (!isDigit(nextChar)) {
                            // 下划线后不是数字，结束扫描
                            break;
                        }
                    } else {
                        // 下划线在末尾，回退
                        this.length--;
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        
        if (this.offset + this.length == this.bufferLen) {
            return Tokens.NUMBER;
        }
        char ch = charAt();
        if (ch == '.') {
            if (flag) {
                return Tokens.NUMBER;
            }
            if (this.offset + this.length + 1 == this.bufferLen) {
                return Tokens.NUMBER;
            }
            this.length++;
            throwIfNeeded();
            // 修改：支持带下划线的小数部分
            while (this.offset + this.length < this.bufferLen) {
                char currentChar = charAt();
                if (isDigit(currentChar)) {
                    this.length++;
                } else if (currentChar == '_') {
                    this.length++;
                    // 检查下划线后是否还有数字字符
                    if (this.offset + this.length < this.bufferLen) {
                        char nextChar = charAt();
                        if (!isDigit(nextChar)) {
                            // 下划线后不是数字，结束扫描
                            break;
                        }
                    } else {
                        // 下划线在末尾，回退
                        this.length--;
                        break;
                    }
                } else {
                    break;
                }
            }
            
            if (this.offset + this.length == this.bufferLen) {
                return Tokens.NUMBER;
            }
            char ch2 = charAt();
            if (ch2 == 'e' || ch2 == 'E') {
                this.length++;
                throwIfNeeded();
                if (charAt() == '-' || charAt() == '+') {
                    this.length++;
                    throwIfNeeded();
                }
                // 修改：支持带下划线的指数部分
                while (this.offset + this.length < this.bufferLen) {
                    char currentChar = charAt();
                    if (isPrimeDigit(currentChar)) {
                        this.length++;
                    } else if (currentChar == '_') {
                        this.length++;
                        // 检查下划线后是否还有数字字符
                        if (this.offset + this.length < this.bufferLen) {
                            char nextChar = charAt();
                            if (!isPrimeDigit(nextChar)) {
                                // 下划线后不是数字，结束扫描
                                break;
                            }
                        } else {
                            // 下划线在末尾，回退
                            this.length--;
                            break;
                        }
                    } else {
                        break;
                    }
                }
                
                if (this.offset + this.length == this.bufferLen) {
                    return Tokens.NUMBER;
                }
                ch2 = charAt();
            }
            if (ch2 == 'f' || ch2 == 'F' || ch2 == 'D' || ch2 == 'd') {
                this.length++;
            }
            return Tokens.NUMBER;
        } else if (ch == 'l' || ch == 'L') {
            this.length++;
            return Tokens.NUMBER;
        } else if (ch == 'F' || ch == 'f' || ch == 'D' || ch == 'd') {
            this.length++;
            return Tokens.NUMBER;
        } else {
            return Tokens.NUMBER;
        }
    }

    protected Tokens scanDIV() {
        if (this.offset + 1 >= this.bufferLen) {
            return Tokens.DIV;
        }
        char ch = charAt(this.offset);
        char nextChar = charAt(this.offset + 1);
        if (ch == '-' && nextChar == '-') {
            this.longCommentEqualCount = 0;
            int i = this.offset + 2;
            if (i < this.bufferLen && charAt(i) == '[') {
                // 可能是长注释
                i++; // 跳过'['
                while (i < this.bufferLen && charAt(i) == '=') {
                    this.longCommentEqualCount++;
                    i++;
                }
                if (i < this.bufferLen && charAt(i) == '[') {
                    // 是长注释开始
                    this.length = i - this.offset + 1;
                    boolean finished = false;
                    while (true) {
                        if (this.offset + this.length >= this.bufferLen) {
                            break;
                        }
                        if (charAt(this.offset + this.length) == ']') {
                            int j = this.offset + this.length + 1;
                            int closeEqCount = 0;
                            while (j < this.bufferLen && charAt(j) == '=') {
                                closeEqCount++;
                                j++;
                            }
                            if (j < this.bufferLen
                                    && charAt(j) == ']'
                                    && closeEqCount == this.longCommentEqualCount) {
                                this.length = j - this.offset + 1;
                                finished = true;
                                break;
                            }
                        }
                        this.length++;
                    }
                    return finished ? Tokens.LONG_COMMENT_COMPLETE : Tokens.LONG_COMMENT_INCOMPLETE;
                }
            }
            // 单行注释
            while (this.offset + this.length < this.bufferLen
                    && charAt(this.offset + this.length) != '\n') {
                this.length++;
            }
            return Tokens.LINE_COMMENT;
        }
        return Tokens.DIV;
    }

    public Tokens scanLongComment(boolean isContinuation) {
        int startOffset = this.offset;
        int eqCount = 0;

        if (!isContinuation) {
            // 解析开始标记
            int i = offset + 2; // 跳过"--"
            if (i < bufferLen && charAt(i) == '[') {
                i++; // 跳过'['
                while (i < bufferLen && charAt(i) == '=') {
                    eqCount++;
                    i++;
                }

                if (i < bufferLen && charAt(i) == '[') {
                    this.length = i - offset + 1;
                } else {
                    // 不是有效的长注释，回退到单行注释
                    while (offset + length < bufferLen && charAt(offset + length) != '\n') {
                        length++;
                    }
                    return Tokens.LINE_COMMENT;
                }
            } else {
                // 不是长注释，回退到单行注释
                while (offset + length < bufferLen && charAt(offset + length) != '\n') {
                    length++;
                }
                return Tokens.LINE_COMMENT;
            }
        } else {
            // 继续上一行的长注释
            eqCount = this.longCommentEqualCount;
        }

        // 保存等号计数用于状态管理
        this.longCommentEqualCount = eqCount;

        // 扫描直到找到匹配的结束标记
        while (offset + length < bufferLen) {
            char ch = charAt(offset + length);

            if (ch == ']') {
                // 检查是否是结束标记
                int endIndex = offset + length + 1;
                int endEqCount = 0;

                // 计算结束标记的等号数量
                while (endIndex < bufferLen && charAt(endIndex) == '=') {
                    endEqCount++;
                    endIndex++;
                }

                // 检查是否有匹配的']'
                if (endIndex < bufferLen && charAt(endIndex) == ']' && endEqCount == eqCount) {
                    // 找到匹配的结束标记
                    length = endIndex - offset + 1;
                    return Tokens.LONG_COMMENT_COMPLETE;
                }
            }

            length++;
        }

        // 没有找到结束标记，返回不完整的长注释
        return Tokens.LONG_COMMENT_INCOMPLETE;
    }

    public boolean isAssignment() {
        Tokens current = getToken();
        Tokens next = nextToken();
        if (current == Tokens.IDENTIFIER && next == Tokens.ASSGN) {
            return true;
        }
        pushBack(1);
        return false;
    }

    public String getVariableName() {
        if (getToken() == Tokens.IDENTIFIER) {
            return getTokenText().toString();
        }
        return null;
    }

    public String getVariableValue() {
        Tokens token = nextToken();
        if (token == Tokens.NUMBER || token == Tokens.STRING) {
            return getTokenText().toString();
        }
        pushBack(1);
        return null;
    }

    protected Tokens scanLT() {
        if (this.offset + 1 < this.bufferLen) {
            char ch = this.source.charAt(this.offset + 1);
            switch (ch) {
                case ':':
                    this.length = 2;
                    return Tokens.CLT;
                case '<':
                    this.length = 2;
                    return Tokens.LTLT;
                case '=':
                    if (this.offset + 2 < this.bufferLen
                            && this.source.charAt(this.offset + 2) == '>') {
                        this.length = 3;
                        return Tokens.OP;
                    }
                    this.length = 2;
                    return Tokens.LEQ;
                case '>':
                    this.length = 2;
                    return Tokens.LTGT;
            }
        }
        return Tokens.LT;
    }

    protected Tokens scanGT() {
        if (this.offset + 1 < this.bufferLen) {
            char ch = this.source.charAt(this.offset + 1);
            switch (ch) {
                case '=':
                    this.length = 2;
                    return Tokens.GEQ;
                case '>':
                    this.length = 2;
                    return Tokens.GTGT;
            }
        }
        return Tokens.GT;
    }

    protected Tokens scanOperatorTwo(Tokens ifWrong) {
        return ifWrong;
    }

    public void reset(CharSequence charSequence, State state) {
        reset(charSequence);
        this.state = state;
    }

    public void reset(CharSequence src) {
        if (src == null) {
            throw new IllegalArgumentException();
        }
        this.source = src;
        this.line = 0;
        this.column = 0;
        this.length = 0;
        this.index = 0;
        this.offset = 0;
        this.currToken = Tokens.WHITESPACE;
        this.bufferLen = src.length();
    }

    protected static void doStaticInit() {
        sKeywords =
                new String[] {
                    "and",
                    "break",
                    "const",
                    "do",
                    "else",
                    "elseif",
                    "end",
                    "enum",
                    "false",
                    "for",
                    "function",
                    "global",
                    "goto",
                    "if",
                    "in",
                    "is",
                    "local",
                    "nil",
                    "not",
                    "or",
                    "repeat",
                    "return",
                    "then",
                    "true",
                    "until",
                    "while",
                    "import",
                    "require",
                    "switch",
                    "continue",
                    "command",
                    "case",
                    "default",
                    "call",
                    "collectgarbage",
                    "compile",
                    "coroutine",
                    "assert",
                    "error",
                    "ipairs",
                    "pairs",
                    "next",
                    "print",
                    "rawequal",
                    "rawget",
                    "rawset",
                    "select",
                    "setmetatable",
                    "getmetatable",
                    "tonumber",
                    "tostring",
                    "type",
                    "unpack",
                    "lambda",
                    "_G",
                    "try",
                    "catch",
                    "finally",
                    "with",
                    "take",
                    "when",
                    "keyword",
                    "operator",
                    "$",
                    "abstract",
                    "class",
                    "extends",
                    "final",
                    "implements",
                    "ointerface",
                    "onew",
                    "osuper",
                    "private",
                    "protected",
                    "public",
                    "static",
                    "pcall",
                    "xpcall",
                    "load",
                    "loadstring",
                    "dofile",
                    "loadfile",
                    "rawlen",
                    "debug",
                    "package"
                };
        Tokens[] sTokens = {
            Tokens.AND,
            Tokens.BREAK,
            Tokens.CONST,
            Tokens.DO,
            Tokens.ELSE,
            Tokens.ELSEIF,
            Tokens.END,
            Tokens.ENUM,
            Tokens.FALSE,
            Tokens.FOR,
            Tokens.FUNCTION,
            Tokens.GLOBAL,
            Tokens.GOTO,
            Tokens.IF,
            Tokens.IN,
            Tokens.IS,
            Tokens.LOCAL,
            Tokens.NIL,
            Tokens.NOT,
            Tokens.OR,
            Tokens.REPEAT,
            Tokens.RETURN,
            Tokens.THEN,
            Tokens.TRUE,
            Tokens.UNTIL,
            Tokens.WHILE,
            Tokens.IMPORT,
            Tokens.REQUIRE,
            Tokens.SWITCH,
            Tokens.CONTINUE,
            Tokens.COMMAND,
            Tokens.CASE,
            Tokens.DEFAULT,
            Tokens.CALL,
            Tokens.COLLECTGARBAGE,
            Tokens.COMPILE,
            Tokens.COROUTINE,
            Tokens.ASSERT,
            Tokens.ERROR,
            Tokens.IPAIRS,
            Tokens.PAIRS,
            Tokens.NEXT,
            Tokens.PRINT,
            Tokens.RAWEQUAL,
            Tokens.RAWGET,
            Tokens.RAWSET,
            Tokens.SELECT,
            Tokens.SETMETATABLE,
            Tokens.GETMETATABLE,
            Tokens.TONUMBER,
            Tokens.TOSTRING,
            Tokens.TYPE,
            Tokens.UNPACK,
            Tokens.LAMBDA,
            Tokens._G,
            Tokens.TRY,
            Tokens.CATCH,
            Tokens.FINALLY,
            Tokens.WITH,
            Tokens.TAKE,
            Tokens.WHEN,
            Tokens.KEYWORD,
            Tokens.OPERATOR_KW,
            Tokens.DOLLAR,
            Tokens.ABSTRACT,
            Tokens.CLASS,
            Tokens.EXTENDS,
            Tokens.FINAL,
            Tokens.IMPLEMENTS,
            Tokens.INTERFACE,
            Tokens.NEW,
            Tokens.SUPER,
            Tokens.PRIVATE,
            Tokens.PROTECTED,
            Tokens.PUBLIC,
            Tokens.STATIC,
            Tokens.PCALL,
            Tokens.XPCALL,
            Tokens.LOAD,
            Tokens.LOADSTRING,
            Tokens.DOFILE,
            Tokens.LOADFILE,
            Tokens.RAWLEN,
            Tokens.DEBUG,
            Tokens.PACKAGE
        };
        keywords = new TrieTree<>();
        for (int i = 0; i < sKeywords.length; i++) {
            keywords.put(sKeywords[i], sTokens[i]);
        }
    }

    public void releaseTokens() {
        if (tokens != null) {
            for (HighlightToken token : tokens) {
                token.text = null; // 释放字符串引用
            }
            tokens.clear();
        }
        this.source = null; // 释放原始内容引用
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    protected static boolean isDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    protected static boolean isPrimeDigit(char c) {
        return c >= '0' && c <= '9';
    }

    protected static boolean isWhitespace(char c) {
        return c == '\t' || c == ' ' || c == '\f' || c == '\n' || c == '\r';
    }
}
