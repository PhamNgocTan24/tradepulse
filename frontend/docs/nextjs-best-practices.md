# Next.js Coding Best Practices & Frontend Guidelines

This document defines the coding standards and project structure conventions for the Next.js frontend in the TradePulse system.

---

## 1. Language & Code Comments
- **Mandatory Rule:** All inline code comments, documentation, and explanations within source files MUST be written in **English**.
- Comments should be concise, clear, and focus on the *why* rather than the *what*.

---

## 2. TypeScript Types & Interfaces Management
- **No Direct Declarations:** Never declare TypeScript `interface` or `type` definitions directly within files that handle application logic (e.g., Route Handlers, Pages, Components).
- **Separation of Files:** All DTOs, API Request/Response shapes, and Component Props must be defined inside the `src/types/` directory.
  - For example: `src/types/auth.ts` holds all authentication-related interfaces.
  - Use explicit ES6 export/import statements to reuse interfaces across the application.
- **Strict Typing:** Never use the `any` type. Always define precise types for inputs and outputs.

---

## 3. Directory Structure (App Router)
The project adheres to the Next.js App Router structure under the `src/` directory:

```text
src/
├── app/                  # Pages and API Route Handlers (File-based Routing)
│   ├── api/              # API BFF routes (called directly by the browser client)
│   │   └── auth/         # Authentication endpoints (login, logout, refresh)
│   ├── login/            # Login page route
│   ├── trade/            # Trading view (Protected Route)
│   ├── layout.tsx        # Root layout for the application
│   └── globals.css       # Global styles (Tailwind CSS v4)
├── components/           # Reusable UI Components
│   ├── ui/               # Shared low-level UI elements (Button, Input, Modal, etc.)
│   └── trade/            # Domain-specific components (OrderForm, OrderBook, etc.)
├── lib/                  # Library configurations, API client helpers, shared utilities
├── types/                # TypeScript interface and type files (*.ts)
└── proxy.ts              # Server-side routing protection (Authentication filter, formerly middleware.ts)
```

---

## 4. Data Fetching & Architecture (BFF Pattern)
- **BFF (Backend-For-Frontend):** The browser client never communicates directly with the Spring Boot backend microservices. Instead, it calls local Next.js API Routes (Route Handlers) which act as a BFF proxy.
- **Secure Token Storage:** Access Tokens and Refresh Tokens received from the Java backend must be stored securely inside **HttpOnly, Secure, SameSite=Lax** cookies from the server-side. Client-side JavaScript must never have access to these cookies to mitigate XSS (Cross-Site Scripting) vulnerabilities.
- **Proxy Protection:** Use `proxy.ts` (formerly `middleware.ts` in Next.js 15) at the edge/server level to intercept incoming requests, validate the session cookie, and redirect unauthorized users before any page renders.

---

## 5. CSS & Styling (Tailwind CSS v4)
- Leverage Tailwind CSS v4 utility-first approach.
- Avoid writing raw CSS. Extend global design tokens using `@theme` directive in `globals.css` if necessary.
- Build components with **Dark Mode** support by default (suited for a trading platform interface).

---

## 6. TypeScript vs. Java Mapping (For Java Developers)
- **Interface/Type** (TS) maps to **DTO/Record** (Java).
- **Route Handler / Server Action** (Next.js) maps to **Controller** (Java Spring Boot).
- **Proxy / Middleware** (Next.js) maps to **Filter/Interceptor** (Java Spring Security).
