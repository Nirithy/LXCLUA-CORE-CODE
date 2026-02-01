package com.nirithy.luaeditor;

import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import java.util.Objects;

public class CompletionName {
    private final String description;
    private final String generic;
    private final String name;
    private final CompletionItemKind type;

    public CompletionName(String str, CompletionItemKind completionItemKind, String str2, String str3) {
        this.name = str;
        this.type = completionItemKind;
        this.description = str2 == null ? "" : str2;
        this.generic = str3;
    }

    public CompletionName(String str, CompletionItemKind completionItemKind) {
        this(str, completionItemKind, "", "");
    }

    public String getName() {
        return this.name;
    }

    public CompletionItemKind getType() {
        return this.type;
    }

    public String getDescription() {
        return this.description;
    }

    public String getGeneric() {
        return this.generic;
    }

    public boolean equals(Object obj) {
        boolean z = true;
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CompletionName completionName = (CompletionName) obj;
        if (!Objects.equals(this.name, completionName.name) || this.type != completionName.type || !Objects.equals(this.description, completionName.description) || !Objects.equals(this.generic, completionName.generic)) {
            z = false;
        }
        return z;
    }

    public int hashCode() {
        return Objects.hash(this.name, this.type, this.description, this.generic);
    }

    public String toString() {
        return "CompletionName{name='" + this.name + "', type=" + this.type + ", description='" + this.description + "', generic='" + this.generic + "'}";
    }
}