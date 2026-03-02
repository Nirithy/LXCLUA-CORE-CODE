/*
 * Copyright (C) 2026-2099 DifierLine.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.luajava;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lua栈操作API类
 * 提供所有Lua C API的Java封装，可用于Lua转Java字节码等场景
 * 
 * @author DifierLine
 */
public class LuaStackAPI {

    private static final String TAG = "LuaStackAPI";

    // ==================== Lua常量定义 ====================

    /** Lua栈最大深度 */
    public static final int LUAI_MAXSTACK = 1000000;

    /** 注册表索引 */
    public static final int LUA_REGISTRYINDEX = -LUAI_MAXSTACK - 1000;

    /** 主线程在注册表中的索引 */
    public static final int LUA_RIDX_MAINTHREAD = 1;

    /** 全局表在注册表中的索引 */
    public static final int LUA_RIDX_GLOBALS = 2;

    /** 注册表最后索引 */
    public static final int LUA_RIDX_LAST = LUA_RIDX_GLOBALS;

    // ==================== Lua类型常量 ====================

    /** 无类型 */
    public static final int LUA_TNONE = -1;

    /** nil类型 */
    public static final int LUA_TNIL = 0;

    /** 布尔类型 */
    public static final int LUA_TBOOLEAN = 1;

    /** 轻量userdata */
    public static final int LUA_TLIGHTUSERDATA = 2;

    /** 数字类型 */
    public static final int LUA_TNUMBER = 3;

    /** 字符串类型 */
    public static final int LUA_TSTRING = 4;

    /** 表类型 */
    public static final int LUA_TTABLE = 5;

    /** 函数类型 */
    public static final int LUA_TFUNCTION = 6;

    /** userdata类型 */
    public static final int LUA_TUSERDATA = 7;

    /** 线程类型 */
    public static final int LUA_TTHREAD = 8;

    /** 整数类型 */
    public static final int LUA_TINTEGER = 9;

    // ==================== Lua调用常量 ====================

    /** 多返回值 */
    public static final int LUA_MULTRET = -1;

    /** 协程挂起 */
    public static final int LUA_YIELD = 1;

    /** 运行时错误 */
    public static final int LUA_ERRRUN = 2;

    /** 语法错误 */
    public static final int LUA_ERRSYNTAX = 3;

    /** 内存分配错误 */
    public static final int LUA_ERRMEM = 4;

    /** GC元方法错误 */
    public static final int LUA_ERRGCMM = 5;

    /** 错误处理函数错误 */
    public static final int LUA_ERRERR = 6;

    // ==================== Lua比较操作常量 ====================

    /** 相等比较 */
    public static final int LUA_OPEQ = 0;

    /** 小于比较 */
    public static final int LUA_OPLT = 1;

    /** 小于等于比较 */
    public static final int LUA_OPLE = 2;

    // ==================== GC操作常量 ====================

    /** 停止GC */
    public static final int LUA_GCSTOP = 0;

    /** 重启GC */
    public static final int LUA_GCRESTART = 1;

    /** 执行一次完整GC */
    public static final int LUA_GCCOLLECT = 2;

    /** 获取GC内存使用量 */
    public static final int LUA_GCCOUNT = 3;

    /** 获取GC内存使用量小数部分 */
    public static final int LUA_GCCOUNTB = 4;

    /** GC步进 */
    public static final int LUA_GCSTEP = 5;

    /** 设置GC暂停参数 */
    public static final int LUA_GCSETPAUSE = 6;

    /** 设置GC步进倍率 */
    public static final int LUA_GCSETSTEPMUL = 7;

    // ==================== 实例字段 ====================

    /** 底层LuaState指针 */
    private long luaStatePtr;

    /** 关联的LuaState对象 */
    private LuaState luaState;

    /** Java对象存储映射 */
    private final HashMap<Integer, Object> javaObjectMap = new HashMap<>();

    /** 待GC的Java对象列表 */
    private final ArrayList<Integer> javaObjectGcList = new ArrayList<>();

    /** 对象索引计数器 */
    private int objectIndex = 0;

