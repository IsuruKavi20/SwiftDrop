import { useEffect, useState, type FormEvent } from 'react';
import {
  createMerchantClient,
  deleteMerchantClient,
  getMerchantClients,
  type CreatedMerchantClient,
  type MerchantClient,
} from '../api/merchantClientsApi';
import { MerchantApiError } from '../api/merchantApi';
import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import shared from './MerchantPages.module.css';
import extra from './StoreOrdersPage.module.css';

export function ApiCredentialsPage({ preview }: { preview: boolean }) {
  const [clients, setClients] = useState<MerchantClient[]>([]);
  const [loading, setLoading] = useState(!preview);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // Creation modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [clientName, setClientName] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // One-time secret display state
  const [createdClient, setCreatedClient] = useState<CreatedMerchantClient | null>(null);
  const [secretCopied, setSecretCopied] = useState(false);

  // Revoke state
  const [revokingId, setRevokingId] = useState<string | null>(null);

  useEffect(() => {
    if (preview) return;

    let active = true;

    getMerchantClients()
      .then((data) => {
        if (active) {
          setClients(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (active) {
          setError(
            err instanceof MerchantApiError
              ? err.message
              : 'Failed to load API credentials. Please try again.'
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

  async function handleCreateClient(e: FormEvent) {
    e.preventDefault();
    if (!clientName.trim()) {
      setModalError('Client name is required.');
      return;
    }

    if (preview) {
      setModalError('API key generation is simulated in development preview mode.');
      return;
    }

    setIsCreating(true);
    setModalError(null);

    try {
      const result = await createMerchantClient({ name: clientName.trim() });
      setCreatedClient(result);
      setClientName('');
      setIsModalOpen(false);
      // Refresh list
      setLoading(true);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      if (err instanceof MerchantApiError) {
        setModalError(err.message);
      } else {
        setModalError('Failed to create API key. Please check your network connection.');
      }
    } finally {
      setIsCreating(false);
    }
  }

  async function handleRevokeClient(clientId: string) {
    if (preview) {
      alert('Revocation is simulated in development preview mode.');
      return;
    }

    if (!window.confirm('Are you sure you want to revoke this API credential? Connected applications will lose access immediately.')) {
      return;
    }

    setRevokingId(clientId);
    try {
      await deleteMerchantClient(clientId);
      setClients((prev) => prev.filter((c) => c.clientId !== clientId));
      setError(null);
    } catch (err) {
      setError(
        err instanceof MerchantApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : 'Failed to revoke API key.'
      );
    } finally {
      setRevokingId(null);
    }
  }

  function handleCopySecret() {
    if (createdClient) {
      void navigator.clipboard.writeText(createdClient.clientSecret);
      setSecretCopied(true);
      setTimeout(() => setSecretCopied(false), 2500);
    }
  }

  return (
    <section className={shared.panel} aria-labelledby="api-credentials-title">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h2 id="api-credentials-title">API Credentials</h2>
          <p>Manage machine-to-machine API keys for your ecommerce platform, POS, or ERP integration.</p>
        </div>
        <button
          type="button"
          className={shared.button}
          onClick={() => {
            setIsModalOpen(true);
            setModalError(null);
          }}
        >
          + Generate New API Key
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

      {/* One-time secret reveal card */}
      {createdClient && (
        <div
          role="alert"
          style={{
            margin: '1.5rem 0',
            padding: '1.5rem',
            borderRadius: '0.75rem',
            background: 'var(--color-surface, #fff)',
            border: '2px solid var(--merchant-orange, #e06500)',
          }}
        >
          <h3 style={{ margin: '0 0 0.5rem', color: 'var(--merchant-orange, #e06500)' }}>
            ⚠️ Save Your Client Secret Now
          </h3>
          <p style={{ margin: '0.25rem 0 1rem' }}>
            This secret will <strong>never be shown again</strong>. If you lose it, you will need to delete this key and generate a new one.
          </p>

          <div style={{ display: 'grid', gap: '0.75rem', marginBottom: '1rem' }}>
            <div>
              <span style={{ fontSize: '0.8rem', color: 'var(--merchant-secondary)' }}>Client ID</span>
              <div style={{ fontFamily: 'monospace', fontWeight: 600, padding: '0.5rem', background: 'var(--color-page)', borderRadius: '0.4rem' }}>
                {createdClient.clientId}
              </div>
            </div>
            <div>
              <span style={{ fontSize: '0.8rem', color: 'var(--merchant-secondary)' }}>Client Secret</span>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                <div style={{ flex: 1, fontFamily: 'monospace', fontWeight: 700, padding: '0.5rem', background: 'var(--color-page)', borderRadius: '0.4rem', color: '#b91c1c' }}>
                  {createdClient.clientSecret}
                </div>
                <button
                  type="button"
                  className={shared.button}
                  style={{ minHeight: '2.4rem', padding: '0.4rem 0.8rem' }}
                  onClick={handleCopySecret}
                >
                  {secretCopied ? '✓ Copied' : 'Copy Secret'}
                </button>
              </div>
            </div>
          </div>

          <button
            type="button"
            className={shared.button}
            onClick={() => setCreatedClient(null)}
          >
            I Have Saved My Secret
          </button>
        </div>
      )}

      {/* API Key Creation Form Modal */}
      {isModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-key-modal-title"
          style={{
            margin: '1.5rem 0',
            padding: '1.25rem',
            borderRadius: '0.75rem',
            background: 'var(--color-page, #f8f9fa)',
            border: '1px solid var(--merchant-line, #e2e8f0)',
          }}
        >
          <h3 id="new-key-modal-title" style={{ marginTop: 0 }}>Create New API Client Key</h3>
          <form onSubmit={handleCreateClient} style={{ display: 'grid', gap: '1rem' }}>
            {modalError && <p className={shared.error}>{modalError}</p>}
            <label style={{ display: 'grid', gap: '0.35rem', fontWeight: 600 }}>
              <span>Application or Key Name</span>
              <input
                type="text"
                required
                maxLength={100}
                placeholder="e.g. WooCommerce Store Plugin or Custom ERP"
                value={clientName}
                onChange={(e) => setClientName(e.target.value)}
                disabled={isCreating}
                style={{
                  padding: '0.55rem 0.75rem',
                  border: '1px solid var(--merchant-line)',
                  borderRadius: '0.5rem',
                }}
              />
            </label>
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button type="submit" className={shared.button} disabled={isCreating}>
                {isCreating ? 'Generating…' : 'Generate Key'}
              </button>
              <button
                type="button"
                className={shared.button}
                disabled={isCreating}
                onClick={() => setIsModalOpen(false)}
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Existing Credentials Table */}
      <div className={extra.previewTableWrapper} style={{ marginTop: '1.5rem' }}>
        <table className={extra.previewTable}>
          <thead>
            <tr>
              <th>Client Name</th>
              <th>Client ID</th>
              <th>Created Date</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={4} style={{ textAlign: 'center', padding: '1.5rem' }}>
                  Loading API credentials…
                </td>
              </tr>
            ) : preview ? (
              <tr>
                <td colSpan={4} style={{ textAlign: 'center', padding: '1.5rem' }}>
                  Development preview: API keys are managed in live mode.
                </td>
              </tr>
            ) : clients.length === 0 ? (
              <tr>
                <td colSpan={4} style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--merchant-secondary)' }}>
                  No API credentials generated yet. Click "Generate New API Key" above.
                </td>
              </tr>
            ) : (
              clients.map((c) => (
                <tr key={c.clientId}>
                  <td style={{ fontWeight: 600 }}>{c.name}</td>
                  <td style={{ fontFamily: 'monospace' }}>{c.clientId}</td>
                  <td>{c.createdAt ? new Date(c.createdAt).toLocaleDateString() : '—'}</td>
                  <td>
                    <button
                      type="button"
                      className={shared.button}
                      style={{
                        minHeight: '2rem',
                        padding: '0.2rem 0.6rem',
                        fontSize: '0.8rem',
                        color: '#b91c1c',
                      }}
                      disabled={revokingId === c.clientId}
                      onClick={() => void handleRevokeClient(c.clientId)}
                    >
                      {revokingId === c.clientId ? 'Revoking…' : 'Revoke'}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Integration Guide Section */}
      <div style={{ marginTop: '2.5rem', borderTop: '1px solid var(--merchant-line)', paddingTop: '1.5rem' }}>
        <h3>How to Authenticate Your Merchant Software</h3>
        <p>Your server application requests a Bearer token using OAuth2 Client Credentials flow:</p>
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
          {`POST /oauth2/token HTTP/1.1
Host: auth.swiftdrop.lk
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials`}
        </pre>
        <p>The response contains your access token:</p>
        <pre
          style={{
            padding: '1rem',
            background: 'var(--color-page, #f8f9fa)',
            borderRadius: '0.5rem',
            overflowX: 'auto',
            fontSize: '0.85rem',
          }}
        >
          {`{
  "access_token": "eyJhbGciOi...",
  "token_type": "Bearer",
  "expires_in": 900
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
