'use client';

import { useEffect } from 'react';
import { createClient } from '@supabase/supabase-js';

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://bqrpgtxtmatxwtdpuoyh.supabase.co';
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
});

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
