package com.checkmarx.intellij.devassist.remediation.prompts;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.util.SeverityLevel;

/**
 * Checkmarx prompts for various remediation tasks.
 */
public final class CxOneAssistFixPrompts {

    private static final String AGENT_NAME = Constants.RealTimeConstants.CX_AGENT_NAME;

    private CxOneAssistFixPrompts() {
        throw new IllegalStateException("Cannot instantiate CxOneAssistFixPrompts class");
    }

    /**
     * Builds the SCA remediation prompt (generic concatenated form).
     *
     * @param packageName    vulnerable package name (e.g. "node-ipc")
     * @param packageVersion vulnerable package version (e.g. "10.1.1")
     * @param packageManager ecosystem / package manager (e.g. "npm", "maven")
     * @param severity       textual severity (e.g. "Malicious", "High")
     * @return composed prompt string (plain text with Markdown fragments)
     */
    public static String scaRemediationPrompt(String packageName, String packageVersion,
                                              String packageManager, String severity) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are the ").append(AGENT_NAME).append(".\n\n")
                .append("A security issue has been detected in `").append(packageName).append("@").append(packageVersion).append("` (package manager: `").append(packageManager).append("`).\n")
                .append("**Severity:** `").append(severity).append("`\n")
                .append("Your task is to remediate the issue **completely and autonomously** using the internal PackageRemediation tool in Checkmarx MCP. Follow the exact instructions in `fix_instructions` — no assumptions or manual interaction allowed.\n\n");

        prompt.append("Step 1. ANALYSIS (AUTOMATED):\n\n")
                .append("Determine the issue type:\n")
                .append("- If `status` is one of: `Critical`, `High`, `Medium`, `Low`, `Info`, set: `issueType = \"CVE\"`\n")
                .append("- If `status = \"Malicious\"`, set: `issueType = \"malicious\"`\n\n")
                .append("Call the internal PackageRemediation tool with:\n\n")
                .append("```json\n")
                .append("{\n")
                .append("  \"packageName\": \"").append(packageName).append("\",\n")
                .append("  \"packageVersion\": \"").append(packageVersion).append("\",\n")
                .append("  \"packageManager\": \"").append(packageManager).append("\",\n")
                .append("  \"issueType\": \"{determined issueType}\"\n")
                .append("}\n")
                .append("```\n\n");

        prompt.append("Parse the response and extract the `fix_instructions` field. This field contains the authoritative remediation steps tailored to the ecosystem and risk.\n\n");

        prompt.append("Step 2. EXECUTION (AUTOMATED):\n\n")
                .append("- Read and execute each line in `fix_instructions`, in order.\n")
                .append("- For each change:\n")
                .append("  - Apply the instruction exactly.\n")
                .append("  - Track all modified files.\n")
                .append("  - Note the type of change (e.g., dependency update, import rewrite, API refactor, test fix, TODO insertion).\n")
                .append("  - Record before → after values where applicable.\n")
                .append("  - Capture line numbers if known.\n")
                .append("Examples:\n")
                .append("- `package.json`: lodash version changed from 3.10.1 → 4.17.21\n")
                .append("- `src/utils/date.ts`: import updated from `lodash` to `date-fns`\n")
                .append("- `src/main.ts:42`: `_.pluck(users, 'id')` → `users.map(u => u.id)`\n")
                .append("- `src/index.ts:78`: // TODO: Verify API migration from old-package to new-package\n\n");

        prompt.append("Step 3. VERIFICATION:\n\n")
                .append("- If the instructions include build, test, or audit steps — run them exactly as written\n")
                .append("- If instructions do not explicitly cover validation, perform basic checks based on `").append(packageManager).append("`:\n")
                .append("  - `npm`: `npx tsc --noEmit`, `npm run build`, `npm test`\n")
                .append("  - `go`: `go build ./...`, `go test ./...`\n")
                .append("  - `maven`: `mvn compile`, `mvn test`\n")
                .append("  - `pypi`: `python -c \"import ").append(packageName).append("\"`, `pytest`\n")
                .append("  - `nuget`: `dotnet build`, `dotnet test`\n\n")

                .append("If any of these validations fail:\n\n")
                .append("- Attempt to fix the issue if it's obvious\n")
                .append("- Otherwise log the error and annotate the code with a TODO\n\n");

