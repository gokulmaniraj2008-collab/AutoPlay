'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';

const ONBOARDING_KEY = 'autoplay_web_onboarding_complete';

const examples = [
  { icon: '◎', label: 'Instagram', text: 'Open Instagram and change my bio to "Building with AI"' },
  { icon: '◉', label: 'WhatsApp', text: 'Open WhatsApp and send Praneesh: Hi' },
  { icon: '▶', label: 'YouTube', text: 'Open YouTube' },
  { icon: '♫', label: 'Spotify', text: 'At 8 PM, play a Tamil song on Spotify' },
];

export default function Home() {
  const [ready, setReady] = useState(false);
  const [onboarding, setOnboarding] = useState(true);
  const [command, setCommand] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    setOnboarding(localStorage.getItem(ONBOARDING_KEY) !== 'true');
    setReady(true);
  }, []);

  function getStarted() {
    localStorage.setItem(ONBOARDING_KEY, 'true');
    setOnboarding(false);
  }

  function submitTask() {
    const value = command.trim();
    if (!value) {
      setMessage('Type a phone task first.');
      return;
    }
    setMessage('Task prepared. Open the AutoPlay Android app to execute supported phone actions.');
  }

  if (!ready) return null;

  if (onboarding) {
    return (
      <main className="welcome-shell">
        <section className="welcome-card card">
          <div className="brand-mark">▶</div>
          <p className="eyebrow">AUTOPLAY · AI PHONE AUTOMATION</p>
          <h1>Your phone.<br />Your command.</h1>
          <p className="welcome-copy">Tell AutoPlay what you want your phone to do. Schedule tasks, open apps and automate supported phone actions from one place.</p>

          <div className="feature-list">
            <div><span>✓</span><div><strong>Natural-language tasks</strong><small>Write commands like “Open YouTube” or “Open WhatsApp and send Praneesh: Hi”.</small></div></div>
            <div><span>✓</span><div><strong>Scheduled automation</strong><small>Run supported phone tasks at a chosen time through the Android app.</small></div></div>
            <div><span>✓</span><div><strong>On-device execution</strong><small>Your Android phone performs supported app and UI actions after required permissions.</small></div></div>
          </div>

          <button className="primary-button welcome-button" onClick={getStarted}>Get Started <span>→</span></button>
          <p className="welcome-note">Some app actions require Android permissions and Accessibility access.</p>
        </section>
      </main>
    );
  }

  return (
    <main>
      <header className="page-head">
        <div className="page-brand">
          <div className="brand-row">
            <div className="mini-brand">▶</div>
            <div>
              <p className="eyebrow">AUTOPLAY · AI PHONE AUTOMATION</p>
              <span className="brand-subtitle">Command center</span>
            </div>
          </div>
          <h1>AutoPlay</h1>
          <p>Describe what you want your phone to do. AutoPlay prepares supported tasks for your Android device.</p>
        </div>
        <Link className="back" href="/schedules">Manage schedules <span>→</span></Link>
      </header>

      <section className="card command-card">
        <div className="section-title command-heading">
          <div>
            <div className="eyebrow-row"><p className="eyebrow">AI TASK COMMAND</p><span className="status-badge compact">AI ready</span></div>
            <h2>What should your phone do?</h2>
            <p className="section-help">Type a command or choose a quick action below.</p>
          </div>
        </div>

        <div className="command-row">
          <input
            value={command}
            onChange={(event) => { setCommand(event.target.value); setMessage(''); }}
            onKeyDown={(event) => { if (event.key === 'Enter') submitTask(); }}
            placeholder="Try: Open YouTube"
            aria-label="Phone automation command"
          />
          <button className="primary-button" onClick={submitTask}>Prepare task <span>→</span></button>
        </div>

        {message && <p className="message" role="status">{message}</p>}

        <div className="quick-actions">
          <div className="quick-actions-head">
            <span>Quick actions</span>
            <small>Tap to edit the command</small>
          </div>
          <div className="example-chips">
            {examples.map((example) => (
              <button key={example.text} className="example-chip" onClick={() => { setCommand(example.text); setMessage(''); }}>
                <span className="chip-icon">{example.icon}</span>
                <span className="chip-copy"><strong>{example.label}</strong><small>{example.text}</small></span>
                <span className="chip-arrow">›</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="automation-grid">
        <div className="card status-card">
          <div className="section-title">
            <div>
              <p className="eyebrow">PHONE CONNECTION</p>
              <h2>Android app required</h2>
            </div>
            <span className="status-dot" aria-label="Android app required" />
          </div>
          <p>The website is your command dashboard. The Android app performs supported phone actions on-device.</p>
          <div className="connection-status"><span className="status-dot small" /> Permissions · Check on Android</div>
        </div>

        <div className="card">
          <p className="eyebrow">SUPPORTED AUTOMATION</p>
          <div className="automation-list">
            <div><strong>💬 WhatsApp</strong><span>Open chats and prepare supported messages.</span></div>
            <div><strong>📸 Instagram</strong><span>Supported UI automation can handle profile actions after Accessibility permission.</span></div>
            <div><strong>▶ YouTube</strong><span>Launch the app from a natural-language command.</span></div>
            <div><strong>🎵 Spotify</strong><span>Open Spotify and manage supported scheduled music actions.</span></div>
            <div><strong>📞 Calls</strong><span>Start calls when Android call permission is granted.</span></div>
            <div><strong>⏰ Scheduling</strong><span>Set future execution times for supported tasks.</span></div>
          </div>
        </div>
      </section>

      <section className="card info-card">
        <p className="eyebrow">HOW AUTOPLAY WORKS</p>
        <div className="steps">
          <div><strong>01</strong><span>Describe the task in normal language</span></div>
          <div><strong>02</strong><span>AutoPlay prepares the supported phone action</span></div>
          <div><strong>03</strong><span>Open or trigger the Android task</span></div>
          <div><strong>04</strong><span>Android executes supported intents or UI automation</span></div>
        </div>
      </section>

      <section className="card safety-card">
        <p className="eyebrow">IMPORTANT</p>
        <h2>Phone automation stays on your device</h2>
        <p>The website can create and prepare commands, while actual app interaction happens through the AutoPlay Android app. Instagram and WhatsApp UI automation may require Accessibility permission and can change when those apps update their interfaces.</p>
      </section>

      <button className="reset-onboarding" onClick={() => { localStorage.removeItem(ONBOARDING_KEY); setOnboarding(true); }}>Show Get Started again</button>
    </main>
  );
}
