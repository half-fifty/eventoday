package com.min.edu.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.OauthProvider;

public interface MemberRepository extends JpaRepository<Member, Long>{
    boolean existsByEmail(String email);

    Optional<Member> findByOauthProviderAndOauthSubject(OauthProvider
         oauthProvider, String oauthSubject
    );
}
