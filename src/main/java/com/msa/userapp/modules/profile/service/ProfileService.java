package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.UpdateUserProfileRequest;
import com.msa.userapp.modules.profile.dto.UpsertUserAddressRequest;
import com.msa.userapp.modules.profile.dto.UserAddressResponse;
import com.msa.userapp.modules.profile.dto.UserProfileResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProfileService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(Long userId) {
        validateUserExists(userId);
        return jdbcTemplate.query("""
                SELECT
                    u.id,
                    u.public_user_id,
                    u.phone,
                    uap.full_name,
                    uap.profile_photo_data_uri,
                    uap.gender,
                    uap.dob,
                    uap.language_code
                FROM users u
                LEFT JOIN user_app_profiles uap ON uap.user_id = u.id
                WHERE u.id = :userId
                LIMIT 1
                """, Map.of("userId", userId), rs -> {
            if (!rs.next()) {
                throw new NotFoundException("User profile not found");
            }
            return new UserProfileResponse(
                    rs.getLong("id"),
                    rs.getString("public_user_id"),
                    rs.getString("phone"),
                    defaultIfBlank(rs.getString("full_name"), "MSA User"),
                    defaultIfBlank(rs.getString("profile_photo_data_uri"), ""),
                    defaultIfBlank(rs.getString("gender"), ""),
                    rs.getObject("dob", LocalDate.class),
                    defaultIfBlank(rs.getString("language_code"), "en")
            );
        });
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request) {
        validateUserExists(userId);
        UserProfileResponse existing = profile(userId);

        String fullName = firstNonBlank(request.fullName(), existing.fullName(), "MSA User");
        String profilePhotoDataUri = normalizeProfilePhotoDataUri(request.profilePhotoDataUri(), existing.profilePhotoDataUri());
        String gender = normalizeGender(request.gender(), existing.gender());
        LocalDate dob = request.dob() != null ? request.dob() : existing.dob();
        String languageCode = firstNonBlank(request.languageCode(), existing.languageCode(), "en").toLowerCase(Locale.ROOT);

        Integer existingProfileCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM user_app_profiles
                WHERE user_id = :userId
                """, Map.of("userId", userId), Integer.class);

        if (existingProfileCount != null && existingProfileCount > 0) {
            jdbcTemplate.update("""
                    UPDATE user_app_profiles
                    SET full_name = :fullName,
                        profile_photo_data_uri = :profilePhotoDataUri,
                        gender = :gender,
                        dob = :dob,
                        language_code = :languageCode
                    WHERE user_id = :userId
                    """, new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("fullName", fullName)
                    .addValue("profilePhotoDataUri", blankToNull(profilePhotoDataUri))
                    .addValue("gender", blankToNull(gender))
                    .addValue("dob", dob)
                    .addValue("languageCode", languageCode));
        } else {
            jdbcTemplate.update("""
                    INSERT INTO user_app_profiles (
                        user_id,
                        full_name,
                        profile_photo_data_uri,
                        gender,
                        dob,
                        language_code
                    ) VALUES (
                        :userId,
                        :fullName,
                        :profilePhotoDataUri,
                        :gender,
                        :dob,
                        :languageCode
                    )
                    """, new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("fullName", fullName)
                    .addValue("profilePhotoDataUri", blankToNull(profilePhotoDataUri))
                    .addValue("gender", blankToNull(gender))
                    .addValue("dob", dob)
                    .addValue("languageCode", languageCode));
        }

        return profile(userId);
    }

    @Transactional(readOnly = true)
    public List<UserAddressResponse> addresses(Long userId) {
        validateUserExists(userId);
        return jdbcTemplate.query("""
                SELECT
                    id,
                    label,
                    address_line1,
                    address_line2,
                    landmark,
                    city,
                    state_id,
                    state,
                    country_id,
                    country,
                    postal_code,
                    latitude,
                    longitude,
                    is_default,
                    created_at,
                    updated_at
                FROM user_addresses
                WHERE user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_booking_temp = 0
                  AND is_hidden = 0
                ORDER BY is_default DESC, updated_at DESC, id DESC
                """, Map.of("userId", userId), (rs, rowNum) -> mapAddress(rs.getLong("id"), rs));
    }

    @Transactional
    public UserAddressResponse createAddress(Long userId, UpsertUserAddressRequest request) {
        validateUserExists(userId);
        ValidatedAddress validated = validateAddress(request);
        int existingCount = countAddresses(userId);
        boolean makeDefault = existingCount == 0 || Boolean.TRUE.equals(request.isDefault());

        if (makeDefault) {
            clearDefaultAddress(userId);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO user_addresses (
                    user_id,
                    label,
                    address_line1,
                    address_line2,
                    landmark,
                    city,
                    state_id,
                    state,
                    country_id,
                    country,
                    postal_code,
                    latitude,
                    longitude,
                    is_default,
                    is_booking_temp,
                    address_scope
                ) VALUES (
                    :userId,
                    :label,
                    :addressLine1,
                    :addressLine2,
                    :landmark,
                    :city,
                    :stateId,
                    :state,
                    :countryId,
                    :country,
                    :postalCode,
                    :latitude,
                    :longitude,
                    :isDefault,
                    0,
                    'CONSUMER'
                )
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("label", validated.label())
                .addValue("addressLine1", validated.addressLine1())
                .addValue("addressLine2", validated.addressLine2())
                .addValue("landmark", validated.landmark())
                .addValue("city", validated.city())
                .addValue("stateId", validated.stateId())
                .addValue("state", validated.state())
                .addValue("countryId", validated.countryId())
                .addValue("country", validated.country())
                .addValue("postalCode", validated.postalCode())
                .addValue("latitude", validated.latitude())
                .addValue("longitude", validated.longitude())
                .addValue("isDefault", makeDefault), keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BadRequestException("Address could not be created");
        }
        return address(userId, key.longValue());
    }

    @Transactional
    public UserAddressResponse createTemporaryBookingAddress(Long userId, UpsertUserAddressRequest request) {
        validateUserExists(userId);
        ValidatedAddress validated = validateAddress(request);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO user_addresses (
                    user_id,
                    label,
                    address_line1,
                    address_line2,
                    landmark,
                    city,
                    state_id,
                    state,
                    country_id,
                    country,
                    postal_code,
                    latitude,
                    longitude,
                    is_default,
                    is_booking_temp,
                    address_scope
                ) VALUES (
                    :userId,
                    :label,
                    :addressLine1,
                    :addressLine2,
                    :landmark,
                    :city,
                    :stateId,
                    :state,
                    :countryId,
                    :country,
                    :postalCode,
                    :latitude,
                    :longitude,
                    0,
                    1,
                    'CONSUMER'
                )
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("label", validated.label())
                .addValue("addressLine1", validated.addressLine1())
                .addValue("addressLine2", validated.addressLine2())
                .addValue("landmark", validated.landmark())
                .addValue("city", validated.city())
                .addValue("stateId", validated.stateId())
                .addValue("state", validated.state())
                .addValue("countryId", validated.countryId())
                .addValue("country", validated.country())
                .addValue("postalCode", validated.postalCode())
                .addValue("latitude", validated.latitude())
                .addValue("longitude", validated.longitude()), keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BadRequestException("Temporary booking address could not be created");
        }
        return address(userId, key.longValue());
    }

    @Transactional
    public UserAddressResponse updateAddress(Long userId, Long addressId, UpsertUserAddressRequest request) {
        validateUserExists(userId);
        UserAddressResponse existing = address(userId, addressId);
        ValidatedAddress validated = validateAddress(request);

        if (Boolean.TRUE.equals(request.isDefault()) && !existing.isDefault()) {
            clearDefaultAddress(userId);
        }

        jdbcTemplate.update("""
                UPDATE user_addresses
                SET label = :label,
                    address_line1 = :addressLine1,
                    address_line2 = :addressLine2,
                    landmark = :landmark,
                    city = :city,
                    state_id = :stateId,
                    state = :state,
                    country_id = :countryId,
                    country = :country,
                    postal_code = :postalCode,
                    latitude = :latitude,
                    longitude = :longitude,
                    is_default = :isDefault
                WHERE id = :addressId
                  AND user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_hidden = 0
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("addressId", addressId)
                .addValue("label", validated.label())
                .addValue("addressLine1", validated.addressLine1())
                .addValue("addressLine2", validated.addressLine2())
                .addValue("landmark", validated.landmark())
                .addValue("city", validated.city())
                .addValue("stateId", validated.stateId())
                .addValue("state", validated.state())
                .addValue("countryId", validated.countryId())
                .addValue("country", validated.country())
                .addValue("postalCode", validated.postalCode())
                .addValue("latitude", validated.latitude())
                .addValue("longitude", validated.longitude())
                .addValue("isDefault", existing.isDefault() || Boolean.TRUE.equals(request.isDefault())));

        return address(userId, addressId);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        validateUserExists(userId);
        UserAddressResponse existing = address(userId, addressId);
        int updated = jdbcTemplate.update("""
                UPDATE user_addresses
                SET is_hidden = 1,
                    is_default = 0
                WHERE id = :addressId
                  AND user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_hidden = 0
                """, Map.of("addressId", addressId, "userId", userId));
        if (updated == 0) {
            throw new NotFoundException("Address not found");
        }

        if (existing.isDefault()) {
            jdbcTemplate.update("""
                    UPDATE user_addresses
                    SET is_default = 1
                    WHERE id = (
                        SELECT id
                        FROM (
                            SELECT id
                            FROM user_addresses
                            WHERE user_id = :userId
                              AND address_scope = 'CONSUMER'
                              AND is_booking_temp = 0
                              AND is_hidden = 0
                            ORDER BY updated_at DESC, id DESC
                            LIMIT 1
                        ) remaining
                    )
                    """, Map.of("userId", userId));
        }
    }

    @Transactional
    public UserAddressResponse setDefaultAddress(Long userId, Long addressId) {
        validateUserExists(userId);
        address(userId, addressId);
        clearDefaultAddress(userId);
        jdbcTemplate.update("""
                UPDATE user_addresses
                SET is_default = 1
                WHERE id = :addressId
                  AND user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_hidden = 0
                """, Map.of("addressId", addressId, "userId", userId));
        return address(userId, addressId);
    }

    @Transactional(readOnly = true)
    public UserAddressResponse address(Long userId, Long addressId) {
        validateUserExists(userId);
        List<UserAddressResponse> rows = jdbcTemplate.query("""
                SELECT
                    id,
                    label,
                    address_line1,
                    address_line2,
                    landmark,
                    city,
                    state_id,
                    state,
                    country_id,
                    country,
                    postal_code,
                    latitude,
                    longitude,
                    is_default,
                    created_at,
                    updated_at
                FROM user_addresses
                WHERE id = :addressId
                  AND user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_hidden = 0
                LIMIT 1
                """, Map.of("addressId", addressId, "userId", userId), (rs, rowNum) -> mapAddress(addressId, rs));
        if (rows.isEmpty()) {
            throw new NotFoundException("Address not found");
        }
        return rows.get(0);
    }

    private UserAddressResponse mapAddress(Long addressId, java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserAddressResponse(
                addressId,
                rs.getString("label"),
                rs.getString("address_line1"),
                defaultIfBlank(rs.getString("address_line2"), ""),
                defaultIfBlank(rs.getString("landmark"), ""),
                rs.getString("city"),
                rs.getObject("state_id") == null ? null : rs.getLong("state_id"),
                rs.getString("state"),
                rs.getObject("country_id") == null ? null : rs.getLong("country_id"),
                rs.getString("country"),
                rs.getString("postal_code"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                rs.getBoolean("is_default"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private ValidatedAddress validateAddress(UpsertUserAddressRequest request) {
        String label = requireTrimmed(request.label(), "Address label is required");
        String addressLine1 = requireTrimmed(request.addressLine1(), "Address line 1 is required");
        String city = requireTrimmed(request.city(), "City is required");
        String state = requireTrimmed(request.state(), "State is required");
        String country = firstNonBlank(request.country(), "India");
        String postalCode = requireTrimmed(request.postalCode(), "Postal code is required");
        BigDecimal latitude = request.latitude();
        BigDecimal longitude = request.longitude();

        if (latitude == null || longitude == null) {
            throw new BadRequestException("Latitude and longitude are required");
        }
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BadRequestException("Latitude must be between -90 and 90");
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BadRequestException("Longitude must be between -180 and 180");
        }

        validateCountryState(request.countryId(), request.stateId());

        return new ValidatedAddress(
                label,
                addressLine1,
                optionalTrimmed(request.addressLine2()),
                optionalTrimmed(request.landmark()),
                city,
                request.stateId(),
                state,
                request.countryId(),
                country,
                postalCode,
                latitude,
                longitude
        );
    }

    private void validateCountryState(Long countryId, Long stateId) {
        if (countryId != null) {
            Integer countryCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM service_countries
                    WHERE id = :countryId
                      AND is_active = 1
                    """, Map.of("countryId", countryId), Integer.class);
            if (countryCount == null || countryCount == 0) {
                throw new BadRequestException("Selected country is not valid");
            }
        }
        if (stateId != null) {
            Integer stateCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM service_states
                    WHERE id = :stateId
                      AND is_active = 1
                    """, Map.of("stateId", stateId), Integer.class);
            if (stateCount == null || stateCount == 0) {
                throw new BadRequestException("Selected state is not valid");
            }
        }
    }

    private int countAddresses(Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM user_addresses
                WHERE user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_booking_temp = 0
                  AND is_hidden = 0
                """, Map.of("userId", userId), Integer.class);
        return count == null ? 0 : count;
    }

    private void clearDefaultAddress(Long userId) {
        jdbcTemplate.update("""
                UPDATE user_addresses
                SET is_default = 0
                WHERE user_id = :userId
                  AND address_scope = 'CONSUMER'
                  AND is_hidden = 0
                """, Map.of("userId", userId));
    }

    private void validateUserExists(Long userId) {
        Integer exists = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM users
                WHERE id = :userId
                """, Map.of("userId", userId), Integer.class);
        if (exists == null || exists == 0) {
            throw new NotFoundException("User not found");
        }
    }

    private String normalizeGender(String requestedGender, String existingGender) {
        String fallback = defaultIfBlank(existingGender, "");
        if (requestedGender == null || requestedGender.trim().isEmpty()) {
            return fallback;
        }
        String normalized = requestedGender.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY" -> normalized;
            default -> throw new BadRequestException("Gender must be one of MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY");
        };
    }

    private String normalizeProfilePhotoDataUri(String requestedPhotoDataUri, String existingPhotoDataUri) {
        String normalized = firstNonBlank(requestedPhotoDataUri, existingPhotoDataUri, "");
        if (normalized.isBlank()) {
            return "";
        }
        if (!normalized.startsWith("data:image/") || !normalized.contains(";base64,")) {
            throw new BadRequestException("Profile photo must be a valid image.");
        }
        if (normalized.length() > 2_500_000) {
            throw new BadRequestException("Profile photo is too large. Please choose a smaller image.");
        }
        return normalized;
    }

    private String requireTrimmed(String value, String message) {
        String trimmed = optionalTrimmed(value);
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }

    private String optionalTrimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String primary, String fallback) {
        String preferred = optionalTrimmed(primary);
        return preferred == null ? fallback : preferred;
    }

    private String firstNonBlank(String primary, String secondary, String fallback) {
        String first = optionalTrimmed(primary);
        if (first != null) {
            return first;
        }
        String second = optionalTrimmed(secondary);
        return second == null ? fallback : second;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = optionalTrimmed(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String blankToNull(String value) {
        return optionalTrimmed(value);
    }

    private record ValidatedAddress(
            String label,
            String addressLine1,
            String addressLine2,
            String landmark,
            String city,
            Long stateId,
            String state,
            Long countryId,
            String country,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
