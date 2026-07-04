export type PlaybackFailureCategory =
  | 'SOURCE_UNAVAILABLE'
  | 'MEDIA_FILE_MISSING'
  | 'MEDIA_PATH_REJECTED'
  | 'VIDEO_STREAM_FAILED'
  | 'VIDEO_FORMAT_UNSUPPORTED'
  | 'BROWSER_MEDIA_ERROR';

export type PlaybackDiagnosticReport = {
  result: 'Failed';
  category: PlaybackFailureCategory;
  directPlayback: 'Failed';
  fallbackPlayback:
    'Not attempted' | 'Generated' | 'Available' | 'Unavailable' | 'Failed';
  httpStatus: number | null;
  rangeRequestsSupported: boolean | null;
  mimeType: string | null;
  videoErrorCode: number | null;
  videoErrorMessage: string | null;
  networkState: number | null;
  readyState: number | null;
  currentSrcCategory: string;
  userAgentCategory: string;
};

type ApiErrorResponse = {
  code?: string;
};

const STORAGE_KEY = 'memoriaVault.lastVideoPlaybackDiagnostic';

const MEDIA_ERR_SRC_NOT_SUPPORTED = 4;

export function getPlaybackMessage(category: PlaybackFailureCategory): {
  title: string;
  detail: string;
} {
  switch (category) {
    case 'SOURCE_UNAVAILABLE':
      return {
        title: 'This archive source is currently unavailable.',
        detail: 'Reconnect the drive containing it and try again.',
      };
    case 'MEDIA_FILE_MISSING':
      return {
        title: 'This original file could not be found.',
        detail: 'Try rescanning the source if files were moved or changed.',
      };
    case 'MEDIA_PATH_REJECTED':
    case 'VIDEO_STREAM_FAILED':
      return {
        title:
          'The video file is available, but it could not be streamed locally.',
        detail: 'Try restarting the app or opening the original file directly.',
      };
    case 'VIDEO_FORMAT_UNSUPPORTED':
      return {
        title:
          'This video is available, but this browser cannot play its format.',
        detail: 'Try opening the original file with a media player.',
      };
    case 'BROWSER_MEDIA_ERROR':
      return {
        title: 'The browser could not play this video.',
        detail: 'Try opening the original file with a media player.',
      };
  }
}

export function readLastPlaybackDiagnostic(): PlaybackDiagnosticReport | null {
  try {
    const rawValue = window.localStorage.getItem(STORAGE_KEY);

    if (!rawValue) {
      return null;
    }

    return JSON.parse(rawValue) as PlaybackDiagnosticReport;
  } catch {
    return null;
  }
}

export function saveLastPlaybackDiagnostic(
  diagnostic: PlaybackDiagnosticReport,
) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(diagnostic));
  } catch {
    // Diagnostics should never interfere with playback UI.
  }
}

export function savePlaybackFallbackResult(
  fallbackPlayback: PlaybackDiagnosticReport['fallbackPlayback'],
) {
  const diagnostic = readLastPlaybackDiagnostic();

  if (!diagnostic) {
    return;
  }

  saveLastPlaybackDiagnostic({
    ...diagnostic,
    fallbackPlayback,
  });
}

export async function diagnoseVideoPlaybackFailure(
  video: HTMLVideoElement,
  mediaUrl: string,
): Promise<PlaybackDiagnosticReport> {
  const baseDiagnostic = buildBaseDiagnostic(video);

  try {
    const response = await fetch(mediaUrl, {
      headers: {
        Range: 'bytes=0-0',
      },
    });
    const mimeType = response.headers.get('Content-Type');
    const rangeRequestsSupported =
      response.headers.get('Accept-Ranges')?.toLowerCase() === 'bytes' ||
      response.status === 206;

    if (!response.ok) {
      return {
        ...baseDiagnostic,
        category: await categoryFromErrorResponse(response),
        httpStatus: response.status,
        rangeRequestsSupported,
        mimeType,
      };
    }

    return {
      ...baseDiagnostic,
      category:
        video.error?.code === MEDIA_ERR_SRC_NOT_SUPPORTED
          ? 'VIDEO_FORMAT_UNSUPPORTED'
          : 'BROWSER_MEDIA_ERROR',
      httpStatus: response.status,
      rangeRequestsSupported,
      mimeType,
    };
  } catch {
    return {
      ...baseDiagnostic,
      category: 'VIDEO_STREAM_FAILED',
      httpStatus: null,
      rangeRequestsSupported: null,
      mimeType: null,
    };
  }
}

function buildBaseDiagnostic(
  video: HTMLVideoElement,
): PlaybackDiagnosticReport {
  return {
    result: 'Failed',
    directPlayback: 'Failed',
    fallbackPlayback: 'Not attempted',
    category: 'BROWSER_MEDIA_ERROR',
    httpStatus: null,
    rangeRequestsSupported: null,
    mimeType: null,
    videoErrorCode: video.error?.code ?? null,
    videoErrorMessage: sanitizeVideoErrorMessage(video.error?.message ?? null),
    networkState: video.networkState,
    readyState: video.readyState,
    currentSrcCategory: categorizeMediaUrl(video.currentSrc),
    userAgentCategory: categorizeUserAgent(window.navigator.userAgent),
  };
}

async function categoryFromErrorResponse(
  response: Response,
): Promise<PlaybackFailureCategory> {
  const contentType = response.headers.get('Content-Type') ?? '';

  if (contentType.includes('application/json')) {
    try {
      const apiError = (await response.json()) as ApiErrorResponse;

      switch (apiError.code) {
        case 'SOURCE_UNAVAILABLE':
          return 'SOURCE_UNAVAILABLE';
        case 'MEDIA_FILE_MISSING':
          return 'MEDIA_FILE_MISSING';
        case 'MEDIA_PATH_REJECTED':
          return 'MEDIA_PATH_REJECTED';
      }
    } catch {
      return 'VIDEO_STREAM_FAILED';
    }
  }

  return 'VIDEO_STREAM_FAILED';
}

function sanitizeVideoErrorMessage(message: string | null): string | null {
  if (!message) {
    return null;
  }

  return message
    .replaceAll(/https?:\/\/\S+/g, '[redacted-url]')
    .replaceAll(/\/\S+/g, '[redacted-path]');
}

function categorizeMediaUrl(currentSrc: string): string {
  if (!currentSrc) {
    return 'empty';
  }

  try {
    const url = new URL(currentSrc, window.location.origin);

    if (url.origin !== window.location.origin) {
      return 'other-origin';
    }

    if (/^\/api\/memories\/[^/]+\/(?:media|overlay)$/.test(url.pathname)) {
      return 'local-memory-media-endpoint';
    }

    return 'local-other-endpoint';
  } catch {
    return 'unparseable';
  }
}

function categorizeUserAgent(userAgent: string): string {
  if (/Edg\//.test(userAgent)) {
    return 'Edge';
  }

  if (/Chrome\//.test(userAgent) && !/Chromium\//.test(userAgent)) {
    return 'Chrome';
  }

  if (/Safari\//.test(userAgent) && !/Chrome\//.test(userAgent)) {
    return 'Safari';
  }

  if (/Firefox\//.test(userAgent)) {
    return 'Firefox';
  }

  return 'Other';
}
