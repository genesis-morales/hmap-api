package com.hmap.backend.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hmap.backend.room.entity.Room;
import com.hmap.backend.room.support.ImageUrlResolver;

import java.math.BigDecimal;
import java.util.List;

/** Habitación tal como la consume el frontend (contrato E2). */
public record RoomDTO(
        Long id,
        String slug,
        String name,
        String description,
        int capacity,
        int area,
        @JsonProperty("beds_label") String bedsLabel,
        @JsonProperty("price_per_night") BigDecimal pricePerNight,
        List<String> images,
        List<String> amenities,
        List<String> bathroom,
        List<String> views,
        @JsonProperty("smoking_policy") String smokingPolicy,
        String status
) {

    public static RoomDTO from(Room room, ImageUrlResolver imageUrlResolver) {
        return new RoomDTO(
                room.getId(),
                room.getSlug(),
                room.getName(),
                room.getDescription(),
                room.getCapacity(),
                room.getArea(),
                room.getBedsLabel(),
                room.getPricePerNight(),
                imageUrlResolver.resolve(List.copyOf(room.getImages())),
                List.copyOf(room.getAmenities()),
                List.copyOf(room.getBathroom()),
                List.copyOf(room.getViews()),
                room.getSmokingPolicy(),
                room.getStatus().name()
        );
    }
}
