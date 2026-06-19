package com.cors.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cors.domain.entity.FileVersion;
import com.cors.domain.vo.FileVersionVo;

import java.util.List;

/**
 * 文件版本管理 Service
 */
public interface FileVersionService extends IService<FileVersion> {

    /**
     * 查询某文件的所有版本列表（按版本号升序）
     *
     * @param fileId 文件ID（file_metadata.id）
     * @return 版本列表，含当前激活标记和向量化状态
     */
    List<FileVersionVo> listVersions(Long fileId);

    /**
     * 切换到指定版本（支持回滚到任意历史版本）
     * <p>
     * 执行流程：
     * 1. 更新 file_metadata.storage_key / current_version / size 指向目标版本
     * 2. 事务提交后异步触发向量库切换：
     *    - 删除当前激活版本在 Milvus 中的向量数据（保留 file_vector_status 记录）
     *    - 重置目标版本状态为 PENDING，重新触发向量化
     *    （Milvus 始终只保留当前激活版本的向量，历史版本向量在文件更新时已删除）
     * </p>
     *
     * @param fileId        文件ID
     * @param targetVersion 目标版本号
     */
    void switchVersion(Long fileId, Integer targetVersion);
}
