import type { FlashbackMemory, Memory } from '../api/types';

type MemoryCardProps = {
  memory: Memory | FlashbackMemory;
  thumbnailUrl?: string | null;
  onOpen: (memoryId: string) => void;
};

function getMediaLabel(memory: Memory | FlashbackMemory): string {
  return memory.mediaType === 'VIDEO' ? 'Video' : 'Photo';
}

export function MemoryCard({ memory, thumbnailUrl, onOpen }: MemoryCardProps) {
  const resolvedThumbnailUrl =
    thumbnailUrl ?? `/api/memories/${memory.id}/thumbnail`;
  const isVideo = memory.mediaType === 'VIDEO';

  return (
    <button
      aria-label={`Open Memory from ${memory.capturedAt}`}
      className="memory-card"
      onClick={() => onOpen(memory.id)}
      type="button"
    >
      <div className="memory-preview">
        <img
          alt={`Memory from ${memory.capturedAt}`}
          className="memory-thumbnail"
          loading="lazy"
          onError={(event) => {
            event.currentTarget.style.display = 'none';

            const fallback = event.currentTarget.nextElementSibling;

            if (fallback instanceof HTMLElement) {
              fallback.hidden = false;
            }
          }}
          src={resolvedThumbnailUrl ?? ''}
        />

        <div className="memory-media-fallback" hidden>
          <span aria-hidden="true" className="media-symbol">
            {isVideo ? '▶' : '▣'}
          </span>
          <strong>
            {isVideo ? 'Video preview unavailable' : 'Preview unavailable'}
          </strong>
          {isVideo && <span>Open video</span>}
        </div>

        <div className="memory-card-overlays" aria-hidden="true">
          {isVideo && <span className="video-indicator">Play</span>}
          {memory.hasOverlay && <span className="overlay-indicator" />}
        </div>
      </div>

      <div className="memory-card-content">
        <strong>{memory.capturedAt}</strong>
        <span>{getMediaLabel(memory)}</span>
      </div>
    </button>
  );
}
