package be.cnoupoue.snapmemoria.source.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMemorySourceRequest(

        @NotBlank(message = "The source name is required.")
        @Size(max = 100, message = "The source name must not exceed 100 characters.")
        String name,

        @NotBlank(message = "The source path is required.")
        String rootPath
) {
}