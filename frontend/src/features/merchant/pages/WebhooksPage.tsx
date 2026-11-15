import { useEffect, useState, type FormEvent } from 'react';
import {
  createMerchantWebhook,
  deleteMerchantWebhook,
  getMerchantWebhooks,
  getWebhookLogs,
  testMerchantWebhook,
  updateMerchantWebhook,
  type CreateWebhookInput,
  type WebhookConfig,
  type WebhookDeliveryLog,
} from '../api/merchantWebhooksApi';
import { MerchantApiError } from '../api/merchantApi';
import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import shared from './MerchantPages.module.css';
import extra from './StoreOrdersPage.module.css';

const AVAILABLE_EVENTS = [
  { key: '*', label: 'All Events (*)' },
  { key: 'DELIVERY_STATUS_CHANGED', label: 'Delivery Status Changed' },
  { key: 'DELIVERY_CONFIRMED', label: 'Delivery Confirmed' },
  { key: 'DELIVERY_PICKED_UP', label: 'Delivery Picked Up' },
  { key: 'DELIVERY_IN_TRANSIT', label: 'Delivery In Transit' },
  { key: 'DELIVERY_DELIVERED', label: 'Delivery Delivered' },
  { key: 'DELIVERY_FAILED', label: 'Delivery Failed' },
  { key: 'DELIVERY_CANCELLED', label: 'Delivery Cancelled' },
];

