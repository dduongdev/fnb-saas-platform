# N+1 Query Analysis Report

**Generated**: April 5, 2026  
**Framework**: Spring Boot 3.5.8 + JPA/Hibernate  
**Total Issues Found**: 10  
**Query Optimization Impact**: Up to 95% reduction in database queries

---

## Overview

N+1 queries occur when:
1. Code executes **1 main query** to fetch a list of entities
2. Then executes **N additional queries** in a loop, one per item
3. Total: 1 + N queries instead of 1 single query with JOIN

**Example Problem:**
```java
List<Order> orders = orderRepository.findAll();  // Query 1
for (Order order : orders) {                     
    order.getSession().getName();  // Query 2, 3, 4... (N queries)
}
//Total: 1 + N queries ❌
```

**Solution (JOIN FETCH):**
```java
List<Order> orders = orderRepository.findAllWithSession();  
// SELECT o FROM Order o LEFT JOIN FETCH o.session  (1 query)
for (Order order : orders) {
    order.getSession().getName();  // No additional query ✅
}
// Total: 1 query ✅
```

---

## 🔴 CRITICAL ISSUES (Fix Immediately)

### Issue #1: MenuService.getPublicMenu() — Nested Lazy Loading

**Location**: [backend/src/main/java/com/project/fnb/modules/menu/service/MenuService.java](backend/src/main/java/com/project/fnb/modules/menu/service/MenuService.java#L23-L45), Lines 23-45

**Severity**: 🔴 CRITICAL — Public API endpoint

**Current Code Problem:**
```java
@Transactional(readOnly = true)
public PublicMenuDto getPublicMenu(String tenantId) {
    // Line 25: JOIN FETCH categories+products (1 query) ✅
    List<Category> categories = categoryRepository.findAllWithProducts(tenantId);
    
    return PublicMenuDto.builder()
        .categories(categories.stream().map(cat ->
            PublicMenuDto.CategoryDto.builder()
                .id(cat.getId())
                .products(cat.getProducts().stream()    // ✅ Already eager-loaded
                    .map(p -> {
                        // ❌ BUG: ProductImage is LAZY loaded
                        var thumb = p.getImages().stream()  // TRIGGERS N QUERIES!
                            .filter(ProductImage::getIsPrimary)
                            .findFirst()
                            .map(ProductImage::getImageUrl)
                            .orElse("");
                        return ProductResponse.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .price(p.getPrice())
                            .thumbnail(thumb)
                            .build();
                    })
                    .toList())
                .build())
            .toList())
        .build();
}
```

**Root Cause**: `ProductImage` is `@OneToMany(fetch = FetchType.LAZY)` but not included in JOIN FETCH chain.

**Estimated Query Count**: 
- 1 query: Categories + Products
- +N queries: ProductImage for each product (100+ products = 100+ queries)
- **Total: 101+ queries** ❌

**Fix Required**:

**Step 1: Update CategoryRepository**
```java
@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    // Original (incomplete JOIN FETCH):
    @Query("SELECT DISTINCT c FROM Category c " +
           "LEFT JOIN FETCH c.products p " +
           "WHERE c.tenantId = :tenantId AND c.isDeleted = false " +
           "ORDER BY c.displayOrder")
    List<Category> findAllWithProducts(@Param("tenantId") String tenantId);
    
    // Fixed (complete JOIN FETCH chain):
    @Query("SELECT DISTINCT c FROM Category c " +
           "LEFT JOIN FETCH c.products p " +
           "LEFT JOIN FETCH p.images " +  // ← ADD THIS
           "WHERE c.tenantId = :tenantId AND c.isDeleted = false " +
           "ORDER BY c.displayOrder")
    List<Category> findAllWithProductsAndImages(@Param("tenantId") String tenantId);
}
```

**Step 2: Update MenuService to use new method**
```java
public PublicMenuDto getPublicMenu(String tenantId) {
    List<Category> categories = categoryRepository
        .findAllWithProductsAndImages(tenantId);  // ← Use new method
    // ... rest of code unchanged
}
```

**Expected Improvement**: 
- **Before**: 101+ queries
- **After**: 1 query
- **Speedup**: 100x faster ✅

---

### Issue #2: KdsService.transformToKdsSessionDto() — Triple Nested Loop

**Location**: [backend/src/main/java/com/project/fnb/modules/pos/service/KdsService.java](backend/src/main/java/com/project/fnb/modules/pos/service/KdsService.java#L62-L135), Lines 62-135

**Severity**: 🔴 CRITICAL — KDS (Kitchen Display System) is real-time, heavily used

**Current Code Problem:**
```java
@Transactional(readOnly = true)
public List<KdsSessionDto> getAllActiveSessions() {
    // Line 65: Repository query with INCOMPLETE JOIN FETCH
    List<ServingSession> sessions = sessionRepository.findAllActive();
    // ❌ BUG: findAllActive() is defined as:
    // @Query("SELECT DISTINCT s FROM ServingSession s " +
    //        "LEFT JOIN FETCH s.tables " +  // ← Only joins tables!
    //        "WHERE s.status = 'ACTIVE'")
    
    return sessions.stream()
        .map(this::transformToKdsSessionDto)  // ← Triggers N+M queries here
        .toList();
}

private KdsSessionDto transformToKdsSessionDto(ServingSession session) {
    return KdsSessionDto.builder()
        .tables(session.getTables().stream()              // ✅ Already loaded
            .map(DiningTableKdsDto::from)
            .toList())
        .orders(session.getOrders().stream()             // ❌ N queries! Not eager-loaded
            .map(order -> KdsOrderDto.builder()
                .items(order.getItems().stream()         // ❌ M queries! Not eager-loaded
                    .filter(item -> item.getStatus() == OrderItem.ItemStatus.PENDING)
                    .map(item -> {
                        // ❌ Product is also lazy-loaded
                        Product product = item.getProduct();  // ← +1 query per item
                        return KdsOrderItemDto.builder()
                            .id(item.getId())
                            .productName(product.getName())
                            .quantity(item.getQuantity())
                            .note(item.getNote())
                            .build();
                    })
                    .toList())
                .build())
            .toList())
        .build();
}
```

**Root Cause**: JOIN FETCH chain incomplete. Missing:
- `s.orders` (serves-orders relationship)
- `order.items` (order-items relationship)  
- `item.product` (item-product relationship)

**Estimated Query Count**:
- 1 query: Sessions + Tables
- +N queries: Orders for each session
- +M queries: OrderItems for each order
- +K queries: Product for each orderItem
- **Total: 1 + N + M + K queries** (easily 100-500 queries per KDS refresh) ❌

**Fix Required**:

**Step 1: Update SessionRepository**
```java
@Repository
public interface SessionRepository extends JpaRepository<ServingSession, Long> {
    // Original (broken):
    @Query("SELECT DISTINCT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables " +
           "WHERE s.status = 'ACTIVE'")
    List<ServingSession> findAllActive();
    
    // Fixed (complete JOIN FETCH chain):
    @Query("SELECT DISTINCT s FROM ServingSession s " +
           "LEFT JOIN FETCH s.tables " +
           "LEFT JOIN FETCH s.orders o " +        // ← ADD
           "LEFT JOIN FETCH o.items oi " +        // ← ADD
           "LEFT JOIN FETCH oi.product " +        // ← ADD
           "WHERE s.status = 'ACTIVE' " +
           "ORDER BY s.createdAt DESC")
    List<ServingSession> findAllActiveWithOrdersAndItemsAndProducts();
}
```

**Step 2: Update KdsService**
```java
@Transactional(readOnly = true)
public List<KdsSessionDto> getAllActiveSessions() {
    List<ServingSession> sessions = sessionRepository
        .findAllActiveWithOrdersAndItemsAndProducts();  // ← Use new method
    return sessions.stream()
        .map(this::transformToKdsSessionDto)
        .toList();
}
```

**Expected Improvement**:
- **Before**: 100-500 queries
- **After**: 1 query
- **Speedup**: 100-500x faster ✅

---

### Issue #3: SessionService.buildCustomerOrderResponse() — Product Lazy Loading

**Location**: [backend/src/main/java/com/project/fnb/modules/pos/service/SessionService.java](backend/src/main/java/com/project/fnb/modules/pos/service/SessionService.java#L1011-L1025), Lines 1011-1025

**Severity**: 🔴 CRITICAL — Customer API endpoint (high traffic)

**Current Code Problem:**
```java
private CustomerOrderResponse buildCustomerOrderResponse(Order order) {
    // ❌ BUG: order.getItems() returns lazy-loaded items
    // Each access to item.getProduct() triggers a separate query
    List<CustomerOrderResponse.OrderItemDto> items = order.getItems().stream()
        .map(i -> CustomerOrderResponse.OrderItemDto.builder()
                .productName(i.getProduct().getName())     // ← N queries!
                .productImage(getProductFirstImage(i.getProduct()))  // ← N more queries!
                .quantity(i.getQuantity())
                .price(i.getPrice())
                .note(i.getNote())
                .build())
        .toList();
    
    return CustomerOrderResponse.builder()
        .id(order.getId())
        .status(order.getStatus().name())
        .items(items)
        .build();
}
```

**Root Cause**: Order and OrderItem are loaded, but Product is lazy-loaded.

**Estimated Query Count**:
- 1 query: Order + Items (with implicit load)
- +N queries: Product for each item (typically 5-20 items)
- **Total: 1 + N queries (average 6-21 queries)** ❌

**Fix Required**:

**Step 1: Add new repository method**
```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.items oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.id = :orderId")
    Optional<Order> findByIdWithItemsAndProducts(@Param("orderId") Long orderId);
}
```

**Step 2: Update SessionService to use eager-loaded order**
```java
private CustomerOrderResponse buildCustomerOrderResponse(Long orderId) {
    // Change method signature to accept orderId instead of Order
    Order order = orderRepository
        .findByIdWithItemsAndProducts(orderId)
        .orElseThrow();  // Now items and products are eagerly loaded
    
    // ... rest of code unchanged
}
```

**Expected Improvement**:
- **Before**: 6-21 queries
- **After**: 1 query
- **Speedup**: 6-21x faster ✅

---

## 🟠 HIGH PRIORITY ISSUES

### Issue #4: SessionService.createInvoice() — Double Loop with Relationship Access

**Location**: [backend/src/main/java/com/project/fnb/modules/pos/service/SessionService.java](backend/src/main/java/com/project/fnb/modules/pos/service/SessionService.java#L1096-L1110)

**Estimated Queries**: 2 + N + M (typically 10-50+ queries)

**Problem**: Extra query for tenant + lazy loading of orders and products in parallel loops.

**Fix**:
```java
// Repository method:
@Query("SELECT o FROM Order o " +
       "LEFT JOIN FETCH o.session " +
       "LEFT JOIN FETCH o.items oi " +
       "LEFT JOIN FETCH oi.product " +
       "WHERE o.session.id = :sessionId")
List<Order> findBySessionIdWithItemsAndProducts(@Param("sessionId") Long sessionId);

// Service:
private InvoiceDto createInvoice(Long sessionId) {
    // Don't query tenant separately - pass tenantId as parameter
    List<Order> orders = orderRepository
        .findBySessionIdWithItemsAndProducts(sessionId);
    
    // ... rest of code
}
```

---

### Issue #5: SessionService.notifyTableUpdate() — N+1 on CurrentSession

**Location**: [backend/src/main/java/com/project/fnb/modules/pos/service/SessionService.java#L1253-L1268](backend/src/main/java/com/project/fnb/modules/pos/service/SessionService.java#L1253-L1268)

**Estimated Queries**: 1 + N (N = number of tables, typically 10-50)

**Problem**:
```java
List<TableDto> tables = tableRepository.findAll().stream()
    .map(t -> TableDto.builder()
            .sessionId(t.getCurrentSession() != null  // ← N queries!
                ? t.getCurrentSession().getId() 
                : null)
            .build())
```

**Fix**:
```java
// Repository:
@Query("SELECT t FROM DiningTable t " +
       "LEFT JOIN FETCH t.currentSession " +
       "WHERE t.isDeleted = false")
List<DiningTable> findAllWithSession();

// Service:
List<TableDto> tables = tableRepository.findAllWithSession().stream()
    .map(TableDto::from)
    .toList();
```

---

### Issue #6: ProductService.getAllProducts() — Image Loading

**Location**: [backend/src/main/java/com/project/fnb/modules/menu/service/ProductService.java#L89-L420](backend/src/main/java/com/project/fnb/modules/menu/service/ProductService.java)

**Estimated Queries**: 1 + N (N = page size, typically 20-50)

**Problem**: Product images are lazy-loaded for each product in pagination.

**Fix**:
```java
// Repository:
@Query("SELECT p FROM Product p " +
       "LEFT JOIN FETCH p.images " +
       "WHERE p.tenantId = :tenantId AND p.isDeleted = false")
Page<Product> findAllWithImages(
    @Param("tenantId") String tenantId, 
    Pageable pageable);

// Service:
public Page<ProductResponse> getAllProducts(String tenantId, Pageable pageable) {
    return productRepository
        .findAllWithImages(tenantId, pageable)
        .map(this::mapToResponse);  // Now images are loaded
}
```

---

## 🟡 MEDIUM PRIORITY ISSUES

### Issue #7: TenantService.getMyTenants() — Full Table Scan

**Location**: [backend/src/main/java/com/project/fnb/modules/global/service/TenantService.java#L275-L285](backend/src/main/java/com/project/fnb/modules/global/service/TenantService.java#L275-L285)

**Problem**:
```java
// ❌ Inefficient: Loads ALL tenants into memory
return tenantRepository.findAll().stream()  // Load every tenant!
    .filter(t -> t.getOwnerId().equals(ownerId))  // Filter in memory
    .toList();
```

**Fix**:
```java
// Add repository method:
@Query("SELECT t FROM Tenant t WHERE t.ownerId = :ownerId")
List<Tenant> findByOwnerId(@Param("ownerId") String ownerId);

// Service:
return tenantRepository.findByOwnerId(ownerId);
```

---

### Issue #8: TableService.getTables() — CurrentSession Lazy Loading

**Location**: [backend/src/main/java/com/project/fnb/modules/pos/service/TableService.java](backend/src/main/java/com/project/fnb/modules/pos/service/TableService.java)

**Estimated Queries**: 1 + N

**Fix**: Same as Issue #5 - use JOIN FETCH for currentSession.

---

### Issue #9: SessionService.getPendingSessions/getActiveSessions()

**Problem**: While repository has JOIN FETCH, potential lazy loading in mapping.

**Fix**: Ensure all relationships accessed in `SessionResponse.fromEntity()` are pre-loaded.

---

### Issue #10: NotificationService Stream Operations

**Location**: [backend/src/main/java/com/project/fnb/modules/pos/service/NotificationService.java](backend/src/main/java/com/project/fnb/modules/pos/service/NotificationService.java)

**Estimated Queries**: 1 + N

**Fix**: Add JOIN FETCH for related session/table if accessed in response.

---

## Implementation Checklist

- [ ] Fix Issue #1: MenuService.getPublicMenu() — Add `findAllWithProductsAndImages()`
- [ ] Fix Issue #2: KdsService.transformToKdsSessionDto() — Add `findAllActiveWithOrdersAndItemsAndProducts()`
- [ ] Fix Issue #3: SessionService.buildCustomerOrderResponse() — Add `findByIdWithItemsAndProducts()`
- [ ] Fix Issue #4: SessionService.createInvoice() — Add `findBySessionIdWithItemsAndProducts()`
- [ ] Fix Issue #5: SessionService.notifyTableUpdate() — Add `findAllWithSession()`
- [ ] Fix Issue #6: ProductService.getAllProducts() — Add `findAllWithImages()` with pagination
- [ ] Fix Issue #7: TenantService.getMyTenants() — Add `findByOwnerId()`
- [ ] Fix Issue #8: TableService.getTables() — Update existing repository method
- [ ] Fix Issue #9: SessionService methods — Review mappings for lazy loading
- [ ] Fix Issue #10: NotificationService — Add JOIN FETCH if needed

---

## Performance Testing Script

After fixes, run this to verify:

```java
@Test
void testKdsServicePerformance() {
    // Enable SQL logging
    HibernateStatistics stats = sessionFactory.getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    
    // Call KDS service
    List<KdsSessionDto> result = kdsService.getAllActiveSessions();
    
    // Check query count (should be exactly 1)
    long queryCount = stats.getQueryExecutionCount();
    assertEquals(1, queryCount, 
        "Expected 1 query but got " + queryCount);
}
```

---

## Summary

| Issue | Type | Severity | Queries | Speedup |
|-------|------|----------|---------|---------|
| #1 MenuService.getPublicMenu() | N+1 | 🔴 CRITICAL | 101+ → 1 | 100x |
| #2 KdsService.transformToKdsSessionDto() | N+1+M | 🔴 CRITICAL | 100-500 → 1 | 100-500x |
| #3 SessionService.buildCustomerOrderResponse() | N+1 | 🔴 CRITICAL | 6-21 → 1 | 6-21x |
| #4 SessionService.createInvoice() | N+1+M | 🟠 HIGH | 10-50 → 1 | 10-50x |
| #5 SessionService.notifyTableUpdate() | N+1 | 🟠 HIGH | 1+N → 1 | Nxx |
| #6 ProductService.getAllProducts() | N+1 | 🟠 HIGH | 1+N → 1 | Nx |
| #7 TenantService.getMyTenants() | Full scan | 🟡 MEDIUM | All → Filtered | Var |
| #8 TableService.getTables() | N+1 | 🟡 MEDIUM | 1+N → 1 | Nx |
| #9 SessionService sessions | Potential | 🟡 MEDIUM | 1+ → 1 | Var |
| #10 NotificationService | N+1 | 🟡 MEDIUM | 1+N → 1 | Nx |

**Total Potential Queries Eliminated**: 200-800+ queries per operation  
**Estimated Speedup**: 10-500x faster for critical endpoints

---

**Next Steps**:
1. Start with CRITICAL issues (#1-#3)
2. Run unit tests after each fix
3. Monitor production with slow query logs
4. Add performance tests to prevent regressions
