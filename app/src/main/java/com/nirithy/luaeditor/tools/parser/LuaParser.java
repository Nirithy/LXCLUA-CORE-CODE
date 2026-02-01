//
// Decompiled by Jadx - 773ms
//
package com.nirithy.luaeditor.tools.parser;

import com.difierline.lua.LuaUtil;
import com.nirithy.luaeditor.tools.parser.Token;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LuaParser {
    private final List<Token> tokens;
    private int current = 0;
    private HashMap<String, String> map = new HashMap<>();
    private List<String> imports = new ArrayList();
    
    public LuaParser(List<Token> list) {
        this.tokens = list;
    }

    public void parseAssignments() {
        while (!isAtEnd()) {
            try {
                if (match(Token.TokenType.IDENTIFIER)) {
                    parseMultiAssignment();
                } else {
                    advance();
                }
            } catch (Exception e) {
                LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
                return;
            }
        }
    }

    private void parseMultiAssignment() {
        try {
            ArrayList arrayList = new ArrayList();
            arrayList.add(previous());
            while (match(Token.TokenType.PUNCTUATION, ",")) {
                if (!match(Token.TokenType.IDENTIFIER)) {
                    return;
                } else {
                    arrayList.add(previous());
                }
            }
            if (match(Token.TokenType.OPERATOR, "=")) {
                assignValues(arrayList, parseValueList());
                match(Token.TokenType.PUNCTUATION, ";");
            }
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
        }
    }

    private void assignValues(List<Token> list, List<Token> list2) {
        int i;
        int i2 = 0;
        int i3 = 0;
        while (i2 < list.size()) {
            try {
                if (i3 < list2.size()) {
                    String str = list2.get(i3).value;
                    if (Character.isLetter(str.charAt(0))) {
                        this.map.put(list.get(i2).value, str);
                    }
                    i = i3 + 1;
                } else {
                    String str2 = list2.get(list2.size() - 1).value;
                    if (Character.isLetter(str2.charAt(0))) {
                        this.map.put(list.get(i2).value, str2);
                        i = i3;
                    } else {
                        i = i3;
                    }
                }
                i2++;
                i3 = i;
            } catch (Exception e) {
                LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
                return;
            }
        }
    }

    public HashMap<String, String> getMap() {
        return this.map;
    }

    private List<Token> parseValueList() {
        try {
            ArrayList arrayList = new ArrayList();
            arrayList.add(expression());
            while (match(Token.TokenType.PUNCTUATION, ",")) {
                arrayList.add(expression());
            }
            return arrayList;
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return new ArrayList();
        }
    }

    private Token expression() {
        int i;
        try {
            StringBuilder sb = new StringBuilder();
            int i2 = 0;
            while (!isAtEnd()) {
                Token peek = peek();
                if (peek.type == Token.TokenType.PUNCTUATION) {
                    String str = peek.value;
                    if (str.equals("(") || str.equals("[") || str.equals("{")) {
                        i = i2 + 1;
                    } else {
                        if (str.equals(")") || str.equals("]") || str.equals("}")) {
                            if (i2 > 0) {
                                i = i2 - 1;
                            }
                        } else if (i2 == 0) {
                            if (sb.length() > 0) {
                                return new Token(Token.TokenType.IDENTIFIER, sb.toString());
                            }
                            if (isValidExpressionToken(peek)) {
                                advance();
                                return peek;
                            }
                        }
                        i = i2;
                    }
                    advance();
                    i2 = i;
                } else {
                    if (i2 == 0) {
                        if (isValidExpressionToken(peek)) {
                            if (sb.length() > 0) {
                                return new Token(Token.TokenType.IDENTIFIER, sb.toString());
                            }
                            advance();
                            return peek;
                        }
                        sb.append(peek.value);
                        i = i2;
                        advance();
                        i2 = i;
                    }
                    i = i2;
                    advance();
                    i2 = i;
                }
            }
            if (sb.length() > 0) {
                return new Token(Token.TokenType.IDENTIFIER, sb.toString());
            }
            return null;
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return null;
        }
    }

    private boolean isValidExpressionToken(Token token) {
        return token.type == Token.TokenType.IDENTIFIER || token.type == Token.TokenType.NUMBER || token.type == Token.TokenType.STRING;
    }

    private boolean match(Token.TokenType tokenType) {
        try {
            if (!check(tokenType)) {
                return false;
            }
            advance();
            return true;
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return false;
        }
    }

    private boolean match(Token.TokenType tokenType, String str) {
        try {
            if (!check(tokenType, str)) {
                return false;
            }
            advance();
            return true;
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return false;
        }
    }

    private boolean check(Token.TokenType tokenType) {
        try {
            if (isAtEnd()) {
                return false;
            }
            return peek().type == tokenType;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean check(Token.TokenType tokenType, String str) {
        try {
            if (!isAtEnd() && peek().type == tokenType) {
                return peek().value.equals(str);
            }
            return false;
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return false;
        }
    }

    private Token advance() {
        try {
            if (!isAtEnd()) {
                this.current++;
            }
            return previous();
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return null;
        }
    }

    private Token peek() {
        try {
            return this.tokens.get(this.current);
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return null;
        }
    }

    private Token previous() {
        try {
            return this.tokens.get(this.current - 1);
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return null;
        }
    }

    private boolean isAtEnd() {
        try {
            Token peek = peek();
            if (peek != null) {
                if (peek.type == Token.TokenType.EOF) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LuaUtil.save2("/sdcard/XCLUA/sora_error.log", e.getMessage());
            return true;
        }
    }

    public String filterParentheses(String str) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char c : str.toCharArray()) {
            if (c != '(' && c != '[' && c != '{') {
                if (c != ')' && c != ']' && c != '}') {
                    if (i == 0) {
                        sb.append(c);
                    }
                } else if (i > 0) {
                    i--;
                }
            } else {
                i++;
            }
        }
        return sb.toString();
    }

    public List<String> parseImport() {
        this.current = 0;
        while (!isAtEnd()) {
            if (match(Token.TokenType.IDENTIFIER)) {
                String str = previous().value;
                if ("import".equals(str) || "require".equals(str)) {
                    if (match(Token.TokenType.STRING)) {
                        this.imports.add(previous().value);
                    }
                }
            } else {
                advance();
            }
        }
        return this.imports;
    }
}