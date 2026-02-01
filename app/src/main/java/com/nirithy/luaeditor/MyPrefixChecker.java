package com.nirithy.luaeditor;

import com.nirithy.luaeditor.CompletionHelper;

public class MyPrefixChecker implements CompletionHelper.PrefixChecker {
    @Override // com.nirithy.luaeditor.CompletionHelper.PrefixChecker
    public boolean check(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '(' || c == ')' || c == '$';
    }
}