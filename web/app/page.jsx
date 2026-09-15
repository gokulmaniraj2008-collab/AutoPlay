'use client';

import { useEffect, useRef, useState } from 'react';
import { createClient } from '@supabase/supabase-js';
import { Menu, X, Mic, MicOff, Send, Power, ChevronUp, Search, Music2, CheckCircle2, Loader2, Wifi, Mail, LockKeyhole, UserPlus, LogIn, LogOut, Image, Library, Folder, Clock3, Puzzle, Pencil, Plus, Sparkles } from 'lucide-react';

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://bqrpgtxtmatxwtdpuoyh.supabase.co';
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';
const headers = {
  'Content-Type': 'application/json',
  apikey: SUPABASE_ANON_KEY,
  Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
};
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
});

async function askGemini(text) {
  const r = await fetch('/api/tommy-gemini', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) });
  const data = await r.json();
  if (!r.ok) {
    const details = data?.details;
    throw new Error(data?.error ? `${data.error}${details ? `: ${details}` : ''}` : `Gemini request failed (${r.status})`);
  }
  return data;
}

async function createCommand(ai, original) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/tommy_commands`, {
    method: 'POST', headers: { ...headers, Prefer: 'return=representation' },
    body: JSON.stringify({ channel: 'tommy-main', source: 'web', command: JSON.stringify({ ...ai, original }), status: 'pending' }),
  });
  const data = await r.json();
  if (!r.ok) throw new Error(data?.message || 'Could not send command to Android');
  return data[0];
}

async function getCommand(id) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/tommy_commands?select=*&id=eq.${encodeURIComponent(id)}&limit=1`, { headers });
  const data = await r.json();
  if (!r.ok) throw new Error('Could not read Tommy status');
  return data[0];
}

function AuthScreen() {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setMessage(''); setError('');
    try {
      if (mode === 'signup') {
        if (password.length < 6) throw new Error('Password must be at least 6 characters.');
        const { data, error: signUpError } = await supabase.auth.signUp({
          email: email.trim(), password,
          options: { data: { full_name: name.trim() }, emailRedirectTo: window.location.origin },
        });
        if (signUpError) throw signUpError;
        if (data.session) setMessage('Account created. Welcome to Tommy.');
        else setMessage('Account created. Check your email to confirm your account, then log in.');
      } else {
        const { error: signInError } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
        if (signInError) throw signInError;
      }
    } catch (e) {
      setError(e?.message || 'Authentication failed.');
    } finally { setBusy(false); }
  };

  return (
    <div className="authPage">
      <div className="authCard">
        <div className="authLogo">T</div>
        <div className="authEyebrow">TOMMY AI ASSISTANT</div>
        <h1>{mode === 'login' ? 'Welcome back' : 'Create your Tommy account'}</h1>
        <p>{mode === 'login' ? 'Sign in to continue to your personal AI assistant.' : 'Create your account and keep your Tommy session ready on your devices.'}</p>
        <div className="authTabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setError(''); setMessage(''); }}>Log in</button>
          <button className={mode === 'signup' ? 'active' : ''} onClick={() => { setMode('signup'); setError(''); setMessage(''); }}>Create account</button>
        </div>
        <form onSubmit={submit} className="authForm">
          {mode === 'signup' && <label><span>Name</span><div className="authInput"><UserPlus size={17}/><input value={name} onChange={e => setName(e.target.value)} placeholder="Your name" autoComplete="name" /></div></label>}
          <label><span>Email</span><div className="authInput"><Mail size={17}/><input type="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" /></div></label>
          <label><span>Password</span><div className="authInput"><LockKeyhole size={17}/><input type="password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 6 characters" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></div></label>
          <button className="authSubmit" disabled={busy}>{mode === 'login' ? <LogIn size={18}/> : <UserPlus size={18}/>} {busy ? 'Please wait…' : mode === 'login' ? 'Log in to Tommy' : 'Create account'}</button>
        </form>
        {message && <div className="authMessage">{message}</div>}
        {error && <div className="authError">{error}</div>}
        <div className="authSecure">Secure authentication powered by Supabase</div>
      </div>
    </div>
  );
}

