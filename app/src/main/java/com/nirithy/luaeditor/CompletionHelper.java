package com.nirithy.luaeditor;

import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;

public class CompletionHelper {

    /* loaded from: 20250717054144b1cf567b-aa1f-4b84-9932-0429c55b30a4.jar:com/yan/luaeditor/CompletionHelper$PrefixChecker.class */
    public interface PrefixChecker {
        boolean check(char c);
    }

    public static String computePrefix(ContentReference contentReference, CharPosition charPosition, PrefixChecker prefixChecker) {
        int i;
        int i2 = charPosition.column;
        String line = contentReference.getLine(charPosition.line);
        int i3 = 0;
        while (true) {
            int i4 = i3;
            if (i2 <= 0 || (i4 == 0 && !prefixChecker.check(line.charAt(i2 - 1)))) {
                break;
            }
            int i5 = i2 - 1;
            if (line.charAt(i5) != '(') {
                i = i4;
                if (line.charAt(i5) == ')') {
                    i = i4 + 1;
                }
            } else if (i4 <= 0) {
                break;
            } else {
                i = i4 - 1;
            }
            i2--;
            i3 = i;
        }
        return line.substring(i2, charPosition.column);
    }

    public static boolean checkCancelled() {
        Thread currentThread = Thread.currentThread();
        if (currentThread instanceof EditorAutoCompletion.CompletionThread) {
            return ((EditorAutoCompletion.CompletionThread) currentThread).isCancelled();
        }
        return false;
    }
}