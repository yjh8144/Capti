# Capti Server 部署指南

## 环境要求

- Python >= 3.8
- CUDA >= 11.8
- GPU: 任意一张 4090 即可（全套模型 ~3GB 显存）

## 安装

```bash
cd server
pip install -r requirements.txt
```

首次运行需要 HuggingFace token（下载 pyannote 模型）：
1. 在 https://huggingface.co/ 注册账号
2. 接受 pyannote/speaker-diarization-3.1 的使用条款
3. 创建 access token

```bash
export HF_TOKEN=your_token_here
huggingface-cli login
```

## 启动

```bash
# 默认配置（medium 延迟模式，GPU 0）
python capti_server.py

# 指定 GPU 和延迟模式
python capti_server.py --device cuda:1 --latency low --port 10095

# 多实例（不同 GPU 不同端口）
python capti_server.py --device cuda:0 --port 10095 &
python capti_server.py --device cuda:1 --port 10096 &
python capti_server.py --device cuda:2 --port 10097 &
python capti_server.py --device cuda:3 --port 10098 &
```

## 延迟模式说明

| 模式 | 延迟 | 说话人分离 | 适用场景 |
|------|------|-----------|----------|
| low | ~1s | 后台异步标注 | 实时对话、速记 |
| medium | ~3s | 窗口内聚类 | 会议记录 |
| high | ~5s | 大窗口全局聚类 | 精确标注 |

客户端可以在连接后发送 JSON 切换模式：
```json
{"latency_mode": "low"}
```

## WebSocket 协议

### 客户端 → 服务端
- 文本消息：JSON 控制指令
- 二进制消息：PCM 16-bit 16kHz 单声道音频数据

### 服务端 → 客户端
```json
{
  "text": "识别的文字",
  "speaker_id": 0,
  "is_final": true,
  "is_overlap": false,
  "timestamp_start": 1.5,
  "timestamp_end": 3.2
}
```

- `speaker_id`: 说话人编号，-1 表示重叠段
- `is_final`: true 为确定结果，false 为中间结果（低延迟模式）
- `is_overlap`: true 表示该段有多人重叠说话

## 显存占用估算

| 模型 | 参数量 | 估算显存 |
|------|--------|---------|
| Paraformer-zh (streaming) | 220M | ~0.5GB |
| Paraformer-zh (offline) | 220M | ~0.5GB |
| fsmn-vad | 0.4M | ~忽略 |
| ct-punc | 290M | ~0.7GB |
| pyannote segmentation | ~5M | ~0.1GB |
| pyannote embedding | ~5M | ~0.1GB |
| **合计** | | **~2GB** |

单张 4090 (24GB) 可轻松运行，剩余显存足够处理大量并发。
