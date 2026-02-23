package com.cors.util;

import com.cors.domain.StorageUsage;
import io.minio.*;
import io.minio.admin.MinioAdminClient;
import io.minio.admin.messages.DataUsageInfo;
import io.minio.admin.messages.info.Disk;
import io.minio.admin.messages.info.Message;
import io.minio.admin.messages.info.ServerProperties;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 简化版 MinIO 工具类（基于 Spring Bean 注入）
 */
@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class MinIoUtil {

    private final MinioClient minioClient;  // 由 MinioConfig 注入

    private final MinioAdminClient minioAdminClient;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket}")
    private String bucketName;

    /**
     * 获取上传文件前缀路径
     */
    public String getBasisUrl() {
        return minioEndpoint + "/" + bucketName + "/";
    }

    public String generateObjectName(String prefix, String filename) {
        String uuid = java.util.UUID.randomUUID().toString();
        return prefix + "/" + uuid + "_" + filename;
    }


    /** ================== Bucket 相关操作 ================== */

    /**
     * 判断 Bucket 是否存在
     */
    public boolean bucketExists(String bucket) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    }

    /**
     * 获取所有 Bucket
     */
    public List<Bucket> getAllBuckets() throws Exception {
        return minioClient.listBuckets();
    }

    /**
     * 获取指定 Bucket
     */
    public Optional<Bucket> getBucket(String bucket) throws Exception {
        return getAllBuckets().stream().filter(b -> b.name().equals(bucket)).findFirst();
    }

    /**
     * 删除 Bucket
     */
    public void removeBucket(String bucket) throws Exception {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
    }

    /** ================== 文件操作 ================== */

    /**
     * 上传文件（MultipartFile）内存安全
     */
    public ObjectWriteResponse uploadFile(MultipartFile file, String objectName, String contentType) throws Exception {

        try (InputStream inputStream = file.getInputStream()) {
            return minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .contentType(contentType)
                            .stream(inputStream, file.getSize(), -1)
                            .build()
            );
        }
    }


    /**
     * 上传文件（InputStream）
     */
    public ObjectWriteResponse uploadFile(InputStream inputStream, String objectName, long size, String contentType) throws Exception {
        return minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .contentType(contentType)
                        .stream(inputStream, size, -1)
                        .build()
        );
    }

    /**
     * 获取文件流
     */
    public InputStream getObject(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }

    /**
     * 获取预签名下载链接（支持自定义文件名）
     * * @param objectName       MinIO 中的存储对象名 (如 "2023/10/uuid.pdf")
     *
     * @param originalFileName 下载时展示的文件名 (如 "项目需求说明书.pdf")
     * @param expires          过期时间（秒），建议 300 (5分钟)
     * @return 外部可访问的 URL
     */
    public String getPresignedObjectUrl(String objectName, String originalFileName, int expires) throws Exception {
        // 处理文件名编码，防止中文乱码
        String encodedFilename = java.net.URLEncoder.encode(originalFileName, StandardCharsets.UTF_8).replace("+", "%20");

        // 设置响应头：Content-Disposition
        java.util.Map<String, String> reqParams = new java.util.HashMap<>();
        reqParams.put("response-content-disposition", "attachment; filename=\"" + encodedFilename + "\"");

        // 生成 URL
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(expires, java.util.concurrent.TimeUnit.SECONDS)
                        .method(Method.GET)
                        .extraQueryParams(reqParams) // 注入 Header 参数
                        .build());
    }


    /**
     * 删除文件
     */
    public void removeFile(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }

    /**
     * 批量删除文件
     */
    public List<String> removeFiles(List<String> objectNames) throws Exception {
        if (objectNames == null || objectNames.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建删除请求
        List<DeleteObject> objects = objectNames.stream()
                .map(DeleteObject::new)
                .collect(Collectors.toList());

        Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucketName).objects(objects).build()
        );

        // MinIO 的 removeObjects 是懒执行的，必须遍历结果才能触发真正的删除检查
        List<String> failedObjects = new ArrayList<>();
        for (Result<DeleteError> result : results) {
            DeleteError error = result.get();  // 异常直接抛给外层
            failedObjects.add(error.objectName());
        }

        return failedObjects;
    }

    /**
     * 列出指定前缀下所有文件
     */
    public List<Item> listObjects(String prefix, boolean recursive) throws Exception {
        List<Item> list = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).recursive(recursive).build());
        for (Result<Item> result : results) {
            list.add(result.get());
        }
        return list;
    }

    /**
     * 获取预签名文件URL
     */
    public String getPresignedObjectUrl(String objectName, int expires) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(expires)
                        .method(Method.GET)
                        .build());
    }


    /**
     * 获取 MinIO 服务存储使用情况 逻辑已用容量
     */
//    public StorageUsage getStorageUsage() {
//        try {
//            DataUsageInfo dataUsageInfo = minioAdminClient.getDataUsageInfo();
//            long totalObjectsSize = dataUsageInfo.objectsTotalSize();  // 所有对象大小（字节）
//            Map<String, Long> bucketsSizes = dataUsageInfo.bucketsSizes(); // 每个 Bucket 大小
//            ZonedDateTime zonedDateTime = dataUsageInfo.lastUpdate();
//            log.debug("MinIO 总对象大小: {} MB", totalObjectsSize);
//            bucketsSizes.forEach((bucket, size) -> log.debug("Bucket [{}] 使用大小: {} MB", bucket, size));
//            log.debug("MinIO 最后更新时间: {}", zonedDateTime);
//            return new StorageUsage(totalObjectsSize, bucketsSizes, zonedDateTime);
//        } catch (Exception e) {
//            log.error("获取 MinIO 存储使用信息失败", e);
//            return new StorageUsage(0, Collections.emptyMap(), ZonedDateTime.now());
//        }
//    }
    public StorageUsage getStorageUsage() {
        try {
            Message serverInfo = minioAdminClient.getServerInfo();
            long totalDiskBytes = 0;
            long usedDiskBytes = 0;

            // 遍历所有节点累加磁盘信息
            for (ServerProperties node : serverInfo.servers()) {
                for (Disk disk : node.disks()) {
                    totalDiskBytes += disk.totalspace().longValue();
                    // 注意：disk.usedSpace() 是物理占用
                    usedDiskBytes += (disk.totalspace().longValue() - disk.availspace().longValue());
                }
            }

            // 获取桶级别的使用详情 (用于展示每个桶占了多少)
            DataUsageInfo dataUsageInfo = minioAdminClient.getDataUsageInfo();
            Map<String, Long> bucketsSizes = dataUsageInfo.bucketsSizes();

            // 计算百分比 (基于物理磁盘)
            double usedPercentage = totalDiskBytes > 0
                    ? Math.round((usedDiskBytes * 100.0 / totalDiskBytes) * 100.0) / 100.0
                    : 0.0;

            return new StorageUsage(
                    usedDiskBytes,   // 已使用字节
                    totalDiskBytes,  // 总可用字节
                    usedPercentage,
                    bucketsSizes,
                    dataUsageInfo.lastUpdate().withZoneSameInstant(ZoneId.of("Asia/Shanghai"))
            );

        } catch (Exception e) {
            log.warn("获取 MinIO 存储使用信息失败", e);
            return new StorageUsage(0, 0, 0, Collections.emptyMap(), ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
        }
    }
}
