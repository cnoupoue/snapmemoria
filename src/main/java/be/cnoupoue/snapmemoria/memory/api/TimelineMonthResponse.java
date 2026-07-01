package be.cnoupoue.snapmemoria.memory.api;

public record TimelineMonthResponse(
        int month,
        long memoryCount
) {
}