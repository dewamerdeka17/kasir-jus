# JusMerdeka remote monitor · Vercel + Neon

Next.js dashboard and authenticated sync API for the local Android SQLite ledger. The Vercel project has been registered as `jusmerdeka-monitor` in `dewas-projects-9fc442ca`, with GitHub root directory `remote-monitor`. **Database credentials have not been configured: automatic approval rejected copying them to the new project. This is not an active production connection yet.**

## Activation

1. With the owner's approval, configure the existing JusMerdeka Neon connection as a Production `DATABASE_URL` secret and configure a separate random `SESSION_SECRET` (at least 32 characters). Do not reuse database access from unrelated projects.
2. Apply `db/001_monitor.sql` with `scripts/migrate.mjs`. The script takes the connection from the process environment or hidden stdin. It only adds `jm_monitor_*` tables/function and does not change existing online POS data or users.
3. Deploy the exact reviewed commit to Vercel. Production must be reachable by the phone; the application itself protects all business data with admin authentication. Keep preview environments protected and isolated from production database access.
4. Sign in using an existing ACTIVE ADMIN web account from the JusMerdeka `User` table. No default passwords or accounts are created. APK PINs are separate.
5. In **Koneksi HP**, download a connection file, valid 24 hours for the first connection. In APK 3.0, **Admin → Setelan → Pantau dari jauh → Pilih file koneksi**. Delete the file after import. Making another configuration revokes previous writers; snapshots already received remain viewable.

## Sync guarantees

- SQLite on one phone is authoritative. The web dashboard is read-only for products, transactions, stock and expenses.
- Canonical per-record SHA-256 checkpoints select only changed records. Initial/lost checkpoints upload a complete reset. Deleted records after a local restore become tombstones.
- `status → start → batch → commit` requests are under 512 KiB; native batches target 250 KB / 100 records. Every request requires a per-device key. The database only stores its hash.
- Data is staged before commit, then a Postgres function locks the device, verifies base revision and complete batch count, applies all changes transactionally, and advances the acknowledgment. Partial uploads do not change reports. Stale versions and changed reused batches are rejected. Exact retries are idempotent.
- Local checkpoints advance only after the committed revision is acknowledged. A lost acknowledgment causes a safe full reset on the next attempt. Local ledger records are retained.
- Updates run while the APK is open, roughly every minute and after changes. Closed/force-stopped apps resume on opening. The dashboard polls while visible and displays capture/receive times and a stale-data warning.
- Reports retain WIB daily/seven-day/calendar-month conventions, best sellers, busiest date, cash/QRIS separation, expenses and styled seven-sheet Excel exports.
- No HPP, PIN, receipt body, request ID or customer contacts are uploaded. Credentials are not bundled into source/APK/backup. Android stores the device configuration with Keystore encryption.

## Auth/security

Uses existing ADMIN account password hashes (bcrypt), separate signed HttpOnly/Secure/SameSite session cookies and database-backed login throttling. All report/device/config/export routes check an active admin server-side and scope records to that owner. CSRF checks protect browser mutations. Writer keys cannot read the dashboard or change permissions. Connection files are downloaded once through an authenticated response with `no-store` and `attachment` headers. No production secret fallback exists.

## Checks

`npm ci`, `npm run typecheck`, `npm test`, and `npm run build`. Tests use an isolated PostgreSQL-compatible PGlite engine and synthetic users/records; they never connect to the hosted database. Include initial sync, duplicate retry, partial batch, cancellation, restore removals, stale concurrent commit, revocation, malformed data, report totals and XLSX. Physical-phone/Vercel connectivity must be verified after the owner enables production secrets and connects their phone.
