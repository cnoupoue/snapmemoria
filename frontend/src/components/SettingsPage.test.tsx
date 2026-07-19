import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Diagnostics, MemorySource } from '../api/types';
import { saveLastPlaybackDiagnostic } from '../videoPlaybackDiagnostics';
import { SettingsPage } from './SettingsPage';

vi.mock('../api/memoriaVaultApi', () => ({
  createMemorySource: vi.fn(),
  deleteMemorySource: vi.fn(),
  exportMemorySourceFavoritesBackup: vi.fn(),
  getDiagnostics: vi.fn(),
  getLatestMemorySourceScan: vi.fn().mockRejectedValue(new Error('No scan')),
  getMemoryScanJob: vi.fn(),
  getMemorySourceAvailability: vi.fn(),
  getMemorySources: vi.fn(),
  previewMemorySourceFavoritesRestore: vi.fn(),
  restoreMemorySourceFavoritesBackup: vi.fn(),
  selectMemorySourceFolder: vi.fn(),
  startMemorySourceScan: vi.fn(),
  MemoriaVaultApiError: class MemoriaVaultApiError extends Error {
    readonly status: number;
    readonly code: string;
    readonly timestamp: string | null;

    constructor(error: {
      status: number;
      code: string;
      message: string;
      timestamp: string | null;
    }) {
      super(error.message);
      this.status = error.status;
      this.code = error.code;
      this.timestamp = error.timestamp;
    }
  },
}));

import {
  createMemorySource,
  deleteMemorySource,
  exportMemorySourceFavoritesBackup,
  getDiagnostics,
  getMemorySourceAvailability,
  getMemorySources,
  previewMemorySourceFavoritesRestore,
  restoreMemorySourceFavoritesBackup,
  selectMemorySourceFolder,
  MemoriaVaultApiError,
  startMemorySourceScan,
} from '../api/memoriaVaultApi';

const createMemorySourceMock = vi.mocked(createMemorySource);
const deleteMemorySourceMock = vi.mocked(deleteMemorySource);
const exportMemorySourceFavoritesBackupMock = vi.mocked(
  exportMemorySourceFavoritesBackup,
);
const getDiagnosticsMock = vi.mocked(getDiagnostics);
const getMemorySourcesMock = vi.mocked(getMemorySources);
const getMemorySourceAvailabilityMock = vi.mocked(getMemorySourceAvailability);
const previewMemorySourceFavoritesRestoreMock = vi.mocked(
  previewMemorySourceFavoritesRestore,
);
const restoreMemorySourceFavoritesBackupMock = vi.mocked(
  restoreMemorySourceFavoritesBackup,
);
const selectMemorySourceFolderMock = vi.mocked(selectMemorySourceFolder);
const startMemorySourceScanMock = vi.mocked(startMemorySourceScan);

beforeEach(() => {
  getDiagnosticsMock.mockResolvedValue(
    buildDiagnostics({
      available: true,
      source: 'BUNDLED',
      message: 'Using bundled FFmpeg.',
    }),
  );
});

afterEach(() => {
  cleanup();
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: undefined,
  });
  vi.restoreAllMocks();
  vi.clearAllMocks();
  window.localStorage.clear();
});

function buildSource(source: Partial<MemorySource>): MemorySource {
  return {
    id: 'source-1',
    name: 'Snapchat USB',
    rootPath: '/Volumes/SNAP/snapchat-memories',
    lastScanAt: null,
    lastScanStatus: 'NOT_SCANNED',
    availabilityStatus: 'AVAILABLE',
    availabilityMessage: 'Source folder is available.',
    favoriteCount: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...source,
  };
}

function buildDiagnostics(
  videoPreviews: Partial<Diagnostics['videoPreviews']> = {},
  sources: Partial<Diagnostics['sources']> = {},
): Diagnostics {
  return {
    appVersion: '0.1.0',
    platform: null,
    videoPreviews: {
      available: true,
      source: 'BUNDLED',
      message: 'Using bundled FFmpeg.',
      ...videoPreviews,
    },
    sources: {
      configured: 1,
      available: 1,
      unavailable: 0,
      ...sources,
    },
    database: {
      status: 'READY',
    },
  };
}

