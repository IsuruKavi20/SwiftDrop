import { AuthLayout } from '../../auth/components/AuthLayout';

/**
 * "Become a merchant" (route /merchant/apply). The login and sign-up pages send businesses here after logging in.
 * Placeholder: the merchant team builds the real form here (see docs/auth-api/merchant-screens.md, screens 1 and 2).
 */
export function MerchantApplyPage() {
  return (
    <AuthLayout headline="Sell with SwiftDrop." tagline="Deliveries for your business." eyebrow="MERCHANTS"
      title="Become a merchant" subtitle="The merchant application form is coming soon.">
      <p>You're logged in. Soon you'll apply here with your business details.</p>
    </AuthLayout>
  );
}
