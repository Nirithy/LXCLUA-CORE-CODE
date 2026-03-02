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

/**
 * LuaJava运行时API类
 * 封装ltcc.c中使用的所有Lua栈操作，用于Lua转Java字节码
 * 
 * 此类提供了与Lua C API对应的Java方法，使得可以将Lua代码编译为Java字节码执行
 * 
 * @author DifierLine
 */
public class LuaJava {

    private static final String TAG = "LuaJava";

    // ==================== Lua常量定义 ====================

    /** Lua栈最大深度 */
    public static final int LUAI_MAXSTACK = 1000000;

    /** 注册表索引 */
    public static final int LUA_REGISTRYINDEX = -LUAI_MAXSTACK - 1000;

    /** 多返回值 */
    public static final int LUA_MULTRET = -1;

    // ==================== Lua类型常量 ====================

    public static final int LUA_TNONE = -1;
    public static final int LUA_TNIL = 0;
    public static final int LUA_TBOOLEAN = 1;
    public static final int LUA_TLIGHTUSERDATA = 2;
    public static final int LUA_TNUMBER = 3;
    public static final int LUA_TSTRING = 4;
    public static final int LUA_TTABLE = 5;
    public static final int LUA_TFUNCTION = 6;
    public static final int LUA_TUSERDATA = 7;
    public static final int LUA_TTHREAD = 8;
    public static final int LUA_TINTEGER = 9;

    // ==================== 算术操作常量 ====================

    public static final int LUA_OPADD = 0;
    public static final int LUA_OPSUB = 1;
    public static final int LUA_OPMUL = 2;
    public static final int LUA_OPDIV = 3;
    public static final int LUA_OPIDIV = 4;
    public static final int LUA_OPMOD = 5;
    public static final int LUA_OPPOW = 6;
    public static final int LUA_OPUNM = 7;
    public static final int LUA_OPBNOT = 8;
    public static final int LUA_OPBAND = 9;
    public static final int LUA_OPBOR = 10;
    public static final int LUA_OPBXOR = 11;
    public static final int LUA_OPSHL = 12;
    public static final int LUA_OPSHR = 13;

    // ==================== 比较操作常量 ====================

    public static final int LUA_OPEQ = 0;
    public static final int LUA_OPLT = 1;
    public static final int LUA_OPLE = 2;

    // ==================== 实例字段 ====================

    private final LuaState L;

    // ==================== 构造函数 ====================

    /**
     * 创建LuaJava实例
     * @param luaState Lua状态机
     */
    public LuaJava(LuaState luaState) {
        this.L = luaState;
    }

    /**
     * 创建新的Lua状态机和LuaJava实例
     * @return LuaJava实例
     */
    public static LuaJava newState() {
        return new LuaJava(LuaStateFactory.newLuaState());
    }

    /**
     * 获取底层LuaState
     * @return LuaState对象
     */
    public LuaState getLuaState() {
        return L;
    }

    // ==================== 栈操作 ====================

    /**
     * 获取栈顶索引
     * @return 栈顶索引
     */
    public int getTop() {
        return L.getTop();
    }

    /**
     * 设置栈顶
     * @param idx 目标索引
     */
    public void setTop(int idx) {
        L.setTop(idx);
    }

    /**
     * 弹出栈顶n个元素
     * @param n 弹出数量
     */
    public void pop(int n) {
        L.pop(n);
    }

    /**
     * 将指定位置的值复制到栈顶
     * @param idx 源索引
     */
    public void pushValue(int idx) {
        L.pushValue(idx);
    }

    /**
     * 替换指定位置的元素
     * @param idx 目标索引
     */
    public void replace(int idx) {
        L.replace(idx);
    }

    /**
     * 将栈顶元素插入到指定位置
     * @param idx 目标索引
     */
    public void insert(int idx) {
        L.insert(idx);
    }

    /**
     * 移除指定位置的元素
     * @param idx 索引
     */
    public void remove(int idx) {
        L.remove(idx);
    }

