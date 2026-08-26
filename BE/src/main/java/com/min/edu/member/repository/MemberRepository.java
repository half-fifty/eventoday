package com.min.edu.member.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.OauthProvider;

public interface MemberRepository extends JpaRepository<Member, Long>{
    boolean existsByEmail(String email);

    Optional<Member> findByEmail(String email);

    Optional<Member> findByOauthProviderAndOauthSubject(OauthProvider
         oauthProvider, String oauthSubject
    );

    @Query(value = """
        select * from members m
        where position(lower(:query) in lower(coalesce(m.email, ''))) > 0
           or position(lower(:query) in lower(coalesce(m.nickname, ''))) > 0
        order by m.nickname asc, m.id asc
        limit 20
        """, nativeQuery = true)
    List<Member> searchByEmailOrNickname(@Param("query") String query);
}
