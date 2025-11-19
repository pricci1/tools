default: list

list:
    @cat README.md

index:
    cd tools-indexer && bun run index.ts .. ../README.md

install:
    cd atuin-history-mover && bun install
    cd tools-indexer && bun install
