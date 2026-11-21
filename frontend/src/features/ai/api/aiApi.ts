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

/** A short message people can understand (the server's own text for limits and bad input; never raw error bodies). */
async function friendlyError(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string };
    if ((response.status === 429 || response.status === 400) && problem.detail) {
      return problem.detail;
    }
  } catch {
    // not JSON: fall through to the general message
  }
  return 'The assistant is not available right now. Please try again shortly.';
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

  const response = await fetch(`${env.apiUrl}/api/public/ai/chat`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      message,
      conversationId,
      history,
    }),
  });

  if (!response.ok) {
    throw new Error(await friendlyError(response));
  }

  return response.json();
}

export async function fetchMcpTools(): Promise<McpToolDefinition[]> {
  const response = await fetch(`${env.apiUrl}/api/public/ai/mcp/tools`);
  if (!response.ok) {
    throw new Error('Failed to load MCP tools');
  }
  return response.json();
}

