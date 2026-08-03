package com.min.edu.event.service;

import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.repository.AdvertisementRepository;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventLifecycleScheduler {
    private final EventRepository eventRepository;
    private final AdvertisementRepository advertisementRepository;

    public EventLifecycleScheduler(EventRepository eventRepository,
            AdvertisementRepository advertisementRepository) {
        this.eventRepository = eventRepository;
        this.advertisementRepository = advertisementRepository;
    }

    @Scheduled(cron = "${app.lifecycle.cron:0 * * * * *}")
    @Transactional
    public void updateLifecycleStatuses() {
        OffsetDateTime now = OffsetDateTime.now();
        eventRepository.findAllByStatusInAndEndAtLessThanEqual(
                List.of(EventStatus.PUBLISHED, EventStatus.SUSPENDED), now)
                .forEach(event -> event.end(now));
        advertisementRepository.findAllByStatusAndStartAtLessThanEqual(
                AdvertisementStatus.SCHEDULED, now).stream()
                .filter(ad -> ad.getEndAt().isAfter(now)).forEach(ad -> ad.activate(now));
        advertisementRepository.findAllByStatusInAndEndAtLessThanEqual(
                List.of(AdvertisementStatus.SCHEDULED, AdvertisementStatus.ACTIVE), now)
                .forEach(ad -> ad.end(now));
    }
}
