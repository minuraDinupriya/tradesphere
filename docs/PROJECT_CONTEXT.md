# Project context

## Business scenario

Lanka Distribution Group is a fictional Sri Lankan B2B distributor operating three warehouses. TradeSphere will support the connected workflow from receiving stock to viewing availability, creating customer orders, reserving stock, and fulfilling or cancelling orders.

The scenario supplies realistic engineering constraints; it is not a claim of a real client engagement. Warehouse names, locations, products, customers, transaction volumes, and existing business processes have not been specified. Any future sample values must be labeled fictional.

## Developer and learning purpose

This is a solo flagship portfolio project. The primary objective is to become a capable software engineer and prepare for Sri Lankan software engineering internship interviews beginning in January 2027. Feature count and technology count are secondary to understanding and correctness.

The developer should be able to explain:

- How a request moves through React, the backend, and PostgreSQL.
- Why modules own particular data and behavior.
- How transactions and concurrency control protect stock.
- How authentication, authorization, and organization isolation differ.
- Which tests support a claim and which risks remain unverified.
- How migrations, containers, CI, and deployment make changes repeatable.

## Mentoring agreement

The AI agent acts as a coding partner and senior engineering mentor. Significant decisions need simple explanations, relevant alternatives, and tradeoffs. Important Java, Spring Boot, React, database, and DevOps concepts should be explained as they arise, without turning every edit into a lecture.

Prefer small working increments the developer can inspect and defend. Challenge incorrect assumptions, avoid unnecessary complexity, and do not introduce tools just to make the portfolio appear impressive. Honest limitations and reasoning are part of the portfolio evidence.

Do not invent teammates, customer conversations, approvals, incidents, project history, or completed work. Jira is a lightweight planning aid, using substantial outcome-based deliverables rather than many administrative subtasks.

## Known constraints and assumptions

- **Specified:** three warehouses in the fictional business; the initial MVP uses one warehouse per order.
- **Specified:** tenant-owned records must be isolated by organization. The exact membership and role model is unresolved.
- **Working assumption:** the initial demonstration will use one fictional distributor organization; tests should use at least two organizations to exercise isolation. This does not remove tenant boundaries.
- **Working assumption:** inventory quantities are whole units initially. Fractional units and unit conversions need explicit agreement before implementation.
- **Unresolved:** warehouse identities, product identifiers, pricing/currency/tax behavior, user roles, and customer-management details.
- **Established for TRD-6:** initial dependency versions and foundation tooling; see [architecture](ARCHITECTURE.md). The supplied repository is `minuraDinupriya/tradesphere`; TRD-6 is the engineering foundation issue. Its Jira URL has not been supplied.
- **Unresolved:** hosting and identity mechanism.

These assumptions are proposals for discussion, not prior approved business decisions. Business behavior belongs in [requirements](REQUIREMENTS.md); technical proposals belong in [architecture](ARCHITECTURE.md).

## Success criteria

A credible result is a functioning, tested MVP whose core inventory and order behavior is correct under concurrent requests, whose organization boundaries are enforced, and whose design the developer can explain. A reproducible setup, clear documentation, meaningful pull requests, and honest validation evidence matter more than an expansive technology stack.

The [roadmap](ROADMAP.md) prioritizes this outcome. January 2027 is an interview-preparation target, not a promise that every planned phase will be complete by then.
