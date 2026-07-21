package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.OrderDto;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.dto.facade.OrderWithDetailsDto;
import com.kawashreh.ecommerce.frontend.facade.OrderFacade;
import com.kawashreh.ecommerce.frontend.facade.ProfileFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Regression test for issue #4: /orders threw a SpringEL property-not-found error for
 * any non-empty order list because the controller put List<OrderWithDetailsDto> on the
 * model while order/orders.html dereferenced each element as a flat OrderDto.
 *
 * OrderController.orders() must flatten to List<OrderDto> under "orders", mirroring how
 * orderDetail() already unwraps OrderWithDetailsDto.getOrder() onto the model.
 *
 * Not executed against Docker/Testcontainers - this is a plain Mockito unit test with no
 * Spring context.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private ProfileFacade profileFacade;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private OrderController orderController;

    @Test
    void orders_flattensOrderWithDetailsDtoListToFlatOrderDtoList() {
        UUID buyerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(sessionManager.isAuthenticated(request)).thenReturn(true);
        when(sessionManager.getUsername(request)).thenReturn("alice");
        when(profileFacade.getUserByUsername("alice"))
                .thenReturn(UserDto.builder().id(buyerId).username("alice").build());

        OrderDto flatOrder = OrderDto.builder()
                .id(orderId)
                .status("CONFIRMED")
                .createdAt(Instant.now())
                .selectedItems(Collections.emptyList())
                .build();
        OrderWithDetailsDto withPayment = OrderWithDetailsDto.builder()
                .order(flatOrder)
                .payment(null)
                .build();
        // Defensive case: a per-order payment/order lookup that failed downstream and
        // left order == null must not blow up the view - it should be filtered out.
        OrderWithDetailsDto missingOrder = OrderWithDetailsDto.builder()
                .order(null)
                .payment(null)
                .build();

        when(orderFacade.getOrdersWithPayments(buyerId))
                .thenReturn(Arrays.asList(withPayment, missingOrder));

        Model model = new ExtendedModelMap();
        String view = orderController.orders(model, request);

        assertEquals("order/orders", view);
        Object ordersAttribute = model.getAttribute("orders");
        assertInstanceOf(List.class, ordersAttribute);

        @SuppressWarnings("unchecked")
        List<OrderDto> orders = (List<OrderDto>) ordersAttribute;
        assertEquals(1, orders.size());
        assertEquals(orderId, orders.get(0).getId());
        assertEquals("CONFIRMED", orders.get(0).getStatus());
    }

    @Test
    void orders_nullFacadeResultRendersEmptyList() {
        UUID buyerId = UUID.randomUUID();

        when(sessionManager.isAuthenticated(request)).thenReturn(true);
        when(sessionManager.getUsername(request)).thenReturn("bob");
        when(profileFacade.getUserByUsername("bob"))
                .thenReturn(UserDto.builder().id(buyerId).username("bob").build());
        when(orderFacade.getOrdersWithPayments(buyerId)).thenReturn(null);

        Model model = new ExtendedModelMap();
        String view = orderController.orders(model, request);

        assertEquals("order/orders", view);
        Object ordersAttribute = model.getAttribute("orders");
        assertInstanceOf(List.class, ordersAttribute);
        assertTrue(((List<?>) ordersAttribute).isEmpty());
    }

    @Test
    void orders_redirectsToLoginWhenUnauthenticated() {
        when(sessionManager.isAuthenticated(request)).thenReturn(false);
        when(sessionManager.getUsername(request)).thenReturn(null);

        Model model = new ExtendedModelMap();
        String view = orderController.orders(model, request);

        assertEquals("redirect:/login", view);
        assertFalse(model.containsAttribute("orders"));
    }
}