    // ==================== 构造函数 ====================

    /**
     * 从现有LuaState创建API实例
     * @param luaState LuaState对象
     */
    public LuaStackAPI(LuaState luaState) {
        this.luaState = luaState;
        this.luaStatePtr = luaState.getPointer();
    }

    /**
     * 创建新的Lua状态机
     * @return LuaStackAPI实例
     */
    public static LuaStackAPI newState() {
        LuaState L = LuaStateFactory.newLuaState();
        return new LuaStackAPI(L);
    }

    // ==================== 状态管理 ====================

    /**
     * 获取底层LuaState指针
     * @return 指针值
     */
    public long getPointer() {
        return luaStatePtr;
    }

    /**
     * 获取关联的LuaState对象
     * @return LuaState对象
     */
    public LuaState getLuaState() {
        return luaState;
    }

    /**
     * 检查状态是否已关闭
     * @return 是否已关闭
     */
    public boolean isClosed() {
        return luaStatePtr == 0;
    }

    /**
     * 关闭Lua状态机
     */
    public void close() {
        if (luaState != null) {
            luaState.close();
            luaStatePtr = 0;
        }
        javaObjectMap.clear();
        javaObjectGcList.clear();
    }

    // ==================== 栈操作 ====================

    /**
     * 获取栈顶索引
     * @return 栈顶索引
     */
    public int getTop() {
        checkState();
        return luaState.getTop();
    }

    /**
     * 设置栈顶
     * @param idx 目标索引
     */
    public void setTop(int idx) {
        checkState();
        luaState.setTop(idx);
    }

    /**
     * 将指定位置的值复制到栈顶
     * @param idx 源索引
     */
    public void pushValue(int idx) {
        checkState();
        luaState.pushValue(idx);
    }

    /**
     * 旋转栈元素
     * @param idx 起始索引
     * @param n 旋转数量
     */
    public void rotate(int idx, int n) {
        checkState();
        luaState.rotate(idx, n);
    }

    /**
     * 复制栈元素
     * @param fromIdx 源索引
     * @param toIdx 目标索引
     */
    public void copy(int fromIdx, int toIdx) {
        checkState();
        luaState.copy(fromIdx, toIdx);
    }

    /**
     * 移除指定位置的元素
     * @param idx 索引
     */
    public void remove(int idx) {
        checkState();
        luaState.remove(idx);
    }

    /**
     * 将栈顶元素插入到指定位置
     * @param idx 目标索引
     */
    public void insert(int idx) {
        checkState();
        luaState.insert(idx);
    }

    /**
     * 替换指定位置的元素
     * @param idx 目标索引
     */
    public void replace(int idx) {
        checkState();
        luaState.replace(idx);
    }

    /**
     * 检查栈空间
     * @param sz 需要的空间大小
     * @return 是否成功
     */
    public int checkStack(int sz) {
        checkState();
        return luaState.checkStack(sz);
    }

    /**
     * 弹出栈顶n个元素
     * @param n 弹出数量
     */
    public void pop(int n) {
        checkState();
        luaState.pop(n);
    }

    // ==================== 类型检查 ====================

    /**
     * 检查是否为数字
     * @param idx 索引
     * @return 是否为数字
     */
    public boolean isNumber(int idx) {
        checkState();
        return luaState.isNumber(idx);
    }

    /**
     * 检查是否为整数
     * @param idx 索引
     * @return 是否为整数
     */
    public boolean isInteger(int idx) {
        checkState();
        return luaState.isInteger(idx);
    }

    /**
     * 检查是否为字符串
     * @param idx 索引
     * @return 是否为字符串
     */
    public boolean isString(int idx) {
        checkState();
        return luaState.isString(idx);
    }

    /**
     * 检查是否为函数
     * @param idx 索引
     * @return 是否为函数
     */
    public boolean isFunction(int idx) {
        checkState();
        return luaState.isFunction(idx);
    }

    /**
     * 检查是否为C函数
     * @param idx 索引
     * @return 是否为C函数
     */
    public boolean isCFunction(int idx) {
        checkState();
        return luaState.isCFunction(idx);
    }

