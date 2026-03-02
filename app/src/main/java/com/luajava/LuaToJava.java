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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lua转Java代码转换器
 * 将Lua字节码转换为等效的Java代码
 * 
 * @author DifierLine
 */
public class LuaToJava {

    private static final String TAG = "LuaToJava";

    // ==================== Lua常量定义 ====================

    public static final int LUAI_MAXSTACK = 1000000;
    public static final int LUA_REGISTRYINDEX = -LUAI_MAXSTACK - 1000;
    public static final int LUA_MULTRET = -1;

    // ==================== Lua类型常量 ====================

    public static final int LUA_TNIL = 0;
    public static final int LUA_TBOOLEAN = 1;
    public static final int LUA_TLIGHTUSERDATA = 2;
    public static final int LUA_TNUMBER = 3;
    public static final int LUA_TSTRING = 4;
    public static final int LUA_TTABLE = 5;
    public static final int LUA_TFUNCTION = 6;
    public static final int LUA_TUSERDATA = 7;
    public static final int LUA_TTHREAD = 8;

    // ==================== 操作码定义 ====================

    public static final int OP_MOVE = 0;
    public static final int OP_LOADI = 1;
    public static final int OP_LOADF = 2;
    public static final int OP_LOADK = 3;
    public static final int OP_LOADKX = 4;
    public static final int OP_LOADFALSE = 5;
    public static final int OP_LFALSESKIP = 6;
    public static final int OP_LOADTRUE = 7;
    public static final int OP_LOADNIL = 8;
    public static final int OP_GETUPVAL = 9;
    public static final int OP_SETUPVAL = 10;
    public static final int OP_GETTABUP = 11;
    public static final int OP_GETTABLE = 12;
    public static final int OP_GETI = 13;
    public static final int OP_GETFIELD = 14;
    public static final int OP_SETTABUP = 15;
    public static final int OP_SETTABLE = 16;
    public static final int OP_SETI = 17;
    public static final int OP_SETFIELD = 18;
    public static final int OP_NEWTABLE = 19;
    public static final int OP_SELF = 20;
    public static final int OP_ADDI = 21;
    public static final int OP_ADDK = 22;
    public static final int OP_SUBK = 23;
    public static final int OP_MULK = 24;
    public static final int OP_MODK = 25;
    public static final int OP_POWK = 26;
    public static final int OP_DIVK = 27;
    public static final int OP_IDIVK = 28;
    public static final int OP_BANDK = 29;
    public static final int OP_BORK = 30;
    public static final int OP_BXORK = 31;
    public static final int OP_SHRI = 32;
    public static final int OP_SHLI = 33;
    public static final int OP_ADD = 34;
    public static final int OP_SUB = 35;
    public static final int OP_MUL = 36;
    public static final int OP_MOD = 37;
    public static final int OP_POW = 38;
    public static final int OP_DIV = 39;
    public static final int OP_IDIV = 40;
    public static final int OP_BAND = 41;
    public static final int OP_BOR = 42;
    public static final int OP_BXOR = 43;
    public static final int OP_SHL = 44;
    public static final int OP_SHR = 45;
    public static final int OP_SPACESHIP = 46;
    public static final int OP_MMBIN = 47;
    public static final int OP_MMBINI = 48;
    public static final int OP_MMBINK = 49;
    public static final int OP_UNM = 50;
    public static final int OP_BNOT = 51;
    public static final int OP_NOT = 52;
    public static final int OP_LEN = 53;
    public static final int OP_CONCAT = 54;
    public static final int OP_CLOSE = 55;
    public static final int OP_TBC = 56;
    public static final int OP_JMP = 57;
    public static final int OP_EQ = 58;
    public static final int OP_LT = 59;
    public static final int OP_LE = 60;
    public static final int OP_EQK = 61;
    public static final int OP_EQI = 62;
    public static final int OP_LTI = 63;
    public static final int OP_LEI = 64;
    public static final int OP_GTI = 65;
    public static final int OP_GEI = 66;
    public static final int OP_TEST = 67;
    public static final int OP_TESTSET = 68;
    public static final int OP_CALL = 69;
    public static final int OP_TAILCALL = 70;
    public static final int OP_RETURN = 71;
    public static final int OP_RETURN0 = 72;
    public static final int OP_RETURN1 = 73;
    public static final int OP_FORLOOP = 74;
    public static final int OP_FORPREP = 75;
    public static final int OP_TFORPREP = 76;
    public static final int OP_TFORCALL = 77;
    public static final int OP_TFORLOOP = 78;
    public static final int OP_SETLIST = 79;
    public static final int OP_CLOSURE = 80;
    public static final int OP_VARARG = 81;
    public static final int OP_GETVARG = 82;
    public static final int OP_ERRNNIL = 83;
    public static final int OP_VARARGPREP = 84;
    public static final int OP_IS = 85;
    public static final int OP_TESTNIL = 86;
    public static final int OP_NEWCLASS = 87;
    public static final int OP_INHERIT = 88;
    public static final int OP_GETSUPER = 89;
    public static final int OP_SETMETHOD = 90;
    public static final int OP_SETSTATIC = 91;
    public static final int OP_NEWOBJ = 92;
    public static final int OP_GETPROP = 93;
    public static final int OP_SETPROP = 94;
    public static final int OP_INSTANCEOF = 95;
    public static final int OP_IMPLEMENT = 96;
    public static final int OP_SETIFACEFLAG = 97;
    public static final int OP_ADDMETHOD = 98;
    public static final int OP_IN = 99;
    public static final int OP_SLICE = 100;
    public static final int OP_NOP = 101;
    public static final int OP_CASE = 102;
    public static final int OP_NEWCONCEPT = 103;
    public static final int OP_NEWNAMESPACE = 104;
    public static final int OP_LINKNAMESPACE = 105;
    public static final int OP_NEWSUPER = 106;
    public static final int OP_SETSUPER = 107;
    public static final int OP_GETCMDS = 108;
    public static final int OP_GETOPS = 109;
    public static final int OP_ASYNCWRAP = 110;
    public static final int OP_GENERICWRAP = 111;
    public static final int OP_CHECKTYPE = 112;
    public static final int OP_EXTRAARG = 113;

    // ==================== 操作码名称 ====================

    private static final String[] OP_NAMES = {
        "MOVE", "LOADI", "LOADF", "LOADK", "LOADKX", "LOADFALSE", "LFALSESKIP", "LOADTRUE",
        "LOADNIL", "GETUPVAL", "SETUPVAL", "GETTABUP", "GETTABLE", "GETI", "GETFIELD", "SETTABUP",
        "SETTABLE", "SETI", "SETFIELD", "NEWTABLE", "SELF", "ADDI", "ADDK", "SUBK",
        "MULK", "MODK", "POWK", "DIVK", "IDIVK", "BANDK", "BORK", "BXORK",
        "SHRI", "SHLI", "ADD", "SUB", "MUL", "MOD", "POW", "DIV",
        "IDIV", "BAND", "BOR", "BXOR", "SHL", "SHR", "SPACESHIP", "MMBIN",
        "MMBINI", "MMBINK", "UNM", "BNOT", "NOT", "LEN", "CONCAT", "CLOSE",
        "TBC", "JMP", "EQ", "LT", "LE", "EQK", "EQI", "LTI",
        "LEI", "GTI", "GEI", "TEST", "TESTSET", "CALL", "TAILCALL", "RETURN",
        "RETURN0", "RETURN1", "FORLOOP", "FORPREP", "TFORPREP", "TFORCALL", "TFORLOOP", "SETLIST",
        "CLOSURE", "VARARG", "GETVARG", "ERRNNIL", "VARARGPREP", "IS", "TESTNIL", "NEWCLASS",
        "INHERIT", "GETSUPER", "SETMETHOD", "SETSTATIC", "NEWOBJ", "GETPROP", "SETPROP", "INSTANCEOF",
        "IMPLEMENT", "SETIFACEFLAG", "ADDMETHOD", "IN", "SLICE", "NOP", "CASE", "NEWCONCEPT",
        "NEWNAMESPACE", "LINKNAMESPACE", "NEWSUPER", "SETSUPER", "GETCMDS", "GETOPS", "ASYNCWRAP", "GENERICWRAP",
        "CHECKTYPE", "EXTRAARG"
    };

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

