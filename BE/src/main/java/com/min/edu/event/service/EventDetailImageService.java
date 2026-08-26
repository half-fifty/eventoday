package com.min.edu.event.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventDetailImage;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.dto.EventDetailImageDtos;
import com.min.edu.event.repository.EventDetailImageRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.file.service.FileService;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventDetailImageService {
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final EventDetailImageRepository detailImageRepository;
    private final EventRepository eventRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final FileService fileService;

    public EventDetailImageService(EventDetailImageRepository detailImageRepository,
            EventRepository eventRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            FileService fileService) {
        this.detailImageRepository = detailImageRepository;
        this.eventRepository = eventRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.fileService = fileService;
    }

    public List<EventDetailImageDtos.Item> findPublic(Long eventId) {
        Event event = getEvent(eventId);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }
        return findItems(eventId);
    }

    public List<EventDetailImageDtos.Item> findManaged(Long organizationId, Long eventId,
            AuthenticatedMemberDto actor) {
        Event event = getManagedEvent(organizationId, eventId, actor);
        return findItems(event.getId());
    }

    @Transactional
    public List<EventDetailImageDtos.Item> replace(Long organizationId, Long eventId,
            EventDetailImageDtos.SaveRequest request, AuthenticatedMemberDto actor) {
        Event event = getManagedEvent(organizationId, eventId, actor);
        Set<Long> fileIds = new HashSet<>();
        for (EventDetailImageDtos.SaveItem image : request.images()) {
            if (!fileIds.add(image.fileId())) {
                throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
            fileService.assertPublicImageAccessible(image.fileId(), actor.getMemberId());
        }

        detailImageRepository.deleteAllByEventId(event.getId());
        detailImageRepository.flush();
        OffsetDateTime now = OffsetDateTime.now();
        List<EventDetailImage> images = java.util.stream.IntStream.range(0, request.images().size())
                .mapToObj(index -> {
                    EventDetailImageDtos.SaveItem image = request.images().get(index);
                    return EventDetailImage.create(event.getId(), image.fileId(), index, image.altText(), now);
                })
                .toList();
        return detailImageRepository.saveAll(images).stream()
                .map(EventDetailImageDtos.Item::from)
                .toList();
    }

    private List<EventDetailImageDtos.Item> findItems(Long eventId) {
        return detailImageRepository.findAllByEventIdOrderByDisplayOrderAsc(eventId).stream()
                .map(EventDetailImageDtos.Item::from)
                .toList();
    }

    private Event getManagedEvent(Long organizationId, Long eventId, AuthenticatedMemberDto actor) {
        if (actor == null) throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        if (!organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                organizationId, actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        Event event = getEvent(eventId);
        if (!event.getOrganizerOrganizationId().equals(organizationId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return event;
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }
}
