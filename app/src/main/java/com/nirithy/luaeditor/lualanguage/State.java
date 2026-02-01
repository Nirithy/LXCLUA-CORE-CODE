package com.nirithy.luaeditor.lualanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

public class State {
    public int state = 0;
    public boolean hasBraces = false;
    public List<String> identifiers = null;
    public Stack<Character> stack = new Stack<>();
    public int longCommentEqualCount = 0;
    public int longStringEqualCount = 0; // 长字符串等号计数
    public boolean inLongString = false; // 是否在长字符串中
    
    public void addIdentifier(CharSequence charSequence) {
        if (this.identifiers == null) {
            this.identifiers = new ArrayList();
        }
        if (charSequence instanceof String) {
            this.identifiers.add((String) charSequence);
        } else {
            this.identifiers.add(charSequence.toString());
        }
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        State state = (State) obj;
        return this.state == state.state
                && this.hasBraces == state.hasBraces
                && this.longCommentEqualCount == state.longCommentEqualCount
                && this.longStringEqualCount == state.longStringEqualCount
                && this.inLongString == state.inLongString;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(this.state),
                Boolean.valueOf(this.hasBraces),
                Integer.valueOf(this.longCommentEqualCount),
                Integer.valueOf(this.longStringEqualCount),
                Boolean.valueOf(this.inLongString));
    }
}