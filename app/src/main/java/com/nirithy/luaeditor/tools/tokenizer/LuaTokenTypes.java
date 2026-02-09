package com.nirithy.luaeditor.tools.tokenizer;

/**
 * Lua词法Token类型定义
 * 与底层 llex.h 中的 RESERVED 枚举保持一致
 */
public enum LuaTokenTypes {
    // 基础类型
    SHEBANG_CONTENT,
    NEW_LINE,
    WHITE_SPACE,
    BAD_CHARACTER,

    // 标识符和字面量
    NAME,
    NUMBER,
    STRING,
    LONG_STRING,
    RAW_STRING,     // r"..." r[[...]] (原生字符串)

    // 关键字 (与底层 luaX_tokens 保持一致)
    AND,            // and
    ASM,            // asm (内联汇编)
    BREAK,          // break
    CASE,           // case
    CATCH,          // catch
    COMMAND,        // command
    CONST,          // const
    CONTINUE,       // continue
    DEFAULT,        // default
    DO,             // do
    ELSE,           // else
    ELSEIF,         // elseif
    END,            // end
    ENUM,           // enum (枚举定义)
    FALSE,          // false
    FINALLY,        // finally
    FOR,            // for
    FUNCTION,       // function
    GLOBAL,         // global
    GOTO,           // goto
    IF,             // if
    IN,             // in
    AS,             // as (类型转换)
    IS,             // is (类型检查)
    LAMBDA,         // lambda
    LOCAL,          // local
    NIL,            // nil
    NOT,            // not
    OR,             // or
    REPEAT,         // repeat
    RETURN,         // return
    SWITCH,         // switch
    TAKE,           // take
    THEN,           // then
    TRUE,           // true
    TRY,            // try
    UNTIL,          // until
    WHEN,           // when
    WITH,           // with
    WHILE,          // while
    KEYWORD,        // keyword (动态关键字)
    OPERATOR_KW,    // operator (运算符重载)
    DOLLAR,         // $
    EXPORT,         // export

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

    // 运算符
    PLUS,           // +
    MINUS,          // -
    MULT,           // *
    DIV,            // /
    DOUBLE_DIV,     // //
    MOD,            // %
    EXP,            // ^
    GETN,           // #
    BIT_AND,        // &
    BIT_OR,         // |
    BIT_TILDE,      // ~
    BIT_LTLT,       // <<
    BIT_RTRT,       // >>

    // 比较运算符
    EQ,             // ==
    NE,             // ~=
    LT,             // <
    GT,             // >
    LE,             // <=
    GE,             // >=
    SPACESHIP,      // <=> (三路比较运算符)

    // 赋值和其他运算符
    ASSIGN,         // =
    CONCAT,         // ..
    ELLIPSIS,       // ...
    DOT,            // .
    COLON,          // :
    DOUBLE_COLON,   // ::
    SEMI,           // ;
    COMMA,          // ,
    AT,             // @

    // 括号
    LPAREN,         // (
    RPAREN,         // )
    LBRACK,         // [
    RBRACK,         // ]
    LCURLY,         // {
    RCURLY,         // }

    // 箭头和特殊运算符
    LEF,            // -> (箭头)
    MEAN,           // => (mean/箭头函数)
    WALRUS,         // := (海象运算符)
    PIPE,           // |> (管道运算符)
    REVPIPE,        // <| (反向管道运算符)
    SAFEPIPE,       // |?> (安全管道运算符)

    // 复合赋值运算符
    ADDEQ,          // +=
    SUBEQ,          // -=
    MULEQ,          // *=
    DIVEQ,          // /=
    IDIVEQ,         // //=
    MODEQ,          // %=
    BANDEQ,         // &=
    BOREQ,          // |=
    BXOREQ,         // ~= (位异或赋值，上下文区分)
    SHREQ,          // >>=
    SHLEQ,          // <<=
    CONCATEQ,       // ..=
    PLUSPLUS,       // ++ (自增)

    // 可选链和空值合并
    OPTCHAIN,       // ?. (可选链)
    NULLCOAL,       // ?? (空值合并)
    QUESTION,       // ? (问号)

    // 注释
    SHORT_COMMENT,
    BLOCK_COMMENT,
    DOC_COMMENT,

    // 区域标记
    REGION,
    ENDREGION,

    // Shebang
    SHEBANG,

    // 标签
    LABEL,

    // 保留 (兼容旧代码)
    DEFER,          // defer (延迟执行)

    // 异步关键字
    ASYNC,          // async (异步函数声明)
    AWAIT           // await (等待异步结果)
}