export function WebhooksPage({ preview }: { preview: boolean }) {
  const [webhooks, setWebhooks] = useState<WebhookConfig[]>([]);
  const [loading, setLoading] = useState(!preview);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // Modal / Form state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingWebhook, setEditingWebhook] = useState<WebhookConfig | null>(null);
  const [endpointUrl, setEndpointUrl] = useState('');
  const [selectedEvents, setSelectedEvents] = useState<string[]>(['*']);
  const [secretKey, setSecretKey] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // Test Ping state
  const [testingId, setTestingId] = useState<number | null>(null);
  const [testResult, setTestResult] = useState<WebhookDeliveryLog | null>(null);

  // Logs viewer state
  const [viewingLogsConfig, setViewingLogsConfig] = useState<WebhookConfig | null>(null);
  const [logs, setLogs] = useState<WebhookDeliveryLog[]>([]);
  const [loadingLogs, setLoadingLogs] = useState(false);

  useEffect(() => {
    if (preview) return;

    let active = true;

    getMerchantWebhooks()
      .then((data) => {
        if (active) {
          setWebhooks(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (active) {
          setError(
            err instanceof MerchantApiError
              ? err.message
              : 'Failed to load webhook configurations. Please try again.'
          );
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [preview, refreshKey]);

  function handleRetry() {
    setLoading(true);
    setError(null);
    setRefreshKey((k) => k + 1);
  }

  function openCreateModal() {
    setEditingWebhook(null);
    setEndpointUrl('');
    setSelectedEvents(['*']);
    setSecretKey('');
    setFormError(null);
    setIsModalOpen(true);
  }

  function openEditModal(config: WebhookConfig) {
    setEditingWebhook(config);
    setEndpointUrl(config.endpointUrl);
    setSelectedEvents(config.subscribedEvents.split(',').map((e) => e.trim()));
    setSecretKey(config.secretKey);
    setFormError(null);
    setIsModalOpen(true);
  }

  async function handleSaveWebhook(e: FormEvent) {
    e.preventDefault();
    if (!endpointUrl.trim()) {
      setFormError('Endpoint URL is required.');
      return;
    }
    if (selectedEvents.length === 0) {
      setFormError('Select at least one event subscription.');
      return;
    }

    if (preview) {
      setFormError('Webhook operations are simulated in development preview mode.');
      return;
    }

    setIsSubmitting(true);
    setFormError(null);

    const subscribedEvents = selectedEvents.includes('*') ? '*' : selectedEvents.join(',');

    try {
      if (editingWebhook) {
        await updateMerchantWebhook(editingWebhook.id, {
          endpointUrl: endpointUrl.trim(),
          subscribedEvents,
          secretKey: secretKey.trim() || undefined,
        });
      } else {
        const input: CreateWebhookInput = {
          endpointUrl: endpointUrl.trim(),
          subscribedEvents,
          secretKey: secretKey.trim() || undefined,
        };
        await createMerchantWebhook(input);
      }
      setIsModalOpen(false);
      setLoading(true);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      if (err instanceof MerchantApiError) {
        setFormError(err.message);
      } else {
        setFormError('Failed to save webhook. Ensure the URL is valid and publicly accessible.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleToggleActive(config: WebhookConfig) {
    if (preview) return;
    try {
      const updated = await updateMerchantWebhook(config.id, {
        endpointUrl: config.endpointUrl,
        subscribedEvents: config.subscribedEvents,
        active: !config.active,
      });
      setWebhooks((prev) => prev.map((w) => (w.id === updated.id ? updated : w)));
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to toggle active state');
    }
  }

  async function handleDeleteWebhook(id: number) {
    if (preview) {
      alert('Delete simulated in preview mode.');
      return;
    }
    if (!window.confirm('Are you sure you want to delete this webhook endpoint?')) return;

    try {
      await deleteMerchantWebhook(id);
      setWebhooks((prev) => prev.filter((w) => w.id !== id));
      if (viewingLogsConfig?.id === id) {
        setViewingLogsConfig(null);
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete webhook.');
    }
  }

  async function handleTestPing(config: WebhookConfig) {
    if (preview) {
      alert('Test ping simulated in preview mode.');
      return;
    }
    setTestingId(config.id);
    try {
      const result = await testMerchantWebhook(config.id);
      setTestResult(result);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Test ping request failed');
    } finally {
      setTestingId(null);
    }
  }

  async function handleViewLogs(config: WebhookConfig) {
    setViewingLogsConfig(config);
    if (preview) {
      setLogs([]);
      return;
    }
    setLoadingLogs(true);
    try {
      const result = await getWebhookLogs(config.id);
      setLogs(result);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to retrieve delivery logs');
    } finally {
      setLoadingLogs(false);
    }
  }

  function handleEventToggle(key: string) {
    if (key === '*') {
      setSelectedEvents(['*']);
    } else {
      let next = selectedEvents.filter((k) => k !== '*');
      if (next.includes(key)) {
        next = next.filter((k) => k !== key);
      } else {
        next.push(key);
      }
      setSelectedEvents(next.length === 0 ? ['*'] : next);
    }
  }

  return (
    <section className={shared.panel} aria-labelledby="webhooks-title">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h2 id="webhooks-title">Merchant Webhooks</h2>
          <p>Receive real-time HTTP POST notifications when shipments are updated, dispatched, or delivered.</p>
        </div>
        <button type="button" className={shared.button} onClick={openCreateModal}>
          + Register Webhook
        </button>
      </div>

      {error && (
        <div className={shared.state} style={{ marginBottom: '1rem' }}>
          <p className={shared.error}>{error}</p>
          <button type="button" className={shared.button} onClick={handleRetry}>
            Retry
          </button>
        </div>
      )}

      {/* Test Ping Result Modal / Banner */}
      {testResult && (
        <div
          role="alert"
          style={{
            margin: '1.5rem 0',
            padding: '1rem 1.25rem',
            borderRadius: '0.5rem',
            background: testResult.status === 'SUCCESS' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)',
            border: `1px solid ${testResult.status === 'SUCCESS' ? '#22c55e' : '#ef4444'}`,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h4 style={{ margin: 0, color: testResult.status === 'SUCCESS' ? '#166534' : '#991b1b' }}>
              Test Ping Result: {testResult.status} {testResult.httpStatusCode ? `(HTTP ${testResult.httpStatusCode})` : ''}
            </h4>
            <button
              type="button"
              className={shared.button}
              style={{ minHeight: '1.8rem', padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}
              onClick={() => setTestResult(null)}
            >
              Dismiss
            </button>
          </div>
          {testResult.errorMessage && (
            <p style={{ margin: '0.5rem 0 0', color: '#991b1b', fontSize: '0.875rem' }}>
              Error: {testResult.errorMessage}
            </p>
          )}
        </div>
      )}

      {/* Webhook Form Modal */}
      {isModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            margin: '1.5rem 0',
            padding: '1.25rem',
            borderRadius: '0.75rem',
            background: 'var(--color-page, #f8f9fa)',
            border: '1px solid var(--merchant-line, #e2e8f0)',
          }}
        >
          <h3 style={{ marginTop: 0 }}>
            {editingWebhook ? 'Edit Webhook Endpoint' : 'Register New Webhook Endpoint'}
          </h3>
          <form onSubmit={handleSaveWebhook} style={{ display: 'grid', gap: '1rem' }}>
            {formError && <p className={shared.error}>{formError}</p>}

            <label style={{ display: 'grid', gap: '0.35rem', fontWeight: 600 }}>
              <span>Endpoint URL (HTTP or HTTPS)</span>
              <input
                type="url"
                required
                maxLength={500}
                placeholder="https://api.yourdomain.com/webhooks/swiftdrop"
                value={endpointUrl}
                onChange={(e) => setEndpointUrl(e.target.value)}
                disabled={isSubmitting}
                style={{
                  padding: '0.55rem 0.75rem',
                  border: '1px solid var(--merchant-line)',
                  borderRadius: '0.5rem',
                }}
              />
              <span style={{ fontSize: '0.8rem', color: 'var(--merchant-secondary)', fontWeight: 400 }}>
                Must be a publicly accessible endpoint. Localhost and private IP addresses are blocked.
              </span>
            </label>

            <div>
              <span style={{ fontWeight: 600, display: 'block', marginBottom: '0.5rem' }}>Subscribed Events</span>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.5rem' }}>
                {AVAILABLE_EVENTS.map((ev) => (
                  <label key={ev.key} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.9rem' }}>
                    <input
                      type="checkbox"
                      checked={selectedEvents.includes(ev.key)}
                      onChange={() => handleEventToggle(ev.key)}
                      disabled={isSubmitting}
                    />
                    {ev.label}
                  </label>
                ))}
              </div>
            </div>

            <label style={{ display: 'grid', gap: '0.35rem', fontWeight: 600 }}>
              <span>HMAC Secret Key (Optional)</span>
              <input
                type="text"
                maxLength={128}
                placeholder="Leave blank to automatically generate a secure 64-char key"
                value={secretKey}
                onChange={(e) => setSecretKey(e.target.value)}
                disabled={isSubmitting}
                style={{
                  padding: '0.55rem 0.75rem',
                  border: '1px solid var(--merchant-line)',
                  borderRadius: '0.5rem',
                  fontFamily: 'monospace',
                }}
              />
              <span style={{ fontSize: '0.8rem', color: 'var(--merchant-secondary)', fontWeight: 400 }}>
                Used to compute the HMAC-SHA256 signature in the <code>X-SwiftDrop-Signature</code> header.
              </span>
            </label>

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
              <button type="submit" className={shared.button} disabled={isSubmitting}>
                {isSubmitting ? 'Saving…' : editingWebhook ? 'Update Webhook' : 'Register Webhook'}
              </button>
              <button
                type="button"
                className={shared.button}
                disabled={isSubmitting}
                onClick={() => setIsModalOpen(false)}
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Webhooks Table */}
      <div className={extra.previewTableWrapper} style={{ marginTop: '1.5rem' }}>
        <table className={extra.previewTable}>
          <thead>
            <tr>
              <th>Status</th>
              <th>Endpoint URL</th>
              <th>Events</th>
              <th>Secret Key</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', padding: '1.5rem' }}>
                  Loading webhooks…
                </td>
              </tr>
            ) : preview ? (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', padding: '1.5rem' }}>
                  Development preview: Webhooks are managed in live mode.
                </td>
              </tr>
            ) : webhooks.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--merchant-secondary)' }}>
                  No webhooks registered yet. Click "+ Register Webhook" to set one up.
                </td>
              </tr>
            ) : (
              webhooks.map((w) => (
                <tr key={w.id}>
                  <td>
                    <button
                      type="button"
                      className={extra.statusBadge}
                      style={{
                        cursor: 'pointer',
                        border: 'none',
                        background: w.active ? 'rgba(34, 197, 94, 0.2)' : 'rgba(100, 116, 139, 0.2)',
                        color: w.active ? '#166534' : '#475569',
                      }}
                      onClick={() => void handleToggleActive(w)}
                      title="Click to toggle active status"
                    >
                      {w.active ? 'Active' : 'Disabled'}
                    </button>
                  </td>
                  <td style={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{w.endpointUrl}</td>
                  <td style={{ fontSize: '0.85rem' }}>{w.subscribedEvents}</td>
                  <td>
                    <code style={{ fontSize: '0.8rem' }}>
                      {w.secretKey.substring(0, 8)}…{w.secretKey.substring(w.secretKey.length - 6)}
                    </code>
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                      <button
                        type="button"
                        className={shared.button}
                        style={{ minHeight: '1.8rem', padding: '0.2rem 0.5rem', fontSize: '0.75rem' }}
                        disabled={testingId === w.id}
                        onClick={() => void handleTestPing(w)}
                      >
                        {testingId === w.id ? 'Pinging…' : 'Test Ping'}
                      </button>
                      <button
                        type="button"
                        className={shared.button}
                        style={{ minHeight: '1.8rem', padding: '0.2rem 0.5rem', fontSize: '0.75rem' }}
                        onClick={() => void handleViewLogs(w)}
                      >
                        Logs
                      </button>
                      <button
                        type="button"
                        className={shared.button}
                        style={{ minHeight: '1.8rem', padding: '0.2rem 0.5rem', fontSize: '0.75rem' }}
                        onClick={() => openEditModal(w)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className={shared.button}
                        style={{ minHeight: '1.8rem', padding: '0.2rem 0.5rem', fontSize: '0.75rem', color: '#b91c1c' }}
                        onClick={() => void handleDeleteWebhook(w.id)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Logs Drawer / Section */}
      {viewingLogsConfig && (
        <div style={{ marginTop: '2.5rem', borderTop: '1px solid var(--merchant-line)', paddingTop: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h3>Recent Delivery Logs</h3>
              <p style={{ margin: 0, fontFamily: 'monospace', fontSize: '0.85rem' }}>
                Endpoint: {viewingLogsConfig.endpointUrl}
              </p>
            </div>
            <button
              type="button"
              className={shared.button}
              style={{ minHeight: '2rem', padding: '0.25rem 0.6rem' }}
              onClick={() => setViewingLogsConfig(null)}
            >
              Close Logs
            </button>
          </div>

          <div className={extra.previewTableWrapper} style={{ marginTop: '1rem' }}>
            <table className={extra.previewTable}>
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Event</th>
                  <th>Status</th>
                  <th>HTTP Code</th>
                  <th>Attempts</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {loadingLogs ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', padding: '1rem' }}>
                      Loading logs…
                    </td>
                  </tr>
                ) : logs.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', padding: '1rem', color: 'var(--merchant-secondary)' }}>
                      No delivery events recorded yet for this webhook.
                    </td>
                  </tr>
                ) : (
                  logs.map((log) => (
                    <tr key={log.id}>
                      <td style={{ fontSize: '0.8rem' }}>{new Date(log.createdAt).toLocaleString()}</td>
                      <td><code>{log.eventType}</code></td>
                      <td>
                        <span
                          className={extra.statusBadge}
                          style={{
                            background:
                              log.status === 'SUCCESS'
                                ? 'rgba(34, 197, 94, 0.2)'
                                : 'rgba(239, 68, 68, 0.2)',
                            color: log.status === 'SUCCESS' ? '#166534' : '#991b1b',
                          }}
                        >
                          {log.status}
                        </span>
                      </td>
                      <td>{log.httpStatusCode ?? '—'}</td>
                      <td>{log.attemptCount}</td>
                      <td style={{ fontSize: '0.8rem', color: log.errorMessage ? '#b91c1c' : 'inherit' }}>
                        {log.errorMessage || 'Delivered successfully'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Signature Verification Guide */}
      <div style={{ marginTop: '2.5rem', borderTop: '1px solid var(--merchant-line)', paddingTop: '1.5rem' }}>
        <h3>Signature Verification</h3>
        <p>SwiftDrop signs all webhook payloads with HMAC-SHA256 using your webhook secret key:</p>
        <pre
          style={{
            padding: '1rem',
            background: 'var(--color-page, #f8f9fa)',
            borderRadius: '0.5rem',
            overflowX: 'auto',
            fontSize: '0.85rem',
            lineHeight: 1.5,
          }}
        >
          {`const crypto = require('crypto');

function verifySwiftDropWebhook(rawPayload, signatureHeader, secretKey) {
  const expected = crypto.createHmac('sha256', secretKey).update(rawPayload).digest('hex');
  return crypto.timingSafeEqual(Buffer.from(signatureHeader), Buffer.from(expected));
}`}
        </pre>
      </div>

      <div style={{ marginTop: '2rem' }}>
        <MerchantLink className={shared.button} href={merchantPath('dashboard', preview)}>
          ← Back to Dashboard
        </MerchantLink>
      </div>
    </section>
  );
}
