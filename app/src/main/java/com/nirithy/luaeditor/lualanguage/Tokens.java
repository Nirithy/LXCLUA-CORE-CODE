package com.nirithy.luaeditor.lualanguage;

public enum Tokens {
    LONG_COMMENT_COMPLETE,
    LONG_COMMENT_INCOMPLETE,
    LINE_COMMENT,
    CHARACTER_LITERAL,
    WHITESPACE,
    NEWLINE,
    UNKNOWN,
    EOF,
    IDENTIFIER,
    STRING,
    NUMBER,
    FUNCTION,
    IMPORT,
    REQUIRE,
    TRUE,
    AT,
    FALSE,
    IF,
    THEN,
    ELSE,
    ELSEIF,
    END,
    ENUM,
    FOR,
    IN,
    AS,             // as (类型转换)
    LOCAL,
    REPEAT,
    RETURN,
    BREAK,
    UNTIL,
    WHILE,
    DO,
    FUNCTION_NAME,
    GOTO,
    NIL,
    NOT,
    AND,
    OR,
    EQ,
    NEQ,
    LT,
    GT,
    LEQ,
    GEQ,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    POW,
    ASSGN,
    SEMICOLON,
    GLOBAL,
    CONST,
    COMMA,
    COLON,
    DOT,
    LBRACK,
    RBRACK,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    SWITCH,
    CONTINUE,
    COMMAND,
    CASE,
    DEFAULT,
    TRY,
    CATCH,
    FINALLY,
    WITH,
    TAKE,
    WHEN,
    KEYWORD,
    OPERATOR_KW,
    DOLLAR,
    XOR,
    QUESTION,
    EQEQ,
    LTEQ,
    GTEQ,
    DOTEQ,
    LTLT,
    LTGT,
    CLT,
    AEQ,
    GTGT,
    ARROW,
    OP,
    CALL,
    COLLECTGARBAGE,
    COMPILE,
    COROUTINE,
    ASSERT,
    ERROR,
    IPAIRS,
    PAIRS,
    NEXT,
    PRINT,
    RAWEQUAL,
    RAWGET,
    RAWSET,
    SELECT,
    SETMETATABLE,
    GETMETATABLE,
    TONUMBER,
    TOSTRING,
    TYPE,
    UNPACK,
    LAMBDA,
    _G,
    IS,
    HEX_COLOR,
    LONG_STRING,
    LONG_STRING_INCOMPLETE,
    CLASS_NAME,
    
    // Lua 内置函数（补充）
    PCALL,          // pcall (保护调用)
    XPCALL,         // xpcall (扩展保护调用)
    LOAD,           // load (加载代码)
    LOADSTRING,     // loadstring (加载字符串代码)
    DOFILE,         // dofile (执行文件)
    LOADFILE,       // loadfile (加载文件)
    LOADSFILE,      // loadsfile (加载文件)
    RAWLEN,         // rawlen (原始长度)
    DEBUG,          // debug (调试库)
    PACKAGE,        // package (包管理)
    EXPORT,         // export (导出关键字)
    DEFER,          // defer (延迟执行)
    
    // OOP 面向对象关键字
    ABSTRACT,       // abstract (抽象方法/类)
    CLASS,          // class (类定义)
    EXTENDS,        // extends (继承)
    FINAL,          // final (不可重写/继承)
    IMPLEMENTS,     // implements (实现接口)
    INTERFACE,      // interface (接口定义)
    NEW,            // new (创建实例)
    PRIVATE,        // private (私有成员)
    PROTECTED,      // protected (受保护成员)
    PUBLIC,         // public (公开成员)
    STATIC,         // static (静态成员)
    SUPER,          // super (调用父类)

    // 异步关键字
    ASYNC,          // async (异步函数声明)
    AWAIT,          // await (等待异步结果)

    // 结构体关键字
    STRUCT,         // struct (结构体定义)
    SUPERSTRUCT,    // superstruct (超级结构体)

    // 概念/约束关键字
    CONCEPT,        // concept (模板约束)

    // 管道运算符
    PIPE,           // |> 管道运算符
    SAFE_PIPE,     // |>? 安全管道
    REV_PIPE,       // <| 反向管道

    // 命名空间关键字
    NAMESPACE,      // namespace (命名空间)
    USING,          // using (使用命名空间/类型)
    REQUIRES,       // requires (依赖声明)
    
    // 类型关键字
    BOOL,           // bool (布尔类型)
    CHAR,           // char (字符类型)
    DOUBLE,         // double (双精度浮点)
    FLOAT,          // float (单精度浮点)
    TYPE_INT,       // int (整数类型)
    LONG,           // long (长整数)
    VOID,           // void (无返回值)

    ARROW_LEFT_LONG,      // <--
    ARROW_RIGHT_LONG,     // -->
    SPACESHIP,            // <=>
    DOT_DOT_EQ,           // ..=
    DOT_DOT_LT,           // ..<
    QUESTION_DOT_DOT,     // ?..
    NOT_NOT,              // !!
    NULL_COALESCING,      // ??
    STAR_STAR,            // **
    TILDE_TILDE,          // ~~
    CARET_CARET,          // ^^
    HASH_HASH,            // ##
    AT_AT,                // @@
    DOLLAR_DOLLAR,        // $$
    COLON_EQ,             // :=
    EQ_COLON,             // =:
    QUESTION_DOT,         // ?.
    QUESTION_COLON,       // ?:
    QUESTION_EQ,          // ?=
    QUESTION_MINUS,       // ?-
    QUESTION_PLUS,        // ?+
    ARROW_LEFT,           // <-
    TILDE_EQ,             // ~=
    EQEQEQ,               // ===
    NEQEQ,                // !==
    FAT_ARROW,            // =>
    PLUS_PLUS,            // ++
    PLUS_EQ,              // +=
    MINUS_EQ,             // -=
    STAR_EQ,              // *=
    SLASH_EQ,             // /=
    PERCENT_EQ,           // %=
    AMP_EQ,               // &=
    BAR_EQ,               // |=
    CARET_EQ,             // ^=
    EQ_LT,                // =<
    COLON_COLON,          // ::
    DOT_DOT,              // ..
    SLASH_SLASH,          // //
    BACKSLASH_BACKSLASH,  // \\
    SLASH_STAR,           // /*
    STAR_SLASH,           // */
    SLASH_STAR_STAR,      // /**
    HASH_HASH_HASH,       // ###
    MINUS_BAR,            // -|
    BAR_GT,               // |>
    LT_BAR                // <|
    
}
