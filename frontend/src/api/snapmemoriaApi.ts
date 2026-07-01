import type {
    CreateMemorySourceRequest,
    FlashbackResponse,
    MemoryDetail,
    MemoryPage,
    MemorySource,
    MemoryScanJob,
    TimelineMonth,
    TimelineYear,
} from "./types";

async function request<T>(
    path: string,
    options?: RequestInit,
): Promise<T> {
    const response = await fetch(path, options);

    if (!response.ok) {
        const errorMessage = await response.text();

        throw new Error(
            errorMessage || `Request failed with status ${response.status}`,
        );
    }

    if (response.status === 204) {
        return undefined as T;
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

export function getTodayFlashbacks(): Promise<FlashbackResponse> {
    return request<FlashbackResponse>("/api/flashbacks/today");
}

export function getFlashbacksByDate(
    date: string,
): Promise<FlashbackResponse> {
    return request<FlashbackResponse>(`/api/flashbacks?date=${date}`);
}

export function getMemorySources(): Promise<MemorySource[]> {
    return request<MemorySource[]>("/api/sources");
}

export function createMemorySource(
    source: CreateMemorySourceRequest,
): Promise<MemorySource> {
    return request<MemorySource>("/api/sources", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(source),
    });
}

export function deleteMemorySource(sourceId: string): Promise<void> {
    return request<void>(`/api/sources/${sourceId}`, {
        method: "DELETE",
    });
}

export function startMemorySourceScan(
    sourceId: string,
): Promise<MemoryScanJob> {
    return request<MemoryScanJob>(`/api/sources/${sourceId}/scan`, {
        method: "POST",
    });
}

export function getMemoryScanJob(
    scanJobId: string,
): Promise<MemoryScanJob> {
    return request<MemoryScanJob>(`/api/scans/${scanJobId}`);
}