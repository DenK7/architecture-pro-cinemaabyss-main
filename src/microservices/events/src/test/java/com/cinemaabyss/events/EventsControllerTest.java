package com.cinemaabyss.events;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventsController.class)
class EventsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private EventPublisher publisher;

    @Test
    void health() throws Exception {
        mvc.perform(get("/api/events/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));
    }

    @Test
    void movieEventIsPublishedToMovieTopic() throws Exception {
        when(publisher.publish(eq(Topics.MOVIE), eq("1"), any())).thenReturn(metadata(Topics.MOVIE, 42));

        mvc.perform(post("/api/events/movie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movie_id\":1,\"title\":\"Inception\",\"action\":\"viewed\",\"user_id\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.partition").value(0))
                .andExpect(jsonPath("$.offset").value(42))
                .andExpect(jsonPath("$.event.id").value("movie-1-viewed"))
                .andExpect(jsonPath("$.event.type").value("movie"))
                .andExpect(jsonPath("$.event.payload.movie_id").value(1))
                .andExpect(jsonPath("$.event.payload.user_id").value(2))
                .andExpect(jsonPath("$.event.payload.rating").doesNotExist());
    }

    @Test
    void userEventIsPublishedToUserTopic() throws Exception {
        when(publisher.publish(eq(Topics.USER), eq("5"), any())).thenReturn(metadata(Topics.USER, 3));

        mvc.perform(post("/api/events/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":5,\"username\":\"neo\",\"action\":\"logged_in\","
                                + "\"timestamp\":\"2026-09-27T10:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.event.id").value("user-5-logged_in"))
                .andExpect(jsonPath("$.event.timestamp").value("2026-09-27T10:00:00Z"));
    }

    @Test
    void paymentEventIsPublishedToPaymentTopic() throws Exception {
        when(publisher.publish(eq(Topics.PAYMENT), eq("7"), any())).thenReturn(metadata(Topics.PAYMENT, 0));

        mvc.perform(post("/api/events/payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payment_id\":7,\"user_id\":5,\"amount\":9.99,\"status\":\"completed\","
                                + "\"timestamp\":\"2026-09-27T10:00:00Z\",\"method_type\":\"credit_card\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.event.id").value("payment-7-completed"))
                .andExpect(jsonPath("$.event.payload.method_type").value("credit_card"));
    }

    @Test
    void missingRequiredFieldIsBadRequest() throws Exception {
        mvc.perform(post("/api/events/payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payment_id\":7,\"user_id\":5,\"status\":\"completed\","
                                + "\"timestamp\":\"2026-09-27T10:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Не заполнено обязательное поле amount"));

        verify(publisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/events/movie").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void kafkaFailureIsServerError() throws Exception {
        when(publisher.publish(anyString(), anyString(), any()))
                .thenThrow(new EventPublishException("Kafka не приняла событие movie-1-viewed", null));

        mvc.perform(post("/api/events/movie").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movie_id\":1,\"title\":\"Inception\",\"action\":\"viewed\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Kafka не приняла событие movie-1-viewed"));
    }

    private static RecordMetadata metadata(String topic, long offset) {
        return new RecordMetadata(new TopicPartition(topic, 0), offset, 0, 0L, 0, 0);
    }
}
