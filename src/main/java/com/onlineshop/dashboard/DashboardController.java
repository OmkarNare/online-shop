package com.onlineshop.dashboard;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlineshop.catalog.ProductResponse;
import com.onlineshop.dashboard.DashboardResponse.DashboardType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * These dashboards are the same for every user, so the responses are marked
 * as publicly cacheable and the CDN can serve most of the traffic.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private static final CacheControl DASHBOARD_CACHE_CONTROL =
            CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic();

    private final PopularItemsService popularItemsService;
    private final NewItemsService newItemsService;

    public DashboardController(PopularItemsService popularItemsService, NewItemsService newItemsService) {
        this.popularItemsService = popularItemsService;
        this.newItemsService = newItemsService;
    }

    @GetMapping("/popular")
    public ResponseEntity<DashboardResponse> getPopularItems(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        List<ProductResponse> items = firstN(popularItemsService.getPopularItems(), limit);
        return cacheableResponse(new DashboardResponse(DashboardType.POPULAR, items));
    }

    @GetMapping("/new")
    public ResponseEntity<DashboardResponse> getNewItems(
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        List<ProductResponse> items = firstN(newItemsService.getNewItems(), limit);
        return cacheableResponse(new DashboardResponse(DashboardType.NEW, items));
    }

    private static List<ProductResponse> firstN(List<ProductResponse> items, int limit) {
        return items.size() <= limit ? items : items.subList(0, limit);
    }

    private static ResponseEntity<DashboardResponse> cacheableResponse(DashboardResponse body) {
        return ResponseEntity.ok()
                .cacheControl(DASHBOARD_CACHE_CONTROL)
                .body(body);
    }
}
