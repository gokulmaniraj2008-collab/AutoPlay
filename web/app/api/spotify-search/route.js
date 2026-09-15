import { NextResponse } from 'next/server';

const TOKEN_URL = 'https://accounts.spotify.com/api/token';
const SEARCH_URL = 'https://api.spotify.com/v1/search';

async function getAccessToken() {
  const clientId = process.env.SPOTIFY_CLIENT_ID;
  const clientSecret = process.env.SPOTIFY_CLIENT_SECRET;

  if (!clientId || !clientSecret) {
    throw new Error('Spotify API credentials are not configured on the server.');
  }

  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');
  const tokenResponse = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${credentials}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
    cache: 'no-store',
  });

  const tokenData = await tokenResponse.json();
  if (!tokenResponse.ok || !tokenData.access_token) {
    throw new Error(tokenData?.error_description || 'Spotify authentication failed.');
  }

  return tokenData.access_token;
}

function firstImage(images) {
  return Array.isArray(images) && images.length > 0 ? images[0]?.url || null : null;
}

function mapTrack(track) {
  return {
    type: 'track',
    id: track.id,
    title: track.name,
    subtitle: Array.isArray(track.artists) ? track.artists.map((artist) => artist.name).join(', ') : 'Spotify',
    imageUrl: firstImage(track.album?.images),
    spotifyUrl: track.external_urls?.spotify || `https://open.spotify.com/track/${track.id}`,
    uri: track.uri,
  };
}

function mapPlaylist(playlist) {
  return {
    type: 'playlist',
    id: playlist.id,
    title: playlist.name,
    subtitle: playlist.owner?.display_name ? `Playlist • ${playlist.owner.display_name}` : 'Spotify • Playlist',
    imageUrl: firstImage(playlist.images),
    spotifyUrl: playlist.external_urls?.spotify || `https://open.spotify.com/playlist/${playlist.id}`,
    uri: playlist.uri,
  };
}

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const query = (searchParams.get('q') || '').trim();

  if (!query) {
    return NextResponse.json({ error: 'Missing search query.' }, { status: 400 });
  }

  try {
    const token = await getAccessToken();
    const params = new URLSearchParams({
      q: query,
      type: 'track,playlist',
      market: 'IN',
      limit: '5',
    });

    const response = await fetch(`${SEARCH_URL}?${params.toString()}`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: 'no-store',
    });
    const data = await response.json();

    if (!response.ok) {
      return NextResponse.json(
        { error: data?.error?.message || 'Spotify search failed.' },
        { status: response.status }
      );
    }

    const tracks = (data.tracks?.items || []).map(mapTrack);
    const playlists = (data.playlists?.items || []).map(mapPlaylist);

    return NextResponse.json({
      query,
      results: [...tracks, ...playlists].slice(0, 10),
    });
  } catch (error) {
    return NextResponse.json(
      { error: error?.message || 'Spotify search is unavailable.' },
      { status: 500 }
    );
  }
}
