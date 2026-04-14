package com.msa.userapp.modules.cart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.userapp.common.exception.BadRequestException;
import com.msa.userapp.common.exception.NotFoundException;
import com.msa.userapp.modules.cart.dto.CartAddItemRequest;
import com.msa.userapp.modules.cart.dto.CartItemResponse;
import com.msa.userapp.modules.cart.dto.CartResponse;
import com.msa.userapp.modules.cart.dto.CartUpdateItemRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CartService {
    private static final String DEFAULT_CURRENCY = "INR";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CartService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CartResponse getActiveCart(Long userId) {
        validateUserExists(userId);
        CartRow cart = findActiveCart(userId);
        if (cart == null) {
            return new CartResponse(null, userId, null, null, DEFAULT_CURRENCY, null, 0, BigDecimal.ZERO, List.of());
        }
        return loadCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(Long userId, CartAddItemRequest request) {
        validateUserExists(userId);
        ResolvedProduct product = resolveProduct(request.productId(), request.variantId());
        OptionSelection optionSelection = resolveOptionSelection(request.productId(), request.optionIds());
        String lineKey = buildLineKey(product.variantId(), optionSelection.optionIds(), request.cookingRequest());

        CartRow cart = findActiveCart(userId);
        if (cart == null) {
            cart = createCart(userId, product.shopId(), product.cartContext());
        } else if (!Objects.equals(cart.shopId(), product.shopId())) {
            throw new BadRequestException("Only one shop can stay active in the cart at a time");
        }

        Long existingItemId = findCartItemIdByLineKey(cart.cartId(), lineKey);
        BigDecimal unitPrice = product.sellingPrice().add(optionSelection.priceDelta());
        if (existingItemId != null) {
            int currentQuantity = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM cart_items WHERE id = :itemId",
                    Map.of("itemId", existingItemId),
                    Integer.class
            );
            updateCartItemInternal(
                    existingItemId,
                    cart.cartId(),
                    product.productId(),
                    product.variantId(),
                    currentQuantity + request.quantity(),
                    lineKey,
                    unitPrice,
                    optionSelection,
                    request.cookingRequest(),
                    product
            );
        } else {
            insertCartItem(
                    cart.cartId(),
                    product,
                    request.quantity(),
                    lineKey,
                    unitPrice,
                    optionSelection,
                    request.cookingRequest()
            );
        }
        touchCart(cart.cartId());
        return loadCartResponse(requireCart(userId));
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, CartUpdateItemRequest request) {
        validateUserExists(userId);
        CartItemOwnershipRow ownedItem = findOwnedCartItem(userId, itemId);
        OptionSelection optionSelection = resolveOptionSelection(ownedItem.productId(), request.optionIds());
        String lineKey = buildLineKey(ownedItem.variantId(), optionSelection.optionIds(), request.cookingRequest());
        ResolvedProduct product = resolveProduct(ownedItem.productId(), ownedItem.variantId());
        BigDecimal unitPrice = product.sellingPrice().add(optionSelection.priceDelta());

        Long existingItemId = findCartItemIdByLineKey(ownedItem.cartId(), lineKey);
        if (existingItemId != null && !existingItemId.equals(itemId)) {
            int mergedQuantity = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM cart_items WHERE id = :itemId",
                    Map.of("itemId", existingItemId),
                    Integer.class
            ) + request.quantity();
            updateCartItemInternal(
                    existingItemId,
                    ownedItem.cartId(),
                    product.productId(),
                    product.variantId(),
                    mergedQuantity,
                    lineKey,
                    unitPrice,
                    optionSelection,
                    request.cookingRequest(),
                    product
            );
            jdbcTemplate.update("DELETE FROM cart_items WHERE id = :itemId", Map.of("itemId", itemId));
        } else {
            updateCartItemInternal(
                    itemId,
                    ownedItem.cartId(),
                    product.productId(),
                    product.variantId(),
                    request.quantity(),
                    lineKey,
                    unitPrice,
                    optionSelection,
                    request.cookingRequest(),
                    product
            );
        }

        touchCart(ownedItem.cartId());
        return loadCartResponse(requireCart(userId));
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        validateUserExists(userId);
        CartItemOwnershipRow ownedItem = findOwnedCartItem(userId, itemId);
        jdbcTemplate.update("DELETE FROM cart_items WHERE id = :itemId", Map.of("itemId", itemId));
        touchCart(ownedItem.cartId());
        return loadCartResponse(requireCart(userId));
    }

    private void insertCartItem(
            Long cartId,
            ResolvedProduct product,
            int quantity,
            String lineKey,
            BigDecimal unitPrice,
            OptionSelection optionSelection,
            String cookingRequest
    ) {
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cartId", cartId)
                .addValue("shopId", product.shopId())
                .addValue("productId", product.productId())
                .addValue("variantId", product.variantId())
                .addValue("lineKey", lineKey)
                .addValue("quantity", quantity)
                .addValue("productNameSnapshot", product.productName())
                .addValue("variantNameSnapshot", product.variantName())
                .addValue("selectedOptionsJson", optionSelection.jsonPayload())
                .addValue("cookingRequest", normalizeText(cookingRequest))
                .addValue("imageFileIdSnapshot", product.imageFileId())
                .addValue("unitPriceSnapshot", unitPrice)
                .addValue("lineTotal", lineTotal);

        jdbcTemplate.update("""
                INSERT INTO cart_items (
                    cart_id,
                    shop_id,
                    product_id,
                    variant_id,
                    line_key,
                    quantity,
                    product_name_snapshot,
                    variant_name_snapshot,
                    selected_options_json,
                    cooking_request,
                    image_file_id_snapshot,
                    unit_price_snapshot,
                    line_total
                ) VALUES (
                    :cartId,
                    :shopId,
                    :productId,
                    :variantId,
                    :lineKey,
                    :quantity,
                    :productNameSnapshot,
                    :variantNameSnapshot,
                    :selectedOptionsJson,
                    :cookingRequest,
                    :imageFileIdSnapshot,
                    :unitPriceSnapshot,
                    :lineTotal
                )
                """, params);
    }

    private void updateCartItemInternal(
            Long itemId,
            Long cartId,
            Long productId,
            Long variantId,
            int quantity,
            String lineKey,
            BigDecimal unitPrice,
            OptionSelection optionSelection,
            String cookingRequest,
            ResolvedProduct product
    ) {
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("cartId", cartId)
                .addValue("productId", productId)
                .addValue("variantId", variantId)
                .addValue("lineKey", lineKey)
                .addValue("quantity", quantity)
                .addValue("productNameSnapshot", product.productName())
                .addValue("variantNameSnapshot", product.variantName())
                .addValue("selectedOptionsJson", optionSelection.jsonPayload())
                .addValue("cookingRequest", normalizeText(cookingRequest))
                .addValue("imageFileIdSnapshot", product.imageFileId())
                .addValue("unitPriceSnapshot", unitPrice)
                .addValue("lineTotal", lineTotal);
        jdbcTemplate.update("""
                UPDATE cart_items
                SET product_id = :productId,
                    variant_id = :variantId,
                    line_key = :lineKey,
                    quantity = :quantity,
                    product_name_snapshot = :productNameSnapshot,
                    variant_name_snapshot = :variantNameSnapshot,
                    selected_options_json = :selectedOptionsJson,
                    cooking_request = :cookingRequest,
                    image_file_id_snapshot = :imageFileIdSnapshot,
                    unit_price_snapshot = :unitPriceSnapshot,
                    line_total = :lineTotal
                WHERE id = :itemId
                  AND cart_id = :cartId
                """, params);
    }

    private CartResponse loadCartResponse(CartRow cart) {
        List<CartItemResponse> items = jdbcTemplate.query("""
                SELECT
                    ci.id,
                    ci.line_key,
                    ci.product_id,
                    ci.variant_id,
                    ci.product_name_snapshot,
                    ci.variant_name_snapshot,
                    ci.quantity,
                    ci.unit_price_snapshot,
                    ci.line_total,
                    ci.selected_options_json,
                    ci.cooking_request,
                    image_file.object_key AS image_object_key
                FROM cart_items ci
                LEFT JOIN files image_file ON image_file.id = ci.image_file_id_snapshot
                WHERE ci.cart_id = :cartId
                ORDER BY ci.id ASC
                """, Map.of("cartId", cart.cartId()), (rs, rowNum) -> new CartItemResponse(
                rs.getLong("id"),
                rs.getString("line_key"),
                nullableLong(rs, "product_id"),
                rs.getLong("variant_id"),
                rs.getString("product_name_snapshot"),
                rs.getString("variant_name_snapshot"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price_snapshot"),
                rs.getBigDecimal("line_total"),
                rs.getString("image_object_key"),
                extractOptionNames(rs.getString("selected_options_json")),
                rs.getString("cooking_request")
        ));
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemCount = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(
                cart.cartId(),
                cart.userId(),
                cart.shopId(),
                cart.shopName(),
                cart.currencyCode(),
                cart.cartContext(),
                itemCount,
                subtotal,
                items
        );
    }

    private CartRow requireCart(Long userId) {
        CartRow cart = findActiveCart(userId);
        if (cart == null) {
            throw new NotFoundException("Active cart not found");
        }
        return cart;
    }

    private CartRow findActiveCart(Long userId) {
        List<CartRow> rows = jdbcTemplate.query("""
                SELECT
                    c.id,
                    c.user_id,
                    c.shop_id,
                    s.shop_name,
                    c.currency_code,
                    c.cart_context
                FROM carts c
                LEFT JOIN shops s ON s.id = c.shop_id
                WHERE c.user_id = :userId
                  AND c.cart_status = 'ACTIVE'
                ORDER BY c.updated_at DESC, c.id DESC
                LIMIT 1
                """, Map.of("userId", userId), (rs, rowNum) -> new CartRow(
                rs.getLong("id"),
                rs.getLong("user_id"),
                nullableLong(rs, "shop_id"),
                rs.getString("shop_name"),
                rs.getString("currency_code"),
                rs.getString("cart_context")
        ));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private CartRow createCart(Long userId, Long shopId, String cartContext) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("shopId", shopId)
                .addValue("currencyCode", DEFAULT_CURRENCY)
                .addValue("cartContext", cartContext);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO carts (
                    user_id,
                    shop_id,
                    currency_code,
                    cart_context,
                    cart_status
                ) VALUES (
                    :userId,
                    :shopId,
                    :currencyCode,
                    :cartContext,
                    'ACTIVE'
                )
                """, params, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create cart");
        }
        return new CartRow(key.longValue(), userId, shopId, null, DEFAULT_CURRENCY, cartContext);
    }

    private CartItemOwnershipRow findOwnedCartItem(Long userId, Long itemId) {
        List<CartItemOwnershipRow> rows = jdbcTemplate.query("""
                SELECT
                    ci.id,
                    ci.cart_id,
                    ci.product_id,
                    ci.variant_id
                FROM cart_items ci
                INNER JOIN carts c ON c.id = ci.cart_id
                WHERE ci.id = :itemId
                  AND c.user_id = :userId
                  AND c.cart_status = 'ACTIVE'
                """, Map.of("itemId", itemId, "userId", userId), (rs, rowNum) -> new CartItemOwnershipRow(
                rs.getLong("id"),
                rs.getLong("cart_id"),
                nullableLong(rs, "product_id"),
                rs.getLong("variant_id")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Cart item not found");
        }
        return rows.getFirst();
    }

    private Long findCartItemIdByLineKey(Long cartId, String lineKey) {
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM cart_items
                WHERE cart_id = :cartId
                  AND line_key = :lineKey
                LIMIT 1
                """, Map.of("cartId", cartId, "lineKey", lineKey), (rs, rowNum) -> rs.getLong("id"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void touchCart(Long cartId) {
        jdbcTemplate.update("UPDATE carts SET updated_at = CURRENT_TIMESTAMP WHERE id = :cartId", Map.of("cartId", cartId));
    }

    private void validateUserExists(Long userId) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE id = :userId",
                Map.of("userId", userId),
                Integer.class
        );
        if (exists == null || exists == 0) {
            throw new NotFoundException("User not found");
        }
    }

    private ResolvedProduct resolveProduct(Long productId, Long requestedVariantId) {
        List<ResolvedProduct> rows = jdbcTemplate.query("""
                SELECT
                    p.id AS product_id,
                    p.name AS product_name,
                    s.id AS shop_id,
                    st.normalized_name AS shop_type_name,
                    pv.id AS variant_id,
                    pv.variant_name,
                    pv.selling_price,
                    image_file.id AS image_file_id
                FROM products p
                INNER JOIN shops s ON s.id = p.shop_id
                LEFT JOIN shop_types st ON st.id = s.shop_type_id
                INNER JOIN product_variants pv ON pv.product_id = p.id AND pv.is_active = 1
                LEFT JOIN product_images pi ON pi.product_id = p.id AND pi.is_primary = 1
                LEFT JOIN files image_file ON image_file.id = pi.file_id
                WHERE p.id = :productId
                  AND p.is_active = 1
                ORDER BY
                  CASE WHEN :variantId IS NOT NULL AND pv.id = :variantId THEN 0 ELSE 1 END,
                  pv.is_default DESC,
                  pv.sort_order ASC,
                  pv.id ASC
                """, new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("variantId", requestedVariantId), (rs, rowNum) -> new ResolvedProduct(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getLong("shop_id"),
                mapCartContext(rs.getString("shop_type_name")),
                rs.getLong("variant_id"),
                rs.getString("variant_name"),
                rs.getBigDecimal("selling_price"),
                nullableLong(rs, "image_file_id")
        ));
        if (rows.isEmpty()) {
            throw new NotFoundException("Product or variant not found");
        }
        if (requestedVariantId != null && rows.stream().noneMatch(row -> row.variantId().equals(requestedVariantId))) {
            throw new NotFoundException("Variant not found");
        }
        return rows.getFirst();
    }

    private OptionSelection resolveOptionSelection(Long productId, List<Long> optionIds) {
        List<Long> normalizedOptionIds = optionIds == null
                ? List.of()
                : optionIds.stream().filter(Objects::nonNull).distinct().sorted(Comparator.naturalOrder()).toList();
        if (normalizedOptionIds.isEmpty()) {
            return new OptionSelection(List.of(), List.of(), BigDecimal.ZERO, "{\"optionIds\":[],\"optionNames\":[]}");
        }

        List<OptionRow> rows = jdbcTemplate.query("""
                SELECT
                    po.id,
                    po.option_name,
                    po.price_delta,
                    pog.product_id
                FROM product_options po
                INNER JOIN product_option_groups pog ON pog.id = po.option_group_id
                WHERE po.id IN (:optionIds)
                  AND po.is_active = 1
                  AND pog.product_id = :productId
                  AND pog.is_active = 1
                """, new MapSqlParameterSource()
                .addValue("optionIds", normalizedOptionIds)
                .addValue("productId", productId), (rs, rowNum) -> new OptionRow(
                rs.getLong("id"),
                rs.getString("option_name"),
                rs.getBigDecimal("price_delta")
        ));
        if (rows.size() != normalizedOptionIds.size()) {
            throw new BadRequestException("One or more selected options are invalid");
        }

        List<String> optionNames = rows.stream().sorted(Comparator.comparing(OptionRow::id)).map(OptionRow::optionName).toList();
        BigDecimal delta = rows.stream()
                .map(OptionRow::priceDelta)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String jsonPayload = writeOptionPayload(normalizedOptionIds, optionNames);
        return new OptionSelection(normalizedOptionIds, optionNames, delta, jsonPayload);
    }

    private String writeOptionPayload(List<Long> optionIds, List<String> optionNames) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("optionIds", optionIds);
        payload.put("optionNames", optionNames);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize cart options", exception);
        }
    }

    private List<String> extractOptionNames(String selectedOptionsJson) {
        if (!StringUtils.hasText(selectedOptionsJson)) {
            return List.of();
        }
        try {
            Object raw = objectMapper.readValue(selectedOptionsJson, Map.class).get("optionNames");
            if (raw instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String buildLineKey(Long variantId, List<Long> optionIds, String cookingRequest) {
        StringBuilder builder = new StringBuilder();
        builder.append(variantId);
        builder.append('|');
        builder.append(String.join(",", optionIds.stream().map(String::valueOf).toList()));
        builder.append('|');
        builder.append(normalizeText(cookingRequest));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < 16; index++) {
                hex.append(String.format("%02x", hash[index]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String mapCartContext(String normalizedShopTypeName) {
        if (!StringUtils.hasText(normalizedShopTypeName)) {
            return "SHOP";
        }
        return switch (normalizedShopTypeName.toLowerCase()) {
            case "restaurant" -> "RESTAURANT";
            case "fashion" -> "FASHION";
            case "footwear" -> "FOOTWEAR";
            case "grocery" -> "GROCERY";
            case "pharmacy" -> "PHARMACY";
            case "gift" -> "GIFT";
            default -> "SHOP";
        };
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private record CartRow(
            Long cartId,
            Long userId,
            Long shopId,
            String shopName,
            String currencyCode,
            String cartContext
    ) {
    }

    private record CartItemOwnershipRow(
            Long itemId,
            Long cartId,
            Long productId,
            Long variantId
    ) {
    }

    private record ResolvedProduct(
            Long productId,
            String productName,
            Long shopId,
            String cartContext,
            Long variantId,
            String variantName,
            BigDecimal sellingPrice,
            Long imageFileId
    ) {
    }

    private record OptionRow(
            Long id,
            String optionName,
            BigDecimal priceDelta
    ) {
    }

    private record OptionSelection(
            List<Long> optionIds,
            List<String> optionNames,
            BigDecimal priceDelta,
            String jsonPayload
    ) {
    }
}
