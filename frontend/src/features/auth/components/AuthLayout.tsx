import type { ReactNode } from 'react';
import checkIcon from '../../../assets/auth/check.svg';
import logoIcon from '../../../assets/auth/logo-box.svg';
import routeIllustration from '../../../assets/auth/route-illustration.svg';
import styles from './AuthLayout.module.css';

const BENEFITS = ['Real-time tracking for every parcel', 'Nationwide courier network', 'Secure payments & verified merchants'];

interface AuthLayoutProps {
  headline: string;
  tagline: string;
  eyebrow: string;
  title: string;
  subtitle: string;
  /** Sign up shows the brand panel on the right (Figma "SwiftDrop desktop signup"). */
  brandOnRight?: boolean;
  children: ReactNode;
}

/** The two-panel screen shared by login and sign up: brand panel + form card. */
export function AuthLayout({ headline, tagline, eyebrow, title, subtitle, brandOnRight, children }: AuthLayoutProps) {
  return (
    <div className={`${styles.page} ${brandOnRight ? styles.reversed : ''}`}>
      <aside className={styles.brand}>
        <img className={styles.route} src={routeIllustration} width={220} height={140} alt="" />
        <div className={styles.logo}>
          <span className={styles.logoMark}>
            <img src={logoIcon} width={20} height={20} alt="" />
          </span>
          SwiftDrop
        </div>
        <h1 className={styles.headline}>{headline}</h1>
        <p className={styles.tagline}>{tagline}</p>
        <ul className={styles.benefits}>
          {BENEFITS.map((benefit) => (
            <li key={benefit} className={styles.benefit}>
              <span className={styles.check}>
                <img src={checkIcon} width={13} height={13} alt="" />
              </span>
              {benefit}
            </li>
          ))}
        </ul>
      </aside>

      <main className={styles.formSide}>
        <section className={styles.card}>
          <p className={styles.eyebrow}>{eyebrow}</p>
          <h2 className={styles.title}>{title}</h2>
          <p className={styles.subtitle}>{subtitle}</p>
          {children}
        </section>
      </main>
    </div>
  );
}
