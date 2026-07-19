import { useCallback, useEffect, useRef, useState } from 'react';
import { MemoryViewer } from './components/MemoryViewer';
import {
  addMemoryFavorite,
  getFavoriteMemories,
  getMemories,
  getMemoryDetail,
  getMemorySources,
  getTimelineMonths,
  getTimelineYears,
  removeMemoryFavorite,
} from './api/memoriaVaultApi';
import type {
  Memory,
  MemoryDetail,
  MemorySource,
  TimelineMonth,
  TimelineYear,
} from './api/types';
import { FlashbacksPage } from './components/FlashbacksPage';
import { MemoryCard } from './components/MemoryCard';
import { OnboardingPage } from './components/OnboardingPage';
import { SettingsPage } from './components/SettingsPage';

const PAGE_SIZE = 48;
const APP_TITLE = 'Memoria Vault';

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

function App() {
  const [years, setYears] = useState<TimelineYear[]>([]);
  const [months, setMonths] = useState<TimelineMonth[]>([]);
  const [memories, setMemories] = useState<Memory[]>([]);

  const [selectedYear, setSelectedYear] = useState<number | undefined>();
  const [selectedMonth, setSelectedMonth] = useState<number | undefined>();

  const [currentPage, setCurrentPage] = useState(0);
  const [totalMemories, setTotalMemories] = useState(0);
  const [hasMoreMemories, setHasMoreMemories] = useState(false);

  const [isLoadingYears, setIsLoadingYears] = useState(true);
  const [isLoadingMemories, setIsLoadingMemories] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [sources, setSources] = useState<MemorySource[]>([]);
  const [isLoadingSources, setIsLoadingSources] = useState(true);
  const [sourceLoadError, setSourceLoadError] = useState<string | null>(null);

  const [selectedMemory, setSelectedMemory] = useState<MemoryDetail | null>(
    null,
  );
  const [viewerMemoryIds, setViewerMemoryIds] = useState<string[]>([]);
  const [isLoadingSelectedMemory, setIsLoadingSelectedMemory] = useState(false);
  const [selectedMemoryError, setSelectedMemoryError] = useState<string | null>(
    null,
  );

  const [activeView, setActiveView] = useState<
    'archive' | 'favorites' | 'flashbacks' | 'settings'
  >('archive');

  const [archiveRefreshVersion, setArchiveRefreshVersion] = useState(0);
  const [shouldFocusSourceForm, setShouldFocusSourceForm] = useState(false);
  const [shouldOpenFolderPicker, setShouldOpenFolderPicker] = useState(false);

  /*
   * Prevents an older response from replacing newer results
   * when the user changes year or month quickly.
   */
  const memoryRequestVersion = useRef(0);

  useEffect(() => {
    document.title = APP_TITLE;
  }, []);

  const loadSources = useCallback(async () => {
    setIsLoadingSources(true);
    setSourceLoadError(null);

    try {
      const data = await getMemorySources();
      setSources(data);
    } catch {
      setSourceLoadError(
        'Could not load setup status. Check that the backend is running.',
      );
    } finally {
      setIsLoadingSources(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    void (async () => {
      try {
        const data = await getMemorySources();

        if (isMounted) {
          setSources(data);
        }
      } catch {
        if (isMounted) {
          setSourceLoadError(
            'Could not load setup status. Check that the backend is running.',
          );
        }
      } finally {
        if (isMounted) {
          setIsLoadingSources(false);
        }
      }
    })();

    return () => {
      isMounted = false;
    };
  }, []);

  const hasConfiguredSources = sources.length > 0;

  useEffect(() => {
    async function loadYears() {
      if (isLoadingSources || !hasConfiguredSources) {
        setYears([]);
        setSelectedYear(undefined);
        setMonths([]);
        return;
      }

      setIsLoadingYears(true);

      try {
        const data = await getTimelineYears();

        setYears(data);

        setSelectedYear((currentYear) => {
          const currentYearStillExists = data.some(
            (item) => item.year === currentYear,
          );

          if (currentYear !== undefined && currentYearStillExists) {
            return currentYear;
          }

          return data[0]?.year;
        });
      } catch {
        setError('Could not load the timeline. Is the backend running?');
      } finally {
        setIsLoadingYears(false);
      }
    }

    void loadYears();
  }, [archiveRefreshVersion, hasConfiguredSources, isLoadingSources]);

  useEffect(() => {
    async function loadMonths() {
      if (!hasConfiguredSources || selectedYear === undefined) {
        setMonths([]);
        return;
      }

      try {
        const data = await getTimelineMonths(selectedYear);
        setMonths(data);
      } catch {
        setError('Could not load the months for this year.');
      }
    }

    void loadMonths();
  }, [selectedYear, archiveRefreshVersion, hasConfiguredSources]);

  useEffect(() => {
    async function loadFirstMemoryPage() {
      if (isLoadingSources || !hasConfiguredSources) {
        setIsLoadingMemories(false);
        setMemories([]);
        setCurrentPage(0);
        setTotalMemories(0);
        setHasMoreMemories(false);
        return;
      }

      const requestVersion = ++memoryRequestVersion.current;

      setIsLoadingMemories(true);
      setError(null);
      setMemories([]);
      setCurrentPage(0);
      setTotalMemories(0);
      setHasMoreMemories(false);

      try {
        const data =
          activeView === 'favorites'
            ? await getFavoriteMemories(0, PAGE_SIZE)
            : await getMemories(selectedYear, selectedMonth, 0, PAGE_SIZE);

        if (requestVersion !== memoryRequestVersion.current) {
          return;
        }

        setMemories(data.content);
        setCurrentPage(data.page);
        setTotalMemories(data.totalElements);
        setHasMoreMemories(data.page + 1 < data.totalPages);
      } catch {
        if (requestVersion === memoryRequestVersion.current) {
          setError('Could not load Memories.');
        }
      } finally {
        if (requestVersion === memoryRequestVersion.current) {
          setIsLoadingMemories(false);
        }
      }
    }

    void loadFirstMemoryPage();
  }, [
    selectedYear,
    selectedMonth,
    activeView,
    archiveRefreshVersion,
    hasConfiguredSources,
    isLoadingSources,
  ]);

  async function loadMoreMemories() {
    if (isLoadingMore || !hasMoreMemories) {
      return;
    }

    const nextPage = currentPage + 1;

    setIsLoadingMore(true);
    setError(null);

    try {
      const data =
        activeView === 'favorites'
          ? await getFavoriteMemories(nextPage, PAGE_SIZE)
          : await getMemories(selectedYear, selectedMonth, nextPage, PAGE_SIZE);

      setMemories((currentMemories) => [...currentMemories, ...data.content]);

      setCurrentPage(data.page);
      setTotalMemories(data.totalElements);
      setHasMoreMemories(data.page + 1 < data.totalPages);
    } catch {
      setError('Could not load more Memories.');
    } finally {
      setIsLoadingMore(false);
    }
  }

  function selectYear(year: number) {
    setActiveView('archive');
    setSelectedYear(year);
    setSelectedMonth(undefined);
  }

  async function toggleFavorite(memoryId: string, nextFavorite: boolean) {
    const previousMemories = memories;
    const previousSelectedMemory = selectedMemory;
    const previousViewerMemoryIds = viewerMemoryIds;
    const favoritedAt = nextFavorite ? new Date().toISOString() : null;
    const currentViewerIndex = viewerMemoryIds.indexOf(memoryId);
    const updatedViewerMemoryIds = viewerMemoryIds.filter(
      (id) => id !== memoryId,
    );
    const fallbackViewerMemoryId =
      currentViewerIndex === -1
        ? undefined
        : (viewerMemoryIds[currentViewerIndex + 1] ??
          viewerMemoryIds[currentViewerIndex - 1]);

    setError(null);
    setMemories((currentMemories) =>
      currentMemories.map((memory) =>
        memory.id === memoryId
          ? {
              ...memory,
              isFavorite: nextFavorite,
              favoritedAt,
            }
          : memory,
      ),
    );
    setSelectedMemory((currentMemory) =>
      currentMemory?.id === memoryId
        ? {
            ...currentMemory,
            isFavorite: nextFavorite,
            favoritedAt,
          }
        : currentMemory,
    );

    try {
      const updatedMemory = nextFavorite
        ? await addMemoryFavorite(memoryId)
        : await removeMemoryFavorite(memoryId);

      setMemories((currentMemories) => {
        const updatedMemories = currentMemories.map((memory) =>
          memory.id === memoryId
            ? {
                ...memory,
                isFavorite: updatedMemory.isFavorite,
                favoritedAt: updatedMemory.favoritedAt,
              }
            : memory,
        );

        if (activeView === 'favorites' && !updatedMemory.isFavorite) {
          return updatedMemories.filter((memory) => memory.id !== memoryId);
        }

        return updatedMemories;
      });
      setSelectedMemory((currentMemory) =>
        currentMemory?.id === memoryId
          ? {
              ...currentMemory,
              isFavorite: updatedMemory.isFavorite,
              favoritedAt: updatedMemory.favoritedAt,
            }
          : currentMemory,
      );

      if (activeView === 'favorites' && !updatedMemory.isFavorite) {
        setTotalMemories((currentTotal) => Math.max(0, currentTotal - 1));
        setViewerMemoryIds(updatedViewerMemoryIds);

        if (selectedMemory?.id === memoryId) {
          if (fallbackViewerMemoryId) {
            void openMemory(fallbackViewerMemoryId, updatedViewerMemoryIds);
          } else {
            closeMemoryViewer();
          }
        }
      }
    } catch {
      setMemories(previousMemories);
      setSelectedMemory(previousSelectedMemory);
      setViewerMemoryIds(previousViewerMemoryIds);
      setError('Could not update Favorites. Try again.');
    }
  }

  function selectMonth(month: number) {
    setActiveView('archive');
    setSelectedMonth(month);
  }

  async function openMemory(
    memoryId: string,
    contextMemoryIds = memories.map((memory) => memory.id),
  ) {
    setSelectedMemory(null);
    setSelectedMemoryError(null);
    setIsLoadingSelectedMemory(true);
    setViewerMemoryIds(contextMemoryIds);

    try {
      const detail = await getMemoryDetail(memoryId);
      setSelectedMemory(detail);
    } catch {
      setSelectedMemoryError(
        'Could not open this memory. The source drive may be unavailable.',
      );
    } finally {
      setIsLoadingSelectedMemory(false);
    }
  }

  function closeMemoryViewer() {
    setSelectedMemory(null);
    setViewerMemoryIds([]);
    setSelectedMemoryError(null);
    setIsLoadingSelectedMemory(false);
  }

  function openAdjacentMemory(offset: -1 | 1) {
    if (!selectedMemory) {
      return;
    }

    const currentIndex = viewerMemoryIds.indexOf(selectedMemory.id);
    const adjacentMemoryId = viewerMemoryIds[currentIndex + offset];

    if (!adjacentMemoryId) {
      return;
    }

    void openMemory(adjacentMemoryId, viewerMemoryIds);
  }

  function refreshArchiveData() {
    setArchiveRefreshVersion((currentVersion) => currentVersion + 1);
    void loadSources();
  }

  function openSourceCreationFlow() {
    setActiveView('settings');
    setShouldFocusSourceForm(true);
    setShouldOpenFolderPicker(true);
  }

  function handleSourceCreated(source: MemorySource) {
    setSources((currentSources) => {
      if (currentSources.some((item) => item.id === source.id)) {
        return currentSources.map((item) =>
          item.id === source.id ? source : item,
        );
      }

      return [...currentSources, source];
    });
    setShouldFocusSourceForm(false);
    setShouldOpenFolderPicker(false);
  }

  function handleSourceDeleted(sourceId: string) {
    setSources((currentSources) =>
      currentSources.filter((source) => source.id !== sourceId),
    );
  }

  const pageTitle =
    selectedYear === undefined
      ? 'All Memories'
      : selectedMonth === undefined
        ? String(selectedYear)
        : `${MONTH_NAMES[selectedMonth - 1]} ${selectedYear}`;
  const currentMemoryIds = memories.map((memory) => memory.id);
  const selectedMemoryIndex = selectedMemory
    ? viewerMemoryIds.indexOf(selectedMemory.id)
    : -1;
  const hasPreviousMemory = selectedMemoryIndex > 0;
  const hasNextMemory =
    selectedMemoryIndex >= 0 &&
    selectedMemoryIndex < viewerMemoryIds.length - 1;

  return (
    <main className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand">
          <span className="brand-mark">M</span>

          <div>
            <h1>Memoria Vault</h1>
            <p>Your local archive</p>
          </div>
        </div>

        <div className="sidebar-scroll">
          <section className="sidebar-section sidebar-primary-navigation">
            <button
              className={`sidebar-main-link ${
                activeView === 'archive' ? 'is-active' : ''
              }`}
              onClick={() => {
                setActiveView('archive');
                setShouldFocusSourceForm(false);
                setShouldOpenFolderPicker(false);
              }}
              type="button"
            >
              Archive
            </button>

            <button
              className={`sidebar-main-link ${
                activeView === 'favorites' ? 'is-active' : ''
              }`}
              onClick={() => {
                setActiveView('favorites');
                setShouldFocusSourceForm(false);
                setShouldOpenFolderPicker(false);
              }}
              type="button"
            >
              Favorites
            </button>

            <button
              className={`sidebar-main-link ${
                activeView === 'flashbacks' ? 'is-active' : ''
              }`}
              onClick={() => {
                setActiveView('flashbacks');
                setShouldFocusSourceForm(false);
                setShouldOpenFolderPicker(false);
              }}
              type="button"
            >
              Flashbacks
            </button>
            <button
              className={`sidebar-main-link ${
                activeView === 'settings' ? 'is-active' : ''
              }`}
              onClick={() => {
                setActiveView('settings');
                setShouldFocusSourceForm(false);
                setShouldOpenFolderPicker(false);
              }}
              type="button"
            >
              Settings
            </button>
          </section>

          <section className="sidebar-section">
            <p className="sidebar-label">Timeline</p>

            {isLoadingSources && <p className="muted-text">Checking setup…</p>}

            {!isLoadingSources && sourceLoadError && (
              <p className="muted-text">Setup status unavailable.</p>
            )}

            {!isLoadingSources && hasConfiguredSources && isLoadingYears && (
              <p className="muted-text">Loading years…</p>
            )}

            {!isLoadingSources && !hasConfiguredSources && !sourceLoadError && (
              <p className="muted-text">
                Add an exported archive folder to build your private local
                archive.
              </p>
            )}

            {!isLoadingSources &&
              hasConfiguredSources &&
              !isLoadingYears &&
              years.length === 0 && (
                <p className="muted-text">
                  No indexed Memories yet. Scan a source first.
                </p>
              )}

            <div className="timeline-list">
              {years.map((item) => (
                <button
                  className={`timeline-year ${
                    selectedYear === item.year ? 'is-active' : ''
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
                  selectedMonth === undefined ? 'is-active' : ''
                }`}
                onClick={() => setSelectedMonth(undefined)}
                type="button"
              >
                <span>All year</span>
              </button>

              <div className="months-list">
                {months.map((item) => (
                  <button
                    className={`month-button ${
                      selectedMonth === item.month ? 'is-active' : ''
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
        </div>

        <div className="sidebar-footer">
          <strong>Local-first</strong>
          <span>Your memories stay on this device.</span>
        </div>
      </aside>

      <div className="app-content">
        {activeView === 'archive' || activeView === 'favorites' ? (
          isLoadingSources ? (
            <section className="content">
              <div className="state-message">Checking setup…</div>
            </section>
          ) : sourceLoadError ? (
            <section className="content">
              <header className="content-header">
                <div>
                  <p className="eyebrow">Setup</p>
                  <h2>Memoria Vault setup</h2>
                </div>
              </header>
              <div className="error-banner">{sourceLoadError}</div>
            </section>
          ) : !hasConfiguredSources ? (
            <OnboardingPage onAddSource={openSourceCreationFlow} />
          ) : (
            <section className="content">
              <header className="content-header">
                <div>
                  <p className="eyebrow">
                    {activeView === 'favorites'
                      ? 'Favorites collection'
                      : 'Memory archive'}
                  </p>
                  <h2>
                    {activeView === 'favorites' ? 'Favorites' : pageTitle}
                  </h2>
                </div>

                <p className="memory-count">
                  {totalMemories.toLocaleString()}{' '}
                  {activeView === 'favorites' ? 'favorites' : 'memories'}
                  {hasMoreMemories
                    ? ` · ${memories.length.toLocaleString()} shown`
                    : ''}
                </p>
              </header>

              {error && <div className="error-banner">{error}</div>}

              {isLoadingMemories && (
                <div className="state-message">Loading Memories…</div>
              )}

              {!isLoadingMemories && memories.length === 0 && !error && (
                <div className="state-message archive-empty-state">
                  {activeView === 'favorites' ? (
                    <>
                      <strong>No favorites yet.</strong>
                      <span>
                        Mark memories with the heart icon to find them here
                        later.
                      </span>
                    </>
                  ) : (
                    <>
                      <strong>No memories here yet.</strong>
                      <span>
                        Scan your configured source to build this private local
                        archive.
                      </span>
                      <button
                        className="primary-button"
                        onClick={openSourceCreationFlow}
                        type="button"
                      >
                        Start scanning
                      </button>
                    </>
                  )}
                </div>
              )}

              {!isLoadingMemories && memories.length > 0 && (
                <>
                  <div className="memory-grid">
                    {memories.map((memory) => (
                      <MemoryCard
                        key={memory.id}
                        memory={memory}
                        onOpen={(memoryId) =>
                          void openMemory(memoryId, currentMemoryIds)
                        }
                        onToggleFavorite={(memoryId, nextFavorite) =>
                          void toggleFavorite(memoryId, nextFavorite)
                        }
                        thumbnailUrl={memory.thumbnailUrl}
                      />
                    ))}
                  </div>

                  {hasMoreMemories && (
                    <div className="load-more-container">
                      <button
                        className="load-more-button"
                        disabled={isLoadingMore}
                        onClick={() => void loadMoreMemories()}
                        type="button"
                      >
                        {isLoadingMore ? 'Loading more memories…' : 'Load more'}
                      </button>
                    </div>
                  )}

                  {!hasMoreMemories && memories.length > 0 && (
                    <p className="end-of-list">
                      {activeView === 'favorites'
                        ? 'You have reached the end of Favorites.'
                        : 'You have reached the end of this period.'}
                    </p>
                  )}
                </>
              )}
            </section>
          )
        ) : activeView === 'flashbacks' ? (
          <FlashbacksPage
            onOpenMemory={(memoryId, contextMemoryIds) =>
              void openMemory(memoryId, contextMemoryIds)
            }
          />
        ) : (
          <SettingsPage
            autoOpenFolderPicker={shouldOpenFolderPicker}
            autoFocusSourceForm={shouldFocusSourceForm}
            onFolderPickerAutoOpened={() => setShouldOpenFolderPicker(false)}
            onSourceCreated={handleSourceCreated}
            onSourceDeleted={handleSourceDeleted}
            onSourceScanned={refreshArchiveData}
          />
        )}

        <footer className="site-footer">
          <p>All rights reserved Cameron Noupoue.</p>

          <nav aria-label="Project links" className="site-footer-links">
            <a
              href="https://github.com/cnoupoue/memoriavault"
              rel="noreferrer"
              target="_blank"
            >
              Open source on GitHub, contributions welcome
            </a>

            <a
              href="https://www.linkedin.com/in/cnoupoue"
              rel="noreferrer"
              target="_blank"
            >
              LinkedIn
            </a>
          </nav>
        </footer>
      </div>

      <MemoryViewer
        error={selectedMemoryError}
        hasNext={hasNextMemory}
        hasPrevious={hasPreviousMemory}
        isLoading={isLoadingSelectedMemory}
        memory={selectedMemory}
        onClose={closeMemoryViewer}
        onNext={() => openAdjacentMemory(1)}
        onPrevious={() => openAdjacentMemory(-1)}
        onToggleFavorite={(memoryId, nextFavorite) =>
          void toggleFavorite(memoryId, nextFavorite)
        }
      />
    </main>
  );
}

export default App;
