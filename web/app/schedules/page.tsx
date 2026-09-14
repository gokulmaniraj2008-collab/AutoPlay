'use client';

import { FormEvent, useEffect, useState } from 'react';
import { supabase, Schedule } from '../../lib/supabase';

const EMPTY = { name: 'Morning music', time: '07:00', playlist_url: '', enabled: true };

function formatTime(value: string) {
  const match = value.match(/^(\d{1,2}):(\d{2})/);
  if (!match) return value;
  const hour = Number(match[1]);
  const minute = match[2];
  const suffix = hour >= 12 ? 'PM' : 'AM';
  return `${hour % 12 || 12}:${minute} ${suffix}`;
}

export default function SchedulesPage() {
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<Schedule | null>(null);
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
    const { error } = await supabase.from('schedules').insert({
      name: form.name.trim(), time: form.time, playlist_url: form.playlist_url.trim(), enabled: form.enabled,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    });
    if (error) setMessage(error.message); else { setForm(EMPTY); setMessage('Schedule created.'); await load(); }
    setSaving(false);
  }

  async function saveEdit(event: FormEvent) {
    event.preventDefault();
    if (!supabase || !editing) return;
    setSaving(true); setMessage('');
    const { error } = await supabase.from('schedules').update({
      name: editing.name.trim(), time: editing.time.slice(0, 5), playlist_url: editing.playlist_url.trim(), enabled: editing.enabled,
      updated_at: new Date().toISOString(),
    }).eq('id', editing.id);
    if (error) setMessage(error.message); else { setEditing(null); setMessage('Schedule updated.'); await load(); }
    setSaving(false);
  }

  async function toggle(id: string, enabled: boolean) {
    if (!supabase) return;
    const { error } = await supabase.from('schedules').update({ enabled: !enabled, updated_at: new Date().toISOString() }).eq('id', id);
    if (error) setMessage(error.message); else load();
  }

  async function remove(id: string) {
    if (!supabase || !window.confirm('Delete this schedule? This cannot be undone.')) return;
    const { error } = await supabase.from('schedules').delete().eq('id', id);
    if (error) setMessage(error.message); else { setMessage('Schedule deleted.'); load(); }
  }

  return (
    <main>
      <div className="page-head">
        <div><p className="eyebrow">AUTOPLAY · CLOUD SYNC</p><h1>Schedules</h1><p>Create, view, edit, enable, disable and delete automations.</p></div>
        <a className="back" href="/">Dashboard</a>
      </div>

      <section className="grid">
        <form className="card form-card" onSubmit={addSchedule}>
          <h2>Create schedule</h2>
          <label>Name<input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required /></label>
          <label>Time<input type="time" value={form.time} onChange={e => setForm({ ...form, time: e.target.value })} required /></label>
          <label>Spotify URL <span>(optional)</span><input type="url" placeholder="Optional — local Android music needs no URL" value={form.playlist_url} onChange={e => setForm({ ...form, playlist_url: e.target.value })} /></label>
          <button disabled={saving}>{saving ? 'Creating…' : 'Create schedule'}</button>
          {message && <p className="message">{message}</p>}
        </form>

        <section className="card">
          <div className="section-title"><div><h2>Your schedules</h2><p>Shared through Supabase with Android</p></div><span className="pill">{schedules.length}</span></div>
          {loading ? <p>Loading…</p> : schedules.length === 0 ? <div className="empty">No schedules yet. Create your first one.</div> : <div className="schedule-list">{schedules.map(item => (
            <article className="schedule" key={item.id}>
              <div><strong>{formatTime(item.time)}</strong><h3>{item.name}</h3><span className="status">{item.enabled ? '● Enabled' : '○ Disabled'}</span>{item.playlist_url && <a href={item.playlist_url} target="_blank" rel="noreferrer">Open Spotify playlist</a>}</div>
              <div className="actions"><button className="secondary" onClick={() => toggle(item.id, item.enabled)}>{item.enabled ? 'Disable' : 'Enable'}</button><button className="secondary" onClick={() => setEditing({ ...item })}>Edit</button><button className="danger" onClick={() => remove(item.id)}>Delete</button></div>
            </article>
          ))}</div>}
        </section>
      </section>

      {editing && <div className="modal-backdrop">
        <form className="card modal" onSubmit={saveEdit}>
          <h2>Edit schedule</h2>
          <p>Changes sync to the Android app.</p>
          <label>Name<input value={editing.name} onChange={e => setEditing({ ...editing, name: e.target.value })} required /></label>
          <label>Time<input type="time" value={editing.time.slice(0, 5)} onChange={e => setEditing({ ...editing, time: e.target.value })} required /></label>
          <label>Spotify URL <span>(optional)</span><input type="url" value={editing.playlist_url || ''} onChange={e => setEditing({ ...editing, playlist_url: e.target.value })} /></label>
          <label className="check"><input type="checkbox" checked={editing.enabled} onChange={e => setEditing({ ...editing, enabled: e.target.checked })} /> Enabled</label>
          <div className="actions"><button type="button" className="secondary" onClick={() => setEditing(null)}>Cancel</button><button disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button></div>
        </form>
      </div>}
    </main>
  );
}
