package com.nirithy.luaeditor.lualanguage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.rosemoe.sora.lang.QuickQuoteHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.StylesUtils;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.TextRange;

public class LuaQuoteHandler implements QuickQuoteHandler {
    @NonNull
    public QuickQuoteHandler.HandleResult onHandleTyping(@NonNull String candidateCharacter, @NonNull Content text, @NonNull TextRange cursor, @Nullable Styles style) {
        if (!StylesUtils.checkNoCompletion(style, cursor.getStart()) && !StylesUtils.checkNoCompletion(style, cursor.getEnd()) && "\"".equals(candidateCharacter) && cursor.getStart().line == cursor.getEnd().line) {
            text.insert(cursor.getStart().line, cursor.getStart().column, "\"");
            text.insert(cursor.getEnd().line, cursor.getEnd().column + 1, "\"");
            return new QuickQuoteHandler.HandleResult(true, new TextRange(text.getIndexer().getCharPosition(cursor.getStartIndex() + 1), text.getIndexer().getCharPosition(cursor.getEndIndex() + 1)));
        }
        return QuickQuoteHandler.HandleResult.NOT_CONSUMED;
    }
}