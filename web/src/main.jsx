import React,{useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {Mic,MicOff,Send,Power,ChevronUp} from 'lucide-react';
import './style.css';

function App(){
 const [on,setOn]=useState(true),[listening,setListening]=useState(false),[text,setText]=useState(''),[messages,setMessages]=useState([{from:'tommy',text:'Hi Gokul. Tommy is on. How can I help?'}]);
 const rec=useRef(null);
 useEffect(()=>()=>rec.current?.stop(),[]);
 const startVoice=()=>{
  if(!on)return;
  const SR=window.SpeechRecognition||window.webkitSpeechRecognition;
  if(!SR){setMessages(m=>[...m,{from:'tommy',text:'Voice recognition is not supported in this browser.'}]);return}
  if(listening){rec.current?.stop();return}
  const r=new SR();rec.current=r;r.lang='en-IN';r.interimResults=true;r.continuous=false;
  r.onstart=()=>setListening(true);r.onend=()=>setListening(false);
  r.onresult=e=>{let final='';for(let i=e.resultIndex;i<e.results.length;i++)final+=e.results[i][0].transcript;if(final){setText(final)}};
  r.onerror=()=>setListening(false);r.start();
 };
 const send=()=>{const q=text.trim();if(!q)return;setMessages(m=>[...m,{from:'user',text:q},{from:'tommy',text:`OK — I heard: “${q}”. Ready to execute.`}]);setText('')};
 return <div className="app"><header><div className="brand"><div className="logo">T</div><div><b>Tommy</b><span>AI Assistant</span></div></div><button className={on?'power on':'power'} onClick={()=>{setOn(!on);setListening(false)}}><Power size={17}/>{on?'ON':'OFF'}</button></header>
 <main><div className="hero"><div className={listening?'orb listening':'orb'}><div className="orb-core">T</div></div><h1>{listening?'I’m listening…':'What can I do for you?'}</h1><p>{on?(listening?'Speak your command now':'Type a command or tap the microphone'):'Tommy is off'}</p></div>
 <section className="chat">{messages.map((m,i)=><div key={i} className={'msg '+m.from}><div>{m.text}</div></div>)}</section>
 <div className="composer"><textarea value={text} onChange={e=>setText(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();send()}}} placeholder={on?'Ask Tommy anything…':'Turn Tommy on to start'}/><button className={listening?'mic active':'mic'} onClick={startVoice} disabled={!on}>{listening?<MicOff/>:<Mic/>}</button><button className="send" onClick={send} disabled={!on||!text.trim()}><Send/></button></div>
 <div className="status"><span className={on?'dot live':'dot'}></span>{on?(listening?'Listening':'Tommy is on'):'Tommy is off'}</div>
 <div className="quick"><button onClick={()=>setText('Open Instagram')}>Open Instagram</button><button onClick={()=>setText('Open Google and search Tamil latest movie')}>Google search</button><button onClick={()=>setText('Open Spotify and search Arabic Kuthu')}>Spotify</button></div>
 </main><footer><ChevronUp size={15}/> Tommy web control • Gemini-ready command pipeline</footer></div>
}
createRoot(document.getElementById('root')).render(<App/>);
