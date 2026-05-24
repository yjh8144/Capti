"""
Capti Server - 实时语音识别服务端
组合方案：pyannote 3.1（说话人分段+重叠检测）+ FunASR Paraformer（ASR）

依赖安装：
pip install funasr pyannote.audio torch torchaudio websockets numpy

首次运行需要 HuggingFace token（用于下载 pyannote 模型）：
export HF_TOKEN=your_huggingface_token
"""

import asyncio
import json
import logging
import numpy as np
import torch
import torchaudio
from collections import defaultdict
from dataclasses import dataclass, asdict
from enum import Enum
from typing import Optional

import websockets
from funasr import AutoModel
from pyannote.audio import Pipeline as PyannotePipeline
from pyannote.audio.pipelines.utils.hook import ProgressHook

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("capti-server")


class LatencyMode(Enum):
    LOW = "low"          # ~1s 延迟，流式直出，说话人分离精度较低
    MEDIUM = "medium"    # ~3s 延迟，滑动窗口，说话人分离精度较好
    HIGH = "high"        # ~5s 延迟，较大窗口，说话人标注最准确


@dataclass
class CaptionResult:
    text: str
    speaker_id: int
    is_final: bool
    is_overlap: bool = False
    timestamp_start: float = 0.0
    timestamp_end: float = 0.0


class CaptiEngine:
    def __init__(self, device: str = "cuda:0", latency_mode: LatencyMode = LatencyMode.MEDIUM):
        self.device = device
        self.latency_mode = latency_mode
        self.sample_rate = 16000

        logger.info("Loading FunASR models...")
        self.asr_model = AutoModel(
            model="paraformer-zh-streaming",
            vad_model="fsmn-vad",
            punc_model="ct-punc",
            device=device,
        )

        self.asr_offline = AutoModel(
            model="paraformer-zh",
            vad_model="fsmn-vad",
            punc_model="ct-punc",
            device=device,
        )

        logger.info("Loading pyannote pipeline...")
        self.diarization = PyannotePipeline.from_pretrained(
            "pyannote/speaker-diarization-3.1"
        )
        self.diarization.to(torch.device(device))

        logger.info("All models loaded.")

    def get_window_size(self) -> float:
        if self.latency_mode == LatencyMode.LOW:
            return 1.0
        elif self.latency_mode == LatencyMode.MEDIUM:
            return 3.0
        else:
            return 5.0

    def get_step_size(self) -> float:
        if self.latency_mode == LatencyMode.LOW:
            return 0.5
        elif self.latency_mode == LatencyMode.MEDIUM:
            return 1.5
        else:
            return 2.5

    async def process_window(self, audio_data: np.ndarray) -> list[CaptionResult]:
        """处理一个音频窗口，返回识别结果列表"""
        if len(audio_data) < self.sample_rate * 0.3:
            return []

        waveform = torch.from_numpy(audio_data).unsqueeze(0).float()

        # 说话人分段
        diarization_result = self.diarization(
            {"waveform": waveform, "sample_rate": self.sample_rate}
        )

        results = []
        for turn, _, speaker in diarization_result.itertracks(yield_label=True):
            start_sample = int(turn.start * self.sample_rate)
            end_sample = int(turn.end * self.sample_rate)

            if end_sample - start_sample < int(0.2 * self.sample_rate):
                continue

            segment_audio = audio_data[start_sample:end_sample]
            segment_text = self._transcribe(segment_audio)

            if segment_text.strip():
                speaker_id = int(speaker.split("_")[-1]) if "_" in speaker else 0
                results.append(CaptionResult(
                    text=segment_text,
                    speaker_id=speaker_id,
                    is_final=True,
                    is_overlap=False,
                    timestamp_start=turn.start,
                    timestamp_end=turn.end,
                ))

        # 检测重叠段
        overlap_timeline = diarization_result.get_overlap()
        for segment in overlap_timeline:
            start_sample = int(segment.start * self.sample_rate)
            end_sample = int(segment.end * self.sample_rate)

            if end_sample - start_sample < int(0.2 * self.sample_rate):
                continue

            segment_audio = audio_data[start_sample:end_sample]
            segment_text = self._transcribe(segment_audio)

            if segment_text.strip():
                results.append(CaptionResult(
                    text=segment_text,
                    speaker_id=-1,
                    is_final=True,
                    is_overlap=True,
                    timestamp_start=segment.start,
                    timestamp_end=segment.end,
                ))

        results.sort(key=lambda r: r.timestamp_start)
        return results

    def _transcribe(self, audio: np.ndarray) -> str:
        """对一段音频进行 ASR"""
        try:
            res = self.asr_offline.generate(
                input=audio,
                batch_size_s=0,
                is_final=True,
            )
            if res and len(res) > 0:
                return res[0].get("text", "")
        except Exception as e:
            logger.error(f"ASR error: {e}")
        return ""

    async def process_streaming(self, audio_chunk: np.ndarray, cache: dict) -> Optional[str]:
        """流式 ASR（低延迟模式下直接出文字，不分说话人）"""
        try:
            chunk_size = [0, 10, 5]
            res = self.asr_model.generate(
                input=audio_chunk,
                cache=cache,
                is_final=False,
                chunk_size=chunk_size,
                encoder_chunk_look_back=4,
                decoder_chunk_look_back=1,
            )
            if res and len(res) > 0:
                text = res[0].get("text", "")
                if text.strip():
                    return text
        except Exception as e:
            logger.error(f"Streaming ASR error: {e}")
        return None


