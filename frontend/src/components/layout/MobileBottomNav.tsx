import React from 'react';
import { NavLink } from 'react-router-dom';
import { HomeIcon, PackageIcon, ClockIcon, BellIcon, UserIcon } from '../common/Icons';

export const MobileBottomNav: React.FC = () => {
  return (
    <nav className="mobile-bottom-nav" aria-label="Mobile Navigation">
      <NavLink
        to="/courier/dashboard"
        className={({ isActive }) => `mobile-nav-item ${isActive ? 'active' : ''}`}
      >
        <HomeIcon size={20} />
        <span>Dashboard</span>
      </NavLink>

      <NavLink
        to="/courier/deliveries"
        className={({ isActive }) => `mobile-nav-item ${isActive ? 'active' : ''}`}
      >
        <PackageIcon size={20} />
        <span>Deliveries</span>
      </NavLink>

      <NavLink
        to="/courier/history"
        className={({ isActive }) => `mobile-nav-item ${isActive ? 'active' : ''}`}
      >
        <ClockIcon size={20} />
        <span>History</span>
      </NavLink>

      <NavLink
        to="/courier/notifications"
        className={({ isActive }) => `mobile-nav-item ${isActive ? 'active' : ''}`}
      >
        <BellIcon size={20} />
        <span>Alerts</span>
      </NavLink>

      <NavLink
        to="/courier/profile"
        className={({ isActive }) => `mobile-nav-item ${isActive ? 'active' : ''}`}
      >
        <UserIcon size={20} />
        <span>Profile</span>
      </NavLink>
    </nav>
  );
};
