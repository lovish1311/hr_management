package com.example.hr_management_backend.features.banners.service;

import com.example.hr_management_backend.core.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

@Service
@Slf4j
public class BannerImageStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private final Path uploadLocation;

    public BannerImageStorageService(@Value("${app.banner.upload-dir:uploads/banners}") String uploadDir) {
        this.uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.uploadLocation);
            log.info("Banner image storage directory initialized at: {}", this.uploadLocation);
        } catch (IOException e) {
            log.error("Could not initialize banner image storage directory at: {}", this.uploadLocation, e);
            throw new RuntimeException("Could not initialize banner storage directory", e);
        }
    }

    @Autowired(required = false)
    private com.cloudinary.Cloudinary cloudinary;

    /**
     * Stores an uploaded banner image.
     * If Cloudinary is configured, uploads directly to Cloudinary CDN with automatic WebP optimization.
     * Otherwise, falls back to local disk storage with magic byte inspection.
     * Returns either the secure Cloudinary CDN HTTPS URL or the local relative filename.
     */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty image file.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image file size exceeds maximum limit of 5MB.");
        }

        String canonicalExt = detectExtensionFromMagicBytes(file);

        // 1. Cloudinary Enterprise CDN Fast-Path
        if (cloudinary != null) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> uploadResult = cloudinary.uploader().upload(
                        file.getBytes(),
                        com.cloudinary.utils.ObjectUtils.asMap(
                                "folder", "hr_management/banners",
                                "resource_type", "image",
                                "format", "webp",
                                "quality", "auto:good"
                        )
                );
                String secureUrl = (String) uploadResult.get("secure_url");
                if (secureUrl != null && !secureUrl.isBlank()) {
                    log.info("[BannerImageStorageService] Uploaded image to Cloudinary CDN: url={}, size={} bytes", secureUrl, file.getSize());
                    return secureUrl;
                }
            } catch (Exception e) {
                log.error("[BannerImageStorageService] Cloudinary upload failed, falling back to local storage: {}", e.getMessage());
            }
        }

        // 2. Local Disk Fallback
        String extension = canonicalExt;
        String originalFilename = file.getOriginalFilename();
        String baseName = "image";
        if (originalFilename != null && originalFilename.contains(".")) {
            int dotIndex = originalFilename.lastIndexOf('.');
            String candidateExt = originalFilename.substring(dotIndex).toLowerCase();
            if (".jpeg".equals(candidateExt) && ".jpg".equals(canonicalExt)) {
                extension = ".jpeg";
            }
            baseName = originalFilename.substring(0, dotIndex).replaceAll("[^a-zA-Z0-9_-]", "_");
            if (baseName.length() > 30) {
                baseName = baseName.substring(0, 30);
            }
        }

        String uniqueFilename = UUID.randomUUID().toString() + "_" + baseName + extension;
        Path targetPath = this.uploadLocation.resolve(uniqueFilename).normalize();

        // Anti-path traversal guard
        if (!targetPath.startsWith(this.uploadLocation)) {
            throw new IllegalArgumentException("Invalid file path sequence.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Banner image saved successfully to local disk: filename={}, size={} bytes", uniqueFilename, file.getSize());
            return uniqueFilename;
        } catch (IOException e) {
            log.error("Failed to store banner image locally: filename={}", uniqueFilename, e);
            throw new RuntimeException("Failed to store image file.", e);
        }
    }

    /**
     * Loads image resource for high-speed serving.
     */
    public Resource loadImageAsResource(String filename) {
        if (filename == null || !filename.matches("^[a-zA-Z0-9_\\-\\.]+$") || filename.contains("..")) {
            throw new IllegalArgumentException("Invalid file path sequence.");
        }
        try {
            Path filePath = this.uploadLocation.resolve(filename).normalize();
            if (!filePath.startsWith(this.uploadLocation)) {
                throw new IllegalArgumentException("Invalid file path sequence.");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Banner image not found: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Banner image not found: " + filename);
        }
    }

    /**
     * Detects MIME content type based on file extension.
     */
    public String determineContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    /**
     * Magic byte inspection ensuring binary file is genuine JPEG, PNG, GIF, or WebP.
     * Returns canonical file extension corresponding to detected file header signature.
     */
    private String detectExtensionFromMagicBytes(MultipartFile file) {
        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 4) {
                throw new IllegalArgumentException("Uploaded file is too small or corrupt.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to inspect file headers.", e);
        }

        // JPEG: FF D8 FF
        boolean isJpeg = (header[0] == (byte) 0xFF) && (header[1] == (byte) 0xD8) && (header[2] == (byte) 0xFF);
        if (isJpeg) return ".jpg";

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        boolean isPng = (header[0] == (byte) 0x89) && (header[1] == (byte) 0x50) &&
                        (header[2] == (byte) 0x4E) && (header[3] == (byte) 0x47);
        if (isPng) return ".png";

        // GIF: 47 49 46 38
        boolean isGif = (header[0] == (byte) 0x47) && (header[1] == (byte) 0x49) &&
                        (header[2] == (byte) 0x46) && (header[3] == (byte) 0x38);
        if (isGif) return ".gif";

        // WEBP: starts with "RIFF" and bytes 8-11 are "WEBP"
        boolean isWebp = (header[0] == (byte) 'R') && (header[1] == (byte) 'I') &&
                         (header[2] == (byte) 'F') && (header[3] == (byte) 'F') &&
                         header.length >= 12 &&
                         (header[8] == (byte) 'W') && (header[9] == (byte) 'E') &&
                         (header[10] == (byte) 'B') && (header[11] == (byte) 'P');
        if (isWebp) return ".webp";

        throw new IllegalArgumentException("Invalid file format. Only JPEG, PNG, WEBP, and GIF images are permitted.");
    }
}
