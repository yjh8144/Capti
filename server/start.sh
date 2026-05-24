#!/bin/bash
# Capti Server 启动脚本
# 等待模型下载完成后自动启动

source /home/mss/anaconda3/etc/profile.d/conda.sh
conda activate vllm
cd /hzy/Capti/server

echo "=== Capti Server 启动脚本 ==="
echo "等待模型下载完成..."

# 等待 SenseVoiceSmall model.pt 下载完成 (893MB)
MODEL_PATH="$HOME/.cache/modelscope/hub/models/iic/SenseVoiceSmall/model.pt"
TARGET_SIZE=890000000

while true; do
    if [ -f "$MODEL_PATH" ]; then
        SIZE=$(stat -c%s "$MODEL_PATH" 2>/dev/null || echo 0)
        if [ "$SIZE" -gt "$TARGET_SIZE" ]; then
            echo "SenseVoiceSmall model.pt 下载完成 ($SIZE bytes)"
            break
        fi
        echo "model.pt 下载中... $(du -h $MODEL_PATH | cut -f1) / 893M"
    fi
    sleep 30
done

# 等待 wget 进程结束（确保文件写入完成）
while pgrep -f "wget.*model.pt" > /dev/null 2>&1; do
    echo "等待 wget 完成..."
    sleep 5
done

echo "拉取最新代码..."
cd /hzy/Capti && git pull && cd server

echo "启动 Capti Server..."
python capti_server.py --device cuda:0 --port 10095
