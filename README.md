# Expense Management System

A web application for submitting, approving and reporting company expenses.
Employees raise expenses with receipts, managers and other approvers decide them step by step,
finance exports approved expenses, and admins configure who approves what.

Built with **Java 17**, **Spring Boot 4**, **PostgreSQL** and a plain HTML/JavaScript frontend served by the same app.

---

## Features

| Role | What they can do |
| --- | --- |
| **Employee** | Create expenses, attach receipts (PDF, PNG, JPEG), submit, see the approval path, fix and resubmit rejected expenses |
| **Manager** | Everything an employee can do, plus approve or reject expenses in their queue and see their decision history |
| **Finance** | Raise their own expenses, decide finance-review steps, filter approved expenses, export them as CSV |
| **Admin** | Manage users, departments, categories and approval rules (create, edit, activate, deactivate); approve expenses for people who have no manager; read the audit log |

Business rules the system enforces:

- **Approval path.** Every expense starts with a manager approval. The first approver is, in order of precedence:
  1. the Manager named in the matching rule's first step,
  2. the employee's direct manager,
  3. the manager of the employee's department,
  4. the **admin team**, when nobody can act as the person's manager (for example, a department head without a manager of their own, or finance staff).

  The matching rule's remaining steps (a named person or the finance team) follow. Nobody appears twice in one path, and nobody approves their own expense.
- **Rule matching and priority.** Only one rule is applied per expense. Of all active rules whose category, department and amount range match, the one with the **lowest priority number** wins, so give specific rules (Travel over ₹50,000) a lower number than general ones (Travel of any amount). Priorities are unique among active rules. Rules can be edited at any time; their steps are copied onto an expense when it is submitted, so edits never change expenses already in flight.
- **Shared queues.** Finance-team and admin-team steps go to everyone in that role, and the first one to decide wins. People never see their own expenses in a queue: a finance user's expense is reviewed by another finance user, and submission is refused if there is no other active finance user.
- **Rejection** needs a reason. The employee edits the expense and resubmits, which starts a new approval round. Earlier rounds stay visible as history.
- **One open expense per category.** An employee cannot start a second expense in a category while one is still a draft or awaiting approval.
- **Receipts** are checked by file content (not just the extension), limited to 5 MB, and required for categories that demand them.
- **Valid emails only**, in the form `name@company.com`.
- **Audit log** of every important action: who did it, when, and the details.

---

## Quick start (local, no installation of PostgreSQL needed)

**Requirements:** JDK 17 and Maven 3.9+.

```bash
export BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-password'
mvn spring-boot:test-run
```

Open <http://localhost:8080> and sign in as `admin@company.com` with the password you chose.

This mode starts an embedded PostgreSQL automatically. Its data is kept in `./data/postgres`
and receipts in `./data/receipts`, so everything survives a restart. To start over with an empty
database, stop the app and delete the `data/` folder.

> Stop the app with **Ctrl+C** so the embedded database shuts down cleanly.

### First-time setup as admin

1. **Departments:** create a department (for example, Engineering).
2. **Users:** create a manager in that department, then employees (optionally with a direct manager) and a finance user.
3. **Departments:** set the department's manager. Employees without a direct manager are approved by this person.
4. **Categories:** create categories such as Travel or Meals, and mark which require a receipt.
5. **Approval rules** (optional): for example, *Travel over ₹10,000 → Finance team*.

The Users page shows who approves each person first, and lists anyone whose expenses go to the admin team because they have no manager.

---

## Running in production

Use a real PostgreSQL database and run the packaged application:

```bash
mvn clean package
java -jar target/expense-management-0.0.1-SNAPSHOT.jar
```

Configuration is done with environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/expense_management` | JDBC URL of the database |
| `DB_USERNAME` | `expense_app` | Database user |
| `DB_PASSWORD` | `expense_app` | Database password |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@company.com` | Email of the first admin |
| `BOOTSTRAP_ADMIN_PASSWORD` | *(empty)* | Creates the first admin on startup if no admin exists yet |
| `RECEIPT_STORAGE_DIR` | `./data/receipts` | Folder where receipt files are stored |

The database schema is created and upgraded automatically by Flyway on startup.

**Before going live:**

- Serve the app over **HTTPS only** (for example, behind a reverse proxy). Sign-in uses HTTP Basic authentication, which must never travel over plain HTTP.
- Use a strong, unique `BOOTSTRAP_ADMIN_PASSWORD`, and change it or remove the variable once the first admin exists.
- Back up both the database and the receipt storage folder.

---

## Running the tests

```bash
mvn test
```

The tests need no external services. Each test run uses its own temporary embedded PostgreSQL,
so they never touch your local data. They cover the domain rules, the repositories and the
full HTTP workflows, including security.

---

## Project structure

```
src/main/java/com/company/expense
├── config/        Startup configuration (bootstrap admin, receipt storage)
├── controller/    REST endpoints
├── dto/           Request and response objects
├── entity/        Domain model with its business rules (Expense, ApprovalRule, User, ...)
├── enums/         Statuses, roles, approver types
├── exception/     Error types and the global error handler (RFC 9457 problem details)
├── repository/    Spring Data JPA repositories
├── security/      HTTP Basic authentication and role-based access
├── service/       Use cases and transactions
└── util/          CSV writer
src/main/resources
├── db/migration/  Flyway SQL migrations
└── static/        Frontend (HTML, CSS, JavaScript modules)
docs/              Design documentation
```

Design notes:

- **Business rules live in the entities** (for example, `Expense.submit` builds the approval path), so they are enforced no matter which endpoint calls them, and they are unit-tested without a database.
- **Services own transactions** and return DTOs; entities never leave the service layer.
- **Concurrent actions are safe:** decisions lock the expense row, so two approvers can never decide the same step twice.

---

## API overview

All endpoints are under `/api` and use HTTP Basic authentication. Errors are returned as JSON problem details.

| Area | Endpoints | Access |
| --- | --- | --- |
| Session | `GET /auth/me` | Any signed-in user |
| Categories | `GET /categories` | Any signed-in user |
| Expenses | `GET, POST /expenses` · `GET, PUT, DELETE /expenses/{id}` · `POST /expenses/{id}/submit` | Employee, Manager, Finance (own expenses) |
| Receipts | `POST /expenses/{id}/receipts` · `GET, DELETE /expenses/{id}/receipts/{receiptId}` | Owner; approvers can download |
| Approvals | `GET /approvals` · `GET /approvals/history` · `POST /approvals/{id}/approve` · `POST /approvals/{id}/reject` | Manager, Finance, Admin |
| Finance | `GET /finance/expenses` · `GET /finance/expenses/filters` · `GET /finance/expenses/export` | Finance |
| Admin | `/admin/users` · `/admin/departments` · `/admin/categories` · `/admin/approval-rules` · `/admin/audit-logs` | Admin |

Finance reports accept the optional filters `from`, `to` (ISO dates), `departmentId`, `categoryId` and `employeeId`.

---

## Known limitations

- Lists are not paginated yet.
- All amounts are in Indian rupees (INR).
- The finance department filter uses each employee's current department.
- Authentication is HTTP Basic. A session- or token-based login is a natural next step for larger deployments.
