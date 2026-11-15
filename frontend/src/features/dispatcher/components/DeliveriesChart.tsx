import type { DayCount } from '../api/adminTypes';
import styles from './Dispatcher.module.css';

const dayLabel = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' });
const fullLabel = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });

function parseDay(day: string): Date {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Delivered (bottom) and failed attempts (top) per day, as stacked bars. Long ranges label every 5th day. */
export function DeliveriesChart({ days }: { days: DayCount[] }) {
  const highest = Math.max(1, ...days.map((d) => d.delivered + d.failedAttempts));
  const sparse = days.length > 10;

  return (
    <figure className={styles.chart}>
      <ol className={styles.chartBars} style={{ gridTemplateColumns: `repeat(${days.length}, minmax(0, 1fr))` }}>
        {days.map((d, index) => {
          const total = d.delivered + d.failedAttempts;
          const date = parseDay(d.day);
          const showLabel = !sparse || index % 5 === 0 || index === days.length - 1;
          return (
            <li key={d.day} title={`${fullLabel.format(date)}: ${d.delivered} delivered, ${d.failedAttempts} failed`}>
              <span className={styles.chartStack} style={{ height: `${(total / highest) * 100}%` }}>
                {total > 0 && !sparse && <span className={styles.chartTotal}>{total}</span>}
                {d.failedAttempts > 0 && <span className={styles.chartFailed} style={{ flexGrow: d.failedAttempts }} />}
                {d.delivered > 0 && <span className={styles.chartDelivered} style={{ flexGrow: d.delivered }} />}
              </span>
              <span className={styles.chartLabel} aria-hidden={!showLabel}>{showLabel ? dayLabel.format(date) : ''}</span>
              <span className={styles.visuallyHidden}>
                {fullLabel.format(date)}: {d.delivered} delivered, {d.failedAttempts} failed attempts
              </span>
            </li>
          );
        })}
      </ol>
      <figcaption className={styles.chartLegend}>
        <span><i className={styles.chartDelivered} /> Delivered</span>
        <span><i className={styles.chartFailed} /> Failed attempts</span>
      </figcaption>
    </figure>
  );
}
