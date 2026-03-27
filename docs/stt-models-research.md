# STT Models Research - Speech-to-Text for AR Glasses (China-Accessible)

**Date:** 2026-03-27
**Context:** Finding speech-to-text models accessible from China without VPN for Rokid AR glasses app

---

## Executive Summary

The main challenge is that **OpenRouter doesn't directly offer dedicated STT models** like Whisper. Instead, audio transcription must be handled through:

1. **Multimodal models with audio input** (GPT-4o Audio, Gemini) via OpenRouter
2. **Direct STT API providers** (Deepgram, Gladia, AssemblyAI)
3. **Chinese LLM providers** with audio capabilities on OpenRouter

For your use case, the **recommended approach** is either:
- **Primary:** Use Gladia or Deepgram directly (not via OpenRouter) with their Asia-Pacific infrastructure
- **Fallback:** Use multimodal models via OpenRouter with Chinese-friendly providers (MiniMax, StepFun)

---

## Option 1: OpenRouter Multimodal Models (Audio Input)

OpenRouter supports audio input through multimodal models via the Chat Completions API using `input_audio` content type.

### Available Models

| Model | Provider | Languages | Notes |
|-------|----------|-----------|-------|
| `openai/gpt-4o-audio-preview` | OpenAI | 50+ | Best quality, but **blocked in China** |
| `google/gemini-2.5-flash-preview` | Google | 40+ | Good multilingual, accessible |
| `openai/gpt-4o-mini-transcribe` | OpenAI | 50+ | New Dec 2025 model, improved WER |

### How It Works

```python
import base64

# Encode audio file
with open("audio.wav", "rb") as f:
    audio_b64 = base64.b64encode(f.read()).decode("utf-8")

# Call OpenRouter
response = client.chat.completions.create(
    model="google/gemini-2.5-flash-preview",
    messages=[{
        "role": "user",
        "content": [
            {"type": "text", "text": "Transcribe this audio accurately."},
            {"type": "input_audio", "input_audio": {
                "data": audio_b64,
                "format": "wav"
            }}
        ]
    }]
)
```

### Pros
- Unified API through OpenRouter
- Works with existing SDKs
- Supports many languages including Chinese

### Cons
- Not optimized for STT (designed for multimodal reasoning)
- Higher latency than dedicated STT
- Higher cost (billed per token, not audio minutes)
- Quality not as good as dedicated STT models

---

## Option 2: Direct STT Providers (Recommended)

These are dedicated speech-to-text APIs with better quality and lower latency:

### Deepgram

| Feature | Details |
|---------|---------|
| **Models** | Nova-3 (batch), Flux (real-time) |
| **Latency** | <300ms for streaming |
| **Languages** | 45+ including English, Chinese (Mandarin) |
| **Pricing** | $0.26-0.46/hr (pre-recorded), $0.46+/hr (streaming) |
| **China Access** | US-based, may require VPN or use Asia-Pacific region |

**API Example:**
```python
import deepgram

dg_client = deepgram.Deepgram("YOUR_API_KEY")
response = await dg_client.transcription.prerecorded(
    {"url": "https://example.com/audio.wav"},
    {"model": "nova-2", "language": "zh"}
)
```

### Gladia (Recommended for Multilingual)

| Feature | Details |
|---------|---------|
| **Model** | Solaria-1 |
| **Latency** | <300ms partial latency |
| **Languages** | 100+ with native code-switching |
| **Pricing** | $0.61-0.75/hr (all features included) |
| **China Access** | European company, has multiple cloud regions |

**Strengths:**
- Best multilingual support (100+ languages)
- Native code-switching (great for EN/CN mixed speech)
- All features included in price (diarization, sentiment, etc.)
- Doesn't use customer data for training

### AssemblyAI

| Feature | Details |
|---------|---------|
| **Models** | Universal-2, Universal-3, Slam-1 |
| **Latency** | ~300ms for streaming |
| **Languages** | 99+ |
| **Pricing** | $0.15-0.54/hr |
| **China Access** | US-based, data routes through US |

**Strengths:**
- Best WER (93.3% word accuracy)
- LeMUR framework for LLM integration
- Low pricing

---

## Option 3: Chinese Providers via OpenRouter

According to recent data, Chinese models account for 61% of token usage on OpenRouter. Some may support audio input:

### Available Chinese Models on OpenRouter

| Model | Provider | Notes |
|-------|----------|-------|
| `minimax/minimax-m2.5` | MiniMax | Top 3 most popular |
| `moonshot/kimi-k2.5` | Moonshot AI | Strong Chinese performance |
| `z-ai/glm-4.5-air` | Zhipu/Beijing | Free tier available |
| `stepfun/step-3.5-flash` | StepFun | Good latency |

**Important Note:** OpenRouter's data shows that MiniMax queries are often processed by US-based data centers, making them accessible from China without VPN.

