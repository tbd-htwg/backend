-- Transport is only start/end places; type and cost live elsewhere (e.g. trip budget).
alter table transport
    drop column if exists type,
    drop column if exists cost,
    drop column if exists currency;