function TommyMenu({ open, close, messages, onNewChat }) {
  const recent = messages.filter((m) => m.from === 'user').slice(-6).reverse();
  return (
    <>
      <div className={open ? 'menuOverlay open' : 'menuOverlay'} onClick={close} />
      <aside className={open ? 'sideMenu open' : 'sideMenu'} aria-hidden={!open}>
        <div className="sideTop">
          <div className="sideTitle"><div className="sideLogo">T</div><b>Tommy</b></div>
          <button className="sideIcon" onClick={close} aria-label="Close menu"><X size={21}/></button>
        </div>
        <div className="sideSearch"><Search size={18}/><span>Search Tommy</span></div>
        <div className="sideFeatures">
          <button><Sparkles size={22}/><span>Skills</span></button>
          <button><Library size={22}/><span>Memory</span></button>
          <button><Folder size={22}/><span>Projects</span></button>
          <button><Clock3 size={22}/><span>Scheduled</span></button>
          <button><Puzzle size={22}/><span>Plugins</span></button>
        </div>
        <div className="sideDivider" />
        <div className="sideRecentHeader"><span>Recent chats</span><button onClick={onNewChat} title="New chat"><Pencil size={18}/></button></div>
        <div className="sideRecent">
          {recent.length ? recent.map((m, i) => <button className={i === 0 ? 'recentItem active' : 'recentItem'} key={`${m.text}-${i}`} onClick={close}>{m.text}</button>) : <div className="recentEmpty">No chats yet</div>}
          <button className="seeAll" onClick={close}>See all…</button>
        </div>
        <div className="sideBottom">
          <button className="newChat" onClick={onNewChat}><Plus size={21}/> <span>Chat</span></button>
          <button className="tommyGo" onClick={close}>GO</button>
        </div>
      </aside>
    </>
  );
}

