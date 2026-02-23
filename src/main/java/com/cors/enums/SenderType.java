package com.cors.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum SenderType {
    USER("用户"),
    AI("智能助手"),
    SYSTEM("系统");

    @EnumValue  // 存数据库用这个字段
    @JsonValue  // 序列化用这个字段
    private final String name;
    private final String desc;

    SenderType(String desc) {
        this.name = this.name();
        this.desc = desc;
    }

    public static SenderType fromName(String name) {
        for (SenderType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知 SenderTypeEnum 类型: " + name);
    }
}
