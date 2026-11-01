-- The external reference only has to be unique within one environment: a merchant who tried ORDER-1001 in the
-- sandbox must be able to use it for the first live order (the bulk upload template does exactly that).
alter table merchant_orders drop constraint uq_orders_extref;
alter table merchant_orders add constraint uq_orders_extref unique (merchant_id, sandbox, external_ref);
