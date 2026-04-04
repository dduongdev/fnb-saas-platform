package com.project.fnb.repository;

import com.project.fnb.modules.global.repository.AccessKeyRepository;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.global.repository.UserRepository;
import com.project.fnb.modules.menu.repository.CategoryRepository;
import com.project.fnb.modules.menu.repository.ProductImageRepository;
import com.project.fnb.modules.menu.repository.ProductRepository;
import com.project.fnb.modules.payment.repository.PaymentTransactionRepository;
import com.project.fnb.modules.pos.repository.NotificationRepository;
import com.project.fnb.modules.pos.repository.OrderItemRepository;
import com.project.fnb.modules.pos.repository.OrderRepository;
import com.project.fnb.modules.pos.repository.PosActionAuditRepository;
import com.project.fnb.modules.pos.repository.SessionRepository;
import com.project.fnb.modules.pos.repository.TableRepository;
import com.project.fnb.modules.reporting.repository.DailyStatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryContractTest {

    @Test
    void allRepositoryInterfaces_ShouldExtendJpaRepository() {
        List<Class<?>> repositories = List.of(
                AccessKeyRepository.class,
                TenantRepository.class,
                UserRepository.class,
                CategoryRepository.class,
                ProductRepository.class,
                ProductImageRepository.class,
                PaymentTransactionRepository.class,
                NotificationRepository.class,
                OrderItemRepository.class,
                OrderRepository.class,
                PosActionAuditRepository.class,
                SessionRepository.class,
                TableRepository.class,
                DailyStatRepository.class
        );

        for (Class<?> repository : repositories) {
            assertTrue(repository.isInterface(), repository.getSimpleName() + " must be an interface");
            assertTrue(JpaRepository.class.isAssignableFrom(repository),
                    repository.getSimpleName() + " must extend JpaRepository");
            assertTrue(repository.getMethods().length > 0,
                    repository.getSimpleName() + " should expose CRUD/query methods");

            Type[] genericInterfaces = repository.getGenericInterfaces();
            assertTrue(genericInterfaces.length > 0,
                    repository.getSimpleName() + " must declare generic repository contract");
        }
    }
}
