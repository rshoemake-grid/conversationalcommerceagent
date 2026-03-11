export type OrchestrationMode = 'convo_commerce' | 'adk_orchestrator';

export interface ChatRequest {
  mode: OrchestrationMode;
  message: string;
  conversationId?: string;
  sessionId?: string;
}

export interface ProductDto {
  id: string;
  title: string;
  description: string;
  price: string;
  imageUri?: string;
}

export interface ChatResponse {
  text: string;
  conversationId?: string;
  refinedQuery?: string;
  products?: ProductDto[];
}
