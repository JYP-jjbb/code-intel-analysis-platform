"""
Unified LLM client supporting multiple providers.
"""

from typing import List, Dict, Optional, Any
from dataclasses import dataclass
import os

# Import provider-specific clients
try:
    from openai import OpenAI
    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False

try:
    import google.generativeai as genai
    GOOGLE_AVAILABLE = True
except ImportError:
    GOOGLE_AVAILABLE = False


@dataclass
class LLMResponse:
    """Unified format for LLM responses."""
    content: str
    usage: Dict[str, Optional[int]]
    finish_reason: str
    raw_response: Optional[Any] = None


class LLMClient:
    """Unified LLM client supporting multiple providers."""
    
    def __init__(
        self,
        provider: str,
        model: str,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        gemini_show_reasoning: bool = False,
    ):
        """
        Initialize LLM client.
        
        Args:
            provider: "openai" / "google" / "deepseek"
            model: Model name
            api_key: API key (if None, read from environment)
            base_url: Base URL for API (optional)
        """
        self.provider = provider.lower()
        self.model = model
        self.api_key = api_key
        self.base_url = base_url
        self.gemini_show_reasoning = gemini_show_reasoning
        self._init_client()

    def _init_client(self):
        """Initialize provider-specific client."""
        if self.provider in ("openai", "openrouter"):
            if not OPENAI_AVAILABLE:
                raise ImportError("openai package is not installed")

            # pick env var name by provider
            env_key = "OPENAI_API_KEY" if self.provider == "openai" else "OPENROUTER_API_KEY"
            api_key = self.api_key or os.environ.get(env_key)

            base_url = self.base_url
            if self.provider == "openrouter" and not base_url:
                base_url = "https://openrouter.ai/api/v1"

            self.client = OpenAI(api_key=api_key, base_url=base_url)

        elif self.provider == "google":
            if not GOOGLE_AVAILABLE:
                raise ImportError("google-generativeai package is not installed")

            genai.configure(api_key=self.api_key or os.environ.get("GOOGLE_API_KEY"))
            self.client = genai.GenerativeModel(self.model)

        elif self.provider == "deepseek":
            if not OPENAI_AVAILABLE:
                raise ImportError("openai package is not installed (required for DeepSeek)")

            self.client = OpenAI(
                api_key=self.api_key or os.environ.get("DEEPSEEK_API_KEY"),
                base_url="https://api.deepseek.com"
            )

        else:
            raise ValueError(f"Unsupported provider: {self.provider}")

    def _get_openrouter_gemini_extra_body(self) -> Optional[Dict[str, Any]]:
        """
        仅当 provider=openrouter 且 model=google/gemini-3-flash-preview
        且 gemini_show_reasoning=True 时，返回 Gemini 专属 extra_body。
        """
        if self.provider != "openrouter":
            return None

        if (self.model or "").strip() != "google/gemini-3-flash-preview":
            return None

        if not self.gemini_show_reasoning:
            return None

        return {
            "include_reasoning": True,
            "reasoning": {
                "enabled": True
            },
        }

    def chat(
            self,
            messages: List[Dict[str, str]],
            temperature: float = 0.0,
            max_tokens: int = 100000,
            extra_body: Optional[Dict[str, Any]] = None,
    ) -> LLMResponse:
        """
        Unified chat interface.
        """
        if self.provider in ["openai", "openrouter", "deepseek"]:
            return self._chat_openai_style(
                messages,
                temperature,
                max_tokens,
                extra_body=extra_body,
            )
        elif self.provider == "google":
            return self._chat_google(messages, temperature, max_tokens)
        else:
            raise ValueError(f"Unsupported provider: {self.provider}")

    def _chat_openai_style(
            self,
            messages: List[Dict[str, str]],
            temperature: float,
            max_tokens: int,
            extra_body: Optional[Dict[str, Any]] = None,
    ) -> LLMResponse:
        kwargs = dict(
            model=self.model,
            messages=messages,
            max_tokens=max_tokens,
            temperature=temperature,
        )

        # 优先级：
        # 1. 显式传入的 extra_body（Claude 用）
        # 2. OpenRouter Gemini 自动推理显示（Gemini 用）
        # 3. 其他模型默认无 extra_body
        final_extra_body = extra_body
        if final_extra_body is None:
            final_extra_body = self._get_openrouter_gemini_extra_body()

        if final_extra_body:
            resp = self.client.chat.completions.create(**kwargs, extra_body=final_extra_body)
        else:
            resp = self.client.chat.completions.create(**kwargs)

        msg = resp.choices[0].message
        content = msg.content or ""

        return LLMResponse(
            content=content,
            usage={
                "prompt_tokens": resp.usage.prompt_tokens,
                "completion_tokens": resp.usage.completion_tokens,
                "total_tokens": resp.usage.total_tokens
            },
            finish_reason=resp.choices[0].finish_reason,
            raw_response=resp
        )

    def _chat_google(
        self,
        messages: List[Dict[str, str]],
        temperature: float,
        max_tokens: int
    ) -> LLMResponse:
        """Google Gemini API call."""
        # Convert message format
        prompt_parts = []
        for msg in messages:
            if msg["role"] == "system":
                prompt_parts.append(f"System: {msg['content']}")
            elif msg["role"] == "user":
                prompt_parts.append(f"User: {msg['content']}")
            elif msg["role"] == "assistant":
                prompt_parts.append(f"Assistant: {msg['content']}")
        
        prompt = "\n\n".join(prompt_parts)
        
        response = self.client.generate_content(
            prompt,
            generation_config={
                "temperature": temperature,
                "max_output_tokens": max_tokens
            }
        )
        
        return LLMResponse(
            content=response.text,
            usage={"total_tokens": None},  # Gemini doesn't directly return token count
            finish_reason="stop",
            raw_response=response
        )
