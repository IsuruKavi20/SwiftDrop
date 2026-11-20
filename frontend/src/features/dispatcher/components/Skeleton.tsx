import styles from './Dispatcher.module.css';

/**
 * Shimmering placeholders shown while a screen's data loads, so a blank area never looks broken.
 * Tables, stat cards and merchant cards get theirs from the stylesheet (aria-busy / data-loading); these cover the rest.
 * Screen readers hear one "Loading…" instead of the shapes.
 */

/** A few lines of text, the last one shorter. */
export function SkeletonLines({ lines = 3, label = 'Loading…' }: Readonly<{ lines?: number; label?: string }>) {
  return (
    <div className={styles.skeletonLines} role="status">
      <span className={styles.visuallyHidden}>{label}</span>
      {Array.from({ length: lines }, (_, i) => (
        <span key={i} className={styles.skeleton} style={{ width: i === lines - 1 ? '60%' : '100%' }} aria-hidden="true" />
      ))}
    </div>
  );
}

/** Bars of different heights, in the chart's place. */
export function ChartSkeleton({ bars = 7 }: Readonly<{ bars?: number }>) {
  const heights = [45, 70, 35, 85, 55, 75, 60];
  return (
    <div className={styles.chartSkeleton} role="status">
      <span className={styles.visuallyHidden}>Loading chart…</span>
      {Array.from({ length: bars }, (_, i) => (
        <span key={i} className={styles.skeleton} style={{ height: `${heights[i % heights.length]}%` }} aria-hidden="true" />
      ))}
    </div>
  );
}

/** A panel with a title and rows of label + value, e.g. the Pricing page's rates while they load. */
export function PanelSkeleton({ rows = 5 }: Readonly<{ rows?: number }>) {
  return (
    <section className={styles.panel} aria-hidden="true">
      <header className={styles.panelHeader}>
        <div className={styles.skeletonLines} style={{ width: '45%' }}>
          <span className={styles.skeleton} style={{ height: '1rem' }} />
          <span className={styles.skeleton} style={{ width: '80%' }} />
        </div>
      </header>
      <ul className={styles.rateRows}>
        {Array.from({ length: rows }, (_, i) => (
          <li key={i}>
            <div className={styles.skeletonLines}>
              <span className={styles.skeleton} style={{ width: '55%' }} />
              <span className={styles.skeleton} style={{ width: '35%' }} />
            </div>
            <span className={styles.skeleton} style={{ height: '2.6rem', borderRadius: 'var(--radius-input)' }} />
          </li>
        ))}
      </ul>
    </section>
  );
}
