package com.min.edu.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.dto.EventDetailImageDtos;
import com.min.edu.event.repository.EventDetailImageRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.file.service.FileService;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventDetailImageServiceTest {
    @Mock private EventDetailImageRepository detailImageRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventOrganizationMemberRepository organizationMemberRepository;
    @Mock private FileService fileService;

    @Test
    void replace_validatesFilesAndKeepsRequestedOrder() {
        EventDetailImageService service = service();
        Event event = Event.builder().id(7L).organizerOrganizationId(3L).status(EventStatus.PUBLISHED).build();
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(11L, PlatformRole.USER);
        given(eventRepository.findById(7L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                3L, 11L, OrganizationMemberStatus.ACTIVE,
                List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER))).willReturn(true);
        given(detailImageRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        List<EventDetailImageDtos.Item> result = service.replace(3L, 7L,
                new EventDetailImageDtos.SaveRequest(List.of(
                        new EventDetailImageDtos.SaveItem(101L, "첫 번째 이미지"),
                        new EventDetailImageDtos.SaveItem(102L, "두 번째 이미지"))), actor);

        assertThat(result).extracting(EventDetailImageDtos.Item::fileId).containsExactly(101L, 102L);
        assertThat(result).extracting(EventDetailImageDtos.Item::displayOrder).containsExactly(0, 1);
        verify(fileService).assertPublicAccessible(101L, 11L);
        verify(fileService).assertPublicAccessible(102L, 11L);
        verify(detailImageRepository).deleteAllByEventId(7L);
    }

    @Test
    void replace_rejectsNonManager() {
        EventDetailImageService service = service();
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(11L, PlatformRole.USER);
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                3L, 11L, OrganizationMemberStatus.ACTIVE,
                List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER))).willReturn(false);

        assertThatThrownBy(() -> service.replace(3L, 7L,
                new EventDetailImageDtos.SaveRequest(List.of()), actor))
                .isInstanceOf(BusinessException.class);
    }

    private EventDetailImageService service() {
        return new EventDetailImageService(detailImageRepository, eventRepository,
                organizationMemberRepository, fileService);
    }
}
