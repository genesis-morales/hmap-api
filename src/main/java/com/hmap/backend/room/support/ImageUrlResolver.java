package com.hmap.backend.room.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resuelve las rutas relativas de imagen guardadas en BD
 * (ej. {@code hmap/rooms/<slug>/<nombre>}) contra la base pública del CDN
 * ({@code app.images.base-url}). La BD no conoce la cuenta de Cloudinary.
 */
@Component
public class ImageUrlResolver {

    private final String baseUrl;

    public ImageUrlResolver(@Value("${app.images.base-url}") String baseUrl) {
        // Sin barra final para concatenar de forma predecible
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /** Antepone la base del CDN; respeta URLs ya absolutas y bases vacías. */
    public String resolve(String path) {
        if (baseUrl.isEmpty() || path == null || path.startsWith("http")) {
            return path;
        }
        return baseUrl + "/" + path;
    }

    public List<String> resolve(List<String> paths) {
        return paths.stream().map(this::resolve).toList();
    }
}
