package com.cors.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cors.domain.entity.FileVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件版本 Mapper
 */
@Mapper
public interface FileVersionMapper extends BaseMapper<FileVersion> {

    /**
     * 查询某文件当前最大版本号（用于生成下一个版本号）
     * 若无版本记录则返回 0
     */
    @Select("SELECT COALESCE(MAX(version), 0) FROM file_versions WHERE file_id = #{fileId}")
    Integer selectMaxVersion(@Param("fileId") Long fileId);
}
