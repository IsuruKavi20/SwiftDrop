import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { CourierLayout } from '../../components/layout/CourierLayout';
import { DashboardPage } from './DashboardPage';
import { MyDeliveriesPage } from './MyDeliveriesPage';
import { DeliveryDetailPage } from './DeliveryDetailPage';
import { HistoryPage } from './HistoryPage';
import { NotificationsPage } from './NotificationsPage';
import { ProfilePage } from './ProfilePage';
import '../../styles/courier/variables.css';
import '../../styles/courier/courier.css';

export function CourierApp() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/courier/dashboard" replace />} />
        <Route path="/courier" element={<Navigate to="/courier/dashboard" replace />} />

        {/* Courier Module Routes wrapped in CourierLayout */}
        <Route path="/courier" element={<CourierLayout />}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="deliveries" element={<MyDeliveriesPage />} />
          <Route path="deliveries/:trackingNumber" element={<DeliveryDetailPage />} />
          <Route path="history" element={<HistoryPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>

        {/* Catch-all route within courier */}
        <Route path="*" element={<Navigate to="/courier/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default CourierApp;
