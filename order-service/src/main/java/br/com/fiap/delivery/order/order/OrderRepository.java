package br.com.fiap.delivery.order.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    long countByStatus(OrderStatus status);
}
