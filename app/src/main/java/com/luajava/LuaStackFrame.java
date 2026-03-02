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
 * Lua栈帧信息类
 * 用于调试和错误追踪
 * 
 * @author DifierLine
 */
public class LuaStackFrame {

    /** 栈帧级别 */
    public int level;

    /** 函数名 */
    public String name;

    /** 函数类型 */
    public String nameWhat;

    /** 源文件名 */
    public String source;

    /** 简短源名 */
    public String shortSrc;

    /** 当前行号 */
    public int currentLine;

    /** 函数起始行 */
    public int lineDefined;

    /** 函数结束行 */
    public int lastLineDefined;

    /** 函数类型("Lua", "C", "main", "Java"等) */
    public String what;

    /** 是否尾调用 */
    public boolean isTailCall;

    /** 参数数量 */
    public int numParams;

    /** 是否可变参数 */
    public boolean isVararg;

    /** upvalue数量 */
    public int numUpvalues;

    /**
     * 默认构造函数
     */
    public LuaStackFrame() {
    }

    /**
     * 构造函数
     * @param level 栈帧级别
     * @param name 函数名
     * @param source 源文件名
     * @param currentLine 当前行号
     */
    public LuaStackFrame(int level, String name, String source, int currentLine) {
        this.level = level;
        this.name = name;
        this.source = source;
        this.currentLine = currentLine;
    }

    /**
     * 获取格式化的栈帧字符串
     * @return 格式化字符串
     */
    public String toTracebackString() {
        StringBuilder sb = new StringBuilder();
        
        if (shortSrc != null) {
            sb.append(shortSrc);
        } else if (source != null) {
            sb.append(source);
        } else {
            sb.append("[?]");
        }
        
        sb.append(":");
        
        if (currentLine > 0) {
            sb.append(currentLine);
        } else {
            sb.append("?");
        }
        
        sb.append(": ");
        
        if (name != null && !name.isEmpty()) {
            sb.append("in function '").append(name).append("'");
        } else if ("main".equals(what)) {
            sb.append("in main chunk");
        } else if ("C".equals(what) || "Java".equals(what)) {
            sb.append("in ").append(what).append(" function");
        } else {
            sb.append("in function");
        }
        
        if (isTailCall) {
            sb.append(" (tail call)");
        }
        
        return sb.toString();
    }

    @Override
    public String toString() {
        return "LuaStackFrame{" +
                "level=" + level +
                ", name='" + name + '\'' +
                ", source='" + source + '\'' +
                ", currentLine=" + currentLine +
                ", what='" + what + '\'' +
                '}';
    }
}
