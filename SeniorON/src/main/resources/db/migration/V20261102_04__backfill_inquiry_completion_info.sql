
UPDATE inquiry i
    JOIN (
    SELECT
    ranked.inquiry_id,
    ranked.created_at,
    ranked.created_by_admin_id
    FROM (
    SELECT
    ia.inquiry_id,
    ia.created_at,
    ia.created_by_admin_id,
    ROW_NUMBER() OVER (
    PARTITION BY ia.inquiry_id
    ORDER BY ia.created_at DESC, ia.inquiry_answer_id DESC
    ) AS rn
    FROM inquiry_answer ia
    ) ranked
    INNER JOIN admin a
    ON a.admin_id = ranked.created_by_admin_id
    WHERE ranked.rn = 1
    AND ranked.created_at IS NOT NULL
    ) latest
ON i.inquiry_id = latest.inquiry_id
    SET
        i.completed_at = latest.created_at,
        i.completed_by_admin_id = latest.created_by_admin_id
WHERE i.status = 'COMPLETED'
  AND i.completed_at IS NULL
  AND i.completed_by_admin_id IS NULL;
