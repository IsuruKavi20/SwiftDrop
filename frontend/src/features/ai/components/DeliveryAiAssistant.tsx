import { useState, useRef, useEffect, type FormEvent } from 'react';
import { sendAiMessage, type AiChatMessage, type AiChatResponse } from '../api/aiApi';
import s from './DeliveryAiAssistant.module.css';

interface MessageItem {
  id: string;
  role: 'user' | 'model';
  content: string;
  toolsUsed?: string[];
  timestamp: Date;
}

const QUICK_PROMPTS = [
  'How much to send 2 kg to Kandy?',
  'Where is my parcel SD-DEMO-TEST?',
  'What are my active deliveries?',
  'What items are prohibited from sending?',
];

export function DeliveryAiAssistant() {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>(undefined);
  const [messages, setMessages] = useState<MessageItem[]>([
    {
      id: 'welcome',
      role: 'model',
      content: "Hello! 👋 I'm **SwiftDrop AI**, your delivery assistant.\n\nAsk me anything about your shipments, delivery rates (e.g. *\"How much to send 2 kg to Kandy?\"*), live parcel tracking, or courier policies!",
      timestamp: new Date(),
    },
  ]);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen]);

  const handleSend = async (textToSend?: string) => {
    const query = (textToSend || input).trim();
    if (!query || loading) return;

    const userMsg: MessageItem = {
      id: 'user-' + Date.now(),
      role: 'user',
      content: query,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      // Build history for context (exclude welcome message)
      const history: AiChatMessage[] = messages
        .filter((m) => m.id !== 'welcome')
        .map((m) => ({ role: m.role, content: m.content }));

      const res: AiChatResponse = await sendAiMessage(query, history, conversationId);
      setConversationId(res.conversationId);

      const aiMsg: MessageItem = {
        id: 'ai-' + Date.now(),
        role: 'model',
        content: res.reply,
        toolsUsed: res.toolsUsed,
        timestamp: new Date(res.timestamp || Date.now()),
      };

      setMessages((prev) => [...prev, aiMsg]);
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Something went wrong';
      setMessages((prev) => [
        ...prev,
        {
          id: 'error-' + Date.now(),
          role: 'model',
          content: `⚠️ Sorry, I encountered an issue: ${errMsg}. Please try again shortly.`,
          timestamp: new Date(),
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    void handleSend();
  };

  return (
    <div className={s.floatingContainer}>
      {!isOpen && (
        <button
          type="button"
          className={s.launcherButton}
          onClick={() => setIsOpen(true)}
          aria-label="Open AI Delivery Assistant"
        >
          <span className={s.launcherSparkle}>✨</span>
          <span>Ask SwiftDrop AI</span>
        </button>
      )}

      {isOpen && (
        <div className={s.chatWindow} role="dialog" aria-label="SwiftDrop AI Chat">
          {/* Header */}
          <div className={s.chatHeader}>
            <div className={s.headerTitleGroup}>
              <div className={s.botAvatar}>S</div>
              <div className={s.headerText}>
                <h3>SwiftDrop AI Assistant</h3>
                <div className={s.headerStatus}>
                  <span className={s.onlineDot} />
                  <span>Powered by Gemini & MCP Tools</span>
                </div>
              </div>
            </div>
            <button
              type="button"
              className={s.closeButton}
              onClick={() => setIsOpen(false)}
              aria-label="Close chat"
            >
              ✕
            </button>
          </div>

          {/* Messages */}
          <div className={s.messageArea}>
            {messages.map((m) => (
              <div
                key={m.id}
                className={`${s.messageBubble} ${m.role === 'user' ? s.userMessage : s.aiMessage}`}
              >
                {m.toolsUsed && m.toolsUsed.length > 0 && (
                  <div>
                    {m.toolsUsed.map((tool) => (
                      <span key={tool} className={s.toolBadge}>
                        ⚡ Tool: {tool}
                      </span>
                    ))}
                  </div>
                )}
                <div className={s.aiText}>{m.content}</div>
              </div>
            ))}

            {loading && (
              <div className={s.typingIndicator}>
                <span className={s.typingDot} />
                <span className={s.typingDot} />
                <span className={s.typingDot} />
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick suggestions */}
          <div className={s.quickPrompts}>
            {QUICK_PROMPTS.map((prompt) => (
              <button
                key={prompt}
                type="button"
                className={s.promptChip}
                onClick={() => void handleSend(prompt)}
                disabled={loading}
              >
                {prompt}
              </button>
            ))}
          </div>

          {/* Input Form */}
          <form className={s.inputArea} onSubmit={onSubmit}>
            <input
              type="text"
              className={s.chatInput}
              placeholder="Ask about tracking, shipping rates..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              disabled={loading}
              autoFocus
            />
            <button
              type="submit"
              className={s.sendButton}
              disabled={loading || !input.trim()}
              aria-label="Send message"
            >
              ➔
            </button>
          </form>
        </div>
      )}
    </div>
  );
}

