import { Glob } from "bun";
import Handlebars from "handlebars";
import matter from "gray-matter";
import { join, relative, dirname } from "node:path";

const args = Bun.argv.slice(2);

const rootDir = args[0];
const outputFile = args[1];

if (!rootDir) {
  console.error("Usage: bun run index.ts <directory> [output-file]");
  process.exit(1);
}

console.error(`Scanning ${rootDir} for tools...`);

const glob = new Glob("**/README.md");

interface Tool {
  name: string;
  purpose: string;
  link: string;
  path: string;
}

const tools: Tool[] = [];

// Scan for README.md files
for await (const file of glob.scan({ cwd: rootDir })) {
  const fullPath = join(rootDir, file);

  if (file.includes("node_modules")) continue;

  try {
    const fileContent = await Bun.file(fullPath).text();
    const { data } = matter(fileContent);

    if (data.name && data.purpose) {
      const toolDir = dirname(file);
      // If we are writing to a file, links should be relative to that file.
      // If printing to stdout, links relative to cwd (which we assume is where we run it from?)
      // Let's make links relative to the rootDir for now, or if outputFile is present, relative to that.

      // Assuming the index file will be placed in rootDir if not specified,
      // or we just link to the relative path found by glob.
      const link = `./${toolDir}`;

      tools.push({
        name: data.name,
        purpose: data.purpose,
        link: link,
        path: file,
      });
    }
  } catch (error) {
    console.error(`Error reading ${file}:`, error);
  }
}

tools.sort((a, b) => a.name.localeCompare(b.name));

const templateSource = `
# Tools

| Name | Purpose |
|------|---------|
{{#each tools}}
| [{{name}}]({{link}}) | {{purpose}} |
{{/each}}
`;

const template = Handlebars.compile(templateSource);
const output = template({ tools });

if (outputFile) {
  await Bun.write(outputFile, output);
  console.error(`Tools index written to ${outputFile}`);
} else {
  console.log(output);
}
