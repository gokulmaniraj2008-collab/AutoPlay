'use client';

import { useEffect, useRef, useState } from 'react';
import { Mic, MicOff, Send, Power, ChevronUp, Search, Music2, CheckCircle2, Loader2, Wifi } from 'lucide-react';

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://bqrpgtxtmatxwtdpuoyh.supabase.co';
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';
const headers = {
  'Content-Type': 'application/json',
  apikey: SUPABASE_ANON_KEY,
  Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
};

async function askGemini(text) {
  const r = await fetch('/api/tommy-gemini', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  });
  const data = await r.json();
  if (!r.ok) {
    const details = data?.details;
    throw new Error(data?.error ? `${data.error}${details ? `: ${details}` : ''}` : `Gemini request failed (${r.status})`);
  }
  return data;
}

async function createCommand(ai, original) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/tommy_commands`, {
    method: 'POST',
    headers: { ...headers, Prefer: 'return=representation' },
    body: JSON.stringify({
      channel: 'tommy-main',
      source: 'web',
      command: JSON.stringify({ ...ai, original }),
      status: 'pending',
    }),
  });
  const data = await r.json();
  if (!r.ok) throw new Error(data?.message || 'Could not send command to Android');
  return data[0];
}

async function getCommand(id) {
  const r = await fetch(
    `${SUPABASE_URL}/rest/v1/tommy_commands?select=*&id=eq.${encodeURIComponent(id)}&limit=1`,
    { headers },
  );
  const data = await r.json();
  if (!r.ok) throw new Error('Could not read Tommy status');
  return data[0];
}

export default function HomePage() {
  const [on, setOn] = useState(true);
  const [listening, setListening] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [text, setText] = useState('');
  const [messages, setMessages] = useState([
    { from: 'tommy', text: 'Hi Gokul. Tommy is on. How can I help?', status: 'done' },
  ]);
  const rec = useRef(null);

  useEffect(() => () => rec.current?.stop(), []);

  const add = (from, message, status) =>
    setMessages((m) => [...m, { from, text: message, status }]);

  const startVoice = () => {
    if (!on || processing) return;
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      add('tommy', 'Voice recognition is not supported in this browser.', 'done');
      return;
    }
    if (listening) {
      rec.current?.stop();
      return;
    }
    const r = new SR();
    rec.current = r;
    r.lang = 'en-IN';
    r.interimResults = true;
    r.continuous = false;
    r.onstart = () => setListening(true);
    r.onend = () => setListening(false);
    r.onresult = (e) => {
      let value = '';
      for (let i = e.resultIndex; i < e.results.length; i += 1) {
        value += e.results[i][0].transcript;
      }
      if (value) setText(value);
    };
    r.onerror = () => setListening(false);
    r.start();
  };

  const execute = async (q) => {
    setProcessing(true);
    add('user', q, 'done');
    add('tommy', 'Thinking with Gemini…', 'processing');
    try {
      const ai = await askGemini(q);
      const row = await createCommand(ai, q);
      setMessages((m) =>
        m.map((x, i) =>
          i === m.length - 1
            ? { ...x, text: ai.reply || 'Command sent to Android Tommy.', status: 'processing' }
            : x,
        ),
      );
      let latest = row;
      for (let i = 0; i < 30; i += 1) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        latest = await getCommand(row.id);
        if (latest.status === 'done' || latest.status === 'failed') break;
      }
      setMessages((m) =>
        m.map((x, i) =>
          i === m.length - 1
            ? { ...x, text: latest.response || ai.reply || 'Tommy is waiting for Android.', status: 'done' }
            : x,
        ),
      );
    } catch (e) {
      setMessages((m) =>
        m.map((x, i) =>
          i === m.length - 1
            ? { ...x, text: `Tommy connection error: ${e.message}`, status: 'done' }
            : x,
        ),
      );
    } finally {
      setProcessing(false);
    }
  };

  const send = () => {
    const q = text.trim();
    if (!q || !on) return;
    setText('');
    execute(q);
  };

  const quick = (q) => setText(q);

  return (
    <div className="app">
      <header>
        <div className="brand">
          <div className="logo">T</div>
          <div>
            <b>Tommy</b>
            <span>AI Assistant</span>
          </div>
        </div>
        <div className="headerRight">
          <div className="connection"><Wifi size={14} /> Gemini + Supabase + Android</div>
          <button className={on ? 'power on' : 'power'} onClick={() => { setOn(!on); setListening(false); }}>
            <Power size={17} />{on ? 'ON' : 'OFF'}
          </button>
        </div>
      </header>

      <main>
        <div className="hero">
          <div className={listening ? 'orb listening' : 'orb'}><div className="orb-core">T</div></div>
          <div className="eyebrow">TOMMY CONTROL</div>
          <h1>{listening ? 'I’m listening…' : processing ? 'Tommy is working…' : 'What can I do for you?'}</h1>
          <p>{on ? (listening ? 'Speak your command now' : processing ? 'Gemini → Supabase → Android…' : 'Type a command or tap the microphone') : 'Tommy is off'}</p>
        </div>

        <section className="chat">
          {messages.map((m, i) => (
            <div key={i} className={`msg ${m.from}`}>
              <div>{m.text}{m.status === 'processing' && <Loader2 className="spin" size={14} />}</div>
              {m.status === 'done' && m.from === 'tommy' && <CheckCircle2 size={14} className="doneIcon" />}
            </div>
          ))}
        </section>

        <div className="composer">
          <textarea value={text} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }} placeholder={on ? 'Ask Tommy anything…' : 'Turn Tommy on to start'} />
          <button className={listening ? 'mic active' : 'mic'} onClick={startVoice} disabled={!on || processing}>{listening ? <MicOff /> : <Mic />}</button>
          <button className="send" onClick={send} disabled={!on || processing || !text.trim()}><Send /></button>
        </div>

        <div className="status"><span className={on ? 'dot live' : 'dot'} />{on ? (listening ? 'Listening' : processing ? 'Executing on Android' : 'Tommy is on') : 'Tommy is off'}</div>
        <div className="quick">
          <button onClick={() => quick('Open Instagram Reels')}>Instagram Reels</button>
          <button onClick={() => quick('Open Instagram and open comments')}>Instagram Comments</button>
          <button onClick={() => quick('Open Google and search Tamil latest movie')}><Search size={15} /> Google search</button>
          <button onClick={() => quick('Open Spotify and search Arabic Kuthu')}><Music2 size={15} /> Spotify</button>
        </div>
      </main>

      <footer><ChevronUp size={15} /> Tommy web control • Gemini + Supabase • Android bridge</footer>
    </div>
  );
}
