<div align="center">

# 🌊 SAGAR
### Subsidy & Grant Administration Platform

**A centralized platform for transparent, secure and traceable subsidy management**

[🌐 Live Demo](https://sagar-subsidy-platform.onrender.com) · [💻 Source Code](https://github.com/MaheshDivekar22/Subsidy-platform-test)

![Java](https://img.shields.io/badge/Java-Spring_Boot-orange) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-Backend-6DB33F) ![MySQL](https://img.shields.io/badge/MySQL-Aiven-4479A1) ![Thymeleaf](https://img.shields.io/badge/Thymeleaf-Frontend-005F0F) ![Docker](https://img.shields.io/badge/Docker-Render-2496ED)

</div>

## 📌 Overview

**SAGAR** is an academic government subsidy and grant administration platform developed as part of an Infosys internship project. It connects citizen applications, scheme-specific eligibility checks, document verification, multi-level officer approvals, staged disbursement, compliance evidence and administrative monitoring in a single web application.

For example, a beneficiary applying for a women's entrepreneurship grant can discover the scheme, submit supporting documents, receive eligibility feedback and follow the application's progress through verification and approval.

> **Project scope:** SAGAR is a demonstration/academic platform. It is not an official government service and does not claim to transfer real government funds.

## ✨ Features

- **Citizen portal:** Registration, login, OTP verification, scheme discovery, applications, document uploads, status tracking and resubmission flows.
- **Eligibility engine:** Scheme-specific rules, required and optional documents, validation and a 100-point eligibility scoring model.
- **Field verification:** Document review, officer remarks and appropriate forwarding or correction actions.
- **District review:** Eligibility assessment, decisions and application routing.
- **Finance review:** Sanction-related decisions and financial verification workflows.
- **Disbursement and compliance:** Milestone schedules, installment tracking, utilization proof, compliance flags and release controls, including freezes where applicable.
- **Administration:** Officer management, application monitoring, audit history and analytics/reporting views.
- **Security:** Spring Security role-based authorization and server-side workflow checks.

## 🏛️ Scheme Categories

1. Education Subsidy
2. Farmer Support Grant
3. Housing Assistance Scheme
4. Women Entrepreneurship Grant
5. Senior Citizen Assistance
6. Divyang Disability Support
7. Rural Skill Development

Each scheme has its own eligibility criteria and document requirements.

## 🔄 Application Lifecycle

```text
Citizen registration / login
           ↓
Scheme discovery and selection
           ↓
Application and document submission
           ↓
Eligibility evaluation
           ↓
Field Officer verification
           ↓
District Officer review
           ↓
Finance review / sanction
           ↓
Milestone-based disbursement schedule
           ↓
Installment release and utilization proof
           ↓
Compliance verification / flags / release controls
           ↓
Monitoring, audit and reporting
```

Applications may be returned for correction, re-verification or rejected depending on the applicable rules and officer decisions.

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot, Spring MVC |
| Security | Spring Security |
| Frontend | Thymeleaf, HTML, CSS, JavaScript |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL hosted on Aiven |
| Document storage | Cloudinary |
| OTP delivery | Google Apps Script integration |
| Build and deployment | Maven, Docker, Render |
| Version control | Git, GitHub |

## 🏗️ Architecture

```text
Browser (Citizen / Officers / Admin)
                 ↓
        Thymeleaf views
                 ↓
     Spring MVC controllers
                 ↓
   Services and workflow rules
                 ↓
      Spring Data JPA
                 ↓
         Aiven MySQL

External services: Cloudinary (documents), Google Apps Script (OTP)
Security: Spring Security and role-based access
Hosting: Render (Docker)
```

## 🚀 Live Deployment

**Website:** https://sagar-subsidy-platform.onrender.com

The Spring Boot application is deployed on Render using Docker, with Aiven MySQL for persistent data and Cloudinary for document storage. The free Render instance may need additional time to start after inactivity.

## ⚙️ Local Development

### Prerequisites

- A compatible JDK (use the version configured in `pom.xml`)
- Git and Maven, or the Maven Wrapper if included
- Access to a MySQL database and the configured external services

### Clone and build

```bash
git clone https://github.com/MaheshDivekar22/Subsidy-platform-test.git
cd Subsidy-platform-test
```

On Windows PowerShell, if the Maven Wrapper is present:

```powershell
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

Or use installed Maven:

```bash
mvn clean package
mvn spring-boot:run
```

Open http://localhost:8081 after successful startup.

### Configuration

The project reads configuration from environment variables. **Never commit credentials, tokens or real beneficiary documents.**

| Variable | Purpose |
|---|---|
| `DB_HOST` | MySQL host |
| `DB_PORT` | MySQL port |
| `DB_NAME` | Database name |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `SAGAR_APP_BASE_URL` | Public base URL (use `http://localhost:8081` locally) |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Matching Cloudinary API secret |
| `SAGAR_OTP_WEBAPP_URL` | OTP Apps Script endpoint |
| `SAGAR_OTP_SECRET` | Shared OTP integration secret |
| `ADMIN_EMAIL` | Admin bootstrap email |
| `ADMIN_PASSWORD` | Admin bootstrap password |

`PORT` is supplied by Render; local startup defaults to port `8081` in the current configuration. Set the database connection values for the database you intend to use. Do not use `ddl-auto=create` or `create-drop` against a database containing important data.

## 🧪 Testing Checklist

- Citizen registration, login and OTP verification
- Scheme discovery, eligibility scoring and document upload
- Application submission, status and resubmission
- Field and district review decisions
- Finance approval and sanction views
- Milestone schedule, utilization proof, flags and release controls
- Admin dashboards, analytics and audit records
- Role-based access and unauthorized-request handling

Use **synthetic test data** and non-sensitive sample documents for demonstrations.

## 🔒 Security Notes

- Keep the GitHub repository private until historical commits have been reviewed for credentials and uploaded personal documents.
- Removing a sensitive file in a later commit does **not** remove it from Git history.
- Rotate any credentials that were previously committed or exposed.
- Store deployment secrets only in Render environment settings or another secure secret store.
- Use backups and controlled schema migrations before treating the system as production-ready.

## 🔭 Future Improvements

- Authorized government identity and treasury integrations
- More extensive automated and security testing
- Improved accessibility, monitoring and operational resilience
- Enhanced regional reporting and notifications

## 👥 Project Context

Developed collaboratively as an **Infosys internship academic project** focused on subsidy administration, eligibility verification, staged disbursement and transparency.

---

<div align="center">

**SAGAR — From application to accountable fund utilization.**

[Live Website](https://sagar-subsidy-platform.onrender.com) · [GitHub Repository](https://github.com/MaheshDivekar22/Subsidy-platform-test)

</div>
