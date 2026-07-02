export type TimelineYear = {
  year: number;
  memoryCount: number;
};

export type TimelineMonth = {
  month: number;
  memoryCount: number;
};

export type Memory = {
  id: string;
  capturedAt: string;
  mediaType: 'IMAGE' | 'VIDEO';
  hasOverlay: boolean;
  fileSizeBytes: number;
  lastModifiedAt: string;
  thumbnailUrl: string | null;
};

export type MemoryPage = {
  content: Memory[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type MemoryDetail = {
  id: string;
  capturedAt: string;
  mediaType: 'IMAGE' | 'VIDEO';
  hasOverlay: boolean;
  fileSizeBytes: number;
  lastModifiedAt: string;
  mediaUrl: string;
  overlayUrl: string | null;
};

export type FlashbackMemory = {
  id: string;
  capturedAt: string;
  year: number;
  yearsAgo: number;
  mediaType: 'IMAGE' | 'VIDEO';
  hasOverlay: boolean;
  fileSizeBytes: number;
};

export type FlashbackResponse = {
  date: string;
  memories: FlashbackMemory[];
};

export type MemorySource = {
  id: string;
  name: string;
  rootPath: string;
  lastScanAt: string | null;
  lastScanStatus: string | null;
  availabilityStatus: SourceAvailabilityStatus;
  availabilityMessage: string;
  createdAt: string;
  updatedAt: string;
};

export type SourceAvailabilityStatus =
  'AVAILABLE' | 'UNAVAILABLE' | 'NOT_A_DIRECTORY' | 'NOT_READABLE';

export type SourceAvailability = {
  availabilityStatus: SourceAvailabilityStatus;
  availabilityMessage: string;
};

export type FolderSelection = {
  selected: boolean;
  path: string | null;
  name: string | null;
};

export type CreateMemorySourceRequest = {
  name: string;
  rootPath: string;
};

export type MemoryScanJob = {
  id: string;
  sourceId: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';

  totalFiles: number;
  filesProcessed: number;

  mainImages: number;
  mainVideos: number;
  overlays: number;

  indexedMemories: number;
  attachedOverlays: number;
  unmatchedOverlays: number;

  unsupportedFiles: number;
  unreadableFiles: number;

  errorMessage: string | null;

  startedAt: string;
  completedAt: string | null;
  updatedAt: string;
};

export type VideoPreviewDiagnostics = {
  available: boolean;
  source: 'CONFIGURED' | 'BUNDLED' | 'SYSTEM' | 'UNAVAILABLE';
  message: string;
};

export type SourceDiagnostics = {
  configured: number;
  available: number;
  unavailable: number;
};

export type DatabaseDiagnostics = {
  status: 'READY' | string;
};

export type Diagnostics = {
  appVersion: string;
  platform: string | null;
  videoPreviews: VideoPreviewDiagnostics;
  sources: SourceDiagnostics;
  database: DatabaseDiagnostics;
};
