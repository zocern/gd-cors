-- ============================================
-- 创建数据库（如果不存在）
-- ============================================
CREATE DATABASE IF NOT EXISTS `gd_cors`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `gd_cors`;

-- ============================================
-- 1. 用户表 (users)
-- ============================================
CREATE TABLE IF NOT EXISTS `users`
(
    `id`       BIGINT UNSIGNED AUTO_INCREMENT COMMENT '用户ID，主键'
    PRIMARY KEY,
    `name`     VARCHAR(50)                            NULL COMMENT '用户名',
    `email`    VARCHAR(100)                           NOT NULL COMMENT '邮箱（最大长度100字符）',
    `password` VARCHAR(60)                            NOT NULL COMMENT '密码（BCrypt加密后固定60字符）',
    `role`     VARCHAR(10) DEFAULT 'USER'             NULL COMMENT '用户角色：普通用户，管理员',
    `created`  DATETIME    DEFAULT CURRENT_TIMESTAMP  NULL COMMENT '创建时间',
    `updated`  DATETIME    DEFAULT CURRENT_TIMESTAMP  NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `uk_user_email` UNIQUE (`email`)
    )
    COMMENT '用户表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

-- 初始化管理员
-- admin@example.com
-- 123456
INSERT INTO `users` (`name`, `email`, `password`, `role`)
SELECT 'admin', 'admin@example.com', '$2a$10$ijByxRcdHqyoM3dHeTVGHOuzAic73CrBZaW6YJs5gdcPNGbM3peU2', 'ADMIN'
    WHERE NOT EXISTS (
    SELECT 1 FROM `users` WHERE `email` = 'admin@example.com'
);


-- ============================================
-- 2. 会话表 (sessions)
-- ============================================
CREATE TABLE IF NOT EXISTS `sessions`
(
    `id`      BIGINT AUTO_INCREMENT COMMENT '会话ID，主键'
    PRIMARY KEY,
    `user_id` BIGINT                             NOT NULL COMMENT '用户ID',
    `title`   VARCHAR(255)                       NOT NULL COMMENT '会话标题',
    `created` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    `updated` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
    )
    COMMENT '聊天会话表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_session_created` ON `sessions` (`created`);
CREATE INDEX `idx_session_user_id` ON `sessions` (`user_id`);

-- ============================================
-- 3. 消息表 (messages)
-- ============================================
CREATE TABLE IF NOT EXISTS `messages`
(
    `id`             BIGINT AUTO_INCREMENT COMMENT '消息ID，主键'
    PRIMARY KEY,
    `session_id`     BIGINT                      NOT NULL COMMENT '所属会话ID',
    `sender_type`    VARCHAR(10)                 NOT NULL COMMENT '发送者类型：0=用户，1=机器人，2=系统',
    `message_type`   VARCHAR(10)                 NOT NULL COMMENT '消息类型：文本，图片，音频，文件，JSON',
    `assistant_type` VARCHAR(10) DEFAULT 'LOCAL' NOT NULL COMMENT '助手类型：LOCAL：本地，ONLINE：在线',
    `content`        TEXT                        NULL COMMENT '消息内容',
    `created`        DATETIME DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间'
    )
    COMMENT '聊天消息表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_message_created` ON `messages` (`created`);
CREATE INDEX `idx_message_session_created` ON `messages` (`session_id`, `created`);
CREATE INDEX `idx_message_session_id` ON `messages` (`session_id`);

-- ============================================
-- 4. 文件信息表 (file_metadata)
-- ============================================
CREATE TABLE IF NOT EXISTS `file_metadata`
(
    `id`              BIGINT UNSIGNED AUTO_INCREMENT COMMENT '文件ID，主键' PRIMARY KEY,
    `name`            VARCHAR(255)                    NOT NULL COMMENT '文件名（含扩展名）',
    `parent_id`       BIGINT UNSIGNED                 NULL COMMENT '父目录ID（NULL 表示根目录）',
    `parent_name`     VARCHAR(255)                    NULL COMMENT '父文件名',
    `folder`          TINYINT(1)      DEFAULT 0       NOT NULL COMMENT '是否为文件夹：0=文件，1=文件夹',
    `size`            BIGINT UNSIGNED DEFAULT 0       NULL COMMENT '文件大小（字节）',
    `storage_key`     VARCHAR(255)                    NULL COMMENT '文件在服务器的存储位置（UUID格式36字符+扩展名）',
    `md5`             CHAR(32)                        NULL COMMENT '文件 MD5',
    `association`     TINYINT(1)      DEFAULT 0       NOT NULL COMMENT '关联信息是否已存在：0=未存在，1=已存在',
    `current_version` INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '当前激活的版本号（从1开始），文件夹为 NULL',
    `version_count`   INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '总版本数（冗余字段，避免 COUNT 查询），文件夹为 NULL',
    `created_by`      BIGINT                          NULL COMMENT '创建者ID',
    `updated_by`      BIGINT                          NULL COMMENT '更新者ID',
    `created`         DATETIME        DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '上传时间',
    `updated`         DATETIME        DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT `uk_files_storage_key` UNIQUE (`storage_key`)
    )
    COMMENT '文件信息表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

