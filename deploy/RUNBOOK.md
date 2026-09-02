# RUNBOOK — Phase 4 deploy

> Server setup, deploy, and restore for the Job Application Tracker.
> Written to be followed top to bottom the first time, and dipped into afterwards.
>
> **Read `CLAUDE.md §6` and `§10` before deviating** — several choices here look arbitrary
> and are not (explicit `-Xmx` rather than `MaxRAMPercentage`, no Docker, no Datadog Agent,
> `/actuator` not proxied). Update this file **as you go**, not afterwards.

Target: `https://app4jobtrack.me` serving the SPA and API, auto-deployed from `main`, with
a nightly off-box backup (backups deferred for now — Step 12).

**Substitute throughout:** `app4jobtrack.me` → your domain, `<VPS_IP>` → the reserved public
IP, `<ATLAS_URI>` → your Atlas SRV string.

---

## Step 0 — Verify the Phase 0 prerequisites (10 min)

Do not skip. Half of first-deploy failures are a Phase 0 item that was assumed done.

> **Docs disagree about this.** `CLAUDE.md §2` says the domain, Oracle VM, Atlas M0 and
> Google OAuth client are all done; `STATE.md §5` still has every box unticked. Rather than
> trust either, check. Tick `STATE.md §5` as you confirm each one.

| # | Check | Command / where | Expected |
|---|---|---|---|
| 1 | Domain registered | `whois app4jobtrack.me \| head -20` | a registrar and an expiry in the future |
| 2 | Oracle instance running | Oracle Cloud console → Compute → Instances | one `VM.Standard.E2.1.Micro`, state **Running** |
| 3 | IP is **reserved**, not ephemeral | console → instance → Attached VNICs → IP addresses | Public IP type = **Reserved** |
| 4 | You can SSH in | `ssh ubuntu@<VPS_IP>` | a shell |
| 5 | Atlas cluster alive | Atlas console → Database | M0 cluster, not paused |
| 6 | Google OAuth client exists | console.cloud.google.com → APIs & Services → Credentials | an OAuth 2.0 Client ID |

If #3 says *Ephemeral*, fix it now. A stop/start then changes the IP, which breaks the
Atlas allowlist **and** the TLS certificate at the same time, from a reboot you did not
think was risky.

If #2 does not exist, you are not in Phase 4 yet — go back to `PLAN.md` Phase 0.

---

## Step 1 — Backfill locally, before touching the server

`PLAN.md` puts this first for a reason: it is the cheapest place to find schema and
validation problems, and an empty app cannot be dogfooded. **Do it against local Mongo.**

```bash
docker start jt-mongo
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# separate shell
cd frontend && npm run dev
```

Enter the real search through the UI (companies first, then applications with their true
`stages[]` and dates), or script it in `backend/src/test/http/backfill.http`.

Two things to watch for, both of which mean a bug rather than bad input:

- **A past-dated stage must be accepted.** Historical backfill is the whole point of
  `SCHEMA.md §8.1` having no `@Future`. If a stage from last month is rejected, that
  constraint has crept back in.
- **Check `GET /api/stats` afterwards.** If the funnel or response rate looks wrong, fix the
  aggregation now. It is much more expensive to diagnose once the data lives in Atlas.

> Backfilled data lives in **local** Mongo. It does not migrate to Atlas by itself — see
> Step 10 if you want to carry it over.

---

## Step 2 — DNS (do this early; propagation is the slow part)

At your registrar:

| Type | Name | Value |
|---|---|---|
| A | `@` | `<VPS_IP>` |
| A | `www` | `<VPS_IP>` |

```bash
dig +short app4jobtrack.me
dig +short www.app4jobtrack.me      # both must return <VPS_IP>
```

Certbot will fail with a confusing authorisation error if DNS has not propagated. Wait for
both to answer before Step 7.

---

## Step 3 — Open the firewall (Oracle's double firewall)

**This is the single most common "site unreachable" cause on Oracle.** There are two
independent firewalls and both default to closed.

**3a. VCN Security List** — console → Networking → VCN → Subnet → Security List → Add
Ingress Rules:

| Source CIDR | Protocol | Dest. port |
|---|---|---|
| `0.0.0.0/0` | TCP | 80 |
| `0.0.0.0/0` | TCP | 443 |

**3b. The instance's own iptables** — Ubuntu's Oracle image ships with these ports blocked
regardless of the Security List:

