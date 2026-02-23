# Logrotate 日志轮转配置

## 📋 说明

此目录包含用于管理 RAG-GDGS 中间件日志文件的 logrotate 配置文件。

## 🎯 功能

- **自动轮转**：每天轮转日志文件
- **自动压缩**：轮转后的日志文件自动压缩
- **自动删除**：保留最近 30 天的日志，超过自动删除
- **权限管理**：自动设置正确的文件权限

## 📁 配置的日志目录

- MySQL 日志：`${RAG_GDGS_STORAGE_PATH}/mysql/logs/*.log`
- Redis 日志：`${RAG_GDGS_STORAGE_PATH}/redis/logs/*.log`
- RabbitMQ 日志：`${RAG_GDGS_STORAGE_PATH}/rabbitmq/logs/*.log`
- 应用日志：`${RAG_GDGS_STORAGE_PATH}/logs/*.log`

## 🚀 安装步骤

### 1. 修改配置路径（如果使用自定义存储路径）

如果使用自定义存储路径（非默认 `/data/rag-gdgs`），需要修改配置文件中的路径：

```bash
# 编辑配置文件
vim docker/logrotate/rag-gdgs-logs

# 将所有 /data/rag-gdgs 替换为你的自定义路径
# 例如：/mnt/data/rag-gdgs
```

### 2. 安装配置文件

```bash
# 复制配置文件到系统目录
sudo cp docker/logrotate/rag-gdgs-logs /etc/logrotate.d/rag-gdgs-logs

# 设置正确的权限
sudo chmod 644 /etc/logrotate.d/rag-gdgs-logs
```

### 3. 测试配置

```bash
# 测试配置文件语法（不会实际执行）
sudo logrotate -d /etc/logrotate.d/rag-gdgs-logs

# 如果配置正确，不会输出错误信息
```

### 4. 手动执行轮转（可选）

```bash
# 强制执行日志轮转（用于测试）
sudo logrotate -f /etc/logrotate.d/rag-gdgs-logs
```

## ⚙️ 配置说明

### 轮转策略

- **daily**：每天轮转一次
- **rotate 30**：保留最近 30 天的日志
- **compress**：轮转后压缩旧日志
- **delaycompress**：延迟压缩（不压缩当天的日志）
- **missingok**：如果日志文件不存在，不报错
- **notifempty**：如果日志文件为空，不轮转
- **create 0644 user group**：创建新日志文件时的权限和所有者

### 自定义配置

如果需要修改保留天数或轮转频率，编辑 `/etc/logrotate.d/rag-gdgs-logs`：

```bash
# 修改保留天数（例如：改为 7 天）
rotate 7

# 修改轮转频率（例如：改为每周轮转）
weekly
```

## 🔍 验证

### 查看 logrotate 状态

```bash
# 查看 logrotate 执行日志
sudo cat /var/log/logrotate.log

# 查看特定配置的执行状态
sudo logrotate -d /etc/logrotate.d/rag-gdgs-logs
```

### 检查日志文件

```bash
# 查看 MySQL 日志目录
ls -lh /data/rag-gdgs/mysql/logs/

# 查看 Redis 日志目录
ls -lh /data/rag-gdgs/redis/logs/

# 查看 RabbitMQ 日志目录
ls -lh /data/rag-gdgs/rabbitmq/logs/
```

## 📝 注意事项

1. **路径一致性**：确保 logrotate 配置中的路径与 `docker-compose.yaml` 中的挂载路径一致
2. **权限问题**：确保 logrotate 有权限访问日志目录
3. **用户和组**：根据容器中运行的用户调整 `create` 指令中的用户和组
4. **存储空间**：定期检查日志目录，确保有足够的存储空间

## 🛠️ 故障排查

### 问题：logrotate 没有执行

```bash
# 检查 logrotate 服务状态
systemctl status logrotate

# 查看 cron 任务（logrotate 通常通过 cron 执行）
cat /etc/cron.daily/logrotate
```

### 问题：权限错误

```bash
# 检查日志目录权限
ls -ld /data/rag-gdgs/*/logs/

# 如果需要，修改权限
sudo chown -R 1000:1000 /data/rag-gdgs/*/logs/
```

### 问题：日志文件没有被轮转

```bash
# 检查日志文件大小
du -sh /data/rag-gdgs/*/logs/

# 手动执行轮转测试
sudo logrotate -v /etc/logrotate.d/rag-gdgs-logs
```

## 📚 相关文档

- [logrotate 官方文档](https://linux.die.net/man/8/logrotate)
- [Docker Compose 日志配置](../docker-compose.yaml)

