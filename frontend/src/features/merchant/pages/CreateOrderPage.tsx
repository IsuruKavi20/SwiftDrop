import { useState, useEffect, type ChangeEvent, type FormEvent } from 'react';
import { MerchantIcon } from '../components/MerchantIcon';
import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import {
  createMerchantDelivery,
  estimateMerchantDelivery,
  formatMerchantFee,
  type CreateMerchantDeliveryInput,
  type DeliveryType,
  type MerchantDeliveryDetail,
  type MerchantDeliveryEstimate,
} from '../api/merchantOrdersApi';
import { getMerchantProfile, MerchantApiError, type MerchantProfile } from '../api/merchantApi';
import { SRI_LANKA_DISTRICTS, resolveDistrict } from '../utils/districtUtils';
import styles from './MerchantOrders.module.css';
import shared from './MerchantPages.module.css';
import extra from './CreateOrderPage.module.css';

const PREVIEW_PROFILE: MerchantProfile = {
  id: 1,
  complete: true,
  ownerName: 'Dilhan Fernando',
  businessName: 'Ceylon Tea Exports',
  businessEmail: 'orders@ceylontea.lk',
  contactNumber: '+94112345678',
  addressLine1: '12 Flower Road',
  addressLine2: null,
  city: 'Colombo',
  province: 'Western',
  postalCode: '00700',
  country: 'Sri Lanka',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const addressFields = [
  { name: 'street', label: 'Street address', max: 255 },
  { name: 'city', label: 'City', max: 100 },
  { name: 'district', label: 'District', max: 100 },
  { name: 'postalCode', label: 'Postal code', max: 15, optional: true },
];

const contactFields = [
  { name: 'contactName', label: 'Contact name', max: 100 },
  { name: 'contactPhone', label: 'Phone number', phone: true },
];

const parcelFields = [
  { name: 'description', label: 'Description', max: 255 },
  { name: 'weightKg', label: 'Weight (kg)', digits: 8 },
  { name: 'lengthCm', label: 'Length (cm)', digits: 8 },
  { name: 'widthCm', label: 'Width (cm)', digits: 8 },
  { name: 'heightCm', label: 'Height (cm)', digits: 8 },
  { name: 'declaredValue', label: 'Declared value', digits: 10, optional: true },
];

type Field = { name: string; label: string; max?: number; optional?: boolean; phone?: boolean; email?: boolean; digits?: number };

function fieldError(field: Field, rawValue: string): string | undefined {
  const value = rawValue.trim();
  if (!value) return field.optional ? undefined : 'This field is required.';
  if (field.max && value.length > field.max) return `Use at most ${field.max} characters.`;
  if (field.phone && !/^\+?[0-9]{9,15}$/.test(value)) return 'Enter 9–15 digits, optionally starting with +.';
  if (field.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return 'Enter a valid email address.';
  if (field.digits) {
    const decimal = new RegExp(`^(?:[0-9]{1,${field.digits}}(?:\\.[0-9]{1,2})?|\\.[0-9]{1,2})$`);
    if (!decimal.test(value)) return `Use up to ${field.digits} digits before the decimal and up to 2 after it.`;
    if (!field.optional && Number(value) < 0.01) return 'Enter a value of at least 0.01.';
  }
}

interface CsvRow {
  rowIndex: number;
  idempotencyKey?: string;
  data: Record<string, string>;
  errors: string[];
  status: 'valid' | 'invalid' | 'pending' | 'success' | 'failed';
  trackingNumber?: string;
  estimatedFee?: number | null;
  errorMessage?: string;
}

const CSV_HEADERS = [
  'deliveryType',
  'pickupContactName',
  'pickupPhone',
  'pickupStreet',
  'pickupCity',
  'pickupDistrict',
  'pickupPostalCode',
  'destContactName',
  'destPhone',
  'destStreet',
  'destCity',
  'destDistrict',
  'destPostalCode',
  'parcelDescription',
  'weightKg',
  'lengthCm',
  'widthCm',
  'heightCm',
  'declaredValue',
  'specialInstructions',
];

const CSV_SAMPLE_ROW = [
  'EXPRESS',
  'Colombo Central Hub',
  '+94112345678',
  '123 Galle Road',
  'Colombo',
  'Colombo',
  '00300',
  'Priyantha Fernando',
  '+94771234567',
  '45 Kandy Road',
  'Peradeniya',
  'Kandy',
  '20400',
  'Handcrafted Tea Box (500g)',
  '0.75',
  '20.0',
  '15.0',
  '10.0',
  '3500.00',
  'Handle with care - fragile tea tin',
];

export function CreateOrderPage({ preview }: { preview: boolean }) {
  const [activeTab, setActiveTab] = useState<'single' | 'bulk'>('single');
  const [values, setValues] = useState<Record<string, string>>({ deliveryType: '' });
  const [parcelIds, setParcelIds] = useState([1]);
  const [nextParcelId, setNextParcelId] = useState(2);
  const [checked, setChecked] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [createdOrder, setCreatedOrder] = useState<MerchantDeliveryDetail | null>(null);
  const [copied, setCopied] = useState(false);
  const [pricing, setPricing] = useState(false);
  const [quote, setQuote] = useState<{ input: string; fee: MerchantDeliveryEstimate }>();
  const [quoteError, setQuoteError] = useState<string | null>(null);

  // Bulk CSV state
  const [csvRows, setCsvRows] = useState<CsvRow[]>([]);
  const [csvFileName, setCsvFileName] = useState('');
  const [csvError, setCsvError] = useState<string | null>(null);
  const [isReadingCsv, setIsReadingCsv] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [importProgress, setImportProgress] = useState({ current: 0, total: 0 });
  const [importSummary, setImportSummary] = useState<{ success: number; failed: number } | null>(null);

  // Business Profile auto-fill state
  const [businessProfile, setBusinessProfile] = useState<MerchantProfile | null>(preview ? PREVIEW_PROFILE : null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [, setProfileError] = useState<string | null>(null);
  const [autoFillNotice, setAutoFillNotice] = useState<{
    type: 'success' | 'warning' | 'error';
    message: string;
  } | null>(null);

  useEffect(() => {
    if (preview) return;

    let active = true;
    getMerchantProfile()
      .then((profile) => {
        if (active) {
          setBusinessProfile(profile);
          setProfileError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setProfileError(err instanceof Error ? err.message : 'Could not load business profile.');
        }
      });

    return () => {
      active = false;
    };
  }, [preview]);

  async function handleUseBusinessAddress() {
    let profile = businessProfile;
    if (!profile && !preview) {
      setProfileLoading(true);
      try {
        profile = await getMerchantProfile();
        setBusinessProfile(profile);
        setProfileError(null);
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Could not load business profile.';
        setProfileError(msg);
        setAutoFillNotice({
          type: 'error',
          message: 'Could not load business profile. Please enter pickup details manually.',
        });
        setProfileLoading(false);
        return;
      }
      setProfileLoading(false);
    }

    if (!profile) {
      setAutoFillNotice({
        type: 'error',
        message: 'Could not load business profile. Please enter pickup details manually.',
      });
      return;
    }

    const hasAnyField = Boolean(
      profile.businessName?.trim() ||
      profile.ownerName?.trim() ||
      profile.contactNumber?.trim() ||
      profile.addressLine1?.trim() ||
      profile.city?.trim()
    );

    if (!hasAnyField) {
      setAutoFillNotice({
        type: 'warning',
        message: 'Your business profile has no saved contact or address details.',
      });
      return;
    }

    const street = [profile.addressLine1?.trim(), profile.addressLine2?.trim()]
      .filter(Boolean)
      .join(', ');
    const city = profile.city?.trim() ?? '';
    const district = resolveDistrict(city, profile.province);
    const contactName = profile.businessName?.trim() || profile.ownerName?.trim() || '';
    const contactPhone = profile.contactNumber?.trim() || '';
    const postalCode = profile.postalCode?.trim() || '';

    setValues((previous) => ({
      ...previous,
      'pickup.contactName': contactName || previous['pickup.contactName'] || '',
      'pickup.contactPhone': contactPhone || previous['pickup.contactPhone'] || '',
      'pickup.street': street || previous['pickup.street'] || '',
      'pickup.city': city || previous['pickup.city'] || '',
      'pickup.district': district || '',
      'pickup.postalCode': postalCode || previous['pickup.postalCode'] || '',
    }));

    setChecked(false);
    setSubmitError(null);
    setQuoteError(null);

    const isIncomplete =
      !profile.complete ||
      !contactName ||
      !contactPhone ||
      !street ||
      !city ||
      !district;

    if (isIncomplete) {
      if (!district && city) {
        setAutoFillNotice({
          type: 'warning',
          message: 'Could not determine pickup district. Please select it manually.',
        });
      } else {
        setAutoFillNotice({
          type: 'warning',
          message: 'Business profile is incomplete. Populated available fields.',
        });
      }
    } else {
      setAutoFillNotice({
        type: 'success',
        message: 'Pickup details populated from business profile.',
      });
    }
  }

  const instructions = { name: 'specialInstructions', label: 'Special instructions', max: 500, optional: true };
  const groups = [
    { label: 'Recipient and delivery address', prefix: 'destination', fields: [...contactFields, { name: 'email', label: 'Recipient Email', email: true, max: 254, optional: true }, ...addressFields] },
    { label: 'Pickup information and address', prefix: 'pickup', fields: [...contactFields, ...addressFields] },
    ...parcelIds.map((id, index) => ({
      label: `Parcel ${index + 1}`,
      prefix: `parcel-${id}`,
      fields: parcelFields,
      parcelId: id,
    })),
  ];
  const fields = groups.flatMap((group) => group.fields.map((field) => ({ ...field, name: `${group.prefix}.${field.name}` })));
  const errors = Object.fromEntries([...fields, instructions].map((field) => [field.name, fieldError(field, values[field.name] ?? '')]));
  const deliveryTypeError = values.deliveryType ? undefined : 'Select a delivery service.';
  const firstError = deliveryTypeError ? 'deliveryType' : Object.keys(errors).find((key) => errors[key]);

  function change(name: string, value: string) {
    setValues((previous) => ({ ...previous, [name]: value }));
    setChecked(false);
    setSubmitError(null);
    setQuoteError(null);
  }

  function bookingInput(): CreateMerchantDeliveryInput {
    return {
      deliveryType: values.deliveryType as DeliveryType,
      pickup: {
        contactName: values['pickup.contactName']?.trim() ?? '',
        contactPhone: values['pickup.contactPhone']?.trim() ?? '',
        street: values['pickup.street']?.trim() ?? '',
        city: values['pickup.city']?.trim() ?? '',
        district: values['pickup.district']?.trim() ?? '',
        postalCode: values['pickup.postalCode']?.trim() || null,
      },
      destination: {
        contactName: values['destination.contactName']?.trim() ?? '',
        contactPhone: values['destination.contactPhone']?.trim() ?? '',
        street: values['destination.street']?.trim() ?? '',
        city: values['destination.city']?.trim() ?? '',
        district: values['destination.district']?.trim() ?? '',
        postalCode: values['destination.postalCode']?.trim() || null,
      },
      parcels: parcelIds.map((id) => ({
        description: values[`parcel-${id}.description`]?.trim() ?? '',
        weightKg: Number(values[`parcel-${id}.weightKg`]) || 1,
        lengthCm: Number(values[`parcel-${id}.lengthCm`]) || 10,
        widthCm: Number(values[`parcel-${id}.widthCm`]) || 10,
        heightCm: Number(values[`parcel-${id}.heightCm`]) || 10,
        declaredValue: values[`parcel-${id}.declaredValue`] ? Number(values[`parcel-${id}.declaredValue`]) : null,
      })),
      specialInstructions: values.specialInstructions?.trim() || null,
    };
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pricing || isSubmitting) return;
    setChecked(true);
    setSubmitError(null);
    setQuoteError(null);

    if (firstError) {
      const field = event.currentTarget.elements.namedItem(firstError);
      if (field instanceof HTMLElement) field.focus();
      return;
    }

    const estimateOnly = (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'estimate';
    if (preview) {
      if (estimateOnly) setQuoteError('Sign in as a Merchant to request an estimate.');
      else setSubmitError('Order submission is simulated in development preview mode.');
      return;
    }

    const payload = bookingInput();
    if (estimateOnly) {
      setPricing(true);
      setQuote(undefined);
      try {
        setQuote({ input: JSON.stringify(payload), fee: await estimateMerchantDelivery(payload) });
      } catch (error) {
        setQuoteError(error instanceof Error ? error.message : 'Could not estimate the delivery fee. Try again.');
      } finally {
        setPricing(false);
      }
      return;
    }
    try {
      setIsSubmitting(true);
      const recipientEmail = values['destination.email']?.trim();
      const result = await createMerchantDelivery({ ...payload, recipientEmail: recipientEmail || undefined });
      setCreatedOrder(result);
      setQuote(undefined);
      setValues({ deliveryType: '' });
      setParcelIds([1]);
      setChecked(false);
    } catch (err) {
      if (err instanceof MerchantApiError) {
        setSubmitError(err.message);
      } else {
        setSubmitError('Failed to create order. Please check your network connection and try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  function renderField(field: Field) {
    const error = checked ? errors[field.name] : undefined;
    const id = `order-${field.name}`;
    const isDistrict = field.name.endsWith('.district');
    return (
      <label key={field.name} htmlFor={id} className={field.email ? extra.fullWidth : undefined}>
        <span>
          {field.label}
          {field.optional && ' (optional)'}
        </span>
        <input
          id={id}
          name={field.name}
          type={field.email ? 'email' : field.phone ? 'tel' : 'text'}
          inputMode={field.digits ? 'decimal' : undefined}
          required={!field.optional}
          maxLength={field.max}
          list={isDistrict ? 'sri-lanka-district-options' : undefined}
          value={values[field.name] ?? ''}
          onChange={(event) => change(field.name, event.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={[field.email ? `${id}-help` : undefined, error ? `${id}-error` : undefined].filter(Boolean).join(' ') || undefined}
          disabled={isSubmitting}
        />
        {field.email && (
          <span className={extra.fieldHint} id={`${id}-help`}>Stored with this shipment for recipient notifications. Email delivery is not enabled yet.</span>
        )}
        {error && (
          <span className={styles.fieldError} id={`${id}-error`}>
            {error}
          </span>
        )}
      </label>
    );
  }

  function handleCopyTracking() {
    if (createdOrder) {
      void navigator.clipboard.writeText(createdOrder.trackingNumber);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  // Bulk CSV methods
  function downloadCsvTemplate() {
    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [CSV_HEADERS.join(','), CSV_SAMPLE_ROW.map((val) => `"${val.replace(/"/g, '""')}"`).join(',')].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', 'swiftdrop-bulk-template.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  function parseCsvLine(line: string): string[] {
    const result: string[] = [];
    let cur = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      if (c === '"') {
        if (inQuotes && line[i + 1] === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c === ',' && !inQuotes) {
        result.push(cur.trim());
        cur = '';
      } else {
        cur += c;
      }
    }
    result.push(cur.trim());
    return result;
  }

  function handleFileUpload(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || isImporting || isReadingCsv) return;

    e.target.value = '';
    setCsvFileName(file.name);
    setCsvError(null);
    setCsvRows([]);
    setImportSummary(null);
    setIsReadingCsv(true);
    const reader = new FileReader();
    reader.onload = (event) => {
      const text = String(event.target?.result ?? '').replace(/^\uFEFF/, '');
      setIsReadingCsv(false);

      const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
      if (lines.length < 2) {
        setCsvError('This CSV has no shipment rows. Add at least one shipment below the header and choose the file again.');
        return;
      }

      const headers = parseCsvLine(lines[0]);
      const rows: CsvRow[] = [];

      for (let i = 1; i < lines.length; i++) {
        const rowValues = parseCsvLine(lines[i]);
        const data: Record<string, string> = {};
        headers.forEach((h, idx) => {
          data[h] = rowValues[idx] ?? '';
        });

        // Validation
        const rowErrors: string[] = [];
        const type = data.deliveryType?.toUpperCase();
        if (!['STANDARD', 'EXPRESS', 'SAME_DAY'].includes(type)) {
          rowErrors.push('Invalid deliveryType (must be STANDARD, EXPRESS, or SAME_DAY)');
        }
        if (!data.pickupContactName) rowErrors.push('Missing pickupContactName');
        if (!data.pickupPhone) rowErrors.push('Missing pickupPhone');
        if (!data.pickupStreet) rowErrors.push('Missing pickupStreet');
        if (!data.pickupCity) rowErrors.push('Missing pickupCity');
        if (!data.pickupDistrict) rowErrors.push('Missing pickupDistrict');
        if (!data.destContactName) rowErrors.push('Missing destContactName');
        if (!data.destPhone) rowErrors.push('Missing destPhone');
        if (!data.destStreet) rowErrors.push('Missing destStreet');
        if (!data.destCity) rowErrors.push('Missing destCity');
        if (!data.destDistrict) rowErrors.push('Missing destDistrict');
        if (!data.parcelDescription) rowErrors.push('Missing parcelDescription');
        if (isNaN(Number(data.weightKg)) || Number(data.weightKg) <= 0) rowErrors.push('Invalid weightKg');
        if (isNaN(Number(data.lengthCm)) || Number(data.lengthCm) <= 0) rowErrors.push('Invalid lengthCm');
        if (isNaN(Number(data.widthCm)) || Number(data.widthCm) <= 0) rowErrors.push('Invalid widthCm');
        if (isNaN(Number(data.heightCm)) || Number(data.heightCm) <= 0) rowErrors.push('Invalid heightCm');

        rows.push({
          rowIndex: i,
          idempotencyKey: rowErrors.length === 0 ? crypto.randomUUID() : undefined,
          data,
          errors: rowErrors,
          status: rowErrors.length === 0 ? 'valid' : 'invalid',
        });
      }

      setCsvRows(rows);
      setImportSummary(null);
    };
    reader.onerror = reader.onabort = () => {
      setIsReadingCsv(false);
      setCsvError('Could not read this CSV. Choose the file again.');
    };
    reader.readAsText(file);
  }

  async function runBulkImport(onlyFailed = false) {
    if (isImporting || isReadingCsv) return;
    if (preview) {
      setCsvError('Sign in as a Merchant to book the shipments in this file.');
      return;
    }

    const rowsToProcess = csvRows.filter((r) =>
      r.idempotencyKey && (onlyFailed ? r.status === 'failed' : r.status === 'valid')
    );

    if (rowsToProcess.length === 0) return;

    setImportSummary(null);
    setIsImporting(true);
    setImportProgress({ current: 0, total: rowsToProcess.length });
    let successCount = 0;
    let failedCount = 0;

    for (let i = 0; i < rowsToProcess.length; i++) {
      const row = rowsToProcess[i];

      const payload: CreateMerchantDeliveryInput = {
        deliveryType: row.data.deliveryType.toUpperCase() as DeliveryType,
        pickup: {
          contactName: row.data.pickupContactName,
          contactPhone: row.data.pickupPhone,
          street: row.data.pickupStreet,
          city: row.data.pickupCity,
          district: row.data.pickupDistrict,
          postalCode: row.data.pickupPostalCode || null,
        },
        destination: {
          contactName: row.data.destContactName,
          contactPhone: row.data.destPhone,
          street: row.data.destStreet,
          city: row.data.destCity,
          district: row.data.destDistrict,
          postalCode: row.data.destPostalCode || null,
        },
        parcels: [
          {
            description: row.data.parcelDescription,
            weightKg: Number(row.data.weightKg),
            lengthCm: Number(row.data.lengthCm),
            widthCm: Number(row.data.widthCm),
            heightCm: Number(row.data.heightCm),
            declaredValue: row.data.declaredValue ? Number(row.data.declaredValue) : null,
          },
        ],
        specialInstructions: row.data.specialInstructions || null,
      };

      try {
        const res = await createMerchantDelivery(payload, row.idempotencyKey);
        successCount++;
        setCsvRows((prev) =>
          prev.map((r) =>
            r.rowIndex === row.rowIndex
              ? { ...r, status: 'success', trackingNumber: res.trackingNumber, estimatedFee: res.estimatedFee, errorMessage: undefined }
              : r
          )
        );
      } catch (err) {
        failedCount++;
        const msg = err instanceof Error ? err.message : 'Booking failed';
        setCsvRows((prev) =>
          prev.map((r) =>
            r.rowIndex === row.rowIndex ? { ...r, status: 'failed', errorMessage: msg } : r
          )
        );
      }
      setImportProgress({ current: i + 1, total: rowsToProcess.length });
    }

    setIsImporting(false);
    setImportSummary({ success: successCount, failed: failedCount });
  }

  const validCount = csvRows.filter(({ status }) => status === 'valid').length;
  const invalidCount = csvRows.filter(({ status }) => status === 'invalid').length;
  const failedCount = csvRows.filter(({ status }) => status === 'failed').length;
  const currentQuote = quote?.input === JSON.stringify(bookingInput()) ? quote?.fee : undefined;

  return (
    <section className={extra.page} aria-labelledby="create-order-title">
      <div className={extra.tabs} role="tablist" aria-label="Order creation methods" onKeyDown={(event) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const target = event.key === 'Home' ? 'single' : event.key === 'End' ? 'bulk'
          : activeTab === 'single' ? 'bulk' : 'single';
        setActiveTab(target);
        event.currentTarget.querySelectorAll('button')[target === 'single' ? 0 : 1]?.focus();
      }}>
        <button
          type="button"
          role="tab"
          id="single-shipment-tab"
          aria-controls="single-shipment"
          aria-selected={activeTab === 'single'}
          tabIndex={activeTab === 'single' ? 0 : -1}
          className={`${extra.tabButton} ${activeTab === 'single' ? extra.tabButtonActive : ''}`}
          onClick={() => setActiveTab('single')}
        >
          Single Shipment
        </button>
        <button
          type="button"
          role="tab"
          id="bulk-import-tab"
          aria-controls="bulk-import"
          aria-selected={activeTab === 'bulk'}
          tabIndex={activeTab === 'bulk' ? 0 : -1}
          className={`${extra.tabButton} ${activeTab === 'bulk' ? extra.tabButtonActive : ''}`}
          onClick={() => setActiveTab('bulk')}
        >
          Bulk CSV Import
        </button>
      </div>

      {activeTab === 'single' ? (
        <div role="tabpanel" id="single-shipment" aria-labelledby="single-shipment-tab">
          <h2 id="create-order-title">Book a Delivery Shipment</h2>
          <p id="submission-note">
            Choose a service, then enter the pickup, delivery and parcel details for this booking.
          </p>

          {createdOrder && (
            <div className={extra.successCard} role="status">
              <div className={extra.successHeader}>
                <MerchantIcon name="check" size={24} />
                <h3 className={extra.successTitle}>Shipment Booked Successfully!</h3>
              </div>
              <p>Your order has been registered in the SwiftDrop courier network.</p>
              <p>Review and confirm the order from its details page to make it available for dispatch.</p>
              {createdOrder.estimatedFee != null && <p>Estimated delivery fee <strong>{formatMerchantFee(createdOrder.estimatedFee)}</strong></p>}
              <div className={extra.trackingBox}>
                <span>Tracking Number:</span>
                <span className={extra.trackingNumber}>{createdOrder.trackingNumber}</span>
                <button type="button" className={extra.copyButton} onClick={handleCopyTracking}>
                  {copied ? 'Copied!' : 'Copy'}
                </button>
              </div>
              <div className={styles.actions}>
                <MerchantLink className={shared.button} href={merchantPath('orders', preview) + `/${createdOrder.id}`}>
                  View Order Details
                </MerchantLink>
                <button
                  type="button"
                  className={styles.primaryButton}
                  onClick={() => {
                    setCreatedOrder(null);
                    setValues({ deliveryType: '' });
                    setChecked(false);
                  }}
                >
                  Book Another Shipment
                </button>
                <MerchantLink className={shared.button} href={merchantPath('orders', preview)}>
                  Back to Store Orders
                </MerchantLink>
              </div>
            </div>
          )}

          {!createdOrder && (
            <form className={styles.orderForm} noValidate onSubmit={handleSubmit}>
              <datalist id="sri-lanka-district-options">
                {SRI_LANKA_DISTRICTS.map((district) => (
                  <option key={district} value={district} />
                ))}
              </datalist>
              <fieldset className={extra.formCard}>
                <legend>Delivery service</legend>
                <div className={shared.formGrid}>
                  <label>
                    <span>Service</span>
                    <select
                      className={styles.select}
                      name="deliveryType"
                      required
                      value={values.deliveryType}
                      aria-invalid={(checked && Boolean(deliveryTypeError)) || undefined}
                      aria-describedby={checked && deliveryTypeError ? 'order-service-error' : undefined}
                      onChange={(event) => change('deliveryType', event.target.value)}
                      disabled={isSubmitting}
                    >
                      <option value="">Select a service</option>
                      <option value="STANDARD">Standard</option>
                      <option value="EXPRESS">Express</option>
                      <option value="SAME_DAY">Same day</option>
                    </select>
                    {checked && deliveryTypeError && (
                      <span className={styles.fieldError} id="order-service-error">
                        {deliveryTypeError}
                      </span>
                    )}
                  </label>
                </div>
              </fieldset>

              {groups.map((group) => (
                <fieldset key={group.prefix} className={extra.formCard}>
                  <legend>{group.label}</legend>
                  {group.prefix === 'pickup' && (
                    <div className={extra.autoFillBar}>
                      <button
                        type="button"
                        id="use-business-address"
                        className={extra.autoFillButton}
                        onClick={() => void handleUseBusinessAddress()}
                        disabled={isSubmitting || profileLoading}
                        title="Auto-fill pickup contact and address from your Business Profile"
                      >
                        <MerchantIcon name="pin" size={16} />
                        Use Business Address
                      </button>
                      {autoFillNotice && (
                        <span
                          className={
                            autoFillNotice.type === 'error'
                              ? extra.autoFillNoticeError
                              : autoFillNotice.type === 'warning'
                              ? extra.autoFillNoticeWarning
                              : extra.autoFillNoticeSuccess
                          }
                          role="status"
                        >
                          {autoFillNotice.message}
                        </span>
                      )}
                    </div>
                  )}
                  <div className={shared.formGrid}>
                    {group.fields.map((field) => renderField({ ...field, name: `${group.prefix}.${field.name}` }))}
                  </div>
                  {'parcelId' in group && parcelIds.length > 1 && (
                    <button
                      type="button"
                      className={shared.button}
                      disabled={isSubmitting}
                      onClick={() => {
                        setParcelIds(parcelIds.filter((id) => id !== group.parcelId));
                        setChecked(false);
                      }}
                    >
                      Remove {group.label.toLowerCase()}
                    </button>
                  )}
                </fieldset>
              ))}

              <button
                type="button"
                className={shared.button}
                disabled={parcelIds.length >= 20 || isSubmitting}
                onClick={() => {
                  setParcelIds([...parcelIds, nextParcelId]);
                  setNextParcelId(nextParcelId + 1);
                  setChecked(false);
                }}
              >
                Add parcel (maximum 20)
              </button>

              <fieldset className={extra.formCard}>
                <legend>Delivery notes</legend>
                <div className={shared.formGrid}>{renderField(instructions)}</div>
              </fieldset>

              {submitError && (
                <p className={shared.error} role="alert">
                  {submitError}
                </p>
              )}

              {currentQuote && <div className={extra.importStatus} role="status">
                <strong>Estimated delivery fee {formatMerchantFee(currentQuote.totalFee, currentQuote.currency)}</strong>
                <p>Base {formatMerchantFee(currentQuote.baseFee)} · Distance {formatMerchantFee(currentQuote.distanceCharge)} · Weight {formatMerchantFee(currentQuote.weightCharge)} · Service {formatMerchantFee(currentQuote.serviceCharge)}</p>
                <p>{currentQuote.explanation}</p>
                <p>The server recalculates this fee when you book the shipment.</p>
              </div>}
              {quoteError && <p className={shared.error} role="alert">{quoteError}</p>}
              <div className={styles.actions}>
                <button type="submit" className={styles.primaryButton} disabled={isSubmitting || pricing}>
                  {isSubmitting ? 'Booking shipment…' : 'Submit order'}
                </button>
                <button type="submit" value="estimate" className={shared.button} disabled={isSubmitting || pricing}>
                  {pricing ? 'Estimating fee…' : 'Get fee estimate'}
                </button>
                <MerchantLink className={shared.button} href={merchantPath('orders', preview)}>
                  Back to Store Orders
                </MerchantLink>
              </div>

              {checked && firstError && (
                <p className={shared.error} role="alert">
                  Please resolve the highlighted validation errors above.
                </p>
              )}
            </form>
          )}
        </div>
      ) : (
        <div className={extra.csvSection} role="tabpanel" id="bulk-import" aria-labelledby="bulk-import-tab">
          <div>
            <h2 id="create-order-title">Bulk Shipment CSV Import</h2>
            <p>Book several shipments from one CSV file. Review your rows before submitting them.</p>
          </div>

          <div className={extra.csvSetup}>
            <div className={extra.csvCard}>
              <h3>Prepare your CSV</h3>
              <p>Keep the template headers and enter one shipment per row. Replace the sample row with your own shipment details.</p>
              <button type="button" className={shared.button} onClick={downloadCsvTemplate}>
                Download CSV Template
              </button>
            </div>
            <div className={extra.csvCard}>
              <h3>Select your file</h3>
              <div className={extra.filePicker}>
                <input id="shipment-csv" type="file" accept=".csv" className={extra.fileInput}
                  disabled={isReadingCsv || isImporting} onChange={handleFileUpload} aria-describedby="csv-file-name" />
                <label htmlFor="shipment-csv" className={shared.button}>{csvFileName ? 'Choose another CSV' : 'Choose CSV File'}</label>
                <p id="csv-file-name" className={extra.fileName} role="status">
                  {csvFileName ? (isReadingCsv ? `Reading ${csvFileName}…` : csvFileName) : 'No CSV selected'}
                </p>
              </div>
            </div>
          </div>

          {csvError && <p className={shared.error} role="alert">{csvError}</p>}

          {csvRows.length > 0 && (
            <div className={extra.csvCard}>
              <h3>Import Preview</h3>
              <p>
                Parsed <strong>{csvRows.length}</strong> rows:{' '}
                <strong>{validCount} valid</strong>{', '}
                <strong>{invalidCount} invalid</strong>
              </p>
              {invalidCount > 0 && <p>Only rows marked Valid will be submitted. To import corrected rows later, upload a CSV containing only those rows.</p>}

              {isImporting && (
                <div className={extra.importStatus}>
                  <p role="status">Processed {importProgress.current} of {importProgress.total} shipments</p>
                  <progress aria-label="Shipment import progress" value={importProgress.current} max={importProgress.total} />
                </div>
              )}

              {importSummary && (
                <div className={extra.importStatus} role="status">
                  <h4>Import Complete</h4>
                  <p>
                    Successfully booked: <strong>{importSummary.success}</strong> · Failed: <strong>{importSummary.failed}</strong>
                  </p>
                  {importSummary.success > 0 && <p>Booked shipments are pending. Open Store Orders to confirm them individually.</p>}
                </div>
              )}

              <div className={styles.actions}>
                <button
                  type="button"
                  className={styles.primaryButton}
                  disabled={isImporting || validCount === 0}
                  onClick={() => void runBulkImport(false)}
                >
                  {isImporting ? 'Processing…' : `Start Import (${validCount} valid)`}
                </button>
                {failedCount > 0 && (
                  <button
                    type="button"
                    className={shared.button}
                    disabled={isImporting}
                    onClick={() => void runBulkImport(true)}
                  >
                    Retry Failed Rows ({failedCount})
                  </button>
                )}
              </div>

              <div className={extra.previewTableWrapper} role="region" aria-label="CSV row results" tabIndex={0}>
                <table className={extra.previewTable}>
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Service</th>
                      <th>Recipient</th>
                      <th>Destination</th>
                      <th>Parcel</th>
                      <th>Status</th>
                      <th>Result</th>
                    </tr>
                  </thead>
                  <tbody>
                    {csvRows.map((r) => (
                      <tr key={r.rowIndex}>
                        <td>{r.rowIndex}</td>
                        <td>{r.data.deliveryType || '—'}</td>
                        <td>{r.data.destContactName || '—'}</td>
                        <td>
                          {r.data.destCity ? `${r.data.destCity}, ${r.data.destDistrict}` : '—'}
                        </td>
                        <td>{r.data.parcelDescription || '—'}</td>
                        <td>
                          {r.status === 'valid' && <span className={`${extra.badge} ${extra.badgeValid}`}>Valid</span>}
                          {r.status === 'invalid' && (
                            <span className={`${extra.badge} ${extra.badgeInvalid}`}>
                              Invalid
                            </span>
                          )}
                          {r.status === 'success' && (
                            <span className={`${extra.badge} ${extra.badgeSuccess}`}>Success</span>
                          )}
                          {r.status === 'failed' && (
                            <span className={`${extra.badge} ${extra.badgeError}`}>Failed</span>
                          )}
                        </td>
                        <td>
                          {r.trackingNumber ? (
                            <>
                              <strong className={extra.trackingNumber}>{r.trackingNumber}</strong>
                              {r.estimatedFee != null && <div>Estimated fee {formatMerchantFee(r.estimatedFee)}</div>}
                            </>
                          ) : r.errors.length ? (
                            <ul className={extra.rowErrors}>{r.errors.map((error) => <li key={error}>{error}</li>)}</ul>
                          ) : r.errorMessage ? (
                            <span className={extra.rowErrors}>{r.errorMessage}</span>
                          ) : (
                            '—'
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
