package com.hmap.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de paginación en {@code snake_case} para las tablas globales del
 * panel interno (HU-023/024 y HU-034). Desacopla el contrato del frontend de la
 * representación de Spring Data {@link Page}.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages
) {

    /** Construye la respuesta mapeando cada elemento de la página al DTO destino. */
    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