```bash
ssh ubuntu@<VPS_IP>
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save        # or: sudo apt install iptables-persistent
sudo iptables -L INPUT -n --line-numbers | head -12   # confirm both rules present
```

Do **not** open 8080. The API is only ever reached through Nginx on loopback; `SecurityConfig`
now relies on that for the health endpoint too.

---

## Step 4 — Server baseline

```bash
ssh ubuntu@<VPS_IP>
sudo apt update && sudo apt upgrade -y
```

### 4a. Java 25 (Temurin, x86_64)

```bash
sudo apt install -y wget apt-transport-https gpg
sudo mkdir -p /etc/apt/keyrings
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-25-jre
java -version        # expect 25.x
```

The **JRE**, not the JDK — nothing compiles on this box and the JDK is wasted disk.

### 4b. Everything else

```bash
sudo apt install -y nginx certbot python3-certbot-nginx rclone fail2ban unattended-upgrades
# mongodump/mongorestore. Not needed today — Step 12 is deferred — but it is a 30-second
# install now versus a remembered errand later, and Step 12's interim manual dump wants it.
wget -qO - https://www.mongodb.org/static/pgp/server-8.0.asc | sudo gpg --dearmor -o /etc/apt/keyrings/mongodb.gpg
echo "deb [signed-by=/etc/apt/keyrings/mongodb.gpg] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" \
  | sudo tee /etc/apt/sources.list.d/mongodb.list
sudo apt update && sudo apt install -y mongodb-database-tools
mongodump --version
```

### 4c. Confirm swap survived Phase 0

```bash
free -m            # Swap total should be ~4096
swapon --show
cat /proc/sys/vm/swappiness            # 10
grep swappiness /etc/sysctl.conf       # must be present, or it resets on reboot
```

If missing:

```bash
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf && sudo sysctl -p
```

Swap here is a **safety margin, not capacity** (`CLAUDE.md §6`). It lets a spike survive
slowly instead of dying; it does not make 1 GB behave like 5 GB.

### 4d. Users and directories

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin jobtracker
sudo useradd --create-home --shell /bin/bash deploy          # CI logs in as this user

# /var/backups/jobtracker is unused while Step 12 is deferred; created anyway so that
# picking backups back up is a matter of dropping in the script and the timer.
sudo mkdir -p /opt/jobtracker /etc/jobtracker /var/www/jobtracker /var/backups/jobtracker
sudo chown -R jobtracker:jobtracker /opt/jobtracker /etc/jobtracker /var/backups/jobtracker

# CI needs write access to the JAR dir and the web root — group membership, not ownership.
sudo usermod -aG jobtracker deploy
sudo chown -R deploy:jobtracker /var/www/jobtracker
sudo chmod -R g+w /opt/jobtracker /var/www/jobtracker
```

Three identities on purpose: `ubuntu` is you, `deploy` is CI (no sudo except one command),
`jobtracker` runs the JVM and owns the secrets (no login shell at all).

### 4e. SSH hardening

```bash
sudo sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin .*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
sudo systemctl enable --now fail2ban
```

**Keep your current session open** until you have confirmed a new one still works.

---

## Step 5 — Secrets

```bash
sudo -u jobtracker tee /etc/jobtracker/jobtracker.env >/dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod
MONGODB_URI=mongodb+srv://USER:PASS@cluster.xxxxx.mongodb.net/jobtracker?retryWrites=true&w=majority
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
APP_ALLOWED_EMAILS=duy.le.21.ng@gmail.com
APP_MCP_TOKEN=
APP_BASE_URL=https://app4jobtrack.me
DD_API_KEY=
DD_SITE=datadoghq.com
EOF
sudo chmod 600 /etc/jobtracker/jobtracker.env
sudo chown jobtracker:jobtracker /etc/jobtracker/jobtracker.env
```

Generate the MCP token and paste it in (Phase 6 needs the same value):

```bash
openssl rand -hex 32
```

Four traps in that file:

- **`MONGODB_URI` must carry the database name.** Atlas's dialog gives you
  `...mongodb.net/?retryWrites=...` — note `/?`, with nothing between. Put `jobtracker` in
  that gap or the app dies at startup with `Database name must not be empty`.
- **`APP_MCP_TOKEN` empty means the MCP server cannot authenticate.** That is the intended
  fail-closed default, not a bug. Fill it before Phase 6.
- **`APP_BASE_URL` has no trailing slash** and must be the **apex**, matching Google exactly.
- **`DD_API_KEY` must hold the real key** — Phase 0 is complete and one is generated, so
  paste it in along with `DD_SITE`. Metrics then start flowing from the first boot;
  Micrometer pushes straight to the Datadog API over HTTPS and needs no Agent, so this is
  safe to have on during the deploy.

  **If you leave it blank the app will not start at all.** `application-prod.yml` enables
  the Micrometer Datadog registry, which auto-configures on classpath presence alone and
  refuses to boot without a key — `apiKey was 'null' but it is required` (`CLAUDE.md §6`).
  The failure looks nothing like a Datadog problem, so if the service dies at Step 7 with an
  opaque startup error, check this line first. The escape hatch, if you ever want the app up
  without Datadog, is `MANAGEMENT_DATADOG_METRICS_EXPORT_ENABLED=false` in the same file.

---

## Step 6 — systemd

Create `/etc/systemd/system/jobtracker.service`:

```ini
[Unit]
Description=Job Application Tracker API
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=jobtracker
Group=jobtracker
EnvironmentFile=/etc/jobtracker/jobtracker.env

