# Deploying Student Management System to Render + TiDB Cloud Serverless

This guide walks you step by step through deploying this Spring Boot application permanently on **Render (Free Web Service, Docker runtime)** connected to a **TiDB Cloud Serverless (MySQL-compatible)** database — with zero hardcoded secrets in Git.

> **Security Note:** If any database passwords were previously committed to Git, **rotate (reset) them immediately** in TiDB Cloud and never paste real secrets into code or `application.properties`. All credentials belong exclusively in Render's **Environment Variables**.

---

## Step 1: Set Up the Database on TiDB Cloud (Free Starter Cluster)

1. Go to [https://tidbcloud.com](https://tidbcloud.com) and sign in (or create a free account).
2. Click **Create Cluster**.
3. Choose the **Starter** (Serverless, Free) tier.
4. **Region:** Select **Singapore (`ap-southeast-1`)** (or the AWS region nearest to Render's **Singapore** region) for lowest latency.
5. Give your cluster a name (for example, `student-mgmt-cluster`) and click **Create**.
6. Wait 1–2 minutes until the cluster status becomes **Available**.
7. *(Recommended if using a custom database name)* Create the database/schema:
   - In the left sidebar of your cluster, click **SQL Editor**.
   - Run:
     ```sql
     CREATE DATABASE IF NOT EXISTS student_mgmt_db;
     ```
   - *(Alternatively, you can use TiDB's pre-created default database named `test`, in which case use `test` as `<DB_NAME>` below.)*
8. Click the **Connect** button (top right of your cluster overview):
   - Select **Connect With:** `General` (or `Java / JDBC` if listed).
   - Click **Generate Password** (or **Reset Password** if rotating an existing one) and **copy and save it immediately** — TiDB only shows it once.
   - Note down the following values from the dialog:
     - **Host** (looks like `gateway01.ap-southeast-1.prod.aws.tidbcloud.com`)
     - **Port**: `4000` (TiDB Cloud always uses port **4000**, not 3306)
     - **User** (looks like `<cluster-prefix>.root`)
     - **Password** (the generated password)
9. Build your **JDBC URL** using this exact format (TiDB requires SSL):
   ```text
   jdbc:mysql://<HOST>:4000/<DB_NAME>?sslMode=VERIFY_IDENTITY
   ```
   Example (replace `<HOST>` and `<DB_NAME>` with your own values):
   ```text
   jdbc:mysql://gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000/student_mgmt_db?sslMode=VERIFY_IDENTITY
   ```
   *(If you run into TLS version negotiation issues, append `&enabledTLSProtocols=TLSv1.2,TLSv1.3` to the end of the URL.)*

---

## Step 2: Deploy the Web Service on Render (Free Docker Runtime)

Make sure your changes are pushed to GitHub on `main` first.

### Option A: Manual Web Service Setup (Click-by-Click)

1. Go to [https://dashboard.render.com](https://dashboard.render.com) and sign in with GitHub.
2. Click **New +** (top right) > **Web Service**.
3. Under **Build and deploy from a Git repository**, select your GitHub repository: `mdnadaf/student-management-system-springboot` and click **Connect**.
4. Configure the service settings:
   - **Name:** `student-management-system` (or any unique name)
   - **Language / Runtime:** **Docker**
   - **Branch:** `main`
   - **Region:** **Singapore (Southeast Asia)**
   - **Root Directory:** *(leave blank — `pom.xml` and `Dockerfile` are at the repo root)*
   - **Instance Type:** **Free**
5. Scroll to **Environment Variables** and click **Add Environment Variable** for each of the following:
   | Key | Value |
   | --- | --- |
   | `DB_URL` | `jdbc:mysql://<HOST>:4000/<DB_NAME>?sslMode=VERIFY_IDENTITY` |
   | `DB_USER` | Your TiDB Cloud username (e.g., `<prefix>.root`) |
   | `DB_PASSWORD` | Your generated TiDB Cloud password |
   | `DDL_AUTO` | `update` |

   *(Optional)* You can also override the seeded login credentials on first startup with `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`.
6. Expand **Advanced** and set:
   - **Health Check Path:** `/actuator/health`
7. Click **Create Web Service**.

### Option B: Deploy via `render.yaml` Blueprint

1. In Render Dashboard, click **New +** > **Blueprint**.
2. Connect the `student-management-system-springboot` repository.
3. Render automatically reads `render.yaml` (Docker runtime, Free plan, Singapore region, `/actuator/health` health check, `DDL_AUTO=update`).
4. When prompted for the `sync: false` secrets, paste your values for **`DB_URL`**, **`DB_USER`**, and **`DB_PASSWORD`**.
5. Click **Apply**.

---

## Step 3: How to Read Build and Runtime Logs on Render

1. Open your service in the Render Dashboard and click **Events** or **Logs** in the left menu.
2. **Build Logs** appear first while Docker runs the multi-stage build:
   - Look for `dependency:go-offline`, `mvn -B -q clean package -DskipTests`, and `Build successful`.
   - The first build takes ~2–4 minutes while Maven downloads dependencies; later builds are faster thanks to Docker layer caching.
3. **Deploy / Runtime Logs** appear once the container starts:
   - Look for:
     - `HikariPool-1 - Start completed.` (confirms database connection succeeded)
     - `Seeded default admin user` or `Default admin user '...' already exists; skipping seed.`
     - `Tomcat started on port 10000 (http)` (Render automatically sets `PORT`)
     - `Started StudentmanagementApplication in ... seconds`
   - Once Render hits `GET /actuator/health` and receives `{"status":"UP"}`, the status banner turns green (**Live**).
4. Visit `https://<your-service>.onrender.com/actuator/health` — you should see `{"status":"UP"}`.
5. Visit `https://<your-service>.onrender.com/login` — the Student Management System login page should load cleanly over HTTPS.

---

## Step 4: Troubleshooting Common Deployment Errors

| Error / Symptom | Likely Cause | Fix |
| --- | --- | --- |
| **`Communications link failure`** | Wrong TiDB host/port, missing database name, or TiDB cluster paused/unreachable. | Verify `DB_URL` uses port **`4000`** (not `3306`), has no trailing spaces, and points to an existing database (`test` or one created with `CREATE DATABASE student_mgmt_db;`). Check that your TiDB cluster is **Available**. |
| **`SSL` / `PKIX path building failed` / `Connections using insecure transport are prohibited`** | Missing SSL parameter in `DB_URL` (TiDB requires TLS). | Ensure `DB_URL` ends with `?sslMode=VERIFY_IDENTITY` (or `?sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3`). Never use `useSSL=false` with TiDB Cloud. |
| **Port scan timeout / `"No open ports detected"`** | App crashed before Tomcat started (usually a DB error earlier in logs) or ignored `$PORT`. | Scroll **up** in Render Runtime Logs to find the root Java exception (e.g., wrong `DB_PASSWORD`). Confirm `server.port=${PORT:8080}` is present in `application.properties` and you did not hardcode `PORT` to something mismatched. |
| **Out of Memory (`Exit status 137` / `OOMKilled`)** | Render Free tier has a strict **512 MB RAM** limit. | The Dockerfile already sets `JAVA_TOOL_OPTIONS="-Xmx400m -XX:+UseSerialGC"` and limits HikariCP to 5 connections. If OOM still occurs under load, add an Environment Variable in Render: `JAVA_TOOL_OPTIONS` = `-Xmx300m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC`. |
| **`502 Bad Gateway` on first request** | Container is still finishing Spring Boot startup (~15–30 s) or waking up from sleep. | Wait 30–60 seconds and refresh the page. Check **Logs** to confirm `Started StudentmanagementApplication` appeared and `/actuator/health` returns `200 OK`. |
| **Login redirects to `http://` or CSRF / session error** | Reverse proxy headers not forwarded or browser cached an old session. | Confirm `server.forward-headers-strategy=framework` is in `application.properties` (already configured). Clear site cookies and retry login over `https://`. |

---

## Step 5: Free Tier Sleep Behavior & Demo Tips

Render Free Web Services **automatically spin down (sleep) after 15 minutes of inactivity**.
- The **first request** after sleep triggers a **30–60 second cold start** while Render starts the container and Spring Boot initializes.
- Subsequent requests while awake are fast.

### How to keep it awake for a live demo or interview:
1. **Quickest way:** Open `https://<your-service>.onrender.com/login` **2 minutes before** your demo so the container wakes up and warms its database connection pool.
2. **Automated way:** Set up a free uptime monitor (such as [UptimeRobot](https://uptimerobot.com) or [cron-job.org](https://cron-job.org)) to send an HTTP `GET` request every **10–14 minutes** to:
   ```text
   https://<your-service>.onrender.com/actuator/health
   ```
   *(Be mindful of Render's monthly free instance hours if you leave a pinger running 24/7 — enabling it on demo days is usually best.)*

---

## Local Development

To run locally against a local MySQL database without modifying committed files:
1. Start local MySQL on port `3306`.
2. Either export `DB_PASSWORD` in your terminal or create an untracked `src/main/resources/application-local.properties` (already ignored by `.gitignore` and `.dockerignore`) and run:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   On Windows PowerShell:
   ```powershell
   $env:DB_PASSWORD="your-local-mysql-password"
   .\mvnw.cmd spring-boot:run
   ```
