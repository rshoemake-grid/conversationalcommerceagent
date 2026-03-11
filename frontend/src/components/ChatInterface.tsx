import { useState, useCallback } from 'react';
import { ModeSelector } from './ModeSelector';
import { MessageList } from './MessageList';
import { sendChatMessage } from '../api/chatApi';
import type { OrchestrationMode, ProductDto } from '../api/types';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  products?: ProductDto[];
}

export function ChatInterface() {
  const [mode, setMode] = useState<OrchestrationMode>('convo_commerce');
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>();
  const [sessionId] = useState(() => `session-${Date.now()}`);

  const handleSend = useCallback(async () => {
    const text = input.trim();
    if (!text || loading) return;

    setInput('');
    setMessages((prev) => [
      ...prev,
      { id: `u-${Date.now()}`, role: 'user', content: text },
    ]);
    setLoading(true);

    try {
      const response = await sendChatMessage({
        mode,
        message: text,
        conversationId,
        sessionId,
      });

      setConversationId(response.conversationId);
      setMessages((prev) => [
        ...prev,
        {
          id: `a-${Date.now()}`,
          role: 'assistant',
          content: response.text,
          products: response.products,
        },
      ]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          id: `e-${Date.now()}`,
          role: 'assistant',
          content: `Error: ${err instanceof Error ? err.message : 'Unknown error'}`,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }, [input, loading, mode, conversationId, sessionId]);

  return (
    <div className="chat-interface">
      <header className="chat-header">
        <h1>Conversational Commerce Agent</h1>
        <ModeSelector
          value={mode}
          onChange={setMode}
          disabled={loading}
        />
      </header>

      <MessageList messages={messages} />

      <div className="chat-input-area">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
          placeholder="Type your message..."
          disabled={loading}
        />
        <button onClick={handleSend} disabled={loading}>
          {loading ? 'Sending...' : 'Send'}
        </button>
      </div>
    </div>
  );
}