        prompt.append("Step 4. OUTPUT:\n\n").append("Prefix all output with: `").append(AGENT_NAME).append(" -`\n\n");

        prompt.append("✅ **Remediation Summary**\n\n")
                .append("Format:\n")
                .append("```\n")
                .append("Security Assistant - Remediation Summary\n\n")
                .append("Package:     ").append(packageName).append("\n")
                .append("Version:     ").append(packageVersion).append("\n")
                .append("Manager:     ").append(packageManager).append("\n")
                .append("Severity:    ").append(severity).append("\n\n")
                .append("Files Modified:\n")
                .append("1. package.json\n")
                .append("   - Updated dependency: lodash 3.10.1 → 4.17.21\n\n")
                .append("2. src/utils/date.ts\n")
                .append("   - Updated import: from 'lodash' to 'date-fns'\n")
                .append("   - Replaced usage: _.pluck(users, 'id') → users.map(u => u.id)\n\n")
                .append("3. src/__tests__/date.test.ts\n")
                .append("   - Fixed test: adjusted mock expectations to match updated API\n\n")
                .append("4. src/index.ts\n")
                .append("   - Line 78: Inserted TODO: Verify API migration from old-package to new-package\n")
                .append("```\n\n");

        prompt.append("✅ **Final Status**\n\n")
                .append("If all tasks succeeded:\n\n")
                .append("- \"Remediation completed for ").append(packageName).append("@").append(packageVersion).append("\"\n")
                .append("- \"All fix instructions and failing tests resolved\"\n")
                .append("- \"Build status: PASS\"\n")
                .append("- \"Test results: PASS\"\n\n");

        prompt.append("If partially resolved:\n\n")
                .append("- \"Remediation partially completed – manual review required\"\n")
                .append("- \"Some test failures or instructions could not be automatically fixed\"\n")
                .append("- \"TODOs inserted where applicable\"\n\n");

        prompt.append("If failed:\n\n")
                .append("- \"Remediation failed for ").append(packageName).append("@").append(packageVersion).append("\"\n")
                .append("- \"Reason: {summary of failure}\"\n")
                .append("- \"Unresolved instructions or failing tests listed above\"\n\n");

