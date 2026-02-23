package com.cors.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum AssistantType {
    LOCAL( "localAssistant"),
    ONLINE( "onlineAssistant");

    @EnumValue // 数据库存这个字段的值
    private final String code;

    @JsonValue // 前端序列化用小写
    private final String value;

    AssistantType(String value) {
        this.code = this.name();
        this.value = value;
    }
    @JsonCreator
    public static AssistantType fromName(String text) {
        for (AssistantType type : values()) {
            if (type.value.equalsIgnoreCase(text) || type.code.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知 AssistantTypeEnum 类型: " + text);
    }
}