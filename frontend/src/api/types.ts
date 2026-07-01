export type TimelineYear = {
    year: number;
    memoryCount: number;
};

export type TimelineMonth = {
    month: number;
    memoryCount: number;
};

export type Memory = {
    id: string;
    capturedAt: string;
    mediaType: "IMAGE" | "VIDEO";
    hasOverlay: boolean;
    fileSizeBytes: number;
    lastModifiedAt: string;
};

export type MemoryPage = {
    content: Memory[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};

export type MemoryDetail = {
    id: string;
    capturedAt: string;
    mediaType: "IMAGE" | "VIDEO";
    hasOverlay: boolean;
    fileSizeBytes: number;
    lastModifiedAt: string;
    mediaUrl: string;
    overlayUrl: string | null;
};