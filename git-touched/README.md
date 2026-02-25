---
name: git-touched
purpose: CLI for listing files touched by commits, ranges, and branches
---

# git-touched

Usage:

```bash
./git-touched <command> [args]
```

Commands:

- `last <N>`: files touched by the last `N` commits
- `range <hash1> <hash2>`: files touched by a commit range (inclusive)
- `commit <hash>`: files touched by a specific commit
- `branch <name>`: files touched by a branch vs merge-base
