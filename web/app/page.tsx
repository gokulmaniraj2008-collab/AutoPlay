import Link from 'next/link';

export default function Home() {
  return (
    <main>
      <div className="page-head">
        <div>
          <p className="eyebrow">AUTOPLAY · MUSIC AUTOMATION</p>
          <h1>AutoPlay</h1>
          <p>Schedule your music automation and manage it from one simple dashboard.</p>
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
          <p>Configure your playlist and schedule from the Schedules page.</p>
        </div>
      </section>

      <section className="card info-card">
        <p className="eyebrow">HOW IT WORKS</p>
        <div className="steps">
          <div><strong>01</strong><span>Create a schedule</span></div>
          <div><strong>02</strong><span>Save your Spotify playlist</span></div>
          <div><strong>03</strong><span>Let your Android device execute it</span></div>
        </div>
      </section>
    </main>
  );
}
