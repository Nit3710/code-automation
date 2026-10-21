# AI Autonomous Software Maintenance Platform - Product Requirements Document (PRD)

## 1. Product Vision

Build a web-based platform that connects to a user's software repository, automatically checks its health, detects problems, investigates failures, attempts verified fixes in an isolated environment, and eventually supports controlled deployment.

The goal is to reduce repetitive software maintenance work while keeping the developer in control of risky changes.

**Core idea:** Connect your repository → Monitor it → Detect issues → Diagnose them → Attempt fixes → Verify the changes → Create a pull request → Deploy safely.

The platform should not claim that every issue can be fixed automatically. Every attempted fix must have a visible result and verification status.

---

## 2. Problem We Are Solving

Developers and agencies repeatedly spend time on:
- Running builds and tests.
- Finding why an application has failed.
- Reading logs and investigating errors.
- Fixing small recurring bugs.
- Checking whether recent changes broke something.
- Preparing pull requests and maintenance reports.
- Monitoring deployments and investigating failures.

Our platform will automate as much of this workflow as is safe and practical.

---

## 3. Target Customers

### Initial customers
1. Individual software developers.
2. Freelancers maintaining client projects.
3. Small development agencies.
4. Small teams managing multiple repositories.

### Future customers
1. Software companies.
2. Engineering teams maintaining large applications.
3. Companies seeking controlled AI-assisted remediation and deployment.

Initially, we should focus on **Java/Spring Boot** and **React/Next.js** projects rather than trying to support every technology.

---

## 4. Product Goals

1. Connect GitHub repositories securely.
2. Detect project type and configure build/test commands.
3. Run manual and scheduled health checks.
4. Store execution logs and historical results.
5. Use AI to explain failures and suggest solutions.
6. Attempt fixes in isolated branches or worktrees.
7. Verify changes using automated checks.
8. Create pull requests for human review.
9. Add staging deployment and smoke tests.
10. Introduce controlled production deployment only after reliability and rollback are proven.

---

## 5. Non-Goals for the First Version

The first MVP will not:
- Guarantee automatic resolution of every bug.
- Modify the production branch directly.
- Deploy to production without explicit authorization.
- Support every programming language and cloud provider.
- Replace a complete CI/CD platform.
- Train a custom foundation model.
- Execute unrestricted repository commands.
- Build a complex multi-agent architecture before the basic workflow works.

---

## 6. Core User Workflow

1. User creates an account.
2. User creates a workspace.
3. User connects GitHub.
4. User selects a repository.
5. The platform detects the project's technology stack.
6. User confirms the build and test commands.
7. User runs the first health check.
8. The platform executes checks in an isolated environment.
9. The dashboard displays the result, logs, duration, commit SHA, and report.
10. User enables scheduled checks.
11. In a later phase, the user asks AI to investigate or attempt a fix.
12. AI creates a proposed change in an isolated branch.
13. The platform runs the required checks again.
14. The user reviews the diff and verification report.
15. The platform opens a pull request if authorized.
16. Deployment is introduced in later phases with approval and rollback safeguards.

---

## 7. Functional Requirements

### FR-01: Authentication and Workspace Management
- Sign up, sign in, and sign out.
- Create and manage workspaces.
- Associate repositories and jobs with the correct workspace.
- Enforce authorization on every backend request.
- Prevent one workspace from accessing another workspace's data.

### FR-02: GitHub Integration
- Connect a GitHub account or GitHub App installation.
- Display repositories the user has authorized.
- Connect and disconnect repositories.
- Detect revoked or expired access.
- Request only the permissions needed for the selected features.
- Never expose access tokens in logs or frontend responses.

### FR-03: Project Detection
Initially support:
- **Java projects**: Java 17, Maven, Spring Boot, JUnit tests.
- **JavaScript projects**: React, Next.js, npm-based projects initially.

The platform should detect common project files such as `pom.xml`, `build.gradle`, `package.json`, and lockfiles.
It should suggest build and test commands, but users must be able to inspect and configure them.

### FR-04: Health Check Engine
- Run checks manually.
- Execute build, test, lint, and configured validation commands.
- Capture output, exit codes, duration, and commit SHA.
- Support queued, running, passed, failed, timed-out, cancelled, and internal-error states.
- Enforce execution time, memory, CPU, and output limits.
- Keep repository code execution isolated from the main API.

### FR-05: Scheduled Monitoring
- Schedule checks at supported intervals.
- Pause, edit, or delete schedules.
- View scheduled and completed runs.
- Prevent unintended duplicate overlapping jobs.
- Track skipped or failed scheduled runs.
- Apply workspace-level usage limits.

