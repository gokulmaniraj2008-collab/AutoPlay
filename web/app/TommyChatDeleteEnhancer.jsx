'use client';

import { useEffect } from 'react';
import { createClient } from '@supabase/supabase-js';

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://bqrpgtxtmatxwtdpuoyh.supabase.co';
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
});

function decorateYouTube() {
  const messages = Array.from(document.querySelectorAll('.chat .msg.tommy'));

  messages.forEach((message) => {
    if (message.dataset.youtubeEnhanced === 'true') return;

    const text = message.textContent || '';
    const match = text.match(/(?:🔍\s*)?([^\n]+?)\s*(?:YouTube search results|YouTube is open|YouTube is ready)/i);
    const isYouTube = text.includes('▶ YouTube') || text.includes('YouTube search results');
    if (!isYouTube) return;

    const queryMatch = text.match(/🔍\s*([^\n]+)/);
    const query = queryMatch?.[1]?.trim() || '';
    const youtubeUrl = query
      ? `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`
      : 'https://www.youtube.com/';

    const card = document.createElement('div');
    card.className = 'tommyRealYoutubeCard';
    card.innerHTML = `
      <div class="tommyRealYoutubeHeader">
        <span class="tommyRealYoutubeLogo">▶</span>
        <strong>YouTube</strong>
        <span class="tommyRealYoutubeLive">LIVE</span>
      </div>
      ${query ? `<div class="tommyRealYoutubeQuery">🔍 ${query.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</div>` : ''}
      <div class="tommyRealYoutubeFrameWrap">
        <iframe
          src="${youtubeUrl}"
          title="YouTube"
          loading="lazy"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          allowfullscreen
        ></iframe>
        <div class="tommyRealYoutubeFallback">
          <span>Open the real YouTube search</span>
          <button type="button">Open YouTube</button>
        </div>
      </div>
      <div class="tommyRealYoutubeFooter">This is the real YouTube page. If YouTube blocks embedding, use Open YouTube.</div>
    `;

    card.querySelector('button')?.addEventListener('click', () => {
      window.location.href = youtubeUrl;
    });

    message.replaceChildren(card);
    message.dataset.youtubeEnhanced = 'true';
  });
}

function injectYouTubeStyles() {
  if (document.getElementById('tommy-real-youtube-style')) return;
  const style = document.createElement('style');
  style.id = 'tommy-real-youtube-style';
  style.textContent = `
    .tommyRealYoutubeCard{width:min(100%,620px);overflow:hidden;border:1px solid #e5e7eb;border-radius:18px;background:#fff;box-shadow:0 8px 28px rgba(15,23,42,.10)}
    .tommyRealYoutubeHeader{display:flex;align-items:center;gap:10px;padding:13px 15px;color:#111827}
    .tommyRealYoutubeLogo{display:grid;place-items:center;width:28px;height:28px;border-radius:8px;background:#ff0000;color:#fff;font-size:12px}
    .tommyRealYoutubeLive{margin-left:auto;font-size:10px;font-weight:800;letter-spacing:.08em;color:#dc2626}
    .tommyRealYoutubeQuery{margin:0 14px 12px;padding:10px 12px;border-radius:10px;background:#f3f4f6;color:#374151;font-size:13px}
    .tommyRealYoutubeFrameWrap{position:relative;background:#000;aspect-ratio:16/9;min-height:220px}
    .tommyRealYoutubeFrameWrap iframe{display:block;width:100%;height:100%;border:0;background:#000}
    .tommyRealYoutubeFallback{position:absolute;inset:auto 12px 12px;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 10px 9px 12px;border-radius:11px;background:rgba(17,24,39,.90);color:#fff;font-size:12px}
    .tommyRealYoutubeFallback button{border:0;border-radius:8px;padding:8px 11px;background:#fff;color:#111827;font-weight:700}
    .tommyRealYoutubeFooter{padding:10px 14px 13px;color:#6b7280;font-size:11px}
  `;
  document.head.appendChild(style);
}

export default function TommyChatDeleteEnhancer() {
  useEffect(() => {
    let disposed = false;
    let observer;

    const deleteChat = async (index) => {
      const confirmed = window.confirm('Delete this Tommy chat? This will permanently delete its messages.');
      if (!confirmed) return;

      const { data: auth } = await supabase.auth.getSession();
      const userId = auth?.session?.user?.id;
      if (!userId) return;

      const { data: chats, error: listError } = await supabase
        .from('tommy_chat_sessions')
        .select('id,title,updated_at')
        .eq('user_id', userId)
        .order('updated_at', { ascending: false })
        .limit(20);
      if (listError) {
        window.alert(`Could not load chat history: ${listError.message}`);
        return;
      }

      const chat = chats?.[index];
      if (!chat) return;

      const { error } = await supabase
        .from('tommy_chat_sessions')
        .delete()
        .eq('id', chat.id)
        .eq('user_id', userId);
      if (error) {
        window.alert(`Could not delete chat: ${error.message}`);
        return;
      }

      window.location.reload();
    };

    const decorate = () => {
      if (disposed) return;
      injectYouTubeStyles();
      decorateYouTube();

      const items = Array.from(document.querySelectorAll('.sideRecent .recentItem'));
      items.forEach((item, index) => {
        item.dataset.chatIndex = String(index);
        if (item.querySelector('.chatDeleteButton')) return;

        const deleteButton = document.createElement('span');
        deleteButton.className = 'chatDeleteButton';
        deleteButton.textContent = '×';
        deleteButton.setAttribute('role', 'button');
        deleteButton.setAttribute('tabindex', '0');
        deleteButton.setAttribute('aria-label', 'Delete chat');
        deleteButton.title = 'Delete chat';

        const remove = (event) => {
          event.preventDefault();
          event.stopPropagation();
          const chatIndex = Number(item.dataset.chatIndex);
          if (Number.isInteger(chatIndex)) deleteChat(chatIndex);
        };

        deleteButton.addEventListener('click', remove);
        deleteButton.addEventListener('keydown', (event) => {
          if (event.key === 'Enter' || event.key === ' ') remove(event);
        });
        item.appendChild(deleteButton);
      });
    };

    decorate();
    observer = new MutationObserver(decorate);
    observer.observe(document.body, { childList: true, subtree: true });

    return () => {
      disposed = true;
      observer?.disconnect();
    };
  }, []);

  return null;
}