    /**
     * 复制栈元素
     * @param fromIdx 源索引
     * @param toIdx 目标索引
     */
    public void copy(int fromIdx, int toIdx) {
        L.copy(fromIdx, toIdx);
    }

    /**
     * 检查栈空间
     * @param sz 需要的空间大小
     * @return 是否成功
     */
    public int checkStack(int sz) {
        return L.checkStack(sz);
    }

    // ==================== 类型检查 ====================

    /**
     * 获取元素类型
     * @param idx 索引
     * @return 类型常量
     */
    public int type(int idx) {
        return L.type(idx);
    }

    /**
     * 获取类型名称
     * @param tp 类型常量
     * @return 类型名称
     */
    public String typeName(int tp) {
        return L.typeName(tp);
    }

    /**
     * 检查是否为nil
     * @param idx 索引
     * @return 是否为nil
     */
    public boolean isNil(int idx) {
        return L.isNil(idx);
    }

    /**
     * 检查是否为布尔值
     * @param idx 索引
     * @return 是否为布尔值
     */
    public boolean isBoolean(int idx) {
        return L.isBoolean(idx);
    }

    /**
     * 检查是否为整数
     * @param idx 索引
     * @return 是否为整数
     */
    public boolean isInteger(int idx) {
        return L.isInteger(idx);
    }

    /**
     * 检查是否为数字
     * @param idx 索引
     * @return 是否为数字
     */
    public boolean isNumber(int idx) {
        return L.isNumber(idx);
    }

    /**
     * 检查是否为字符串
     * @param idx 索引
     * @return 是否为字符串
     */
    public boolean isString(int idx) {
        return L.isString(idx);
    }

    /**
     * 检查是否为表
     * @param idx 索引
     * @return 是否为表
     */
    public boolean isTable(int idx) {
        return L.isTable(idx);
    }

    /**
     * 检查是否为函数
     * @param idx 索引
     * @return 是否为函数
     */
    public boolean isFunction(int idx) {
        return L.isFunction(idx);
    }

    /**
     * 检查是否为userdata
     * @param idx 索引
     * @return 是否为userdata
     */
    public boolean isUserdata(int idx) {
        return L.isUserdata(idx);
    }

    /**
     * 检查是否为线程
     * @param idx 索引
     * @return 是否为线程
     */
    public boolean isThread(int idx) {
        return L.isThread(idx);
    }

    /**
     * 检查是否为无类型
     * @param idx 索引
     * @return 是否为无类型
     */
    public boolean isNone(int idx) {
        return L.isNone(idx);
    }

    /**
     * 检查是否为无类型或nil
     * @param idx 索引
     * @return 是否为无类型或nil
     */
    public boolean isNoneOrNil(int idx) {
        return L.isNoneOrNil(idx);
    }

    // ==================== 值获取 ====================

    /**
     * 转换为布尔值
     * @param idx 索引
     * @return 布尔值
     */
    public boolean toBoolean(int idx) {
        return L.toBoolean(idx);
    }

    /**
     * 转换为整数
     * @param idx 索引
     * @return 整数值
     */
    public long toInteger(int idx) {
        return L.toInteger(idx);
    }

    /**
     * 转换为数字
     * @param idx 索引
     * @return 数字值
     */
    public double toNumber(int idx) {
        return L.toNumber(idx);
    }

    /**
     * 转换为字符串
     * @param idx 索引
     * @return 字符串值
     */
    public String toString(int idx) {
        return L.toString(idx);
    }

    /**
     * 转换为Java对象
     * @param idx 索引
     * @return Java对象
     */
    public Object toJavaObject(int idx) throws LuaException {
        return L.toJavaObject(idx);
    }

    // ==================== 压栈操作 ====================

    /**
     * 压入nil
     */
    public void pushNil() {
        L.pushNil();
    }

    /**
     * 压入布尔值
     * @param b 布尔值
     */
    public void pushBoolean(boolean b) {
        L.pushBoolean(b);
    }