    /**
     * 检查是否为userdata
     * @param idx 索引
     * @return 是否为userdata
     */
    public boolean isUserdata(int idx) {
        checkState();
        return luaState.isUserdata(idx);
    }

    /**
     * 检查是否为表
     * @param idx 索引
     * @return 是否为表
     */
    public boolean isTable(int idx) {
        checkState();
        return luaState.isTable(idx);
    }

    /**
     * 检查是否为布尔值
     * @param idx 索引
     * @return 是否为布尔值
     */
    public boolean isBoolean(int idx) {
        checkState();
        return luaState.isBoolean(idx);
    }

    /**
     * 检查是否为nil
     * @param idx 索引
     * @return 是否为nil
     */
    public boolean isNil(int idx) {
        checkState();
        return luaState.isNil(idx);
    }

    /**
     * 检查是否为线程
     * @param idx 索引
     * @return 是否为线程
     */
    public boolean isThread(int idx) {
        checkState();
        return luaState.isThread(idx);
    }

    /**
     * 检查是否为无类型
     * @param idx 索引
     * @return 是否为无类型
     */
    public boolean isNone(int idx) {
        checkState();
        return luaState.isNone(idx);
    }

    /**
     * 检查是否为无类型或nil
     * @param idx 索引
     * @return 是否为无类型或nil
     */
    public boolean isNoneOrNil(int idx) {
        checkState();
        return luaState.isNoneOrNil(idx);
    }

    /**
     * 检查是否为Java对象
     * @param idx 索引
     * @return 是否为Java对象
     */
    public boolean isObject(int idx) {
        checkState();
        return luaState.isObject(idx);
    }

    /**
     * 检查是否为Java函数
     * @param idx 索引
     * @return 是否为Java函数
     */
    public boolean isJavaFunction(int idx) {
        checkState();
        return luaState.isJavaFunction(idx);
    }

    /**
     * 获取元素类型
     * @param idx 索引
     * @return 类型常量
     */
    public int type(int idx) {
        checkState();
        return luaState.type(idx);
    }

    /**
     * 获取类型名称
     * @param tp 类型常量
     * @return 类型名称
     */
    public String typeName(int tp) {
        checkState();
        return luaState.typeName(tp);
    }

    // ==================== 值获取 ====================

    /**
     * 转换为数字
     * @param idx 索引
     * @return 数字值
     */
    public double toNumber(int idx) {
        checkState();
        return luaState.toNumber(idx);
    }

    /**
     * 转换为整数
     * @param idx 索引
     * @return 整数值
     */
    public long toInteger(int idx) {
        checkState();
        return luaState.toInteger(idx);
    }

    /**
     * 转换为布尔值
     * @param idx 索引
     * @return 布尔值
     */
    public boolean toBoolean(int idx) {
        checkState();
        return luaState.toBoolean(idx);
    }

    /**
     * 转换为字符串
     * @param idx 索引
     * @return 字符串值
     */
    public String toString(int idx) {
        checkState();
        return luaState.toString(idx);
    }

    /**
     * 转换为字节数组
     * @param idx 索引
     * @return 字节数组
     */
    public byte[] toBuffer(int idx) {
        checkState();
        return luaState.toBuffer(idx);
    }

    /**
     * 转换为线程
     * @param idx 索引
     * @return LuaState线程
     */
    public LuaState toThread(int idx) {
        checkState();
        return luaState.toThread(idx);
    }

    /**
     * 转换为Java对象
     * @param idx 索引
     * @return Java对象
     */
    public Object toJavaObject(int idx) throws LuaException {
        checkState();
        return luaState.toJavaObject(idx);
    }

    /**
     * 从userdata获取Java对象
     * @param idx 索引
     * @return Java对象
     */
    public Object getObjectFromUserdata(int idx) throws LuaException {
        checkState();
        return luaState.getObjectFromUserdata(idx);
    }

    // ==================== 长度获取 ====================

    /**
     * 获取字符串长度
     * @param idx 索引
     * @return 长度
     */
    public int strLen(int idx) {
        checkState();
        return luaState.strLen(idx);
    }