describe('SettingsPage', () => {
  it('shows the folder picker action in source creation', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    ).toBeInTheDocument();
  });

  it('populates path and source name from a selected folder', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([]);
    selectMemorySourceFolderMock.mockResolvedValue({
      selected: true,
      path: '/Volumes/SNAPCHAT/snapchat-memories',
      name: 'snapchat-memories',
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    );

    expect(screen.getByLabelText('Source name')).toHaveValue(
      'snapchat-memories',
    );
    expect(
      screen.getByLabelText('Or enter the folder path manually'),
    ).toHaveValue('/Volumes/SNAPCHAT/snapchat-memories');
  });

  it('preserves an existing manually typed source name', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([]);
    selectMemorySourceFolderMock.mockResolvedValue({
      selected: true,
      path: '/Volumes/SNAPCHAT/snapchat-memories',
      name: 'snapchat-memories',
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.type(await screen.findByLabelText('Source name'), 'My USB');
    await user.click(
      screen.getByRole('button', { name: 'Choose exported archive folder' }),
    );

    expect(screen.getByLabelText('Source name')).toHaveValue('My USB');
    expect(
      screen.getByLabelText('Or enter the folder path manually'),
    ).toHaveValue('/Volumes/SNAPCHAT/snapchat-memories');
  });

  it('keeps form values unchanged when folder selection is cancelled', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([]);
    selectMemorySourceFolderMock.mockResolvedValue({
      selected: false,
      path: null,
      name: null,
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.type(await screen.findByLabelText('Source name'), 'Manual name');
    await user.type(
      screen.getByLabelText('Or enter the folder path manually'),
      '/Volumes/manual/snapchat-memories',
    );
    await user.click(
      screen.getByRole('button', { name: 'Choose exported archive folder' }),
    );

    expect(screen.getByLabelText('Source name')).toHaveValue('Manual name');
    expect(
      screen.getByLabelText('Or enter the folder path manually'),
    ).toHaveValue('/Volumes/manual/snapchat-memories');
    expect(screen.queryByText(/unavailable/i)).not.toBeInTheDocument();
  });

  it('shows manual path fallback when folder picker is unavailable', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([]);
    selectMemorySourceFolderMock.mockRejectedValue(
      new MemoriaVaultApiError({
        status: 409,
        code: 'FOLDER_PICKER_UNAVAILABLE',
        message:
          'Folder selection is unavailable in this environment. Enter the folder path manually.',
        timestamp: '2026-01-01T00:00:00Z',
      }),
    );

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    );

    expect(
      screen.getByText(
        'Folder selection is unavailable in this environment. Enter the folder path manually.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Or enter the folder path manually'),
    ).toBeEnabled();
  });

  it('renders available source status', async () => {
    getMemorySourcesMock.mockResolvedValue([buildSource({})]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(await screen.findByText('Available')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scan source' })).toBeEnabled();
  });

  it('shows ready video preview diagnostics when FFmpeg is available', async () => {
    getMemorySourcesMock.mockResolvedValue([]);
    getDiagnosticsMock.mockResolvedValue(
      buildDiagnostics(
        {
          available: true,
          source: 'BUNDLED',
          message: 'Using bundled FFmpeg.',
        },
        {
          configured: 1,
          available: 1,
          unavailable: 0,
        },
      ),
    );

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(await screen.findByText('Memoria Vault 0.1.0')).toBeInTheDocument();
    expect(screen.getAllByText('Ready')).toHaveLength(2);
    expect(screen.getByText('Using bundled FFmpeg')).toBeInTheDocument();
    expect(screen.getByText('Sources: 1 configured')).toBeInTheDocument();
  });

  it('renders bundled FFmpeg diagnostics', async () => {
    getMemorySourcesMock.mockResolvedValue([]);
    getDiagnosticsMock.mockResolvedValue(
      buildDiagnostics({
        available: true,
        source: 'BUNDLED',
        message: 'Using bundled FFmpeg.',
      }),
    );

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(await screen.findByText('Using bundled FFmpeg')).toBeInTheDocument();
  });

  it('shows the independence disclaimer in Settings', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByText(
        'This application is an independent, open-source local tool and is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Snap Inc. or Snapchat.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Compatible Snapchat export formats may be read locally. Compatibility references are descriptive only.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/MemoriaVault/)).not.toBeInTheDocument();
    expect(screen.queryByText(/official Snapchat/i)).not.toBeInTheDocument();
  });

  it('uses neutral wording for the primary source selection action', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    ).toBeInTheDocument();
  });

  it('shows safe unavailable video preview diagnostics', async () => {
    getMemorySourcesMock.mockResolvedValue([]);
    getDiagnosticsMock.mockResolvedValue(
      buildDiagnostics({
        available: false,
        source: 'UNAVAILABLE',
        message: 'Original videos can still be opened.',
      }),
    );

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(await screen.findByText('Video previews')).toBeInTheDocument();
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
    expect(
      screen.getByText('Original videos can still be opened.'),
    ).toBeInTheDocument();
  });

  it('copies sanitized diagnostic text to the clipboard', async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);

    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    getMemorySourcesMock.mockResolvedValue([]);
    getDiagnosticsMock.mockResolvedValue(
      buildDiagnostics(
        {
          available: true,
          source: 'BUNDLED',
          message: 'Using bundled FFmpeg.',
        },
        {
          configured: 1,
          available: 1,
          unavailable: 0,
        },
      ),
    );

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Copy diagnostic information',
      }),
    );

    expect(writeText).toHaveBeenCalledWith(`Memoria Vault diagnostics

App version: 0.1.0
Video previews: Ready
FFmpeg source: Bundled
Configured sources: 1
Available sources: 1
Unavailable sources: 0
Local database: Ready`);
    expect(
      screen.getByText('Diagnostic information copied'),
    ).toBeInTheDocument();
  });

  it('shows a safe fallback message when clipboard writing fails', async () => {
    const user = userEvent.setup();

    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
    });
    getMemorySourcesMock.mockResolvedValue([]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Copy diagnostic information',
      }),
    );

    expect(
      screen.getByText(
        'Copying is unavailable in this browser. Select and copy the visible diagnostic details instead.',
      ),
    ).toBeInTheDocument();
  });

  it('never copies source names or paths in the diagnostic report', async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);

    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    getMemorySourcesMock.mockResolvedValue([buildSource({})]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(await screen.findByText('Snapchat USB')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: 'Copy diagnostic information',
      }),
    );

    const copiedReport = writeText.mock.calls[0][0] as string;

    expect(copiedReport).not.toContain('Snapchat USB');
    expect(copiedReport).not.toContain('/Volumes/SNAP');
    expect(copiedReport).not.toContain('snapchat-memories');
  });

  it('copies sanitized last video playback diagnostics without media URLs', async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);

    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    getMemorySourcesMock.mockResolvedValue([]);
    saveLastPlaybackDiagnostic({
      result: 'Failed',
      directPlayback: 'Failed',
      fallbackPlayback: 'Unavailable',
      category: 'VIDEO_FORMAT_UNSUPPORTED',
      httpStatus: 206,
      rangeRequestsSupported: true,
      mimeType: 'video/mp4',
      videoErrorCode: 4,
      videoErrorMessage: 'Unsupported format',
      networkState: 3,
      readyState: 0,
      currentSrcCategory: 'local-memory-media-endpoint',
      userAgentCategory: 'Safari',
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Copy diagnostic information',
      }),
    );

    const copiedReport = writeText.mock.calls[0][0] as string;

    expect(copiedReport).toContain('Last video playback result: Failed');
    expect(copiedReport).toContain('Direct playback: Failed');
    expect(copiedReport).toContain(
      'Playback failure category: VIDEO_FORMAT_UNSUPPORTED',
    );
    expect(copiedReport).toContain('HTTP stream status: 206');
    expect(copiedReport).toContain('Range requests supported: Yes');
    expect(copiedReport).toContain('Video MIME type: video/mp4');
    expect(copiedReport).toContain('Browser media error code: 4');
    expect(copiedReport).toContain('Fallback playback: Unavailable');
    expect(copiedReport).not.toContain('/api/memories');
    expect(copiedReport).not.toContain('memory-video');
    expect(copiedReport).not.toContain('/Users/cameron');
  });

  it('renders unavailable source status', async () => {
    getMemorySourcesMock.mockResolvedValue([
      buildSource({
        availabilityStatus: 'UNAVAILABLE',
        availabilityMessage:
          'Connect the drive containing this source, then refresh its status.',
      }),
    ]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByText('Folder moved or missing'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Connect the drive containing this source, then refresh its status.',
      ),
    ).toBeInTheDocument();
  });

  it('disables scan when source is unavailable', async () => {
    getMemorySourcesMock.mockResolvedValue([
      buildSource({ availabilityStatus: 'NOT_READABLE' }),
    ]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByText('Folder is not readable'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scan source' })).toBeDisabled();
    expect(startMemorySourceScanMock).not.toHaveBeenCalled();
  });

  it('refreshes a single source availability status', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([
      buildSource({
        availabilityStatus: 'UNAVAILABLE',
        availabilityMessage:
          'Connect the drive containing this source, then refresh its status.',
      }),
    ]);
    getMemorySourceAvailabilityMock.mockResolvedValue({
      availabilityStatus: 'AVAILABLE',
      availabilityMessage: 'Source folder is available.',
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByText('Folder moved or missing'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Refresh status' }));

    await waitFor(() => {
      expect(screen.getByText('Available')).toBeInTheDocument();
    });
    expect(getMemorySourceAvailabilityMock).toHaveBeenCalledWith('source-1');
    await waitFor(() => {
      expect(getDiagnosticsMock).toHaveBeenCalledTimes(2);
    });
  });

  it('starts scanning automatically after adding a source', async () => {
    const user = userEvent.setup();
    const source = buildSource({});

    getMemorySourcesMock.mockResolvedValue([]);
    createMemorySourceMock.mockResolvedValue(source);
    startMemorySourceScanMock.mockResolvedValue({
      id: 'scan-1',
      sourceId: source.id,
      status: 'RUNNING',
      totalFiles: 0,
      filesProcessed: 0,
      mainImages: 0,
      mainVideos: 0,
      overlays: 0,
      indexedMemories: 0,
      attachedOverlays: 0,
      unmatchedOverlays: 0,
      unsupportedFiles: 0,
      unreadableFiles: 0,
      errorMessage: null,
      startedAt: '2026-01-01T00:00:00Z',
      completedAt: null,
      updatedAt: '2026-01-01T00:00:00Z',
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.type(await screen.findByLabelText('Source name'), source.name);
    await user.type(
      screen.getByLabelText('Or enter the folder path manually'),
      source.rootPath,
    );
    await user.click(screen.getByRole('button', { name: 'Add source' }));

    await waitFor(() => {
      expect(startMemorySourceScanMock).toHaveBeenCalledWith(source.id);
    });
    expect(screen.getByText('Scanning memories…')).toBeInTheDocument();
  });

  it('warns before rescanning a source with favorites and cancels safely', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource({ favoriteCount: 2 })]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', { name: 'Scan source' }),
    );

    expect(screen.getByRole('dialog')).toHaveTextContent(
      'Favorites linked to memories that are no longer present may be removed.',
    );

    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(startMemorySourceScanMock).not.toHaveBeenCalled();
  });

  it('continues rescan after favorite warning confirmation', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource({ favoriteCount: 1 })]);
    startMemorySourceScanMock.mockResolvedValue({
      id: 'scan-1',
      sourceId: 'source-1',
      status: 'RUNNING',
      totalFiles: 0,
      filesProcessed: 0,
      mainImages: 0,
      mainVideos: 0,
      overlays: 0,
      indexedMemories: 0,
      attachedOverlays: 0,
      unmatchedOverlays: 0,
      unsupportedFiles: 0,
      unreadableFiles: 0,
      errorMessage: null,
      startedAt: '2026-01-01T00:00:00Z',
      completedAt: null,
      updatedAt: '2026-01-01T00:00:00Z',
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', { name: 'Scan source' }),
    );
    await user.click(screen.getByRole('button', { name: 'Continue rescan' }));

    expect(startMemorySourceScanMock).toHaveBeenCalledWith('source-1');
  });

  it('backs up favorites from the rescan warning', async () => {
    const user = userEvent.setup();
    const appendSpy = vi.spyOn(document.body, 'append');
    const createObjectUrlSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:backup');
    const revokeObjectUrlSpy = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockImplementation(() => {});

    HTMLAnchorElement.prototype.click = vi.fn();
    getMemorySourcesMock.mockResolvedValue([buildSource({ favoriteCount: 1 })]);
    exportMemorySourceFavoritesBackupMock.mockResolvedValue({
      version: 1,
      exportedAt: '2026-07-18T00:00:00Z',
      sourceId: 'source-1',
      favorites: [
        {
          memoryId: 'memory-1',
          externalMemoryId: 'external-1',
          capturedAt: '2024-01-01',
          mediaType: 'IMAGE',
          mainPath: '/local/export/memory.jpg',
          favoritedAt: '2026-07-18T10:00:00Z',
        },
      ],
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', { name: 'Scan source' }),
    );
    await user.click(screen.getByRole('button', { name: 'Back up favorites' }));

    await waitFor(() => {
      expect(exportMemorySourceFavoritesBackupMock).toHaveBeenCalledWith(
        'source-1',
      );
    });
    expect(createObjectUrlSpy).toHaveBeenCalledWith(expect.any(Blob));
    expect(appendSpy).toHaveBeenCalledWith(expect.any(HTMLAnchorElement));
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:backup');
    expect(
      screen.getByText('Favorites backup downloaded.'),
    ).toBeInTheDocument();
  });

  it('previews and restores a selected favorites backup', async () => {
    const user = userEvent.setup();
    const source = buildSource({ favoriteCount: 1 });
    const backup = {
      version: 1,
      exportedAt: '2026-07-18T20:30:00Z',
      sourceId: source.id,
      favorites: [
        {
          memoryId: 'memory-1',
          externalMemoryId: 'external-1',
          capturedAt: '2024-01-01',
          mediaType: 'IMAGE',
          mainPath: '/local/export/memory.jpg',
          favoritedAt: '2026-07-18T10:00:00Z',
        },
      ],
    };

    getMemorySourcesMock.mockResolvedValue([source]);
    previewMemorySourceFavoritesRestoreMock.mockResolvedValue({
      totalFavorites: 3,
      restorable: 2,
      restored: 0,
      alreadyFavorite: 1,
      notFound: 1,
    });
    restoreMemorySourceFavoritesBackupMock.mockResolvedValue({
      totalFavorites: 3,
      restorable: 2,
      restored: 1,
      alreadyFavorite: 1,
      notFound: 1,
    });

    const { container } = render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', { name: 'Import Favorites Backup' }),
    );
    await user.upload(
      container.querySelector('input[type="file"]') as HTMLInputElement,
      new File([JSON.stringify(backup)], 'favorites.json', {
        type: 'application/json',
      }),
    );

    expect(previewMemorySourceFavoritesRestoreMock).toHaveBeenCalledWith(
      source.id,
      backup,
    );
    expect(await screen.findByRole('dialog')).toHaveTextContent(
      'Backup contains 3 favorites',
    );
    expect(screen.getByText('Can be restored')).toBeInTheDocument();
    expect(screen.getByText('Already favorite')).toBeInTheDocument();
    expect(screen.getByText('Not found')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Restore Favorites' }));

    expect(restoreMemorySourceFavoritesBackupMock).toHaveBeenCalledWith(
      source.id,
      backup,
    );
    expect(
      await screen.findByText('Favorites restore summary'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        '1 restored · 1 already favorite · 1 could not be matched',
      ),
    ).toBeInTheDocument();
  });

  it('rejects invalid favorites backup JSON before previewing', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource({})]);

    const { container } = render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', { name: 'Import Favorites Backup' }),
    );
    await user.upload(
      container.querySelector('input[type="file"]') as HTMLInputElement,
      new File(['{invalid json'], 'favorites.json', {
        type: 'application/json',
      }),
    );

    expect(
      await screen.findByText(/Expected property name|Unexpected token/),
    ).toBeInTheDocument();
    expect(previewMemorySourceFavoritesRestoreMock).not.toHaveBeenCalled();
  });

  it('rejects unsupported favorites backup versions before previewing', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource({})]);

    const { container } = render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', { name: 'Import Favorites Backup' }),
    );
    await user.upload(
      container.querySelector('input[type="file"]') as HTMLInputElement,
      new File(
        [JSON.stringify({ version: 2, favorites: [] })],
        'favorites.json',
        {
          type: 'application/json',
        },
      ),
    );

    expect(
      await screen.findByText(
        'Only version 1 favorites backups can be imported.',
      ),
    ).toBeInTheDocument();
    expect(previewMemorySourceFavoritesRestoreMock).not.toHaveBeenCalled();
  });

  it('rejects favorites backups with missing required fields before previewing', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource({})]);

    const { container } = render(<SettingsPage onSourceScanned={vi.fn()} />);

    await user.click(
      await screen.findByRole('button', { name: 'Import Favorites Backup' }),
    );
    await user.upload(
      container.querySelector('input[type="file"]') as HTMLInputElement,
      new File(
        [JSON.stringify({ version: 1, favorites: [{ externalMemoryId: '' }] })],
        'favorites.json',
        {
          type: 'application/json',
        },
      ),
    );

    expect(
      await screen.findByText(
        'The favorites backup has missing required fields.',
      ),
    ).toBeInTheDocument();
    expect(previewMemorySourceFavoritesRestoreMock).not.toHaveBeenCalled();
  });

  it('removes a deleted source from the UI and notifies the parent', async () => {
    const user = userEvent.setup();
    const onSourceDeleted = vi.fn();
    const onSourceScanned = vi.fn();

    getMemorySourcesMock.mockResolvedValue([buildSource({})]);
    deleteMemorySourceMock.mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(
      <SettingsPage
        onSourceDeleted={onSourceDeleted}
        onSourceScanned={onSourceScanned}
      />,
    );

    expect(await screen.findByText('Snapchat USB')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Remove' }));

    await waitFor(() => {
      expect(screen.queryByText('Snapchat USB')).not.toBeInTheDocument();
    });
    expect(deleteMemorySourceMock).toHaveBeenCalledWith('source-1');
    expect(onSourceDeleted).toHaveBeenCalledWith('source-1');
    expect(onSourceScanned).toHaveBeenCalled();
  });
});