    /**
     * 压入整数
     * @param n 整数值
     */
    public void pushInteger(long n) {
        L.pushInteger(n);
    }

    /**
     * 压入数字
     * @param n 数字值
     */
    public void pushNumber(double n) {
        L.pushNumber(n);
    }

    /**
     * 压入字符串
     * @param s 字符串值
     */
    public void pushString(String s) {
        L.pushString(s);
    }

    /**
     * 压入Java对象
     * @param obj Java对象
     */
    public void pushJavaObject(Object obj) {
        L.pushJavaObject(obj);
    }

    /**
     * 压入全局表
     */
    public void pushGlobalTable() {
        L.pushGlobalTable();
    }

    /**
     * 压入Java函数
     * @param func Java函数
     */
    public void pushJavaFunction(JavaFunction func) throws LuaException {
        L.pushJavaFunction(func);
    }

    /**
     * 压入任意对象值
     * @param obj 对象
     */
    public void pushObjectValue(Object obj) throws LuaException {
        L.pushObjectValue(obj);
    }

    // ==================== 表操作 ====================

    /**
     * 创建新表
     * @param narr 数组部分预分配大小
     * @param nrec 哈希部分预分配大小
     */
    public void createTable(int narr, int nrec) {
        L.createTable(narr, nrec);
    }

    /**
     * 创建新表(默认大小)
     */
    public void newTable() {
        L.newTable();
    }

    /**
     * 获取表字段(table[idx])
     * @param idx 表索引
     * @return 结果类型
     */
    public int getTable(int idx) {
        return L.getTable(idx);
    }

    /**
     * 设置表字段(table[idx] = value)
     * @param idx 表索引
     */
    public void setTable(int idx) {
        L.setTable(idx);
    }

    /**
     * 获取表字段(table.k)
     * @param idx 表索引
     * @param k 字段名
     * @return 结果类型
     */
    public int getField(int idx, String k) {
        return L.getField(idx, k);
    }

    /**
     * 设置表字段(table.k = value)
     * @param idx 表索引
     * @param k 字段名
     */
    public void setField(int idx, String k) {
        L.setField(idx, k);
    }

    /**
     * 获取表字段(table[n])
     * @param idx 表索引
     * @param n 数字键
     * @return 结果类型
     */
    public int getI(int idx, long n) {
        return L.getI(idx, n);
    }

    /**
     * 设置表字段(table[n] = value)
     * @param idx 表索引
     * @param n 数字键
     */
    public void setI(int idx, long n) {
        L.setI(idx, n);
    }

    /**
     * 原始获取表字段
     * @param idx 表索引
     * @return 结果类型
     */
    public int rawGet(int idx) {
        return L.rawGet(idx);
    }

    /**
     * 原始设置表字段
     * @param idx 表索引
     */
    public void rawSet(int idx) {
        L.rawSet(idx);
    }

    /**
     * 原始获取表字段(table[n])
     * @param idx 表索引
     * @param n 数字键
     * @return 结果类型
     */
    public int rawGetI(int idx, long n) {
        return L.rawGetI(idx, n);
    }

    /**
     * 原始设置表字段(table[n] = value)
     * @param idx 表索引
     * @param n 数字键
     */
    public void rawSetI(int idx, long n) {
        L.rawSetI(idx, n);
    }

    /**
     * 获取对象长度(#运算符)
     * @param idx 索引
     * @return 长度
     */
    public int objLen(int idx) {
        return L.objLen(idx);
    }

    /**
     * 获取原始长度
     * @param idx 索引
     * @return 长度
     */
    public int rawLen(int idx) {
        return L.rawLen(idx);
    }

    /**
     * 获取下一个键值对
     * @param idx 表索引
     * @return 是否还有更多元素
     */
    public int next(int idx) {
        return L.next(idx);
    }

    // ==================== 全局操作 ====================

    /**
     * 获取全局变量
     * @param name 变量名
     * @return 结果类型
     */
    public int getGlobal(String name) {
        return L.getGlobal(name);
    }