# Explicit -Xmx, NOT MaxRAMPercentage. On a 1 GB box 50% is a 512 MB heap with nothing
# left for metaspace, thread stacks, code cache, Nginx and the OS (CLAUDE.md §6).
# The collector is SerialGC — the JVM already picks it on a 1-core sub-2 GB machine.
# Do not override it; G1 and ZGC are both wrong at this size.
ExecStart=/usr/bin/java -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k -jar /opt/jobtracker/app.jar

Restart=on-failure
RestartSec=5
SuccessExitStatus=143

# A cap, because swap does not save you from a leak — it just makes the box thrash for a
# long time and take SSH down with it. Capped, the JVM alone is killed and Restart brings
# it back in seconds. A fast restart is a better failure mode than an unreachable host.
MemoryHigh=700M
MemoryMax=850M

NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable jobtracker
```

Do **not** start it yet — there is no JAR.

---

## Step 7 — First deploy, by hand

Get one deploy working manually before automating it. When CI later fails you will know
whether the problem is the deploy or the pipeline.

**On your laptop:**

```bash
cd backend && ./mvnw clean verify          # 103 tests
scp target/jobtracker-0.0.1-SNAPSHOT.jar deploy@<VPS_IP>:/opt/jobtracker/app-manual.jar

cd ../frontend && npm ci && npm run build
rsync -avz --delete dist/ deploy@<VPS_IP>:/var/www/jobtracker/
```

**On the server:**

```bash
cd /opt/jobtracker && ln -sfn app-manual.jar app.jar
sudo systemctl start jobtracker
sudo systemctl status jobtracker          # active (running)
journalctl -u jobtracker -f               # watch it boot

curl -s localhost:8080/actuator/health    # {"status":"UP"}
```

If it will not start, `journalctl -u jobtracker -n 100` is the answer. The usual causes are
in Step 5.

---

## Step 8 — Nginx + TLS

Copy the three config files from the repo (they are already written and were verified under
`nginx -t` plus real request/response testing — see `CLAUDE.md §6`, 2026-09-02):

```bash
# from your laptop, in the repo root
scp deploy/nginx-jobtracker.conf deploy@<VPS_IP>:/tmp/
scp deploy/jobtracker-proxy.conf deploy/jobtracker-security-headers.conf deploy@<VPS_IP>:/tmp/

