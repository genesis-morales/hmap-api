package com.hmap.backend.room.entity;

import com.hmap.backend.room.enums.RoomStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Habitación del hotel. Catálogo migrado del frontend (rooms.ts) vía seed en V3.
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    /**
     * Número físico de la habitación (el de la puerta). Segunda llave natural:
     * dos habitaciones no pueden compartirlo. Es texto y no entero porque es un
     * código, no una cantidad: admite formas como {@code 101-A} sin migrar.
     */
    @Column(name = "room_number", nullable = false, unique = true, length = 10)
    private String roomNumber;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int area;

    @Column(name = "beds_label", nullable = false, length = 120)
    private String bedsLabel;

    @Column(name = "price_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "smoking_policy", nullable = false, length = 120)
    private String smokingPolicy;

    @Enumerated(EnumType.STRING)
    // VARCHAR (no ENUM nativo de MySQL) para consistencia con Reservation.status
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @ElementCollection
    @CollectionTable(name = "room_images", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false, length = 500)
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "position")
    @Column(name = "label", nullable = false, length = 150)
    @Builder.Default
    private List<String> amenities = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "room_bathroom", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "position")
    @Column(name = "label", nullable = false, length = 150)
    @Builder.Default
    private List<String> bathroom = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "room_views", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "position")
    @Column(name = "label", nullable = false, length = 150)
    @Builder.Default
    private List<String> views = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = RoomStatus.DISPONIBLE;
        }
    }
}
