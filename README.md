# MSA User App API

Backend service for the customer-facing MSA user app.

Package root: `com.msa.userapp`

## Module Boundaries

- `common`: Shared API response, errors, utilities.
- `config`: Spring configuration and feature flags.
- `security`: User token/session integration.
- `modules.home`: User home feed and discovery sections.
- `modules.shop`: Shop, catalog, restaurant, fashion, footwear, grocery, pharmacy, and gift APIs.
- `modules.shop.restaurant`: Restaurant menu, item customization, and restaurant-only flow.
- `modules.shop.fashion`: Fashion filters, shop profile, fashion listing, and fashion item page flow.
- `modules.shop.footwear`: Footwear copy of fashion flow, kept separate so future changes do not leak into fashion.
- `modules.shop.grocery`: Grocery sections, grocery filters, grocery shop profile, and grocery item flow.
- `modules.shop.pharmacy`: Pharmacy sections and item flow, kept separate from grocery.
- `modules.shop.gift`: Gift store sections, gift listing, and gift item flow.
- `modules.labour`: Labour discovery, profile, single booking, and group booking APIs.
- `modules.service`: Service provider discovery, category filter, and service booking APIs.
- `modules.cart`: One-shop cart and checkout preparation APIs.
- `modules.order`: User order history, delivery OTP, cancellation, and refund status APIs.
- `modules.profile`: User profile, addresses, ratings, and saved items APIs.
- `persistence.sql`: SQL entities/repositories for auth references, orders, payments, labour, service, and system-of-record tables.
- `persistence.mongo`: Future MongoDB read models for high-volume shop catalog/search/home feeds.
- `integration`: HTTP/Feign clients to auth, business, booking/payment, notification, and file services.

## Database Direction

Start with SQL integration for correctness. Keep package boundaries ready for later Mongo read-model split:

- SQL: authentication references, user addresses, labour/service booking, cart, order, payment, refund, settlement.
- Mongo later: shop catalog/search/home feed read models and category-specific item attributes.

Rule for future development: category modules may look similar, but should not share mutable business logic unless the shared contract is intentionally placed in `modules.shop.common`.

## First User App APIs

Public endpoints added for Flutter bootstrap/catalog integration:

- `GET /api/v1/public/home/bootstrap`
- `GET /api/v1/public/shop/types`
- `GET /api/v1/public/shop/categories?shopTypeId=&parentCategoryId=`
- `GET /api/v1/public/shop/products?shopTypeId=&categoryId=&search=&page=&size=`
- `GET /api/v1/public/shop/products/{productId}?variantId=`
- Category route groups:
  - `/api/v1/public/restaurant/**`
  - `/api/v1/public/fashion/**`
  - `/api/v1/public/footwear/**`
  - `/api/v1/public/grocery/**`
  - `/api/v1/public/pharmacy/**`
  - `/api/v1/public/gift/**`

Temporary cart endpoints added before auth-service integration:

- `GET /api/v1/cart`
- `POST /api/v1/cart/items`
- `PATCH /api/v1/cart/items/{itemId}`
- `DELETE /api/v1/cart/items/{itemId}`

Temporary saved-item and checkout endpoints:

- `GET /api/v1/profile/saved-items`
- `POST /api/v1/profile/saved-items`
- `DELETE /api/v1/profile/saved-items`
- `POST /api/v1/orders/checkout-preview`
- `POST /api/v1/orders/place`

Current SQL migrations are:

- `src/main/resources/db/migration/V4__user_app_api_foundation.sql`
- `src/main/resources/db/migration/V5__cart_item_line_key.sql`

They are not applied automatically while `FLYWAY_ENABLED=false`.