CREATE INDEX `idx_files_created` ON `file_metadata` (`created`);
CREATE INDEX `idx_parent_id_id` ON `file_metadata` (`parent_id`, `id`);

-- ============================================
-- 5. 项目管理信息表 (file_association)
-- ============================================
CREATE TABLE IF NOT EXISTS `file_association`
(
    `id`                     BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    `file_metadata_id`       BIGINT                              NOT NULL COMMENT '关联的文件ID',
    `project_name`           VARCHAR(200)                        NOT NULL COMMENT '项目名称',
    `project_start_date`     DATE                                NULL COMMENT '项目创建日期',
    `project_duration`       INT                                 NULL COMMENT '项目工期（天）',
    `project_manager`        VARCHAR(100)                        NULL COMMENT '项目负责人',
    `project_manager_second` VARCHAR(100)                        NULL COMMENT '项目第二负责人',
    `project_location`       VARCHAR(200)                        NULL COMMENT '项目实施位置',
    `project_partner`        VARCHAR(200)                        NULL COMMENT '项目乙方单位',
    `created`            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL COMMENT '记录创建时间',
    `updated`            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    UNIQUE INDEX `uq_file_metadata_id` (`file_metadata_id`)  -- 唯一索引
    )
    COMMENT '项目管理信息表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

-- ============================================
-- 6. 向量化状态表 (file_vector_status)
-- ============================================
CREATE TABLE IF NOT EXISTS `file_vector_status` (
    `storage_key` VARCHAR(255) PRIMARY KEY,
    `status`      ENUM('PENDING','PROCESSING','SUCCESS','FAILED','UNSUPPORTED') NOT NULL,
    `updated`     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created`     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `retry_count` INT DEFAULT 0,
    `error_msg`   TEXT
);

CREATE INDEX `idx_file_vector_status_status_created` ON `file_vector_status` (`status`, `created` DESC);

-- ============================================
-- 7. 文件版本表 (file_versions)
-- ============================================
CREATE TABLE IF NOT EXISTS `file_versions`
(
    `id`          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '版本记录ID',
    `file_id`     BIGINT UNSIGNED NOT NULL COMMENT '关联的 file_metadata.id',
    `version`     INT UNSIGNED    NOT NULL COMMENT '版本号（从1开始递增）',
    `storage_key` VARCHAR(255)    NOT NULL COMMENT '该版本在 MinIO 的存储键',
    `size`        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该版本文件大小（字节）',
    `md5`         CHAR(32)        NULL COMMENT '该版本文件 MD5',
    `remark`      VARCHAR(500)    NULL COMMENT '版本备注（可选）',
    `created_by`  BIGINT          NULL COMMENT '上传者 ID',
    `created`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
    UNIQUE KEY `uk_file_version` (`file_id`, `version`),
    UNIQUE KEY `uk_storage_key`  (`storage_key`),
    INDEX `idx_file_id` (`file_id`)
)
    COMMENT '文件版本表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

-- ============================================
-- 8. 标签表 (tags)
-- ============================================
CREATE TABLE IF NOT EXISTS `tags`
(
    `id`          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '标签ID，主键' PRIMARY KEY,
    `project`     VARCHAR(100)                        NOT NULL COMMENT '所属项目标识（同项目内标签名唯一）',
    `name`        VARCHAR(100)                        NOT NULL COMMENT '标签名称（同项目内唯一）',
    `color`       VARCHAR(20)                         NULL COMMENT '标签颜色（如 #FF5733）',
    `description` VARCHAR(500)                        NULL COMMENT '标签描述',
    `created_by`  BIGINT                              NULL COMMENT '创建者ID',
    `updated_by`  BIGINT                              NULL COMMENT '更新者ID',
    `created`     DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    `updated`     DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `uk_tag_project_name` UNIQUE (`project`, `name`),
    INDEX `idx_tag_project` (`project`)
)
    COMMENT '标签表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

-- ============================================
-- 9. 文件标签关联表 (file_tag_relation)
-- ============================================
CREATE TABLE IF NOT EXISTS `file_tag_relation`
(
    `id`       BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    `file_id`  BIGINT UNSIGNED NOT NULL COMMENT '关联的 file_metadata.id（文件或文件夹）',
    `tag_id`   BIGINT UNSIGNED NOT NULL COMMENT '关联的 tags.id',
    `created`  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '绑定时间',
    UNIQUE KEY `uk_file_tag` (`file_id`, `tag_id`),
    INDEX `idx_file_id`  (`file_id`),
    INDEX `idx_tag_id`   (`tag_id`)
)
    COMMENT '文件标签关联表'
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;
