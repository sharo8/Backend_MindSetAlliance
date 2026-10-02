-- Libellé manquant / trop technique pour la vitrine consolidée.
UPDATE permissions
SET libelle = 'Voir la vitrine consolidée'
WHERE code = 'VIEW_VITRINE'
  AND (libelle IS NULL OR libelle = '' OR libelle = 'VIEW_VITRINE' OR libelle = 'Consulter la vitrine / KPIs');