    /**
     * 获取对象长度(#运算符)
     * @param idx 索引
     * @return 长度
     */
    public int objLen(int idx) {
        checkState();
        return luaState.objLen(idx);
    }

    /**
     * 获取原始长度
     * @param idx 索引
     * @return 长度
     */
    public int rawLen(int idx) {
        checkState();
        return luaState.rawLen(idx);
    }

    // ==================== 比较操作 ====================

    /**
     * 相等比较
     * @param idx1 索引1
     * @param idx2 索引2
     * @return 比较结果
     */
    public int equal(int idx1, int idx2) {
        checkState();
        return luaState.equal(idx1, idx2);
    }

    /**
     * 原始相等比较
     * @param idx1 索引1
     * @param idx2 索引2
     * @return 比较结果
     */
    public int rawequal(int idx1, int idx2) {
        checkState();
        return luaState.rawequal(idx1, idx2);
    }

    /**
     * 小于比较
     * @param idx1 索引1
     * @param idx2 索引2
     * @return 比较结果
     */
    public int lessThan(int idx1, int idx2) {
        checkState();
        return luaState.lessThan(idx1, idx2);
    }

    /**
     * 通用比较
     * @param idx1 索引1
     * @param idx2 索引2
     * @param op 操作类型
     * @return 比较结果
     */
    public int compare(int idx1, int idx2, int op) {
        checkState();
        return luaState.compare(idx1, idx2, op);
    }

    // ==================== 压栈操作 ====================

    /**
     * 压入nil
     */
    public void pushNil() {
        checkState();
        luaState.pushNil();
    }

    /**
     * 压入数字
     * @param number 数字值
     */
    public void pushNumber(double number) {
        checkState();
        luaState.pushNumber(number);
    }

    /**
     * 压入整数
     * @param integer 整数值
     */
    public void pushInteger(long integer) {
        checkState();
        luaState.pushInteger(integer);
    }

    /**
     * 压入字符串
     * @param str 字符串值
     */
    public void pushString(String str) {
        checkState();
        luaState.pushString(str);
    }

    /**
     * 压入字节数组
     * @param bytes 字节数组
     */
    public void pushString(byte[] bytes) {
        checkState();
        luaState.pushString(bytes);
    }

    /**
     * 压入布尔值
     * @param bool 布尔值
     */
    public void pushBoolean(boolean bool) {
        checkState();
        luaState.pushBoolean(bool);
    }

    /**
     * 压入Java对象
     * @param obj Java对象
     */
    public void pushJavaObject(Object obj) {
        checkState();
        luaState.pushJavaObject(obj);
    }

    /**
     * 压入Java函数
     * @param func Java函数
     */
    public void pushJavaFunction(JavaFunction func) throws LuaException {
        checkState();
        luaState.pushJavaFunction(func);
    }

    /**
     * 压入任意对象值
     * @param obj 对象
     */
    public void pushObjectValue(Object obj) throws LuaException {
        checkState();
        luaState.pushObjectValue(obj);
    }

    /**
     * 压入全局表
     */
    public void pushGlobalTable() {
        checkState();
        luaState.pushGlobalTable();
    }

    // ==================== 表操作 ====================

    /**
     * 创建新表
     * @param narr 数组部分预分配大小
     * @param nrec 哈希部分预分配大小
     */
    public void createTable(int narr, int nrec) {
        checkState();
        luaState.createTable(narr, nrec);
    }

    /**
     * 创建新表(默认大小)
     */
    public void newTable() {
        checkState();
        luaState.newTable();
    }

    /**
     * 获取表字段(table[idx])
     * @param idx 表索引
     * @return 结果类型
     */
    public int getTable(int idx) {
        checkState();
        return luaState.getTable(idx);
    }

    /**
     * 获取表字段(table.k)
     * @param idx 表索引
     * @param k 字段名
     * @return 结果类型
     */
    public int getField(int idx, String k) {
        checkState();
        return luaState.getField(idx, k);
    }

