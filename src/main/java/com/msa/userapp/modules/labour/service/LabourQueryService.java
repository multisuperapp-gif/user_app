package com.msa.userapp.modules.labour.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.labour.dto.LabourApiDtos;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import com.msa.userapp.persistence.sql.repository.LabourDiscoveryRepository;
import com.msa.userapp.persistence.sql.repository.UserAddressRepository;
import com.msa.userapp.persistence.sql.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LabourQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final LabourDiscoveryRepository labourDiscoveryRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    public LabourQueryService(
            LabourDiscoveryRepository labourDiscoveryRepository,
            UserAddressRepository userAddressRepository,
            UserRepository userRepository
    ) {
        this.labourDiscoveryRepository = labourDiscoveryRepository;
        this.userAddressRepository = userAddressRepository;
        this.userRepository = userRepository;
    }

    public LabourApiDtos.LabourLandingResponse landing(
            Long userId,
            Long categoryId,
            String city,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        return new LabourApiDtos.LabourLandingResponse(
                categories(),
                profiles(userId, categoryId, null, city, latitude, longitude, page, size)
        );
    }

    public List<LabourApiDtos.LabourCategoryResponse> categories() {
        return labourDiscoveryRepository.findActiveCategories().stream()
                .map(row -> new LabourApiDtos.LabourCategoryResponse(
                        row.getId(),
                        row.getName(),
                        row.getNormalizedName()
                ))
                .toList();
    }

    public PageResponse<LabourApiDtos.LabourProfileCardResponse> profiles(
            Long userId,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        return profiles(userId, categoryId, search, null, null, null, page, size);
    }

    public PageResponse<LabourApiDtos.LabourProfileCardResponse> profiles(
            Long userId,
            Long categoryId,
            String search,
            String city,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;
        UserLocation userLocation = resolveUserLocation(userId, city, latitude, longitude);
        String currentUserPhone = resolveUserPhone(userId);

        List<LabourApiDtos.LabourProfileCardResponse> rows = labourDiscoveryRepository.findProfiles(
                        userId,
                        currentUserPhone,
                        categoryId,
                        StringUtils.hasText(search) ? "%" + search.trim() + "%" : null,
                        userLocation.city(),
                        userLocation.latitude(),
                        userLocation.longitude(),
                        PageRequest.of(safePage, limit)
                ).stream()
                .map(this::toLabourCard)
                .toList();
        boolean hasMore = rows.size() > safeSize;
        List<LabourApiDtos.LabourProfileCardResponse> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new PageResponse<>(items, safePage, safeSize, hasMore);
    }

    public LabourApiDtos.LabourProfileResponse profile(Long userId, Long labourId) {
        LabourApiDtos.LabourProfileCardResponse profile = requireProfile(userId, labourId);

        List<String> skills = labourDiscoveryRepository.findEnabledSkillsByLabourId(labourId);

        return new LabourApiDtos.LabourProfileResponse(profile, skills);
    }

    public Long resolveDefaultAddressId(Long userId, Long explicitAddressId) {
        if (explicitAddressId != null) {
            if (userAddressRepository.findByIdAndUserIdAndAddressScopeAndHiddenFalse(explicitAddressId, userId, "CONSUMER").isEmpty()) {
                throw new NotFoundException("Address not found for this user");
            }
            return explicitAddressId;
        }
        return userAddressRepository
                .findByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalseOrderByDefaultAddressDescUpdatedAtDescIdDesc(userId, "CONSUMER")
                .stream()
                .findFirst()
                .map(address -> address.getId())
                .orElseThrow(() -> new NotFoundException("Please add an address before booking labour"));
    }

    private LabourApiDtos.LabourProfileCardResponse requireProfile(Long userId, Long labourId) {
        UserLocation userLocation = resolveUserLocation(userId, null, null, null);
        String currentUserPhone = resolveUserPhone(userId);
        LabourApiDtos.LabourProfileCardResponse profile = labourDiscoveryRepository.findProfiles(
                        userId,
                        currentUserPhone,
                        null,
                        null,
                        userLocation.city(),
                        userLocation.latitude(),
                        userLocation.longitude(),
                        PageRequest.of(0, 100)
                ).stream()
                .filter(row -> row.getLabourId().equals(labourId))
                .findFirst()
                .map(this::toLabourCard)
                .orElse(null);
        if (profile == null) {
            throw new NotFoundException("Labour profile not found");
        }
        return profile;
    }

    private List<LabourApiDtos.LabourCategoryPricingResponse> loadCategoryPricings(Long labourId) {
        return labourDiscoveryRepository.findCategoryPricingsByLabourId(labourId).stream()
                .map(row -> new LabourApiDtos.LabourCategoryPricingResponse(
                        row.getCategoryId(),
                        row.getCategoryName(),
                        row.getHalfDayRate(),
                        row.getFullDayRate()
                ))
                .toList();
    }

    private UserLocation resolveUserLocation(Long userId, String overrideCity, Double overrideLatitude, Double overrideLongitude) {
        if (overrideLatitude != null && overrideLongitude != null) {
            return new UserLocation(
                    null,
                    StringUtils.hasText(overrideCity) ? overrideCity.trim() : null,
                    BigDecimal.valueOf(overrideLatitude),
                    BigDecimal.valueOf(overrideLongitude)
            );
        }
        if (userId == null || userId <= 0) {
            return new UserLocation(null, null, null, null);
        }
        return userAddressRepository
                .findByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalseOrderByDefaultAddressDescUpdatedAtDescIdDesc(userId, "CONSUMER")
                .stream()
                .findFirst()
                .map(address -> new UserLocation(address.getId(), address.getCity(), address.getLatitude(), address.getLongitude()))
                .orElse(new UserLocation(null, null, null, null));
    }

    private String resolveUserPhone(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        String phone = userRepository.findById(userId).map(user -> user.getPhone()).orElse(null);
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }

    private LabourApiDtos.LabourProfileCardResponse toLabourCard(LabourDiscoveryRepository.LabourProfileRowView row) {
        return new LabourApiDtos.LabourProfileCardResponse(
                row.getLabourId(),
                row.getCategoryId(),
                row.getCategoryName(),
                loadCategoryPricings(row.getLabourId()),
                row.getFullName(),
                row.getPhotoObjectKey(),
                maskPhone(row.getPhone()),
                row.getExperienceYears(),
                row.getHourlyRate(),
                row.getHalfDayRate(),
                row.getFullDayRate(),
                row.getAvgRating(),
                row.getTotalCompletedJobs(),
                row.getDistanceKm(),
                row.getRadiusKm(),
                row.getWorkLatitude(),
                row.getWorkLongitude(),
                Boolean.TRUE.equals(row.getOnlineStatus()),
                row.getAvailableNow() != null && row.getAvailableNow() == 1,
                row.getAvailabilityStatus(),
                row.getActiveBookingCount(),
                row.getSkillsSummary()
        );
    }

    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String normalized = phone.trim();
        if (normalized.length() <= 4) {
            return normalized;
        }
        String prefix = normalized.substring(0, Math.min(2, normalized.length()));
        String suffix = normalized.substring(Math.max(normalized.length() - 2, 0));
        return prefix + "xxxxxx" + suffix;
    }

    private record UserLocation(
            Long addressId,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
