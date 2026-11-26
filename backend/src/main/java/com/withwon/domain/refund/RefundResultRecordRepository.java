package com.withwon.domain.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundResultRecordRepository extends JpaRepository<RefundResultRecord, Long> {

    List<RefundResultRecord> findAllByIncident_IdOrderByVersionNoDesc(Long incidentId);
}
