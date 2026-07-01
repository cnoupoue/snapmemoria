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
    thumbnailUrl: string | null;
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

export type FlashbackMemory = {
    id: string;
    capturedAt: string;
    year: number;
    yearsAgo: number;
    mediaType: "IMAGE" | "VIDEO";
    hasOverlay: boolean;
    fileSizeBytes: number;
};

export type FlashbackResponse = {
    date: string;
    memories: FlashbackMemory[];
};