    /**
     * 获取表字段(table[n])
     * @param idx 表索引
     * @param n 数字键
     * @return 结果类型
     */
    public int getI(int idx, long n) {
        checkState();
        return luaState.getI(idx, n);
    }

    /**
     * 原始获取表字段
     * @param idx 表索引
     * @return 结果类型
     */
    public int rawGet(int idx) {
        checkState();
        return luaState.rawGet(idx);
    }

    /**
     * 原始获取表字段(table[n])
     * @param idx 表索引
     * @param n 数字键
     * @return 结果类型
     */
    public int rawGetI(int idx, long n) {
        checkState();
        return luaState.rawGetI(idx, n);
    }

    /**
     * 设置表字段(table[idx] = value)
     * @param idx 表索引
     */
    public void setTable(int idx) {
        checkState();
        luaState.setTable(idx);
    }

    /**
     * 设置表字段(table.k = value)
     * @param idx 表索引
     * @param k 字段名
     */
    public void setField(int idx, String k) {
        checkState();
        luaState.setField(idx, k);
    }

    /**
     * 设置表字段(table[n] = value)
     * @param idx 表索引
     * @param n 数字键
     */
    public void setI(int idx, long n) {
        checkState();
        luaState.setI(idx, n);
    }

    /**
     * 原始设置表字段
     * @param idx 表索引
     */
    public void rawSet(int idx) {
        checkState();
        luaState.rawSet(idx);
    }

    /**
     * 原始设置表字段(table[n] = value)
     * @param idx 表索引
     * @param n 数字键
     */
    public void rawSetI(int idx, long n) {
        checkState();
        luaState.rawSetI(idx, n);
    }

    /**
     * 获取全局变量
     * @param name 变量名
     * @return 结果类型
     */
    public int getGlobal(String name) {
        checkState();
        return luaState.getGlobal(name);
    }

    /**
     * 设置全局变量
     * @param name 变量名
     */
    public void setGlobal(String name) {
        checkState();
        luaState.setGlobal(name);
    }

    /**
     * 获取下一个键值对
     * @param idx 表索引
     * @return 是否还有更多元素
     */
    public int next(int idx) {
        checkState();
        return luaState.next(idx);
    }

    // ==================== 元表操作 ====================

    /**
     * 获取元表
     * @param idx 索引
     * @return 是否有元表
     */
    public int getMetaTable(int idx) {
        checkState();
        return luaState.getMetaTable(idx);
    }

    /**
     * 设置元表
     * @param idx 索引
     * @return 是否成功
     */
    public int setMetaTable(int idx) {
        checkState();
        return luaState.setMetaTable(idx);
    }

    /**
     * 获取用户值
     * @param idx 索引
     * @return 结果类型
     */
    public int getUserValue(int idx) {
        checkState();
        return luaState.getUserValue(idx);
    }

    /**
     * 设置用户值
     * @param idx 索引
     */
    public void setUserValue(int idx) {
        checkState();
        luaState.setUserValue(idx);
    }

    // ==================== 函数调用 ====================

    /**
     * 调用函数
     * @param nArgs 参数数量
     * @param nResults 返回值数量
     */
    public void call(int nArgs, int nResults) {
        checkState();
        luaState.call(nArgs, nResults);
    }

    /**
     * 保护调用函数
     * @param nArgs 参数数量
     * @param nResults 返回值数量
     * @param errFunc 错误处理函数索引
     * @return 错误码(0表示成功)
     */
    public int pcall(int nArgs, int nResults, int errFunc) {
        checkState();
        return luaState.pcall(nArgs, nResults, errFunc);
    }

    // ==================== 协程操作 ====================

    /**
     * 创建新线程(协程)
     * @return 新的LuaState
     */
    public LuaState newThread() {
        checkState();
        return luaState.newThread();
    }

    /**
     * 挂起协程
     * @param nResults 返回值数量
     * @return 状态码
     */
    public int yield(int nResults) {
        checkState();
        return luaState.yield(nResults);
    }

    /**
     * 恢复协程
     * @param from 来源协程
     * @param nArgs 参数数量
     * @return 状态码
     */
    public int resume(LuaState from, int nArgs) {
        checkState();
        return luaState.resume(from, nArgs);
    }

