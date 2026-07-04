import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MemorySource } from './api/types';
import App from './App';

vi.mock('./api/snapmemoriaApi', () => ({
  createMemorySource: vi.fn(),
  deleteMemorySource: vi.fn(),
  getFlashbacksByDate: vi.fn(),
  getDiagnostics: vi.fn(),
  getLatestMemorySourceScan: vi.fn().mockRejectedValue(new Error('No scan')),
  getMemories: vi.fn(),
  getMemoryDetail: vi.fn(),
  getMemoryScanJob: vi.fn(),
  getMemorySourceAvailability: vi.fn(),
  getMemorySources: vi.fn(),
  selectMemorySourceFolder: vi.fn(),
  getTimelineMonths: vi.fn(),
  getTimelineYears: vi.fn(),
  getTodayFlashbacks: vi.fn(),
  startMemorySourceScan: vi.fn(),
  SnapmemoriaApiError: class SnapmemoriaApiError extends Error {},
}));

import {
  createMemorySource,
  getDiagnostics,
  getMemories,
  getMemoryDetail,
  getMemorySources,
  getTimelineMonths,
  selectMemorySourceFolder,
  startMemorySourceScan,
  getTimelineYears,
} from './api/snapmemoriaApi';

const createMemorySourceMock = vi.mocked(createMemorySource);
const getDiagnosticsMock = vi.mocked(getDiagnostics);
const getMemoriesMock = vi.mocked(getMemories);
const getMemoryDetailMock = vi.mocked(getMemoryDetail);
const getMemorySourcesMock = vi.mocked(getMemorySources);
const getTimelineMonthsMock = vi.mocked(getTimelineMonths);
const selectMemorySourceFolderMock = vi.mocked(selectMemorySourceFolder);
const startMemorySourceScanMock = vi.mocked(startMemorySourceScan);
const getTimelineYearsMock = vi.mocked(getTimelineYears);

beforeEach(() => {
  getDiagnosticsMock.mockResolvedValue({
    appVersion: '0.1.0',
    platform: null,
    videoPreviews: {
      available: true,
      source: 'BUNDLED',
      message: 'Using bundled FFmpeg.',
    },
    sources: {
      configured: 0,
      available: 0,
      unavailable: 0,
    },
    database: {
      status: 'READY',
    },
  });
  getMemoriesMock.mockResolvedValue({
    content: [],
    page: 0,
    size: 48,
    totalElements: 0,
    totalPages: 0,
  });
  getMemoryDetailMock.mockResolvedValue({
    id: 'memory-video',
    capturedAt: '2026-01-01',
    mediaType: 'VIDEO',
    hasOverlay: false,
    fileSizeBytes: 1024,
    lastModifiedAt: '2026-01-01T00:00:00Z',
    mediaUrl: '/api/memories/memory-video/media',
    overlayUrl: null,
  });
  getTimelineMonthsMock.mockResolvedValue([]);
  getTimelineYearsMock.mockResolvedValue([]);
  selectMemorySourceFolderMock.mockResolvedValue({
    selected: false,
    path: null,
    name: null,
  });
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
});

afterEach(() => {
  vi.clearAllMocks();
  cleanup();
});

