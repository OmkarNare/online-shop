package com.onlineshop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.onlineshop.support.IntegrationTestBase;

class ProductApiIT extends IntegrationTestBase {

    private static final String CONSOLE = """
            {"sku":"CONSOLE","name":"Console","category":"gaming","price":499.99,"initialStock":25,"highDemand":true}
            """;

    @Test
    void createsProductWithItsStockRow() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(CONSOLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("CONSOLE"))
                .andExpect(jsonPath("$.highDemand").value(true))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-01T09:00:00Z"));

        assertThat(jdbc.queryForObject("SELECT on_hand FROM inventory", Integer.class)).isEqualTo(25);
    }

    @Test
    void duplicateSkuIsAConflict() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(CONSOLE))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(CONSOLE))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidProductIsRejected() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\",\"name\":\"x\",\"category\":\"c\",\"price\":-1,\"initialStock\":1}"))
                .andExpect(status().isBadRequest());
    }
}
