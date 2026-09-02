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
`stages[]` and dates).

> **What actually happened:** this step was skipped and done *after* the deploy, through the
> live UI. It cost nothing that time, but the ordering above is still the right one — a schema
> problem found here is cheap, and the same problem found once data is in Atlas is not.

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
sudo apt install -y nginx certbot python3-certbot-nginx ssl-cert rclone fail2ban unattended-upgrades
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

**Give `deploy` its SSH key now**, not in Step 11. It has a home directory and no
`authorized_keys`, and Step 4e turned password authentication off — so until this is done
`deploy` cannot log in at all, and Step 7's `scp` fails with a bare
`Permission denied (publickey)`.

Note the shape of this: `ssh-copy-id deploy@<VPS_IP>` **cannot work**, because it has to
authenticate as `deploy` in order to install a key for `deploy`. The key has to go in
through `ubuntu`, which is the only account that can currently get in.

```bash
# on your laptop — no passphrase, because CI cannot type one
ssh-keygen -t ed25519 -f ~/.ssh/jobtracker_deploy -N "" -C "github-actions-jobtracker"

cat ~/.ssh/jobtracker_deploy.pub | ssh ubuntu@<VPS_IP> \
  "sudo mkdir -p /home/deploy/.ssh \
   && sudo tee -a /home/deploy/.ssh/authorized_keys >/dev/null \
   && sudo chown -R deploy:deploy /home/deploy/.ssh \
   && sudo chmod 700 /home/deploy/.ssh \
   && sudo chmod 600 /home/deploy/.ssh/authorized_keys"

ssh -i ~/.ssh/jobtracker_deploy deploy@<VPS_IP> 'whoami && id'
```

That must print `deploy` and show `jobtracker` among its groups — the group membership is
what gives it write access to `/opt/jobtracker`.

The `700` on `.ssh` and `600` on `authorized_keys` are not cosmetic: sshd silently ignores
keys from a world-readable directory and reports the same
`Permission denied (publickey)`, with nothing in the client output to say why.

**If you use `~/.ssh/config` aliases, mind what they match on.** A `Host myserver` block
matches the *alias*, not the address inside it — so if you connect as `ssh myserver`, then
`ssh ubuntu@<VPS_IP>` matches nothing, falls back to the default identities
(`~/.ssh/id_ed25519`, `~/.ssh/id_rsa`), and fails with the same
`Permission denied (publickey)` on a box you can otherwise reach fine. Substitute your alias
wherever this runbook writes `ubuntu@<VPS_IP>`.

Worth adding an alias for the deploy account too, and pinning both to one key:

```
Host jobtracker-deploy
    HostName <VPS_IP>
    User deploy
    IdentityFile ~/.ssh/jobtracker_deploy
    IdentitiesOnly yes
```

`IdentitiesOnly yes` matters once you have a few keys: ssh offers them one at a time and
sshd's default `MaxAuthTries` is 6, so unrelated keys tried first can exhaust the limit and
fail a connection that would otherwise work. With the alias in place, Step 7 is just
`scp … jobtracker-deploy:/opt/jobtracker/app-manual.jar`.

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
MONGODB_URI=mongodb+srv://USER:PASS@cluster0.sfdtyrk.mongodb.net/jobtracker?retryWrites=true&w=majority&appName=jobtracker
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
APP_ALLOWED_EMAILS=duy.le.21.ng@gmail.com
APP_MCP_TOKEN=
APP_BASE_URL=https://app4jobtrack.me
DD_API_KEY=
DD_SITE=us5.datadoghq.com
EOF
sudo chmod 600 /etc/jobtracker/jobtracker.env
sudo chown jobtracker:jobtracker /etc/jobtracker/jobtracker.env
```

### Coming back to edit this file

You will. It is mode 600 owned by `jobtracker`, so:

```bash
sudo cat  /etc/jobtracker/jobtracker.env      # look
sudo nano /etc/jobtracker/jobtracker.env      # edit