function buildSource(source: Partial<MemorySource> = {}): MemorySource {
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

describe('App onboarding', () => {
  it('sets the public browser title', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    await screen.findByText('Welcome to Memoria Vault');

    expect(document.title).toBe('Memoria Vault');
  });

  it('renders onboarding when there are no configured sources', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Welcome to Memoria Vault'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Choose exported archive folder'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/SnapMemoria/)).not.toBeInTheDocument();
    expect(screen.queryByText(/official Snapchat/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/affiliated with/i)).not.toBeInTheDocument();
  });

  it('does not show the archive as a broken empty state when no sources exist', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Welcome to Memoria Vault'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('No Memories found for this period.'),
    ).not.toBeInTheDocument();
    expect(getTimelineYearsMock).not.toHaveBeenCalled();
  });

  it('explains that files remain local', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Your files stay on your computer.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Nothing is uploaded.')).toBeInTheDocument();
  });

  it('shows the independence disclaimer and descriptive compatibility wording', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

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
  });

  it('shows the folder structure example', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(await screen.findByText(/exported-archive\//)).toBeInTheDocument();
    expect(screen.getByText(/memories 2\//)).toBeInTheDocument();
    expect(screen.getByText(/memories 3\//)).toBeInTheDocument();
  });

  it('opens the folder selection flow from the primary action', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([]);
    selectMemorySourceFolderMock.mockResolvedValue({
      selected: true,
      path: '/Volumes/SNAPCHAT/snapchat-memories',
      name: 'snapchat-memories',
    });

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    );

    expect(
      screen.getByRole('heading', { name: 'Settings' }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(selectMemorySourceFolderMock).toHaveBeenCalled();
    });
    expect(screen.getByLabelText('Source name')).toHaveValue(
      'snapchat-memories',
    );
    expect(
      screen.getByLabelText('Or enter the folder path manually'),
    ).toHaveValue('/Volumes/SNAPCHAT/snapchat-memories');
  });

  it('hides onboarding after a source is added', async () => {
    const user = userEvent.setup();
    const source = buildSource();

    getMemorySourcesMock.mockResolvedValue([]);
    createMemorySourceMock.mockResolvedValue(source);

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    );
    await user.type(screen.getByLabelText('Source name'), 'Snapchat USB');
    await user.type(
      screen.getByLabelText('Or enter the folder path manually'),
      '/Volumes/SNAP/snapchat-memories',
    );
    await user.click(screen.getByRole('button', { name: 'Add source' }));

    await waitFor(() => {
      expect(
        screen.queryByText('Welcome to Memoria Vault'),
      ).not.toBeInTheDocument();
    });
    expect(startMemorySourceScanMock).toHaveBeenCalledWith(source.id);
    expect(screen.getByText('Scanning memories…')).toBeInTheDocument();
  });

  it('renders source loading errors safely', async () => {
    getMemorySourcesMock.mockRejectedValue(
      new Error('/Users/private/path failed'),
    );

    render(<App />);

    expect(
      await screen.findByText(
        'Could not load setup status. Check that the backend is running.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('/Users/private/path failed'),
    ).not.toBeInTheDocument();
  });
});

describe('App footer', () => {
  it('shows project links and ownership information', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('All rights reserved Cameron Noupoue.'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: 'Open source on GitHub, contributions welcome',
      }),
    ).toHaveAttribute('href', 'https://github.com/cnoupoue/snapmemoria');

    expect(screen.getByRole('link', { name: 'LinkedIn' })).toHaveAttribute(
      'href',
      'https://www.linkedin.com/in/cnoupoue',
    );
  });
});

describe('App video preview fallback', () => {
  it('renders a clickable fallback when a video thumbnail is unavailable', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 1 }]);
    getMemoriesMock.mockResolvedValue({
      content: [
        {
          id: 'memory-video',
          capturedAt: '2026-01-01',
          mediaType: 'VIDEO',
          hasOverlay: false,
          fileSizeBytes: 1024,
          lastModifiedAt: '2026-01-01T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-video/thumbnail',
        },
      ],
      page: 0,
      size: 48,
      totalElements: 1,
      totalPages: 1,
    });

    render(<App />);

    const thumbnail = await screen.findByAltText('Memory from 2026-01-01');
    fireEvent.error(thumbnail);

    expect(screen.getByText('Video preview unavailable')).toBeInTheDocument();
    expect(screen.getByText('Open video')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: 'Open Memory from 2026-01-01' }),
    );

    await waitFor(() => {
      expect(getMemoryDetailMock).toHaveBeenCalledWith('memory-video');
    });
  });
});
