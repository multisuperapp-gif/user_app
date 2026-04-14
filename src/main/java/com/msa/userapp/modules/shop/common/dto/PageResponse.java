package com.msa.userapp.modules.shop.common.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        boolean hasMore
) {
}