# on the server
sudo mkdir -p /etc/nginx/snippets
sudo mv /tmp/jobtracker-proxy.conf /tmp/jobtracker-security-headers.conf /etc/nginx/snippets/
sudo mv /tmp/nginx-jobtracker.conf /etc/nginx/sites-available/jobtracker
sudo ln -sfn /etc/nginx/sites-available/jobtracker /etc/nginx/sites-enabled/jobtracker
sudo rm -f /etc/nginx/sites-enabled/default        # its server_name _ would shadow ours
```

`nginx -t` **will fail right now** — the two `:443` blocks have no certificate yet. That is
expected. Get one:

```bash
sudo certbot --nginx -d app4jobtrack.me -d www.app4jobtrack.me
sudo nginx -t          # NOW it must pass
sudo systemctl reload nginx
sudo systemctl list-timers | grep certbot     # auto-renew armed
```

**Both `-d` flags are required.** The vhost has a `:443` server for `www` whose only job is
to 301 to the apex; without a cert covering `www` that block cannot load. It exists because
otherwise `www` is served by the app itself, Spring builds the OAuth `redirect_uri` from the
`www` Host, and Google rejects it — a failure that only appears on the `www` hostname and
survives every test done on the apex.

Confirm the headers survived certbot's edit:

```bash
curl -sI https://app4jobtrack.me/ | grep -iE "content-security-policy|x-frame|strict-transport"
curl -sI https://www.app4jobtrack.me/ | grep -i location     # -> https://app4jobtrack.me/
```

---

## Step 9 — Atlas and Google

**Atlas** — console → Network Access → Add IP Address → `<VPS_IP>/32`. Not `0.0.0.0/0`.
Remove your laptop's entry once the deploy works, or leave it if you still run locally.

**Google** — console.cloud.google.com → Credentials → your OAuth client → Authorized
redirect URIs. Add exactly:

```
https://app4jobtrack.me/login/oauth2/code/google
```

Keep `http://localhost:5173/login/oauth2/code/google` for local dev. Do **not** add the
`www` form — the vhost guarantees `www` never reaches the app.

Changes can take a few minutes to take effect on Google's side.

---

## Step 10 — Smoke test

```bash
curl -sI http://app4jobtrack.me/            # 301 -> https://app4jobtrack.me/
curl -s  https://app4jobtrack.me/ | head -5 # index.html
curl -s -o /dev/null -w '%{http_code}\n' https://app4jobtrack.me/api/companies   # 401
curl -s https://app4jobtrack.me/actuator/health                                  # denied
```

That last one **should not** return health JSON — `/actuator` is not proxied, and
`SecurityConfig` only permits health on loopback.

Then in a browser: load the site, sign in with Google, create a company and an application,
add a stage, check the dashboard. That is the Phase 4 acceptance bar.

**Carrying the local backfill over to Atlas**, if you want it:

```bash
mongodump --uri "mongodb://localhost:27017/jobtracker" --archive=/tmp/local.gz --gzip
mongorestore --uri "<ATLAS_URI>" --archive=/tmp/local.gz --gzip --nsFrom 'jobtracker.*' --nsTo 'jobtracker.*'
```

Verify with `GET /api/stats` against the live app before deleting anything local.

---

## Step 11 — CI/CD

**Generate a deploy-only SSH key** (on your laptop, no passphrase — CI cannot type one):

```bash
ssh-keygen -t ed25519 -f ~/.ssh/jobtracker_deploy -N "" -C "github-actions-jobtracker"
ssh-copy-id -i ~/.ssh/jobtracker_deploy.pub deploy@<VPS_IP>
ssh -i ~/.ssh/jobtracker_deploy deploy@<VPS_IP> 'echo ok'
```

**Scoped sudo** — on the server, `sudo visudo -f /etc/sudoers.d/jobtracker-deploy`:

```
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart jobtracker
```

Exactly that one command. Not blanket sudo. Missing this is the most common first-deploy
stall: the CI restart step hangs on a password prompt and times out.

```bash
# -l checks the permission without running the command
sudo -u deploy sudo -n -l /usr/bin/systemctl restart jobtracker
```

**GitHub secrets** — repo → Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `SSH_PRIVATE_KEY` | contents of `~/.ssh/jobtracker_deploy` (the private half) |
| `SSH_HOST` | `<VPS_IP>` |
| `SSH_USER` | `deploy` |
| `SSH_KNOWN_HOSTS` | output of `ssh-keyscan <VPS_IP>` |

`SSH_KNOWN_HOSTS` is not in `PLAN.md`'s list but you want it — the alternative is
`StrictHostKeyChecking=no`, which turns off exactly the check that would catch a MITM.

The two workflow files (`.github/workflows/backend.yml`, `frontend.yml`) still need writing.
Shape, per `PLAN.md`:

- **backend.yml**, on push to `main` touching `backend/**`: `mvn -B verify` (Testcontainers
  works on GitHub runners, and this already produces the JAR — do **not** follow it with a
  second `package -DskipTests`) → `scp` to `/opt/jobtracker/app-<sha>.jar` → repoint the
  `app.jar` symlink → prune to the last 3 → `ssh sudo systemctl restart jobtracker` → poll
  `/actuator/health` on loopback until `UP`, with a timeout.
- **frontend.yml**, on push to `main` touching `frontend/**`: `npm ci` → `npm run build` →
  `rsync --delete dist/` to `/var/www/jobtracker`.

