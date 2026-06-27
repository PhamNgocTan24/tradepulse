# TradePulse — System Diagrams Index

This folder contains the Mermaid diagrams representing the TradePulse microservice architecture and core workflows.

| Diagram | Type | Description |
|---|---|---|
| 📐 [System Architecture Diagram](file:///Users/newtan/tradepulse/docs/architecture/diagrams/system_architecture.md) | Component Diagram | Visualizes the layout of the 9 microservices, 2 shared packages, polyglot databases (Postgres, MongoDB, Redis), Kafka topics, and external APIs. |
| 🔑 [Authentication & Security Flow](file:///Users/newtan/tradepulse/docs/architecture/diagrams/auth_flow.md) | Sequence Diagram | Details the login process (RS256 JWT, TOTP 2FA), logout blacklisting, and token propagation via Gateway headers. |
| 📈 [Market Data Streaming Flow](file:///Users/newtan/tradepulse/docs/architecture/diagrams/market_data_flow.md) | Component & State Diagram | Illustrates how ticks flow from Binance WebSocket, through `market-data-service`, into caches/databases, and broadcast to subscribers. |
| 📥 [Order Placement, Matching & Execution Flow](file:///Users/newtan/tradepulse/docs/architecture/diagrams/order_flow.md) | Sequence & Activity Flowchart | Traces order creation, async matching in `matching-engine` memory-based orderbook, portfolio balance settlement, and transaction logging. |
| 🔔 [Price Alert & Notification Flow](file:///Users/newtan/tradepulse/docs/architecture/diagrams/price_alert_flow.md) | Sequence Diagram | Outlines alert registration, real-time price matching triggers, and the dual-delivery path (WebSocket push & AWS SES email). |

---

### How to render these diagrams locally

These diagrams are written using [Mermaid.js](https://mermaid.js.org/). 
* **VS Code**: Install the extension **Markdown Preview Mermaid Support** to render them directly.
* **GitHub**: GitHub automatically renders markdown code blocks labeled with `mermaid`.
* **Mermaid Live Editor**: Copy-paste any raw `.md` mermaid block code into [mermaid.live](https://mermaid.live/) to export as SVG or PNG.
