'use client';

import { FormEvent, useEffect, useState } from 'react';
import { supabase, Schedule } from '../../lib/supabase';

const EMPTY = { name: 'Tamil playlist', time: '15:00', playlist_url: '', enabled: true };

export default function SchedulesPage() {
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  async function load() {
    if (!supabase) { setMessage('Connect Supabase to sync schedules.'); setLoading(false); return; }
    const { data, error } = await supabase.from('schedules').select('*').order('time');
    if (error) setMessage(error.message); else setSchedules(data ?? []);
    setLoading(false);
  }

  useEffect(() => { load(); }, []);

  async function addSchedule(event: FormEvent) {
    event.preventDefault();
    if (!supabase) return setMessage('Missing Supabase environment variables.');
    setSaving(true); setMessage('');
    const { error } = await supabase.from('schedules').insert({ ...form, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone });
    if (error) setMessage(error.message); else { setForm(EMPTY); setMessage('Schedule synced to Supabase.'); await load(); }
    setSaving(false);
  }

  async function toggle(id: string, enabled: boolean) {
    if (!supabase) return;
    const { error } = await supabase.from('schedules').update({ enabled: !enabled, updated_at: new Date().toISOString() }).eq('id', id);
    if (error) setMessage(error.message); else load();
  }

  async function remove(id: string) {
    if (!supabase) return;
    const { error } = await supabase.from('schedules').delete().eq('id', id);
    if (error) setMessage(error.message); else { setMessage('Schedule deleted.'); load(); }
  }

  return (
    <main>
      <div className="page-head">
        <div><p className="eyebrow">AUTOPLAY · CLOUD SYNC</p><h1>Schedules</h1><p>Create and manage your music automations from the web.</p></div>
        <a className="back" href="/">Dashboard</a>
      </div>

      <section className="grid">
        <form className="card form-card" onSubmit={addSchedule}>
          <h2>Add schedule</h2>
          <label>Name<input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required /></label>
          <label>Time<input type="time" value={form.time} onChange={e => setForm({ ...form, time: e.target.value })} required /></label>
          <label>Spotify playlist URL<input type="url" placeholder="https://open.spotify.com/playlist/..." value={form.playlist_url} onChange={e => setForm({ ...form, playlist_url: e.target.value })} required /></label>
          <button disabled={saving}>{saving ? 'Syncing…' : 'Save & sync'}</button>
          {message && <p className="message">{message}</p>}
        </form>

        <section className="card">
          <div className="section-title"><div><h2>Your schedules</h2><p>Synced from Supabase</p></div><span className="pill">{schedules.length}</span></div>
          {loading ? <p>Loading…</p> : schedules.length === 0 ? <div className="empty">No schedules yet. Add your first automation.</div> : <div className="schedule-list">{schedules.map(item => (
            <article className="schedule" key={item.id}>
              <div><strong>{item.time}</strong><h3>{item.name}</h3><a href={item.playlist_url} target="_blank" rel="noreferrer">Open playlist</a></div>
              <div className="actions"><button className="secondary" onClick={() => toggle(item.id, item.enabled)}>{item.enabled ? 'Enabled' : 'Disabled'}</button><button className="danger" onClick={() => remove(item.id)}>Delete</button></div>
            </article>
          ))}</div>}
        </section>
      </section>
    </main>
  );
}
