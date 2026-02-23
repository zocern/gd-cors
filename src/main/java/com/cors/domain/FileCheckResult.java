package com.cors.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class FileCheckResult {

    /**
     * 是否已完成上传（秒传标志）
     * true: 文件已存在，无需上传
     * false: 文件不存在或不完整，需要上传剩余分片
     */
    private boolean finished;

    /**
     * 已上传的分片索引列表
     * 仅当 finished = false 时有效
     * 例如: [0, 1, 2, 5] 表示第 0,1,2,5 片已经传过了，前端只需传 3,4 和 6以后
     */
    private List<Integer> uploadedChunks;
}