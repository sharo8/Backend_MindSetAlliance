-- Multi-société : ville de référence + SSS déjà présent en V2
ALTER TABLE projects
    ADD COLUMN ville_reference VARCHAR(80) NULL AFTER backend_url;

UPDATE projects SET ville_reference = 'Kinshasa' WHERE code = 'CNN' AND ville_reference IS NULL;
UPDATE projects SET ville_reference = 'Kinshasa' WHERE code = 'SSS' AND ville_reference IS NULL;
