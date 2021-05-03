package com.mecong.maven.plugin.validator.dto;

import lombok.Value;

@Value
public class BoundDtoInfo {
    String type;
    String boundId;
    String dtoFields;
}
