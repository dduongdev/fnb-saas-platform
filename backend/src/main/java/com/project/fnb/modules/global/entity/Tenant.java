package com.project.fnb.modules.global.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import com.project.fnb.modules.global.dto.PaymentConfigDto;

import java.time.LocalDateTime;

import java.util.List;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "owner_id", length = 64)
    private String ownerId; 

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "payment_config", columnDefinition = "LONGTEXT")
    @Convert(converter = PaymentConfigConverter.class) 
    private PaymentConfigDto paymentConfig;
    
    // Danh sách access key nội bộ thuộc tenant này
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tenant_access_keys", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "access_key", length = 64)
    private List<String> accessKeys;
}