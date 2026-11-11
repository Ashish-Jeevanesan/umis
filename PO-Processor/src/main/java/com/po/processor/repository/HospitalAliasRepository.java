package com.po.processor.repository;

import com.po.processor.entity.HospitalAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Hospital aliases and alternative name variants.
 */
@Repository
public interface HospitalAliasRepository extends JpaRepository<HospitalAlias, UUID> {

    Optional<HospitalAlias> findByHospitalIdAndAliasName(UUID hospitalId, String aliasName);

    List<HospitalAlias> findByHospitalId(UUID hospitalId);

    List<HospitalAlias> findByAliasNameIgnoreCase(String aliasName);
}
