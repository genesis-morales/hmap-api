package com.hmap.backend.common.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.hmap.backend.exception.BadRequestException;

/**
 * Construcción validada de {@link PageRequest} para las tablas globales del
 * panel interno (HU-023/024 y HU-034).
 *
 * <p>{@code PageRequest.of} lanza {@link IllegalArgumentException} ante valores
 * inválidos, que sin handler se traduciría en un <b>500</b>. Aquí se convierte en
 * un <b>400</b> con mensaje en español, coherente con el resto del contrato.
 */
public final class PageRequests {

    /** Tope de página: evita que un {@code size} enorme degrade el listado (RNF-004). */
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /**
     * Valida los parámetros de paginación y devuelve el {@code PageRequest} ordenado.
     *
     * @throws BadRequestException si {@code page} es negativo, o {@code size} está
     *                             fuera del rango {@code 1..MAX_SIZE}
     */
    public static PageRequest of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BadRequestException("El número de página no puede ser negativo");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("El tamaño de página debe estar entre 1 y " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
