"""
Capti Server v1 - 实时语音识别服务端（简化版）
使用 FunASR（Paraformer + VAD + 标点 + 说话人分离）

启动方式：
  python capti_server.py --device cuda:0 --port 10095
"""

import asyncio
import json
import logging
import numpy as np
import sys

import websockets
from funasr import AutoModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("capti-server")


class CaptiEngine:
    def __init__(self, device: str = "cuda:0"):
        self.device = device
        self.sample_rate = 16000

        logger.info("Loading FunASR models (this may download models on first run)...")

        self.model = AutoModel(
            model="iic/speech_paraformer-large-vad-punc_asr_nat-zh-cn-16k-common-vocab8404-pytorch",
            vad_model="fsmn-vad",
            punc_model="ct-punc",
            spk_model="cam++",
            device=device,
        )

        logger.info("All models loaded successfully.")

    def transcribe(self, audio_data: np.ndarray) -> list[dict]:
        """对一段音频进行 ASR + 说话人分离"""
        try:
            res = self.model.generate(
                input=audio_data,
                batch_size_s=0,
            )
            if res and len(res) > 0:
                return res
        except Exception as e:
            logger.error(f"ASR error: {e}")
        return []


class ClientSession:
    def __init__(self, engine: CaptiEngine, window_seconds: float = 5.0, step_seconds: float = 2.0):
        self.engine = engine
        self.audio_buffer = np.array([], dtype=np.float32)
        self.window_seconds = window_seconds
        self.step_seconds = step_seconds
        self.last_process_pos = 0
        self.processed_text = ""

    def feed_audio(self, pcm_bytes: bytes):
        """接收 PCM 16-bit 音频数据"""
        samples = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        self.audio_buffer = np.concatenate([self.audio_buffer, samples])

    def should_process(self) -> bool:
        step_samples = int(self.step_seconds * self.engine.sample_rate)
        return len(self.audio_buffer) - self.last_process_pos >= step_samples

    async def process(self) -> list[dict]:
        window_samples = int(self.window_seconds * self.engine.sample_rate)
        window_start = max(0, len(self.audio_buffer) - window_samples)
        window_audio = self.audio_buffer[window_start:]

        self.last_process_pos = len(self.audio_buffer)

        if len(window_audio) < self.engine.sample_rate * 0.5:
            return []

        results = await asyncio.get_event_loop().run_in_executor(
            None, self.engine.transcribe, window_audio
        )

        output = []
        for item in results:
            text = item.get("text", "")
            if not text.strip():
                continue

            # FunASR spk_model 返回的说话人信息
            spk_info = item.get("spk", None)
            sentence_info = item.get("sentence_info", None)

            if sentence_info:
                # 按句子分段，每句带说话人标签
                for sent in sentence_info:
                    sent_text = sent.get("text", "")
                    spk_id = sent.get("spk", 0)
                    if sent_text.strip():
                        output.append({
                            "text": sent_text,
                            "speaker_id": spk_id,
                            "is_final": True,
                            "is_overlap": False,
                        })
            else:
                output.append({
                    "text": text,
                    "speaker_id": 0,
                    "is_final": True,
                    "is_overlap": False,
                })

        # 保留最近 30 秒的音频
        max_keep = 30 * self.engine.sample_rate
        if len(self.audio_buffer) > max_keep:
            self.audio_buffer = self.audio_buffer[-max_keep:]
            self.last_process_pos = len(self.audio_buffer)

        return output

    async def process_final(self) -> list[dict]:
        """处理剩余音频"""
        if len(self.audio_buffer) - self.last_process_pos < self.engine.sample_rate * 0.3:
            return []
        return await self.process()


async def handle_client(websocket, engine: CaptiEngine):
    """处理一个客户端连接"""
    window_seconds = 5.0
    step_seconds = 2.0
    session = ClientSession(engine, window_seconds, step_seconds)
    logger.info(f"Client connected from {websocket.remote_address}")

    try:
        async for message in websocket:
            if isinstance(message, str):
                try:
                    config = json.loads(message)
                    # 客户端可设置延迟模式
                    latency = config.get("latency_mode", "medium")
                    if latency == "low":
                        session.window_seconds = 2.0
                        session.step_seconds = 1.0
                    elif latency == "medium":
                        session.window_seconds = 5.0
                        session.step_seconds = 2.0
                    elif latency == "high":
                        session.window_seconds = 8.0
                        session.step_seconds = 3.0

                    if config.get("is_speaking") is False:
                        results = await session.process_final()
                        for r in results:
                            await websocket.send(json.dumps(r, ensure_ascii=False))
                except json.JSONDecodeError:
                    pass
            else:
                # 二进制音频数据
                session.feed_audio(message)
                if session.should_process():
                    results = await session.process()
                    for r in results:
                        await websocket.send(json.dumps(r, ensure_ascii=False))

    except websockets.exceptions.ConnectionClosed:
        logger.info(f"Client disconnected: {websocket.remote_address}")
    except Exception as e:
        logger.error(f"Error handling client: {e}")


async def main():
    import argparse
    parser = argparse.ArgumentParser(description="Capti Server")
    parser.add_argument("--host", default="0.0.0.0", help="绑定地址")
    parser.add_argument("--port", type=int, default=10095, help="端口")
    parser.add_argument("--device", default="cuda:0", help="GPU设备 (cuda:0, cuda:1, ...)")
    args = parser.parse_args()

    engine = CaptiEngine(device=args.device)

    logger.info(f"Server starting on ws://{args.host}:{args.port}")
    async with websockets.serve(
        lambda ws: handle_client(ws, engine),
        args.host,
        args.port,
        max_size=10 * 1024 * 1024,
    ):
        logger.info("Server ready. Waiting for connections...")
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
