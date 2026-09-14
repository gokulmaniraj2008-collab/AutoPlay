import React,{useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {Mic,MicOff,Send,Power,ChevronUp,Instagram,Search,Music2,CheckCircle2,Loader2,Wifi} from 'lucide-react';
import './style.css';

const SUPABASE_URL='https://bqrpgtxtmatxwtdpuoyh.supabase.co';
const SUPABASE_ANON_KEY='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJxcnBndHh0bWF0eHd0ZHB1b3loIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgyMTA2NDIsImV4cCI6MjA5Mzc4NjY0Mn0.FQ6RBDVt81H6XbSZRSMnup9jJ_UgtsozzLJJAOF6B4k';

async function askGemini(text){
 const r=await fetch(`${SUPABASE_URL}/functions/v1/tommy-gemini`,{method:'POST',headers:{'Content-Type':'application/json','apikey':SUPABASE_ANON_KEY,'Authorization':`Bearer ${SUPABASE_ANON_KEY}`},body:JSON.stringify({text})});
 const data=await r.json();
 if(!r.ok) throw new Error(data?.error||'Gemini request failed');
 return data;
}

function App(){
 const [on,setOn]=useState(true),[listening,setListening]=useState(false),[processing,setProcessing]=useState(false),[text,setText]=useState(''),[messages,setMessages]=useState([{from:'tommy',text:'Hi Gokul. Tommy is on. How can I help?',status:'done'}]);
 const rec=useRef(null); useEffect(()=>()=>rec.current?.stop(),[]);
 const add=(from,text,status)=>setMessages(m=>[...m,{from,text,status}]);
 const startVoice=()=>{if(!on||processing)return;const SR=window.SpeechRecognition||window.webkitSpeechRecognition;if(!SR){add('tommy','Voice recognition is not supported in this browser.');return}if(listening){rec.current?.stop();return}const r=new SR();rec.current=r;r.lang='en-IN';r.interimResults=true;r.continuous=false;r.onstart=()=>setListening(true);r.onend=()=>setListening(false);r.onresult=e=>{let value='';for(let i=e.resultIndex;i<e.results.length;i++)value+=e.results[i][0].transcript;if(value)setText(value)};r.onerror=()=>setListening(false);r.start()};
 const execute=async q=>{setProcessing(true);add('user',q,'done');add('tommy','Thinking with Gemini…','processing');try{const result=await askGemini(q);setMessages(m=>m.map((x,i)=>i===m.length-1?{...x,text:result.reply||`Gemini selected: ${result.action||'none'}`,status:'done'}:x));}catch(e){setMessages(m=>m.map((x,i)=>i===m.length-1?{...x,text:`Gemini connection error: ${e.message}`,status:'done'}:x));}finally{setProcessing(false)}};
 const send=()=>{const q=text.trim();if(!q||!on)return;setText('');execute(q)}; const quick=q=>setText(q);
 return <div className="app"><header><div className="brand"><div className="logo">T</div><div><b>Tommy</b><span>AI Assistant</span></div></div><div className="headerRight"><div className="connection"><Wifi size={14}/> Gemini + Supabase</div><button className={on?'power on':'power'} onClick={()=>{setOn(!on);setListening(false)}}><Power size={17}/>{on?'ON':'OFF'}</button></div></header>
 <main><div className="hero"><div className={listening?'orb listening':'orb'}><div className="orb-core">T</div></div><div className="eyebrow">TOMMY CONTROL</div><h1>{listening?'I’m listening…':processing?'Tommy is thinking…':'What can I do for you?'}</h1><p>{on?(listening?'Speak your command now':processing?'Gemini is interpreting your command…':'Type a command or tap the microphone'):'Tommy is off'}</p></div>
 <section className="chat">{messages.map((m,i)=><div key={i} className={'msg '+m.from}><div>{m.text}{m.status==='processing'&&<Loader2 className="spin" size={14}/>}</div>{m.status==='done'&&m.from==='tommy'&&<CheckCircle2 size={14} className="doneIcon"/>}</div>)}</section>
 <div className="composer"><textarea value={text} onChange={e=>setText(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send()}}} placeholder={on?'Ask Tommy anything…':'Turn Tommy on to start'}/><button className={listening?'mic active':'mic'} onClick={startVoice} disabled={!on||processing}>{listening?<MicOff/>:<Mic/>}</button><button className="send" onClick={send} disabled={!on||processing||!text.trim()}><Send/></button></div>
 <div className="status"><span className={on?'dot live':'dot'}></span>{on?(listening?'Listening':processing?'Gemini processing':'Tommy is on'):'Tommy is off'}</div>
 <div className="quick"><button onClick={()=>quick('Open Instagram Reels')}><Instagram size={15}/> Reels</button><button onClick={()=>quick('Open Instagram and open comments')}><Instagram size={15}/> Comments</button><button onClick={()=>quick('Open Google and search Tamil latest movie')}><Search size={15}/> Google search</button><button onClick={()=>quick('Open Spotify and search Arabic Kuthu')}><Music2 size={15}/> Spotify</button></div></main>
 <footer><ChevronUp size={15}/> Tommy web control • Gemini + Supabase • Android-ready</footer></div>}
createRoot(document.getElementById('root')).render(<App/>);
