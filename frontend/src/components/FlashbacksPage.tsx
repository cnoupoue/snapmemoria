import { useEffect, useState } from 'react';
import {
  getFlashbacksByDate,
  getTodayFlashbacks,
} from '../api/memoriaVaultApi';
import type { FlashbackMemory, FlashbackResponse } from '../api/types';
import { MemoryCard } from './MemoryCard';

type FlashbacksPageProps = {
  onOpenMemory: (memoryId: string, contextMemoryIds: string[]) => void;
};

function formatDateForInput(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function groupByYear(
  memories: FlashbackMemory[],
): Map<number, FlashbackMemory[]> {
  const memoriesByYear = new Map<number, FlashbackMemory[]>();

  for (const memory of memories) {
    const currentYearMemories = memoriesByYear.get(memory.year) ?? [];

    currentYearMemories.push(memory);
    memoriesByYear.set(memory.year, currentYearMemories);
  }

  return memoriesByYear;
}

export function FlashbacksPage({ onOpenMemory }: FlashbacksPageProps) {
  const [flashbacks, setFlashbacks] = useState<FlashbackResponse | null>(null);
  const [selectedDate, setSelectedDate] = useState(
    formatDateForInput(new Date()),
  );
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function loadTodayFlashbacks() {
    setIsLoading(true);
    setError(null);

    try {
      const data = await getTodayFlashbacks();

      setFlashbacks(data);
      setSelectedDate(data.date);
    } catch {
      setError('Could not load flashbacks. Check that the backend is running.');
    } finally {
      setIsLoading(false);
    }
  }

  async function loadFlashbacksForDate(date: string) {
    setIsLoading(true);
    setError(null);

    try {
      const data = await getFlashbacksByDate(date);
      setFlashbacks(data);
    } catch {
      setError('Could not load flashbacks for this date.');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    let isCancelled = false;

    getTodayFlashbacks()
      .then((data) => {
        if (isCancelled) {
          return;
        }

        setFlashbacks(data);
        setSelectedDate(data.date);
      })
      .catch(() => {
        if (isCancelled) {
          return;
        }

        setError(
          'Could not load flashbacks. Check that the backend is running.',
        );
      })
      .finally(() => {
        if (!isCancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, []);

  function handleDateChange(date: string) {
    setSelectedDate(date);
    void loadFlashbacksForDate(date);
  }

  const memoriesByYear = flashbacks
    ? [...groupByYear(flashbacks.memories).entries()].sort(
        ([firstYear], [secondYear]) => secondYear - firstYear,
      )
    : [];
  const memoryIdsInCurrentContext = memoriesByYear.flatMap(([, memories]) =>
    memories.map((memory) => memory.id),
  );

  return (
    <section className="content">
      <header className="content-header flashbacks-header">
        <div>
          <p className="eyebrow">On this day</p>
          <h2>Flashbacks</h2>
        </div>

        <div className="flashback-date-controls">
          <input
            aria-label="Choose a flashback date"
            className="flashback-date-input"
            max={formatDateForInput(new Date())}
            onChange={(event) => handleDateChange(event.target.value)}
            type="date"
            value={selectedDate}
          />

          <button
            className="flashback-today-button"
            onClick={() => void loadTodayFlashbacks()}
            type="button"
          >
            Today
          </button>
        </div>
      </header>

      {flashbacks && (
        <p className="flashbacks-summary">
          {flashbacks.memories.length.toLocaleString()} memories from previous
          years on <strong>{flashbacks.date}</strong>
        </p>
      )}

      {error && <div className="error-banner">{error}</div>}

      {isLoading && <div className="state-message">Loading flashbacks…</div>}

      {!isLoading && !error && flashbacks?.memories.length === 0 && (
        <div className="state-message empty-state">
          <strong>No flashbacks for this day.</strong>
          <span>
            Try another date, or come back after your archive has more memories.
          </span>
        </div>
      )}

      {!isLoading &&
        !error &&
        memoriesByYear.map(([year, memories]) => (
          <section className="flashback-year-section" key={year}>
            <header className="flashback-year-header">
              <h3>{year}</h3>
              <span>
                {memories[0].yearsAgo}{' '}
                {memories[0].yearsAgo === 1 ? 'year ago' : 'years ago'}
              </span>
            </header>

            <div className="memory-grid">
              {memories.map((memory) => (
                <MemoryCard
                  key={memory.id}
                  memory={memory}
                  onOpen={(memoryId) =>
                    onOpenMemory(memoryId, memoryIdsInCurrentContext)
                  }
                />
              ))}
            </div>
          </section>
        ))}
    </section>
  );
}
