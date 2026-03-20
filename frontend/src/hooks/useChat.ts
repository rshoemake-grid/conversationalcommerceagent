import { useState, useCallback, useRef, useEffect } from 'react';
import { sendChatMessage } from '../api/chatApi';
import { useVoiceOutput } from './useVoiceOutput';
import type { Message, OrchestrationMode } from '../api/types';

function createUserMessage(text: string, imageUri?: string, imageBase64?: string): Message {
  return {
    id: `u-${crypto.randomUUID()}`,
    role: 'user',
    content: text || (imageUri ? '[Image]' : ''),
    imageUri,
    imageBase64,
  };
}

function createAssistantMessage(response: { text?: string; products?: Message['products']; refinedQuery?: string; source?: Message['source'] }): Message {
  let text = (response.text ?? '').trim();
  const products = response.products ?? [];
  const refinedQuery = response.refinedQuery ?? '';
  // When we have products but text is "Searching for:" placeholder, show count instead
  if (products.length > 0 && text.startsWith('Searching for:')) {
    text = products.length === 1
      ? 'I found 1 product matching your request.'
      : `I found ${products.length} products matching your request.`;
  }
  // When no products and text is placeholder or empty, show explicit no-results message
  else if (products.length === 0 && refinedQuery && (text === '' || text.startsWith('Searching for:'))) {
    text = 'No products found.';
  }
  const hasContent = text.length > 0 || products.length > 0;
  return {
    id: `a-${crypto.randomUUID()}`,
    role: 'assistant',
    content: hasContent ? text : "I didn't understand your response.",
    products,
    source: response.source,
  };
}

function createErrorMessage(err: unknown): Message {
  return {
    id: `e-${crypto.randomUUID()}`,
    role: 'assistant',
    content: err instanceof Error ? err.message : 'Unknown error',
    isError: true,
  };
}

/** Extract raw base64 from data URL (data:image/...;base64,XXXX) */
function toRawBase64(dataUrl: string): string {
  const idx = dataUrl.indexOf(',');
  return idx >= 0 ? dataUrl.slice(idx + 1) : dataUrl;
}

export function useChat() {
  const [mode, setMode] = useState<OrchestrationMode>('convo_commerce');
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [pendingImage, setPendingImage] = useState<string | null>(null);
  const [voiceOutputEnabled, setVoiceOutputEnabled] = useState(false);
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>();
  const [sessionId] = useState(() => `session-${crypto.randomUUID()}`);
  const { speak, stop: stopSpeaking, isSpeaking } = useVoiceOutput();

  const inputRef = useRef(input);
  const conversationIdRef = useRef<string | undefined>();
  useEffect(() => {
    inputRef.current = input;
  }, [input]);
  useEffect(() => {
    conversationIdRef.current = conversationId;
  }, [conversationId]);

  const sendMessage = useCallback(
    async (
      messageText: string,
      options?: { imageBase64?: string; removeErrorId?: string }
    ) => {
      if (loading) return;
      const hasText = messageText != null && messageText.trim().length > 0;
      const hasImage = options?.imageBase64 != null && options.imageBase64.length > 0;
      if (!hasText && !hasImage) return;

      if (options?.removeErrorId) {
        setMessages((prev) => prev.filter((m) => m.id !== options.removeErrorId));
      }
      setLoading(true);

      const currentConversationId = conversationIdRef.current;

      try {
        const response = await sendChatMessage({
          mode,
          message: hasText ? messageText : '',
          conversationId: currentConversationId,
          sessionId,
          imageBase64: options?.imageBase64,
        });
        if (response.conversationId != null && response.conversationId !== '') {
          conversationIdRef.current = response.conversationId;
          setConversationId(response.conversationId);
        }
        setMessages((prev) => [...prev, createAssistantMessage(response)]);
        if (voiceOutputEnabled && response.text?.trim()) {
          speak(response.text);
        }
      } catch (err) {
        setMessages((prev) => [...prev, createErrorMessage(err)]);
      } finally {
        setLoading(false);
      }
    },
    [loading, mode, sessionId, voiceOutputEnabled, speak]
  );

  const handleSend = useCallback(() => {
    const text = inputRef.current.trim();
    const imageData = pendingImage;
    const hasText = text.length > 0;
    const hasImage = imageData != null && imageData.length > 0;
    if ((!hasText && !hasImage) || loading) return;

    const displayUri = hasImage && imageData
      ? (imageData.startsWith('data:') ? imageData : `data:image/jpeg;base64,${imageData}`)
      : undefined;
    const rawBase64 = displayUri ? toRawBase64(displayUri) : undefined;

    setInput('');
    setPendingImage(null);
    setMessages((prev) => [
      ...prev,
      createUserMessage(text || '[Image]', displayUri, rawBase64),
    ]);
    sendMessage(hasText ? text : '', { imageBase64: rawBase64 });
  }, [loading, pendingImage, sendMessage]);

  const handleVoiceResult = useCallback(
    (transcript: string) => {
      const t = transcript.trim();
      if (!t || loading) return;
      // Filter false positives: "Attached"/"attach" often transcribed when no image is uploaded
      if (/^(attached|attach)$/i.test(t)) return;
      setMessages((prev) => [...prev, createUserMessage(t)]);
      sendMessage(t);
    },
    [loading, sendMessage]
  );

  const handleImageSelect = useCallback((dataUrl: string) => {
    setPendingImage(dataUrl);
  }, []);

  const clearPendingImage = useCallback(() => {
    setPendingImage(null);
  }, []);

  const handleRetry = useCallback(
    (messageText: string, errorId: string, imageBase64?: string) => {
      sendMessage(messageText, { removeErrorId: errorId, imageBase64 });
    },
    [sendMessage]
  );

  const handleDismissError = useCallback((messageId: string) => {
    setMessages((prev) => prev.filter((m) => m.id !== messageId));
  }, []);

  const startNewConversation = useCallback(() => {
    setMessages([]);
    conversationIdRef.current = undefined;
    setConversationId(undefined);
    setInput('');
    setPendingImage(null);
    stopSpeaking();
  }, [stopSpeaking]);

  return {
    mode,
    setMode,
    messages,
    input,
    setInput,
    pendingImage,
    loading,
    voiceOutputEnabled,
    setVoiceOutputEnabled,
    isSpeaking,
    stopSpeaking,
    handleSend,
    handleVoiceResult,
    handleImageSelect,
    clearPendingImage,
    handleRetry,
    handleDismissError,
    startNewConversation,
  };
}
