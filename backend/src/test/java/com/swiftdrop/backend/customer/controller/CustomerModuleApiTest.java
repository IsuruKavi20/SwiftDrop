package com.swiftdrop.backend.customer.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Customer Module REST API end to end: HTTP, security, validation, services and the database.
 * Tokens are simulated with the claims the auth service sends ("sub" + the CUSTOMER permissions).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerModuleApiTest {

    @Autowired
    private MockMvc mockMvc;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    /** A CUSTOMER token: the role's permissions from docs/AUTH.md. */
    private static RequestPostProcessor customer(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("name", "Test User")
                        .claim("roles", java.util.List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("parcel:create"), new SimpleGrantedAuthority("parcel:read-own"));
    }

    /** A COURIER token: logged in, but without the customer permissions. */
    private static RequestPostProcessor courier(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("delivery:read-own"), new SimpleGrantedAuthority("delivery:update-status"));
    }

    private void register(UUID userId, String email) throws Exception {
        mockMvc.perform(post("/api/customers/me").with(customer(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"0771234567","address":{"label":"Home","street":"12 Galle Road","city":"Colombo","district":"Colombo"}}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void registrationNeedsAnAddress() throws Exception {
        mockMvc.perform(post("/api/customers/me").with(customer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"0771234567"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.address").value("Please add your address"));
    }

    @Test
    void theFirstAddressIsSavedAsTheDefault() throws Exception {
        register(alice, "alice@example.com");

        mockMvc.perform(get("/api/customers/me/addresses").with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Home"))
                .andExpect(jsonPath("$[0].defaultAddress").value(true));
    }

    @Test
    void anIncompleteAddressIsRefused() throws Exception {
        mockMvc.perform(post("/api/customers/me").with(customer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"0771234567","address":{"label":"Home","city":"Colombo","district":"Colombo"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['address.street']").exists());
    }

    private static final String BOOKING = """
            {
              "deliveryType": "EXPRESS",
              "pickup":      {"contactName":"Alice Fernando","contactPhone":"0771111111","street":"1 Main St",
                              "city":"Colombo","district":"Colombo","postalCode":"00100"},
              "destination": {"contactName":"Kamal Perera","contactPhone":"0719999999","street":"5 Temple Rd",
                              "city":"Kandy","district":"Kandy"},
              "parcels": [{"description":"Books","weightKg":2.5,"lengthCm":30,"widthCm":20,"heightCm":10}],
              "specialInstructions": "Call before arriving"
            }""";

    /** Books a delivery for the user and returns the response JSON. */
    private String book(UUID userId) throws Exception {
        return mockMvc.perform(post("/api/customers/me/deliveries").with(customer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    // ---- Security ----

    @Test
    void customerEndpointsNeedALogin() throws Exception {
        mockMvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/customers/me/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/customers/me/deliveries").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersWithoutCustomerPermissionsAreForbidden() throws Exception {
        UUID courierId = UUID.randomUUID();
        mockMvc.perform(get("/api/customers/me").with(courier(courierId))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/me/deliveries").with(courier(courierId))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/customers/me/deliveries").with(courier(courierId))
                .contentType(MediaType.APPLICATION_JSON).content(BOOKING)).andExpect(status().isForbidden());
    }

    @Test
    void aCustomerCannotSeeOrConfirmAnotherCustomersDelivery() throws Exception {
        register(alice, "alice@example.com");
        register(bob, "bob@example.com");
        String booked = book(alice);
        Integer id = JsonPath.read(booked, "$.id");
        String trackingNumber = JsonPath.read(booked, "$.trackingNumber");

        mockMvc.perform(get("/api/customers/me/deliveries/{id}", id).with(customer(bob))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/customers/me/deliveries/{id}/confirm", id).with(customer(bob))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/customers/me/tracking/{tn}", trackingNumber).with(customer(bob))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/customers/me/deliveries").with(customer(bob))).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- Registration and profile ----

    @Test
    void registersAndUpdatesTheProfile() throws Exception {
        register(alice, "alice@example.com");

        mockMvc.perform(get("/api/customers/me").with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test User"));
        mockMvc.perform(put("/api/customers/me").with(customer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+94773333333"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+94773333333"));
    }

    @Test
    void registeringTwiceIsAConflict() throws Exception {
        register(alice, "alice@example.com");
        mockMvc.perform(post("/api/customers/me").with(customer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"0771234567","address":{"label":"Home","street":"12 Galle Road","city":"Colombo","district":"Colombo"}}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You are already registered as a customer"));
    }

    @Test
    void anUnregisteredUserIsToldToRegister() throws Exception {
        mockMvc.perform(get("/api/customers/me/dashboard").with(customer(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Customer profile not found. Please complete registration first."));
    }

    @Test
    void invalidFieldsAreListedForTheForm() throws Exception {
        register(alice, "alice@example.com");
        mockMvc.perform(post("/api/customers/me/deliveries").with(customer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deliveryType":"EXPRESS",
                                 "pickup":{"contactName":"A","contactPhone":"12ab","street":"s","city":"c","district":"d"},
                                 "parcels":[{"description":"Books","weightKg":0,"lengthCm":1,"widthCm":1,"heightCm":1}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['pickup.contactPhone']").exists())
                .andExpect(jsonPath("$.errors.destination").exists())
                .andExpect(jsonPath("$.errors['parcels[0].weightKg']").exists());
    }

    // ---- Saved addresses ----

    @Test
    void managesSavedAddresses() throws Exception {
        register(alice, "alice@example.com");
        String created = mockMvc.perform(post("/api/customers/me/addresses").with(customer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Office","street":"2 Lake Rd","city":"Colombo","district":"Colombo"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(false))   // the registration address stays the default
                .andReturn().getResponse().getContentAsString();
        Integer addressId = JsonPath.read(created, "$.id");

        mockMvc.perform(put("/api/customers/me/addresses/{id}", addressId).with(customer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Office","street":"3 Lake Rd","city":"Colombo","district":"Colombo","defaultAddress":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.street").value("3 Lake Rd"));
        mockMvc.perform(delete("/api/customers/me/addresses/{id}", addressId).with(customer(alice)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/me/addresses").with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Home"));
    }

    // ---- Booking, My Deliveries, dashboard, tracking ----

    @Test
    void booksReviewsAndConfirmsADelivery() throws Exception {
        register(alice, "alice@example.com");
        String booked = book(alice);
        Integer id = JsonPath.read(booked, "$.id");

        mockMvc.perform(get("/api/customers/me/deliveries/{id}", id).with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.destination.contactName").value("Kamal Perera"))
                .andExpect(jsonPath("$.parcels[0].weightKg").value(2.5));
        mockMvc.perform(post("/api/customers/me/deliveries/{id}/confirm", id).with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.trackingEvents.length()").value(2));
        mockMvc.perform(post("/api/customers/me/deliveries/{id}/confirm", id).with(customer(alice)))
                .andExpect(status().isConflict());
    }

    @Test
    void listsSearchesAndSummarisesDeliveries() throws Exception {
        register(alice, "alice@example.com");
        book(alice);
        book(alice);

        mockMvc.perform(get("/api/customers/me/deliveries").param("search", "kandy").param("size", "1").with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].destinationCity").value("Kandy"));
        mockMvc.perform(get("/api/customers/me/deliveries").param("status", "DELIVERED").with(customer(alice)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/customers/me/deliveries").param("status", "NOT_A_STATUS").with(customer(alice)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/customers/me/dashboard").with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeliveries").value(2))
                .andExpect(jsonPath("$.countsByStatus.PENDING").value(2))
                .andExpect(jsonPath("$.recentDeliveries.length()").value(2));
    }

    @Test
    void publicTrackingNeedsNoLoginAndHidesPrivateData() throws Exception {
        register(alice, "alice@example.com");
        String booked = book(alice);
        String trackingNumber = JsonPath.read(booked, "$.trackingNumber");
        String token = JsonPath.read(booked, "$.publicTrackingToken");

        String byNumber = mockMvc.perform(get("/api/public/tracking/{tn}", trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.toCity").value("Kandy"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/public/tracking/link/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber));

        org.assertj.core.api.Assertions.assertThat(byNumber)
                .doesNotContain("Kamal", "Alice", "0719999999", "0771111111", "Temple Rd", "Books", token);
        mockMvc.perform(get("/api/public/tracking/link/{token}", "guessed")).andExpect(status().isNotFound());
        // Only reading is public
        mockMvc.perform(post("/api/public/tracking/{tn}", trackingNumber)).andExpect(status().isUnauthorized());
    }

    @Test
    void customerTrackingShowsTheTimeline() throws Exception {
        register(alice, "alice@example.com");
        String trackingNumber = JsonPath.read(book(alice), "$.trackingNumber");

        mockMvc.perform(get("/api/customers/me/tracking/{tn}", trackingNumber.toLowerCase()).with(customer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].status", hasItem("PENDING")))
                .andExpect(jsonPath("$.events[*].status", not(hasItem("DELIVERED"))));
    }
}