Keep the last 3 JARs: with no Docker there is no image-tag rollback, and those symlink
targets are the rollback (`CLAUDE.md §6`).

---

## Step 12 — Backups — **deferred, not done**

Removed from this runbook by decision, 2026-09-02. Recorded rather than silently dropped,
because the exposure is specific:

**Atlas M0 has no automated backups of any kind.** Until this step is done, the job search
lives in exactly one place, and `CLAUDE.md §3` names the `mongodump` cron as the reason M0
was acceptable over self-hosting at all — that argument is currently unbacked. There is no
undelete and no point-in-time restore on M0; an accidental `DELETE /api/applications/{id}`
or a dropped collection is permanent.

Cheap interim measure, if you want one before the real thing:

```bash
mongodump --uri "<ATLAS_URI>" --archive=~/jobtracker-$(date +%F).archive.gz --gzip
```

Run it by hand now and then and keep the file off the laptop's only disk. Thirty seconds,
and it converts "no copy" into "a stale copy", which is a different category of problem.

**To restore this step in full** — the `backup-mongo.sh` script, the systemd service and
timer, the rclone/Object Storage setup and the restore test — see `PLAN.md` Phase 4
"Backups", or `git show <commit-before-this-one>:deploy/RUNBOOK.md`.

> Note the bucket must be **private** when this is picked back up. The dumps contain
> recruiter names, emails and phone numbers, plus your own compensation expectations.

---

## Done when

- [ ] `https://app4jobtrack.me` loads the SPA
- [ ] Google login works end to end in a browser
- [ ] Full CRUD against the live app: company → application → stage → dashboard reflects it
- [ ] `curl -sI https://app4jobtrack.me/` shows CSP, HSTS and `X-Frame-Options`
- [ ] `https://www.app4jobtrack.me/` 301s to the apex
- [ ] `/api/companies` unauthenticated returns 401; `/actuator/health` is not public
- [ ] Push to `main` auto-deploys within a few minutes
- [ ] ~~A backup archive is in Object Storage and a test restore succeeded~~ — **deferred**, see Step 12
- [ ] You are entering real applications

---

## Operations

```bash
sudo systemctl status jobtracker
journalctl -u jobtracker -f
journalctl -u jobtracker --since "1 hour ago" -p err
sudo systemctl restart jobtracker
```

### Rollback

```bash
ls -lt /opt/jobtracker/app-*.jar          # newest first
cd /opt/jobtracker && ln -sfn app-<previous-sha>.jar app.jar
sudo systemctl restart jobtracker
```

### Memory — the binding constraint on this box

```bash
systemctl status jobtracker | grep Memory   # RSS
free -m
vmstat 5
```

**`si`/`so` are the columns that matter.** Steady non-zero swap-in/out means the app is
living in swap — lower `-Xmx`, do not add more swap. A large "swap used" number with
`si`/`so` at zero is harmless: cold pages parked, which is what swap is for.

Rough 1 GB budget: OS 150–250 MB, Nginx ~15 MB, JVM RSS 450–550 MB at `-Xmx256m`. If the
OOM killer appears in `dmesg`, the JVM is what it kills.

If startup is painfully slow, `-XX:TieredStopAtLevel=1` trades steady-state throughput for
faster warmup — a good trade for one user.

---

## Gotchas, ranked by how much time they cost

1. **Oracle's double firewall** (Step 3). Security List *and* iptables. The #1 cause of
   "site unreachable".
2. **Missing `X-Forwarded-Proto`** → Spring builds an `http://` OAuth `redirect_uri` →
   Google rejects it → login fails outright. Handled by `jobtracker-proxy.conf` plus
   `server.forward-headers-strategy: framework` (already set in `application-prod.yml`).
   If you rewrite the vhost by hand, keep it.
3. **`www` reaching the app** → same `redirect_uri_mismatch`, but only on `www`, so it
   survives testing on the apex. Handled by the `:443` www block. Run certbot with both
   `-d` flags.
4. **Atlas SRV string with no database name** → `Database name must not be empty` at
   startup.
5. **Sudoers not scoped/missing** → CI restart hangs on a password prompt.
6. **A changed public IP** → breaks the Atlas allowlist and TLS at once. Keep it reserved.
7. **`certbot --nginx` rewrites the vhost.** After any renewal or re-run, re-check
   `nginx -t` and that the security headers still come back.
