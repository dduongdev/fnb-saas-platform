package com.project.fnb.modules.hrm.entity;

import com.project.fnb.common.BaseEntity;
import com.project.fnb.modules.global.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "employees", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "user_id"}) 
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role role; 

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    public enum Role {
        MANAGER, STAFF
    }

    public enum Status {
        ACTIVE, RESIGNED
    }
}