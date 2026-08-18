package com.min.edu.funnel.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.min.edu.funnel.domain.FunnelAction;

public interface FunnelActionRepository extends ElasticsearchRepository<FunnelAction, String> {
}
