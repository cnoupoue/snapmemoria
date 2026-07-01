import type {
    MemoryDetail,
    MemoryPage,
    TimelineMonth,
    TimelineYear,
} from "./types";

async function request<T>(path: string): Promise<T> {
    const response = await fetch(path);

    if (!response.ok) {
        throw new Error(`Request failed with status ${response.status}`);
    }

    return response.json() as Promise<T>;
}

export function getTimelineYears(): Promise<TimelineYear[]> {
    return request<TimelineYear[]>("/api/timeline/years");
}

export function getTimelineMonths(year: number): Promise<TimelineMonth[]> {
    return request<TimelineMonth[]>(`/api/timeline/years/${year}/months`);
}

export function getMemories(
    year?: number,
    month?: number,
    page = 0,
    size = 48,
): Promise<MemoryPage> {
    const params = new URLSearchParams({
        page: String(page),
        size: String(size),
    });

    if (year !== undefined) {
        params.set("year", String(year));
    }

    if (month !== undefined) {
        params.set("month", String(month));
    }

    return request<MemoryPage>(`/api/memories?${params.toString()}`);
}

export function getMemoryDetail(memoryId: string): Promise<MemoryDetail> {
    return request<MemoryDetail>(`/api/memories/${memoryId}`);
}