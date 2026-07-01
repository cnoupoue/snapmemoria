import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryViewer } from './MemoryViewer';

afterEach(() => {
  cleanup();
});

describe('MemoryViewer', () => {
  it('renders an image memory', () => {
    render(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={{
          id: 'memory-1',
          capturedAt: '2020-06-10',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1_500_000,
          lastModifiedAt: '2020-06-10T10:00:00Z',
          mediaUrl: '/api/memories/memory-1/media',
          overlayUrl: null,
        }}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();

    expect(
      screen.getByRole('img', {
        name: 'Snapchat Memory from 2020-06-10',
      }),
    ).toHaveAttribute('src', '/api/memories/memory-1/media');
  });

  it('does not render when no memory is selected', () => {
    render(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={null}
        onClose={vi.fn()}
      />,
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
