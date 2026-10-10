package br.com.fiap.delivery.review;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReviewService reviewService;

    @Test
    void rankingReturnsContractFormat() throws Exception {
        when(reviewService.ranking()).thenReturn(List.of(
                new RankingItem(1L, "House Burger", 4.6, 128),
                new RankingItem(2L, "Pizza", 4.1, 10)));

        mockMvc.perform(get("/reviews/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dishId").value(1))
                .andExpect(jsonPath("$[0].dishName").value("House Burger"))
                .andExpect(jsonPath("$[0].average").value(4.6))
                .andExpect(jsonPath("$[0].count").value(128))
                .andExpect(jsonPath("$[1].dishName").value("Pizza"));
    }
}
