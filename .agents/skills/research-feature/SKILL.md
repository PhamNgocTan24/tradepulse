---
name: research-feature
description: >-
  Automates picking uncompleted tasks from tasks.md (or promoting new tasks from backlog.md), researching design guidelines in docs/development/, generating a {feature}_executive.md design plan, obtaining user approval, and executing the plan upon a /proceed command.
---

# Research Feature & Task Execution Agent

## Overview
This skill provides a structured workflow for picking, designing, and implementing features in the TradePulse project. It ensures that no code is written without a design review and that all implementations align with the architectural patterns and rules established in the repository.

---

## Workflow

### Phase 1: Task Selection & Promotion
When this skill is activated, or when you are asked to research the next feature:
1. Open and read [tasks.md](file:///Users/newtan/tradepulse/docs/planning/tasks.md).
2. Identify the first uncompleted task marked with `[ ]`.
3. If **all** tasks in `tasks.md` are marked as completed (`[x]`):
   - Open and read [backlog.md](file:///Users/newtan/tradepulse/docs/planning/backlog.md).
   - Select the next pending Product Backlog Item (PBI) with a priority status of `🔨 In Progress` or `⬜ Pending`.
   - Add this PBI as a new uncompleted task in `tasks.md` under the active sprint section.
4. Select this task as the active task to process.

### Phase 2: Design & Research
Align your technical design with project conventions before coding:
1. Read the developer guidelines in the `docs/development/` folder:
   - [Patterns.md](file:///Users/newtan/tradepulse/docs/development/Patterns.md) (15 distributed system patterns)
   - [Syntax.md](file:///Users/newtan/tradepulse/docs/development/Syntax.md) (Java do/donts, constructor injection, BigDecimals)
   - [FolderStructure.md](file:///Users/newtan/tradepulse/docs/development/FolderStructure.md) (package conventions)
2. Read the system design documents if applicable:
   - [Architecture.md](file:///Users/newtan/tradepulse/docs/architecture/Architecture.md) (database isolation, Kafka topologies, WebSockets)
   - [ApiContracts.md](file:///Users/newtan/tradepulse/docs/api/ApiContracts.md) (REST request/response mapping)
3. Research the current codebase for existing references (e.g., check `services/` and `shared/` directories for related files).

### Phase 3: Executive Plan Generation
Create a design file named `docs/planning/{feature}_executive.md` (e.g., `docs/planning/matching_engine_executive.md`) using the following exact template:

```markdown
# EXECUTIVE DESIGN PLAN: [Task ID - Feature Name]

> **Branch Name:** `task/[short-feature-name]`
> **Target Services:** `[service-name]`
> **Status:** ⬜ Draft | ⬜ Approved by Tech Lead | ⬜ In Progress | ⬜ Completed

---

## 1. Context & Architectural Guardrails
- [ ] **Data Types:** All balance, price, and quantity variables MUST use Java `BigDecimal` and SQL `DECIMAL(18, 8)`. NO double/float.
- [ ] **Dependency Injection:** Use Constructor Injection via Lombok `@RequiredArgsConstructor`. NO field-level `@Autowired`.
- [ ] **Database Boundary:** No cross-database/cross-service queries or direct repository calls.
- [ ] **Stateless Rules (If matching-engine):** No database calls in hot path.

---

## 2. Infrastructure Changes

### 2.1. Database Schema updates (PostgreSQL / MongoDB)
*Specify Service, DB Type, Table/Collection name, and DDL/Document Schema details.*

### 2.2. Kafka Integration
*Specify Topic, Key, and Event Payload DTO structure.*

### 2.3. Redis Caching
*Specify Key Pattern, Data Type, and TTL.*

---

## 3. Step-by-Step Coding Plan
*List files to modify or create with package name, class name, annotations, and key method signatures.*

### 3.1. [Service-A] Changes
#### 📂 Package: `[package.path]`
- **[NEW/MODIFY] `[ClassName].java`**:
  * Details...

---

## 4. Definition of Done & Acceptance Criteria
- **AC-1:** [Details...]
- **AC-2:** [Details...]

---

## 5. Verification Plan

### 5.1. Automated Unit / Integration Tests
*Specify test classes and the exact command to run tests.*
- Command: `mvn test -pl services/[service] -Dtest=[TestClass]`

### 5.2. Manual Verification
*Specify manual steps (e.g., cURL requests).*
```


### Phase 4: User Approval (Consent Gate)
Do **NOT** write any implementation code yet.
1. Present the selected task and the link to the generated `{feature}_executive.md` file to the user.
2. Ask the user for explicit confirmation:
   > "Tôi đã chuẩn bị bản kế hoạch thiết kế cho task **[Tên Task]** tại [file_executive](file:///Users/newtan/tradepulse/docs/planning/{feature}_executive.md). Bạn có đồng ý thực hiện task này không? Hãy cho tôi biết nếu cần chỉnh sửa gì thêm."
3. Wait for the user to confirm. If the user suggests modifications, update `{feature}_executive.md` accordingly.
4. The user indicates final consent by saying "Proceed", ticking a choice, or calling the `/proceed` trigger.

---

## Execution Protocol (`/proceed`)

When the user enters `/proceed docs/planning/{feature}_executive.md` (or simply says `/proceed {feature}_executive.md`):
1. **Branch Checkout:** You **MUST** check out a new git branch first before coding:
   ```bash
   git checkout -b task/short-task-name   # or feature/short-feature-name
   ```
2. **Implementation:** Read the `{feature}_executive.md` carefully and write the required code.
   - Follow all Java conventions (Constructor injection, `@RequiredArgsConstructor`, no raw `@Autowired`).
   - Use `BigDecimal` and `DECIMAL(18, 8)` for monetary amounts.
   - Ensure database boundaries are respected (no cross-db queries).
3. **Verification:** Run the verification commands specified in the plan (e.g., `mvn test -pl services/{service}`).
4. **Status Update:**
   - Mark the task as completed (`[x]`) in `docs/planning/tasks.md`.
   - Update the status of the corresponding requirement to `Done` (or check the box) in `docs/planning/backlog.md`.
   - Let the user know the branch name and summarize the completed tasks.

---

## Common Mistakes
- **Writing code before user approval:** Do not touch Java source code files during Phase 2 or 3.
- **Forgetting to check out a branch:** Writing code directly on `main` or `master` violates Git rules.
- **Ignoring Design Guidelines:** Writing fields with `@Autowired` or using `double`/`float` instead of `BigDecimal`. Always re-read `docs/development/Syntax.md` and `docs/development/Patterns.md` during Phase 2.