    /**
     * 设置全局变量
     * @param name 变量名
     */
    public void setGlobal(String name) {
        L.setGlobal(name);
    }

    // ==================== 元表操作 ====================

    /**
     * 获取元表
     * @param idx 索引
     * @return 是否有元表
     */
    public int getMetaTable(int idx) {
        return L.getMetaTable(idx);
    }

    /**
     * 设置元表
     * @param idx 索引
     * @return 是否成功
     */
    public int setMetaTable(int idx) {
        return L.setMetaTable(idx);
    }

    // ==================== 函数调用 ====================

    /**
     * 调用函数
     * @param nArgs 参数数量
     * @param nResults 返回值数量
     */
    public void call(int nArgs, int nResults) {
        L.call(nArgs, nResults);
    }

    /**
     * 保护调用函数
     * @param nArgs 参数数量
     * @param nResults 返回值数量
     * @param errFunc 错误处理函数索引
     * @return 错误码(0表示成功)
     */
    public int pcall(int nArgs, int nResults, int errFunc) {
        return L.pcall(nArgs, nResults, errFunc);
    }

    // ==================== 算术操作 ====================

    /**
     * 执行算术运算
     * @param op 操作类型
     */
    public void arith(int op) {
        synchronized (L) {
            _arith(L.getPointer(), op);
        }
    }

    private native void _arith(long ptr, int op);

    // ==================== 比较操作 ====================

    /**
     * 比较两个值
     * @param idx1 索引1
     * @param idx2 索引2
     * @param op 操作类型
     * @return 比较结果
     */
    public int compare(int idx1, int idx2, int op) {
        return L.compare(idx1, idx2, op);
    }

    // ==================== 长度操作 ====================

    /**
     * 获取长度(触发元方法)
     * @param idx 索引
     */
    public void len(int idx) {
        synchronized (L) {
            _len(L.getPointer(), idx);
        }
    }

    private native void _len(long ptr, int idx);

    // ==================== 连接操作 ====================

    /**
     * 连接字符串
     * @param n 元素数量
     */
    public void concat(int n) {
        L.concat(n);
    }

    // ==================== 错误处理 ====================

    /**
     * 触发错误
     * @return 错误码
     */
    public int error() {
        return L.error();
    }

    /**
     * 抛出Lua错误
     * @param msg 错误消息
     */
    public void throwError(String msg) throws LuaException {
        throw new LuaException(msg);
    }

    // ==================== 加载与执行 ====================

    /**
     * 加载文件
     * @param fileName 文件名
     * @return 错误码(0表示成功)
     */
    public int loadFile(String fileName) {
        return L.LloadFile(fileName);
    }

    /**
     * 加载字符串
     * @param s Lua代码字符串
     * @return 错误码(0表示成功)
     */
    public int loadString(String s) {
        return L.LloadString(s);
    }

    /**
     * 加载字节码
     * @param buff 字节码
     * @param name 名称
     * @return 错误码(0表示成功)
     */
    public int loadBuffer(byte[] buff, String name) {
        return L.LloadBuffer(buff, name);
    }

    /**
     * 执行文件
     * @param fileName 文件名
     * @return 错误码(0表示成功)
     */
    public int doFile(String fileName) {
        return L.LdoFile(fileName);
    }

    /**
     * 执行字符串
     * @param str Lua代码字符串
     * @return 错误码(0表示成功)
     */
    public int doString(String str) {
        return L.LdoString(str);
    }

    // ==================== 库加载 ====================

    /**
     * 打开所有标准库
     */
    public void openLibs() {
        L.openLibs();
    }

    /**
     * 打开基础库
     */
    public void openBase() {
        L.openBase();
    }

    /**
     * 打开表库
     */
    public void openTable() {
        L.openTable();
    }

    /**
     * 打开字符串库
     */
    public void openString() {
        L.openString();
    }

    /**
     * 打开数学库
     */
    public void openMath() {
        L.openMath();
    }

