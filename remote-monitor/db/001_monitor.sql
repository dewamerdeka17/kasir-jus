-- Additive schema only. Does not alter existing online POS tables or seed users/sales.
CREATE TABLE IF NOT EXISTS jm_monitor_devices (
 id uuid PRIMARY KEY,
 owner_user_id integer NOT NULL REFERENCES "User"(id),
 name text NOT NULL,
 secret_hash text NOT NULL,
 expires_at timestamptz NOT NULL,
 revoked boolean NOT NULL DEFAULT false,
 revision bigint NOT NULL DEFAULT 0,
 shop_name text NOT NULL DEFAULT 'JusMerdeka',
 received_at timestamptz,
 captured_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
-- statement
CREATE INDEX IF NOT EXISTS jm_monitor_owner_idx ON jm_monitor_devices(owner_user_id,created_at);
-- statement
CREATE TABLE IF NOT EXISTS jm_monitor_records (
 device_id uuid NOT NULL REFERENCES jm_monitor_devices(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK(kind IN ('products','sales','expenses')),
 record_id bigint NOT NULL CHECK(record_id>0),
 payload jsonb NOT NULL,
 PRIMARY KEY(device_id,kind,record_id)
);
-- statement
CREATE TABLE IF NOT EXISTS jm_monitor_uploads (
 id uuid PRIMARY KEY,
 device_id uuid NOT NULL REFERENCES jm_monitor_devices(id) ON DELETE CASCADE,
 revision bigint NOT NULL CHECK(revision>0),
 base_revision bigint NOT NULL CHECK(base_revision>=0),
 reset boolean NOT NULL,
 expected_batches integer NOT NULL CHECK(expected_batches>=0 AND expected_batches<=10000),
 shop_name text NOT NULL,
 captured_at timestamptz NOT NULL,
 committed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(device_id,revision)
);
-- statement
CREATE TABLE IF NOT EXISTS jm_monitor_chunks (
 upload_id uuid NOT NULL REFERENCES jm_monitor_uploads(id) ON DELETE CASCADE,
 batch_index integer NOT NULL CHECK(batch_index>=0),
 content_hash text NOT NULL,
 changes jsonb NOT NULL,
 PRIMARY KEY(upload_id,batch_index)
);
-- statement
CREATE TABLE IF NOT EXISTS jm_monitor_login_attempts (
 key_hash text PRIMARY KEY,
 count integer NOT NULL DEFAULT 1,
 reset_at timestamptz NOT NULL
);
-- statement
CREATE OR REPLACE FUNCTION jm_monitor_commit(p_device uuid,p_upload uuid) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE d jm_monitor_devices%ROWTYPE; u jm_monitor_uploads%ROWTYPE; c record; item jsonb; n integer; stamp timestamptz;
BEGIN
 SELECT * INTO d FROM jm_monitor_devices WHERE id=p_device FOR UPDATE;
 IF NOT FOUND OR d.revoked THEN RAISE EXCEPTION 'WRITER_REVOKED'; END IF;
 SELECT * INTO u FROM jm_monitor_uploads WHERE id=p_upload AND device_id=p_device FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'UPLOAD_MISSING'; END IF;
 IF u.committed_at IS NOT NULL THEN RETURN jsonb_build_object('ok',true,'revision',u.revision,'receivedAt',u.committed_at); END IF;
 IF u.created_at<now()-interval '24 hours' THEN RAISE EXCEPTION 'UPLOAD_EXPIRED'; END IF;
 IF d.revision<>u.base_revision OR u.revision<=d.revision THEN RAISE EXCEPTION 'STALE_REVISION'; END IF;
 SELECT count(*) INTO n FROM jm_monitor_chunks WHERE upload_id=p_upload;
 IF n<>u.expected_batches THEN RAISE EXCEPTION 'INCOMPLETE_BATCHES'; END IF;
 IF u.reset THEN DELETE FROM jm_monitor_records WHERE device_id=p_device; END IF;
 FOR c IN SELECT changes FROM jm_monitor_chunks WHERE upload_id=p_upload ORDER BY batch_index LOOP
  FOR item IN SELECT value FROM jsonb_array_elements(c.changes) LOOP
   IF item->>'op'='remove' THEN
    DELETE FROM jm_monitor_records WHERE device_id=p_device AND kind=item->>'kind' AND record_id=(item->>'id')::bigint;
   ELSE
    INSERT INTO jm_monitor_records(device_id,kind,record_id,payload) VALUES(p_device,item->>'kind',(item->>'id')::bigint,item->'data')
    ON CONFLICT(device_id,kind,record_id) DO UPDATE SET payload=excluded.payload;
   END IF;
  END LOOP;
 END LOOP;
 stamp=clock_timestamp();
 UPDATE jm_monitor_devices SET revision=u.revision,shop_name=u.shop_name,captured_at=u.captured_at,received_at=stamp WHERE id=p_device;
 UPDATE jm_monitor_uploads SET committed_at=stamp WHERE id=p_upload;
 DELETE FROM jm_monitor_chunks WHERE upload_id=p_upload;
 RETURN jsonb_build_object('ok',true,'revision',u.revision,'receivedAt',stamp);
END;
$$;
