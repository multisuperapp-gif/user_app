package com.msa.userapp.modules.profile.service;

import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.profile.dto.SaveItemRequest;
import com.msa.userapp.modules.profile.dto.SavedItemResponse;
import com.msa.userapp.modules.shop.common.dto.ShopTypeResponse;
import com.msa.userapp.modules.shop.common.service.ShopCatalogGatewayService;
import com.msa.userapp.persistence.sql.entity.UserSavedItemEntity;
import com.msa.userapp.persistence.sql.repository.UserRepository;
import com.msa.userapp.persistence.sql.repository.UserSavedItemRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedItemService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final UserSavedItemRepository userSavedItemRepository;
    private final ShopCatalogGatewayService shopCatalogGatewayService;

    public SavedItemService(
            UserRepository userRepository,
            UserSavedItemRepository userSavedItemRepository,
            ShopCatalogGatewayService shopCatalogGatewayService
    ) {
        this.userRepository = userRepository;
        this.userSavedItemRepository = userSavedItemRepository;
        this.shopCatalogGatewayService = shopCatalogGatewayService;
    }

    @Transactional(readOnly = true)
    public List<SavedItemResponse> list(Long userId, String targetType, String savedKind, int page, int size) {
        validateUserExists(userId);
        String normalizedTargetType = normalizeNullableEnum(targetType);
        String normalizedSavedKind = normalizeNullableEnum(savedKind);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        List<UserSavedItemRepository.SavedItemRowView> rows = userSavedItemRepository.findSavedItems(
                userId,
                normalizedTargetType,
                normalizedSavedKind,
                PageRequest.of(safePage, safeSize)
        );

        Set<Long> shopTypeIds = new HashSet<>();
        for (UserSavedItemRepository.SavedItemRowView row : rows) {
            if ("SHOP".equals(row.getTargetType()) && row.getShopTypeId() != null) {
                shopTypeIds.add(row.getShopTypeId());
            }
        }
        Map<Long, ShopTypeResponse> shopTypesById = loadSavedShopTypes(shopTypeIds);

        return rows.stream()
                .map(row -> {
                    ShopTypeResponse shopType = row.getShopTypeId() == null ? null : shopTypesById.get(row.getShopTypeId());
                    String subtitle = "SHOP".equals(row.getTargetType())
                            ? (shopType == null ? "Shop" : shopType.name())
                            : row.getSubtitle();
                    String imageObjectKey = "SHOP".equals(row.getTargetType())
                            ? (shopType == null ? null : shopType.bannerObjectKey())
                            : row.getImageObjectKey();
                    return new SavedItemResponse(
                            row.getId(),
                            row.getTargetType(),
                            row.getTargetId(),
                            row.getSavedKind(),
                            row.getTitle(),
                            subtitle,
                            imageObjectKey,
                            row.getPrice(),
                            row.getRating(),
                            row.getCreatedAt()
                    );
                })
                .toList();
    }

    private Map<Long, ShopTypeResponse> loadSavedShopTypes(Collection<Long> shopTypeIds) {
        if (shopTypeIds == null || shopTypeIds.isEmpty()) {
            return Map.of();
        }
        return shopCatalogGatewayService.shopTypes().stream()
                .filter(Objects::nonNull)
                .filter(type -> shopTypeIds.contains(type.id()))
                .collect(java.util.stream.Collectors.toMap(
                        ShopTypeResponse::id,
                        type -> type,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Transactional
    public SavedItemResponse save(Long userId, SaveItemRequest request) {
        validateUserExists(userId);
        String targetType = normalizeRequiredEnum(request.targetType(), "targetType");
        String savedKind = normalizeRequiredEnum(request.savedKind(), "savedKind");
        validateTargetExists(targetType, request.targetId());

        userSavedItemRepository.findByUserIdAndTargetTypeAndTargetIdAndSavedKind(userId, targetType, request.targetId(), savedKind)
                .orElseGet(() -> {
                    UserSavedItemEntity entity = new UserSavedItemEntity();
                    entity.setUserId(userId);
                    entity.setTargetType(targetType);
                    entity.setTargetId(request.targetId());
                    entity.setSavedKind(savedKind);
                    return userSavedItemRepository.save(entity);
                });

        return list(userId, targetType, savedKind, 0, 1).stream()
                .filter(item -> item.targetId().equals(request.targetId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Saved item not found after insert"));
    }

    @Transactional
    public void remove(Long userId, String targetType, Long targetId, String savedKind) {
        validateUserExists(userId);
        String normalizedTargetType = normalizeRequiredEnum(targetType, "targetType");
        String normalizedSavedKind = normalizeRequiredEnum(savedKind, "savedKind");
        int deleted = userSavedItemRepository.deleteByUserIdAndTargetTypeAndTargetIdAndSavedKind(
                userId,
                normalizedTargetType,
                targetId,
                normalizedSavedKind
        );
        if (deleted == 0) {
            throw new NotFoundException("Saved item not found");
        }
    }

    private void validateTargetExists(String targetType, Long targetId) {
        long count = switch (targetType) {
            case "PRODUCT" -> userSavedItemRepository.countActiveProductTarget(targetId);
            case "SHOP" -> userSavedItemRepository.countApprovedShopTarget(targetId);
            case "LABOUR" -> userSavedItemRepository.countApprovedLabourTarget(targetId);
            case "SERVICE_PROVIDER" -> userSavedItemRepository.countApprovedServiceProviderTarget(targetId);
            default -> throw new BadRequestException("Unsupported targetType");
        };
        if (count == 0) {
            throw new NotFoundException("Target not found");
        }
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
    }

    private static String normalizeRequiredEnum(String value, String fieldName) {
        String normalized = normalizeNullableEnum(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return normalized;
    }

    private static String normalizeNullableEnum(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
