package com.min.edu.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exhibit_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitCategory {
    @Id @Column(name = "id") private Long id;
    @Column(name = "code", nullable = false, unique = true, length = 50) private String code;
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "display_order", nullable = false) private Integer displayOrder;
    @Column(name = "active", nullable = false) private boolean active;
}
