package com.onlineshop.dashboard;

import java.util.List;

import com.onlineshop.catalog.ProductResponse;

public record DashboardResponse(DashboardType type, List<ProductResponse> items) {

    public enum DashboardType {
        POPULAR,
        NEW
    }
}
