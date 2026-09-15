'use client';

import { useEffect, useRef, useState } from 'react';
import { createClient } from '@supabase/supabase-js';
import { Menu, X, Mic, MicOff, Send, Power, ChevronUp, Search, Music2, CheckCircle2, Loader2, Wifi, Mail, LockKeyhole, UserPlus, LogIn, LogOut, Library, Folder, Clock3, Puzzle, Pencil, Plus, Sparkles } from 'lucide-react';

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://bqrpgtxtmatxwtdpuoyh.supabase.co';
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';
const headers = { 'Content-Type': 'application/json', apikey: SUPABASE_ANON_KEY, Authorization: `Bearer ${SUPABASE_ANON_KEY}` };
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } });

async function askGemini(text) {
  const r = await fetch('/api/tommy-gemini', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) });
  const data = await r.json();
  if (!r.ok) { const details = data?.details; throw new Error(data?.error ? `${data.error}${details ? `: ${details}` : ''}` : `Gemini request failed (${r.status})`); }
  return data;
}

async function createCommand(ai, original) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/tommy_commands`, { method: 'POST', headers: { ...headers, Prefer: 'return=representation' }, body: JSON.stringify({ channel: 'tommy-main', source: 'web', command: JSON.stringify({ ...ai, original }), status: 'pending' }) });
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

async function createChatSession(userId, title = 'New Tommy chat') {
  const { data, error } = await supabase.from('tommy_chat_sessions').insert({ user_id: userId, title }).select().single();
  if (error) throw error;
  return data;
}

async function loadChatMessages(sessionId) {
  const { data, error } = await supabase.from('tommy_chat_messages').select('id,role,text,status,created_at').eq('session_id', sessionId).order('created_at', { ascending: true });
  if (error) throw error;
  return (data || []).map((m) => ({ id: m.id, from: m.role, text: m.text, status: m.status }));
}

async function saveChatMessage(sessionId, userId, role, text, status = 'done') {
  const { data, error } = await supabase.from('tommy_chat_messages').insert({ session_id: sessionId, user_id: userId, role, text, status }).select('id').single();
  if (error) throw error;
  await supabase.from('tommy_chat_sessions').update({ updated_at: new Date().toISOString() }).eq('id', sessionId).eq('user_id', userId);
  return data;
}

async function updateChatMessage(id, userId, text, status = 'done') {
  const { error } = await supabase.from('tommy_chat_messages').update({ text, status }).eq('id', id).eq('user_id', userId);
  if (error) throw error;
}

async function listChatSessions(userId) {
  const { data, error } = await supabase.from('tommy_chat_sessions').select('id,title,updated_at,created_at').eq('user_id', userId).order('updated_at', { ascending: false }).limit(20);
  if (error) throw error;
  return data || [];
}

async function deleteChatSession(userId, sessionId) {
  const { error } = await supabase.from('tommy_chat_sessions').delete().eq('id', sessionId).eq('user_id', userId);
  if (error) throw error;
}

function AuthScreen() {
  const [mode, setMode] = useState('login'); const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [name, setName] = useState(''); const [busy, setBusy] = useState(false); const [message, setMessage] = useState(''); const [error, setError] = useState('');
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setMessage(''); setError('');
    try {
      if (mode === 'signup') {
        if (password.length < 6) throw new Error('Password must be at least 6 characters.');
        const { data, error: signUpError } = await supabase.auth.signUp({ email: email.trim(), password, options: { data: { full_name: name.trim() }, emailRedirectTo: window.location.origin } });
        if (signUpError) throw signUpError;
        if (data.session) setMessage('Account created. Welcome to Tommy.'); else setMessage('Account created. Check your email to confirm your account, then log in.');
      } else {
        const { error: signInError } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
        if (signInError) throw signInError;
      }
    } catch (e) { setError(e?.message || 'Authentication failed.'); } finally { setBusy(false); }
  };
  return (
    <div className="authPage"><div className="authCard"><div className="authLogo">T</div><div className="authEyebrow">TOMMY AI ASSISTANT</div><h1>{mode === 'login' ? 'Welcome back' : 'Create your Tommy account'}</h1><p>{mode === 'login' ? 'Sign in to continue to your personal AI assistant.' : 'Create your account and keep your Tommy session ready on your devices.'}</p><div className="authTabs"><button className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setError(''); setMessage(''); }}>Log in</button><button className={mode === 'signup' ? 'active' : ''} onClick={() => { setMode('signup'); setError(''); setMessage(''); }}>Create account</button></div><form onSubmit={submit} className="authForm">{mode === 'signup' && <label><span>Name</span><div className="authInput"><UserPlus size={17}/><input value={name} onChange={e => setName(e.target.value)} placeholder="Your name" autoComplete="name" /></div></label>}<label><span>Email</span><div className="authInput"><Mail size={17}/><input type="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" /></div></label><label><span>Password</span><div className="authInput"><LockKeyhole size={17}/><input type="password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 6 characters" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></div></label><button className="authSubmit" disabled={busy}>{mode === 'login' ? <LogIn size={18}/> : <UserPlus size={18}/>} {busy ? 'Please wait…' : mode === 'login' ? 'Log in to Tommy' : 'Create account'}</button></form>{message && <div className="authMessage">{message}</div>}{error && <div className="authError">{error}</div>}<div className="authSecure">Secure authentication powered by Supabase</div></div></div>
  );
}

function TommyMenu({ open, close, recentChats, onOpenChat, onNewChat, onDeleteChat }) {
  const [deleteChatId, setDeleteChatId] = useState(null); const longPressTimer = useRef(null);
  const clearLongPress = () => { if (longPressTimer.current) { clearTimeout(longPressTimer.current); longPressTimer.current = null; } };
  const startLongPress = (id) => { clearLongPress(); longPressTimer.current = setTimeout(() => { setDeleteChatId(id); longPressTimer.current = null; }, 550); };
  const handleOpen = (id) => { clearLongPress(); if (deleteChatId === id) return; onOpenChat(id); };
  const handleDelete = async (id) => { clearLongPress(); setDeleteChatId(null); await onDeleteChat(id); };
  useEffect(() => () => clearLongPress(), []);
  return (
    <><div className={open ? 'menuOverlay open' : 'menuOverlay'} onClick={() => { setDeleteChatId(null); close(); }} /><aside className={open ? 'sideMenu open' : 'sideMenu'} aria-hidden={!open}><div className="sideTop"><div className="sideTitle"><div className="sideLogo">T</div><b>Tommy</b></div><button className="sideIcon" onClick={close} aria-label="Close menu"><X size={21}/></button></div><div className="sideSearch"><Search size={18}/><span>Search Tommy</span></div><div className="sideFeatures"><button><Sparkles size={22}/><span>Skills</span></button><button><Library size={22}/><span>Memory</span></button><button><Folder size={22}/><span>Projects</span></button><button><Clock3 size={22}/><span>Scheduled</span></button><button><Puzzle size={22}/><span>Plugins</span></button></div><div className="sideDivider" /><div className="sideRecentHeader"><span>Recent chats</span><button onClick={onNewChat} title="New chat"><Pencil size={18}/></button></div><div className="sideRecent">{recentChats.length ? recentChats.map((chat, i) => <div className={i === 0 ? 'recentChatRow active' : 'recentChatRow'} key={chat.id} onPointerDown={() => startLongPress(chat.id)} onPointerUp={clearLongPress} onPointerLeave={clearLongPress} onPointerCancel={clearLongPress} onContextMenu={(e) => e.preventDefault()} role="button" tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleOpen(chat.id); } }}><span className="recentItem" onClick={() => handleOpen(chat.id)}>{chat.title}</span>{deleteChatId === chat.id && <button className="chatDeleteButton" onPointerDown={(e) => { e.stopPropagation(); clearLongPress(); }} onPointerUp={(e) => e.stopPropagation()} onClick={(e) => { e.preventDefault(); e.stopPropagation(); handleDelete(chat.id); }} title="Delete chat" aria-label={`Delete ${chat.title}`}>×</button>}</div>) : <div className="recentEmpty">No chats yet</div>}{recentChats.length > 0 && <button className="seeAll" onClick={close}>See all…</button>}</div><div className="sideBottom"><button className="newChat" onClick={onNewChat}><Plus size={21}/> <span>Chat</span></button><button className="tommyGo" onClick={close}>GO</button></div></aside></>
  );
}

export default function HomePage() {
  const [session, setSession] = useState(undefined); const [on, setOn] = useState(true); const [menuOpen, setMenuOpen] = useState(false); const [listening, setListening] = useState(false); const [processing, setProcessing] = useState(false); const [text, setText] = useState(''); const [messages, setMessages] = useState([{ from: 'tommy', text: 'Hi. Tommy is on. How can I help?', status: 'done' }]); const [chatSessionId, setChatSessionId] = useState(null); const [recentChats, setRecentChats] = useState([]); const rec = useRef(null);
  useEffect(() => { let mounted = true; supabase.auth.getSession().then(({ data }) => { if (mounted) setSession(data.session); }); const { data: listener } = supabase.auth.onAuthStateChange((_event, nextSession) => setSession(nextSession)); return () => { mounted = false; listener.subscription.unsubscribe(); rec.current?.stop(); }; }, []);
  useEffect(() => { if (!session?.user?.id) return; let cancelled = false; const restoreChat = async () => { try { const chats = await listChatSessions(session.user.id); if (cancelled) return; setRecentChats(chats); let active = chats[0]; if (!active) active = await createChatSession(session.user.id); if (cancelled) return; setChatSessionId(active.id); const loaded = await loadChatMessages(active.id); if (cancelled) return; setMessages(loaded.length ? loaded : [{ from: 'tommy', text: 'Hi. Tommy is on. How can I help?', status: 'done' }]); if (!loaded.length) await saveChatMessage(active.id, session.user.id, 'tommy', 'Hi. Tommy is on. How can I help?', 'done'); const refreshed = await listChatSessions(session.user.id); if (!cancelled) setRecentChats(refreshed); } catch (e) { if (!cancelled) setMessages([{ from: 'tommy', text: `Chat history error: ${e.message}`, status: 'done' }]); } }; restoreChat(); return () => { cancelled = true; }; }, [session?.user?.id]);
  const add = (from, message, status) => setMessages((m) => [...m, { from, text: message, status }]);
  const openChat = async (id) => { if (!session?.user?.id || id === chatSessionId) { setMenuOpen(false); return; } try { setProcessing(false); const loaded = await loadChatMessages(id); setChatSessionId(id); setMessages(loaded.length ? loaded : [{ from: 'tommy', text: 'This chat is empty. How can I help?', status: 'done' }]); setText(''); setMenuOpen(false); } catch (e) { add('tommy', `Could not open chat history: ${e.message}`, 'done'); } };
  const deleteChat = async (id) => { if (!session?.user?.id) return; try { const deletingCurrent = id === chatSessionId; await deleteChatSession(session.user.id, id); const remaining = await listChatSessions(session.user.id); setRecentChats(remaining); if (deletingCurrent) { if (remaining[0]) { const loaded = await loadChatMessages(remaining[0].id); setChatSessionId(remaining[0].id); setMessages(loaded.length ? loaded : [{ from: 'tommy', text: 'This chat is empty. How can I help?', status: 'done' }]); } else { const chat = await createChatSession(session.user.id); await saveChatMessage(chat.id, session.user.id, 'tommy', 'New Tommy chat started. How can I help?', 'done'); setChatSessionId(chat.id); setMessages([{ from: 'tommy', text: 'New Tommy chat started. How can I help?', status: 'done' }]); setRecentChats(await listChatSessions(session.user.id)); } } } catch (e) { add('tommy', `Could not delete chat: ${e.message}`, 'done'); } };
  const startVoice = () => { if (!on || processing) return; const SR = window.SpeechRecognition || window.webkitSpeechRecognition; if (!SR) { add('tommy', 'Voice recognition is not supported in this browser.', 'done'); return; } if (listening) { rec.current?.stop(); return; } const r = new SR(); rec.current = r; r.lang = 'en-IN'; r.interimResults = true; r.continuous = false; r.onstart = () => setListening(true); r.onend = () => setListening(false); r.onresult = (e) => { let value = ''; for (let i = e.resultIndex; i < e.results.length; i += 1) value += e.results[i][0].transcript; if (value) setText(value); }; r.onerror = () => setListening(false); r.start(); };
  const execute = async (q) => { if (!session?.user?.id || !chatSessionId) return; setProcessing(true); add('user', q, 'done'); let tommyRow = null; try { await saveChatMessage(chatSessionId, session.user.id, 'user', q, 'done'); add('tommy', 'Thinking with Gemini…', 'processing'); tommyRow = await saveChatMessage(chatSessionId, session.user.id, 'tommy', 'Thinking with Gemini…', 'processing'); const ai = await askGemini(q); const row = await createCommand(ai, q); const thinkingText = ai.reply || 'Command sent to Android Tommy.'; setMessages((m) => m.map((x, i) => i === m.length - 1 ? { ...x, text: thinkingText, status: 'processing' } : x)); await updateChatMessage(tommyRow.id, session.user.id, thinkingText, 'processing'); let latest = row; for (let i = 0; i < 30; i += 1) { await new Promise((resolve) => setTimeout(resolve, 1000)); latest = await getCommand(row.id); if (latest.status === 'done' || latest.status === 'failed') break; } const finalText = latest.response || ai.reply || 'Tommy is waiting for Android.'; setMessages((m) => m.map((x, i) => i === m.length - 1 ? { ...x, text: finalText, status: 'done' } : x)); await updateChatMessage(tommyRow.id, session.user.id, finalText, 'done'); if (messages.length <= 1) await supabase.from('tommy_chat_sessions').update({ title: q.slice(0, 60), updated_at: new Date().toISOString() }).eq('id', chatSessionId).eq('user_id', session.user.id); } catch (e) { const errorText = `Tommy connection error: ${e.message}`; setMessages((m) => m.map((x, i) => i === m.length - 1 ? { ...x, text: errorText, status: 'done' } : x)); if (tommyRow?.id) await updateChatMessage(tommyRow.id, session.user.id, errorText, 'done').catch(() => {}); } finally { setProcessing(false); listChatSessions(session.user.id).then(setRecentChats).catch(() => {}); } };
  const send = () => { const q = text.trim(); if (!q || !on) return; setText(''); execute(q); }; const quick = (q) => setText(q);
  const newChat = async () => { if (!session?.user?.id) return; try { const chat = await createChatSession(session.user.id); await saveChatMessage(chat.id, session.user.id, 'tommy', 'New Tommy chat started. How can I help?', 'done'); setChatSessionId(chat.id); setMessages([{ from: 'tommy', text: 'New Tommy chat started. How can I help?', status: 'done' }]); setText(''); setMenuOpen(false); setRecentChats(await listChatSessions(session.user.id)); setOn(true); } catch (e) { add('tommy', `Could not create chat: ${e.message}`, 'done'); } };
  const logout = async () => { await supabase.auth.signOut(); setOn(false); setChatSessionId(null); setRecentChats([]); };
  if (session === undefined) return <div className="authLoading"><div className="authLogo">T</div><Loader2 className="spin" size={20}/></div>; if (!session) return <AuthScreen />;
  return <div className="app"><TommyMenu open={menuOpen} close={() => setMenuOpen(false)} recentChats={recentChats} onOpenChat={openChat} onNewChat={newChat} onDeleteChat={deleteChat}/><header><div className="headerLeft"><button className="menuButton" onClick={() => setMenuOpen(true)} aria-label="Open Tommy menu"><Menu size={22}/></button><div className="brand"><div className="logo">T</div><div><b>Tommy</b><span>AI Assistant</span></div></div></div><div className="headerRight"><div className="connection"><Wifi size={14}/> Gemini + Supabase + Android</div><span className="accountEmail">{session.user.email}</span><button className="logout" onClick={logout} title="Log out"><LogOut size={16}/></button><button className={on ? 'power on' : 'power'} onClick={() => { setOn(!on); setListening(false); }}><Power size={17}/> {on ? 'ON' : 'OFF'}</button></div></header><main><div className="hero"><div className={listening ? 'orb listening' : 'orb'}><div className="orb-core">T</div></div><div className="eyebrow">TOMMY CONTROL</div><h1>{listening ? 'I’m listening…' : processing ? 'Tommy is working…' : 'What can I do for you?'}</h1><p>{on ? (listening ? 'Speak your command now' : processing ? 'Gemini → Supabase → Android…' : 'Type a command or tap the microphone') : 'Tommy is off'}</p></div><section className="chat">{messages.map((m, i) => <div key={m.id || i} className={`msg ${m.from}`}><div>{m.text}{m.status === 'processing' && <Loader2 className="spin" size={14}/>}</div>{m.status === 'done' && m.from === 'tommy' && <CheckCircle2 size={14} className="doneIcon"/>}</div>)}</section><div className="composer"><textarea value={text} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }} placeholder={on ? 'Ask Tommy anything…' : 'Turn Tommy on to start'}/><button className={listening ? 'mic active' : 'mic'} onClick={startVoice} disabled={!on || processing}>{listening ? <MicOff/> : <Mic/>}</button><button className="send" onClick={send} disabled={!on || processing || !text.trim()}><Send/></button></div><div className="status"><span className={on ? 'dot live' : 'dot'}/>{on ? (listening ? 'Listening' : processing ? 'Executing on Android' : 'Tommy is on') : 'Tommy is off'}</div><div className="quick"><button onClick={() => quick('Open Instagram Reels')}>Instagram Reels</button><button onClick={() => quick('Open Instagram and open comments')}>Instagram Comments</button><button onClick={() => quick('Open Google and search Tamil latest movie')}><Search size={15}/> Google search</button><button onClick={() => quick('Open Spotify and search Arabic Kuthu')}><Music2 size={15}/> Spotify</button></div></main><footer><ChevronUp size={15}/> Tommy web control • Gemini + Supabase • Android bridge</footer></div>;
}
