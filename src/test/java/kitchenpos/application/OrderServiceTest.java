package kitchenpos.application;

import kitchenpos.domain.*;
import kitchenpos.infra.KitchenridersClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private OrderTableRepository orderTableRepository;
    @Mock
    private KitchenridersClient kitchenridersClient;
    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("타입은 필수이다")
    void requiredType() {
        final var request = new Order();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> orderService.create(request));
    }

    @ParameterizedTest
    @DisplayName("주문 상품은 null 이거나 비어있으면 안된다")
    @NullAndEmptySource
    void requiredOrderLineItems(List<OrderLineItem> orderLineItems) {
        final var request = new Order();
        request.setType(OrderType.EAT_IN);
        request.setOrderLineItems(orderLineItems);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> orderService.create(request));
    }

    @Test
    @DisplayName("주문 상품의 수와 메뉴의 수는 같아야 한다")
    void orderLineItemsSizeAndMenusSizeAreMustBeSame() {
        final var request = new Order();
        request.setType(OrderType.EAT_IN);
        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        request.setOrderLineItems(List.of(orderLineItem));

        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> orderService.create(request));
    }

    @Test
    @DisplayName("매장이 아닌 주문의 주문상품 수량은 음수가 될 수 없다")
    void notEatInOrderNegativeOrderLineItem() {
        final var request = new Order();
        request.setType(OrderType.TAKEOUT);

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        orderLineItem.setQuantity(-1);
        request.setOrderLineItems(List.of(orderLineItem));

        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of(new Menu()));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> orderService.create(request));
    }

    @Test
    @DisplayName("메뉴 중 비노출이 있다면 주문할 수 없다")
    void requiredAllMenuDisplayed() {
        final var request = new Order();
        request.setType(OrderType.EAT_IN);

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        orderLineItem.setQuantity(-1);
        request.setOrderLineItems(List.of(orderLineItem));

        Menu menu = new Menu();
        menu.setDisplayed(false);
        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of(menu));
        when(menuRepository.findById(any())).thenReturn(Optional.of(menu));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.create(request));
    }

    @Test
    @DisplayName("메뉴의 가격과 주문 상품의 가격이 다를수 없다")
    void menuPriceAndOrderLineItemPriceMustBeSame() {
        final var request = new Order();
        request.setType(OrderType.EAT_IN);

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        orderLineItem.setQuantity(1);
        orderLineItem.setPrice(BigDecimal.ZERO);
        request.setOrderLineItems(List.of(orderLineItem));

        Menu menu = new Menu();
        menu.setDisplayed(true);
        menu.setPrice(BigDecimal.TEN);
        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of(menu));
        when(menuRepository.findById(any())).thenReturn(Optional.of(menu));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> orderService.create(request));
    }

    @ParameterizedTest
    @DisplayName("배달 주문은 배달 주소가 필수")
    @NullAndEmptySource
    void requiresDeliveryAddress(final String deliveryAddress) {
        final var request = new Order();
        request.setType(OrderType.DELIVERY);
        request.setDeliveryAddress(deliveryAddress);

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        orderLineItem.setQuantity(1);
        orderLineItem.setPrice(BigDecimal.TEN);
        request.setOrderLineItems(List.of(orderLineItem));

        Menu menu = new Menu();
        menu.setDisplayed(true);
        menu.setPrice(BigDecimal.TEN);
        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of(menu));
        when(menuRepository.findById(any())).thenReturn(Optional.of(menu));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> orderService.create(request));
    }

    @Test
    @DisplayName("정상 배달 주문")
    void deliveryOrder() {
        final var request = new Order();
        request.setType(OrderType.DELIVERY);
        request.setDeliveryAddress("서울시");

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        orderLineItem.setQuantity(1);
        orderLineItem.setPrice(BigDecimal.TEN);
        request.setOrderLineItems(List.of(orderLineItem));

        Menu menu = new Menu();
        menu.setDisplayed(true);
        menu.setPrice(BigDecimal.TEN);
        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of(menu));
        when(menuRepository.findById(any())).thenReturn(Optional.of(menu));
        when(orderRepository.save(any())).then(invocationOnMock -> invocationOnMock.getArgument(0));

        final var order = orderService.create(request);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getDeliveryAddress()).isEqualTo("서울시");
    }

    @Test
    @DisplayName("매장 주문은 채워진 테이블 주문이여야 한다")
    void requiresOccupiedTable() {
        final var request = new Order();
        request.setType(OrderType.EAT_IN);

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        orderLineItem.setQuantity(1);
        orderLineItem.setPrice(BigDecimal.TEN);
        request.setOrderLineItems(List.of(orderLineItem));

        Menu menu = new Menu();
        menu.setDisplayed(true);
        menu.setPrice(BigDecimal.TEN);

        OrderTable orderTable = new OrderTable();
        orderTable.setOccupied(false);

        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of(menu));
        when(menuRepository.findById(any())).thenReturn(Optional.of(menu));
        when(orderTableRepository.findById(any())).thenReturn(Optional.of(orderTable));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.create(request));
    }

    @Test
    @DisplayName("매장 정상 주문")
    void eatInOrder() {
        final var request = new Order();
        request.setType(OrderType.EAT_IN);

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setMenuId(UUID.randomUUID());
        orderLineItem.setQuantity(1);
        orderLineItem.setPrice(BigDecimal.TEN);
        request.setOrderLineItems(List.of(orderLineItem));

        Menu menu = new Menu();
        menu.setDisplayed(true);
        menu.setPrice(BigDecimal.TEN);

        OrderTable orderTable = new OrderTable();
        orderTable.setOccupied(true);

        when(menuRepository.findAllByIdIn(any())).thenReturn(List.of(menu));
        when(menuRepository.findById(any())).thenReturn(Optional.of(menu));
        when(orderTableRepository.findById(any())).thenReturn(Optional.of(orderTable));
        when(orderRepository.save(any())).then(invocationOnMock -> invocationOnMock.getArgument(0));

        final var order = orderService.create(request);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderTable()).isNotNull();
    }

    @Test
    @DisplayName("주문을 수락하기 위해서는 주문이 대기중이어야 한다")
    void requiresWaitingToAccept() {
        final var order = new Order();
        order.setStatus(OrderStatus.COMPLETED);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.accept(UUID.randomUUID()));
    }

    @Test
    @DisplayName("수락시 배달 주문은 배달 대해사에 배달을 요청한다")
    void acceptDeliveryOrderRequestDelivery() {
        final var order = new Order();
        order.setType(OrderType.DELIVERY);
        order.setDeliveryAddress("서울시");
        order.setStatus(OrderStatus.WAITING);

        final var menu = new Menu();
        menu.setPrice(BigDecimal.TEN);

        final var orderLineItem = new OrderLineItem();
        orderLineItem.setQuantity(1);
        orderLineItem.setMenu(menu);

        order.setOrderLineItems(List.of(orderLineItem));

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        Order accepted = orderService.accept(UUID.randomUUID());

        verify(kitchenridersClient, times(1)).requestDelivery(any(UUID.class), any(BigDecimal.class), any(String.class));
        assertThat(accepted.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("정상 주문 수락")
    void accept() {
        final var order = new Order();
        order.setType(OrderType.EAT_IN);
        order.setStatus(OrderStatus.WAITING);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        Order accepted = orderService.accept(UUID.randomUUID());

        assertThat(accepted.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("주문이 제공되기 위해서는 주문이 수락상태여야 한다")
    void requiresAccept() {
        final var order = new Order();
        order.setStatus(OrderStatus.WAITING);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.serve(UUID.randomUUID()));
    }

    @Test
    @DisplayName("정상 제공")
    void serve() {
        final var order = new Order();
        order.setStatus(OrderStatus.ACCEPTED);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        Order served = orderService.serve(UUID.randomUUID());

        assertThat(served.getStatus()).isEqualTo(OrderStatus.SERVED);
    }

    @Test
    @DisplayName("배달을 시작하기 위해서는 배달 주문이여야 한다")
    void requiresOrderTypeDelivery() {
        final var order = new Order();
        order.setType(OrderType.EAT_IN);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.startDelivery(UUID.randomUUID()));
    }

    @Test
    @DisplayName("배달은 시작하기 위해서는 주문이 제공된 상태여야 한다")
    void requiresOrderServed() {
        final var order = new Order();
        order.setType(OrderType.DELIVERY);
        order.setStatus(OrderStatus.ACCEPTED);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.startDelivery(UUID.randomUUID()));
    }

    @Test
    @DisplayName("정상 배달 시작")
    void startDelivery() {
        final var order = new Order();
        order.setType(OrderType.DELIVERY);
        order.setStatus(OrderStatus.SERVED);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        Order started = orderService.startDelivery(UUID.randomUUID());

        assertThat(started.getStatus()).isEqualTo(OrderStatus.DELIVERING);
    }

    @Test
    @DisplayName("배달이 완료되기 위해서는 주문이 배달중이어야 한다")
    void requiresOrderDelivering() {
        final var order = new Order();
        order.setStatus(OrderStatus.SERVED);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.completeDelivery(UUID.randomUUID()));
    }

    @Test
    @DisplayName("정상 배달 완료")
    void completeDelivery() {
        final var order = new Order();
        order.setStatus(OrderStatus.DELIVERING);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        Order completed = orderService.completeDelivery(UUID.randomUUID());

        assertThat(completed.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("배달 주문은 배달된 상태여야 주문 완료가 가능하다")
    void requiresOrderDelivered() {
        final var order = new Order();
        order.setType(OrderType.DELIVERY);
        order.setStatus(OrderStatus.DELIVERING);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.complete(UUID.randomUUID()));
    }

    @ParameterizedTest
    @DisplayName("배달 주문을 제외한 주문은 제공된 상태여야 주문 완료가 가능하다")
    @MethodSource("orderTypeTakeOutAndEatIn")
    void requiresTakeOutAndEatInOrderServed(final OrderType type) {
        final var order = new Order();
        order.setType(type);
        order.setStatus(OrderStatus.ACCEPTED);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatIllegalStateException()
                .isThrownBy(() -> orderService.complete(UUID.randomUUID()));
    }

    @Test
    @DisplayName("매장 주문이 완료시 테이블에 모든 주문이 완료되었다면 치워야 한다")
    void eatInOrderTableClean() {
        final var order = new Order();
        order.setType(OrderType.EAT_IN);
        order.setStatus(OrderStatus.SERVED);

        final var orderTable = new OrderTable();
        orderTable.setOccupied(true);
        orderTable.setNumberOfGuests(1);
        order.setOrderTable(orderTable);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderRepository.existsByOrderTableAndStatusNot(any(), any())).thenReturn(false);

        Order completed = orderService.complete(UUID.randomUUID());

        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderTable.getNumberOfGuests()).isZero();
        assertThat(orderTable.isOccupied()).isFalse();
    }

    @Test
    @DisplayName("매장 주문이 완료시 테이블에 다른 주문이 남아 있다면 치우지 않는다")
    void eatInOrderTableCleanNot() {
        final var order = new Order();
        order.setType(OrderType.EAT_IN);
        order.setStatus(OrderStatus.SERVED);

        final var orderTable = new OrderTable();
        orderTable.setOccupied(true);
        orderTable.setNumberOfGuests(1);
        order.setOrderTable(orderTable);

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderRepository.existsByOrderTableAndStatusNot(any(), any())).thenReturn(true);

        Order completed = orderService.complete(UUID.randomUUID());

        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderTable.getNumberOfGuests()).isNotZero();
        assertThat(orderTable.isOccupied()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("정상 주문 완료")
    @MethodSource("completableOrder")
    void complete(final Order order) {

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        Order completed = orderService.complete(UUID.randomUUID());

        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void findAll() {
    }

    static Stream<Arguments> completableOrder() {
        return Stream.of(
                Arguments.of(new Order() {{
                    setType(OrderType.TAKEOUT);
                    setStatus(OrderStatus.SERVED);
                }}),
                Arguments.of(new Order() {{
                    setType(OrderType.DELIVERY);
                    setStatus(OrderStatus.DELIVERED);
                }})
        );
    }

    static Stream<Arguments> orderTypeTakeOutAndEatIn() {
        return Stream.of(
                Arguments.of(OrderType.TAKEOUT),
                Arguments.of(OrderType.EAT_IN)
        );
    }
}