    /**
     * 获取协程状态
     * @return 状态码
     */
    public int status() {
        checkState();
        return luaState.status();
    }

    /**
     * 检查是否可挂起
     * @return 是否可挂起
     */
    public int isYieldable() {
        checkState();
        return luaState.isYieldable();
    }

    // ==================== GC操作 ====================

    /**
     * 执行GC操作
     * @param what 操作类型
     * @param data 操作数据
     * @return 操作结果
     */
    public int gc(int what, int data) {
        checkState();
        return luaState.gc(what, data);
    }

    /**
     * 执行完整GC
     */
    public void gcCollect() {
        gc(LUA_GCCOLLECT, 0);
    }

    /**
     * 停止GC
     */
    public void gcStop() {
        gc(LUA_GCSTOP, 0);
    }

    /**
     * 重启GC
     */
    public void gcRestart() {
        gc(LUA_GCRESTART, 0);
    }

    /**
     * 获取GC内存使用量(KB)
     * @return 内存使用量
     */
    public int gcCount() {
        return gc(LUA_GCCOUNT, 0);
    }

    // ==================== 加载与执行 ====================

    /**
     * 加载文件
     * @param fileName 文件名
     * @return 错误码(0表示成功)
     */
    public int LloadFile(String fileName) {
        checkState();
        return luaState.LloadFile(fileName);
    }

    /**
     * 加载字符串
     * @param s Lua代码字符串
     * @return 错误码(0表示成功)
     */
    public int LloadString(String s) {
        checkState();
        return luaState.LloadString(s);
    }

    /**
     * 加载字节码
     * @param buff 字节码
     * @param name 名称
     * @return 错误码(0表示成功)
     */
    public int LloadBuffer(byte[] buff, String name) {
        checkState();
        return luaState.LloadBuffer(buff, name);
    }

    /**
     * 执行文件
     * @param fileName 文件名
     * @return 错误码(0表示成功)
     */
    public int LdoFile(String fileName) {
        checkState();
        return luaState.LdoFile(fileName);
    }

    /**
     * 执行字符串
     * @param str Lua代码字符串
     * @return 错误码(0表示成功)
     */
    public int LdoString(String str) {
        checkState();
        return luaState.LdoString(str);
    }

    // ==================== 库加载 ====================

    /**
     * 打开基础库
     */
    public void openBase() {
        checkState();
        luaState.openBase();
    }

    /**
     * 打开表库
     */
    public void openTable() {
        checkState();
        luaState.openTable();
    }

    /**
     * 打开IO库
     */
    public void openIo() {
        checkState();
        luaState.openIo();
    }

    /**
     * 打开OS库
     */
    public void openOs() {
        checkState();
        luaState.openOs();
    }

    /**
     * 打开字符串库
     */
    public void openString() {
        checkState();
        luaState.openString();
    }

    /**
     * 打开数学库
     */
    public void openMath() {
        checkState();
        luaState.openMath();
    }

    /**
     * 打开调试库
     */
    public void openDebug() {
        checkState();
        luaState.openDebug();
    }

    /**
     * 打开包库
     */
    public void openPackage() {
        checkState();
        luaState.openPackage();
    }

    /**
     * 打开所有标准库
     */
    public void openLibs() {
        checkState();
        luaState.openLibs();
    }

    // ==================== 辅助库函数 ====================

    /**
     * 获取元字段
     * @param obj 对象索引
     * @param e 字段名
     * @return 结果
     */
    public int LgetMetaField(int obj, String e) {
        checkState();
        return luaState.LgetMetaField(obj, e);
    }

    /**
     * 调用元方法
     * @param obj 对象索引
     * @param e 元方法名
     * @return 结果
     */
    public int LcallMeta(int obj, String e) {
        checkState();
        return luaState.LcallMeta(obj, e);
    }

    /**
     * 参数错误
     * @param numArg 参数编号
     * @param extraMsg 额外信息
     * @return 错误码
     */
    public int LargError(int numArg, String extraMsg) {
        checkState();
        return luaState.LargError(numArg, extraMsg);
    }

