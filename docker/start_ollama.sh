#!/bin/bash

# --- 0. 环境与目录配置 ---
# 1. 获取当前脚本所在的绝对目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ENV_FILE="${SCRIPT_DIR}/.env"

# 2. 如果同级目录下存在 .env 文件，则自动加载它
if [ -f "$ENV_FILE" ]; then
    echo ">>> 发现并加载环境配置文件: $ENV_FILE"
    # 过滤掉注释行，并导出为环境变量
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo ">>> 未找到 .env 文件，将使用默认配置。"
fi

# 3. 获取 STORAGE_PATH_1 (优先用 .env 里的值，如果没有则用后面的默认值兜底)
export STORAGE_PATH_1="${STORAGE_PATH_1:-/home/gdcors/docker/data-disk/gd-cors}"

# 4. 设定 Ollama 的日志子目录并创建
LOG_DIR="${STORAGE_PATH_1}/ollama/logs"
mkdir -p "$LOG_DIR"
echo ">>> Ollama 日志将统一存储在: $LOG_DIR"
echo "-----------------------------------------------"


# --- 1. 强力清理 (需要 sudo 权限) ---
# 注意：正式运行时建议解除注释，确保端口没被占用
# echo "正在清理旧进程与释放端口..."
# sudo pkill -9 ollama
# sleep 3

# --- 2. 启动配置函数 ---
# 参数: $1:端口, $2:显卡序号, $3:模型名, $4:日志名
start_instance() {
    local port=$1
    local gpus=$2
    local model=$3
    local log_file=$4

    echo ">>> 正在启动实例: $model (端口: $port, 显卡: $gpus)"

    export CUDA_VISIBLE_DEVICES=$gpus
    export OLLAMA_SCHED_SPREAD=1

    # 日志输出重定向到数据盘
    OLLAMA_HOST=0.0.0.0:$port ollama serve > "${LOG_DIR}/${log_file}" 2>&1 &

    sleep 8

    echo "    正在预加载模型 $model 到显存..."
    OLLAMA_HOST=127.0.0.1:$port ollama run "$model" --keepalive 24h "init" > /dev/null 2>&1

    echo "    $model 加载尝试完成。"
    echo "-----------------------------------------------"
}

# --- 3. 布局执行 ---

# 聊天推理 (0-5卡)
start_instance 11431 "0,1" "qwen3:32b" "gpu0-1.log"
start_instance 11432 "2,3" "qwen3:32b" "gpu2-3.log"
start_instance 11433 "4,5" "qwen3:32b" "gpu4-5.log"

# 视觉理解 (6卡)
start_instance 11435 "6" "qwen3-vl:8b" "gpu6.log"

# 向量化 (7卡)
start_instance 11436 "7" "qwen3-embedding:0.6b" "gpu7.log"

echo "所有实例启动指令已发出！底层算力池已就绪。"