# Then always re-assert this. Some editors write a new file and rename it over the old
# one, which silently leaves it owned by root. The service runs as `jobtracker` and then
# cannot read its own config — and the failure does not say "permission denied", it looks
# like every variable is unset, which sends you hunting in completely the wrong place.
sudo chown jobtracker:jobtracker /etc/jobtracker/jobtracker.env
sudo chmod 600 /etc/jobtracker/jobtracker.env
ls -l /etc/jobtracker/jobtracker.env          # -rw------- 1 jobtracker jobtracker
```

Two rules for the contents: **no spaces around `=` and no quotes** unless the value contains
`#`, in which case single-quote the whole value or systemd reads the rest of the line as a
comment. And **do not edit it with `sed -i` from the command line** — it works, but the
password ends up in your shell history.

Once the service is running, `EnvironmentFile` is read only at process start, so any edit
here needs `sudo systemctl restart jobtracker`. A `daemon-reload` alone will not pick it up.

Generate the MCP token **on the VPS**, straight into the file — the token never appears on
screen, and the shell records the literal `$(openssl rand -hex 32)` rather than what it
expanded to, so it stays out of history and scrollback:

```bash
sudo -u jobtracker sed -i "s|^APP_MCP_TOKEN=.*|APP_MCP_TOKEN=$(openssl rand -hex 32)|" \
  /etc/jobtracker/jobtracker.env
```

Generating it on the laptop instead works identically — `openssl rand` reads the OS CSPRNG
either way — but then it lives in a terminal buffer and probably a clipboard, and still has
to reach the server. One copy of record, on the box, is simpler.

Phase 6 needs the same value on the laptop for the MCP server. Read it back then:

```bash
sudo grep APP_MCP_TOKEN /etc/jobtracker/jobtracker.env
```

None of this is needed for Phase 4 — leaving it blank is a valid state, not a broken one
(see the `APP_MCP_TOKEN` note below).

Five things in that file worth understanding:

- **`MONGODB_URI` must carry the database name**, and the password must be URL-safe.
  Atlas's dialog gives you `...mongodb.net/?appName=Cluster0` — note `/?`, with nothing
  between. `jobtracker` goes in that gap or the app dies at startup with `Database name
  must not be empty`. Substitute `USER`/`PASS` and delete the angle brackets Atlas wraps
  its placeholders in. The string is a **URI**, so a password containing `@ : / ? # [ ] %`
  must be percent-encoded — an unencoded `@` splits the userinfo in the wrong place and
  surfaces as a host-lookup or auth error nowhere near the password:
  `python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" 'p@ss'`.
  Easiest alternative: give the DB user an alphanumeric password.

  **And it very likely needs `&authSource=admin`.** This is the sting in the tail of adding
  the database name. Per the connection-string spec, when `authSource` is not given it
  defaults to *the database named in the URI*, falling back to `admin` only when there is
  none. Atlas creates every database user in `admin` — so the string Atlas hands you
  (`.../?appName=...`, no database) authenticates fine, and the moment you insert
  `/jobtracker` to satisfy Spring, the auth database silently becomes `jobtracker`, where the
  user does not exist. Atlas answers `bad auth : authentication failed`, which reads as a
  wrong password and is not one. The tell is `unable to authenticate using mechanism
  "SCRAM-SHA-1"` in the same message: Atlas uses SCRAM-SHA-256 for users it recognises, so a
  SHA-1 fallback means it could not find the user at all.
- **`APP_ALLOWED_EMAILS` is the entire authorization model.** Anyone on the internet can
  start a Google login — that is Google's page, not ours — so Google only establishes *who
  someone is*. This line is what decides whether that person may use the app, checked in
  `AllowlistOidcUserService.verify()` alongside Google's `email_verified` claim (an
  unverified address is a string the account holder typed, not an identity).

  It is comma-separated and matched case-insensitively — values are trimmed and lowercased
  when bound, so `Duy.Le.21.NG@Gmail.com` in the file still matches. The address must be the
  one Google actually returns for the account you sign in with; a Workspace alias or a
  secondary address will not match, and you will get a 403 that looks like a broken login.

  **Empty admits nobody, and that is deliberate** (`CLAUDE.md §6`). An allowlist that fails
  open is not an allowlist: a deploy that forgets this variable locks you out, which is
  recoverable in thirty seconds by editing this file and restarting; the alternative admits
  the internet, which is not.

  **Adding a second address grants full access, not partial.** The app is single-user by
  design (`§14`) — there are no per-document ownership checks anywhere, because there has
  only ever needed to be one owner. A second person on this line sees every application,
  every note, and every compensation figure.

