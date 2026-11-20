import { useEffect, useId, useRef, useState } from 'react';
import { navigate } from '../../../app/router';
import { dispatcherPages, dispatcherPath, type DispatcherPageId } from '../dispatcherRoutes';
import { DispatcherIcon } from './DispatcherIcon';
import styles from './Dispatcher.module.css';

/** Extra words people may type for a page ("money" finds Pricing). */
const KEYWORDS: Partial<Record<DispatcherPageId, string>> = {
  admin: 'home dashboard summary',
  users: 'accounts people roles block admin',
  merchants: 'business shops applications approve reject',
  couriers: 'drivers riders vehicles phone availability',
  zones: 'districts areas regions',
  pricing: 'rates fees money tariff quote',
  reports: 'statistics charts success failure',
  operations: 'dispatch today status',
  unassigned: 'waiting assign parcels',
  deliveries: 'parcels tracking all orders',
  workloads: 'couriers busy load',
  failed: 'failed attempts problems retry',
};

const SHORTCUTS: { keys: string[]; what: string }[] = [
  { keys: ['Ctrl', 'K'], what: 'Go to any page' },
  { keys: ['/'], what: 'Search this page' },
  { keys: ['Esc'], what: 'Close a dialog or clear the search' },
  { keys: ['Ctrl', 'Z'], what: 'Undo the last change (while its message shows)' },
  { keys: ['?'], what: 'Show these shortcuts' },
];

interface CommandPaletteProps {
  preview: boolean;
  /** Opens with the shortcut list showing first (the "?" key). */
  showHelp?: boolean;
  onClose: () => void;
}

/** Ctrl/Cmd+K: type part of a page's name, arrow to it, Enter to go. */
export function CommandPalette({ preview, showHelp, onClose }: Readonly<CommandPaletteProps>) {
  const ref = useRef<HTMLDialogElement>(null);
  const listId = useId();
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const [help, setHelp] = useState(Boolean(showHelp));

  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => dialog?.close();
  }, []);

  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const results = dispatcherPages.filter((page) => {
    const haystack = `${page.title} ${page.section} ${KEYWORDS[page.id] ?? ''}`.toLowerCase();
    return words.every((word) => haystack.includes(word));
  });
  const current = Math.min(active, Math.max(results.length - 1, 0));

  const go = (id: DispatcherPageId) => {
    onClose();
    navigate(dispatcherPath(id, preview));
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === '?' && !query) { event.preventDefault(); setHelp((h) => !h); }
    else if (event.key === 'ArrowDown') { event.preventDefault(); setActive((i) => Math.min(i + 1, results.length - 1)); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); setActive((i) => Math.max(i - 1, 0)); }
    else if (event.key === 'Enter' && results[current]) { event.preventDefault(); go(results[current].id); }
  };

  return (
    <dialog ref={ref} className={styles.palette} aria-label="Go to a page"
      onCancel={(event) => { event.preventDefault(); onClose(); }}
      onClick={(event) => { if (event.target === ref.current) onClose(); }}>
      <div className={styles.paletteSearch}>
        <DispatcherIcon name="search" size={18} />
        <input autoFocus value={query} placeholder="Go to a page… (try “pricing” or “failed”)" role="combobox"
          aria-expanded="true" aria-controls={listId} aria-activedescendant={results[current] ? `${listId}-${results[current].id}` : undefined}
          onChange={(e) => { setQuery(e.target.value); setActive(0); }} onKeyDown={onKeyDown} />
        <kbd>Esc</kbd>
      </div>

      {help && !query && <ShortcutList />}

      <ul id={listId} role="listbox" className={styles.paletteList} aria-label="Pages">
        {results.map((page, index) => (
          <li key={page.id} id={`${listId}-${page.id}`} role="option" aria-selected={index === current}
            data-active={index === current || undefined}
            onPointerEnter={() => setActive(index)} onClick={() => go(page.id)}>
            <span className={styles.navIcon}><DispatcherIcon name={page.icon} size={18} /></span>
            <span>{page.title}</span>
            <small>{page.section}</small>
          </li>
        ))}
        {results.length === 0 && <li className={styles.paletteEmpty} role="presentation">No page matches “{query}”.</li>}
      </ul>

      {!help && (
        <footer className={styles.paletteFooter}>
          <span><kbd>↑</kbd><kbd>↓</kbd> move</span>
          <span><kbd>Enter</kbd> open</span>
          <span><kbd>?</kbd> all shortcuts</span>
        </footer>
      )}
    </dialog>
  );
}

function ShortcutList() {
  return (
    <section className={styles.shortcutList} aria-label="Keyboard shortcuts">
      <h2>Keyboard shortcuts</h2>
      <dl>
        {SHORTCUTS.map((s) => (
          <div key={s.what}>
            <dt>{s.keys.map((k) => <kbd key={k}>{k}</kbd>)}</dt>
            <dd>{s.what}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
