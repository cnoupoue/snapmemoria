import type {
  CreateMemorySourceRequest,
  Diagnostics,
  FavoritesBackup,
  FavoritesRestoreSummary,
  FlashbackResponse,
  FolderSelection,
  Memory,
  MemoryDetail,
  MemoryPage,
  MemorySource,
  MemoryScanJob,
  CompatibilityPlayback,
  OriginalFileOpen,
  SourceAvailability,
  TimelineMonth,
  TimelineYear,
} from './types';

type ApiErrorResponse = {
  status: number;
  code: string;
  message: string;
  timestamp: string;
};

const ERROR_MESSAGES: Record<string, string> = {
  FOLDER_PICKER_UNAVAILABLE:
    'Folder selection is unavailable in this environment. Enter the folder path manually.',
  SOURCE_UNAVAILABLE:
    'The configured source folder is currently unavailable. Connect the drive containing this source, then refresh its status.',
  VIDEO_THUMBNAIL_UNAVAILABLE:
    'Video preview generation is unavailable, but the original video can still be opened.',
};

export class MemoriaVaultApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly timestamp: string | null;

  constructor(error: ApiErrorResponse) {
    super(ERROR_MESSAGES[error.code] ?? error.message);
    this.name = 'MemoriaVaultApiError';
    this.status = error.status;
    this.code = error.code;
    this.timestamp = error.timestamp;
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, options);

  if (!response.ok) {
    const contentType = response.headers.get('Content-Type') ?? '';

    if (contentType.includes('application/json')) {
      const apiError = (await response.json()) as ApiErrorResponse;

      throw new MemoriaVaultApiError(apiError);
    }

    throw new Error(`Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function getTimelineYears(): Promise<TimelineYear[]> {
  return request<TimelineYear[]>('/api/timeline/years');
}

export function getTimelineMonths(year: number): Promise<TimelineMonth[]> {
  return request<TimelineMonth[]>(`/api/timeline/years/${year}/months`);
}

export function getMemories(
  year?: number,
  month?: number,
  page = 0,
  size = 48,
): Promise<MemoryPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (year !== undefined) {
    params.set('year', String(year));
  }

  if (month !== undefined) {
    params.set('month', String(month));
  }

  return request<MemoryPage>(`/api/memories?${params.toString()}`);
}

export function getFavoriteMemories(page = 0, size = 48): Promise<MemoryPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  return request<MemoryPage>(`/api/memories/favorites?${params.toString()}`);
}

export function getMemoryDetail(memoryId: string): Promise<MemoryDetail> {
  return request<MemoryDetail>(`/api/memories/${memoryId}`);
}

export function addMemoryFavorite(memoryId: string): Promise<Memory> {
  return request<Memory>(`/api/memories/${memoryId}/favorite`, {
    method: 'PUT',
  });
}

export function removeMemoryFavorite(memoryId: string): Promise<Memory> {
  return request<Memory>(`/api/memories/${memoryId}/favorite`, {
    method: 'DELETE',
  });
}

export function prepareCompatibilityPlayback(
  memoryId: string,
): Promise<CompatibilityPlayback> {
  return request<CompatibilityPlayback>(
    `/api/memories/${memoryId}/playback/compatible`,
    {
      method: 'POST',
    },
  );
}

export function openOriginalFile(memoryId: string): Promise<OriginalFileOpen> {
  return request<OriginalFileOpen>(`/api/memories/${memoryId}/open-original`, {
    method: 'POST',
  });
}

export function getTodayFlashbacks(): Promise<FlashbackResponse> {
  return request<FlashbackResponse>('/api/flashbacks/today');
}

export function getFlashbacksByDate(date: string): Promise<FlashbackResponse> {
  return request<FlashbackResponse>(`/api/flashbacks?date=${date}`);
}

export function getMemorySources(): Promise<MemorySource[]> {
  return request<MemorySource[]>('/api/sources');
}

export function getDiagnostics(): Promise<Diagnostics> {
  return request<Diagnostics>('/api/diagnostics');
}

export function selectMemorySourceFolder(): Promise<FolderSelection> {
  return request<FolderSelection>('/api/sources/select-folder', {
    method: 'POST',
  });
}

export function getMemorySourceAvailability(
  sourceId: string,
): Promise<SourceAvailability> {
  return request<SourceAvailability>(`/api/sources/${sourceId}/availability`);
}

export function exportMemorySourceFavoritesBackup(
  sourceId: string,
): Promise<FavoritesBackup> {
  return request<FavoritesBackup>(`/api/sources/${sourceId}/favorites-backup`);
}

export function previewMemorySourceFavoritesRestore(
  sourceId: string,
  backup: FavoritesBackup,
): Promise<FavoritesRestoreSummary> {
  return request<FavoritesRestoreSummary>(
    `/api/sources/${sourceId}/favorites-backup/preview`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(backup),
    },
  );
}

export function restoreMemorySourceFavoritesBackup(
  sourceId: string,
  backup: FavoritesBackup,
): Promise<FavoritesRestoreSummary> {
  return request<FavoritesRestoreSummary>(
    `/api/sources/${sourceId}/favorites-backup/restore`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(backup),
    },
  );
}

export function createMemorySource(
  source: CreateMemorySourceRequest,
): Promise<MemorySource> {
  return request<MemorySource>('/api/sources', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(source),
  });
}

export function deleteMemorySource(sourceId: string): Promise<void> {
  return request<void>(`/api/sources/${sourceId}`, {
    method: 'DELETE',
  });
}

export function startMemorySourceScan(
  sourceId: string,
): Promise<MemoryScanJob> {
  return request<MemoryScanJob>(`/api/sources/${sourceId}/scan`, {
    method: 'POST',
  });
}

export function getMemoryScanJob(scanJobId: string): Promise<MemoryScanJob> {
  return request<MemoryScanJob>(`/api/scans/${scanJobId}`);
}

export function getLatestMemorySourceScan(
  sourceId: string,
): Promise<MemoryScanJob> {
  return request<MemoryScanJob>(`/api/scans/latest/source/${sourceId}`);
}
