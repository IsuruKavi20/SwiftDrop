import { MerchantLink } from '../components/MerchantLink';
import { merchantPath, type MerchantPageId } from '../merchantRoutes';
import styles from './MerchantPages.module.css';

const unavailablePages = {
  'api-credentials': {
    title: 'API credentials are unavailable',
    description: 'Credential management is not connected. Keys cannot be issued, viewed, or revoked from this portal.',
  },
  webhooks: {
    title: 'Webhook management is unavailable',
    description: 'This portal cannot register endpoints or show webhook delivery history yet.',
  },
  settings: {
    title: 'Portal preferences are unavailable',
    description: 'Portal preference management is not connected. You can update your business contact and address information in Business Profile.',
    link: 'business-profile', linkText: 'Go to Business Profile',
  },
} as const;

type UnavailablePage = keyof typeof unavailablePages;

export function MerchantUnavailablePage({ page, preview }: { page: UnavailablePage; preview: boolean }) {
  const content = unavailablePages[page];
  const link: MerchantPageId | undefined = 'link' in content ? content.link : undefined;
  return (
    <section className={styles.panel}>
      <h2>{content.title}</h2>
      <p>{content.description}</p>
      {link && 'linkText' in content && <MerchantLink className={styles.button} href={merchantPath(link, preview)}>{content.linkText}</MerchantLink>}
    </section>
  );
}
