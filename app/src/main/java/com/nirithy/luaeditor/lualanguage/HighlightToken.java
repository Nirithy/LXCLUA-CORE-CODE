package com.nirithy.luaeditor.lualanguage;

public class HighlightToken {
    public Tokens token;
    public int offset;
    public String url;
    public String text; // 仅在使用时赋值

    public HighlightToken(Tokens token, int offset) {
        this.token = token;
        this.offset = offset;
    }

    public HighlightToken(Tokens token, int offset, String url) {
        this.token = token;
        this.offset = offset;
        this.url = url;
    }
}
