import { NextResponse } from 'next/server';

const SYSTEM_PROMPT = `You are Tommy, an Android voice assistant. Convert the user's natural-language request into one safe, structured action. Return ONLY valid JSON with keys: reply, action, target, query. Allowed actions: open_app, search_web, open_instagram_reels, open_instagram_comments, spotify_search, none. target is an app/package-style target when useful. query contains the search text when applicable. Never invent unsupported actions. If the request is unclear, use action none.`;

export async function POST(request) {
  try {
    const { text } = await request.json();
    if (typeof text !== 'string' || !text.trim()) {
      return NextResponse.json({ error: 'text is required' }, { status: 400 });
    }

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      return NextResponse.json({ error: 'GEMINI_API_KEY is not configured on Vercel' }, { status: 500 });
    }

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${encodeURIComponent(apiKey)}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
          contents: [{ role: 'user', parts: [{ text: text.trim() }] }],
          generationConfig: { responseMimeType: 'application/json', temperature: 0.1 },
        }),
      },
    );

    const data = await response.json();
    if (!response.ok) {
      return NextResponse.json(
        { error: 'Gemini request failed', details: data?.error?.message || data },
        { status: 502 },
      );
    }

    const raw = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!raw) throw new Error('Gemini returned no content');

    return NextResponse.json(JSON.parse(raw));
  } catch (error) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Unknown error' },
      { status: 500 },
    );
  }
}
