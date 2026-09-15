import { NextResponse } from 'next/server';

const SYSTEM_PROMPT = `You are Tommy, an Android voice assistant. Convert the user's natural-language request into one safe, structured action. Return ONLY valid JSON with keys: reply, action, target, query. Allowed actions: open_app, search_web, youtube_search, open_instagram_reels, open_instagram_comments, spotify_search, none. target is an app/package-style target when useful. query contains the search text when applicable. For requests to search YouTube, use action youtube_search, target YouTube, and put ONLY the requested search terms in query. For requests only to open YouTube, use action open_app with target YouTube and an empty query. Never invent unsupported actions. If the request is unclear, use action none.`;

function youtubeCardReply(query, openOnly = false) {
  if (openOnly) {
    return '▶ YouTube\n\n┌────────────────────────────┐\n│ ▶  YouTube                  │\n│                            │\n│ ▶  YouTube is ready         │\n│                            │\n│ YouTube is open.            │\n│                            │\n│ [ Open YouTube ]            │\n└────────────────────────────┘';
  }

  return `▶ YouTube\n\n┌────────────────────────────┐\n│ ▶  YouTube                  │\n│                            │\n│ 🔍 ${query}                 │\n│                            │\n│ YouTube search results      │\n│ are open.                  │\n│                            │\n│ [ Open YouTube results ]    │\n└────────────────────────────┘`;
}

export async function POST(request) {
  try {
    const body = await request.json();
    const text = typeof body?.text === 'string' ? body.text.trim() : '';

    if (!text) {
      return NextResponse.json({ error: 'text is required' }, { status: 400 });
    }

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      return NextResponse.json(
        { error: 'GEMINI_API_KEY is not configured on the production server' },
        { status: 500 },
      );
    }

    const response = await fetch(
      'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': apiKey,
        },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
          contents: [{ role: 'user', parts: [{ text }] }],
          generationConfig: {
            responseMimeType: 'application/json',
            temperature: 0.1,
          },
        }),
      },
    );

    const data = await response.json();

    if (!response.ok) {
      return NextResponse.json(
        {
          error: 'Gemini request failed',
          details: data?.error?.message || `Gemini HTTP ${response.status}`,
        },
        { status: 502 },
      );
    }

    const raw = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!raw) {
      return NextResponse.json(
        { error: 'Gemini returned no content' },
        { status: 502 },
      );
    }

    const cleaned = raw.replace(/^```json\s*/i, '').replace(/\s*```$/i, '').trim();
    const result = JSON.parse(cleaned);
    const action = typeof result.action === 'string' ? result.action : 'none';
    const target = typeof result.target === 'string' ? result.target : '';
    const query = typeof result.query === 'string' ? result.query : '';

    let reply = typeof result.reply === 'string' ? result.reply : `I understood: ${text}`;
    if (action === 'youtube_search') {
      reply = youtubeCardReply(query);
    } else if (action === 'open_app' && target.toLowerCase() === 'youtube') {
      reply = youtubeCardReply('', true);
    }

    return NextResponse.json({ reply, action, target, query });
  } catch (error) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Unknown Gemini server error' },
      { status: 500 },
    );
  }
}
