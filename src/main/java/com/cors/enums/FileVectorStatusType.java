package com.cors.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum FileVectorStatusType {
    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    UNSUPPORTED("UNSUPPORTED");

    @EnumValue // MyBatis-Plus 保存数据库的值
    @JsonValue  // 返回给前端时使用这个值
    private final String value;

    FileVectorStatusType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static FileVectorStatusType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (FileVectorStatusType type : FileVectorStatusType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("非法的 FileVectorStatusType: " + value);
    }
}
