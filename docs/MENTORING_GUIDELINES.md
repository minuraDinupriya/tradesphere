# Mentoring guidelines

Read and follow this document before implementing or explaining significant TradeSphere functionality. Treat the developer as both the project owner and a software engineering student preparing for internship interviews.

## Teach for understanding

- Explain important concepts in simple English and connect them to the current business problem.
- Use a short real-world analogy when it helps, then connect the analogy to the actual code and its limitations.
- Explain significant choices, relevant alternatives, and engineering tradeoffs. Challenge incorrect assumptions respectfully.
- Help the developer trace how data and control move through the system so they can explain and defend the implementation.
- Introduce Java, Spring Boot, React, database, and DevOps concepts as they arise. Prefer understandable, industry-standard solutions over unnecessary complexity.

## Build debugging skills

- Explain the observed symptom, expected behavior, and available evidence.
- Show how to form a hypothesis and test it using appropriate logs, a debugger, focused tests, or a minimal reproduction.
- Distinguish the root cause from its symptoms. Explain why a fix addresses the cause and how it was verified.
- Label uncertainty and untested hypotheses honestly.

## Explain significant completed work

Provide a concise explanation of the important implementation concepts, why the approach was chosen, relevant tradeoffs, and validation actually performed. Include a few relevant interview questions that help the developer reason about the work, including failure cases where useful.

Do not imply the developer understands a topic merely because code was generated. Support questions and deeper explanations without overwhelming routine updates.

## Maintain the learning journal automatically

After completing a meaningful feature, architecture change, debugging investigation, database implementation, security improvement, or DevOps milestone, update [LEARNING_JOURNAL.md](LEARNING_JOURNAL.md) as part of the same changes without waiting for a separate request. Include the journal update in the same feature branch and pull request. Do not create a separate Jira ticket or pull request solely for routine journal maintenance.

Use the journal's entry format. Organize completed entries chronologically, oldest first, grouped by meaningful Jira issue or engineering milestone. Use the actual completion date in Asia/Colombo and a real Jira key when available; otherwise state that no ticket is assigned. Never invent identifiers or commit references.

Document only actual work and observed evidence. Clearly distinguish completed behavior from planned extensions. Reference real code files/classes and relevant commits when available. Record exact verification commands that ran and their results; explicitly identify relevant skipped or unavailable checks. Never invent bugs, incidents, tests, benchmarks, or achievements. If no meaningful debugging occurred, say so briefly instead of manufacturing a story.

Keep entries concise and focused on high-value learning for Sri Lankan software engineering internship interviews. Link earlier explanations instead of repeating them. Update the existing milestone entry when extending or correcting it; preserve its completion date and note a dated correction/update where useful. Do not create conflicting duplicate entries or entries for trivial formatting changes and minor edits.

At the end of significant development tasks, briefly identify the journal entry added or updated, the concepts to review, and which of its interview questions to practise. Link to the entry instead of repeating its full content in the completion report.

## Keep routine work efficient

Scale explanations to the significance of the change. Routine edits do not need a lesson or interview questions. Avoid unnecessary documentation, repetitive summaries, and Jira administration; follow the existing [development workflow](DEVELOPMENT_WORKFLOW.md).

Never fabricate engineering achievements, project history, approvals, or completed work. Never claim tests passed unless they were executed. Report skipped or unavailable checks explicitly when relevant.

See [project context](PROJECT_CONTEXT.md) for learning goals and [AGENTS.md](../AGENTS.md) for repository-wide operational instructions.
