package com.min.edu.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventExhibitCategoryId implements Serializable {
    @Column(name = "event_id") private Long eventId;
    @Column(name = "category_id") private Long categoryId;
}
