import { useRef, useEffect } from 'react';
import { ModeSelector } from './ModeSelector';
import { MessageList } from './MessageList';
import { VoiceInput } from './VoiceInput';
import { ImageInput } from './ImageInput';
import { VoiceOutputToggle } from './VoiceOutputToggle';
import { useChat } from '../hooks/useChat';

export function ChatInterface() {
  const inputRef = useRef<HTMLInputElement>(null);
  const {
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
  } = useChat();

  useEffect(() => {
    if (!loading && inputRef.current) {
      inputRef.current.focus();
    }
  }, [loading]);

  return (
    <div className="chat-interface">
      <header className="chat-header">
        <h1>Conversational Commerce Agent</h1>
        <div className="chat-header__controls">
          <button
            type="button"
            onClick={startNewConversation}
            disabled={loading}
            className="chat-header__new-conversation"
            aria-label="Start new conversation"
            title="Start new conversation"
          >
            New conversation
          </button>
          <ModeSelector
            value={mode}
            onChange={setMode}
            disabled={loading}
          />
          <VoiceOutputToggle
            enabled={voiceOutputEnabled}
            onToggle={setVoiceOutputEnabled}
            isSpeaking={isSpeaking}
            onStop={stopSpeaking}
            disabled={loading}
          />
        </div>
      </header>

      <MessageList
        messages={messages}
        loading={loading}
        onRetry={handleRetry}
        onDismissError={handleDismissError}
      />

      <div className="chat-input-area">
        <div className="chat-input-area__row">
          {pendingImage && (
            <div className="chat-input-area__preview">
              <img src={pendingImage.startsWith('data:') ? pendingImage : `data:image/jpeg;base64,${pendingImage}`} alt="Preview" className="chat-input-area__preview-img" />
              <button type="button" onClick={clearPendingImage} className="chat-input-area__preview-remove" aria-label="Remove image">×</button>
            </div>
          )}
          <div className="chat-input-area__inputs">
            <input
              ref={inputRef}
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
              placeholder="Type your message..."
              disabled={loading}
              aria-label="Chat message"
              aria-busy={loading}
            />
            <div className="chat-input-area__buttons">
              <VoiceInput onResult={handleVoiceResult} disabled={loading} />
              <ImageInput onSelect={handleImageSelect} disabled={loading} />
              <button onClick={handleSend} disabled={loading} aria-label={loading ? 'Sending message' : 'Send message'}>
                {loading ? 'Sending...' : 'Send'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
