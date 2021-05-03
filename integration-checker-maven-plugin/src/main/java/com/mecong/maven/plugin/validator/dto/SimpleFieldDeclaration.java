package com.mecong.maven.plugin.validator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimpleFieldDeclaration  implements Comparable<SimpleFieldDeclaration>{
    private String name;
    private String type;

    @Override
    public String toString() {
        return "name='" + name + "':type='" + type + "'";
    }

    @Override
    public int compareTo(SimpleFieldDeclaration o) {
        return name.compareTo(o.name);
    }
}
