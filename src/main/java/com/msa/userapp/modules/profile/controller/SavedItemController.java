package com.msa.userapp.modules.profile.controller;

import com.msa.userapp.common.api.ApiResponse;
import com.msa.userapp.modules.profile.dto.SaveItemRequest;
import com.msa.userapp.modules.profile.dto.SavedItemResponse;
import com.msa.userapp.modules.profile.service.SavedItemService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/saved-items")
public class SavedItemController {
    private final SavedItemService savedItemService;

    public SavedItemController(SavedItemService savedItemService) {
        this.savedItemService = savedItemService;
    }

    @GetMapping
    public ApiResponse<List<SavedItemResponse>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String savedKind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(savedItemService.list(userId, targetType, savedKind, page, size));
    }

    @PostMapping
    public ApiResponse<SavedItemResponse> save(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SaveItemRequest request
    ) {
        return ApiResponse.success("Saved item updated", savedItemService.save(userId, request));
    }

    @DeleteMapping
    public ApiResponse<Void> remove(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam String targetType,
            @RequestParam Long targetId,
            @RequestParam String savedKind
    ) {
        savedItemService.remove(userId, targetType, targetId, savedKind);
        return ApiResponse.success("Saved item removed");
    }
}
