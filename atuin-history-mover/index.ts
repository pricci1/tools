#!/usr/bin/env bun
import { Database } from "bun:sqlite";
import { existsSync } from "fs";
import { resolve, join } from "path";
import { randomUUID } from "crypto";

// Atuin history entry
interface HistoryEntry {
  id: string;
  timestamp: number;
  duration: number;
  exit: number;
  command: string;
  cwd: string;
  session: string;
  hostname: string;
  deleted_at: number | null;
}

function printUsage(): void {
  console.log(`
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
}

function getAtuinDbPath(): string {
  const envPath = process.env.ATUIN_DB_PATH;
  if (envPath) {
    return envPath;
  }

  const home = process.env.HOME || process.env.USERPROFILE;
  if (!home) {
    throw new Error("Cannot determine home directory");
  }

  // Default path: ~/.local/share/atuin/history.db
  const dataDir = process.env.XDG_DATA_HOME || join(home, ".local", "share");
  return join(dataDir, "atuin", "history.db");
}

// expand ~ and resolve
function normalizePath(path: string): string {
  if (path.startsWith("~/")) {
    const home = process.env.HOME || process.env.USERPROFILE;
    if (!home) {
      throw new Error("Cannot determine home directory");
    }
    path = join(home, path.slice(2));
  }
  return resolve(path);
}

function moveHistory(
  db: Database,
  fromDir: string,
  toDir: string,
  dryRun: boolean = false,
): number {
  const fromPath = normalizePath(fromDir);
  const toPath = normalizePath(toDir);

  console.log(`Moving history from: ${fromPath}`);
  console.log(`                 to: ${toPath}`);

  const countQuery = db.query<{ count: number }, [string]>(
    "SELECT COUNT(*) as count FROM history WHERE cwd = ? AND deleted_at IS NULL",
  );
  const { count } = countQuery.get(fromPath)!;

  if (count === 0) {
    console.log(`No history entries found for directory: ${fromPath}`);
    return 0;
  }

  console.log(`Found ${count} history entries to move`);

  if (dryRun) {
    console.log("DRY RUN: No changes will be made");
    return count;
  }

  // Update the cwd for all matching entries
  const updateQuery = db.query<void, [string, string]>(
    "UPDATE history SET cwd = ? WHERE cwd = ? AND deleted_at IS NULL",
  );
  const result = updateQuery.run(toPath, fromPath);

  console.log(`Successfully moved ${result.changes} entries`);
  return result.changes;
}

function copyHistory(
  db: Database,
  fromDir: string,
  toDir: string,
  dryRun: boolean = false,
): number {
  const fromPath = normalizePath(fromDir);
  const toPath = normalizePath(toDir);

  console.log(`Copying history from: ${fromPath}`);
  console.log(`                  to: ${toPath}`);

  // Get all matching entries
  const selectQuery = db.query<HistoryEntry, [string]>(
    "SELECT * FROM history WHERE cwd = ? AND deleted_at IS NULL",
  );
  const entries = selectQuery.all(fromPath);

  if (entries.length === 0) {
    console.log(`No history entries found for directory: ${fromPath}`);
    return 0;
  }

  console.log(`Found ${entries.length} history entries to copy`);

  if (dryRun) {
    console.log("DRY RUN: No changes will be made");
    return entries.length;
  }

  const insertQuery = db.prepare(
    `INSERT INTO history (id, timestamp, duration, exit, command, cwd, session, hostname, deleted_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  );

  let copied = 0;
  for (const entry of entries) {
    try {
      // New UUID for the copied entry
      const newId = randomUUID();
      insertQuery.run(
        newId,
        entry.timestamp,
        entry.duration,
        entry.exit,
        entry.command,
        toPath,
        entry.session,
        entry.hostname,
        entry.deleted_at,
      );
      copied++;
    } catch (error) {
      console.error(`Failed to copy entry ${entry.id}:`, error);
    }
  }

  console.log(`Successfully copied ${copied} entries`);
  return copied;
}

function listHistory(db: Database, dir: string, limit: number = 10): void {
  const dirPath = normalizePath(dir);

  console.log(`Listing history for: ${dirPath}\n`);

  const query = db.query<HistoryEntry, [string, number]>(
    `SELECT * FROM history
     WHERE cwd = ? AND deleted_at IS NULL
     ORDER BY timestamp DESC
     LIMIT ?`,
  );
  const entries = query.all(dirPath, limit);

  if (entries.length === 0) {
    console.log("No history entries found");
    return;
  }

  console.log(`Found ${entries.length} entries (showing up to ${limit}):\n`);

  for (const entry of entries) {
    const date = new Date(Number(entry.timestamp) / 1_000_000); // Convert from nanoseconds
    console.log(`[${date.toISOString()}] ${entry.command}`);
    console.log(
      `  Exit: ${entry.exit} | Duration: ${entry.duration}ns | Session: ${entry.session.slice(0, 8)}...`,
    );
    console.log();
  }
}

function countHistory(db: Database, dir: string): void {
  const dirPath = normalizePath(dir);

  const query = db.query<{ count: number }, [string]>(
    "SELECT COUNT(*) as count FROM history WHERE cwd = ? AND deleted_at IS NULL",
  );
  const { count } = query.get(dirPath)!;

  console.log(`History entries for ${dirPath}: ${count}`);
}

async function main() {
  const args = process.argv.slice(2);

  if (args.length === 0 || args.includes("--help") || args.includes("-h")) {
    printUsage();
    process.exit(0);
  }

  const command = args[0];
  const dryRun = args.includes("--dry-run");

  // Handle custom database path
  let dbPath = getAtuinDbPath();
  const dbIndex = args.indexOf("--db");
  if (dbIndex !== -1 && args[dbIndex + 1]) {
    dbPath = resolve(args[dbIndex + 1]);
  }

  if (!existsSync(dbPath)) {
    console.error(`Error: Atuin database not found at: ${dbPath}`);
    console.error(
      "Make sure Atuin is installed and has been used at least once.",
    );
    console.error("You can specify a custom path with --db <path>");
    process.exit(1);
  }

  console.log(`Using database: ${dbPath}\n`);

  const db = new Database(dbPath);

  try {
    switch (command) {
      case "move": {
        if (args.length < 3) {
          console.error("Error: move requires <from> and <to> arguments");
          printUsage();
          process.exit(1);
        }
        const fromDir = args[1];
        const toDir = args[2];
        moveHistory(db, fromDir, toDir, dryRun);
        break;
      }

      case "copy": {
        if (args.length < 3) {
          console.error("Error: copy requires <from> and <to> arguments");
          printUsage();
          process.exit(1);
        }
        const fromDir = args[1];
        const toDir = args[2];
        copyHistory(db, fromDir, toDir, dryRun);
        break;
      }

      case "list": {
        if (args.length < 2) {
          console.error("Error: list requires <dir> argument");
          printUsage();
          process.exit(1);
        }
        const dir = args[1];
        const limit = args[2] ? parseInt(args[2], 10) : 10;
        listHistory(db, dir, limit);
        break;
      }

      case "count": {
        if (args.length < 2) {
          console.error("Error: count requires <dir> argument");
          printUsage();
          process.exit(1);
        }
        const dir = args[1];
        countHistory(db, dir);
        break;
      }

      default:
        console.error(`Error: Unknown command '${command}'`);
        printUsage();
        process.exit(1);
    }
  } catch (error) {
    console.error("Error:", error instanceof Error ? error.message : error);
    process.exit(1);
  } finally {
    db.close();
  }
}

main();
