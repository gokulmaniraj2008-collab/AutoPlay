import { NextResponse } from 'next/server';

// ₹0 Tommy brain: deterministic command understanding with no paid AI API.
// Keep this endpoint compatible with the existing Tommy bridge contract.
const APP_ALIASES = {
  instagram: 'Instagram',
  youtube: 'YouTube',
  spotify: 'Spotify',
  whatsapp: 'WhatsApp',
  google: 'Google',
  chrome: 'Chrome',
};

function result(reply, action = 'none', target = '', query = '', executionCommand = '') {
  return { reply, action, target, query, executionCommand, source: 'local-free' };
}

function parseCommand(text) {
  const original = text.trim();
  const lower = original.toLowerCase();

  for (const [alias, target] of Object.entries(APP_ALIASES)) {
    if (/^(open|launch|start|போ|திற)/i.test(lower) && lower.includes(alias)) {
      return result(`Okay, opening ${target}.`, 'open_app', target, '', `open ${target}`);
    }
  }

  if (/(open|search|google).*(google search|search)/i.test(lower) || /^(search|google)\s+/i.test(lower)) {
    const query = original
      .replace(/^(open\s+)?(google\s+)?(search\s+)?/i, '')
      .trim();
    if (query) return result(`Okay, searching for ${query}.`, 'search_web', 'Google', query, `search Google ${query}`);
  }

  if (/(youtube|yt)/i.test(lower) && /(search|find|play)/i.test(lower)) {
    const query = original.replace(/.*?(?:youtube|yt)\s*(?:search|find|play)?\s*/i, '').trim();
    if (query) return result(`Okay, searching YouTube for ${query}.`, 'youtube_search', 'YouTube', query, `search YouTube ${query}`);
  }

  if (/instagram/i.test(lower)) {
    if (/(like|heart)/i.test(lower) && /(reel|this)/i.test(lower)) {
      return result('Okay, liking this Instagram reel.', 'instagram_like_reel', 'Instagram', '', 'like this reel');
    }
    if (/(follow)/i.test(lower)) {
      const account = original.match(/follow\s+(.+?)(?:\s+account)?$/i)?.[1]?.trim() || '';
      return result(account ? `Okay, following ${account}.` : 'Okay, following this Instagram account.', 'instagram_follow_account', account, '', account ? `follow ${account}` : 'follow this account');
    }
    if (/(comment|comments)/i.test(lower)) {
      return result('Okay, opening Instagram comments.', 'open_instagram_comments', 'Instagram', '', 'open comments');
    }
    if (/(reel|scroll)/i.test(lower)) {
      return result('Okay, opening Instagram reels.', 'open_instagram_reels', 'Instagram', '', 'open Instagram reels');
    }
  }

  if (/spotify/i.test(lower) && /(search|find|play)/i.test(lower)) {
    const query = original.replace(/.*?spotify\s*(?:search|find|play)?\s*/i, '').trim();
    if (query) return result(`Okay, searching Spotify for ${query}.`, 'spotify_search', 'Spotify', query, `search Spotify ${query}`);
  }

  if (/(open|show)\s+(comments?)/i.test(lower)) {
    return result('Okay, opening comments.', 'open_comments', '', '', 'open comments');
  }

  return result(`I can handle that locally when Tommy has a registered skill.`, 'none', '', '', '');
}

export async function POST(request) {
  try {
    const body = await request.json();
    const text = typeof body?.text === 'string' ? body.text.trim() : '';
    if (!text) return NextResponse.json({ error: 'text is required' }, { status: 400 });
    return NextResponse.json(parseCommand(text));
  } catch {
    return NextResponse.json({ error: 'Invalid request body' }, { status: 400 });
  }
}

export async function GET() {
  return NextResponse.json({
    ok: true,
    mode: 'local-free',
    provider: 'none',
    paid_ai_dependency: false,
    message: 'Tommy command bridge is running without a paid AI API.',
  });
}
