Atuin History Mover - Move or copy shell history between directories

USAGE:
  bun run src/index.ts <command> [options]

COMMANDS:
  move <from> <to>      Move history entries from one directory to another
  copy <from> <to>      Copy history entries from one directory to another
  list <dir> [limit]    List history entries for a directory (default limit: 10)
  count <dir>           Count history entries for a directory

OPTIONS:
  --dry-run            Show what would be changed without making changes
  --db <path>          Path to Atuin database (overrides ATUIN_DB_PATH)
  --help, -h           Show this help message

EXAMPLES:
  # Move history from old to new project directory
  bun run src/index.ts move ~/projects/old-name ~/projects/new-name

  # Copy history (keeps both)
  bun run src/index.ts copy ~/projects/template ~/projects/new-project

  # Preview changes without modifying
  bun run src/index.ts move ~/old ~/new --dry-run

  # List recent commands from a directory
  bun run src/index.ts list ~/projects/myapp 20

  # Count history entries
  bun run src/index.ts count ~/projects/myapp

ENVIRONMENT:
  ATUIN_DB_PATH        Custom path to Atuin database
  XDG_DATA_HOME        XDG data directory (default: ~/.local/share)
`);