    /**
     * 打开调试库
     */
    public void openDebug() {
        L.openDebug();
    }

    // ==================== 引用操作 ====================

    /**
     * 创建引用
     * @param t 表索引
     * @return 引用ID
     */
    public int ref(int t) {
        return L.Lref(t);
    }

    /**
     * 释放引用
     * @param t 表索引
     * @param ref 引用ID
     */
    public void unref(int t, int ref) {
        L.LunRef(t, ref);
    }

    // ==================== Upvalue操作 ====================

    /**
     * 获取upvalue索引
     * @param n upvalue编号
     * @return 栈索引
     */
    public static int upvalueIndex(int n) {
        return LUA_REGISTRYINDEX - n;
    }

    /**
     * 获取Upvalue名
     * @param funcIndex 函数索引
     * @param n Upvalue编号
     * @return Upvalue名
     */
    public String getUpValue(int funcIndex, int n) {
        return L.getUpValue(funcIndex, n);
    }

    /**
     * 设置Upvalue
     * @param funcIndex 函数索引
     * @param n Upvalue编号
     * @return Upvalue名
     */
    public String setUpValue(int funcIndex, int n) {
        return L.setUpValue(funcIndex, n);
    }

    /**
     * 导出函数字节码
     * @param funcIndex 函数索引
     * @return 字节码
     */
    public byte[] dump(int funcIndex) {
        return L.dump(funcIndex);
    }

    // ==================== TCC特定API ====================

    /**
     * 函数序言 - 处理可变参数
     * @param nparams 固定参数数量
     * @param maxstack 最大栈大小
     */
    public void tccPrologue(int nparams, int maxstack) {
        synchronized (L) {
            _tccPrologue(L.getPointer(), nparams, maxstack);
        }
    }

    private native void _tccPrologue(long ptr, int nparams, int maxstack);

    /**
     * 获取upvalue表字段
     * @param upval upvalue索引
     * @param k 字段名
     * @param dest 目标位置
     */
    public void tccGettabup(int upval, String k, int dest) {
        synchronized (L) {
            _tccGettabup(L.getPointer(), upval, k, dest);
        }
    }

    private native void _tccGettabup(long ptr, int upval, String k, int dest);

    /**
     * 设置upvalue表字段
     * @param upval upvalue索引
     * @param k 字段名
     * @param valIdx 值索引
     */
    public void tccSettabup(int upval, String k, int valIdx) {
        synchronized (L) {
            _tccSettabup(L.getPointer(), upval, k, valIdx);
        }
    }

    private native void _tccSettabup(long ptr, int upval, String k, int valIdx);

    /**
     * 加载字符串常量到指定位置
     * @param dest 目标位置
     * @param s 字符串值
     */
    public void tccLoadkStr(int dest, String s) {
        pushString(s);
        replace(dest);
    }

    /**
     * 加载整数常量到指定位置
     * @param dest 目标位置
     * @param v 整数值
     */
    public void tccLoadkInt(int dest, long v) {
        pushInteger(v);
        replace(dest);
    }

    /**
     * 加载浮点常量到指定位置
     * @param dest 目标位置
     * @param v 浮点值
     */
    public void tccLoadkFlt(int dest, double v) {
        pushNumber(v);
        replace(dest);
    }

    /**
     * in操作符实现
     * @param valIdx 值索引
     * @param containerIdx 容器索引
     * @return 是否包含(1或0)
     */
    public int tccIn(int valIdx, int containerIdx) {
        synchronized (L) {
            return _tccIn(L.getPointer(), valIdx, containerIdx);
        }
    }

    private native int _tccIn(long ptr, int valIdx, int containerIdx);

    /**
     * 压入参数
     * @param startReg 起始寄存器
     * @param count 数量
     */
    public void tccPushArgs(int startReg, int count) {
        checkStack(count);
        for (int i = 0; i < count; i++) {
            pushValue(startReg + i);
        }
    }

