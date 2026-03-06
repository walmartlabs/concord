# Repository Instructions

## Workflow

- Keep ongoing multi-step progress notes in `PROGRESS.md` and update them periodically during the task.
- When a task includes a small production change plus supporting tests, commit the production change separately once it is ready.
- Follow the existing git commit message pattern: `module-name: short description`.
- When creating commits for this repo, skip GPG signing so commits do not block on interactive signing prompts.

## Java Style

- In new `.java` files, prefer `var` for local variable declarations.
- Do not mix `var` and explicit local variable types within the same `.java` file.
- In tests and other setup-heavy code, assign complex object construction to its own local variable instead of nesting it inline inside another constructor or method call.
