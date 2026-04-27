package com.msa.userapp.modules.profile.storage;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class UserProfileMediaStorageService {
    private static final String STORAGE_PROVIDER_S3 = "S3";
    private static final String STORAGE_PROVIDER_LOCAL = "LOCAL";

    private final UserProfileMediaStorageProperties storageProperties;

    public StoredProfilePhoto storeProfilePhoto(String requestedPhotoDataUri) {
        DecodedPhoto decoded = decodeDataUri(requestedPhotoDataUri);
        String objectKey = buildObjectKey(decoded.extension());
        String storageProvider = STORAGE_PROVIDER_S3.equalsIgnoreCase(storageProperties.getProvider())
                ? STORAGE_PROVIDER_S3
                : STORAGE_PROVIDER_LOCAL;
        storeBytes(storageProperties.getPublicMedia().getBucket(), objectKey, decoded.bytes(), decoded.contentType(), storageProvider);
        return new StoredProfilePhoto(objectKey, decoded.contentType());
    }

    public LoadedProfilePhoto loadProfilePhoto(String objectKey, String contentType) {
        String normalizedKey = normalizeObjectKey(objectKey);
        String bucketName = storageProperties.getPublicMedia().getBucket();
        byte[] bytes;
        if (STORAGE_PROVIDER_S3.equalsIgnoreCase(storageProperties.getProvider())) {
            try (S3Client s3Client = s3Client()) {
                ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(normalizedKey)
                                .build()
                );
                bytes = response.asByteArray();
            } catch (Exception ex) {
                throw new NotFoundException("Profile photo not found");
            }
        } else {
            Path source = resolveLocalRoot().resolve(bucketName).resolve(normalizedKey);
            if (!Files.exists(source)) {
                throw new NotFoundException("Profile photo not found");
            }
            try {
                bytes = Files.readAllBytes(source);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read profile photo", ex);
            }
        }
        return new LoadedProfilePhoto(bytes, defaultContentType(contentType, normalizedKey));
    }

    public void deleteProfilePhoto(String objectKey) {
        String normalizedKey = normalizeObjectKey(objectKey);
        if (normalizedKey.isEmpty()) {
            return;
        }
        String bucketName = storageProperties.getPublicMedia().getBucket();
        if (STORAGE_PROVIDER_S3.equalsIgnoreCase(storageProperties.getProvider())) {
            try (S3Client s3Client = s3Client()) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(normalizedKey)
                        .build());
            } catch (Exception ignored) {
                return;
            }
            return;
        }
        try {
            Files.deleteIfExists(resolveLocalRoot().resolve(bucketName).resolve(normalizedKey));
        } catch (IOException ignored) {
            return;
        }
    }

    private void storeBytes(String bucketName, String objectKey, byte[] bytes, String contentType, String storageProvider) {
        if (STORAGE_PROVIDER_S3.equalsIgnoreCase(storageProvider)) {
            try (S3Client s3Client = s3Client()) {
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectKey)
                                .contentType(contentType)
                                .build(),
                        RequestBody.fromBytes(bytes)
                );
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to upload profile photo to S3", ex);
            }
            return;
        }
        Path destination = resolveLocalRoot().resolve(bucketName).resolve(objectKey);
        try {
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store profile photo locally", ex);
        }
    }

    private DecodedPhoto decodeDataUri(String requestedPhotoDataUri) {
        String normalized = requestedPhotoDataUri == null ? "" : requestedPhotoDataUri.trim();
        if (!normalized.startsWith("data:image/") || !normalized.contains(";base64,")) {
            throw new BadRequestException("Profile photo must be a valid image.");
        }
        if (normalized.length() > 2_500_000) {
            throw new BadRequestException("Profile photo is too large. Please choose a smaller image.");
        }
        int markerIndex = normalized.indexOf(";base64,");
        String mimeType = normalized.substring(5, markerIndex).trim().toLowerCase(Locale.ROOT);
        String base64Body = normalized.substring(markerIndex + 8);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Body);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Profile photo must be a valid base64 image.");
        }
        if (bytes.length > 3 * 1024 * 1024) {
            throw new BadRequestException("Profile photo is too large. Please choose a smaller image.");
        }
        return new DecodedPhoto(bytes, mimeType, extensionFor(mimeType));
    }

    private String buildObjectKey(String extension) {
        LocalDate today = LocalDate.now();
        String prefix = normalizePrefix(storageProperties.getPrefixes().getProfile());
        String random = UUID.randomUUID().toString().replace("-", "");
        return "%s/%d/%02d/%s.%s".formatted(prefix, today.getYear(), today.getMonthValue(), random, extension);
    }

    private String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "profile" : normalized;
    }

    private String normalizeObjectKey(String objectKey) {
        return objectKey == null ? "" : objectKey.trim();
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private String defaultContentType(String contentType, String objectKey) {
        String normalized = contentType == null ? "" : contentType.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private Path resolveLocalRoot() {
        return Path.of(storageProperties.getLocalRoot()).toAbsolutePath().normalize();
    }

    private S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(storageProperties.getAwsRegion()))
                .build();
    }

    private record DecodedPhoto(byte[] bytes, String contentType, String extension) {
    }

    public record StoredProfilePhoto(String objectKey, String contentType) {
    }

    public record LoadedProfilePhoto(byte[] bytes, String contentType) {
    }
}
