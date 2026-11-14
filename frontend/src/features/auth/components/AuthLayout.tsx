import type { ReactNode } from 'react';
import logoIcon from '../../../assets/brand/logo-mark.svg';
import logoIconLight from '../../../assets/brand/logo-mark-light.svg';
import { navigate } from '../../../app/router';
import { DeliveryScene } from '../../../shared/ui/DeliveryScene';
import { Icon, type IconName } from '../../../shared/ui/Icon';
import styles from './AuthLayout.module.css';

const BENEFITS: { icon: IconName; text: string }[] = [
  { icon: 'pin', text: 'Real-time tracking for every parcel' },
  { icon: 'truck', text: 'Nationwide courier network' },
  { icon: 'shield', text: 'Secure payments & verified merchants' },
];

interface AuthLayoutProps {
  headline: string;
  tagline: string;
  eyebrow: string;
  title: string;
  subtitle: string;
  /** Sign up shows the brand words on the right (mirrored layout). */
  brandOnRight?: boolean;
  children: ReactNode;
}

/**
 * Login / sign up: the dark curve on one side holds the logo, the animated delivery town and the words; the form card
 * sits on the other side. Only the look lives here; the forms and their logic stay in the pages.
 */
export function AuthLayout({ headline, tagline, eyebrow, title, subtitle, brandOnRight, children }: AuthLayoutProps) {
  const goHome = (event: React.MouseEvent) => { event.preventDefault(); navigate('/'); };
  return (
    <div className={`${styles.page} ${brandOnRight ? styles.reversed : ''}`}>
      <aside className={styles.brand}>
        <a className={styles.logo} href="/" onClick={goHome}>
          <span className={styles.logoMark}><img src={logoIconLight} width={20} height={20} alt="" /></span>
          SwiftDrop
        </a>
        <div className={styles.sceneSide}>
          <DeliveryScene className={styles.scene} />
        </div>

        <div className={styles.copy}>
          <h1 className={styles.headline}>{headline}</h1>
          <p className={styles.tagline}>{tagline}</p>
          <ul className={styles.benefits}>
            {BENEFITS.map((benefit) => (
              <li key={benefit.text} className={styles.benefit}>
                <span className={styles.benefitIcon}><Icon name={benefit.icon} size={18} /></span>
                {benefit.text}
              </li>
            ))}
          </ul>
        </div>
      </aside>

      <main className={styles.formSide}>
        <section className={styles.card}>
          <a className={styles.mobileLogo} href="/" onClick={goHome}>
            <span className={styles.logoMark}><img src={logoIcon} width={18} height={18} alt="" /></span>
            SwiftDrop
          </a>
          <p className={styles.eyebrow}>{eyebrow}</p>
          <h2 className={styles.title}>{title}</h2>
          <p className={styles.subtitle}>{subtitle}</p>
          {children}
        </section>
      </main>
    </div>
  );
}
