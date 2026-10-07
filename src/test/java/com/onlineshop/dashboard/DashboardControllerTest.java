package com.onlineshop.dashboard;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.onlineshop.catalog.ProductResponse;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PopularItemsService popularItems;
    @MockitoBean NewItemsService newItems;

    @Test
    void popularReturnsTheRequestedNumberOfItemsWithPublicCacheHeaders() throws Exception {
        when(popularItems.getPopularItems()).thenReturn(items(5));

        mvc.perform(get("/api/v1/dashboards/popular").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=10, public"))
                .andExpect(jsonPath("$.type").value("POPULAR"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(1));
    }

    @Test
    void newItemsDefaultTo20() throws Exception {
        when(newItems.getNewItems()).thenReturn(items(30));

        mvc.perform(get("/api/v1/dashboards/new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NEW"))
                .andExpect(jsonPath("$.items.length()").value(20));
    }

    @Test
    void limitAboveFiftyIsRejected() throws Exception {
        mvc.perform(get("/api/v1/dashboards/new").param("limit", "51"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(newItems);
    }

    private static List<ProductResponse> items(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> new ProductResponse(id, "SKU-" + id, "Product " + id, "test",
                        new BigDecimal("1.00"), "GBP", false, Instant.parse("2026-10-01T10:00:00Z")))
                .toList();
    }
}