- **`APP_MCP_TOKEN` empty means the MCP server cannot authenticate.** That is the intended
  fail-closed default, not a bug — the same fail-closed reasoning as the allowlist above.
  Fill it before Phase 6; it is not needed for Phase 4.
- **`APP_BASE_URL` has no trailing slash** and must be the **apex**, matching Google exactly.
- **`DD_API_KEY` must hold the real key** — Phase 0 is complete and one is generated, so
  paste it in along with `DD_SITE`. Metrics then start flowing from the first boot;
  Micrometer pushes straight to the Datadog API over HTTPS and needs no Agent, so this is
  safe to have on during the deploy.

  **`DD_SITE` must match your org's Datadog site, and the obvious default is probably
  wrong.** Datadog runs several (`datadoghq.com` = US1, `us3`, `us5`, `datadoghq.eu`,
  `ap1`), a key is valid only on its own, and a mismatch fails in the least helpful way
  available: metrics are rejected, nothing appears in the UI, and the app looks perfectly
  healthy. This org is **US5**. Confirm rather than trust — the site is in the browser URL
  when you are logged in, and this settles it from the server:

  ```bash
  DD_KEY=$(sudo sed -n 's/^DD_API_KEY=//p' /etc/jobtracker/jobtracker.env)
  for S in datadoghq.com us3.datadoghq.com us5.datadoghq.com datadoghq.eu ap1.datadoghq.com; do
    printf '%-22s %s\n' "$S" "$(curl -s "https://api.$S/api/v1/validate" -H "DD-API-KEY: $DD_KEY")"
  done
  ```

  Whichever returns `{"valid":true}` is yours. Check the key's shape while you are there:
  `echo -n "$DD_KEY" | wc -c` should be **32**. A 40-character value is an *Application*
  key rather than an API key — they sit on the same settings page and only the API key
  works here.

  **If you leave it blank the app will not start at all.** `application-prod.yml` enables
  the Micrometer Datadog registry, which auto-configures on classpath presence alone and
  refuses to boot without a key — `apiKey was 'null' but it is required` (`CLAUDE.md §6`).
  The failure looks nothing like a Datadog problem, so if the service dies at Step 7 with an
  opaque startup error, check this line first. The escape hatch, if you ever want the app up
  without Datadog, is `MANAGEMENT_DATADOG_METRICS_EXPORT_ENABLED=false` in the same file.

---

## Step 6 — systemd

First confirm Java is where the unit will look for it — the Temurin package wires it up
through `update-alternatives`, so it should be `/usr/bin/java`. If it is not, change
`ExecStart` to match:

```bash
command -v java        # expect /usr/bin/java
```

Now write the unit. Paste the whole block, `EOF` included — the quoted `<<'EOF'` stops the
shell touching anything inside it:

```bash
sudo tee /etc/systemd/system/jobtracker.service >/dev/null <<'EOF'
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
EOF
```

`tee` as root leaves it owned by `root:root` at mode 644, which is what systemd wants — the
unit is not a secret, and the secrets it reads live in the `EnvironmentFile` from Step 5.

Check it parses before relying on it:

```bash
sudo systemd-analyze verify /etc/systemd/system/jobtracker.service
```

Silence means it is fine. A complaint about `/opt/jobtracker/app.jar` is expected at this
point — the JAR arrives in Step 7 — but anything about a malformed directive or a missing
`EnvironmentFile` is real and worth fixing now.

```bash
sudo systemctl daemon-reload
sudo systemctl enable jobtracker
```

`enable` only registers it to start at boot. Do **not** `start` it yet — there is no JAR,
and a failed start now just means `Restart=on-failure` retries every 5 seconds until you
notice.

---

## Step 7 — First deploy, by hand

Get one deploy working manually before automating it. When CI later fails you will know
whether the problem is the deploy or the pipeline.

