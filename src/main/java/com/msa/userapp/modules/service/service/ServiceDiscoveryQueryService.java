package com.msa.userapp.modules.service.service;

import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.service.dto.ServiceApiDtos;
import com.msa.userapp.modules.shop.common.dto.PageResponse;
import com.msa.userapp.persistence.sql.repository.ServiceDiscoveryRepository;
import com.msa.userapp.persistence.sql.repository.UserAddressRepository;
import com.msa.userapp.persistence.sql.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ServiceDiscoveryQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final ServiceDiscoveryRepository serviceDiscoveryRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    public ServiceDiscoveryQueryService(
            ServiceDiscoveryRepository serviceDiscoveryRepository,
            UserAddressRepository userAddressRepository,
            UserRepository userRepository
    ) {
        this.serviceDiscoveryRepository = serviceDiscoveryRepository;
        this.userAddressRepository = userAddressRepository;
        this.userRepository = userRepository;
    }

    public ServiceApiDtos.ServiceLandingResponse landing(
            Long userId,
            Long categoryId,
            Long subcategoryId,
            String city,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        return new ServiceApiDtos.ServiceLandingResponse(
                categories(),
                providers(userId, categoryId, subcategoryId, null, city, latitude, longitude, page, size)
        );
    }

    public List<ServiceApiDtos.ServiceCategoryResponse> categories() {
        Map<Long, ServiceApiDtos.ServiceCategoryResponse> categories = new LinkedHashMap<>();
        for (ServiceDiscoveryRepository.ServiceCategoryRowView row : serviceDiscoveryRepository.findActiveCategories()) {
            Long categoryId = row.getCategoryId();
            ServiceApiDtos.ServiceCategoryResponse existing = categories.get(categoryId);
            if (existing == null) {
                existing = new ServiceApiDtos.ServiceCategoryResponse(
                        categoryId,
                        row.getCategoryName(),
                        new ArrayList<>()
                );
                categories.put(categoryId, existing);
            }
            Long subcategoryId = row.getSubcategoryId();
            if (subcategoryId != null) {
                @SuppressWarnings("unchecked")
                List<ServiceApiDtos.ServiceSubcategoryResponse> subcategories =
                        (List<ServiceApiDtos.ServiceSubcategoryResponse>) existing.subcategories();
                subcategories.add(new ServiceApiDtos.ServiceSubcategoryResponse(
                        subcategoryId,
                        categoryId,
                        row.getSubcategoryName()
                ));
            }
        }
        return new ArrayList<>(categories.values());
    }

    public PageResponse<ServiceApiDtos.ServiceProviderCardResponse> providers(
            Long userId,
            Long categoryId,
            Long subcategoryId,
            String search,
            int page,
            int size
    ) {
        return providers(userId, categoryId, subcategoryId, search, null, null, null, page, size);
    }

    public PageResponse<ServiceApiDtos.ServiceProviderCardResponse> providers(
            Long userId,
            Long categoryId,
            Long subcategoryId,
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

        List<ServiceApiDtos.ServiceProviderCardResponse> rows = serviceDiscoveryRepository.findProviders(
                        userId,
                        currentUserPhone,
                        categoryId,
                        subcategoryId,
                        StringUtils.hasText(search) ? "%" + search.trim() + "%" : null,
                        userLocation.city(),
                        userLocation.latitude(),
                        userLocation.longitude(),
                        PageRequest.of(safePage, limit)
                ).stream()
                .map(this::toProviderCard)
                .toList();
        boolean hasMore = rows.size() > safeSize;
        List<ServiceApiDtos.ServiceProviderCardResponse> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new PageResponse<>(items, safePage, safeSize, hasMore);
    }

    public ServiceApiDtos.ServiceProviderProfileResponse providerProfile(
            Long userId,
            Long providerId,
            Long categoryId,
            Long subcategoryId
    ) {
        ServiceApiDtos.ServiceProviderCardResponse provider = requireProvider(userId, providerId, categoryId, subcategoryId);
        List<ServiceApiDtos.ServiceProviderServiceOptionResponse> serviceOptions = serviceDiscoveryRepository
                .findServiceOptionsByProviderId(providerId, categoryId)
                .stream()
                .map(row -> new ServiceApiDtos.ServiceProviderServiceOptionResponse(
                        row.getCategoryId(),
                        row.getSubcategoryId(),
                        row.getCategoryName(),
                        row.getSubcategoryName(),
                        row.getVisitingCharge()
                ))
                .toList();
        List<String> serviceItems = !provider.serviceItems().isEmpty()
                ? provider.serviceItems()
                : serviceOptions.stream()
                        .map(ServiceApiDtos.ServiceProviderServiceOptionResponse::subcategoryName)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
        if (serviceItems.isEmpty()) {
            serviceItems = serviceDiscoveryRepository.findServiceItemsByProviderId(providerId, categoryId, subcategoryId);
        }
        return new ServiceApiDtos.ServiceProviderProfileResponse(provider, serviceItems, serviceOptions);
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
                .orElseThrow(() -> new NotFoundException("Please add an address before booking service"));
    }

    ServiceApiDtos.ServiceProviderCardResponse requireProvider(
            Long userId,
            Long providerId,
            Long categoryId,
            Long subcategoryId
    ) {
        PageResponse<ServiceApiDtos.ServiceProviderCardResponse> page = providers(
                userId,
                categoryId,
                subcategoryId,
                null,
                null,
                null,
                null,
                0,
                MAX_PAGE_SIZE
        );
        return page.items().stream()
                .filter(item -> item.providerId().equals(providerId))
                .findFirst()
                .orElseGet(() -> loadSingleProvider(userId, providerId, categoryId, subcategoryId));
    }

    private ServiceApiDtos.ServiceProviderCardResponse loadSingleProvider(
            Long userId,
            Long providerId,
            Long categoryId,
            Long subcategoryId
    ) {
        UserLocation userLocation = resolveUserLocation(userId, null, null, null);
        String currentUserPhone = resolveUserPhone(userId);
        ServiceApiDtos.ServiceProviderCardResponse provider = serviceDiscoveryRepository.findProviders(
                        userId,
                        currentUserPhone,
                        categoryId,
                        subcategoryId,
                        null,
                        null,
                        userLocation.latitude(),
                        userLocation.longitude(),
                        PageRequest.of(0, 100)
                ).stream()
                .filter(row -> row.getProviderId().equals(providerId))
                .findFirst()
                .map(this::toProviderCard)
                .orElse(null);
        if (provider == null) {
            throw new NotFoundException("Service provider not found");
        }
        return provider;
    }

    private List<String> parseServiceItems(String rawItems) {
        if (!StringUtils.hasText(rawItems)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : rawItems.split("\\|\\|")) {
            String normalized = item == null ? "" : item.trim();
            if (!normalized.isEmpty()) {
                items.add(normalized);
            }
        }
        return items;
    }

    private UserLocation resolveUserLocation(Long userId, String overrideCity, Double overrideLatitude, Double overrideLongitude) {
        if (overrideLatitude != null && overrideLongitude != null) {
            return new UserLocation(
                    null,
                    BigDecimal.valueOf(overrideLatitude),
                    BigDecimal.valueOf(overrideLongitude)
            );
        }
        if (userId == null || userId <= 0) {
            return new UserLocation(null, null, null);
        }
        return userAddressRepository
                .findByUserIdAndAddressScopeAndBookingTempFalseAndHiddenFalseOrderByDefaultAddressDescUpdatedAtDescIdDesc(userId, "CONSUMER")
                .stream()
                .findFirst()
                .map(address -> new UserLocation(address.getCity(), address.getLatitude(), address.getLongitude()))
                .orElse(new UserLocation(null, null, null));
    }

    private String resolveUserPhone(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        String phone = userRepository.findById(userId).map(user -> user.getPhone()).orElse(null);
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }

    private ServiceApiDtos.ServiceProviderCardResponse toProviderCard(ServiceDiscoveryRepository.ServiceProviderRowView row) {
        return new ServiceApiDtos.ServiceProviderCardResponse(
                row.getProviderId(),
                row.getCategoryId(),
                row.getSubcategoryId(),
                row.getCategoryName(),
                row.getSubcategoryName(),
                row.getProviderName(),
                row.getServiceName(),
                row.getPhotoObjectKey(),
                maskPhone(row.getPhone()),
                row.getVisitingCharge(),
                row.getAvgRating(),
                numberToLong(row.getTotalCompletedJobs()),
                numberToInt(row.getAvailableServiceMen()),
                row.getDistanceKm(),
                row.getLatitude(),
                row.getLongitude(),
                objectToBoolean(row.getOnlineStatus()),
                objectToBoolean(row.getAvailableNow()),
                row.getAvailabilityStatus(),
                numberToInt(row.getActiveBookingCount()),
                numberToInt(row.getRemainingServiceMen()),
                parseServiceItems(row.getServiceItems())
        );
    }

    private static int numberToInt(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private static long numberToLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private static boolean objectToBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "y".equalsIgnoreCase(normalized);
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
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
