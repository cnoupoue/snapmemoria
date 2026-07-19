import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryCard } from './MemoryCard';

afterEach(() => {
  cleanup();
});

describe('MemoryCard favorites', () => {
  it('renders an empty heart for a non-favorited memory', () => {
    render(
      <MemoryCard
        memory={{
          id: 'memory-1',
          capturedAt: '2026-01-01',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1024,
          lastModifiedAt: '2026-01-01T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-1/thumbnail',
          isFavorite: false,
          favoritedAt: null,
        }}
        onOpen={vi.fn()}
        onToggleFavorite={vi.fn()}
      />,
    );

    expect(
      screen.getByRole('button', { name: 'Add to Favorites' }),
    ).toHaveTextContent('♡');
  });

  it('renders a filled heart for a favorited memory', () => {
    render(
      <MemoryCard
        memory={{
          id: 'memory-1',
          capturedAt: '2026-01-01',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1024,
          lastModifiedAt: '2026-01-01T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-1/thumbnail',
          isFavorite: true,
          favoritedAt: '2026-07-18T10:15:30Z',
        }}
        onOpen={vi.fn()}
        onToggleFavorite={vi.fn()}
      />,
    );

    expect(
      screen.getByRole('button', { name: 'Remove from Favorites' }),
    ).toHaveTextContent('♥');
  });

  it('toggles favorite state without opening the memory', async () => {
    const user = userEvent.setup();
    const onOpen = vi.fn();
    const onToggleFavorite = vi.fn();

    render(
      <MemoryCard
        memory={{
          id: 'memory-1',
          capturedAt: '2026-01-01',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1024,
          lastModifiedAt: '2026-01-01T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-1/thumbnail',
          isFavorite: false,
          favoritedAt: null,
        }}
        onOpen={onOpen}
        onToggleFavorite={onToggleFavorite}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Add to Favorites' }));

    expect(onToggleFavorite).toHaveBeenCalledWith('memory-1', true);
    expect(onOpen).not.toHaveBeenCalled();
  });
});
