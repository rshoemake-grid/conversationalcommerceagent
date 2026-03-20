export type OrchestrationMode = 'convo_commerce' | 'adk_orchestrator';

export interface ProductDto {
  id: string;
  title: string;
  description: string;
  price: string;
  imageUri?: string;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  products?: ProductDto[];
  isError?: boolean;
  /** Image data URL for display (e.g. data:image/jpeg;base64,...) */
  imageUri?: string;
  /** Raw base64 for retry when message had image */
  imageBase64?: string;
  /** "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback */
  source?: 'agent' | 'app';
}

export interface ChatRequest {
  mode: OrchestrationMode;
  message: string;
  conversationId?: string;
  sessionId?: string;
  /** Optional base64-encoded image (with or without data URL prefix) for visual search */
  imageBase64?: string;
}

export interface ChatResponse {
  text: string;
  conversationId?: string;
  refinedQuery?: string;
  products?: ProductDto[];
  /** "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback */
  source?: 'agent' | 'app';
}
