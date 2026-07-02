import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Diagnostics, MemorySource } from '../api/types';
import { SettingsPage } from './SettingsPage';

vi.mock('../api/snapmemoriaApi', () => ({
  createMemorySource: vi.fn(),
  deleteMemorySource: vi.fn(),
  getDiagnostics: vi.fn(),
  getLatestMemorySourceScan: vi.fn().mockRejectedValue(new Error('No scan')),
  getMemoryScanJob: vi.fn(),
  getMemorySourceAvailability: vi.fn(),
  getMemorySources: vi.fn(),
  selectMemorySourceFolder: vi.fn(),
  startMemorySourceScan: vi.fn(),
  SnapmemoriaApiError: class SnapmemoriaApiError extends Error {
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
  getDiagnostics,
  getMemorySourceAvailability,
  getMemorySources,
  selectMemorySourceFolder,
  SnapmemoriaApiError,
  startMemorySourceScan,
} from '../api/snapmemoriaApi';

const createMemorySourceMock = vi.mocked(createMemorySource);
const deleteMemorySourceMock = vi.mocked(deleteMemorySource);
const getDiagnosticsMock = vi.mocked(getDiagnostics);
const getMemorySourcesMock = vi.mocked(getMemorySources);
const getMemorySourceAvailabilityMock = vi.mocked(getMemorySourceAvailability);
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
        name: 'Choose Snapchat export folder',
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
        name: 'Choose Snapchat export folder',
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
      screen.getByRole('button', { name: 'Choose Snapchat export folder' }),
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
      screen.getByRole('button', { name: 'Choose Snapchat export folder' }),
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
      new SnapmemoriaApiError({
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
        name: 'Choose Snapchat export folder',
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

    expect(await screen.findByText('SnapMemoria 0.1.0')).toBeInTheDocument();
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

    expect(writeText).toHaveBeenCalledWith(`SnapMemoria diagnostics

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
    expect(screen.getByText('Scanning Memories…')).toBeInTheDocument();
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
