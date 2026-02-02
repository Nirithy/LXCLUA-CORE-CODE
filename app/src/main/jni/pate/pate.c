/**
 * @file pate.c
 * @brief Lua C模块 - 封装luaD_protectedparser内部解析器接口
 */

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"
#include "ldo.h"
#include "lzio.h"
#include "lfunc.h"
#include "lstate.h"
#include "lgc.h"
#include "ltable.h"

/**
 * @brief 获取全局表宏(从lapi.c复制)
 */
#define getGtable(L)  \
    (&hvalue(&G(L)->l_registry)->array[LUA_RIDX_GLOBALS - 1])

/**
 * @brief 为闭包设置全局环境(_ENV)
 * @param L Lua状态机
 * 
 * 解析后的闭包第一个upvalue是_ENV，需要设置为全局表
 */
static void set_env(lua_State *L) {
    LClosure *f = clLvalue(s2v(L->top.p - 1));
    if (f->nupvalues >= 1) {
        const TValue *gt = getGtable(L);
        setobj(L, f->upvals[0]->v.p, gt);
        luaC_barrier(L, f->upvals[0], gt);
    }
}

/**
 * @brief 字符串读取器的用户数据结构
 */
typedef struct StringReaderData {
    const char *str;    /**< 字符串指针 */
    size_t size;        /**< 剩余大小 */
} StringReaderData;

/**
 * @brief 字符串读取器回调函数
 * @param L Lua状态机
 * @param ud 用户数据(StringReaderData*)
 * @param size [out]读取的字节数
 * @return 读取的数据指针，NULL表示结束
 */
static const char *string_reader(lua_State *L, void *ud, size_t *size) {
    StringReaderData *data = (StringReaderData *)ud;
    (void)L;
    if (data->size == 0) {
        *size = 0;
        return NULL;
    }
    *size = data->size;
    data->size = 0;
    return data->str;
}

/**
 * @brief 解析Lua代码字符串
 * @param L Lua状态机
 * @return 成功返回1(闭包在栈顶)，失败返回2(nil和错误信息)
 * 
 * Lua调用: pate.parse(code [, name [, mode]])
 *   - code: 要解析的Lua代码字符串
 *   - name: 代码块名称，默认"=pate"
 *   - mode: 加载模式 "t"(文本)/"b"(二进制)/"bt"(两者)，默认"bt"
 */
static int pate_parse(lua_State *L) {
    size_t len;
    const char *code = luaL_checklstring(L, 1, &len);
    const char *name = luaL_optstring(L, 2, "=pate");
    const char *mode = luaL_optstring(L, 3, "bt");
    
    StringReaderData data;
    data.str = code;
    data.size = len;
    
    ZIO z;
    luaZ_init(L, &z, string_reader, &data);
    
    int status = luaD_protectedparser(L, &z, name, mode);
    
    if (status != LUA_OK) {
        lua_pushnil(L);
        lua_insert(L, -2);
        return 2;
    }
    set_env(L);
    return 1;
}

/**
 * @brief 解析并执行Lua代码
 * @param L Lua状态机
 * @return 执行结果数量或错误信息
 * 
 * Lua调用: pate.dostring(code [, name [, mode]])
 */
static int pate_dostring(lua_State *L) {
    int base = lua_gettop(L);
    size_t len;
    const char *code = luaL_checklstring(L, 1, &len);
    const char *name = luaL_optstring(L, 2, "=pate");
    const char *mode = luaL_optstring(L, 3, "bt");
    
    StringReaderData data;
    data.str = code;
    data.size = len;
    
    ZIO z;
    luaZ_init(L, &z, string_reader, &data);
    
    int status = luaD_protectedparser(L, &z, name, mode);
    
    if (status != LUA_OK) {
        lua_pushnil(L);
        lua_insert(L, -2);
        return 2;
    }
    set_env(L);
    
    status = lua_pcall(L, 0, LUA_MULTRET, 0);
    if (status != LUA_OK) {
        lua_pushnil(L);
        lua_insert(L, -2);
        return 2;
    }
    
    return lua_gettop(L) - base;
}

/**
 * @brief 检查代码语法是否正确
 * @param L Lua状态机
 * @return 1个返回值: true或(false, errmsg)
 * 
 * Lua调用: pate.check(code [, name])
 */
static int pate_check(lua_State *L) {
    size_t len;
    const char *code = luaL_checklstring(L, 1, &len);
    const char *name = luaL_optstring(L, 2, "=pate");
    
    StringReaderData data;
    data.str = code;
    data.size = len;
    
    ZIO z;
    luaZ_init(L, &z, string_reader, &data);
    
    int status = luaD_protectedparser(L, &z, name, "t");
    
    if (status != LUA_OK) {
        lua_pushboolean(L, 0);
        lua_insert(L, -2);
        return 2;
    }
    set_env(L);
    lua_pop(L, 1);
    lua_pushboolean(L, 1);
    return 1;
}

/**
 * @brief 模块函数注册表
 */
static const luaL_Reg pate_funcs[] = {
    {"parse", pate_parse},
    {"dostring", pate_dostring},
    {"check", pate_check},
    {NULL, NULL}
};

/**
 * @brief 模块入口函数
 * @param L Lua状态机
 * @return 1(模块表)
 */
LUAMOD_API int luaopen_pate(lua_State *L) {
    luaL_newlib(L, pate_funcs);
    return 1;
}
