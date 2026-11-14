import { env } from '../../../config/env';
import { getAccessToken } from '../../auth/lib/session';

export interface AiChatMessage {
  role: 'user' | 'model';
  content: string;
}

export interface AiChatResponse {
  reply: string;
  conversationId: string;
  toolsUsed: string[];
  metadata: Record<string, unknown>;
  timestamp: string;
}

export interface McpToolDefinition {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
}

export async function sendAiMessage(
  message: string,
  history: AiChatMessage[] = [],
  conversationId?: string
): Promise<AiChatResponse> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${env.apiUrl}/api/v1/ai/chat`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      message,
      conversationId,
      history,
    }),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`AI Service error (${response.status}): ${errorBody || response.statusText}`);
  }

  return response.json();
}

export async function fetchMcpTools(): Promise<McpToolDefinition[]> {
  const response = await fetch(`${env.apiUrl}/api/v1/ai/mcp/tools`);
  if (!response.ok) {
    throw new Error('Failed to load MCP tools');
  }
  return response.json();
}

