'use client';

import { FormEvent, useEffect, useState } from 'react';
import { supabase, Schedule } from '../../lib/supabase';

function today() {
  return new Date().toISOString().slice(0, 10);
}

const EMPTY = { name: 'Morning music', scheduled_date: today(), time: '07:00', enabled: true };

function formatTime(value: string) {
  const match = value.match(/^(\d{1,2}):(\d{2})/);
  if (!match) return value;
  const hour = Number(match[1]);
  const minute = match[2];
  const suffix = hour >= 12 ? 'PM' : 'AM';
  return `${hour % 12 || 12}:${minute} ${suffix}`;
}

function formatDate(value?: string | null) {
  if (!value) return 'Every day';
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatScheduleDateTime(date?: string | null, time?: string) {
  return `${formatDate(date)} · ${formatTime(time || '')}`;
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
    const { data, error } = await supabase.from('schedules').select('*').order('scheduled_date', { ascending: true, nullsFirst: false }).order('time');
    if (error) setMessage(error.message); else setSchedules(data ?? []);
    setLoading(false);
  }

  useEffect(() => { load(); }, []);

  async function addSchedule(event: FormEvent) {
    event.preventDefault();
    if (!supabase) return setMessage('Missing Supabase environment variables.');
    setSaving(true); setMessage('');
    const { error } = await supabase.from('schedules').insert({
      name: form.name.trim(), scheduled_date: form.scheduled_date || null, time: form.time, playlist_url: '', enabled: form.enabled,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    });
    if (error) setMessage(error.message); else { setForm({ ...EMPTY, scheduled_date: today() }); setMessage(`Schedule created for ${formatScheduleDateTime(form.scheduled_date, form.time)}. Select music on the Android app.`); await load(); }
    setSaving(false);
  }

  async function saveEdit(event: FormEvent) {
    event.preventDefault();
    if (!supabase || !editing) return;
    setSaving(true); setMessage('');
    const { error } = await supabase.from('schedules').update({
      name: editing.name.trim(), scheduled_date: editing.scheduled_date || null, time: editing.time.slice(0, 5), enabled: editing.enabled,
      updated_at: new Date().toISOString(),
    }).eq('id', editing.id);
    if (error) setMessage(error.message); else { setEditing(null); setMessage(`Schedule updated to ${formatScheduleDateTime(editing.scheduled_date, editing.time)}.`); await load(); }
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
        <div><p className="eyebrow">AUTOPLAY · CLOUD SYNC</p><h1>Schedules</h1><p>Create, view, edit, enable, disable and delete automations with a date and exact time.</p></div>
        <a className="back" href="/">Dashboard</a>
      </div>

      <section className="grid">
        <form className="card form-card" onSubmit={addSchedule}>
          <h2>Create schedule</h2>
          <label>Name<input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required /></label>
          <label>Date<input type="date" value={form.scheduled_date} min={today()} onChange={e => setForm({ ...form, scheduled_date: e.target.value })} required /></label>
          <label>Time<input type="time" value={form.time} onChange={e => setForm({ ...form, time: e.target.value })} required /></label>
          <p className="message">🎵 Music is selected from the Android phone. No Spotify URL is required.</p>
          <button disabled={saving}>{saving ? 'Creating…' : 'Create schedule'}</button>
          {message && <p className="message">{message}</p>}
        </form>

        <section className="card">
          <div className="section-title"><div><h2>Your schedules</h2><p>Shared through Supabase with Android</p></div><span className="pill">{schedules.length}</span></div>
          {loading ? <p>Loading…</p> : schedules.length === 0 ? <div className="empty">No schedules yet. Create your first one.</div> : <div className="schedule-list">{schedules.map(item => (
            <article className="schedule" key={item.id}>
              <div><strong>📅 {formatDate(item.scheduled_date)}</strong><h3>⏰ {formatTime(item.time)}</h3><span>{item.name}</span><br /><span className="status">{item.enabled ? '● Enabled' : '○ Disabled'}</span></div>
              <div className="actions"><button className="secondary" onClick={() => toggle(item.id, item.enabled)}>{item.enabled ? 'Disable' : 'Enable'}</button><button className="secondary" onClick={() => setEditing({ ...item })}>Edit</button><button className="danger" onClick={() => remove(item.id)}>Delete</button></div>
            </article>
          ))}</div>}
        </section>
      </section>

      {editing && <div className="modal-backdrop">
        <form className="card modal" onSubmit={saveEdit}>
          <h2>Edit schedule</h2>
          <p>Changes sync to the Android app. Music is selected locally on Android.</p>
          <label>Name<input value={editing.name} onChange={e => setEditing({ ...editing, name: e.target.value })} required /></label>
          <label>Date<input type="date" value={editing.scheduled_date || ''} min={today()} onChange={e => setEditing({ ...editing, scheduled_date: e.target.value || null })} required /></label>
          <label>Time<input type="time" value={editing.time.slice(0, 5)} onChange={e => setEditing({ ...editing, time: e.target.value })} required /></label>
          <label className="check"><input type="checkbox" checked={editing.enabled} onChange={e => setEditing({ ...editing, enabled: e.target.checked })} /> Enabled</label>
          <div className="actions"><button type="button" className="secondary" onClick={() => setEditing(null)}>Cancel</button><button disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button></div>
        </form>
      </div>}
    </main>
  );
}
