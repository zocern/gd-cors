package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cors.domain.entity.FileVectorStatus;
import com.cors.domain.vo.FileVectorStatusVo;
import com.cors.enums.FileVectorStatusType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * FileVectorStatus Mapper
 * 用于操作 vector_file_status 表
 */
@Mapper
public interface FileVectorStatusMapper extends BaseMapper<FileVectorStatus> {

    IPage<FileVectorStatusVo> selectStatusWithFile(Page<?> page,
                                                   @Param("status") FileVectorStatusType status,
                                                   @Param("keyword") String keyword,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end
    );
}
