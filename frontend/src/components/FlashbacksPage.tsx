import { useEffect, useState } from "react";
import {
    getFlashbacksByDate,
    getTodayFlashbacks,
} from "../api/snapmemoriaApi";
import type { FlashbackMemory, FlashbackResponse } from "../api/types";

type FlashbacksPageProps = {
    onOpenMemory: (memoryId: string) => void;
};

function formatFileSize(bytes: number): string {
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

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

export function FlashbacksPage({
                                   onOpenMemory,
                               }: FlashbacksPageProps) {
    const [flashbacks, setFlashbacks] = useState<FlashbackResponse | null>(
        null,
    );
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
            setError(
                "Could not load flashbacks. Check that the backend is running.",
            );
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
            setError("Could not load flashbacks for this date.");
        } finally {
            setIsLoading(false);
        }
    }

    useEffect(() => {
        void loadTodayFlashbacks();
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
                    {flashbacks.memories.length} Memories from previous years on{" "}
                    <strong>{flashbacks.date}</strong>
                </p>
            )}

            {error && <div className="error-banner">{error}</div>}

            {isLoading && (
                <div className="state-message">Loading flashbacks…</div>
            )}

            {!isLoading && !error && flashbacks?.memories.length === 0 && (
                <div className="state-message">
                    No flashbacks found for this date.
                </div>
            )}

            {!isLoading &&
                !error &&
                memoriesByYear.map(([year, memories]) => (
                    <section className="flashback-year-section" key={year}>
                        <header className="flashback-year-header">
                            <h3>{year}</h3>
                            <span>
                {memories[0].yearsAgo}{" "}
                                {memories[0].yearsAgo === 1 ? "year ago" : "years ago"}
              </span>
                        </header>

                        <div className="memory-grid">
                            {memories.map((memory) => (
                                <button
                                    aria-label={`Open flashback from ${memory.capturedAt}`}
                                    className="memory-card"
                                    key={memory.id}
                                    onClick={() => onOpenMemory(memory.id)}
                                    type="button"
                                >
                                    <div className="memory-preview">
                                        <img
                                            alt={`Snapchat Memory from ${memory.capturedAt}`}
                                            className="memory-thumbnail"
                                            loading="lazy"
                                            onError={(event) => {
                                                event.currentTarget.style.display = "none";

                                                const fallback =
                                                    event.currentTarget.nextElementSibling;

                                                if (fallback instanceof HTMLElement) {
                                                    fallback.hidden = false;
                                                }
                                            }}
                                            src={`/api/memories/${memory.id}/thumbnail`}
                                        />

                                        <div className="memory-video-placeholder" hidden>
                      <span className="media-icon">
                        {memory.mediaType === "VIDEO" ? "▶" : "▣"}
                      </span>

                                            <span>
                        {memory.mediaType === "VIDEO"
                            ? "Video preview unavailable"
                            : "Image preview unavailable"}
                      </span>
                                        </div>

                                        {memory.hasOverlay && (
                                            <span className="overlay-badge">Overlay</span>
                                        )}

                                        {memory.mediaType === "VIDEO" && (
                                            <span className="video-badge">Video</span>
                                        )}
                                    </div>

                                    <div className="memory-card-content">
                                        <strong>{memory.capturedAt}</strong>

                                        <span>
                      {memory.mediaType.toLowerCase()} ·{" "}
                                            {formatFileSize(memory.fileSizeBytes)}
                    </span>
                                    </div>
                                </button>
                            ))}
                        </div>
                    </section>
                ))}
        </section>
    );
}