> **Do Step 9 first.** Atlas access has to work before the app can boot at all, so the
> allowlist entry and a verified database user belong *before* this step, not after — the
> numbering here is historical. `IndexInitializer` is an `ApplicationRunner`, so it runs
> after Tomcat is already listening and is the first thing to actually open a Mongo
> connection. When it throws, the failure looks like this, which reads as a crash rather
> than a config problem:
>
> ```
> ... Tomcat started on port 8080
> ... GracefulShutdown : Graceful shutdown complete
> systemd[1]: jobtracker.service: Main process exited, code=exited, status=1/FAILURE
> ```
>
> The real cause is far higher up the journal. Two to tell apart:
> `MongoTimeoutException` / `No server chosen` means the IP is not allowlisted;
> `bad auth : authentication failed` means the allowlist is fine and the **credentials** are
> wrong — most often punctuation in the password that was never percent-encoded.

**On your laptop:**

```bash
cd backend && ./mvnw clean verify          # 112 tests (25 unit, 87 integration)
scp -i ~/.ssh/jobtracker_deploy target/jobtracker-0.0.1-SNAPSHOT.jar \
    deploy@<VPS_IP>:/opt/jobtracker/app-manual.jar

cd ../frontend && npm ci && npm run build
rsync -avz --delete -e 'ssh -i ~/.ssh/jobtracker_deploy' dist/ deploy@<VPS_IP>:/var/www/jobtracker/
```

**On the server — and mind which user runs what.** These two lines need different accounts,
which is the easiest thing here to get wrong:

- `deploy` owns the JAR and the symlink. It is in the `jobtracker` group, so it can write
  `/opt/jobtracker`.
- `ubuntu` is you, and is **not** in that group — it gets `r-x` on that directory. Running
  the symlink step as `ubuntu` fails with `ln: failed to create symbolic link 'app.jar':
  Permission denied`, even though the `scp` a moment earlier succeeded (that went over the
  deploy account).

```bash
# as deploy — repoint the symlink. CI does exactly this on every deploy, so if it needs
# sudo now it will need sudo then, and Step 11's sudoers entry covers only systemctl.
ssh jobtracker-deploy 'cd /opt/jobtracker && ln -sfn app-manual.jar app.jar && ls -l'

# as ubuntu — start it and watch
ssh app4jobtracker      # or ubuntu@<VPS_IP>
sudo systemctl start jobtracker
sudo systemctl status jobtracker --no-pager   # active (running)
journalctl -u jobtracker -f                   # watch it boot

curl -s localhost:8080/actuator/health        # {"status":"UP"}
```

If the symlink step fails as `deploy` too, the group membership did not take — `id` will not
list `jobtracker`. Fix with `sudo usermod -aG jobtracker deploy` and reconnect; group changes
only apply to new sessions.

Check the JAR is readable by the service account while you are there — it runs as
`jobtracker`, not `deploy`:

```bash
ssh jobtracker-deploy 'ls -l /opt/jobtracker/'   # want -rw-r--r--
```

A restrictive local umask can land it `600` owned by `deploy`, and the service then fails to
start with an error that says nothing about permissions.

If it will not start, `journalctl -u jobtracker -n 100` is the answer. The usual causes are
in Step 5.

To test the database credentials on their own, without starting the app:

```bash
URI=$(sudo sed -n 's/^MONGODB_URI=//p' /etc/jobtracker/jobtracker.env)
mongodump --uri "$URI" --collection __probe__ --archive=/dev/null 2>&1 \
  | sed -E 's|(//)[^:]*:[^@]*(@)|\1USER:PASS\2|'
```

**The mask on the output is not optional.** On a connection failure `mongodump` prints the
URI it tried — *including the password* — into your terminal, and from there into scrollback,
a screenshot, or a support thread. Piping through that `sed` replaces the userinfo before you
ever see it. If you have already run an unmasked probe, treat the password as disclosed and
rotate it in Atlas → Database Access.

Silence from that command means it connected. Any `bad auth` comes back with the credentials
already masked.

**Do not test it by sourcing the env file in a shell** — `set -a; . /etc/jobtracker/jobtracker.env`
looks right and quietly lies. A systemd `EnvironmentFile` is not a shell script, and the URI
contains `&` (from `&w=majority&appName=…`), which bash reads as "run in background": the
assignment is truncated at the first `&` and discarded. `$MONGODB_URI` then comes out empty,
`mongodump` falls back to its `localhost:27017` default, and you get a connection-refused
error against your own machine that has nothing to do with the problem you were chasing.
systemd's parser has no such behaviour, so the app sees the full value either way.