    /**
     * 检查字符串参数
     * @param numArg 参数编号
     * @return 字符串值
     */
    public String LcheckString(int numArg) {
        checkState();
        return luaState.LcheckString(numArg);
    }

    /**
     * 可选字符串参数
     * @param numArg 参数编号
     * @param def 默认值
     * @return 字符串值
     */
    public String LoptString(int numArg, String def) {
        checkState();
        return luaState.LoptString(numArg, def);
    }

    /**
     * 检查数字参数
     * @param numArg 参数编号
     * @return 数字值
     */
    public double LcheckNumber(int numArg) {
        checkState();
        return luaState.LcheckNumber(numArg);
    }

    /**
     * 可选数字参数
     * @param numArg 参数编号
     * @param def 默认值
     * @return 数字值
     */
    public double LoptNumber(int numArg, double def) {
        checkState();
        return luaState.LoptNumber(numArg, def);
    }

    /**
     * 检查整数参数
     * @param numArg 参数编号
     * @return 整数值
     */
    public int LcheckInteger(int numArg) {
        checkState();
        return luaState.LcheckInteger(numArg);
    }

    /**
     * 可选整数参数
     * @param numArg 参数编号
     * @param def 默认值
     * @return 整数值
     */
    public int LoptInteger(int numArg, int def) {
        checkState();
        return luaState.LoptInteger(numArg, def);
    }

    /**
     * 检查栈空间
     * @param sz 空间大小
     * @param msg 错误消息
     */
    public void LcheckStack(int sz, String msg) {
        checkState();
        luaState.LcheckStack(sz, msg);
    }

    /**
     * 检查类型
     * @param nArg 参数编号
     * @param t 期望类型
     */
    public void LcheckType(int nArg, int t) {
        checkState();
        luaState.LcheckType(nArg, t);
    }

    /**
     * 检查任意类型
     * @param nArg 参数编号
     */
    public void LcheckAny(int nArg) {
        checkState();
        luaState.LcheckAny(nArg);
    }

    /**
     * 创建新元表
     * @param tName 类型名
     * @return 是否已存在
     */
    public int LnewMetatable(String tName) {
        checkState();
        return luaState.LnewMetatable(tName);
    }

    /**
     * 获取元表
     * @param tName 类型名
     */
    public void LgetMetatable(String tName) {
        checkState();
        luaState.LgetMetatable(tName);
    }

    /**
     * 压入调用位置信息
     * @param lvl 层级
     */
    public void Lwhere(int lvl) {
        checkState();
        luaState.Lwhere(lvl);
    }

    /**
     * 创建引用
     * @param t 表索引
     * @return 引用ID
     */
    public int Lref(int t) {
        checkState();
        return luaState.Lref(t);
    }

    /**
     * 释放引用
     * @param t 表索引
     * @param ref 引用ID
     */
    public void LunRef(int t, int ref) {
        checkState();
        luaState.LunRef(t, ref);
    }

    /**
     * 字符串替换
     * @param s 源字符串
     * @param p 模式
     * @param r 替换字符串
     * @return 结果字符串
     */
    public String Lgsub(String s, String p, String r) {
        checkState();
        return luaState.Lgsub(s, p, r);
    }

    /**
     * 转换为字符串表示
     * @param idx 索引
     * @return 字符串表示
     */
    public String LtoString(int idx) {
        checkState();
        return luaState.LtoString(idx);
    }

    // ==================== Upvalue操作 ====================

    /**
     * 获取Upvalue名
     * @param funcIndex 函数索引
     * @param n Upvalue编号
     * @return Upvalue名
     */
    public String getUpValue(int funcIndex, int n) {
        checkState();
        return luaState.getUpValue(funcIndex, n);
    }

    /**
     * 设置Upvalue
     * @param funcIndex 函数索引
     * @param n Upvalue编号
     * @return Upvalue名
     */
    public String setUpValue(int funcIndex, int n) {
        checkState();
        return luaState.setUpValue(funcIndex, n);
    }

