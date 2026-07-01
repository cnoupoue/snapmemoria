import { useEffect, useState } from "react";
import {
  getMemories,
  getTimelineMonths,
  getTimelineYears,
} from "./api/snapmemoriaApi";
import type { Memory, TimelineMonth, TimelineYear } from "./api/types";

const MONTH_NAMES = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

function formatFileSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }

  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function App() {
  const [years, setYears] = useState<TimelineYear[]>([]);
  const [months, setMonths] = useState<TimelineMonth[]>([]);
  const [memories, setMemories] = useState<Memory[]>([]);

  const [selectedYear, setSelectedYear] = useState<number | undefined>();
  const [selectedMonth, setSelectedMonth] = useState<number | undefined>();

  const [isLoadingYears, setIsLoadingYears] = useState(true);
  const [isLoadingMemories, setIsLoadingMemories] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadYears() {
      try {
        const data = await getTimelineYears();
        setYears(data);

        if (data.length > 0) {
          setSelectedYear(data[0].year);
        }
      } catch {
        setError("Could not load the timeline. Is the backend running?");
      } finally {
        setIsLoadingYears(false);
      }
    }

    void loadYears();
  }, []);

  useEffect(() => {
    async function loadMonths() {
      if (selectedYear === undefined) {
        setMonths([]);
        return;
      }

      try {
        const data = await getTimelineMonths(selectedYear);
        setMonths(data);
      } catch {
        setError("Could not load the months for this year.");
      }
    }

    void loadMonths();
  }, [selectedYear]);

  useEffect(() => {
    async function loadMemories() {
      setIsLoadingMemories(true);
      setError(null);

      try {
        const data = await getMemories(selectedYear, selectedMonth);
        setMemories(data.content);
      } catch {
        setError("Could not load memories.");
      } finally {
        setIsLoadingMemories(false);
      }
    }

    void loadMemories();
  }, [selectedYear, selectedMonth]);

  function selectYear(year: number) {
    setSelectedYear(year);
    setSelectedMonth(undefined);
  }

  function selectMonth(month: number) {
    setSelectedMonth(month);
  }

  const pageTitle =
      selectedYear === undefined
          ? "All Memories"
          : selectedMonth === undefined
              ? String(selectedYear)
              : `${MONTH_NAMES[selectedMonth - 1]} ${selectedYear}`;

  return (
      <main className="app-shell">
        <aside className="sidebar">
          <div className="brand">
            <span className="brand-mark">S</span>
            <div>
              <h1>SnapMemoria</h1>
              <p>Your Snapchat archive</p>
            </div>
          </div>

          <section className="sidebar-section">
            <p className="sidebar-label">Timeline</p>

            {isLoadingYears && <p className="muted-text">Loading years…</p>}

            {!isLoadingYears && years.length === 0 && (
                <p className="muted-text">
                  No indexed Memories yet. Scan a source first.
                </p>
            )}

            <div className="timeline-list">
              {years.map((item) => (
                  <button
                      className={`timeline-year ${
                          selectedYear === item.year ? "is-active" : ""
                      }`}
                      key={item.year}
                      onClick={() => selectYear(item.year)}
                      type="button"
                  >
                    <span>{item.year}</span>
                    <span>{item.memoryCount}</span>
                  </button>
              ))}
            </div>
          </section>

          {selectedYear !== undefined && months.length > 0 && (
              <section className="sidebar-section">
                <p className="sidebar-label">{selectedYear} months</p>

                <button
                    className={`month-button ${
                        selectedMonth === undefined ? "is-active" : ""
                    }`}
                    onClick={() => setSelectedMonth(undefined)}
                    type="button"
                >
                  All year
                </button>

                <div className="months-list">
                  {months.map((item) => (
                      <button
                          className={`month-button ${
                              selectedMonth === item.month ? "is-active" : ""
                          }`}
                          key={item.month}
                          onClick={() => selectMonth(item.month)}
                          type="button"
                      >
                        <span>{MONTH_NAMES[item.month - 1]}</span>
                        <span>{item.memoryCount}</span>
                      </button>
                  ))}
                </div>
              </section>
          )}
        </aside>

        <section className="content">
          <header className="content-header">
            <div>
              <p className="eyebrow">Memory archive</p>
              <h2>{pageTitle}</h2>
            </div>

            <p className="memory-count">
              {memories.length} Memories loaded
            </p>
          </header>

          {error && <div className="error-banner">{error}</div>}

          {isLoadingMemories && (
              <div className="state-message">Loading Memories…</div>
          )}

          {!isLoadingMemories && memories.length === 0 && !error && (
              <div className="state-message">
                No Memories found for this period.
              </div>
          )}

          {!isLoadingMemories && memories.length > 0 && (
              <div className="memory-grid">
                {memories.map((memory) => (
                    <article className="memory-card" key={memory.id}>
                      <div className="memory-placeholder">
                  <span className="media-icon">
                    {memory.mediaType === "VIDEO" ? "▶" : "▣"}
                  </span>

                        {memory.hasOverlay && (
                            <span className="overlay-badge">Overlay</span>
                        )}
                      </div>

                      <div className="memory-card-content">
                        <strong>{memory.capturedAt}</strong>
                        <span>
                    {memory.mediaType.toLowerCase()} ·{" "}
                          {formatFileSize(memory.fileSizeBytes)}
                  </span>
                      </div>
                    </article>
                ))}
              </div>
          )}
        </section>
      </main>
  );
}

export default App;