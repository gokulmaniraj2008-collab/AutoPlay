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

    const sendDirectReelsTest = async (button) => {
      button.disabled = true;
      const original = button.textContent;
      button.textContent = 'Sending…';
      try {
        const { data: auth } = await supabase.auth.getSession();
        if (!auth?.session?.user?.id) {
          window.alert('Please log in to Tommy first.');
          return;
        }

        const command = {
          action: 'open_instagram_reels',
          original: 'Test Instagram Reels',
          reply: 'Opening Instagram Reels.',
        };

        const { data: inserted, error } = await supabase.from('tommy_commands').insert({
          channel: 'tommy-main',
          source: 'web-test',
          command: JSON.stringify(command),
          status: 'pending',
        }).select('id').single();

        if (error) throw error;
        if (!inserted?.id) throw new Error('Tommy command was created without an id.');

        button.textContent = 'Opening…';

        let latest = null;
        for (let i = 0; i < 20; i += 1) {
          await new Promise((resolve) => setTimeout(resolve, 500));
          const { data, error: readError } = await supabase
            .from('tommy_commands')
            .select('status,response')
            .eq('id', inserted.id)
            .single();
          if (readError) throw readError;
          latest = data;
          if (data.status === 'done' || data.status === 'failed') break;
        }

        if (latest?.status === 'done') {
          button.textContent = '✓ Reels command done';
        } else if (latest?.status === 'failed') {
          button.textContent = '✕ Android failed';
          window.alert(latest.response || 'Android Tommy failed to execute the Reels command.');
        } else {
          button.textContent = '⌛ Android not responding';
          window.alert('Command reached Supabase but Android Tommy did not acknowledge it within 10 seconds. Keep the Tommy Android app running and check Accessibility Service.');
        }

        setTimeout(() => { if (!disposed) button.textContent = original; }, 2500);
      } catch (error) {
        button.textContent = original;
        window.alert(`Could not send Reels test: ${error.message}`);
      } finally {
        button.disabled = false;
      }
    };

    const addReelsTestButton = () => {
      const reelsButton = Array.from(document.querySelectorAll('button')).find(
        (button) => (button.textContent || '').trim().toLowerCase() === 'instagram reels'
      );
      if (!reelsButton || reelsButton.dataset.testButtonAdded === 'true') return;

      const testButton = document.createElement('button');
      testButton.type = 'button';
      testButton.className = reelsButton.className;
      testButton.textContent = '🧪 Test Reels';
      testButton.title = 'Direct Android Reels test — bypasses Gemini';
      testButton.setAttribute('aria-label', 'Test Instagram Reels directly');
      testButton.style.marginLeft = '8px';
      testButton.addEventListener('click', () => sendDirectReelsTest(testButton));

      reelsButton.dataset.testButtonAdded = 'true';
      reelsButton.parentElement?.appendChild(testButton);
    };

    const removeStaleFakeYouTubeCards = () => {
      const messages = Array.from(document.querySelectorAll('.chat .msg.tommy'));
      messages.forEach((message) => {
        const text = (message.textContent || '').toLowerCase();
        const isFakeYouTubeCard = text.includes('youtube is ready') || text.includes('[ open youtube ]');
        if (isFakeYouTubeCard) message.remove();
      });
    };

    const decorate = () => {
      if (disposed) return;
      removeStaleFakeYouTubeCards();
      addReelsTestButton();

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