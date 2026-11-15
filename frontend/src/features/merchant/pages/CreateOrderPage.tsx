import { useState, type ChangeEvent, type FormEvent } from 'react';
import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import {
  createMerchantDelivery,
  type CreateMerchantDeliveryInput,
  type DeliveryType,
  type MerchantDeliveryDetail,
} from '../api/merchantOrdersApi';
import { MerchantApiError } from '../api/merchantApi';
import styles from './MerchantOrders.module.css';
import shared from './MerchantPages.module.css';
import extra from './CreateOrderPage.module.css';

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

type Field = { name: string; label: string; max?: number; optional?: boolean; phone?: boolean; digits?: number };

function fieldError(field: Field, rawValue: string): string | undefined {
  const value = rawValue.trim();
  if (!value) return field.optional ? undefined : 'This field is required.';
  if (field.max && value.length > field.max) return `Use at most ${field.max} characters.`;
  if (field.phone && !/^\+?[0-9]{9,15}$/.test(value)) return 'Enter 9–15 digits, optionally starting with +.';
  if (field.digits) {
    const decimal = new RegExp(`^(?:[0-9]{1,${field.digits}}(?:\\.[0-9]{1,2})?|\\.[0-9]{1,2})$`);
    if (!decimal.test(value)) return `Use up to ${field.digits} digits before the decimal and up to 2 after it.`;
    if (!field.optional && Number(value) < 0.01) return 'Enter a value of at least 0.01.';
  }
}

interface CsvRow {
  rowIndex: number;
  data: Record<string, string>;
  errors: string[];
  status: 'valid' | 'invalid' | 'pending' | 'success' | 'failed';
  trackingNumber?: string;
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

  // Bulk CSV state
  const [csvRows, setCsvRows] = useState<CsvRow[]>([]);
  const [isImporting, setIsImporting] = useState(false);
  const [importProgress, setImportProgress] = useState({ current: 0, total: 0 });
  const [importSummary, setImportSummary] = useState<{ success: number; failed: number } | null>(null);

