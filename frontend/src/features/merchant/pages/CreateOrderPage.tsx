import { useState, type FormEvent } from 'react';
import { MerchantLink } from '../components/MerchantLink';
import { merchantPath } from '../merchantRoutes';
import styles from './MerchantOrders.module.css';
import shared from './MerchantPages.module.css';

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

export function CreateOrderPage({ preview }: { preview: boolean }) {
  const [values, setValues] = useState<Record<string, string>>({ deliveryType: '' });
  const [parcelIds, setParcelIds] = useState([1]);
  const [nextParcelId, setNextParcelId] = useState(2);
  const [checked, setChecked] = useState(false);
  const instructions = { name: 'specialInstructions', label: 'Special instructions', max: 500, optional: true };
  const groups = [
    { label: 'Recipient information', prefix: 'destination', fields: contactFields },
    { label: 'Delivery address', prefix: 'destination', fields: addressFields },
    { label: 'Pickup information and address', prefix: 'pickup', fields: [...contactFields, ...addressFields] },
    ...parcelIds.map((id, index) => ({ label: `Parcel ${index + 1}`, prefix: `parcel-${id}`, fields: parcelFields, parcelId: id })),
  ];
  const fields = groups.flatMap((group) => group.fields.map((field) => ({ ...field, name: `${group.prefix}.${field.name}` })));
  const errors = Object.fromEntries([...fields, instructions].map((field) => [field.name, fieldError(field, values[field.name] ?? '')]));
  const deliveryTypeError = values.deliveryType ? undefined : 'Select a delivery service.';
  const firstError = deliveryTypeError ? 'deliveryType' : Object.keys(errors).find((key) => errors[key]);

  function change(name: string, value: string) {
    setValues((previous) => ({ ...previous, [name]: value }));
    setChecked(false);
  }

  function validate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setChecked(true);
    if (firstError) {
      const field = event.currentTarget.elements.namedItem(firstError);
      if (field instanceof HTMLElement) field.focus();
    }
  }

  function renderField(field: Field) {
    const error = checked ? errors[field.name] : undefined;
    const id = `order-${field.name}`;
    return (
      <label key={field.name} htmlFor={id}>
        <span>{field.label}{field.optional && ' (optional)'}</span>
        <input id={id} name={field.name} type={field.phone ? 'tel' : 'text'}
          inputMode={field.digits ? 'decimal' : undefined} required={!field.optional} maxLength={field.max}
          value={values[field.name] ?? ''} onChange={(event) => change(field.name, event.target.value)}
          aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined} />
        {error && <span className={styles.fieldError} id={`${id}-error`}>{error}</span>}
      </label>
    );
  }

  return (
    <section className={shared.panel} aria-labelledby="create-order-title">
      <h2 id="create-order-title">Delivery details</h2>
      <p id="submission-note">Merchant order submission is unavailable. You can check the form against SwiftDrop’s current delivery booking rules.</p>
      <form className={styles.orderForm} noValidate onSubmit={validate}>
        <fieldset className={shared.fieldset}>
          <legend>Delivery service</legend>
          <div className={shared.formGrid}>
            <label>
              <span>Service</span>
              <select className={styles.select} name="deliveryType" required value={values.deliveryType}
                aria-invalid={checked && Boolean(deliveryTypeError) || undefined}
                aria-describedby={checked && deliveryTypeError ? 'order-service-error' : undefined}
                onChange={(event) => change('deliveryType', event.target.value)}>
                <option value="">Select a service</option>
                <option value="STANDARD">Standard</option>
                <option value="EXPRESS">Express</option>
                <option value="SAME_DAY">Same day</option>
              </select>
              {checked && deliveryTypeError && <span className={styles.fieldError} id="order-service-error">{deliveryTypeError}</span>}
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
              <button type="button" className={shared.button} onClick={() => {
                setParcelIds(parcelIds.filter((id) => id !== group.parcelId));
                setChecked(false);
              }}>Remove {group.label.toLowerCase()}</button>
            )}
          </fieldset>
        ))}
        <button type="button" className={shared.button} disabled={parcelIds.length >= 20} onClick={() => {
          setParcelIds([...parcelIds, nextParcelId]);
          setNextParcelId(nextParcelId + 1);
          setChecked(false);
        }}>Add parcel (maximum 20)</button>
        <fieldset className={shared.fieldset}>
          <legend>Delivery notes</legend>
          <div className={shared.formGrid}>{renderField(instructions)}</div>
        </fieldset>
        <div className={styles.actions}>
          <button type="submit" className={shared.button}>Check form</button>
          <button type="button" className={shared.button} disabled aria-describedby="submission-note">Submit order</button>
          <MerchantLink className={shared.button} href={merchantPath('orders', preview)}>Back to Store Orders</MerchantLink>
        </div>
        {checked && (firstError
          ? <p className={shared.error} role="alert">Check the highlighted fields.</p>
          : <p className={shared.success} role="status">Form validation passed. Submission requires the Merchant order API.</p>)}
      </form>
    </section>
  );
}