export default function HomePage() {
  const [session, setSession] = useState(undefined);
  const [on, setOn] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const [listening, setListening] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [text, setText] = useState('');
  const [messages, setMessages] = useState([{ from: 'tommy', text: 'Hi. Tommy is on. How can I help?', status: 'done' }]);
  const rec = useRef(null);

  useEffect(() => {
    let mounted = true;
    supabase.auth.getSession().then(({ data }) => { if (mounted) setSession(data.session); });
    const { data: listener } = supabase.auth.onAuthStateChange((_event, nextSession) => setSession(nextSession));
    return () => { mounted = false; listener.subscription.unsubscribe(); rec.current?.stop(); };
  }, []);

  const add = (from, message, status) => setMessages((m) => [...m, { from, text: message, status }]);

  const startVoice = () => {
    if (!on || processing) return;
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) { add('tommy', 'Voice recognition is not supported in this browser.', 'done'); return; }
    if (listening) { rec.current?.stop(); return; }
    const r = new SR(); rec.current = r; r.lang = 'en-IN'; r.interimResults = true; r.continuous = false;
    r.onstart = () => setListening(true); r.onend = () => setListening(false);
    r.onresult = (e) => { let value = ''; for (let i = e.resultIndex; i < e.results.length; i += 1) value += e.results[i][0].transcript; if (value) setText(value); };
    r.onerror = () => setListening(false); r.start();
  };

  const execute = async (q) => {
    setProcessing(true); add('user', q, 'done'); add('tommy', 'Thinking with Gemini…', 'processing');
    try {
      const ai = await askGemini(q); const row = await createCommand(ai, q);
      setMessages((m) => m.map((x, i) => i === m.length - 1 ? { ...x, text: ai.reply || 'Command sent to Android Tommy.', status: 'processing' } : x));
      let latest = row;
      for (let i = 0; i < 30; i += 1) { await new Promise((resolve) => setTimeout(resolve, 1000)); latest = await getCommand(row.id); if (latest.status === 'done' || latest.status === 'failed') break; }
      setMessages((m) => m.map((x, i) => i === m.length - 1 ? { ...x, text: latest.response || ai.reply || 'Tommy is waiting for Android.', status: 'done' } : x));
    } catch (e) {
      setMessages((m) => m.map((x, i) => i === m.length - 1 ? { ...x, text: `Tommy connection error: ${e.message}`, status: 'done' } : x));
    } finally { setProcessing(false); }
  };

  const send = () => { const q = text.trim(); if (!q || !on) return; setText(''); execute(q); };
  const quick = (q) => setText(q);
  const newChat = () => { setMessages([{ from: 'tommy', text: 'New Tommy chat started. How can I help?', status: 'done' }]); setText(''); setMenuOpen(false); };
  const logout = async () => { await supabase.auth.signOut(); setOn(false); };

  if (session === undefined) return <div className="authLoading"><div className="authLogo">T</div><Loader2 className="spin" size={20}/></div>;
  if (!session) return <AuthScreen />;

  return (
    <div className="app">
      <TommyMenu open={menuOpen} close={() => setMenuOpen(false)} messages={messages} onNewChat={newChat} />
      <header>
        <div className="headerLeft"><button className="menuButton" onClick={() => setMenuOpen(true)} aria-label="Open Tommy menu"><Menu size={22}/></button><div className="brand"><div className="logo">T</div><div><b>Tommy</b><span>AI Assistant</span></div></div></div>
        <div className="headerRight">
          <div className="connection"><Wifi size={14} /> Gemini + Supabase + Android</div>
          <span className="accountEmail">{session.user.email}</span>
          <button className="logout" onClick={logout} title="Log out"><LogOut size={16}/></button>
          <button className={on ? 'power on' : 'power'} onClick={() => { setOn(!on); setListening(false); }}><Power size={17} />{on ? 'ON' : 'OFF'}</button>
        </div>
      </header>
      <main>
        <div className="hero">
          <div className={listening ? 'orb listening' : 'orb'}><div className="orb-core">T</div></div>
          <div className="eyebrow">TOMMY CONTROL</div>
          <h1>{listening ? 'I’m listening…' : processing ? 'Tommy is working…' : 'What can I do for you?'}</h1>
          <p>{on ? (listening ? 'Speak your command now' : processing ? 'Gemini → Supabase → Android…' : 'Type a command or tap the microphone') : 'Tommy is off'}</p>
        </div>
        <section className="chat">{messages.map((m, i) => <div key={i} className={`msg ${m.from}`}><div>{m.text}{m.status === 'processing' && <Loader2 className="spin" size={14}/>}</div>{m.status === 'done' && m.from === 'tommy' && <CheckCircle2 size={14} className="doneIcon"/>}</div>)}</section>
        <div className="composer"><textarea value={text} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }} placeholder={on ? 'Ask Tommy anything…' : 'Turn Tommy on to start'}/><button className={listening ? 'mic active' : 'mic'} onClick={startVoice} disabled={!on || processing}>{listening ? <MicOff/> : <Mic/>}</button><button className="send" onClick={send} disabled={!on || processing || !text.trim()}><Send/></button></div>
        <div className="status"><span className={on ? 'dot live' : 'dot'}/>{on ? (listening ? 'Listening' : processing ? 'Executing on Android' : 'Tommy is on') : 'Tommy is off'}</div>
        <div className="quick"><button onClick={() => quick('Open Instagram Reels')}>Instagram Reels</button><button onClick={() => quick('Open Instagram and open comments')}>Instagram Comments</button><button onClick={() => quick('Open Google and search Tamil latest movie')}><Search size={15}/> Google search</button><button onClick={() => quick('Open Spotify and search Arabic Kuthu')}><Music2 size={15}/> Spotify</button></div>
      </main>
      <footer><ChevronUp size={15}/> Tommy web control • Gemini + Supabase • Android bridge</footer>
    </div>
  );
}
