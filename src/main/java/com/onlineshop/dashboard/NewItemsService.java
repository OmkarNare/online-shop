package com.onlineshop.dashboard;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.onlineshop.catalog.ProductRepository;
import com.onlineshop.catalog.ProductResponse;

@Service
public class NewItemsService {

    private final ProductRepository productRepository;
    private final DashboardProperties dashboardProperties;

    public NewItemsService(ProductRepository productRepository, DashboardProperties dashboardProperties) {
        this.productRepository = productRepository;
        this.dashboardProperties = dashboardProperties;
    }

    // Always caches the full list; the controller trims it to the requested limit.
    @Cacheable(cacheNames = "newItems", key = "'all'", sync = true)
    public List<ProductResponse> getNewItems() {
        Limit limit = Limit.of(dashboardProperties.maxItems());
        return productRepository.findByActiveTrueOrderByCreatedAtDescIdDesc(limit).stream()
                .map(ProductResponse::from)
                .toList();
    }
}
