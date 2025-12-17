package com.project.fnb.modules.pos.entity;

import com.project.fnb.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "dining_tables") 
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE dining_tables SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DiningTable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Status status = Status.AVAILABLE;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    // --- Logic Gộp Bàn ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_table_id")
    private DiningTable masterTable;

    public enum Status {
        AVAILABLE, // Trống
        SERVING,   // Có khách
        RESERVED   // Đã đặt trước
    }
}