    // ==================== 编译选项 ====================

    private boolean usePureJava = false;
    private boolean obfuscate = false;
    private boolean stringEncryption = false;
    private int seed = 0;
    private String moduleName = "LuaModule";
    private String className = "LuaGenerated";
    private Random random;

    // ==================== Proto信息 ====================

    private static class ProtoInfo {
        int id;
        String name;
        int numParams;
        int maxStackSize;
        boolean isVararg;
        long[] code;
        InstructionInfo[] instrInfo;
        Object[] constants;
        int[] constantTypes;
        ProtoInfo[] subProtos;
        UpvalDesc[] upvalues;
        int sizeUpvalues;
    }

    private static class UpvalDesc {
        boolean instack;
        int idx;
        String name;
    }

    // ==================== 构造函数 ====================

    /**
     * 创建Lua转Java转换器
     */
    public LuaToJava() {
        this.random = new Random();
    }

    /**
     * 创建Lua转Java转换器
     * @param seed 随机种子(用于混淆)
     */
    public LuaToJava(int seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    // ==================== 配置方法 ====================

    /**
     * 设置是否使用纯Java运算
     * @param usePureJava 是否使用纯Java运算
     * @return this
     */
    public LuaToJava setUsePureJava(boolean usePureJava) {
        this.usePureJava = usePureJava;
        return this;
    }

    /**
     * 设置是否混淆
     * @param obfuscate 是否混淆
     * @return this
     */
    public LuaToJava setObfuscate(boolean obfuscate) {
        this.obfuscate = obfuscate;
        return this;
    }

    /**
     * 设置是否加密字符串
     * @param encrypt 是否加密字符串
     * @return this
     */
    public LuaToJava setStringEncryption(boolean encrypt) {
        this.stringEncryption = encrypt;
        return this;
    }

    /**
     * 设置随机种子
     * @param seed 随机种子
     * @return this
     */
    public LuaToJava setSeed(int seed) {
        this.seed = seed;
        this.random = new Random(seed);
        return this;
    }

    /**
     * 设置模块名
     * @param name 模块名
     * @return this
     */
    public LuaToJava setModuleName(String name) {
        this.moduleName = name;
        return this;
    }

    /**
     * 设置类名
     * @param name 类名
     * @return this
     */
    public LuaToJava setClassName(String name) {
        this.className = name;
        return this;
    }

    // ==================== 主编译方法 ====================

    /**
     * 编译Lua代码为Java代码
     * @param luaCode Lua代码
     * @return Java代码
     */
    public String compile(String luaCode) throws LuaException {
        LuaState L = LuaStateFactory.newLuaState();
        try {
            L.openLibs();
            
            String protoJson = L.parseProto(luaCode);
            if (protoJson == null) {
                throw new LuaException("无法解析Lua代码");
            }
            
            if (protoJson.startsWith("{") == false) {
                throw new LuaException("Lua语法错误: " + protoJson);
            }
            
            return compileFromJson(protoJson);
        } finally {
            L.close();
        }
    }

    /**
     * 从JSON格式的Proto信息生成Java代码
     * @param json Proto的JSON字符串
     * @return Java代码
     */
    private String compileFromJson(String json) throws LuaException {
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            ProtoInfo mainProto = parseProtoFromJson(root);
            
            ArrayList<ProtoInfo> protos = new ArrayList<>();
            collectProtos(mainProto, protos);
            
            StringBuilder sb = new StringBuilder();
            generateJavaCode(sb, protos);
            return sb.toString();
        } catch (Exception e) {
            throw new LuaException("解析Proto JSON失败: " + e.getMessage());
        }
    }

    /**
     * 从JSON对象解析ProtoInfo
     */
    private ProtoInfo parseProtoFromJson(org.json.JSONObject obj) throws org.json.JSONException {
        ProtoInfo proto = new ProtoInfo();
        
        proto.numParams = obj.getInt("numparams");
        proto.isVararg = obj.getInt("is_vararg") != 0;
        proto.maxStackSize = obj.getInt("maxstacksize");
        
        org.json.JSONArray codeArr = obj.getJSONArray("code");
        proto.code = new long[codeArr.length()];
        proto.instrInfo = new InstructionInfo[codeArr.length()];
        for (int i = 0; i < codeArr.length(); i++) {
            org.json.JSONObject instr = codeArr.getJSONObject(i);
            proto.code[i] = 0;
            InstructionInfo info = new InstructionInfo();
            info.opcode = instr.getInt("op");
            info.opname = instr.getString("opname");
            info.a = instr.getInt("a");
            info.b = instr.optInt("b", 0);
            info.c = instr.optInt("c", 0);
            info.k = instr.optInt("k", 0);
            info.bx = instr.optLong("bx", 0);
            info.sbx = instr.optInt("sbx", 0);
            info.ax = instr.optLong("ax", 0);
            info.sj = instr.optLong("sj", 0);
            info.vb = instr.optInt("vb", 0);
            info.vc = instr.optInt("vc", 0);
            info.sc = instr.optInt("sc", 0);
            info.sb = instr.optInt("sb", 0);
            proto.instrInfo[i] = info;
        }
        
        org.json.JSONArray kArr = obj.getJSONArray("k");
        proto.constants = new Object[kArr.length()];
        proto.constantTypes = new int[kArr.length()];
        for (int i = 0; i < kArr.length(); i++) {
            Object val = kArr.get(i);
            if (val == org.json.JSONObject.NULL) {
                proto.constants[i] = null;
                proto.constantTypes[i] = LUA_TNIL;
            } else if (val instanceof Boolean) {
                proto.constants[i] = val;
                proto.constantTypes[i] = LUA_TBOOLEAN;
            } else if (val instanceof Integer || val instanceof Long) {
                proto.constants[i] = ((Number)val).doubleValue();
                proto.constantTypes[i] = LUA_TNUMBER;
            } else if (val instanceof Number) {
                proto.constants[i] = ((Number)val).doubleValue();
                proto.constantTypes[i] = LUA_TNUMBER;
            } else if (val instanceof String) {
                proto.constants[i] = val;
                proto.constantTypes[i] = LUA_TSTRING;
            } else {
                proto.constants[i] = null;
                proto.constantTypes[i] = LUA_TNIL;
            }
        }
        
        org.json.JSONArray pArr = obj.optJSONArray("p");
        if (pArr != null && pArr.length() > 0) {
            proto.subProtos = new ProtoInfo[pArr.length()];
            for (int i = 0; i < pArr.length(); i++) {
                proto.subProtos[i] = parseProtoFromJson(pArr.getJSONObject(i));
            }
        } else {
            proto.subProtos = new ProtoInfo[0];
        }
        
        return proto;
    }

    /**
     * 指令信息类
     */
    private static class InstructionInfo {
        int opcode;
        String opname;
        int a, b, c, k;
        int sbx;
        long bx, ax, sj;
        int vb, vc;
        int sc, sb;
    }

    // ==================== 字节码解析 ====================