    /**
     * 存储结果
     * @param startReg 起始寄存器
     * @param count 数量
     */
    public void tccStoreResults(int startReg, int count) {
        for (int i = count - 1; i >= 0; i--) {
            replace(startReg + i);
        }
    }

    // ==================== 扩展操作 ====================

    /**
     * 标记为待关闭
     * @param idx 索引
     */
    public void toClose(int idx) {
        synchronized (L) {
            _toClose(L.getPointer(), idx);
        }
    }

    private native void _toClose(long ptr, int idx);

    /**
     * 关闭槽
     * @param idx 索引
     */
    public void closeSlot(int idx) {
        synchronized (L) {
            _closeSlot(L.getPointer(), idx);
        }
    }

    private native void _closeSlot(long ptr, int idx);

    /**
     * 创建新类
     * @param className 类名
     */
    public void newClass(String className) {
        synchronized (L) {
            _newClass(L.getPointer(), className);
        }
    }

    private native void _newClass(long ptr, String className);

    /**
     * 继承
     * @param classIdx 类索引
     * @param parentIdx 父类索引
     */
    public void inherit(int classIdx, int parentIdx) {
        synchronized (L) {
            _inherit(L.getPointer(), classIdx, parentIdx);
        }
    }

    private native void _inherit(long ptr, int classIdx, int parentIdx);

    /**
     * 设置方法
     * @param classIdx 类索引
     * @param methodName 方法名
     * @param funcIdx 函数索引
     */
    public void setMethod(int classIdx, String methodName, int funcIdx) {
        synchronized (L) {
            _setMethod(L.getPointer(), classIdx, methodName, funcIdx);
        }
    }

    private native void _setMethod(long ptr, int classIdx, String methodName, int funcIdx);

    /**
     * 设置静态成员
     * @param classIdx 类索引
     * @param name 名称
     * @param valIdx 值索引
     */
    public void setStatic(int classIdx, String name, int valIdx) {
        synchronized (L) {
            _setStatic(L.getPointer(), classIdx, name, valIdx);
        }
    }

    private native void _setStatic(long ptr, int classIdx, String name, int valIdx);

    /**
     * 获取super
     * @param objIdx 对象索引
     * @param methodName 方法名
     */
    public void getSuper(int objIdx, String methodName) {
        synchronized (L) {
            _getSuper(L.getPointer(), objIdx, methodName);
        }
    }

    private native void _getSuper(long ptr, int objIdx, String methodName);

    /**
     * 创建新对象
     * @param classIdx 类索引
     * @param nargs 参数数量
     */
    public void newObject(int classIdx, int nargs) {
        synchronized (L) {
            _newObject(L.getPointer(), classIdx, nargs);
        }
    }

    private native void _newObject(long ptr, int classIdx, int nargs);

    /**
     * 获取属性
     * @param objIdx 对象索引
     * @param propName 属性名
     */
    public void getProp(int objIdx, String propName) {
        synchronized (L) {
            _getProp(L.getPointer(), objIdx, propName);
        }
    }

    private native void _getProp(long ptr, int objIdx, String propName);

    /**
     * 设置属性
     * @param objIdx 对象索引
     * @param propName 属性名
     * @param valIdx 值索引
     */
    public void setProp(int objIdx, String propName, int valIdx) {
        synchronized (L) {
            _setProp(L.getPointer(), objIdx, propName, valIdx);
        }
    }

    private native void _setProp(long ptr, int objIdx, String propName, int valIdx);

    /**
     * instanceof检查
     * @param objIdx 对象索引
     * @param classIdx 类索引
     * @return 是否是实例
     */
    public int instanceofCheck(int objIdx, int classIdx) {
        synchronized (L) {
            return _instanceof(L.getPointer(), objIdx, classIdx);
        }
    }

    private native int _instanceof(long ptr, int objIdx, int classIdx);