### FR-06: AI Failure Analysis
When a check fails, AI should:
1. Summarize the failure.
2. Identify relevant error messages and logs.
3. Explain the likely root cause.
4. Distinguish confirmed facts from hypotheses.
5. Suggest a solution.
6. Identify what should be tested next.

*AI must never say that a problem has been fixed unless a change was actually applied and the required checks were run successfully.*

### FR-07: AI-Assisted Fixes
*(After health checks and reporting are reliable)*
- Create an isolated branch or worktree.
- Generate a proposed code patch.
- Display the exact diff.
- Run required tests and builds.
- Record the results.
- Allow the user to discard the patch.
- Never push directly to the default branch.
- Require human review and approval by default.

### FR-08: Pull Request Automation
- Open a pull request only when authorized.
- Include a summary of the issue and proposed change.
- Include tests executed and their results.
- Link to the relevant run report.
- Disclose failed checks and remaining uncertainty.
- Leave merge decisions to the user by default.

### FR-09: Reports and History
Every run should store:
- Repository and branch.
- Commit SHA.
- Start and finish timestamps.
- Duration.
- Check results and exit codes.
- Relevant logs.
- AI analysis, when enabled.
- Proposed changes, when applicable.
- Execution environment and error details.

Provide a dashboard and downloadable or copyable Markdown reports.

### FR-10: Deployment Automation *(Later Phase)*
- Begin with one supported staging provider.
- Require explicit deployment configuration.
- Run required checks before deployment.
- Perform post-deployment smoke tests.
- Record deployed commit and deployment status.
- Implement and test rollback before enabling production automation.
- Require explicit approval for production deployments by default.

### FR-11: Usage and Billing *(Later Phase)*
- Usage tracking.
- Repository and run limits.
- AI usage limits.
- Subscription plans.
- Billing history.
- Clear quota and cost information.

---

## 8. Non-Functional Requirements

### Security
- Encrypt sensitive credentials at rest.
- Use TLS for network communication.
- Use least-privilege repository permissions.
- Redact secrets from logs.
- Run repository commands in isolated workers.
- Restrict execution time, network access, resources, and credentials.
- Treat repository files, comments, logs, and test output as untrusted input.
- Protect against prompt injection in repository content.
- Maintain an audit trail for important operations.

### Reliability
- Handle worker crashes and timeouts.
- Use bounded retries and backoff.
- Prevent duplicate jobs where possible.
- Distinguish application failures from platform failures.
- Never report a failed check as successful.

### Privacy
- Enforce workspace isolation.
- Define data retention and deletion rules.
- Avoid sending repository data to an AI provider without appropriate disclosure and authorization.
- Do not expose one customer's repository or logs to another customer.

### Cost Control
- Limit execution duration and output size.
- Track AI usage.
- Apply per-workspace quotas.
- Limit concurrent jobs.
- Use ordinary deterministic checks before expensive AI analysis where practical.

---

## 9. Proposed Technical Architecture

### Frontend: Next.js / React
- Authentication screens
- Workspace and repository dashboard
- Run history and logs
- Scheduling interface
- AI analysis and proposed diffs
- Pull request and deployment status

### Backend API: Java 17 + Spring Boot
- Authentication and authorization
- Workspace and repository management
- GitHub integration
- Job creation and scheduling APIs
- Run status and report APIs
- AI provider abstraction
- Usage tracking and audit events

### Database: PostgreSQL
Initial entities:
- `users`
- `workspaces`
- `workspace_members`
- `repositories`
- `repository_configurations`
- `schedules`
- `jobs`
- `job_logs`
- `reports`
- `ai_analysis`
- `fix_attempts`
- `pull_requests`
- `deployments`
- `audit_events`
- `usage_records`

### Background Worker
- Fetch authorized repository content.
- Prepare an isolated execution environment (Docker / container sandboxing).
- Run configured checks.
- Collect logs and exit status.
- Store results and notify the backend.
- Execute AI fix attempts in isolated branches or worktrees.

*The API must not execute untrusted repository code inside the API process.*

### AI Layer
- Provider-neutral adapter (OpenAI / Anthropic / Gemini / etc.).
- Failure summarization & root-cause hypotheses.
- Patch generation and review.
- Output treated as untrusted until validated.

---

## 10. Phased Development Roadmap

- **Phase 0:** Validate the Problem
- **Phase 1:** Repository Health Check MVP
- **Phase 2:** Scheduled Monitoring
- **Phase 3:** AI Diagnosis
- **Phase 4:** Verified AI Fixes
- **Phase 5:** Staging Deployment
- **Phase 6:** Production Controls and SaaS
