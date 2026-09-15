'use client';

import { useState } from 'react';

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://bqrpgtxtmatxwtdpuoyh.supabase.co';
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';

export default function InstagramTestPage() {
  const [status, setStatus] = useState('Ready to test');
  const [busy, setBusy] = useState(false);

  const testInstagramReels = async () => {
    setBusy(true);
    setStatus('Sending Instagram Reels test…');
    try {
      const response = await fetch(`${SUPABASE_URL}/rest/v1/tommy_commands`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: SUPABASE_ANON_KEY,
          Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
          Prefer: 'return=representation',
        },
        body: JSON.stringify({
          channel: 'tommy-main',
          source: 'web-test',
          command: JSON.stringify({ action: 'OPEN_INSTAGRAM_REELS', original: 'TEST: Open Instagram Reels' }),
          status: 'pending',
        }),
      });

      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data?.message || 'Could not send test command');
      setStatus('Test sent. Instagram should open, then Reels should be selected.');
    } catch (error) {
      setStatus(`Test failed: ${error?.message || 'Unknown error'}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, background: '#fff', fontFamily: 'system-ui, sans-serif' }}>
      <section style={{ width: '100%', maxWidth: 460, padding: 28, border: '1px solid #e5e7eb', borderRadius: 24, textAlign: 'center', boxShadow: '0 12px 40px rgba(15,23,42,.08)' }}>
        <div style={{ width: 72, height: 72, margin: '0 auto 18px', borderRadius: 22, display: 'grid', placeItems: 'center', background: '#111827', color: '#fff', fontSize: 30, fontWeight: 700 }}>T</div>
        <h1 style={{ margin: 0, fontSize: 28, color: '#111827' }}>Tommy Instagram Test</h1>
        <p style={{ color: '#6b7280', lineHeight: 1.5 }}>Direct Android bridge test. Gemini is not used.</p>
        <button onClick={testInstagramReels} disabled={busy} style={{ width: '100%', marginTop: 18, padding: '16px 20px', border: 0, borderRadius: 16, background: '#111827', color: '#fff', fontSize: 17, fontWeight: 700, cursor: busy ? 'wait' : 'pointer', opacity: busy ? .65 : 1 }}>
          {busy ? 'Testing…' : '▶ Open Instagram Reels — TEST'}
        </button>
        <div style={{ marginTop: 18, padding: 14, borderRadius: 14, background: '#f8fafc', color: '#475569', fontSize: 14 }}>{status}</div>
        <p style={{ marginTop: 18, color: '#94a3b8', fontSize: 12 }}>Keep Tommy Android app running and Accessibility Service enabled.</p>
      </section>
    </main>
  );
}