The `sed` form above avoids it because command substitution captures the line literally and
`"$URI"` is quoted at the point of use.

---

## Step 8 — Nginx + TLS

Copy the three config files from the repo (they are already written and were verified under
`nginx -t` plus real request/response testing — see `CLAUDE.md §6`, 2026-09-02):

```bash
# from your laptop, in the repo root. -i because a bare deploy@<VPS_IP> will not match a
# `Host` alias in ~/.ssh/config and will fail with Permission denied (publickey).
scp -i ~/.ssh/jobtracker_deploy \
    deploy/nginx-jobtracker.conf deploy/jobtracker-proxy.conf \
    deploy/jobtracker-security-headers.conf \
    deploy@<VPS_IP>:/tmp/

# on the server
sudo mkdir -p /etc/nginx/snippets
sudo mv /tmp/jobtracker-proxy.conf /tmp/jobtracker-security-headers.conf /etc/nginx/snippets/
sudo mv /tmp/nginx-jobtracker.conf /etc/nginx/sites-available/jobtracker
sudo ln -sfn /etc/nginx/sites-available/jobtracker /etc/nginx/sites-enabled/jobtracker
sudo rm -f /etc/nginx/sites-enabled/default        # its server_name _ would shadow ours
```

Check it loads *before* running certbot — it should, with no certificate yet, because the
`:443` blocks point at Ubuntu's snakeoil placeholder for exactly this moment:

```bash
sudo nginx -t
```

That matters more than it looks. `certbot --nginx` runs `nginx -t` itself and refuses to do
anything if it fails — so a config that cannot load without a certificate can never obtain
one through the nginx plugin. Chicken and egg. The placeholder is the egg; certbot rewrites
both `ssl_certificate` lines to `/etc/letsencrypt/live/...` on first issue.

If it complains the snakeoil files are missing: `sudo apt install ssl-cert`.

Now get the real certificate:

```bash
sudo certbot --nginx -d app4jobtrack.me -d www.app4jobtrack.me
sudo nginx -t          # still passes, now with real certs
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

**The deploy key already exists** — it was generated and installed in Step 4d, because
Step 7 needs it. Nothing to do here but confirm it still works:

```bash
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

**The workflows are written** — `.github/workflows/backend.yml` and `frontend.yml`. Both
trigger on push to `main` filtered by path, so a CSS change does not restart the JVM, and
both carry `workflow_dispatch` so you can re-run a deploy without an empty commit.

Three things in them worth knowing rather than discovering:

- **The health check runs on the box, over ssh — not from the runner.** `/actuator/health`
  is permitted on loopback only (`SecurityConfig.LOOPBACK_HEALTH`) and Nginx does not proxy
  `/actuator`, so a runner curling the public URL would get a 401 and fail every deploy. It
  polls for up to 180s because the app takes ~40s to boot on a 1/8-OCPU host.
- **`concurrency` queues deploys rather than cancelling them.** Two overlapping runs would
  race on the `app.jar` symlink and the restart; cancelling one mid-restart is worse than
  making it wait.
- **The last three JARs are kept.** With no Docker there is no image tag to roll back to, so
  those files and the symlink *are* the rollback (`CLAUDE.md §6`).

Deploys only fire from `main`. While you are on `phase-4-deploy` nothing runs — merge first.

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

All met as of 2026-09-02 except the deferred backup step.

- [x] `https://app4jobtrack.me` loads the SPA
- [x] Google login works end to end in a browser
- [x] Full CRUD against the live app: company → application → stage → dashboard reflects it
- [x] `curl -sI https://app4jobtrack.me/` shows CSP, HSTS and `X-Frame-Options`
- [x] `https://www.app4jobtrack.me/` 301s to the apex
- [x] `/api/companies` unauthenticated returns 401; `/actuator/health` is not public
- [x] Push to `main` auto-deploys within a few minutes
- [~] ~~A backup archive is in Object Storage and a test restore succeeded~~ — **deferred**, see Step 12
- [x] You are entering real applications

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