    /**
     * 解析字节码
     */
    private ProtoInfo parseBytecode(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            
            if (buf.remaining() < 4) {
                android.util.Log.e(TAG, "字节码太短: " + buf.remaining());
                return null;
            }
            
            int signature = buf.getInt();
            if (signature != 0x1B4C7561) {
                android.util.Log.e(TAG, "签名错误: 0x" + Integer.toHexString(signature));
                return null;
            }
            
            byte version = buf.get();
            byte format = buf.get();
            android.util.Log.d(TAG, "Lua版本: " + (version >> 4) + "." + (version & 0x0F) + ", 格式: " + format);
            
            byte[] luacData = new byte[6];
            buf.get(luacData);
            
            byte sizeofInt = buf.get();
            byte sizeofSizeT = buf.get();
            byte sizeofInstruction = buf.get();
            byte sizeofInteger = buf.get();
            byte sizeofNumber = buf.get();
            
            android.util.Log.d(TAG, "sizeofInt=" + sizeofInt + ", sizeofSizeT=" + sizeofSizeT + 
                ", sizeofInstruction=" + sizeofInstruction + ", sizeofInteger=" + sizeofInteger + 
                ", sizeofNumber=" + sizeofNumber);
            
            long luacInt = sizeofInteger == 8 ? buf.getLong() : buf.getInt();
            double luacNum = buf.getDouble();
            
            int sizeUpvalues = buf.get() & 0xFF;
            android.util.Log.d(TAG, "sizeUpvalues=" + sizeUpvalues + ", 剩余字节=" + buf.remaining());
            
            return parseProto(buf, sizeofInt, sizeofSizeT, sizeofInstruction, sizeofInteger, sizeofNumber);
        } catch (Exception e) {
            android.util.Log.e(TAG, "解析字节码异常: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析Proto
     */
    private ProtoInfo parseProto(ByteBuffer buf, int sizeofInt, int sizeofSizeT, 
                                  int sizeofInstruction, int sizeofInteger, int sizeofNumber) {
        ProtoInfo proto = new ProtoInfo();
        
        try {
            proto.name = parseString(buf, sizeofSizeT);
            int firstLine = (int) (sizeofInteger == 8 ? buf.getLong() : buf.getInt());
            int lastLine = (int) (sizeofInteger == 8 ? buf.getLong() : buf.getInt());
            byte numParams = buf.get();
            byte isVararg = buf.get();
            byte maxStackSize = buf.get();
            
            proto.numParams = numParams;
            proto.isVararg = isVararg != 0;
            proto.maxStackSize = maxStackSize & 0xFF;
            
            int codeSize = (int) readSizeT(buf, sizeofSizeT);
            proto.code = new long[codeSize];
            for (int i = 0; i < codeSize; i++) {
                proto.code[i] = buf.getLong();
            }
            
            int constSize = (int) readSizeT(buf, sizeofSizeT);
            proto.constants = new Object[constSize];
            proto.constantTypes = new int[constSize];
            
            for (int i = 0; i < constSize; i++) {
                byte type = buf.get();
                proto.constantTypes[i] = type;
                switch (type) {
                    case LUA_TNIL:
                        proto.constants[i] = null;
                        break;
                    case LUA_TBOOLEAN:
                        proto.constants[i] = buf.get() != 0;
                        break;
                    case LUA_TNUMBER:
                        if (sizeofNumber == 8) {
                            proto.constants[i] = buf.getDouble();
                        } else {
                            proto.constants[i] = (double) buf.getFloat();
                        }
                        break;
                    case LUA_TSTRING:
                        proto.constants[i] = parseString(buf, sizeofSizeT);
                        break;
                    default:
                        proto.constants[i] = null;
                        break;
                }
            }
            
            int protoSize = (int) readSizeT(buf, sizeofSizeT);
            proto.subProtos = new ProtoInfo[protoSize];
            for (int i = 0; i < protoSize; i++) {
                proto.subProtos[i] = parseProto(buf, sizeofInt, sizeofSizeT, 
                                                sizeofInstruction, sizeofInteger, sizeofNumber);
            }
            
            int upvalSize = (int) readSizeT(buf, sizeofSizeT);
            proto.upvalues = new UpvalDesc[upvalSize];
            proto.sizeUpvalues = upvalSize;
            for (int i = 0; i < upvalSize; i++) {
                UpvalDesc desc = new UpvalDesc();
                desc.instack = buf.get() != 0;
                desc.idx = buf.get() & 0xFF;
                desc.name = parseString(buf, sizeofSizeT);
                proto.upvalues[i] = desc;
            }
            
            return proto;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析字符串
     */
    private String parseString(ByteBuffer buf, int sizeofSizeT) {
        try {
            long size = readSizeT(buf, sizeofSizeT);
            if (size == 0) return null;
            size--;
            if (size > Integer.MAX_VALUE) return null;
            byte[] bytes = new byte[(int) size];
            buf.get(bytes);
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取size_t
     */
    private long readSizeT(ByteBuffer buf, int sizeofSizeT) {
        switch (sizeofSizeT) {
            case 4: return buf.getInt() & 0xFFFFFFFFL;
            case 8: return buf.getLong();
            default: return buf.getInt() & 0xFFFFFFFFL;
        }
    }

    // ==================== Proto收集 ====================

    /**
     * 收集所有Proto
     */
    private void collectProtos(ProtoInfo proto, ArrayList<ProtoInfo> list) {
        proto.id = list.size();
        proto.name = obfuscate ? getRandomName() : "function_" + proto.id;
        list.add(proto);
        
        if (proto.subProtos != null) {
            for (ProtoInfo sub : proto.subProtos) {
                collectProtos(sub, list);
            }
        }
    }

    // ==================== Java代码生成 ====================

    /**
     * 生成Java代码
     */
    private void generateJavaCode(StringBuilder sb, ArrayList<ProtoInfo> protos) {
        sb.append("/*\n");
        sb.append(" * 由LuaToJava自动生成\n");
        sb.append(" * 模块名: ").append(moduleName).append("\n");
        sb.append(" * 生成时间: ").append(new java.util.Date()).append("\n");
        sb.append(" */\n\n");
        
        sb.append("import com.luajava.LuaJava;\n");
        sb.append("import com.luajava.LuaException;\n");
        sb.append("import com.luajava.CompiledFunction;\n\n");
        
        sb.append("public class ").append(className).append(" implements CompiledFunction {\n\n");
        
        sb.append("    private int funcId;\n");
        sb.append("    private int vtabIdx;\n\n");
        
        for (int i = 0; i < protos.size(); i++) {
            generateFunction(sb, protos.get(i), protos);
            if (i < protos.size() - 1) {
                sb.append("\n");
            }
        }
        
        sb.append("\n");
        sb.append("    public ").append(className).append("() {\n");
        sb.append("        this.funcId = 0;\n");
        sb.append("    }\n\n");
        
        sb.append("    public ").append(className).append("(int id) {\n");
        sb.append("        this.funcId = id;\n");
        sb.append("    }\n\n");
        
        sb.append("    @Override\n");
        sb.append("    public int execute(LuaJava L) throws LuaException {\n");
        sb.append("        return ").append(protos.get(0).name).append("(L);\n");
        sb.append("    }\n\n");
        
        sb.append("    @Override\n");
        sb.append("    public int getFunctionId() {\n");
        sb.append("        return funcId;\n");
        sb.append("    }\n\n");
        
        sb.append("    @Override\n");
        sb.append("    public String getFunctionName() {\n");
        sb.append("        return \"").append(protos.get(0).name).append("\";\n");
        sb.append("    }\n\n");
        
        sb.append("    @Override\n");
        sb.append("    public int getNumParams() {\n");
        sb.append("        return ").append(protos.get(0).numParams).append(";\n");
        sb.append("    }\n\n");
        
        sb.append("    @Override\n");
        sb.append("    public boolean isVararg() {\n");
        sb.append("        return ").append(protos.get(0).isVararg).append(";\n");
        sb.append("    }\n\n");
        
        sb.append("    @Override\n");
        sb.append("    public int getNumUpvalues() {\n");
        sb.append("        return ").append(protos.get(0).sizeUpvalues).append(";\n");
        sb.append("    }\n");
        
        sb.append("}\n");
    }

    /**
     * 生成函数
     */
    private void generateFunction(StringBuilder sb, ProtoInfo proto, ArrayList<ProtoInfo> protos) {
        sb.append("    private int ").append(proto.name).append("(LuaJava L) throws LuaException {\n");
        
        if (proto.isVararg) {
            sb.append("        int _nargs = L.getTop();\n");
            sb.append("        vtabIdx = ").append(proto.numParams + 1).append(";\n");
            sb.append("        if (_nargs < ").append(proto.maxStackSize).append(") {\n");
            sb.append("            L.setTop(").append(proto.maxStackSize).append(");\n");
            sb.append("        }\n");
            sb.append("        // 处理可变参数\n");
        } else {
            sb.append("        L.setTop(").append(proto.maxStackSize).append(");\n");
        }
        
        sb.append("        int _pc = 0;\n");
        sb.append("        while (true) {\n");
        sb.append("            switch (_pc) {\n");
        
        // 先构建 pcToCase 映射
        int caseNum = 0;
        int[] pcToCase = new int[proto.code.length];
        for (int pc = 0; pc < proto.code.length; pc++) {
            InstructionInfo info = proto.instrInfo[pc];
            if (info.opcode == OP_EXTRAARG) {
                pcToCase[pc] = -1;
            } else {
                pcToCase[pc] = caseNum++;
            }
        }
        
        // 再生成指令
        caseNum = 0;
        for (int pc = 0; pc < proto.code.length; pc++) {
            InstructionInfo info = proto.instrInfo[pc];
            int opcode = info.opcode;
            
            if (opcode == OP_EXTRAARG) {
                continue;
            }
            
            sb.append("                case ").append(caseNum).append(": {\n");
            sb.append("                    // ").append(info.opname).append("\n");
            generateInstruction(sb, proto, pc, info, protos, pcToCase);
            
            if (opcode != OP_JMP && opcode != OP_EQ && opcode != OP_LT && opcode != OP_LE &&
                opcode != OP_EQK && opcode != OP_EQI && opcode != OP_LTI && opcode != OP_LEI &&
                opcode != OP_GTI && opcode != OP_GEI && opcode != OP_TEST && opcode != OP_TESTSET &&
                opcode != OP_FORPREP && opcode != OP_FORLOOP && opcode != OP_TFORLOOP &&
                opcode != OP_TFORPREP &&
                opcode != OP_RETURN && opcode != OP_RETURN0 && opcode != OP_RETURN1) {
                sb.append("                    _pc = ").append(caseNum + 1).append("; break;\n");
            }
            sb.append("                }\n");
            caseNum++;
        }
        
        sb.append("                default: return 0;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n");
    }

    /**
     * 查找目标PC对应的case编号
     */
    private int findCaseNum(int targetPc, int[] pcToCase, int codeLength) {
        if (targetPc < 0 || targetPc >= codeLength) {
            return -1;
        }
        return pcToCase[targetPc];
    }

    /**
     * 分析跳转目标
     */
    private HashMap<Integer, String> analyzeJumpTargets(InstructionInfo[] instrInfo) {
        HashMap<Integer, String> labels = new HashMap<>();
        
        for (int pc = 0; pc < instrInfo.length; pc++) {
            InstructionInfo info = instrInfo[pc];
            int opcode = info.opcode;
            
            switch (opcode) {
                case OP_JMP: {
                    long sj = info.sj;
                    int target = pc + 1 + (int)sj;
                    if (target >= 0 && target < instrInfo.length) {
                        labels.put(target, obfuscate ? getRandomName() : "Label_" + target);
                    }
                    break;
                }
                case OP_EQ: case OP_LT: case OP_LE:
                case OP_EQK: case OP_EQI: case OP_LTI: case OP_LEI:
                case OP_GTI: case OP_GEI:
                case OP_TEST: case OP_TESTSET: case OP_TESTNIL:
                case OP_INSTANCEOF: case OP_IS: {
                    int target = pc + 2;
                    if (target >= 0 && target < instrInfo.length) {
                        labels.put(target, obfuscate ? getRandomName() : "Label_" + target);
                    }
                    break;
                }
                case OP_LFALSESKIP: {
                    int target = pc + 2;
                    if (target >= 0 && target < instrInfo.length) {
                        labels.put(target, obfuscate ? getRandomName() : "Label_" + target);
                    }
                    break;
                }
                case OP_FORLOOP: {
                    long bx = info.bx;
                    int target = pc + 2 - (int)bx - 1;
                    if (target >= 0 && target < instrInfo.length) {
                        labels.put(target, obfuscate ? getRandomName() : "Label_" + target);
                    }
                    break;
                }
                case OP_FORPREP: {
                    long bx = info.bx;
                    int target = pc + 1 + (int)bx + 1;
                    if (target >= 0 && target < instrInfo.length) {
                        labels.put(target, obfuscate ? getRandomName() : "Label_" + target);
                    }
                    break;
                }
                case OP_TFORPREP: {
                    long bx = info.bx;
                    int target = pc + 1 + (int)bx + 1;
                    if (target >= 0 && target < instrInfo.length) {
                        labels.put(target, obfuscate ? getRandomName() : "Label_" + target);
                    }
                    break;
                }
                case OP_TFORLOOP: {
                    long bx = info.bx;
                    int target = pc + 2 - (int)bx - 1;
                    if (target >= 0 && target < instrInfo.length) {
                        labels.put(target, obfuscate ? getRandomName() : "Label_" + target);
                    }
                    break;
                }
            }
        }
        
        return labels;
    }

    /**
     * 生成指令
     */
    private void generateInstruction(StringBuilder sb, ProtoInfo proto, int pc, 
                                      InstructionInfo info, ArrayList<ProtoInfo> protos, int[] pcToCase) {
        int opcode = info.opcode;
        int a = info.a;
        int b = info.b;
        int c = info.c;
        int kflag = info.k;
        long bx = info.bx;
        int sbx = info.sbx;
        long ax = info.ax;
        long sj = info.sj;
        int vb = info.vb;
        int vc = info.vc;
        int sc = info.sc;
        int sbArg = info.sb;
        
        sb.append("                    // ").append(info.opname).append(" pc=").append(pc)
          .append(" a=").append(a).append(" b=").append(b).append(" c=").append(c).append(" k=").append(kflag);
        if (bx != 0) sb.append(" bx=").append(bx);
        if (sbx != 0) sb.append(" sbx=").append(sbx);
        if (ax != 0) sb.append(" ax=").append(ax);
        if (sj != 0) sb.append(" sj=").append(sj);
        if (vb != 0) sb.append(" vb=").append(vb);
        if (vc != 0) sb.append(" vc=").append(vc);
        if (sc != 0) sb.append(" sc=").append(sc);
        if (sbArg != 0) sb.append(" sb=").append(sbArg);
        sb.append("\n");
        
        switch (opcode) {
            case OP_MOVE: {
                sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_LOADI: {
                sb.append("                    // LOADI: pc=").append(pc).append(" a=").append(a).append(" sbx=").append(sbx).append("\n");
                sb.append("                    L.pushInteger(").append(sbx).append(");\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_LOADF: {
                sb.append("                    // LOADF: pc=").append(pc).append(" a=").append(a).append(" sbx=").append(sbx).append("\n");
                sb.append("                    L.pushNumber(").append((double)sbx).append(");\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_LOADK: {
                sb.append("                    // LOADK: pc=").append(pc).append(" a=").append(a).append(" bx=").append(bx).append("\n");
                generateLoadK(sb, proto, (int)bx, a);
                break;
            }
            
            case OP_LOADKX: {
                if (pc + 1 < proto.code.length) {
                    long nextInst = proto.code[pc + 1];
                    if ((nextInst & 0x1FF) == OP_EXTRAARG) {
                        long localAx = nextInst >>> 16;
                        generateLoadK(sb, proto, (int)localAx, a);
                    }
                }
                break;
            }
            
            case OP_LOADFALSE: {
                sb.append("                    L.pushBoolean(false);\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_LFALSESKIP: {
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                sb.append("                    if (!L.toBoolean(").append(a + 1).append(")) {\n");
                sb.append("                        _pc = ").append(targetCase).append("; break;\n");
                sb.append("                    } else {\n");
                sb.append("                        L.pushBoolean(false);\n");
                sb.append("                        L.replace(").append(a + 1).append(");\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_LOADTRUE: {
                sb.append("                    L.pushBoolean(true);\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_LOADNIL: {
                sb.append("        for (int i = 0; i <= ").append(b).append("; i++) {\n");
                sb.append("            L.pushNil();\n");
                sb.append("            L.replace(").append(a + 1).append(" + i);\n");
                sb.append("        }\n");
                break;
            }
            
            case OP_GETUPVAL: {
                sb.append("                    L.pushValue(LuaJava.upvalueIndex(").append(b + 1).append("));\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_SETUPVAL: {
                sb.append("                    L.pushValue(").append(a + 1).append(");\n");
                sb.append("                    L.replace(LuaJava.upvalueIndex(").append(b + 1).append("));\n");
                break;
            }
            
            case OP_GETTABUP: {
                int k = c;
                if (b == 0) {
                    sb.append("                    L.rawGetI(LuaJava.LUA_REGISTRYINDEX, 2);\n");
                } else {
                    sb.append("                    L.pushValue(LuaJava.upvalueIndex(").append(b + 1).append("));\n");
                }
                if (k < proto.constants.length && proto.constantTypes[k] == LUA_TSTRING) {
                    String key = escapeString((String) proto.constants[k]);
                    sb.append("                    L.getField(-1, \"").append(key).append("\");\n");
                    sb.append("                    L.remove(-2);\n");
                    sb.append("                    L.replace(").append(a + 1).append(");\n");
                } else {
                    generateLoadK(sb, proto, k, -1);
                    sb.append("                    L.getTable(-2);\n");
                    sb.append("                    L.remove(-2);\n");
                    sb.append("                    L.replace(").append(a + 1).append(");\n");
                }
                break;
            }
            
            case OP_SETTABUP: {
                int keyIdx = b;
                int valIdx = c;
                boolean isK = kflag != 0;
                
                if (b == 0) {
                    sb.append("                    L.rawGetI(LuaJava.LUA_REGISTRYINDEX, 2);\n");
                } else {
                    sb.append("                    L.pushValue(LuaJava.upvalueIndex(").append(b + 1).append("));\n");
                }
                
                if (keyIdx < proto.constants.length && proto.constantTypes[keyIdx] == LUA_TSTRING) {
                    String key = escapeString((String) proto.constants[keyIdx]);
                    if (isK) {
                        generateLoadK(sb, proto, valIdx, -1);
                    } else {
                        sb.append("                    L.pushValue(").append(valIdx + 1).append(");\n");
                    }
                    sb.append("                    L.setField(-2, \"").append(key).append("\");\n");
                    sb.append("                    L.pop(1);\n");
                } else {
                    generateLoadK(sb, proto, keyIdx, -1);
                    if (isK) {
                        generateLoadK(sb, proto, valIdx, -1);
                    } else {
                        sb.append("                    L.pushValue(").append(valIdx + 1).append(");\n");
                    }
                    sb.append("                    L.setTable(-3);\n");
                    sb.append("                    L.pop(1);\n");
                }
                break;
            }
            
            case OP_GETTABLE: {
                sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                sb.append("                    L.pushValue(").append(c + 1).append(");\n");
                sb.append("                    L.getTable(-2);\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                sb.append("                    L.pop(1);\n");
                break;
            }
            
            case OP_SETTABLE: {
                boolean isK = kflag != 0;
                sb.append("                    L.pushValue(").append(a + 1).append(");\n");
                sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                if (isK) {
                    generateLoadK(sb, proto, c, -1);
                } else {
                    sb.append("                    L.pushValue(").append(c + 1).append(");\n");
                }
                sb.append("                    L.setTable(-3);\n");
                sb.append("                    L.pop(1);\n");
                break;
            }
            
            case OP_GETI: {
                sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                sb.append("                    L.getI(-1, ").append(c).append(");\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                sb.append("                    L.pop(1);\n");
                break;
            }
            
            case OP_SETI: {
                boolean isK = kflag != 0;
                sb.append("                    L.pushValue(").append(a + 1).append(");\n");
                if (isK) {
                    generateLoadK(sb, proto, c, -1);
                } else {
                    sb.append("                    L.pushValue(").append(c + 1).append(");\n");
                }
                sb.append("                    L.setI(-2, ").append(b).append(");\n");
                sb.append("                    L.pop(1);\n");
                break;
            }
            
            case OP_GETFIELD: {
                if (c < proto.constants.length && proto.constantTypes[c] == LUA_TSTRING) {
                    String key = escapeString((String) proto.constants[c]);
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    sb.append("                    L.getField(-1, \"").append(key).append("\");\n");
                    sb.append("                    L.replace(").append(a + 1).append(");\n");
                    sb.append("                    L.pop(1);\n");
                }
                break;
            }
            
            case OP_SETFIELD: {
                boolean isK = kflag != 0;
                if (b < proto.constants.length && proto.constantTypes[b] == LUA_TSTRING) {
                    String key = escapeString((String) proto.constants[b]);
                    sb.append("                    L.pushValue(").append(a + 1).append(");\n");
                    if (isK) {
                        generateLoadK(sb, proto, c, -1);
                    } else {
                        sb.append("                    L.pushValue(").append(c + 1).append(");\n");
                    }
                    sb.append("                    L.setField(-2, \"").append(key).append("\");\n");
                    sb.append("                    L.pop(1);\n");
                }
                break;
            }
            
            case OP_NEWTABLE: {
                int arraySize = c;
                int hashSize = b > 0 ? (1 << (b - 1)) : 0;
                sb.append("                    L.createTable(").append(arraySize).append(", ").append(hashSize).append(");\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_SELF: {
                boolean isK = kflag != 0;
                sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                sb.append("                    L.pushValue(-1);\n");
                sb.append("                    L.replace(").append(a + 2).append(");\n");
                if (isK && c < proto.constants.length && proto.constantTypes[c] == LUA_TSTRING) {
                    String key = escapeString((String) proto.constants[c]);
                    sb.append("                    L.getField(-1, \"").append(key).append("\");\n");
                } else {
                    sb.append("                    L.pushValue(").append(c + 1).append(");\n");
                    sb.append("                    L.getTable(-2);\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                sb.append("                    L.pop(1);\n");
                break;
            }
            
            case OP_ADDI: {
                int localSc = info.sc != 0 ? info.sc : (c - 32767);
                if (usePureJava) {
                    sb.append("                    L.pushInteger(L.toInteger(").append(b + 1).append(") + ").append(localSc).append(");\n");
                } else {
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    sb.append("                    L.pushInteger(").append(localSc).append(");\n");
                    sb.append("                    L.arith(LuaJava.LUA_OPADD);\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_ADDK: case OP_SUBK: case OP_MULK: case OP_MODK:
            case OP_POWK: case OP_DIVK: case OP_IDIVK:
            case OP_BANDK: case OP_BORK: case OP_BXORK: {
                int opEnum = getArithOp(opcode - OP_ADDK + LUA_OPADD);
                if (usePureJava && c < proto.constants.length) {
                    generatePureCArithK(sb, opcode, a, b, proto.constants[c], proto.constantTypes[c]);
                } else {
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    generateLoadK(sb, proto, c, -1);
                    sb.append("                    L.arith(").append(opEnum).append(");\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_SHRI: {
                int localSc = info.sc != 0 ? info.sc : (c - 32767);
                if (usePureJava) {
                    sb.append("                    L.pushInteger(L.toInteger(").append(b + 1).append(") >> ").append(localSc).append(");\n");
                } else {
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    sb.append("                    L.pushInteger(").append(localSc).append(");\n");
                    sb.append("                    L.arith(LuaJava.LUA_OPSHR);\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_SHLI: {
                int localSc = info.sc != 0 ? info.sc : (c - 32767);
                if (usePureJava) {
                    sb.append("                    L.pushInteger(").append(localSc).append(" << L.toInteger(").append(b + 1).append("));\n");
                } else {
                    sb.append("                    L.pushInteger(").append(localSc).append(");\n");
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    sb.append("                    L.arith(LuaJava.LUA_OPSHL);\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_ADD: case OP_SUB: case OP_MUL: case OP_MOD:
            case OP_POW: case OP_DIV: case OP_IDIV:
            case OP_BAND: case OP_BOR: case OP_BXOR:
            case OP_SHL: case OP_SHR: {
                int opEnum = getArithOp(opcode - OP_ADD + LUA_OPADD);
                if (usePureJava) {
                    generatePureCArith(sb, opcode, a, b, c);
                } else {
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    sb.append("                    L.pushValue(").append(c + 1).append(");\n");
                    sb.append("                    L.arith(").append(opEnum).append(");\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_UNM: {
                if (usePureJava) {
                    sb.append("                    L.pushNumber(-L.toNumber(").append(b + 1).append("));\n");
                } else {
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    sb.append("                    L.arith(LuaJava.LUA_OPUNM);\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_BNOT: {
                if (usePureJava) {
                    sb.append("                    L.pushInteger(~L.toInteger(").append(b + 1).append("));\n");
                } else {
                    sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                    sb.append("                    L.arith(LuaJava.LUA_OPBNOT);\n");
                }
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_NOT: {
                sb.append("                    L.pushBoolean(!L.toBoolean(").append(b + 1).append("));\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_LEN: {
                sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                sb.append("                    L.len(-1);\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                sb.append("                    L.pop(1);\n");
                break;
            }
            
            case OP_CONCAT: {
                sb.append("                    for (int k = 0; k < ").append(b).append("; k++) {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(" + k);\n");
                sb.append("                    }\n");
                sb.append("                    L.concat(").append(b).append(");\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_CLOSE: {
                sb.append("                    L.closeSlot(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_TBC: {
                sb.append("                    L.toClose(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_JMP: {
                int localSj = info.sj != 0 ? (int)info.sj : sbx;
                int target = pc + 1 + localSj;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                sb.append("                    // JMP: pc=").append(pc).append(" sj=").append(localSj).append(" targetPc=").append(target).append(" targetCase=").append(targetCase).append("\n");
                sb.append("                    _pc = ").append(targetCase).append("; break;\n");
                break;
            }
            
            case OP_EQ: {
                int k = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" k=").append(k).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        L.pushValue(").append(b + 1).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPEQ);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(k).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_LT: {
                int k = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" k=").append(k).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        L.pushValue(").append(b + 1).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPLT);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(k).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_LE: {
                int k = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" k=").append(k).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        L.pushValue(").append(b + 1).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPLE);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(k).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_EQK: {
                int k = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" k=").append(k).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                generateLoadK(sb, proto, b, -1);
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPEQ);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(k).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_EQI: {
                int immB = info.sb != 0 ? info.sb : (b - 32767);
                int kFlag = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" kFlag=").append(kFlag).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        L.pushInteger(").append(immB).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPEQ);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(kFlag).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_LTI: {
                int immB = info.sb != 0 ? info.sb : (b - 32767);
                int kFlag = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" kFlag=").append(kFlag).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        L.pushInteger(").append(immB).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPLT);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(kFlag).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_LEI: {
                int immB = info.sb != 0 ? info.sb : (b - 32767);
                int kFlag = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" kFlag=").append(kFlag).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        L.pushInteger(").append(immB).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPLE);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(kFlag).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_GTI: {
                int immB = info.sb != 0 ? info.sb : (b - 32767);
                int kFlag = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" kFlag=").append(kFlag).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushInteger(").append(immB).append(");\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPLT);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(kFlag).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_GEI: {
                int immB = info.sb != 0 ? info.sb : (b - 32767);
                int kFlag = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" kFlag=").append(kFlag).append("\n");
                sb.append("                    {\n");
                sb.append("                        L.pushInteger(").append(immB).append(");\n");
                sb.append("                        L.pushValue(").append(a + 1).append(");\n");
                sb.append("                        int res = L.compare(-2, -1, LuaJava.LUA_OPLE);\n");
                sb.append("                        L.pop(2);\n");
                sb.append("                        if (res != ").append(kFlag).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                        _pc = ").append(nextCase).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_TEST: {
                int k = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" k=").append(k).append("\n");
                sb.append("                    if (L.toBoolean(").append(a + 1).append(") == ").append(k).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                    _pc = ").append(nextCase).append("; break;\n");
                break;
            }
            
            case OP_TESTSET: {
                int k = kflag;
                int target = pc + 2;
                int targetCase = findCaseNum(target, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // targetPc=").append(target).append(" targetCase=").append(targetCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append(" k=").append(k).append("\n");
                sb.append("                    if (L.toBoolean(").append(b + 1).append(") == ").append(k).append(") { _pc = ").append(targetCase).append("; break; }\n");
                sb.append("                    L.pushValue(").append(b + 1).append(");\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                sb.append("                    _pc = ").append(nextCase).append("; break;\n");
                break;
            }
            
            case OP_CALL: {
                int nArgs = b > 0 ? b - 1 : -1;
                int nResults = c > 0 ? c - 1 : -1;
                
                sb.append("                    {\n");
                if (b > 0) {
                    sb.append("            int startTop = L.getTop();\n");
                    sb.append("            L.tccPushArgs(").append(a + 1).append(", ").append(nArgs + 1).append(");\n");
                    sb.append("            L.call(").append(nArgs).append(", ").append(nResults).append(");\n");
                    if (c > 0) {
                        sb.append("            L.tccStoreResults(").append(a + 1).append(", ").append(nResults).append(");\n");
                    } else {
                        sb.append("            int nres = L.getTop() - startTop + 1;\n");
                        sb.append("            for (int k = 0; k < nres; k++) {\n");
                        sb.append("                L.pushValue(startTop + k);\n");
                        sb.append("                L.replace(").append(a + 1).append(" + k);\n");
                        sb.append("            }\n");
                        sb.append("            L.setTop(").append(a).append(" + nres);\n");
                    }
                } else {
                    sb.append("            L.call(L.getTop() - ").append(a + 1).append(", ").append(nResults).append(");\n");
                }
                sb.append("        }\n");
                break;
            }
            
            case OP_TAILCALL: {
                int nArgs = b > 0 ? b - 1 : -1;
                sb.append("                    L.tccPushArgs(").append(a + 1).append(", ").append(nArgs + 1).append(");\n");
                sb.append("                    L.call(").append(nArgs).append(", LuaJava.LUA_MULTRET);\n");
                sb.append("        return L.toJavaObject(-1);\n");
                break;
            }
            
            case OP_RETURN: {
                int nRet = b > 0 ? b - 1 : -1;
                if (nRet > 0) {
                    sb.append("                    L.tccPushArgs(").append(a + 1).append(", ").append(nRet).append(");\n");
                    sb.append("                    return ").append(nRet).append(";\n");
                } else {
                    sb.append("                    return 0;\n");
                }
                break;
            }
            
            case OP_RETURN0: {
                sb.append("                    return 0;\n");
                break;
            }
            
            case OP_RETURN1: {
                sb.append("                    L.pushValue(").append(a + 1).append(");\n");
                sb.append("                    return 1;\n");
                break;
            }
            
            case OP_FORPREP: {
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                int skipPc = pc + 1 + (int)bx + 1;
                int skipCase = findCaseNum(skipPc, pcToCase, proto.code.length);
                sb.append("                    // FORPREP: pc=").append(pc).append(" bx=").append(bx).append(" skipPc=").append(skipPc).append(" skipCase=").append(skipCase).append(" nextPc=").append(pc + 1).append(" nextCase=").append(nextCase).append("\n");
                sb.append("                    if (L.isInteger(").append(a + 1).append(") && L.isInteger(").append(a + 3).append(")) {\n");
                sb.append("                        long step = L.toInteger(").append(a + 3).append(");\n");
                sb.append("                        long init = L.toInteger(").append(a + 1).append(");\n");
                sb.append("                        L.pushInteger(init);\n");
                sb.append("                        L.replace(").append(a + 1).append(");\n");
                sb.append("                        L.pushInteger(init);\n");
                sb.append("                        L.replace(").append(a + 4).append(");\n");
                sb.append("                    } else {\n");
                sb.append("                        double step = L.toNumber(").append(a + 3).append(");\n");
                sb.append("                        double init = L.toNumber(").append(a + 1).append(");\n");
                sb.append("                        L.pushNumber(init);\n");
                sb.append("                        L.replace(").append(a + 1).append(");\n");
                sb.append("                        L.pushNumber(init);\n");
                sb.append("                        L.replace(").append(a + 4).append(");\n");
                sb.append("                    }\n");
                sb.append("                    _pc = ").append(nextCase).append("; break;\n");
                break;
            }

            case OP_FORLOOP: {
                int targetPc = pc + 2 - (int)bx - 1;
                int loopTarget = findCaseNum(targetPc, pcToCase, proto.code.length);
                int nextCase = findCaseNum(pc + 1, pcToCase, proto.code.length);
                sb.append("                    // FORLOOP: pc=").append(pc).append(", bx=").append(bx).append(", targetPc=").append(targetPc).append(", loopTarget=").append(loopTarget).append("\n");
                sb.append("                    // FORLOOP: 检查循环条件，更新索引并可能跳回循环开始\n");
                sb.append("                    if (L.isInteger(").append(a + 3).append(")) {\n");
                sb.append("                        long step = L.toInteger(").append(a + 3).append(");\n");
                sb.append("                        long limit = L.toInteger(").append(a + 2).append(");\n");
                sb.append("                        long idx = L.toInteger(").append(a + 1).append(") + step;\n");
                sb.append("                        L.pushInteger(idx);\n");
                sb.append("                        L.replace(").append(a + 1).append(");\n");
                sb.append("                        if ((step > 0) ? (idx <= limit) : (idx >= limit)) {\n");
                sb.append("                            // 更新R(A+4)为当前索引值\n");
                sb.append("                            L.pushInteger(idx);\n");
                sb.append("                            L.replace(").append(a + 4).append(");\n");
                sb.append("                            _pc = ").append(loopTarget).append("; break;\n");
                sb.append("                        }\n");
                sb.append("                    } else {\n");
                sb.append("                        double step = L.toNumber(").append(a + 3).append(");\n");
                sb.append("                        double limit = L.toNumber(").append(a + 2).append(");\n");
                sb.append("                        double idx = L.toNumber(").append(a + 1).append(") + step;\n");
                sb.append("                        L.pushNumber(idx);\n");
                sb.append("                        L.replace(").append(a + 1).append(");\n");
                sb.append("                        if ((step > 0) ? (idx <= limit) : (idx >= limit)) {\n");
                sb.append("                            // 更新R(A+4)为当前索引值\n");
                sb.append("                            L.pushNumber(idx);\n");
                sb.append("                            L.replace(").append(a + 4).append(");\n");
                sb.append("                            _pc = ").append(loopTarget).append("; break;\n");
                sb.append("                        }\n");
                sb.append("                    }\n");
                sb.append("                    _pc = ").append(nextCase).append("; break;\n");
                break;
            }
            
            case OP_TFORPREP: {
                int targetPc = pc + 1 + (int)bx + 1;
                int targetCase = findCaseNum(targetPc, pcToCase, proto.code.length);
                sb.append("                    // TFORPREP: pc=").append(pc).append(" bx=").append(bx).append(" targetPc=").append(targetPc).append(" targetCase=").append(targetCase).append("\n");
                sb.append("                    L.toClose(").append(a + 4).append(");\n");
                sb.append("                    _pc = ").append(targetCase).append("; break;\n");
                break;
            }
            
            case OP_TFORCALL: {
                sb.append("                    // TFORCALL: a=").append(a).append(" c=").append(c).append("\n");
                sb.append("                    // Lua 5.5: R[A+4], ... ,R[A+3+C] := R[A](R[A+1], R[A+2])\n");
                sb.append("                    L.pushValue(").append(a + 1).append(");\n");
                sb.append("                    L.pushValue(").append(a + 2).append(");\n");
                sb.append("                    L.pushValue(").append(a + 3).append(");\n");
                sb.append("                    L.call(2, ").append(c).append(");\n");
                sb.append("                    // 存储返回值到 R[A+4] 到 R[A+3+C]\n");
                sb.append("                    for (int j = 1; j <= ").append(c).append("; j++) {\n");
                sb.append("                        L.pushValue(-").append(c).append(" + j - 1);\n");
                sb.append("                        L.replace(").append(a + 3).append(" + j);\n");
                sb.append("                    }\n");
                sb.append("                    L.pop(").append(c).append(");\n");
                break;
            }
            
            case OP_TFORLOOP: {
                int loopTargetPc = pc + 2 - (int)bx - 1;
                int loopTarget = findCaseNum(loopTargetPc, pcToCase, proto.code.length);
                int exitTargetPc = pc + 1 + (int)bx + 1;
                int exitTarget = findCaseNum(exitTargetPc, pcToCase, proto.code.length);
                sb.append("                    // TFORLOOP: pc=").append(pc).append(" bx=").append(bx).append(" loopTargetPc=").append(loopTargetPc).append(" loopTarget=").append(loopTarget).append(" exitTargetPc=").append(exitTargetPc).append(" exitTarget=").append(exitTarget).append("\n");
                // Lua 5.5: if R[A+4] ~= nil then { R[A+2]=R[A+4]; pc -= Bx }
                sb.append("                    if (!L.isNil(").append(a + 4).append(")) {\n");
                sb.append("                        L.pushValue(").append(a + 4).append(");\n");
                sb.append("                        L.replace(").append(a + 2).append(");\n");
                sb.append("                        _pc = ").append(loopTarget).append("; break;\n");
                sb.append("                    } else {\n");
                sb.append("                        _pc = ").append(exitTarget).append("; break;\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_SETLIST: {
                int n = b;
                int offset = c;
                // SETLIST使用vB和vC编码
                if (n == 0 && vb > 0) {
                    n = vb;
                }
                // c=0时可能需要用vc来计算实际偏移
                if (offset == 0 && vc > 0) {
                    // vc表示需要累积的元素数量
                    offset = vc;
                }
                // c是直接偏移，不需要用vc
                sb.append("                    {\n");
                sb.append("                        int n = ").append(n).append(";\n");
                sb.append("                        if (n == 0) n = L.getTop() - ").append(a + 1).append(";\n");
                sb.append("                        // Lua 5.5: R[A][vC+i] := R[A+i], 从后往前设置\n");
                int baseOffset = (offset > 0 ? offset : 1);
                sb.append("                        int startIdx = ").append(baseOffset).append(" + n - 1;\n");
                sb.append("                        for (int j = n; j >= 1; j--) {\n");
                sb.append("                            L.pushValue(").append(a + 1).append(" + j);\n");
                sb.append("                            L.setI(").append(a + 1).append(", startIdx);\n");
                sb.append("                            startIdx--;\n");
                sb.append("                        }\n");
                // SETLIST会把R[A+1]到R[A+n]的值移到表中，所以需要清理这些栈位置
                sb.append("                        L.setTop(").append(a + 1).append(");\n");
                sb.append("                    }\n");
                break;
            }
            
            case OP_CLOSURE: {
                int closureBx = (int)bx;
                sb.append("                    // CLOSURE: pc=").append(pc).append(" bx=").append(bx).append(" closureBx=").append(closureBx).append("\n");
                if (closureBx < proto.subProtos.length) {
                    ProtoInfo child = proto.subProtos[closureBx];
                    sb.append("                    // 创建闭包: ").append(child.name).append(" (sizeUpvalues=").append(child.sizeUpvalues).append(")\n");
                    for (int j = 0; j < child.sizeUpvalues; j++) {
                        UpvalDesc uv = child.upvalues[j];
                        if (uv.instack) {
                            sb.append("                    L.pushValue(").append(uv.idx + 1).append("); // upval ").append(j).append(" (local)\n");
                        } else {
                            sb.append("                    L.pushValue(LuaJava.upvalueIndex(").append(uv.idx + 1).append(")); // upval ").append(j).append(" (upval)\n");
                        }
                    }
                    sb.append("                    L.pushJavaFunction(this, \"").append(child.name).append("\", ").append(child.sizeUpvalues).append(");\n");
                    sb.append("                    L.replace(").append(a + 1).append(");\n");
                }
                break;
            }
            
            case OP_VARARG: {
                int nNeeded = c - 1;
                if (nNeeded >= 0) {
                    sb.append("                    for (int i = 0; i < ").append(nNeeded).append("; i++) {\n");
                    sb.append("                        if (vtabIdx + i <= L.getTop()) {\n");
                    sb.append("                            L.pushValue(vtabIdx + i);\n");
                    sb.append("                        } else {\n");
                    sb.append("                            L.pushNil();\n");
                    sb.append("                        }\n");
                    sb.append("                        L.replace(").append(a + 1).append(" + i);\n");
                    sb.append("                    }\n");
                } else {
                    sb.append("                    int nvar = L.getTop() - vtabIdx + 1;\n");
                    sb.append("                    for (int i = 0; i < nvar; i++) {\n");
                    sb.append("                        L.pushValue(vtabIdx + i);\n");
                    sb.append("                        L.replace(").append(a + 1).append(" + i);\n");
                    sb.append("                    }\n");
                }
                break;
            }
            
            case OP_IN: {
                sb.append("                    L.pushInteger(L.tccIn(").append(b + 1).append(", ").append(c + 1).append("));\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_VARARGPREP: {
                sb.append("        /* VARARGPREP: 调整可变参数 */\n");
                break;
            }
            
            case OP_GETVARG: {
                sb.append("                    L.rawGetI(vtabIdx, L.toInteger(").append(c + 1).append("));\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_MMBIN:
            case OP_MMBINI:
            case OP_MMBINK: {
                sb.append("        /* MMBIN: 由算术操作自动处理 */\n");
                break;
            }
            
            case OP_SPACESHIP: {
                sb.append("                    L.pushInteger(L.spaceship(").append(b + 1).append(", ").append(c + 1).append("));\n");
                sb.append("                    L.replace(").append(a + 1).append(");\n");
                break;
            }
            
            case OP_NOP: {
                sb.append("        /* NOP: 无操作 */\n");
                break;
            }
            
            case OP_NEWCONCEPT:
            case OP_NEWNAMESPACE:
            case OP_LINKNAMESPACE:
            case OP_NEWSUPER:
            case OP_SETSUPER:
            case OP_GETCMDS:
            case OP_GETOPS:
            case OP_ASYNCWRAP:
            case OP_GENERICWRAP:
            case OP_CHECKTYPE:
            case OP_CASE: {
                sb.append("        /* ").append(info.opname).append(": 扩展操作码 */\n");
                break;
            }
            
            default: {
                sb.append("        // 未实现的操作码: ").append(info.opname).append("\n");
                break;
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成加载常量代码
     */
    private void generateLoadK(StringBuilder sb, ProtoInfo proto, int kIdx, int dest) {
        if (kIdx >= proto.constants.length) {
            sb.append("                    L.pushNil();\n");
            if (dest >= 0) {
                sb.append("                    L.replace(").append(dest + 1).append(");\n");
            }
            return;
        }
        
        Object val = proto.constants[kIdx];
        int type = proto.constantTypes[kIdx];
        
        switch (type) {
            case LUA_TNIL:
                sb.append("                    L.pushNil();\n");
                break;
            case LUA_TBOOLEAN:
                sb.append("                    L.pushBoolean(").append(val).append(");\n");
                break;
            case LUA_TNUMBER:
                if (val instanceof Integer || val instanceof Long) {
                    sb.append("                    L.pushInteger(").append(val).append("L);\n");
                } else {
                    sb.append("                    L.pushNumber(").append(val).append(");\n");
                }
                break;
            case LUA_TSTRING:
                String str = escapeString((String) val);
                sb.append("                    L.pushString(\"").append(str).append("\");\n");
                break;
            default:
                sb.append("                    L.pushNil();\n");
                break;
        }
        
        if (dest >= 0) {
            sb.append("                    L.replace(").append(dest + 1).append(");\n");
        }
    }

    /**
     * 获取算术操作码
     */
    private int getArithOp(int op) {
        return op;
    }

    /**
     * 生成纯Java算术运算
     */
    private void generatePureCArith(StringBuilder sb, int opcode, int a, int b, int c) {
        String op = "";
        boolean isInt = false;
        
        switch (opcode) {
            case OP_ADD: op = "+"; break;
            case OP_SUB: op = "-"; break;
            case OP_MUL: op = "*"; break;
            case OP_DIV: op = "/"; break;
            case OP_IDIV: op = "/"; isInt = true; break;
            case OP_MOD: op = "%"; isInt = true; break;
            case OP_POW:
                sb.append("                    L.pushNumber(Math.pow(L.toNumber(").append(b + 1).append("), L.toNumber(").append(c + 1).append(")));\n");
                return;
            case OP_BAND: op = "&"; isInt = true; break;
            case OP_BOR: op = "|"; isInt = true; break;
            case OP_BXOR: op = "^"; isInt = true; break;
            case OP_SHL: op = "<<"; isInt = true; break;
            case OP_SHR: op = ">>"; isInt = true; break;
        }
        
        if (isInt) {
            sb.append("                    L.pushInteger(L.toInteger(").append(b + 1).append(") ").append(op).append(" L.toInteger(").append(c + 1).append("));\n");
        } else {
            sb.append("                    L.pushNumber(L.toNumber(").append(b + 1).append(") ").append(op).append(" L.toNumber(").append(c + 1).append("));\n");
        }
    }

    /**
     * 生成纯Java算术运算(常量版本)
     */
    private void generatePureCArithK(StringBuilder sb, int opcode, int a, int b, Object kVal, int kType) {
        String kStr = "";
        if (kType == LUA_TNUMBER) {
            if (kVal instanceof Integer || kVal instanceof Long) {
                kStr = kVal.toString() + "L";
            } else {
                kStr = kVal.toString();
            }
        } else {
            kStr = "0";
        }
        
        String op = "";
        boolean isInt = false;
        
        switch (opcode) {
            case OP_ADDK: op = "+"; break;
            case OP_SUBK: op = "-"; break;
            case OP_MULK: op = "*"; break;
            case OP_DIVK: op = "/"; break;
            case OP_IDIVK: op = "/"; isInt = true; break;
            case OP_MODK: op = "%"; isInt = true; break;
            case OP_POWK:
                sb.append("                    L.pushNumber(Math.pow(L.toNumber(").append(b + 1).append("), ").append(kStr).append("));\n");
                return;
            case OP_BANDK: op = "&"; isInt = true; break;
            case OP_BORK: op = "|"; isInt = true; break;
            case OP_BXORK: op = "^"; isInt = true; break;
        }
        
        if (isInt) {
            sb.append("                    L.pushInteger(L.toInteger(").append(b + 1).append(") ").append(op).append(" ").append(kStr).append(");\n");
        } else {
            sb.append("                    L.pushNumber(L.toNumber(").append(b + 1).append(") ").append(op).append(" ").append(kStr).append(");\n");
        }
    }

    /**
     * 转义字符串
     */
    private String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * 获取随机名称
     */
    private String getRandomName() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 查找Proto的ID
     */
    private int findProtoId(ArrayList<ProtoInfo> protos, ProtoInfo target) {
        for (int i = 0; i < protos.size(); i++) {
            if (protos.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 生成upvalue索引辅助方法
     */
    private String generateUpvalueIndex(int n) {
        return "LuaJava.upvalueIndex(" + n + ")";
    }
}
