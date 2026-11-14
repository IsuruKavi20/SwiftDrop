import { useState, type FormEvent } from 'react';
import logoIcon from '../../../assets/auth/logo-box.svg';
import routeIllustration from '../../../assets/auth/route-illustration.svg';
import { navigate, routes } from '../../../app/router';
import { landingPage, withNext } from '../../auth/lib/landing';
import { getAccessToken } from '../../auth/lib/session';
import styles from './LandingPage.module.css';

const STEPS = [
  { title: 'Book', text: 'Enter pickup and delivery details and see the fee before you confirm.' },
  { title: 'We pick up', text: 'A SwiftDrop courier collects the parcel from your door or shop.' },
  { title: 'Delivered', text: 'Follow every step live. The receiver gets a tracking link by SMS.' },
];

/**
 * The home page (/). Anyone can track a parcel here without an account; sending, selling and managing need a login.
 * Logged-in people see "My dashboard" instead of Log in / Sign up.
 */
export function LandingPage() {
  const [number, setNumber] = useState('');
  const loggedIn = getAccessToken() !== null;

  function track(event: FormEvent) {
    event.preventDefault();
    const code = number.trim().toUpperCase();
    if (code) {
      navigate(`${routes.publicTrack}/${encodeURIComponent(code)}`);
    }
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <a className={styles.logo} href="/" onClick={(e) => { e.preventDefault(); navigate('/'); }}>
          <span className={styles.logoMark}><img src={logoIcon} width={20} height={20} alt="" /></span>
          SwiftDrop
        </a>
        <nav className={styles.nav} aria-label="Account">
          {loggedIn ? (
            <button type="button" className={styles.primaryButton} onClick={() => navigate(landingPage())}>
              My dashboard
            </button>
          ) : (
            <>
              <button type="button" className={styles.textButton} onClick={() => navigate(routes.login)}>Log in</button>
              <button type="button" className={styles.primaryButton} onClick={() => navigate(routes.signup)}>Sign up</button>
            </>
          )}
        </nav>
      </header>

      <main>
        <section className={styles.hero}>
          <div className={styles.heroText}>
            <span className={styles.eyebrow}>PARCEL TRACKING · NO ACCOUNT NEEDED</span>
            <h1 className={styles.headline}>Where is my parcel?</h1>
            <p className={styles.tagline}>
              Enter the tracking number from your sender or SMS to see where your parcel is right now.
            </p>
            <form className={styles.trackForm} onSubmit={track} role="search">
              <label htmlFor="tracking-number" className={styles.srOnly}>Tracking number</label>
              <input
                id="tracking-number"
                className={styles.trackInput}
                placeholder="SD-XXXXXXXXXX"
                autoComplete="off"
                spellCheck={false}
                required
                value={number}
                onChange={(e) => setNumber(e.target.value)}
              />
              <button type="submit" className={styles.trackButton}>Track parcel</button>
            </form>
            <p className={styles.hint}>Got a link by SMS? Just open it; it shows your parcel directly.</p>
          </div>
          <img className={styles.heroArt} src={routeIllustration} width={360} height={230} alt="" />
        </section>

        <section className={styles.cards} aria-label="What you can do">
          <article className={styles.card}>
            <h2>Send a parcel</h2>
            <p>Book a delivery in minutes, see the price first, and download your receipt.</p>
            <button type="button" className={styles.cardLink} onClick={() => navigate(loggedIn ? landingPage() : routes.signup)}>
              {loggedIn ? 'Go to my dashboard' : 'Create a free account'} →
            </button>
          </article>
          <article className={styles.card}>
            <h2>Sell with SwiftDrop</h2>
            <p>For shops and online stores: create orders, track them all, and connect your own system.</p>
            <button type="button" className={styles.cardLink} onClick={() => navigate(withNext(routes.signup, routes.merchantApply))}>
              Become a merchant →
            </button>
          </article>
          <article className={styles.card}>
            <h2>Receiving a parcel?</h2>
            <p>You never need an account. Use the tracking number or the link in your SMS.</p>
            <button type="button" className={styles.cardLink} onClick={() => document.getElementById('tracking-number')?.focus()}>
              Track now →
            </button>
          </article>
        </section>

        <section className={styles.steps} aria-labelledby="how-it-works">
          <h2 id="how-it-works" className={styles.sectionTitle}>How it works</h2>
          <ol className={styles.stepList}>
            {STEPS.map((step, index) => (
              <li key={step.title} className={styles.step}>
                <span className={styles.stepNumber} aria-hidden="true">{index + 1}</span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
        </section>
      </main>

      <footer className={styles.footer}>
        <span>© {new Date().getFullYear()} SwiftDrop</span>
        <span className={styles.footerLinks}>
          <button type="button" className={styles.footerLink} onClick={() => navigate(routes.publicTrack)}>Track</button>
          <button type="button" className={styles.footerLink} onClick={() => navigate(routes.login)}>Log in</button>
        </span>
      </footer>
    </div>
  );
}