    /**
     * 实现接口
     * @param classIdx 类索引
     * @param ifaceIdx 接口索引
     */
    public void implement(int classIdx, int ifaceIdx) {
        synchronized (L) {
            _implement(L.getPointer(), classIdx, ifaceIdx);
        }
    }

    private native void _implement(long ptr, int classIdx, int ifaceIdx);

    /**
     * 类型检查
     * @param valIdx 值索引
     * @param typeName 类型名
     */
    public void checkType(int valIdx, String typeName) {
        synchronized (L) {
            _checkType(L.getPointer(), valIdx, typeName);
        }
    }

    private native void _checkType(long ptr, int valIdx, String typeName);

    /**
     * 飞船操作符(<=>)
     * @param idx1 索引1
     * @param idx2 索引2
     * @return 比较结果(-1, 0, 1)
     */
    public int spaceship(int idx1, int idx2) {
        synchronized (L) {
            return _spaceship(L.getPointer(), idx1, idx2);
        }
    }

    private native int _spaceship(long ptr, int idx1, int idx2);

    /**
     * is类型检查
     * @param valIdx 值索引
     * @param typeName 类型名
     * @return 是否是该类型
     */
    public int isType(int valIdx, String typeName) {
        synchronized (L) {
            return _isType(L.getPointer(), valIdx, typeName);
        }
    }

    private native int _isType(long ptr, int valIdx, String typeName);

    /**
     * 创建命名空间
     * @param name 名称
     */
    public void newNamespace(String name) {
        synchronized (L) {
            _newNamespace(L.getPointer(), name);
        }
    }

    private native void _newNamespace(long ptr, String name);

    /**
     * 链接命名空间
     * @param nsIdx 命名空间索引
     * @param targetIdx 目标索引
     */
    public void linkNamespace(int nsIdx, int targetIdx) {
        synchronized (L) {
            _linkNamespace(L.getPointer(), nsIdx, targetIdx);
        }
    }

    private native void _linkNamespace(long ptr, int nsIdx, int targetIdx);

    /**
     * 切片操作
     * @param objIdx 对象索引
     * @param startIdx 开始索引
     * @param endIdx 结束索引
     * @param stepIdx 步长索引
     */
    public void slice(int objIdx, int startIdx, int endIdx, int stepIdx) {
        synchronized (L) {
            _slice(L.getPointer(), objIdx, startIdx, endIdx, stepIdx);
        }
    }

    private native void _slice(long ptr, int objIdx, int startIdx, int endIdx, int stepIdx);

    /**
     * 非nil错误
     * @param valIdx 值索引
     * @param name 名称
     */
    public void errNotNil(int valIdx, String name) {
        synchronized (L) {
            _errNotNil(L.getPointer(), valIdx, name);
        }
    }

    private native void _errNotNil(long ptr, int valIdx, String name);

    // ==================== 工具方法 ====================

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
                    value = "userdata";
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
     * 关闭Lua状态机
     */
    public void close() {
        if (L != null) {
            L.close();
        }
    }

    // ==================== 闭包操作 ====================

    /**
     * 创建Java函数闭包
     * 将Java函数包装为Lua可调用的闭包
     * @param funcId 函数标识符(用于标识哪个生成的函数)
     * @param nUpvalues upvalue数量
     */
    public void pushClosure(int funcId, int nUpvalues) {
        synchronized (L) {
            _pushClosure(L.getPointer(), funcId, nUpvalues);
        }
    }

    private native void _pushClosure(long ptr, int funcId, int nUpvalues);

    /**
     * 推送Java函数到栈上
     * @param obj 包含方法的对象
     * @param methodName 方法名
     * @param nUpvalues upvalue数量
     */
    public void pushJavaFunction(Object obj, String methodName, int nUpvalues) {
        synchronized (L) {
            _pushJavaFunction(L.getPointer(), obj, methodName, nUpvalues);
        }
    }

    private native void _pushJavaFunction(long ptr, Object obj, String methodName, int nUpvalues);

