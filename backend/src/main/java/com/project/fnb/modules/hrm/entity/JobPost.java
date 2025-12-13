package com.project.fnb.modules.hrm.entity;

import com.project.fnb.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "job_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE job_posts SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class JobPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title; 

    @Column(columnDefinition = "TEXT")
    private String description; 

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true; 
}