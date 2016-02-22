package ch.wisv.areafiftylan.service;

import ch.wisv.areafiftylan.dto.TicketDTO;
import ch.wisv.areafiftylan.dto.TicketInformationResponse;
import ch.wisv.areafiftylan.exception.TicketUnavailableException;
import ch.wisv.areafiftylan.model.Order;
import ch.wisv.areafiftylan.model.Ticket;
import ch.wisv.areafiftylan.model.User;
import ch.wisv.areafiftylan.model.util.TicketType;

import java.util.Collection;
import java.util.List;

public interface OrderService {

    Order getOrderById(Long id);

    List<Order> getAllOrders();

    Collection<Order> findOrdersByUsername(String username);

    List<Order> getOpenOrders(String username);

    /**
     * Create an order with at least one Ticket.
     *
     * @param userId    User which orders the ticket
     * @param ticketDTO The ticket that is being ordered
     *
     * @return The created order
     */
    Order create(Long userId, TicketDTO ticketDTO);

    /**
     * Add a ticket to an order. Checks if a ticket is available first
     *
     * @param orderId   Order to which the ticket should be added
     * @param ticketDTO DTO of the ticket to be added
     *
     * @return The updated Order
     */
    Order addTicketToOrder(Long orderId, TicketDTO ticketDTO);

    /**
     * Removes a ticket with the given DTO from an order. Throws a NotFoundException when a ticket with such a DTO can't be found
     * @param orderId Order from which the Ticket should be removed
     * @param ticketDTO Ticket which should be removed from the Order
     * @return The modified Order
     */
    Order removeTicketFromOrder(Long orderId, TicketDTO ticketDTO);

    /**
     * Check if a ticket is available, and return when it is. When a ticket is unavailable (sold out for instance) a
     * TicketUnavailableException is thrown
     *
     * @param type          Type of the Ticket requested
     * @param owner         User that wants the Ticket
     * @param pickupService If the Ticket includes the pickupService
     *
     * @return The requested ticket, if available
     *
     * @throws TicketUnavailableException If the requested ticket is sold out.
     */
    Ticket requestTicketOfType(TicketType type, User owner, boolean pickupService, boolean chMember)
            throws TicketUnavailableException;

    /**
     * Register the order with the payment provider
     *
     * @param orderId The order to be checked out
     *
     * @return The URL for payment
     */
    String requestPayment(Long orderId);

    Order updateOrderStatus(String orderReference);

    Order updateOrderStatus(Long orderId);

    /**
     * Expire an order which will remove the order from the orders table and enter a relevant
     * entry in the expired orders table
     * @param o The order to expire
     */
    void expireOrder(Order o);

    /**
     * This method returns an overview of all available tickets, and information about them
     * @return A collection of TicketInformation objects
     */
    Collection<TicketInformationResponse> getAvailableTickets();
}
