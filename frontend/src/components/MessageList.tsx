import type { ProductDto } from '../api/types';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  products?: ProductDto[];
}

interface MessageListProps {
  messages: Message[];
}

export function MessageList({ messages }: MessageListProps) {
  return (
    <div className="message-list">
      {messages.map((msg) => (
        <div key={msg.id} className={`message message--${msg.role}`}>
          <div className="message__role">{msg.role === 'user' ? 'You' : 'Assistant'}</div>
          <div className="message__content">{msg.content}</div>
          {msg.products && msg.products.length > 0 && (
            <div className="message__products">
              <h4>Products</h4>
              <div className="product-grid">
                {msg.products.map((p) => (
                  <div key={p.id} className="product-card">
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
    </div>
  );
}
