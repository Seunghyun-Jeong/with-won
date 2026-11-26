package com.withwon.domain.checkitem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheckItemRepository extends JpaRepository<CheckItem, Long> {

    @Query(value = """
            SELECT item_code AS itemCode,
                   question AS question,
                   answer_type AS answerType,
                   CAST(answer_options AS CHAR) AS answerOptions,
                   required AS required,
                   scope AS scope
            FROM check_items
            WHERE enabled = TRUE AND schema_version = :schemaVersion
            ORDER BY sort_order ASC, id ASC
            """, nativeQuery = true)
    List<CheckItemConfigProjection> findEnabledConfigItems(
            @Param("schemaVersion") String schemaVersion);
}