    /**
     * 导出函数字节码
     * @param funcIndex 函数索引
     * @return 字节码
     */
    public byte[] dump(int funcIndex) {
        checkState();
        return luaState.dump(funcIndex);
    }

    // ==================== 其他操作 ====================

    /**
     * 触发错误
     * @return 错误码
     */
    public int error() {
        checkState();
        return luaState.error();
    }

    /**
     * 连接字符串
     * @param n 元素数量
     */
    public void concat(int n) {
        checkState();
        luaState.concat(n);
    }

    /**
     * 分析Lua代码
     * @param code Lua代码
     * @return 错误信息(无错误返回null)
     */
    public String analyzeCode(String code) {
        checkState();
        return luaState.analyzeCode(code);
    }

    // ==================== LuaObject获取 ====================

    /**
     * 获取全局Lua对象
     * @param name 变量名
     * @return LuaObject
     */
    public LuaObject getLuaObject(String name) {
        checkState();
        return luaState.getLuaObject(name);
    }

    /**
     * 获取栈上Lua对象
     * @param idx 索引
     * @return LuaObject
     */
    public LuaObject getLuaObject(int idx) {
        checkState();
        return luaState.getLuaObject(idx);
    }

    /**
     * 获取表字段的Lua对象
     * @param parent 父对象
     * @param name 字段名
     * @return LuaObject
     */
    public LuaObject getLuaObject(LuaObject parent, String name) throws LuaException {
        checkState();
        return luaState.getLuaObject(parent, name);
    }

    // ==================== 工具方法 ====================

    /**
     * 检查状态是否有效
     */
    private void checkState() {
        if (luaStatePtr == 0 || luaState == null) {
            throw new IllegalStateException("LuaState已关闭或未初始化");
        }
    }

    /**
     * 获取类型名称字符串
     * @param type 类型常量
     * @return 类型名称
     */
    public static String getTypeName(int type) {
        switch (type) {
            case LUA_TNONE: return "none";
            case LUA_TNIL: return "nil";
            case LUA_TBOOLEAN: return "boolean";
            case LUA_TLIGHTUSERDATA: return "lightuserdata";
            case LUA_TNUMBER: return "number";
            case LUA_TSTRING: return "string";
            case LUA_TTABLE: return "table";
            case LUA_TFUNCTION: return "function";
            case LUA_TUSERDATA: return "userdata";
            case LUA_TTHREAD: return "thread";
            case LUA_TINTEGER: return "integer";
            default: return "unknown";
        }
    }

    /**
     * 打印栈内容(调试用)
     * @param tag 日志标签
     */
    public void printStack(String tag) {
        int top = getTop();
        Log.d(tag, "===== Lua Stack (top=" + top + ") =====");
        for (int i = 1; i <= top; i++) {
            int type = type(i);
            String typeName = typeName(type);
            String value;
            try {
                if (type == LUA_TSTRING) {
                    value = "\"" + toString(i) + "\"";
                } else if (type == LUA_TNUMBER) {
                    if (isInteger(i)) {
                        value = String.valueOf(toInteger(i));
                    } else {
                        value = String.valueOf(toNumber(i));
                    }
                } else if (type == LUA_TBOOLEAN) {
                    value = toBoolean(i) ? "true" : "false";
                } else if (type == LUA_TTABLE) {
                    value = "table#" + objLen(i);
                } else if (type == LUA_TFUNCTION) {
                    value = "function";
                } else if (type == LUA_TNIL) {
                    value = "nil";
                } else if (type == LUA_TUSERDATA) {
                    if (isObject(i)) {
                        value = "userdata: " + getObjectFromUserdata(i);
                    } else {
                        value = "userdata";
                    }
                } else {
                    value = typeName;
                }
            } catch (Exception e) {
                value = "error: " + e.getMessage();
            }
            Log.d(tag, "[" + i + "] " + typeName + ": " + value);
        }
        Log.d(tag, "===== End Stack =====");
    }

    /**
     * 打印栈内容(默认标签)
     */
    public void printStack() {
        printStack(TAG);
    }
}