class ClientSession:
    def __init__(self, engine: CaptiEngine):
        self.engine = engine
        self.audio_buffer = np.array([], dtype=np.float32)
        self.stream_cache = {}
        self.last_process_pos = 0
        self.speaker_map = {}

    def feed_audio(self, pcm_bytes: bytes):
        """接收 PCM 16-bit 音频数据"""
        samples = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        self.audio_buffer = np.concatenate([self.audio_buffer, samples])

    def should_process(self) -> bool:
        step_samples = int(self.engine.get_step_size() * self.engine.sample_rate)
        return len(self.audio_buffer) - self.last_process_pos >= step_samples

    async def process(self) -> list[CaptionResult]:
        window_samples = int(self.engine.get_window_size() * self.engine.sample_rate)
        window_start = max(0, len(self.audio_buffer) - window_samples)
        window_audio = self.audio_buffer[window_start:]

        self.last_process_pos = len(self.audio_buffer)

        results = await self.engine.process_window(window_audio)

        # 保留最近 30 秒的音频，防止内存无限增长
        max_keep = 30 * self.engine.sample_rate
        if len(self.audio_buffer) > max_keep:
            self.audio_buffer = self.audio_buffer[-max_keep:]
            self.last_process_pos = len(self.audio_buffer)

        return results

    async def process_streaming_chunk(self, pcm_bytes: bytes) -> Optional[str]:
        """流式模式下直接出文字"""
        samples = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
        return await self.engine.process_streaming(samples, self.stream_cache)


async def handle_client(websocket, engine: CaptiEngine):
    """处理一个客户端连接"""
    session = ClientSession(engine)
    latency_mode = engine.latency_mode
    logger.info(f"Client connected, latency mode: {latency_mode.value}")

    try:
        async for message in websocket:
            if isinstance(message, str):
                # JSON 控制消息
                try:
                    config = json.loads(message)
                    if "latency_mode" in config:
                        mode_str = config["latency_mode"]
                        latency_mode = LatencyMode(mode_str)
                        engine_copy = engine
                        logger.info(f"Latency mode changed to: {mode_str}")
                    if config.get("is_speaking") is False:
                        # 客户端结束说话，处理剩余音频
                        if len(session.audio_buffer) > 0:
                            results = await session.process()
                            for r in results:
                                await websocket.send(json.dumps(asdict(r), ensure_ascii=False))
                except json.JSONDecodeError:
                    pass
            else:
                # 二进制音频数据
                if latency_mode == LatencyMode.LOW:
                    # 低延迟：流式直出
                    session.feed_audio(message)
                    text = await session.process_streaming_chunk(message)
                    if text:
                        result = CaptionResult(
                            text=text,
                            speaker_id=0,
                            is_final=False,
                            is_overlap=False,
                        )
                        await websocket.send(json.dumps(asdict(result), ensure_ascii=False))

                    # 同时后台做说话人分段
                    if session.should_process():
                        results = await session.process()
                        for r in results:
                            r.is_final = True
                            await websocket.send(json.dumps(asdict(r), ensure_ascii=False))
                else:
                    # 中/高延迟：窗口处理
                    session.feed_audio(message)
                    if session.should_process():
                        results = await session.process()
                        for r in results:
                            await websocket.send(json.dumps(asdict(r), ensure_ascii=False))

    except websockets.exceptions.ConnectionClosed:
        logger.info("Client disconnected")


async def main():
    import argparse
    parser = argparse.ArgumentParser(description="Capti Server")
    parser.add_argument("--host", default="0.0.0.0", help="绑定地址")
    parser.add_argument("--port", type=int, default=10095, help="端口")
    parser.add_argument("--device", default="cuda:0", help="GPU设备")
    parser.add_argument("--latency", default="medium", choices=["low", "medium", "high"],
                        help="延迟模式: low(~1s), medium(~3s), high(~5s)")
    args = parser.parse_args()

    engine = CaptiEngine(
        device=args.device,
        latency_mode=LatencyMode(args.latency),
    )

    logger.info(f"Starting server on {args.host}:{args.port}")
    async with websockets.serve(
        lambda ws: handle_client(ws, engine),
        args.host,
        args.port,
        max_size=10 * 1024 * 1024,
    ):
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
