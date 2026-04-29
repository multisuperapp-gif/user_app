package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.UpdateUserProfileRequest;
import com.msa.userapp.modules.profile.dto.UpsertUserAddressRequest;
import com.msa.userapp.modules.profile.dto.UserAddressResponse;
import com.msa.userapp.modules.profile.dto.UserProfileResponse;
import com.msa.userapp.modules.profile.storage.UserProfileMediaStorageService;
import com.msa.userapp.persistence.sql.entity.UserAddressEntity;
import com.msa.userapp.persistence.sql.entity.UserAppProfileEntity;
import com.msa.userapp.persistence.sql.entity.UserEntity;
import com.msa.userapp.persistence.sql.repository.ServiceCountryRepository;
import com.msa.userapp.persistence.sql.repository.ServiceStateRepository;
import com.msa.userapp.persistence.sql.repository.UserAddressRepository;
import com.msa.userapp.persistence.sql.repository.UserAppProfileRepository;
import com.msa.userapp.persistence.sql.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private static final String CONSUMER_SCOPE = "CONSUMER";
    private static final int MAX_PROFILE_PHOTO_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PROFILE_PHOTO_DATA_URI_LENGTH = 8_000_000;

    private final UserRepository userRepository;
    private final UserAppProfileRepository userAppProfileRepository;
    private final UserAddressRepository userAddressRepository;
    private final ServiceCountryRepository serviceCountryRepository;
    private final ServiceStateRepository serviceStateRepository;
    private final UserProfileMediaStorageService userProfileMediaStorageService;

    public ProfileService(
            UserRepository userRepository,
            UserAppProfileRepository userAppProfileRepository,
            UserAddressRepository userAddressRepository,
            ServiceCountryRepository serviceCountryRepository,
            ServiceStateRepository serviceStateRepository,
            UserProfileMediaStorageService userProfileMediaStorageService
    ) {
        this.userRepository = userRepository;
        this.userAppProfileRepository = userAppProfileRepository;
        this.userAddressRepository = userAddressRepository;
        this.serviceCountryRepository = serviceCountryRepository;
        this.serviceStateRepository = serviceStateRepository;
        this.userProfileMediaStorageService = userProfileMediaStorageService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(Long userId) {
        UserEntity user = requireUser(userId);
        UserAppProfileEntity profile = userAppProfileRepository.findById(userId).orElse(null);
        return new UserProfileResponse(
                user.getId(),
                user.getPublicUserId(),
                user.getPhone(),
                defaultIfBlank(profile == null ? null : profile.getFullName(), "MSA User"),
                "",
                defaultIfBlank(profile == null ? null : profile.getProfilePhotoObjectKey(), ""),
                defaultIfBlank(profile == null ? null : profile.getGender(), ""),
                profile == null ? null : profile.getDob(),
                defaultIfBlank(profile == null ? null : profile.getLanguageCode(), "en")
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request) {
        requireUser(userId);
        UserProfileResponse existing = profile(userId);

        String fullName = firstNonBlank(request.fullName(), existing.fullName(), "MSA User");
        String gender = normalizeGender(request.gender(), existing.gender());
        LocalDate dob = request.dob() != null ? request.dob() : existing.dob();
        String languageCode = firstNonBlank(request.languageCode(), existing.languageCode(), "en").toLowerCase(Locale.ROOT);

        UserAppProfileEntity profile = userAppProfileRepository.findById(userId).orElseGet(() -> {
            UserAppProfileEntity entity = new UserAppProfileEntity();
            entity.setUserId(userId);
            return entity;
        });
        profile.setFullName(fullName);
        String requestedProfilePhotoDataUri = normalizeRequestedProfilePhotoDataUri(request.profilePhotoDataUri());
        if (requestedProfilePhotoDataUri != null) {
            String previousObjectKey = profile.getProfilePhotoObjectKey();
            UserProfileMediaStorageService.StoredProfilePhoto storedProfilePhoto =
                    userProfileMediaStorageService.storeProfilePhoto(requestedProfilePhotoDataUri);
            profile.setProfilePhotoDataUri(null);
            profile.setProfilePhotoObjectKey(storedProfilePhoto.objectKey());
            profile.setProfilePhotoContentType(storedProfilePhoto.contentType());
            if (previousObjectKey != null && !previousObjectKey.isBlank() && !previousObjectKey.equals(storedProfilePhoto.objectKey())) {
                userProfileMediaStorageService.deleteProfilePhoto(previousObjectKey);
            }
        }
        profile.setGender(blankToNull(gender));
        profile.setDob(dob);
        profile.setLanguageCode(languageCode);
        userAppProfileRepository.save(profile);

        return profile(userId);
    }

    @Transactional(readOnly = true)
    public List<UserAddressResponse> addresses(Long userId) {
        requireUser(userId);
        return userAddressRepository
                .findByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalseOrderByDefaultAddressDescUpdatedAtDescIdDesc(
                        userId,
                        CONSUMER_SCOPE
                )
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    @Transactional
    public UserAddressResponse createAddress(Long userId, UpsertUserAddressRequest request) {
        requireUser(userId);
        ValidatedAddress validated = validateAddress(request);
        boolean makeDefault = countAddresses(userId) == 0 || Boolean.TRUE.equals(request.isDefault());

        if (makeDefault) {
            clearDefaultAddress(userId);
        }

        UserAddressEntity address = new UserAddressEntity();
        OffsetDateTime now = OffsetDateTime.now();
        address.setUserId(userId);
        address.setAddressScope(CONSUMER_SCOPE);
        address.setBookingTemp(false);
        address.setHidden(false);
        address.setDefaultAddress(makeDefault);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        applyValidatedAddress(address, validated);
        return toAddressResponse(userAddressRepository.save(address));
    }

    @Transactional
    public UserAddressResponse createTemporaryBookingAddress(Long userId, UpsertUserAddressRequest request) {
        requireUser(userId);
        ValidatedAddress validated = validateAddress(request);

        UserAddressEntity address = new UserAddressEntity();
        OffsetDateTime now = OffsetDateTime.now();
        address.setUserId(userId);
        address.setAddressScope(CONSUMER_SCOPE);
        address.setBookingTemp(true);
        address.setHidden(false);
        address.setDefaultAddress(false);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        applyValidatedAddress(address, validated);
        return toAddressResponse(userAddressRepository.save(address));
    }

    @Transactional
    public UserAddressResponse updateAddress(Long userId, Long addressId, UpsertUserAddressRequest request) {
        requireUser(userId);
        UserAddressEntity address = requireAddress(userId, addressId);
        ValidatedAddress validated = validateAddress(request);

        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefaultAddress()) {
            clearDefaultAddress(userId);
        }

        applyValidatedAddress(address, validated);
        address.setDefaultAddress(address.isDefaultAddress() || Boolean.TRUE.equals(request.isDefault()));
        address.setUpdatedAt(OffsetDateTime.now());
        return toAddressResponse(userAddressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        requireUser(userId);
        UserAddressEntity address = requireAddress(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();

        address.setHidden(true);
        address.setDefaultAddress(false);
        address.setUpdatedAt(OffsetDateTime.now());
        userAddressRepository.save(address);

        if (wasDefault) {
            userAddressRepository
                    .findTopByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalseOrderByUpdatedAtDescIdDesc(userId, CONSUMER_SCOPE)
                    .ifPresent(remaining -> {
                        remaining.setDefaultAddress(true);
                        remaining.setUpdatedAt(OffsetDateTime.now());
                        userAddressRepository.save(remaining);
                    });
        }
    }

    @Transactional
    public UserAddressResponse setDefaultAddress(Long userId, Long addressId) {
        requireUser(userId);
        UserAddressEntity address = requireAddress(userId, addressId);
        clearDefaultAddress(userId);
        address.setDefaultAddress(true);
        address.setUpdatedAt(OffsetDateTime.now());
        return toAddressResponse(userAddressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public UserAddressResponse address(Long userId, Long addressId) {
        requireUser(userId);
        return toAddressResponse(requireAddress(userId, addressId));
    }

    private void applyValidatedAddress(UserAddressEntity address, ValidatedAddress validated) {
        address.setLabel(validated.label());
        address.setAddressLine1(validated.addressLine1());
        address.setAddressLine2(validated.addressLine2());
        address.setLandmark(validated.landmark());
        address.setCity(validated.city());
        address.setStateId(validated.stateId());
        address.setState(validated.state());
        address.setCountryId(validated.countryId());
        address.setCountry(validated.country());
        address.setPostalCode(validated.postalCode());
        address.setLatitude(validated.latitude());
        address.setLongitude(validated.longitude());
    }

    private UserAddressResponse toAddressResponse(UserAddressEntity address) {
        return new UserAddressResponse(
                address.getId(),
                address.getLabel(),
                address.getAddressLine1(),
                defaultIfBlank(address.getAddressLine2(), ""),
                defaultIfBlank(address.getLandmark(), ""),
                address.getCity(),
                address.getStateId(),
                address.getState(),
                address.getCountryId(),
                address.getCountry(),
                address.getPostalCode(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefaultAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt()
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
        if (countryId != null && !serviceCountryRepository.existsByIdAndActiveTrue(countryId)) {
            throw new BadRequestException("Selected country is not valid");
        }
        if (stateId != null && !serviceStateRepository.existsByIdAndActiveTrue(stateId)) {
            throw new BadRequestException("Selected state is not valid");
        }
    }

    private int countAddresses(Long userId) {
        return (int) userAddressRepository.countByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalse(userId, CONSUMER_SCOPE);
    }

    private void clearDefaultAddress(Long userId) {
        userAddressRepository.clearDefaultByUserIdAndAddressScope(userId, CONSUMER_SCOPE);
    }

    private UserEntity requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserAddressEntity requireAddress(Long userId, Long addressId) {
        return userAddressRepository
                .findByIdAndUserIdAndAddressScopeAndHiddenFalse(addressId, userId, CONSUMER_SCOPE)
                .orElseThrow(() -> new NotFoundException("Address not found"));
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

    public UserProfileMediaStorageService.LoadedProfilePhoto loadProfilePhoto(String objectKey) {
        UserAppProfileEntity profile = userAppProfileRepository.findByProfilePhotoObjectKey(objectKey)
                .orElseThrow(() -> new NotFoundException("Profile photo not found"));
        return userProfileMediaStorageService.loadProfilePhoto(
                profile.getProfilePhotoObjectKey(),
                profile.getProfilePhotoContentType()
        );
    }

    private String normalizeRequestedProfilePhotoDataUri(String requestedPhotoDataUri) {
        String normalized = optionalTrimmed(requestedPhotoDataUri);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (!normalized.startsWith("data:image/") || !normalized.contains(";base64,")) {
            throw new BadRequestException("Profile photo must be a valid image.");
        }
        if (normalized.length() > MAX_PROFILE_PHOTO_DATA_URI_LENGTH) {
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
