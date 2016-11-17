/*
 * Copyright (c) 2016  W.I.S.V. 'Christiaan Huygens'
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ch.wisv.areafiftylan.products.service;

import ch.wisv.areafiftylan.exception.ImmutableOrderException;
import ch.wisv.areafiftylan.exception.PaymentException;
import ch.wisv.areafiftylan.exception.TicketNotFoundException;
import ch.wisv.areafiftylan.products.model.*;
import ch.wisv.areafiftylan.users.model.User;
import ch.wisv.areafiftylan.users.service.UserService;
import com.google.common.base.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private OrderRepository orderRepository;
    private ExpiredOrderRepository expiredOrderRepository;
    private TicketService ticketService;
    private UserService userService;
    private PaymentService paymentService;

    @Value("${a5l.orderLimit}")
    private int ORDER_LIMIT;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository, UserService userService, TicketService ticketService,
                            PaymentService paymentService, ExpiredOrderRepository expiredOrderRepository) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.ticketService = ticketService;
        this.paymentService = paymentService;
        this.expiredOrderRepository = expiredOrderRepository;
    }

    @Override
    public Order getOrderById(Long id) {
        return orderRepository.findOne(id);
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public Collection<Order> findOrdersByUsername(String username) {
        return orderRepository.findAllByUserUsernameIgnoreCase(username);
    }

    @Override
    public List<Order> getOpenOrders(String username) {
        Collection<Order> ordersByUsername = findOrdersByUsername(username);

        return ordersByUsername.stream().
                filter(o -> o.getStatus().equals(OrderStatus.CREATING)).
                collect(Collectors.toList());
    }

    @Override
    public Order create(Long userId, TicketDTO ticketDTO) {
        User user = userService.getUserById(userId);

        for (Order order : orderRepository.findAllByUserUsernameIgnoreCase(user.getUsername())) {
            if (order.getStatus().equals(OrderStatus.CREATING)) {
                throw new IllegalStateException("User already created a new Order: " + order.getId());
            }
        }

        // Request a ticket to see if one is available. If a ticket is sold out, the method ends here due to the
        // exception thrown. Else, we'll get a new ticket to add to the order.
        Ticket ticket = ticketService
                .requestTicketOfType(ticketDTO.getType(), user, ticketDTO.hasPickupService(), ticketDTO.isCHMember());

        Order order = new Order(user);

        order.addTicket(ticket);

        return orderRepository.save(order);
    }

    @Override
    public Order addTicketToOrder(Long orderId, TicketDTO ticketDTO) {
        Order order = orderRepository.getOne(orderId);

        // Check Order status
        if (!order.getStatus().equals(OrderStatus.CREATING)) {
            throw new ImmutableOrderException(orderId);
        }

        // Check amount of Tickets already in Order
        if (order.getTickets().size() >= ORDER_LIMIT) {
            throw new IllegalStateException("Order limit reached");
        }

        User user = order.getUser();

        // Request a ticket to see if one is available. If a ticket is sold out, the method ends here due to the
        // exception thrown. Else, we'll get a new ticket to add to the order.
        Ticket ticket = ticketService
                .requestTicketOfType(ticketDTO.getType(), user, ticketDTO.hasPickupService(), ticketDTO.isCHMember());

        order.addTicket(ticket);
        return orderRepository.save(order);
    }

    @Override
    public Order removeTicketFromOrder(Long orderId, TicketDTO ticketDTO) {
        Order order = orderRepository.getOne(orderId);
        if (order.getStatus().equals(OrderStatus.CREATING)) {

            // Find a Ticket in the order, equal to the given DTO. Throw an exception when the ticket doesn't exist
            Ticket ticket = order.getTickets().stream().filter(isEqualToDTO(ticketDTO)).findFirst()
                    .orElseThrow(TicketNotFoundException::new);

            order.getTickets().remove(ticket);
            ticketService.removeTicket(ticket.getId());

            return orderRepository.save(order);

        } else {
            throw new ImmutableOrderException(orderId);
        }
    }

    private static Predicate<Ticket> isEqualToDTO(TicketDTO ticketDTO) {
        return t -> (t.getType() == ticketDTO.getType()) && (t.isChMember() == ticketDTO.isCHMember()) &&
                (t.hasPickupService() == ticketDTO.hasPickupService());
    }

    @Override
    public String requestPayment(Long orderId) {
        Order order = getOrderById(orderId);
        return paymentService.registerOrder(order);
    }

    @Override
    public Order updateOrderStatus(String orderReference) {

        Order order = paymentService.updateStatus(orderReference);

        // Set all tickets from this Order to valid
        validateTicketsIfPaid(order);
        return order;
    }

    private void validateTicketsIfPaid(Order order) {
        if (order.getStatus().equals(OrderStatus.PAID)) {
            for (Ticket ticket : order.getTickets()) {
                ticketService.validateTicket(ticket.getId());
            }
        }
    }

    @Override
    public Order updateOrderStatus(Long orderId) {
        Order order = orderRepository.findOne(orderId);
        if (!Strings.isNullOrEmpty(order.getReference())) {
            return paymentService.updateStatus(order.getReference());
        } else {
            throw new PaymentException("Order with id " + order + " has not been checked out yet");
        }
    }

    @Override
    public void adminApproveOrder(Long orderId) {
        Order order = orderRepository.findOne(orderId);
        order.setStatus(OrderStatus.PAID);
        validateTicketsIfPaid(order);
        orderRepository.save(order);
    }

    @Override
    public void expireOrder(Order o) {
        orderRepository.delete(o);
        ExpiredOrder eo = new ExpiredOrder(o);
        expiredOrderRepository.save(eo);
        o.getTickets().forEach(t -> ticketService.removeTicket(t.getId()));
    }

    @Override
    public Collection<TicketInformationResponse> getAvailableTickets() {
        Collection<TicketInformationResponse> ticketInfo = new ArrayList<>();

        for (TicketType ticketType : TicketType.values()) {
            if (ticketType.isBuyable() && ticketType != TicketType.TEST) {
                Integer typeSold = ticketService.getNumberSoldOfType(ticketType);
                ticketInfo.add(new TicketInformationResponse(ticketType, typeSold));
            }
        }
        return ticketInfo;
    }
}