        prompt.append("Step 5. CONSTRAINTS:\n\n")
                .append("- Do not  the user\n")
                .append("- Do not skip or reorder fix steps\n")
                .append("- Only execute what's explicitly listed in `fix_instructions`\n")
                .append("- Attempt to fix test failures automatically\n")
                .append("- Insert clear TODO comments for unresolved issues\n")
                .append("- Ensure remediation is deterministic, auditable, and fully automated");
        return prompt.toString();
    }


    /**
     * Generates a secret remediation prompt.
     *
     * @param title       - issue title
     * @param description - issue description (optional) - if null, will be empty string.
     * @param severity    - issue severity (optional) - if null, will be empty string.
     * @return - prompt string (plain text with Markdown fragments)
     */
    public static String generateSecretRemediationPrompt(String title, String description, String severity) {
        StringBuilder prompt = new StringBuilder()
                .append("A secret has been detected: \"").append(title).append("\"  \n")
                .append(description != null ? description : "").append("\n\n")
                .append("---\n\n")
                .append("You are the `").append(AGENT_NAME).append("`.\n\n")
                .append("Your mission is to identify and remediate this secret using secure coding standards. Follow industry best practices, automate safely, and clearly document all actions taken.\n\n")
                .append("---\n\n");

        prompt.append("Step 1. SEVERITY INTERPRETATION  \n")
                .append("Severity level: `").append(severity != null ? severity : "").append("`\n\n")
                .append("- `Critical`: Secret is confirmed **valid**. Immediate remediation required.  \n")
                .append("- `High`: Secret may be valid. Treat as sensitive and externalize it securely.  \n")
                .append("- `Medium`: Likely **invalid** (e.g., test or placeholder). Still remove from code and annotate accordingly.\n\n")
                .append("---\n\n");

        prompt.append("Step 2. TOOL CALL – Remediation Plan\n\n")
                .append("Determine the programming language of the file where the secret was detected.  \n")
                .append("If unknown, leave the `language` field empty.\n\n")
                .append("Call the internal `codeRemediation` Checkmarx MCP tool with:\n\n")
                .append("```json\n")
                .append("{\n")
                .append("  \"type\": \"secret\",\n")
                .append("  \"sub_type\": \"").append(title).append("\",\n")
                .append("  \"language\": \"[auto-detected language]\"\n")
                .append("}\n")
                .append("```\n\n")
                .append("- If the tool is **available**, parse the response:\n")
                .append("  - `remediation_steps` – exact steps to follow\n")
                .append("  - `best_practices` – explain secure alternatives\n")
                .append("  - `description` – contextual background\n\n")
                .append("- If the tool is **not available**, display:\n")
                .append("`[MCP ERROR] codeRemediation tool is not available. Please check the Checkmarx MCP server.`\n\n")
                .append("---\n\n");

        prompt.append("Step 3. ANALYSIS & RISK\n\n")
                .append("Identify the type of secret (API key, token, credential). Explain:\n")
                .append("- Why it’s a risk (leakage, unauthorized access, compliance violations)\n")
                .append("- What could happen if misused or left in source\n\n")
                .append("---\n\n");

        prompt.append("Step 4. REMEDIATION STRATEGY\n\n")
                .append("- Parse and apply every item in `remediation_steps` sequentially\n")
                .append("- Automatically update code/config files if safe\n")
                .append("- If a step cannot be applied automatically, insert a clear TODO\n")
                .append("- Replace secret with environment variable or vault reference\n\n")
                .append("---\n\n");

        prompt.append("Step 5. VERIFICATION\n\n")
                .append("If applicable for the language:\n")
                .append("- Run type checks or compile the code\n")
                .append("- Ensure changes build and tests pass\n")
                .append("- Fix issues if introduced by secret removal\n\n")
                .append("---\n\n");

        prompt.append("Step 6. OUTPUT FORMAT\n\n")
                .append("Generate a structured remediation summary:\n\n")
                .append("```markdown\n")
                .append("### ").append(AGENT_NAME).append(" - Secret Remediation Summary\n\n")
                .append("**Secret:** ").append(title).append("  \n")
                .append("**Severity:** ").append(severity != null ? severity : "").append("  \n")
                .append("**Assessment:** ").append(getAssessmentText(severity)).append("\n\n")
                .append("**Files Modified:**\n")
                .append("- `.env`: Added/updated with `SECRET_NAME`\n")
                .append("- `src/config.ts`: Replaced hardcoded secret with `process.env.SECRET_NAME`\n\n")
                .append("**Remediation Actions Taken:**\n")
                .append("- ✅ Removed hardcoded secret\n")
                .append("- ✅ Inserted environment reference\n")
                .append("- ✅ Updated or created .env\n")
                .append("- ✅ Added TODOs for secret rotation or vault storage\n\n")
                .append("**Next Steps:**\n")
                .append("- [ ] Revoke exposed secret (if applicable)\n")
                .append("- [ ] Store securely in vault (AWS Secrets Manager, GitHub Actions, etc.)\n")
                .append("- [ ] Add CI/CD secret scanning\n\n")
                .append("**Best Practices:**\n")
                .append("- (From tool response, or fallback security guidelines)\n\n")
                .append("**Description:**\n")
                .append("- (From `description` field or fallback to original input)\n\n")
                .append("```\n\n")
                .append("---\n\n");

        prompt.append("Step 7. CONSTRAINTS\n\n")
                .append("- ❌ Do NOT expose real secrets\n")
                .append("- ❌ Do NOT generate fake-looking secrets\n")
                .append("- ✅ Follow only what’s explicitly returned from MCP\n")
                .append("- ✅ Use secure externalization patterns\n")
                .append("- ✅ Respect OWASP, NIST, and GitHub best practices\n");
        return prompt.toString();
    }

    /**
     * Generates the assessment text for given severity.
     *
     * @param severity severity level
     * @return assessment text
     */
    private static String getAssessmentText(String severity) {
        if (SeverityLevel.CRITICAL.getSeverity().equalsIgnoreCase(severity)) {
            return "✅ Confirmed valid secret. Immediate remediation performed.";
        } else if (SeverityLevel.HIGH.getSeverity().equalsIgnoreCase(severity)) {
            return "⚠️ Possibly valid. Handled as sensitive.";
        } else {
            return "ℹ️ Likely invalid (test/fake). Removed for hygiene.";
        }
    }

}
