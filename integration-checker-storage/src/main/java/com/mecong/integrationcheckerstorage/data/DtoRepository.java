package com.mecong.integrationcheckerstorage.data;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel = "dtos", path = "dtos")
public interface DtoRepository extends PagingAndSortingRepository<DtoEntity, String> {
}
