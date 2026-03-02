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

/**
 * 编译后的函数接口
 * 用于LuaToJava生成的函数实现此接口
 * 
 * @author DifierLine
 */
public interface CompiledFunction {

    /**
     * 执行函数
     * @param L LuaJava运行时实例
     * @return 返回值数量
     * @throws LuaException 执行错误
     */
    int execute(LuaJava L) throws LuaException;

    /**
     * 获取函数ID
     * @return 函数ID
     */
    int getFunctionId();

    /**
     * 获取函数名
     * @return 函数名
     */
    String getFunctionName();

    /**
     * 获取参数数量
     * @return 参数数量
     */
    int getNumParams();

    /**
     * 是否可变参数
     * @return 是否可变参数
     */
    boolean isVararg();

    /**
     * 获取upvalue数量
     * @return upvalue数量
     */
    int getNumUpvalues();
}
