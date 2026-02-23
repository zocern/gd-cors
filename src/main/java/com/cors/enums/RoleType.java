package com.cors.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum RoleType {
    USER("普通用户"),
    ADMIN("管理员");

    @EnumValue   // 存数据库
    @JsonValue   // 序列化前端
    private final String name;
    private final String description;

    RoleType(String description) {
        this.name = this.name();
        this.description = description;
    }

    public static RoleType fromName(String name) {
        for (RoleType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知 UserRoleEnum 类型: " + name);
    }
}
