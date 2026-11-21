import React from 'react';
import type { DashboardStats } from '../../types/courier';

interface ProgressCardProps {
  stats: DashboardStats;
}

export const ProgressCard: React.FC<ProgressCardProps> = ({ stats }) => {
  const radius = 44;
  const circumference = 2 * Math.PI * radius;
  const pct = Math.min(100, Math.max(0, stats.completionPercentage));
  const offset = circumference - (pct / 100) * circumference;

  const total = Math.max(1, stats.assignedToday || (stats.inProgress + stats.delivered + stats.failed));
  const deliveredWidth = Math.round((stats.delivered / total) * 100);
  const inProgressWidth = Math.round((stats.inProgress / total) * 100);
  const failedWidth = Math.round((stats.failed / total) * 100);

  return (
    <div className="content-card progress-card-widget">
      <div className="card-header-row">
        <div>
          <h2 className="card-title">Shift Progress</h2>
          <span className="card-subtitle-small">Real-time delivery fulfillment</span>
        </div>
        <span className="progress-fraction-badge">
          <strong>{stats.delivered}</strong> / {stats.assignedToday} parcels
        </span>
      </div>

      <div className="progress-card-inner">
        <div className="progress-visual-row">
          {/* Circular Donut Ring with SVG Gradient */}
          <div className="progress-donut" aria-label={`Completion rate: ${pct}%`}>
            <svg width="112" height="112" viewBox="0 0 112 112">
              <defs>
                <linearGradient id="donutProgressGrad" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stopColor="#F4B942" />
                  <stop offset="100%" stopColor="#D9961A" />
                </linearGradient>
              </defs>
              {/* Background Track */}
              <circle
                cx="56"
                cy="56"
                r={radius}
                fill="none"
                stroke="var(--border-subtle)"
                strokeWidth="11"
              />
              {/* Progress Arc */}
              <circle
                cx="56"
                cy="56"
                r={radius}
                fill="none"
                stroke="url(#donutProgressGrad)"
                strokeWidth="11"
                strokeDasharray={circumference}
                strokeDashoffset={offset}
                strokeLinecap="round"
                transform="rotate(-90 56 56)"
                style={{ transition: 'stroke-dashoffset 0.8s cubic-bezier(0.4, 0, 0.2, 1)' }}
              />
            </svg>
            <div className="progress-donut-center">
              <span className="donut-pct">{pct}%</span>
              <span className="donut-sub">DONE</span>
            </div>
          </div>

          {/* Breakdown Legend with visual mini bars */}
          <div className="progress-breakdown-list">
            <div className="progress-breakdown-item">
              <div className="breakdown-header">
                <span className="breakdown-label">
                  <span className="breakdown-dot green" />
                  Delivered
                </span>
                <span className="breakdown-count">{stats.delivered}</span>
              </div>
              <div className="breakdown-mini-track">
                <div className="breakdown-mini-fill green" style={{ width: `${deliveredWidth}%` }} />
              </div>
            </div>

            <div className="progress-breakdown-item">
              <div className="breakdown-header">
                <span className="breakdown-label">
                  <span className="breakdown-dot blue" />
                  In Progress
                </span>
                <span className="breakdown-count">{stats.inProgress}</span>
              </div>
              <div className="breakdown-mini-track">
                <div className="breakdown-mini-fill blue" style={{ width: `${inProgressWidth}%` }} />
              </div>
            </div>

            <div className="progress-breakdown-item">
              <div className="breakdown-header">
                <span className="breakdown-label">
                  <span className="breakdown-dot red" />
                  Failed
                </span>
                <span className="breakdown-count">{stats.failed}</span>
              </div>
              <div className="breakdown-mini-track">
                <div className="breakdown-mini-fill red" style={{ width: `${failedWidth}%` }} />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
