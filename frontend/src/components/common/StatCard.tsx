import React from 'react';

interface StatCardProps {
  label: string;
  value: number | string;
  variant: 'gold' | 'blue' | 'green' | 'red';
  icon: React.ReactNode;
  subtitle?: string;
  progressPercent?: number;
}

export const StatCard: React.FC<StatCardProps> = ({
  label,
  value,
  variant,
  icon,
  subtitle,
  progressPercent,
}) => {
  return (
    <div className={`stat-card stat-card-${variant}`}>
      <div className="stat-card-header">
        <div className={`stat-icon-wrapper ${variant}`}>
          {icon}
        </div>
        {typeof progressPercent === 'number' && (
          <span className="stat-badge-chip">
            {progressPercent}%
          </span>
        )}
      </div>

      <div className="stat-content">
        <span className="stat-value">{value}</span>
        <span className="stat-label">{label}</span>
        {subtitle && <span className="stat-subtitle">{subtitle}</span>}
      </div>

      {typeof progressPercent === 'number' && (
        <div className="stat-mini-progress-track">
          <div
            className={`stat-mini-progress-bar ${variant}`}
            style={{ width: `${Math.min(100, Math.max(0, progressPercent))}%` }}
          />
        </div>
      )}
    </div>
  );
};
