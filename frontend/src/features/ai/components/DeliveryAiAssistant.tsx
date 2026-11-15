import { useState, useRef, useEffect, type FormEvent } from 'react';
import { sendAiMessage, type AiChatMessage, type AiChatResponse } from '../api/aiApi';
import logoLight from '../../../assets/brand/logo-mark-light.svg';
import { Icon } from '../../../shared/ui/Icon';
import { RichText } from './RichText';
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
  'What are my active deliveries?',
  'What items can I not send?',
  'How fast is Express delivery?',
];

const MAX_HISTORY = 10;
export const MAX_MESSAGE_CHARS = 500;

// Event handlers create message ids; a counter keeps render pure (no Date.now() during render)
let messageCounter = 0;
const newId = (prefix: string) => `${prefix}-${++messageCounter}`;

export function DeliveryAiAssistant() {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | undefined>(undefined);
  const [messages, setMessages] = useState<MessageItem[]>([
    {
      id: 'welcome',
      role: 'model',
      content: "Hi, I'm the SwiftDrop assistant. Ask me about delivery prices, where a parcel is, or what you can send.",
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
      id: newId('user'),
      role: 'user',
      content: query,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      // Build history for context (exclude welcome message)
      // Only the last 10 messages (the server keeps no more): smaller, cheaper requests
      const history: AiChatMessage[] = messages
        .filter((m) => m.id !== 'welcome' && !m.id.startsWith('error'))
        .slice(-MAX_HISTORY)
        .map((m) => ({ role: m.role, content: m.content }));

      const res: AiChatResponse = await sendAiMessage(query, history, conversationId);
      setConversationId(res.conversationId);

      const aiMsg: MessageItem = {
        id: newId('ai'),
        role: 'model',
        content: res.reply,
        toolsUsed: res.toolsUsed,
        timestamp: res.timestamp ? new Date(res.timestamp) : new Date(),
      };

      setMessages((prev) => [...prev, aiMsg]);
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Something went wrong. Please try again shortly.';
      setMessages((prev) => [
        ...prev,
        {
          id: newId('error'),
          role: 'model',
          content: `⚠️ ${errMsg}`,
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

  const started = messages.some((m) => m.role === 'user');

  return (
    <div className={s.floatingContainer}>
      {!isOpen && (
        <button type="button" className={s.launcherButton} onClick={() => setIsOpen(true)}
          aria-label="Open the SwiftDrop assistant">
          <span className={s.launcherIcon}><Icon name="sparkle" size={18} /></span>
          Ask SwiftDrop
        </button>
      )}

      {isOpen && (
        <section className={s.chatWindow} role="dialog" aria-label="SwiftDrop assistant">
          <header className={s.chatHeader}>
            <span className={s.botAvatar}><img src={logoLight} width={22} height={22} alt="" /></span>
            <div className={s.headerText}>
              <h3>SwiftDrop Assistant</h3>
              <span className={s.headerStatus}><span className={s.onlineDot} />Prices, tracking and help</span>
            </div>
            <button type="button" className={s.closeButton} onClick={() => setIsOpen(false)} aria-label="Close the assistant">
              <Icon name="close" size={18} />
            </button>
          </header>

          <div className={s.messageArea} aria-live="polite">
            {messages.map((m) => (
              <div key={m.id} className={`${s.messageRow} ${m.role === 'user' ? s.fromUser : s.fromBot}`}>
                <div className={`${s.messageBubble} ${m.role === 'user' ? s.userMessage : s.aiMessage}`}>
                  {m.role === 'user' ? m.content : <RichText text={m.content} className={s.richText} />}
                  {m.toolsUsed && m.toolsUsed.length > 0 && (
                    <span className={s.toolBadge}><Icon name="check" size={12} />Checked live SwiftDrop data</span>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div className={`${s.messageRow} ${s.fromBot}`}>
                <div className={s.typingIndicator} aria-label="The assistant is typing">
                  <span className={s.typingDot} /><span className={s.typingDot} /><span className={s.typingDot} />
                </div>
              </div>
            )}

            {!started && (
              <div className={s.quickPrompts}>
                <span className={s.quickLabel}>Try asking</span>
                {QUICK_PROMPTS.map((prompt) => (
                  <button key={prompt} type="button" className={s.promptChip} onClick={() => void handleSend(prompt)} disabled={loading}>
                    {prompt}
                  </button>
                ))}
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <form className={s.inputArea} onSubmit={onSubmit}>
            <input
              type="text"
              className={s.chatInput}
              placeholder="Ask about prices, tracking, rules..."
              aria-label="Message to the SwiftDrop assistant"
              maxLength={MAX_MESSAGE_CHARS}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              disabled={loading}
              autoFocus
            />
            <button type="submit" className={s.sendButton} disabled={loading || !input.trim()} aria-label="Send message">
              <Icon name="send" size={18} />
            </button>
          </form>
          <p className={s.disclaimer}>AI answers can be wrong. Check prices before you book.</p>
        </section>
      )}
    </div>
  );
}

