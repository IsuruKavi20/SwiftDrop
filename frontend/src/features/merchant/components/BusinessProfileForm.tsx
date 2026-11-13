import { useState, type FormEvent } from 'react';
import { navigate, routes } from '../../../app/router';
import { Alert } from '../../auth/components/FormControls';
import {
  MerchantApiError,
  updateMerchantProfile,
  type MerchantResponse,
  type MerchantUpdateRequest,
} from '../api/merchantApi';
import styles from './MerchantProfile.module.css';

interface FormState {
  businessName: string;
  businessEmail: string;
  contactNumber: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  province: string;
  postalCode: string;
  country: string;
}

interface BusinessProfileFormProps {
  initialProfile: MerchantResponse;
  onProfileUpdated: (updated: MerchantResponse) => void;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function BusinessProfileForm({ initialProfile, onProfileUpdated }: BusinessProfileFormProps) {
  const [formData, setFormData] = useState<FormState>({
    businessName: initialProfile.businessName ?? '',
    businessEmail: initialProfile.businessEmail ?? '',
    contactNumber: initialProfile.contactNumber ?? '',
    addressLine1: initialProfile.addressLine1 ?? '',
    addressLine2: initialProfile.addressLine2 ?? '',
    city: initialProfile.city ?? '',
    province: initialProfile.province ?? '',
    postalCode: initialProfile.postalCode ?? '',
    country: initialProfile.country ?? '',
  });

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const updateField = (field: keyof FormState) => (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setFormData((prev) => ({ ...prev, [field]: value }));
    // Clear field-level error on change
    if (fieldErrors[field]) {
      setFieldErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
    if (generalError) setGeneralError(null);
    if (successMessage) setSuccessMessage(null);
  };

  function validateClient(): Record<string, string> {
    const errors: Record<string, string> = {};

    if (!formData.businessName.trim()) {
      errors.businessName = 'Business name is required.';
    } else if (formData.businessName.length > 150) {
      errors.businessName = 'Business name cannot exceed 150 characters.';
    }

    if (!formData.businessEmail.trim()) {
      errors.businessEmail = 'Business email is required.';
    } else if (!EMAIL_PATTERN.test(formData.businessEmail.trim())) {
      errors.businessEmail = 'Please provide a valid email address.';
    } else if (formData.businessEmail.length > 150) {
      errors.businessEmail = 'Business email cannot exceed 150 characters.';
    }

    if (!formData.contactNumber.trim()) {
      errors.contactNumber = 'Contact phone number is required.';
    } else if (formData.contactNumber.length > 20) {
      errors.contactNumber = 'Contact number cannot exceed 20 characters.';
    }

    if (!formData.addressLine1.trim()) {
      errors.addressLine1 = 'Address line 1 is required.';
    } else if (formData.addressLine1.length > 255) {
      errors.addressLine1 = 'Address line 1 cannot exceed 255 characters.';
    }

    if (formData.addressLine2 && formData.addressLine2.length > 255) {
      errors.addressLine2 = 'Address line 2 cannot exceed 255 characters.';
    }

    if (!formData.city.trim()) {
      errors.city = 'City is required.';
    } else if (formData.city.length > 100) {
      errors.city = 'City cannot exceed 100 characters.';
    }

    if (formData.province && formData.province.length > 100) {
      errors.province = 'Province cannot exceed 100 characters.';
    }

    if (formData.postalCode && formData.postalCode.length > 20) {
      errors.postalCode = 'Postal code cannot exceed 20 characters.';
    }

    if (!formData.country.trim()) {
      errors.country = 'Country is required.';
    } else if (formData.country.length > 100) {
      errors.country = 'Country cannot exceed 100 characters.';
    }

    return errors;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setGeneralError(null);
    setSuccessMessage(null);

    const clientErrors = validateClient();
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      setGeneralError('Please correct the highlighted fields before saving.');
      return;
    }

    setSaving(true);

    const requestPayload: MerchantUpdateRequest = {
      businessName: formData.businessName.trim(),
      businessEmail: formData.businessEmail.trim(),
      contactNumber: formData.contactNumber.trim(),
      addressLine1: formData.addressLine1.trim(),
      addressLine2: formData.addressLine2.trim() || null,
      city: formData.city.trim(),
      province: formData.province.trim() || null,
      postalCode: formData.postalCode.trim() || null,
      country: formData.country.trim(),
    };

    try {
      const updated = await updateMerchantProfile(requestPayload);
      onProfileUpdated(updated);
      setSuccessMessage('Business profile updated successfully.');
      setFieldErrors({});
    } catch (err) {
      if (err instanceof MerchantApiError) {
        if (err.status === 401) {
          navigate(routes.login);
          return;
        }
        if (err.status === 403) {
          setGeneralError('Access forbidden. You do not have permission to update this merchant profile.');
          return;
        }
        setGeneralError(err.message);
        if (Object.keys(err.fieldErrors).length > 0) {
          setFieldErrors(err.fieldErrors);
        }
      } else {
        setGeneralError('An unexpected error occurred while saving. Please try again.');
      }
    } finally {
      setSaving(false);
    }
  }

  const isComplete = initialProfile.complete;

  return (
    <section className={styles.card} aria-labelledby="profile-title">
      <div className={styles.headerRow}>
        <div className={styles.titleArea}>
          <h2 id="profile-title" className={styles.title}>Business Profile</h2>
          <p className={styles.subtitle}>
            Trading details and dispatch contact for your merchant account
          </p>
        </div>
        <div>
          {isComplete ? (
            <span className={styles.badgeComplete} role="status">
              ● Profile complete
            </span>
          ) : (
            <span className={styles.badgeIncomplete} role="status">
              ● Profile incomplete
            </span>
          )}
        </div>
      </div>

      <div className={styles.noticeBox}>
        <strong>Note on profile status:</strong> Completed trading details ensure accurate sender information on delivery waybills. Profile completion is separate from administrative role approval, which is managed by your account administrator.
      </div>

      {generalError && <Alert kind="error">{generalError}</Alert>}
      {successMessage && <Alert kind="success">{successMessage}</Alert>}

      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {/* Read-only Owner Information */}
        <div className={styles.fieldGroup}>
          <label htmlFor="ownerName" className={styles.label}>
            Account Owner
          </label>
          <input
            id="ownerName"
            type="text"
            className={`${styles.input} ${styles.inputReadOnly}`}
            value={initialProfile.ownerName || '—'}
            readOnly
            disabled
          />
        </div>

        {/* Business Name & Business Email */}
        <div className={`${styles.fieldGrid} ${styles.fieldGrid2}`}>
          <div className={styles.fieldGroup}>
            <label htmlFor="businessName" className={styles.label}>
              Trading / Business Name *
            </label>
            <input
              id="businessName"
              type="text"
              required
              maxLength={150}
              className={`${styles.input} ${fieldErrors.businessName ? styles.inputError : ''}`}
              placeholder="e.g. Ceylon Tea House"
              value={formData.businessName}
              onChange={updateField('businessName')}
              aria-invalid={Boolean(fieldErrors.businessName)}
              aria-describedby={fieldErrors.businessName ? 'err-businessName' : undefined}
            />
            {fieldErrors.businessName && (
              <span id="err-businessName" className={styles.fieldError}>
                {fieldErrors.businessName}
              </span>
            )}
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="businessEmail" className={styles.label}>
              Business Email *
            </label>
            <input
              id="businessEmail"
              type="email"
              required
              maxLength={150}
              className={`${styles.input} ${fieldErrors.businessEmail ? styles.inputError : ''}`}
              placeholder="e.g. orders@ceylontea.lk"
              value={formData.businessEmail}
              onChange={updateField('businessEmail')}
              aria-invalid={Boolean(fieldErrors.businessEmail)}
              aria-describedby={fieldErrors.businessEmail ? 'err-businessEmail' : undefined}
            />
            {fieldErrors.businessEmail && (
              <span id="err-businessEmail" className={styles.fieldError}>
                {fieldErrors.businessEmail}
              </span>
            )}
          </div>
        </div>

        {/* Contact Phone & Address Line 1 */}
        <div className={`${styles.fieldGrid} ${styles.fieldGrid2}`}>
          <div className={styles.fieldGroup}>
            <label htmlFor="contactNumber" className={styles.label}>
              Contact Phone Number *
            </label>
            <input
              id="contactNumber"
              type="text"
              required
              maxLength={20}
              className={`${styles.input} ${fieldErrors.contactNumber ? styles.inputError : ''}`}
              placeholder="e.g. 0112345678"
              value={formData.contactNumber}
              onChange={updateField('contactNumber')}
              aria-invalid={Boolean(fieldErrors.contactNumber)}
              aria-describedby={fieldErrors.contactNumber ? 'err-contactNumber' : undefined}
            />
            {fieldErrors.contactNumber && (
              <span id="err-contactNumber" className={styles.fieldError}>
                {fieldErrors.contactNumber}
              </span>
            )}
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="addressLine1" className={styles.label}>
              Address Line 1 *
            </label>
            <input
              id="addressLine1"
              type="text"
              required
              maxLength={255}
              className={`${styles.input} ${fieldErrors.addressLine1 ? styles.inputError : ''}`}
              placeholder="e.g. 12 Galle Road"
              value={formData.addressLine1}
              onChange={updateField('addressLine1')}
              aria-invalid={Boolean(fieldErrors.addressLine1)}
              aria-describedby={fieldErrors.addressLine1 ? 'err-addressLine1' : undefined}
            />
            {fieldErrors.addressLine1 && (
              <span id="err-addressLine1" className={styles.fieldError}>
                {fieldErrors.addressLine1}
              </span>
            )}
          </div>
        </div>

        {/* Address Line 2 */}
        <div className={styles.fieldGroup}>
          <label htmlFor="addressLine2" className={styles.label}>
            Address Line 2 <span className={styles.optionalTag}>(Optional)</span>
          </label>
          <input
            id="addressLine2"
            type="text"
            maxLength={255}
            className={`${styles.input} ${fieldErrors.addressLine2 ? styles.inputError : ''}`}
            placeholder="e.g. Suite 4B, 2nd Floor"
            value={formData.addressLine2}
            onChange={updateField('addressLine2')}
            aria-invalid={Boolean(fieldErrors.addressLine2)}
            aria-describedby={fieldErrors.addressLine2 ? 'err-addressLine2' : undefined}
          />
          {fieldErrors.addressLine2 && (
            <span id="err-addressLine2" className={styles.fieldError}>
              {fieldErrors.addressLine2}
            </span>
          )}
        </div>

        {/* City, Province, Postal Code */}
        <div className={`${styles.fieldGrid} ${styles.fieldGrid3}`}>
          <div className={styles.fieldGroup}>
            <label htmlFor="city" className={styles.label}>
              City *
            </label>
            <input
              id="city"
              type="text"
              required
              maxLength={100}
              className={`${styles.input} ${fieldErrors.city ? styles.inputError : ''}`}
              placeholder="e.g. Colombo"
              value={formData.city}
              onChange={updateField('city')}
              aria-invalid={Boolean(fieldErrors.city)}
              aria-describedby={fieldErrors.city ? 'err-city' : undefined}
            />
            {fieldErrors.city && (
              <span id="err-city" className={styles.fieldError}>
                {fieldErrors.city}
              </span>
            )}
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="province" className={styles.label}>
              Province / State <span className={styles.optionalTag}>(Optional)</span>
            </label>
            <input
              id="province"
              type="text"
              maxLength={100}
              className={`${styles.input} ${fieldErrors.province ? styles.inputError : ''}`}
              placeholder="e.g. Western"
              value={formData.province}
              onChange={updateField('province')}
              aria-invalid={Boolean(fieldErrors.province)}
              aria-describedby={fieldErrors.province ? 'err-province' : undefined}
            />
            {fieldErrors.province && (
              <span id="err-province" className={styles.fieldError}>
                {fieldErrors.province}
              </span>
            )}
          </div>

          <div className={styles.fieldGroup}>
            <label htmlFor="postalCode" className={styles.label}>
              Postal Code <span className={styles.optionalTag}>(Optional)</span>
            </label>
            <input
              id="postalCode"
              type="text"
              maxLength={20}
              className={`${styles.input} ${fieldErrors.postalCode ? styles.inputError : ''}`}
              placeholder="e.g. 00300"
              value={formData.postalCode}
              onChange={updateField('postalCode')}
              aria-invalid={Boolean(fieldErrors.postalCode)}
              aria-describedby={fieldErrors.postalCode ? 'err-postalCode' : undefined}
            />
            {fieldErrors.postalCode && (
              <span id="err-postalCode" className={styles.fieldError}>
                {fieldErrors.postalCode}
              </span>
            )}
          </div>
        </div>

        {/* Country */}
        <div className={styles.fieldGroup}>
          <label htmlFor="country" className={styles.label}>
            Country *
          </label>
          <input
            id="country"
            type="text"
            required
            maxLength={100}
            className={`${styles.input} ${fieldErrors.country ? styles.inputError : ''}`}
            placeholder="e.g. Sri Lanka"
            value={formData.country}
            onChange={updateField('country')}
            aria-invalid={Boolean(fieldErrors.country)}
            aria-describedby={fieldErrors.country ? 'err-country' : undefined}
          />
          {fieldErrors.country && (
            <span id="err-country" className={styles.fieldError}>
              {fieldErrors.country}
            </span>
          )}
        </div>

        {/* Actions */}
        <div className={styles.buttonRow}>
          <button
            type="submit"
            className={styles.primaryButton}
            disabled={saving}
          >
            {saving ? 'Saving changes…' : isComplete ? 'Update Business Profile' : 'Complete Business Profile'}
          </button>
        </div>
      </form>

      <div className={styles.metaRow}>
        <span>Merchant ID: #{initialProfile.id}</span>
        {initialProfile.updatedAt && (
          <span>
            Last updated: {new Date(initialProfile.updatedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
          </span>
        )}
      </div>
    </section>
  );
}
