package com.cors.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;


@Getter
public enum MessageType {
    TEXT("文本"),
    IMAGE("图片"),
    AUDIO("音频"),
    FILE("文件"),
    JSON("JSON");

    @EnumValue   // 存数据库
    @JsonValue   // 序列化前端
    private final String name; // 枚举名
    private final String description; // 中文描述，仅作展示

    MessageType(String description) {
        this.name = this.name();  // 枚举名 TEXT/IMAGE/...
        this.description = description;
    }

    public static MessageType fromName(String name) {
        for (MessageType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知发送者类型: " + name);
    }
}
