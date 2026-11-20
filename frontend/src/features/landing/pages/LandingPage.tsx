import { useState, type FormEvent } from 'react';
import logoIcon from '../../../assets/brand/logo-mark.svg';
import { navigate, routes } from '../../../app/router';
import { forgetNext, landingPage, withNext } from '../../auth/lib/landing';
import { getAccessToken } from '../../auth/lib/session';
import { Icon, type IconName } from '../../../shared/ui/Icon';
import styles from './LandingPage.module.css';

const STEPS = [
  { title: 'Book', text: 'Enter pickup and delivery details and see the fee before you confirm.' },
  { title: 'We pick up', text: 'A SwiftDrop courier collects the parcel from your door or shop.' },
  { title: 'Delivered', text: 'Follow every step live. The receiver gets a tracking link by SMS.' },
];

const FEATURES: { icon: IconName; title: string; text: string }[] = [
  { icon: 'pin', title: 'Live tracking', text: 'Every scan, from pickup to door' },
  { icon: 'message', title: 'SMS & email updates', text: 'Sender and receiver stay informed' },
  { icon: 'shield', title: 'Verified merchants', text: 'Businesses are checked before they sell' },
  { icon: 'truck', title: 'Nationwide couriers', text: 'One network across the island' },
];

// The example card in the hero: a picture of what tracking looks like, not real data
const PREVIEW = [
  { label: 'Booked', time: '09:12', done: true },
  { label: 'Picked up', time: '10:40', done: true },
  { label: 'In transit', time: '13:05', current: true },
  { label: 'Out for delivery', time: '—' },
  { label: 'Delivered', time: '—' },
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

  const sendTarget = () => navigate(loggedIn ? landingPage() : routes.signup);
  const merchantTarget = () => navigate(withNext(routes.signup, routes.merchantApply));

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <a className={styles.logo} href="/" onClick={(e) => { e.preventDefault(); navigate('/'); }}>
            <span className={styles.logoMark}><img src={logoIcon} width={20} height={20} alt="" /></span>
            SwiftDrop
          </a>
          <nav className={styles.nav} aria-label="Account">
            <span className={styles.navLinks}>
              <a className={styles.navLink} href="#tracking-number"><Icon name="search" size={15} />Track</a>
              <a className={styles.navLink} href="#how-it-works"><Icon name="truck" size={15} />How it works</a>
              <a className={styles.navLink} href="#business"><Icon name="store" size={15} />For business</a>
            </span>
            {loggedIn ? (
              <button type="button" className={styles.primaryButton} onClick={() => navigate(landingPage())}>
                My dashboard
              </button>
            ) : (
              <>
                <button type="button" className={styles.textButton} onClick={() => { forgetNext(); navigate(routes.login); }}>Log in</button>
                <button type="button" className={styles.primaryButton} onClick={() => { forgetNext(); navigate(routes.signup); }}>Sign up</button>
              </>
            )}
          </nav>
        </div>
      </header>

      <main>
        <section className={styles.hero} aria-labelledby="hero-title">
          <div className={styles.heroText}>
            <span className={styles.eyebrow}><span className={styles.liveDot} aria-hidden="true" />Parcel tracking · no account needed</span>
            <h1 id="hero-title" className={styles.headline}>Where is my <em>parcel?</em></h1>
            <p className={styles.tagline}>
              Enter the tracking number from your sender or SMS to see where your parcel is right now.
            </p>
            <form className={styles.trackForm} onSubmit={track} role="search">
              <label htmlFor="tracking-number" className={styles.srOnly}>Tracking number</label>
              <span className={styles.trackIcon}><Icon name="search" /></span>
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
              <button type="submit" className={styles.trackButton}>Track parcel <Icon name="arrowRight" size={18} /></button>
            </form>
            <p className={styles.hint}>Got a link by SMS? Just open it; it shows your parcel directly.</p>
          </div>

          <div className={styles.previewWrap}>
          {/* Floating parcels: pure CSS 3D boxes, decorative only, still when "reduce motion" is on */}
          {(['cubeA', 'cubeB', 'cubeC'] as const).map((cube) => (
            <span key={cube} className={`${styles.cube} ${styles[cube]}`} aria-hidden="true">
              <span className={styles.cubeInner}>
                {(['faceFront', 'faceBack', 'faceRight', 'faceLeft', 'faceTop', 'faceBottom'] as const).map((face) => (
                  <span key={face} className={`${styles.face} ${styles[face]}`} />
                ))}
              </span>
            </span>
          ))}
          <figure className={styles.preview} aria-label="Example of a tracked parcel">
            <div className={styles.previewHead}>
              <div>
                <span className={styles.previewLabel}>Tracking number</span>
                <strong className={styles.previewCode}>SD-EXAMPLE000</strong>
              </div>
              <span className={styles.previewBadge}><Icon name="truck" size={14} />In transit</span>
            </div>
            <div className={styles.previewRoute}>
              <span><small>From</small>Colombo</span>
              <span className={styles.previewLine} aria-hidden="true" />
              <span><small>To</small>Kandy</span>
            </div>
            <ol className={styles.previewSteps}>
              {PREVIEW.map((step) => (
                <li key={step.label} className={step.done ? styles.stepDone : step.current ? styles.stepCurrent : undefined}>
                  <span className={styles.previewDot} aria-hidden="true">{step.done && <Icon name="check" size={12} />}</span>
                  <span className={styles.previewStep}>{step.label}</span>
                  <span className={styles.previewTime}>{step.time}</span>
                </li>
              ))}
            </ol>
            <figcaption className={styles.previewCaption}>Example only, not a real parcel</figcaption>
          </figure>
          </div>
        </section>

        <section className={styles.features} aria-label="Why SwiftDrop">
          {FEATURES.map((feature) => (
            <div key={feature.title} className={styles.feature}>
              <span className={styles.featureIcon}><Icon name={feature.icon} /></span>
              <div>
                <strong>{feature.title}</strong>
                <span>{feature.text}</span>
              </div>
            </div>
          ))}
        </section>

        <section className={styles.cards} aria-labelledby="what-you-can-do">
          <h2 id="what-you-can-do" className={styles.sectionTitle}>One service, three ways to use it</h2>
          <div className={styles.cardGrid}>
            <article className={`${styles.card} ${styles.cardFeatured}`}>
              <span className={styles.cardIcon}><Icon name="send" /></span>
              <h3>Send a parcel</h3>
              <p>Book a delivery in minutes, see the price first, and download your receipt.</p>
              <button type="button" className={styles.cardLink} onClick={sendTarget}>
                {loggedIn ? 'Go to my dashboard' : 'Create a free account'} <Icon name="arrowRight" size={16} />
              </button>
            </article>
            <article className={styles.card}>
              <span className={styles.cardIcon}><Icon name="store" /></span>
              <h3>Sell with SwiftDrop</h3>
              <p>For shops and online stores: create orders, track them all, and connect your own system.</p>
              <button type="button" className={styles.cardLink} onClick={merchantTarget}>
                Become a merchant <Icon name="arrowRight" size={16} />
              </button>
            </article>
            <article className={styles.card}>
              <span className={styles.cardIcon}><Icon name="package" /></span>
              <h3>Receiving a parcel?</h3>
              <p>You never need an account. Use the tracking number or the link in your SMS.</p>
              <button type="button" className={styles.cardLink} onClick={() => document.getElementById('tracking-number')?.focus()}>
                Track now <Icon name="arrowRight" size={16} />
              </button>
            </article>
          </div>
        </section>

        <section id="how-it-works" className={styles.steps} aria-labelledby="how-it-works-title">
          <div className={styles.stepsInner}>
            <span className={styles.eyebrowPlain}>How it works</span>
            <h2 id="how-it-works-title" className={styles.sectionTitle}>From your door to theirs in three steps</h2>
            <ol className={styles.stepList}>
              {STEPS.map((step, index) => (
                <li key={step.title} className={styles.step}>
                  <span className={styles.stepNumber} aria-hidden="true">{index + 1}</span>
                  <h3>{step.title}</h3>
                  <p>{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="business" className={styles.business} aria-labelledby="business-title">
          <div className={styles.businessText}>
            <span className={styles.eyebrowPlain}>For business</span>
            <h2 id="business-title">Ship at scale. Connect your own system.</h2>
            <p>Create orders, follow every parcel on one dashboard, and get status updates pushed to your store by webhook.</p>
            <button type="button" className={styles.primaryButton} onClick={merchantTarget}>Become a merchant</button>
          </div>
          <ul className={styles.businessList}>
            <li><Icon name="store" /> Merchant dashboard for all your orders</li>
            <li><Icon name="code" /> API access and webhooks</li>
            <li><Icon name="zap" /> Status changes delivered in real time</li>
          </ul>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <span className={styles.footerBrand}>
            <span className={styles.logoMark}><img src={logoIcon} width={16} height={16} alt="" /></span>
            © {new Date().getFullYear()} SwiftDrop
          </span>
          <span className={styles.footerLinks}>
            <button type="button" className={styles.footerLink} onClick={() => navigate(routes.publicTrack)}>Track</button>
            <button type="button" className={styles.footerLink} onClick={() => { forgetNext(); navigate(routes.login); }}>Log in</button>
            <button type="button" className={styles.footerLink} onClick={merchantTarget}>Become a merchant</button>
          </span>
        </div>
      </footer>
    </div>
  );
}
