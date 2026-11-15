import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { DispatcherIcon } from './DispatcherIcon';
import styles from './Dispatcher.module.css';

export interface DropdownOption {
  value: string;
  label: string;
}

interface DropdownProps {
  value: string;
  options: readonly DropdownOption[];
  onChange: (value: string) => void;
  /** Shown when no option has the current value (e.g. "Choose an account…"). */
  placeholder?: string;
  /** Shown in the open list when there are no options, so the click visibly does something. */
  emptyMessage?: string;
  disabled?: boolean;
  id?: string;
  'aria-label'?: string;
}

interface MenuPosition {
  left: number;
  width: number;
  top?: number;
  bottom?: number;
  maxHeight: number;
}

const MENU_MAX_HEIGHT = 288;
const GAP = 6;

/**
 * The admin area's replacement for <select>: the browser draws a native select's open list itself, so it can't match
 * the SwiftDrop look. This follows the WAI-ARIA "select-only combobox" pattern: focus stays on the button, the arrow
 * keys, Home/End and typing move through the options, Enter/Space picks one and Escape closes the list.
 *
 * The list is rendered into the open dialog (or the admin layout) with fixed positioning, so a dialog's scrolling body
 * can't cut it off, and it sits outside any wrapping <label> (a click inside a label would re-click the button).
 */
export function Dropdown({ value, options, onChange, placeholder = 'Select…', emptyMessage = 'Nothing to choose from', disabled, id, 'aria-label': ariaLabel }: Readonly<DropdownProps>) {
  const listId = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const typed = useRef({ text: '', at: 0 });
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [position, setPosition] = useState<MenuPosition | null>(null);
  const [host, setHost] = useState<HTMLElement | null>(null);

  const selectedIndex = options.findIndex((o) => o.value === value);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : undefined;

  const place = useCallback(() => {
    const button = buttonRef.current;
    if (!button) return;
    const rect = button.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom - GAP - 8;
    const above = rect.top - GAP - 8;
    const openUp = below < Math.min(MENU_MAX_HEIGHT, 160) && above > below;
    setPosition({
      left: rect.left,
      width: rect.width,
      ...(openUp ? { bottom: window.innerHeight - rect.top + GAP } : { top: rect.bottom + GAP }),
      maxHeight: Math.min(MENU_MAX_HEIGHT, openUp ? above : below),
    });
  }, []);

  const openList = (startAt = options.length ? Math.max(selectedIndex, 0) : -1) => {
    const button = buttonRef.current;
    if (disabled || !button) return;
    // Inside the open dialog (top layer) or the admin layout, so the menu shows above it and keeps the theme colours.
    setHost(button.closest('dialog') ?? button.closest<HTMLElement>(`.${styles.layout}`) ?? document.body);
    place();
    setActive(startAt);
    setOpen(true);
  };

  const close = () => setOpen(false);

  const choose = (index: number) => {
    const option = options[index];
    if (option && option.value !== value) onChange(option.value);
    close();
    buttonRef.current?.focus();
  };

  // Keep the menu attached to the button while the page or a dialog scrolls, and close on a click elsewhere.
  useLayoutEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!buttonRef.current?.contains(target) && !listRef.current?.contains(target)) close();
    };
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    document.addEventListener('pointerdown', onPointerDown);
    return () => {
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [open, place]);

  useEffect(() => {
    if (!open || active < 0) return;
    listRef.current?.children[active]?.scrollIntoView({ block: 'nearest' });
  }, [open, active]);

  /** Typing a few letters jumps to the first option starting with them, like a native select. */
  const typeAhead = (key: string): number => {
    const now = Date.now();
    typed.current = { text: (now - typed.current.at > 600 ? '' : typed.current.text) + key.toLowerCase(), at: now };
    const text = typed.current.text;
    const from = open ? active : selectedIndex;
    const order = [...options.keys()].map((i) => (i + Math.max(from, 0) + (text.length === 1 ? 1 : 0)) % options.length);
    return order.find((i) => options[i].label.toLowerCase().startsWith(text)) ?? -1;
  };

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const last = options.length - 1;
    if (event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey && !event.altKey) {
      const match = typeAhead(event.key);
      if (match >= 0) {
        if (open) setActive(match);
        else openList(match);
      }
      return;
    }
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        event.preventDefault();
        openList();
      }
      return;
    }
    switch (event.key) {
      case 'ArrowDown': event.preventDefault(); setActive((i) => Math.min(i + 1, last)); break;
      case 'ArrowUp':
        event.preventDefault();
        if (event.altKey) choose(active);
        else setActive((i) => Math.max(i - 1, 0));
        break;
      case 'Home': event.preventDefault(); setActive(0); break;
      case 'End': event.preventDefault(); setActive(last); break;
      case 'PageDown': event.preventDefault(); setActive((i) => Math.min(i + 8, last)); break;
      case 'PageUp': event.preventDefault(); setActive((i) => Math.max(i - 8, 0)); break;
      case 'Enter':
      case ' ': event.preventDefault(); choose(active); break;
      case 'Escape': event.preventDefault(); event.stopPropagation(); close(); break; // stopPropagation: keep a dialog open
      case 'Tab': close(); break;
    }
  };

  return (
    <>
      <button ref={buttonRef} id={id} type="button" role="combobox" className={styles.dropdownButton}
        aria-label={ariaLabel} aria-haspopup="listbox" aria-expanded={open} aria-controls={listId}
        aria-activedescendant={open && active >= 0 ? `${listId}-${active}` : undefined}
        disabled={disabled} data-open={open || undefined} data-placeholder={!selected || undefined}
        onClick={() => (open ? close() : openList())} onKeyDown={onKeyDown}>
        <span className={styles.dropdownValue}>{selected?.label ?? placeholder}</span>
        <DispatcherIcon name="down" size={16} />
      </button>
      {open && position && host && createPortal(
        <ul ref={listRef} id={listId} role="listbox" tabIndex={-1} className={styles.dropdownMenu}
          style={{ left: position.left, width: position.width, top: position.top, bottom: position.bottom, maxHeight: position.maxHeight }}>
          {options.length === 0 && <li role="presentation" className={styles.dropdownEmpty}>{emptyMessage}</li>}
          {options.map((option, index) => (
            <li key={option.value} id={`${listId}-${index}`} role="option" aria-selected={index === selectedIndex}
              className={styles.dropdownOption} data-active={index === active || undefined}
              onPointerDown={(e) => e.preventDefault()} onPointerEnter={() => setActive(index)} onClick={() => choose(index)}>
              <span>{option.label}</span>
              {index === selectedIndex && <DispatcherIcon name="tick" size={16} />}
            </li>
          ))}
        </ul>,
        host,
      )}
    </>
  );
}
