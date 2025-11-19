import { dirname, join } from "node:path";
import { Glob } from "bun";
import matter from "gray-matter";
import Handlebars from "handlebars";

const args = Bun.argv.slice(2);

let rootDir = args[0];
let outputFile = args[1];
let generateJustfile = false;

const flagIndex = args.indexOf("--just");
if (flagIndex !== -1) {
	generateJustfile = true;
	const argsWithoutJustFlag = args.toSpliced(flagIndex, 1);
	rootDir = argsWithoutJustFlag[0];
	outputFile = argsWithoutJustFlag[1];
}

if (!rootDir) {
	console.error("Usage: bun run index.ts <directory> [output-file] [--just]");
	process.exit(1);
}

console.error(`Scanning ${rootDir} for tools...`);

interface Tool {
	name: string;
	purpose: string;
	link: string;
	path: string;
	lang?: "bun";
}

async function scanToolsDirectory(rootDir: string): Promise<Tool[]> {
	const scannedTools: Tool[] = [];
	const glob = new Glob("**/README.md");

	// Scan for README.md files
	for await (const file of glob.scan({ cwd: rootDir })) {
		const fullPath = join(rootDir, file);

		if (file.includes("node_modules")) continue;

		try {
			const fileContent = await Bun.file(fullPath).text();
			const { data } = matter(fileContent);

			if (data.name && data.purpose) {
				const toolDir = dirname(file);
				const link = `./${toolDir}`;

				// Check if tool uses Bun
				const bunLockPath = join(rootDir, toolDir, "bun.lock");
				const hasBunLock = await Bun.file(bunLockPath).exists();

				scannedTools.push({
					name: data.name,
					purpose: data.purpose,
					link: link,
					path: file,
					lang: hasBunLock ? "bun" : undefined,
				});
			}
		} catch (error) {
			console.error(`Error reading ${file}:`, error);
		}
	}

	return scannedTools;
}

const tools = await scanToolsDirectory(rootDir);

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

if (generateJustfile) {
	const outputDir = outputFile ? dirname(outputFile) : rootDir;
	if (!outputDir) {
		console.error("Couldn't determine output directory for the Justfile");
		process.exit(1);
	}
	const justfileContent = generateJustfileContent(tools);
	const justfilePath = join(outputDir, "Justfile");
	await Bun.write(justfilePath, justfileContent);
	console.error(`Justfile written to ${justfilePath}`);
}

function generateJustfileContent(tools: Tool[]): string {
	const justfileTemplate = `# Auto-generated. Do not edit manually.

default: list

list:
    @cat README.md

index:
    cd tools-indexer && bun run index.ts .. ../README.md --just

install-all:
{{#each tools}}
    cd {{toolDir}} && bun install
{{/each}}`;

	const template = Handlebars.compile(justfileTemplate);
	const bunTools = tools
		.filter((tool) => tool.lang === "bun")
		.map((tool) => ({
			...tool,
			toolDir: tool.path.split("/")[0],
		}));
	return template({ tools: bunTools });
}