    /**
     * 创建C闭包
     * @param func C函数指针
     * @param nUpvalues upvalue数量
     */
    public void pushCClosure(long func, int nUpvalues) {
        synchronized (L) {
            _pushCClosure(L.getPointer(), func, nUpvalues);
        }
    }

    private native void _pushCClosure(long ptr, long func, int nUpvalues);

    /**
     * 注册编译后的函数
     * @param funcId 函数ID
     * @param func 函数实现接口
     */
    public void registerCompiledFunction(int funcId, CompiledFunction func) {
        synchronized (L) {
            _registerCompiledFunc(L.getPointer(), funcId, func);
        }
    }

    private native void _registerCompiledFunc(long ptr, int funcId, CompiledFunction func);

    /**
     * 调用编译后的函数
     * @param funcId 函数ID
     * @param nArgs 参数数量
     * @param nResults 返回值数量
     */
    public void callCompiled(int funcId, int nArgs, int nResults) {
        synchronized (L) {
            _callCompiled(L.getPointer(), funcId, nArgs, nResults);
        }
    }

    private native void _callCompiled(long ptr, int funcId, int nArgs, int nResults);

    /**
     * 设置函数环境
     * @param funcIdx 函数索引
     * @param envIdx 环境表索引
     */
    public void setFEnv(int funcIdx, int envIdx) {
        synchronized (L) {
            _setFEnv(L.getPointer(), funcIdx, envIdx);
        }
    }

    private native void _setFEnv(long ptr, int funcIdx, int envIdx);

    /**
     * 获取函数环境
     * @param funcIdx 函数索引
     */
    public void getFEnv(int funcIdx) {
        synchronized (L) {
            _getFEnv(L.getPointer(), funcIdx);
        }
    }

    private native void _getFEnv(long ptr, int funcIdx);

    /**
     * 设置函数upvalue
     * @param funcIdx 函数索引
     * @param upvalIdx upvalue索引
     * @param valIdx 值索引
     */
    public void setUpvalue(int funcIdx, int upvalIdx, int valIdx) {
        synchronized (L) {
            _setUpvalue(L.getPointer(), funcIdx, upvalIdx, valIdx);
        }
    }

    private native void _setUpvalue(long ptr, int funcIdx, int upvalIdx, int valIdx);

    /**
     * 获取函数upvalue
     * @param funcIdx 函数索引
     * @param upvalIdx upvalue索引
     */
    public void getUpvalue(int funcIdx, int upvalIdx) {
        synchronized (L) {
            _getUpvalue(L.getPointer(), funcIdx, upvalIdx);
        }
    }

    private native void _getUpvalue(long ptr, int funcIdx, int upvalIdx);

    // ==================== 栈帧操作 ====================

    /**
     * 获取当前栈帧信息
     * @return 栈帧信息数组
     */
    public LuaStackFrame[] getStackFrames() {
        synchronized (L) {
            return _getStackFrames(L.getPointer());
        }
    }

    private native LuaStackFrame[] _getStackFrames(long ptr);

    /**
     * 获取局部变量
     * @param frame 栈帧级别
     * @param varIdx 变量索引
     */
    public void getLocal(int frame, int varIdx) {
        synchronized (L) {
            _getLocal(L.getPointer(), frame, varIdx);
        }
    }

    private native void _getLocal(long ptr, int frame, int varIdx);

    /**
     * 设置局部变量
     * @param frame 栈帧级别
     * @param varIdx 变量索引
     */
    public void setLocal(int frame, int varIdx) {
        synchronized (L) {
            _setLocal(L.getPointer(), frame, varIdx);
        }
    }

    private native void _setLocal(long ptr, int frame, int varIdx);

    /**
     * 获取局部变量名
     * @param frame 栈帧级别
     * @param varIdx 变量索引
     * @return 变量名
     */
    public String getLocalName(int frame, int varIdx) {
        synchronized (L) {
            return _getLocalName(L.getPointer(), frame, varIdx);
        }
    }

    private native String _getLocalName(long ptr, int frame, int varIdx);
}
