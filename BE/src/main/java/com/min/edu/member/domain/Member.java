package com.min.edu.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", length = 20)
    private OauthProvider oauthProvider;

    @Column(name = "oauth_subject", length = 255)
    private String oauthSubject;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 30)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Member createOAuthMember(
            String email,
            String nickname,
            OauthProvider oauthProvider,
            String oauthSubject,
            OffsetDateTime now) {
        return Member.builder()
            .email(email)
            .nickname(nickname)
            .oauthProvider(oauthProvider)
            .oauthSubject(oauthSubject)
            .platformRole(PlatformRole.USER)
            .status(MemberStatus.ACTIVE)
            .lastLoginAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public static Member createBusinessMember(
            String email,
            String nickname,
            String passwordHash,
            OffsetDateTime now) {
        return Member.builder()
            .email(email)
            .nickname(nickname)
            .passwordHash(passwordHash)
            .platformRole(PlatformRole.USER)
            .status(MemberStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public void updateOAuthProfile(
            String email,
            OffsetDateTime loginAt) {
        this.email = email;
        this.lastLoginAt = loginAt;
        this.updatedAt = loginAt;
    }

    public void updateLastLoginAt(OffsetDateTime loginAt) {
        this.lastLoginAt = loginAt;
        this.updatedAt = loginAt;
    }
}
