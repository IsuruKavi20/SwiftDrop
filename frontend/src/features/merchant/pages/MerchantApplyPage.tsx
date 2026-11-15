import { useEffect, useState, type FormEvent } from 'react';
import userIcon from '../../../assets/auth/icon-user.svg';
import mailIcon from '../../../assets/auth/icon-mail.svg';
import { navigate, routes } from '../../../app/router';
import { refresh } from '../../auth/api/authApi';
import { AuthLayout } from '../../auth/components/AuthLayout';
import { Alert, PrimaryButton, TextField } from '../../auth/components/FormControls';
import {
  getMerchantApplication,
  submitMerchantApplication,
  type MerchantApplicationResponse,
} from '../api/merchantApplicationApi';
import { MerchantApiError } from '../api/merchantApi';

export function MerchantApplyPage() {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [application, setApplication] = useState<MerchantApplicationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Form fields
  const [businessName, setBusinessName] = useState('');
  const [registrationNumber, setRegistrationNumber] = useState('');
  const [website, setWebsite] = useState('');

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const existing = await getMerchantApplication();
        if (!cancelled && existing) {
          setApplication(existing);
        }
      } catch (err) {
        if (!cancelled && err instanceof MerchantApiError && err.status !== 404) {
          setError(err.message);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleApply(e: FormEvent) {
    e.preventDefault();
    if (!businessName.trim() || !registrationNumber.trim()) {
      setError('Business name and business registration number are required.');
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const res = await submitMerchantApplication({
        businessName,
        registrationNumber,
        website: website.trim() ? website.trim() : undefined,
      });
      setApplication(res);
    } catch (err) {
      if (err instanceof MerchantApiError) {
        setError(err.message);
      } else {
        setError('Failed to submit application. Please check your connection and try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleOpenDashboard() {
    setLoading(true);
    try {
      // Rotate token so the new MERCHANT role and merchant:api-access entitlement are loaded
      await refresh();
      navigate(routes.merchantDashboard);
    } catch {
      navigate(routes.merchantDashboard);
    }
  }

  if (loading) {
    return (
      <AuthLayout
        headline="Sell with SwiftDrop."
        tagline="Deliveries for your business."
        eyebrow="MERCHANTS"
        title="Merchant Application"
        subtitle="Loading your merchant application status…"
      >
        <p>Checking existing applications…</p>
      </AuthLayout>
    );
  }

  // Screen 2: Status View
  if (application) {
    if (application.status === 'APPROVED') {
      return (
        <AuthLayout
          headline="Welcome to SwiftDrop."
          tagline="Scale your business courier deliveries."
          eyebrow="APPLICATION APPROVED"
          title="You're a merchant!"
          subtitle="Your merchant account has been approved by our operations team."
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <Alert kind="success">
              Congratulations! Your business <strong>{application.businessName}</strong> is registered as a verified
              SwiftDrop Merchant.
            </Alert>
            <p>You now have full access to the Merchant Dashboard, store deliveries, and courier logistics API.</p>
            <PrimaryButton type="button" onClick={() => void handleOpenDashboard()}>
              Open Merchant Dashboard
            </PrimaryButton>
          </div>
        </AuthLayout>
      );
    }

    if (application.status === 'REJECTED') {
      return (
        <AuthLayout
          headline="Merchant Portal."
          tagline="Deliveries for your business."
          eyebrow="APPLICATION UPDATE"
          title="Application Not Approved"
          subtitle="We reviewed your merchant application."
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <Alert kind="error">
              {application.rejectionReason
                ? `Reason: ${application.rejectionReason}`
                : 'Your merchant application could not be approved at this time.'}
            </Alert>
            <p>You can apply again after 7 days or contact SwiftDrop support for assistance.</p>
            <button
              type="button"
              style={{
                padding: '0.6rem 1rem',
                border: '1px solid var(--merchant-line, #ccc)',
                borderRadius: '0.5rem',
                background: 'var(--color-surface, #fff)',
                cursor: 'pointer',
                fontWeight: 600,
              }}
              onClick={() => setApplication(null)}
            >
              Submit New Application
            </button>
          </div>
        </AuthLayout>
      );
    }

    // PENDING state
    return (
      <AuthLayout
        headline="Sell with SwiftDrop."
        tagline="Deliveries for your business."
        eyebrow="APPLICATION UNDER REVIEW"
        title="We're reviewing your application"
        subtitle="Our team is verifying your business details."
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div
            style={{
              padding: '1rem',
              borderRadius: '0.5rem',
              background: 'var(--color-page, #f8f9fa)',
              border: '1px solid var(--merchant-line, #e2e8f0)',
            }}
          >
            <p style={{ margin: '0.25rem 0' }}>
              <strong>Business:</strong> {application.businessName}
            </p>
            <p style={{ margin: '0.25rem 0' }}>
              <strong>Registration #:</strong> {application.registrationNumber}
            </p>
            {application.website && (
              <p style={{ margin: '0.25rem 0' }}>
                <strong>Website:</strong> {application.website}
              </p>
            )}
            <p style={{ margin: '0.25rem 0', color: 'var(--color-text-muted, #64748b)' }}>
              <strong>Status:</strong> PENDING APPROVAL
            </p>
          </div>
          <p>Reviews typically take 1–2 business days. Once approved, you can immediately access the merchant portal.</p>
          <button
            type="button"
            style={{
              padding: '0.6rem 1rem',
              border: '1px solid var(--merchant-line, #ccc)',
              borderRadius: '0.5rem',
              background: 'var(--color-surface, #fff)',
              cursor: 'pointer',
              fontWeight: 600,
            }}
            onClick={async () => {
              setLoading(true);
              const updated = await getMerchantApplication().catch(() => null);
              setApplication(updated);
              setLoading(false);
            }}
          >
            Check Status Again
          </button>
        </div>
      </AuthLayout>
    );
  }

  // Screen 1: Application Form
  return (
    <AuthLayout
      headline="Sell with SwiftDrop."
      tagline="Deliveries for your business."
      eyebrow="MERCHANTS"
      title="Become a merchant"
      subtitle="Register your business to ship parcels at discounted merchant rates."
    >
      <form onSubmit={handleApply} noValidate style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {error && <Alert kind="error">{error}</Alert>}

        <TextField
          id="businessName"
          label="Legal Business Name"
          icon={userIcon}
          placeholder="e.g. Ceylon Tea House"
          required
          maxLength={150}
          value={businessName}
          onChange={(e) => setBusinessName(e.target.value)}
          disabled={submitting}
        />

        <TextField
          id="registrationNumber"
          label="Business Registration Number"
          icon={userIcon}
          placeholder="e.g. PV12345 or BRN-8921"
          required
          maxLength={50}
          value={registrationNumber}
          onChange={(e) => setRegistrationNumber(e.target.value)}
          disabled={submitting}
        />

        <TextField
          id="website"
          label="Business Website or Online Store (Optional)"
          icon={mailIcon}
          placeholder="https://ceylontea.lk"
          maxLength={255}
          value={website}
          onChange={(e) => setWebsite(e.target.value)}
          disabled={submitting}
        />

        <div style={{ marginTop: '0.5rem' }}>
          <PrimaryButton type="submit" disabled={submitting}>
            {submitting ? 'Submitting Application…' : 'Submit Application'}
          </PrimaryButton>
        </div>
      </form>
    </AuthLayout>
  );
}