### Testing Audio Input with Chinese Models

```python
# Not all Chinese models support audio - test empirically
response = client.chat.completions.create(
    model="minimax/minimax-m2.5",  # May not support audio input
    messages=[{
        "role": "user",
        "content": [
            {"type": "text", "text": "转录这段音频"},
            {"type": "input_audio", "input_audio": {
                "data": audio_b64,
                "format": "wav"
            }}
        ]
    }]
)
```

**Caveat:** Most Chinese LLM providers on OpenRouter are optimized for text generation, not speech transcription. Audio input support and transcription quality are unknown.

---

## Option 4: Self-Hosted / Edge STT

For complete China independence, consider running STT locally:

### Whisper Models (Open Source)

| Model | Size | WER | RTFx | Notes |
|-------|------|-----|------|-------|
| Whisper Large V3 Turbo | 809M | 10-12% | 216x | Good balance |
| Distil-Whisper | 76M | ~12% | 1500x | Fastest, smaller |
| Parakeet TDT 0.6B | 600M | 6.05% | 3386x | Fastest, Nvidia only |

**Deployment Options:**
- **Modal.ai** - Deploy Whisper on cloud GPU
- **Ollama** - Local inference
- **Rokid glasses** - Could run distilled model on-device

**Pros:** Complete offline capability, no API dependency
**Cons:** Requires device compute, lower quality than cloud APIs

---

## Ranking & Recommendation

### For Your Use Case (Real-time AR Glasses, China-Accessible)

| Rank | Option | Latency | Quality | China Access | Notes |
|------|--------|---------|---------|--------------|-------|
| 1 | **Gladia** (direct) | <300ms | Excellent | ✅ Good | Best multilingual, code-switching |
| 2 | **Deepgram Flux** (direct) | <300ms | Excellent | ⚠️ Test | Best for voice agents |
| 3 | **OpenRouter + Gemini** | ~1-2s | Good | ✅ Good | Accessible via OpenRouter |
| 4 | **MiniMax via OpenRouter** | Varies | Unknown | ✅ Good | May not support audio |

### Recommendation

**Primary:** Use **Gladia's real-time API** directly. They're a pure-play speech infrastructure company that:
- Supports 100+ languages with native code-switching
- Has sub-300ms latency
- Offers competitive pricing ($0.75/hr all-inclusive)
- Has European infrastructure (GDPR-compliant)

**Fallback:** If Gladia isn't accessible from China, use **OpenRouter with Gemini 2.5 Flash** as a multimodal transcription option.

### Testing Checklist

1. [ ] Test Gladia API from China (check latency)
2. [ ] Test OpenRouter Gemini audio input
3. [ ] Test MiniMax/Moonshot audio input (if available)
4. [ ] Benchmark actual latency with your audio format

---

## API Integration Notes

### Gladia Integration

```python
import requests

url = "https://api.gladia.io/v2/pre-recorded"
headers = {"x-gladia-key": "YOUR_API_KEY"}

response = requests.post(url, json={
    "audio_url": "https://your-server/audio.wav",
    "language_detection": True,
    "diarization": True
})
```

### OpenRouter Audio Transcription

```bash
# Using the transcribe script skill
OPENROUTER_API_KEY=xxx transcribe.sh audio.m4a --model google/gemini-2.5-flash-preview
```

### Audio Format Requirements

- **Sample rate:** 16kHz (most models), 8kHz (telephony)
- **Formats:** WAV, MP3, M4A, FLAC, OGG
- **Channels:** Mono recommended
- **Max duration:** Varies by provider (typically 4-24 hours)

---

## Cost Comparison

| Provider | Mode | Cost/min | Est. Monthly (1hr/day) |
|----------|------|----------|----------------------|
| Gladia | Streaming | $0.0125/min | ~$225/month |
| Deepgram | Streaming | $0.0077/min | ~$140/month |
| AssemblyAI | Batch | $0.0025/min | ~$45/month |
| OpenRouter Gemini | Token-based | ~$0.001/req | ~$18/month |

---

## Caveats

1. **No dedicated STT on OpenRouter** - Must use multimodal models or direct providers
2. **China accessibility varies** - Test each provider empirically; VPNs may still be needed for some
3. **Real-time requirements** - For <2s latency, prefer streaming APIs (Gladia, Deepgram)
4. **Multilingual quality** - Test Chinese transcription quality specifically; English-focused models may underperform
5. **Audio format** - Ensure phone→glasses audio pipeline matches API requirements

---

## References

- OpenRouter Audio Documentation: https://openrouter.ai/docs/guides/overview/multimodal/audio
- Gladia: https://www.gladia.io
- Deepgram: https://deepgram.com
- AssemblyAI: https://www.assemblyai.com
- Open ASR Leaderboard: https://huggingface.co/open_asr_leaderboard
- ChinAI Newsletter (Chinese models on OpenRouter): https://chinai.substack.com