package com.hmap.backend.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos para crear o editar una habitación (HU-025/HU-026). El {@code status}
 * es opcional: al crear, por defecto {@code DISPONIBLE}; al editar, si viene
 * nulo se conserva el estado actual (el cambio de estado tiene su propio
 * endpoint, HU-028).
 */
public record RoomRequest(

        @NotBlank(message = "El slug es obligatorio")
        @Size(max = 80, message = "El slug no puede superar los 80 caracteres")
        String slug,

        @JsonProperty("room_number")
        @NotBlank(message = "El número de habitación es obligatorio")
        @Size(max = 10, message = "El número de habitación no puede superar los 10 caracteres")
        String roomNumber,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String name,

        @NotBlank(message = "La descripción es obligatoria")
        String description,

        @NotNull(message = "La capacidad es obligatoria")
        @Min(value = 1, message = "La capacidad debe ser al menos 1")
        Integer capacity,

        @NotNull(message = "El área es obligatoria")
        @Min(value = 1, message = "El área debe ser al menos 1")
        Integer area,

        @JsonProperty("beds_label")
        @NotBlank(message = "La descripción de camas es obligatoria")
        @Size(max = 120, message = "La descripción de camas no puede superar los 120 caracteres")
        String bedsLabel,

        @JsonProperty("price_per_night")
        @NotNull(message = "El precio por noche es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio por noche debe ser mayor a 0")
        BigDecimal pricePerNight,

        @JsonProperty("smoking_policy")
        @NotBlank(message = "La política de fumado es obligatoria")
        @Size(max = 120, message = "La política de fumado no puede superar los 120 caracteres")
        String smokingPolicy,

        String status,

        List<String> images,
        List<String> amenities,
        List<String> bathroom,
        List<String> views
) {
}
