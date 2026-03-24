#!/bin/bash

set -e

# 读取 .env
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

BASE_PATH=${STORAGE_PATH_1:-/home/gdcors/docker/data-disk/gd-cors}

echo "Using base path: $BASE_PATH"

# ================================
# 1. 创建目录
# ================================
sudo mkdir -p $BASE_PATH/{mysql,redis,rabbitmq,minio/data1,minio/config,tei/models,milvus/etcd,milvus/data,app/logs}

# ================================
# 2. MySQL（UID 999）
# ================================
sudo chown -R 999:999 $BASE_PATH/mysql
sudo chmod -R 750 $BASE_PATH/mysql

# ================================
# 3. Redis（UID 999）
# ================================
sudo mkdir -p $BASE_PATH/redis/appendonlydir
sudo chown -R 999:999 $BASE_PATH/redis
sudo chmod -R 750 $BASE_PATH/redis

# 关键：AOF目录必须存在
sudo chmod 770 $BASE_PATH/redis/appendonlydir

# ================================
# 4. RabbitMQ（UID 999）
# ================================
sudo chown -R 999:999 $BASE_PATH/rabbitmq
sudo chmod -R 750 $BASE_PATH/rabbitmq

# 删除旧 cookie
sudo rm -f $BASE_PATH/rabbitmq/.erlang.cookie

# ================================
# 5. MinIO（UID 1000）
# ================================
sudo chown -R 1000:1000 $BASE_PATH/minio
sudo chmod -R 750 $BASE_PATH/minio

# ================================
# 6. Milvus（UID 1000）
# ================================
sudo chown -R 1000:1000 $BASE_PATH/milvus
sudo chmod -R 750 $BASE_PATH/milvus

# ================================
# 7. TEI 模型
# ================================
sudo chown -R 1000:1000 $BASE_PATH/tei
sudo chmod -R 755 $BASE_PATH/tei

# ================================
# 8. App 日志
# ================================
sudo chown -R 1000:1000 $BASE_PATH/app
sudo chmod -R 755 $BASE_PATH/app

echo "✅ 权限设置完成（生产级）"