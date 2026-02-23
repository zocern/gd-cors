#!/bin/bash

# ============================================
# RAG-GDGS Logrotate 安装脚本
# ============================================
# 说明：此脚本用于安装 logrotate 配置文件
# ============================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/rag-gdgs-logs"
TARGET_FILE="/etc/logrotate.d/rag-gdgs-logs"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}RAG-GDGS Logrotate 安装脚本${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}错误：请使用 sudo 运行此脚本${NC}"
    exit 1
fi

# 检查配置文件是否存在
if [ ! -f "$CONFIG_FILE" ]; then
    echo -e "${RED}错误：配置文件不存在: $CONFIG_FILE${NC}"
    exit 1
fi

# 检查存储路径
STORAGE_PATH="${RAG_GDGS_STORAGE_PATH:-/data/rag-gdgs}"
echo -e "${YELLOW}存储路径: $STORAGE_PATH${NC}"

# 如果使用自定义路径，提示用户修改配置
if [ "$STORAGE_PATH" != "/data/rag-gdgs" ]; then
    echo -e "${YELLOW}⚠️  检测到自定义存储路径: $STORAGE_PATH${NC}"
    echo -e "${YELLOW}请确保配置文件中的路径已更新为: $STORAGE_PATH${NC}"
    read -p "是否继续安装？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# 复制配置文件
echo -e "${GREEN}正在安装配置文件...${NC}"
cp "$CONFIG_FILE" "$TARGET_FILE"
chmod 644 "$TARGET_FILE"

echo -e "${GREEN}✅ 配置文件已安装到: $TARGET_FILE${NC}"

# 测试配置
echo -e "${GREEN}正在测试配置...${NC}"
if logrotate -d "$TARGET_FILE" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 配置测试通过${NC}"
else
    echo -e "${RED}⚠️  配置测试失败，请检查配置文件${NC}"
    logrotate -d "$TARGET_FILE"
    exit 1
fi

# 检查日志目录是否存在
echo -e "${GREEN}正在检查日志目录...${NC}"
DIRS=(
    "$STORAGE_PATH/mysql/logs"
    "$STORAGE_PATH/redis/logs"
    "$STORAGE_PATH/rabbitmq/logs"
    "$STORAGE_PATH/logs"
)

for dir in "${DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo -e "${GREEN} 目录存在: $dir${NC}"
    else
        echo -e "${YELLOW}⚠️  目录不存在: $dir（将在首次日志写入时创建）${NC}"
    fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}安装完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "下一步："
echo "1. logrotate 通常每天自动执行一次"
echo "2. 如需手动执行轮转：sudo logrotate -f $TARGET_FILE"
echo "3. 查看执行日志：sudo cat /var/log/logrotate.log"
echo ""

