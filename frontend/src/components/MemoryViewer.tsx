import { useEffect, useState } from "react";
import type { MemoryDetail } from "../api/types";

type MemoryViewerProps = {
    memory: MemoryDetail | null;
    isLoading: boolean;
    error: string | null;
    onClose: () => void;
};

export function MemoryViewer({
                                 memory,
                                 isLoading,
                                 error,
                                 onClose,
                             }: MemoryViewerProps) {
    const [mediaError, setMediaError] = useState<string | null>(null);

    useEffect(() => {
        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") {
                onClose();
            }
        }

        window.addEventListener("keydown", handleKeyDown);

        return () => {
            window.removeEventListener("keydown", handleKeyDown);
        };
    }, [onClose]);

    useEffect(() => {
        setMediaError(null);
    }, [memory?.id]);

    const isOpen = isLoading || error !== null || memory !== null;

    if (!isOpen) {
        return null;
    }

    return (
        <div
            aria-modal="true"
            className="memory-viewer-backdrop"
            onMouseDown={onClose}
            role="dialog"
        >
            <section
                className="memory-viewer"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <button
                    aria-label="Close viewer"
                    className="memory-viewer-close"
                    onClick={onClose}
                    type="button"
                >
                    ×
                </button>

                {isLoading && (
                    <div className="memory-viewer-state">
                        Loading Memory…
                    </div>
                )}

                {!isLoading && error && (
                    <div className="memory-viewer-state memory-viewer-error">
                        {error}
                    </div>
                )}

                {!isLoading && !error && memory && (
                    <>
                        <div className="memory-viewer-media">
                            {mediaError ? (
                                <div className="memory-viewer-state memory-viewer-error">
                                    {mediaError}
                                </div>
                            ) : memory.mediaType === "IMAGE" ? (
                                <img
                                    alt={`Snapchat Memory from ${memory.capturedAt}`}
                                    className="memory-viewer-image"
                                    onError={() => {
                                        setMediaError(
                                            "This image could not be loaded. Check that the USB drive is connected and the source folder is available.",
                                        );
                                    }}
                                    src={memory.mediaUrl}
                                />
                            ) : (
                                <video
                                    autoPlay
                                    className="memory-viewer-video"
                                    controls
                                    onError={() => {
                                        setMediaError(
                                            "This video could not be loaded. Check that the USB drive is connected and the source folder is available.",
                                        );
                                    }}
                                    playsInline
                                    src={memory.mediaUrl}
                                />
                            )}

                            {!mediaError && memory.overlayUrl && (
                                <img
                                    alt=""
                                    aria-hidden="true"
                                    className="memory-viewer-overlay"
                                    src={memory.overlayUrl}
                                />
                            )}
                        </div>

                        <footer className="memory-viewer-footer">
                            <div>
                                <strong>{memory.capturedAt}</strong>
                                <span>
                  {memory.mediaType === "VIDEO" ? "Video" : "Photo"}
                                    {" · "}
                                    {(memory.fileSizeBytes / 1024 / 1024).toFixed(1)} MB
                </span>
                            </div>
                        </footer>
                    </>
                )}
            </section>
        </div>
    );
}