  const instructions = { name: 'specialInstructions', label: 'Special instructions', max: 500, optional: true };
  const groups = [
    { label: 'Recipient information', prefix: 'destination', fields: contactFields },
    { label: 'Delivery address', prefix: 'destination', fields: addressFields },
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
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setChecked(true);
    setSubmitError(null);

    if (firstError) {
      const field = event.currentTarget.elements.namedItem(firstError);
      if (field instanceof HTMLElement) field.focus();
      return;
    }

    if (preview) {
      setSubmitError('Order submission is simulated in development preview mode.');
      return;
    }

    const payload: CreateMerchantDeliveryInput = {
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

    try {
      setIsSubmitting(true);
      const result = await createMerchantDelivery(payload);
      setCreatedOrder(result);
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
    return (
      <label key={field.name} htmlFor={id}>
        <span>
          {field.label}
          {field.optional && ' (optional)'}
        </span>
        <input
          id={id}
          name={field.name}
          type={field.phone ? 'tel' : 'text'}
          inputMode={field.digits ? 'decimal' : undefined}
          required={!field.optional}
          maxLength={field.max}
          value={values[field.name] ?? ''}
          onChange={(event) => change(field.name, event.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          disabled={isSubmitting}
        />
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
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const text = event.target?.result as string;
      if (!text) return;

      const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
      if (lines.length < 2) {
        alert('The uploaded CSV is empty or missing data rows.');
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
          data,
          errors: rowErrors,
          status: rowErrors.length === 0 ? 'valid' : 'invalid',
        });
      }

      setCsvRows(rows);
      setImportSummary(null);
    };
    reader.readAsText(file);
  }

  async function runBulkImport(onlyFailed = false) {
    if (preview) {
      alert('Bulk import execution is simulated in development preview mode.');
      return;
    }

    const rowsToProcess = csvRows.filter((r) =>
      onlyFailed ? r.status === 'failed' : r.status === 'valid' || r.status === 'pending'
    );

    if (rowsToProcess.length === 0) return;

    setIsImporting(true);
    setImportProgress({ current: 0, total: rowsToProcess.length });
    let successCount = 0;
    let failedCount = 0;

    for (let i = 0; i < rowsToProcess.length; i++) {
      const row = rowsToProcess[i];
      setImportProgress({ current: i + 1, total: rowsToProcess.length });

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
        const res = await createMerchantDelivery(payload);
        successCount++;
        setCsvRows((prev) =>
          prev.map((r) =>
            r.rowIndex === row.rowIndex
              ? { ...r, status: 'success', trackingNumber: res.trackingNumber, errorMessage: undefined }
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
    }

    setIsImporting(false);
    setImportSummary({ success: successCount, failed: failedCount });
  }

  return (
    <section className={shared.panel} aria-labelledby="create-order-title">
      <div className={extra.tabs} role="tablist" aria-label="Order creation methods">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'single'}
          className={`${extra.tabButton} ${activeTab === 'single' ? extra.tabButtonActive : ''}`}
          onClick={() => setActiveTab('single')}
        >
          Single Shipment
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'bulk'}
          className={`${extra.tabButton} ${activeTab === 'bulk' ? extra.tabButtonActive : ''}`}
          onClick={() => setActiveTab('bulk')}
        >
          Bulk CSV Import
        </button>
      </div>

      {activeTab === 'single' ? (
        <>
          <h2 id="create-order-title">Book a Delivery Shipment</h2>
          <p id="submission-note">
            Create a single courier booking for your customer order. Pickups and dropoffs will be handled by SwiftDrop dispatch.
          </p>

          {createdOrder && (
            <div className={extra.successCard} role="status">
              <div className={extra.successHeader}>
                <span style={{ fontSize: '1.5rem' }}>✓</span>
                <h3 className={extra.successTitle}>Shipment Booked Successfully!</h3>
              </div>
              <p>Your order has been registered in the SwiftDrop courier network.</p>
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
                  className={shared.button}
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
              <fieldset className={shared.fieldset}>
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
                <fieldset key={group.prefix === 'destination' ? group.label : group.prefix} className={shared.fieldset}>
                  <legend>{group.label}</legend>
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

              <fieldset className={shared.fieldset}>
                <legend>Delivery notes</legend>
                <div className={shared.formGrid}>{renderField(instructions)}</div>
              </fieldset>

              {submitError && (
                <p className={shared.error} role="alert">
                  {submitError}
                </p>
              )}

              <div className={styles.actions}>
                <button type="submit" className={shared.button} disabled={isSubmitting}>
                  {isSubmitting ? 'Booking shipment…' : 'Submit order'}
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
        </>
      ) : (
        <div className={extra.csvSection}>
          <h2>Bulk Shipment CSV Import</h2>
          <p>
            Upload a spreadsheet with multiple delivery orders to book them in batch. Download the template below to ensure correct column formatting.
          </p>

          <div className={extra.csvActions}>
            <button type="button" className={shared.button} onClick={downloadCsvTemplate}>
              📥 Download CSV Template
            </button>
            <label className={`${shared.button} ${extra.fileInputLabel}`}>
              📁 Choose CSV File
              <input type="file" accept=".csv" className={extra.hiddenInput} onChange={handleFileUpload} />
            </label>
          </div>

          {csvRows.length > 0 && (
            <div>
              <h3>Import Preview</h3>
              <p>
                Parsed <strong>{csvRows.length}</strong> rows:{' '}
                <span style={{ color: '#2e7d32', fontWeight: 600 }}>
                  {csvRows.filter((r) => r.status === 'valid' || r.status === 'success').length} valid
                </span>
                {', '}
                <span style={{ color: '#d32f2f', fontWeight: 600 }}>
                  {csvRows.filter((r) => r.status === 'invalid' || r.status === 'failed').length} invalid
                </span>
              </p>

              {isImporting && (
                <div>
                  <p>
                    Importing row {importProgress.current} of {importProgress.total}…
                  </p>
                  <div className={extra.progressBarContainer}>
                    <div
                      className={extra.progressBarFill}
                      style={{
                        width: `${importProgress.total > 0 ? (importProgress.current / importProgress.total) * 100 : 0}%`,
                      }}
                    />
                  </div>
                </div>
              )}

              {importSummary && (
                <div className={shared.state}>
                  <h4>Import Complete</h4>
                  <p>
                    Successfully created: <strong>{importSummary.success}</strong> | Failed:{' '}
                    <strong>{importSummary.failed}</strong>
                  </p>
                </div>
              )}

              <div className={styles.actions} style={{ margin: '1rem 0' }}>
                <button
                  type="button"
                  className={shared.button}
                  disabled={isImporting || csvRows.filter((r) => r.status === 'valid').length === 0}
                  onClick={() => void runBulkImport(false)}
                >
                  {isImporting ? 'Processing…' : `Start Import (${csvRows.filter((r) => r.status === 'valid').length} valid)`}
                </button>
                {csvRows.some((r) => r.status === 'failed') && (
                  <button
                    type="button"
                    className={shared.button}
                    disabled={isImporting}
                    onClick={() => void runBulkImport(true)}
                  >
                    Retry Failed Rows ({csvRows.filter((r) => r.status === 'failed').length})
                  </button>
                )}
              </div>

              <div className={extra.previewTableWrapper}>
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
                              {r.errors.join('; ')}
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
                            <strong style={{ fontFamily: 'monospace' }}>{r.trackingNumber}</strong>
                          ) : r.errorMessage ? (
                            <span style={{ color: '#d32f2f' }}>{r.errorMessage}</span>
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
