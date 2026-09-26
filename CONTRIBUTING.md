# Contributing to Aux

This document covers how our group works in this repo. Read it before your first PR.

## Branch Protection

`main` is protected:
- No direct pushes — all changes go through a Pull Request (PR)
- 1 approval required before merging
- No force pushes, no branch deletion
- This applies to everyone, including admins

## Branching

Always branch off `main` before starting work:

```bash
git checkout main
git pull origin main
git checkout -b <prefix>/<short-description>
```

**Prefixes:**
- `frontend/` — Vue components, UI, styling
- `backend/` — Spring Boot controllers, entities, DB logic
- `fix/` — bug fixes
- `chore/` — config, tooling, docs, non-feature work

Examples: `frontend/profile-page`, `backend/compatibility-endpoint`, `fix/upload-crash`

## Making a PR

1. Push your branch: `git push -u origin <branch-name>`
2. Open a PR into `main` on GitHub
3. Write a short description of what changed and why
4. Tag a reviewer (Justin for now, or the other backend dev for backend-only PRs)
5. Address review comments, then it gets merged

**Keep PRs small.** One feature or fix per PR. Small PRs get reviewed faster and are much easier to fix if something's wrong.

## Before You Start Coding

Always pull the latest `main` first:

```bash
git checkout main
git pull origin main
git checkout -b your-new-branch
```

This avoids painful merge conflicts later.

## Local Setup

See `README.md` for how to get the backend and frontend running locally.

## Questions

If you're stuck on git/GitHub steps, ask before force-pushing, deleting branches, or doing anything that looks irreversible — mistakes are much easier to fix before they happen than after.
