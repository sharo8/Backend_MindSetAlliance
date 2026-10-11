-- Retire les jeux factices Colis na Nga (courses, coursiers, incidents, abonnements, factures).
-- Les tickets internes Mindset Alliance ne sont pas concernés.

DELETE o FROM ops_records o
INNER JOIN projects p ON p.id = o.project_id AND p.code = 'CNN'
WHERE o.module IN ('COURSE', 'INCIDENT', 'COURIER', 'SUBSCRIPTION', 'INVOICE', 'ENTERPRISE');

DELETE k FROM vitrine_kpis k
INNER JOIN projects p ON p.id = k.project_id AND p.code = 'CNN';
