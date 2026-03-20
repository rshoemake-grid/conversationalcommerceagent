import { memo, useEffect, useRef } from 'react';
import type { Message } from '../api/types';

interface MessageListProps {
  messages: Message[];
  loading?: boolean;
  onRetry?: (messageText: string, errorId: string, imageBase64?: string) => void;
  onDismissError?: (messageId: string) => void;
}

function MessageListComponent({
  messages,
  loading = false,
  onRetry,
  onDismissError,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = bottomRef.current;
    if (el && typeof el.scrollIntoView === 'function') {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, loading]);

  return (
    <div className="message-list" role="log" aria-live="polite" aria-busy={loading}>
      {messages.length === 0 && (
        <div className="message-list__empty" aria-label="No messages yet">
          <p>Send a message to get started. Try asking about products or store hours.</p>
        </div>
      )}
      {loading && (
        <div className="message message--assistant message--loading" role="status" aria-live="polite">
          <div className="message__role">Assistant</div>
          <div className="message__content message__content--loading">
            <span className="message__spinner" aria-hidden="true" />
            <span>Searching...</span>
          </div>
        </div>
      )}
      {messages.map((msg, idx) => (
        <div
          key={msg.id}
          className={`message message--${msg.role}${msg.isError ? ' message--error' : ''}`}
          role={msg.isError ? 'alert' : undefined}
        >
          <div className="message__role">
            {msg.role === 'user'
              ? 'You'
              : msg.isError
                ? 'Error'
                : msg.source === 'app'
                  ? 'Application'
                  : 'Assistant'}
            {msg.role === 'assistant' && !msg.isError && msg.source === 'agent' && (
              <span className="message__source" title="Conversational Commerce agent">
                {' '}(agent)
              </span>
            )}
          </div>
          {msg.imageUri && msg.role === 'user' && (
            <div className="message__media">
              <img src={msg.imageUri} alt="Attached" className="message__image" />
            </div>
          )}
          <div className="message__content">{msg.content}</div>
          {msg.isError && (onRetry || onDismissError) && (
            <div className="message__actions">
              {onRetry && (
                <button
                  type="button"
                  className="message__action message__action--retry"
                  onClick={() => {
                    const prevUser = messages[idx - 1];
                    if (prevUser?.role === 'user')
                      onRetry(prevUser.content, msg.id, prevUser.imageBase64);
                  }}
                  aria-label="Retry sending message"
                >
                  Retry
                </button>
              )}
              {onDismissError && (
                <button
                  type="button"
                  className="message__action message__action--dismiss"
                  onClick={() => onDismissError(msg.id)}
                  aria-label="Dismiss error"
                >
                  Dismiss
                </button>
              )}
            </div>
          )}
          {msg.products && msg.products.length > 0 && (
            <div className="message__products">
              <h4>Products</h4>
              <div className="product-grid">
                {msg.products.map((p, idx) => (
                  <div key={p.id || `p-${idx}`} className="product-card">
                    {p.imageUri && (
                      <img src={p.imageUri} alt={p.title} className="product-card__image" />
                    )}
                    <div className="product-card__title">{p.title}</div>
                    {p.description && (
                      <div className="product-card__desc">{p.description}</div>
                    )}
                    {p.price && (
                      <div className="product-card__price">{p.price}</div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      ))}
      <div ref={bottomRef} aria-hidden="true" />
    </div>
  );
}

export const MessageList = memo(MessageListComponent);
