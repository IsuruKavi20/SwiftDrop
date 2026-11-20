/** What auth and the backend put in the name field when an account has no name of its own. */
const PLACEHOLDERS = new Set(['', 'unnamed account', 'unnamed']);

/**
 * A readable name for a person. Accounts without a name come back named after their email (or "Unnamed account"):
 * then the email's first part becomes the name, e.g. "isuru.premasiri2001@…" → "Isuru Premasiri", and `missing`
 * tells the screen to say so.
 */
export function personName(name: string | null | undefined, email?: string | null): { display: string; missing: boolean } {
  const trimmed = (name ?? '').trim();
  const usable = trimmed && !PLACEHOLDERS.has(trimmed.toLowerCase()) && trimmed.toLowerCase() !== (email ?? '').trim().toLowerCase()
    && !trimmed.includes('@');
  if (usable) return { display: trimmed, missing: false };

  const local = (email || trimmed).split('@')[0] ?? '';
  const words = local.replace(/\d+/g, ' ').split(/[._\-+\s]+/).filter(Boolean);
  const display = words.map((w) => w[0].toUpperCase() + w.slice(1).toLowerCase()).join(' ');
  return { display: display || 'Unnamed account', missing: true };
}

/** Up to two initials ("Isuru Premasiri" → "IP", "Kasun" → "K"). */
export function initials(display: string): string {
  return display.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || '?';
}
