import { NextResponse } from 'next/server';

const SYSTEM_PROMPT = `You are the ChatGPT brain behind Tommy, an Android assistant.
Your job is to understand a natural-language command and convert it into ONE safe, executable Tommy command.
Return only the requested JSON object.

Allowed actions:
- open_app
- search_web
- youtube_search
- open_instagram_reels
- open_instagram_comments
- instagram_like_reel
- instagram_follow_account
- instagram_scroll_reel
- spotify_search
- open_comments
- none

Rules:
- Preserve the user's intent and search terms.
- Never invent unsupported actions.
- For a request to open an app, use action=open_app and target=the app name.
- For web/search requests, put only the requested search terms in query.
- For Instagram commands, keep the action specific when possible.
- executionCommand must be a short natural-language command that the existing Tommy Android SkillEngine can execute. If no registered skill can safely execute the request, set action=none and executionCommand to an empty string.
- reply should be a concise Tommy-style acknowledgement.
- If the request is ambiguous or unsafe, use action=none and an empty executionCommand.`;

const ACTIONS = new Set([
  'open_app',
  'search_web',
  'youtube_search',
  'open_instagram_reels',
  'open_instagram_comments',
  'instagram_like_reel',
  'instagram_follow_account',
  'instagram_scroll_reel',
  'spotify_search',
  'open_comments',
  'none',
]);

function fallback(text) {
  return {
    reply: `I understood: ${text}`,
    action: 'none',
    target: '',
    query: '',
    executionCommand: '',
    source: 'chatgpt',
  };
}

export async function POST(request) {
  try {
    const body = await request.json();
    const text = typeof body?.text === 'string' ? body.text.trim() : '';

    if (!text) {
      return NextResponse.json({ error: 'text is required' }, { status: 400 });
    }

    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) {
      return NextResponse.json(
        { error: 'OPENAI_API_KEY is not configured on the production server' },
        { status: 503 },
      );
    }

    const model = process.env.OPENAI_TOMMY_MODEL || 'gpt-5.6-luna';

    const response = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model,
        instructions: SYSTEM_PROMPT,
        input: text,
        text: {
          format: {
            type: 'json_schema',
            name: 'tommy_command',
            strict: true,
            schema: {
              type: 'object',
              additionalProperties: false,
              properties: {
                reply: { type: 'string' },
                action: { type: 'string', enum: Array.from(ACTIONS) },
                target: { type: 'string' },
                query: { type: 'string' },
                executionCommand: { type: 'string' },
              },
              required: ['reply', 'action', 'target', 'query', 'executionCommand'],
            },
          },
        },
      }),
    });

    const data = await response.json();

    if (!response.ok) {
      return NextResponse.json(
        { error: 'OpenAI request failed', details: data?.error?.message || `OpenAI HTTP ${response.status}` },
        { status: 502 },
      );
    }

    const raw = data?.output_text;
    if (!raw) {
      return NextResponse.json({ error: 'OpenAI returned no text output' }, { status: 502 });
    }

    const result = JSON.parse(raw);
    const action = ACTIONS.has(result.action) ? result.action : 'none';
    const executionCommand = action === 'none' ? '' : String(result.executionCommand || '').trim();

    return NextResponse.json({
      reply: String(result.reply || `I understood: ${text}`),
      action,
      target: String(result.target || ''),
      query: String(result.query || ''),
      executionCommand,
      source: 'chatgpt',
      model,
    });
  } catch (error) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Unknown ChatGPT bridge error' },
      { status: 500 },
    );
  }
}
