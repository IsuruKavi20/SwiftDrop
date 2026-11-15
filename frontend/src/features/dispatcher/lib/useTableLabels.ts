import { useEffect, type RefObject } from 'react';

/** A heading's text, or '' when it is only visually hidden text (e.g. "Actions"). */
function headingText(th: HTMLTableCellElement, hiddenClass: string): string {
  const hidden = th.querySelector(`.${hiddenClass}`);
  const text = (th.textContent ?? '').trim();
  return hidden && text === (hidden.textContent ?? '').trim() ? '' : text;
}

/** Copies each column heading onto that column's body cells as data-label (only where it changed). */
function labelTable(table: HTMLTableElement, hiddenClass: string) {
  const headers = [...table.querySelectorAll<HTMLTableCellElement>('thead th')].map((th) => headingText(th, hiddenClass));
  for (const row of table.querySelectorAll<HTMLTableRowElement>('tbody tr')) {
    for (const cell of row.cells) {
      const text = headers[cell.cellIndex] ?? '';
      if (cell.dataset.label !== text) cell.dataset.label = text;
    }
  }
}

/**
 * On phones the admin tables turn into one card per row (Dispatcher.module.css), and each value needs its column's
 * name beside it. Rather than repeating every heading in every page, this labels the body cells from the table's own
 * header row, and keeps doing so as rows load, filter or change.
 *
 * Cells match headers by position (the admin tables use no colspan or rowspan). A visually hidden heading gives
 * data-label="", which the stylesheet treats as "no label".
 */
export function useTableLabels(root: RefObject<HTMLElement | null>, tableClass: string, hiddenClass: string) {
  useEffect(() => {
    const element = root.current;
    if (!element) return;

    let frame = 0;
    const labelAll = () => {
      frame = 0;
      element.querySelectorAll<HTMLTableElement>(`table.${tableClass}`).forEach((table) => labelTable(table, hiddenClass));
    };
    // One pass per frame; setting data-label is an attribute change, which this observer ignores, so it can't loop
    const schedule = () => { frame ||= requestAnimationFrame(labelAll); };

    labelAll();
    const observer = new MutationObserver(schedule);
    observer.observe(element, { childList: true, subtree: true });
    return () => {
      observer.disconnect();
      if (frame) cancelAnimationFrame(frame);
    };
  }, [root, tableClass, hiddenClass]);
}
