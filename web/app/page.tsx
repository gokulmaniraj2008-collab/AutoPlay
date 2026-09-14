'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

const ONBOARDING_KEY = 'autoplay_web_onboarding_complete';

export default function Home() {
  const [ready, setReady] = useState(false);
  const [onboarding, setOnboarding] = useState(true);

  useEffect(() => {
    setOnboarding(localStorage.getItem(ONBOARDING_KEY) !== 'true');
    setReady(true);
  }, []);

  function getStarted() {
    localStorage.setItem(ONBOARDING_KEY, 'true');
    setOnboarding(false);
  }

  if (!ready) return null;

  if (onboarding) {
    return (
      <main className="welcome-shell">
        <section className="welcome-card card">
          <div className="brand-mark">♫</div>
          <p className="eyebrow">AUTOPLAY</p>
          <h1>Your music.<br />Your time.</h1>
          <p className="welcome-copy">Schedule music simply and keep your experience focused on what matters — listening.</p>

          <div className="feature-list">
            <div><span>✓</span><div><strong>Simple & ₹0</strong><small>No paid music automation service required.</small></div></div>
            <div><span>✓</span><div><strong>Local music on Android</strong><small>Choose music stored on your phone in the AutoPlay app.</small></div></div>
            <div><span>✓</span><div><strong>Web control</strong><small>Create and manage schedules from this dashboard.</small></div></div>
          </div>

          <button className="primary-button welcome-button" onClick={getStarted}>Get Started →</button>
          <p className="welcome-note">Best experience: use the Android app for actual local scheduled playback.</p>
        </section>
      </main>
    );
  }

  return (
    <main>
      <div className="page-head">
        <div>
          <div className="brand-row"><div className="mini-brand">♫</div><p className="eyebrow">AUTOPLAY · MUSIC AUTOMATION</p></div>
          <h1>AutoPlay</h1>
          <p>Simple music scheduling. Use the Android app for local phone playback and this dashboard to manage your schedules.</p>
        </div>
        <Link className="back" href="/schedules">Manage schedules</Link>
      </div>

      <section className="dashboard-grid">
        <div className="card hero-card">
          <p className="eyebrow">NEXT AUTOMATION</p>
          <div className="time">3:00 PM</div>
          <h2>Tamil playlist</h2>
          <p>Daily schedule · Android device</p>
          <Link className="primary-button" href="/schedules">Open schedules</Link>
        </div>

        <div className="card status-card">
          <div className="section-title">
            <div>
              <p className="eyebrow">STATUS</p>
              <h2>Ready</h2>
            </div>
            <span className="status-dot" aria-label="Ready" />
          </div>
          <p>Your schedules are managed in the cloud and can be used by the AutoPlay Android app.</p>
          <div className="status-badge">₹0 · Local playback</div>
        </div>
      </section>

      <section className="card info-card">
        <p className="eyebrow">HOW IT WORKS</p>
        <div className="steps">
          <div><strong>01</strong><span>Choose music on Android</span></div>
          <div><strong>02</strong><span>Create a schedule here</span></div>
          <div><strong>03</strong><span>Android plays it at the set time</span></div>
        </div>
      </section>

      <button className="reset-onboarding" onClick={() => { localStorage.removeItem(ONBOARDING_KEY); setOnboarding(true); }}>Show Get Started again</button>
    </main>
  );
}
