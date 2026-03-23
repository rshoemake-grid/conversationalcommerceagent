export type OrchestrationMode = 'convo_commerce' | 'adk_orchestrator';

export interface ProductDto {
  id: string;
  title: string;
  description: string;
  price: string;
  imageUri?: string;
  /** UPC/GTIN for product lookup */
  gtin?: string;
  /** Short product id (final component of name) */
  productId?: string;
  categories?: string[];
  brands?: string[];
  uri?: string;
  availability?: string;
  sizes?: string[];
  materials?: string[];
  /** Custom attributes (key -> value) */
  attributes?: Record<string, unknown>;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  products?: ProductDto[];
  /** Quick-reply options from GCP suggestedAnswers: displayText for UI, value sent to API */
  suggestedAnswers?: SuggestedAnswer[];
  isError?: boolean;
  /** Image data URL for display (e.g. data:image/jpeg;base64,...) */
  imageUri?: string;
  /** Raw base64 for retry when message had image */
  imageBase64?: string;
  /** "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback */
  source?: 'agent' | 'app';
  /** GCP query classification, e.g. SIMPLE_PRODUCT_SEARCH, GENERAL_QUESTION */
  queryType?: string;
}

export interface ChatRequest {
  mode: OrchestrationMode;
  message: string;
  conversationId?: string;
  sessionId?: string;
  /** Optional base64-encoded image (with or without data URL prefix) for visual search */
  imageBase64?: string;
  /** Max suggested answers to request (null/omit = no limit). Display is also sliced client-side for real-time control. */
  maxSuggestedAnswers?: number;
  /** Previous assistant text (for SIMPLE_PRODUCT_SEARCH no-products fallback when user retries a suggested answer) */
  previousAssistantText?: string;
  /** Previous assistant suggested answers (for no-products fallback) */
  previousSuggestedAnswers?: SuggestedAnswer[];
}

export interface ChatResponse {
  text: string;
  conversationId?: string;
  refinedQuery?: string;
  products?: ProductDto[];
  /** "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback */
  source?: 'agent' | 'app';
  /** GCP query classification, e.g. SIMPLE_PRODUCT_SEARCH, GENERAL_QUESTION */
  queryType?: string;
  /** Raw JSON response from GCP API (REST transport only) */
  rawResponse?: string;
  /** Quick-reply options from GCP suggestedAnswers: displayText for UI, value sent to API */
  suggestedAnswers?: SuggestedAnswer[];
}

/** Display text for UI; value is sent to API when user selects */
export interface SuggestedAnswer {
  displayText: string;
  value: string;
}
