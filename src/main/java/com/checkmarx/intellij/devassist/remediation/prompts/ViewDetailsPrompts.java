package com.checkmarx.intellij.devassist.remediation.prompts;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.util.SeverityLevel;

import java.util.List;

/**
 * Prompt builder for generating prompts for viewing vulnerability details.
 */
public final class ViewDetailsPrompts {

    private ViewDetailsPrompts() {
        throw new IllegalStateException("Cannot instantiate ViewDetailsPrompts class");
    }

    private static final String AGENT_NAME = Constants.RealTimeConstants.CX_AGENT_NAME;

    /**
     * Generates a detailed prompt for explaining the security issue of an SCA/OSS.
     *
     * @param packageName     The name of the software package.
     * @param version         The version of the software package.
     * @param status          The security status of the package (e.g., "malicious", "vulnerable").
     * @param vulnerabilities A list of vulnerabilities affecting the package.
     * @return A formatted prompt string for explaining the security issue.
     */
    public static String generateSCAExplanationPrompt(String packageName, String version,
                                                      String status, List<Vulnerability> vulnerabilities) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the `").append(AGENT_NAME).append("`.\n\n")
                .append("Your task is to **analyze and explain** the security issue affecting the package `")
                .append(packageName).append("@").append(version).append("` with status: `").append(status).append("`.\n\n");

        prompt.append("### ❗ Important Instructions:\n")
                .append("- 👉 **Do not change anything in the code. Just explain the risks and remediation steps.**\n")
                .append("- ❌ **Never include references from Checkmarx competitors.**\n\n");

        prompt.append("### 🔍 Package Overview\n\n")
                .append("- **Package:** `").append(packageName).append("`\n")
                .append("- **Version:** `").append(version).append("`\n")
                .append("- **Status:** `").append(status).append("`\n");

        if (status.equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity())) {
            buildMaliciousContentForSCAPrompt(version, prompt);
        } else {
            buildVulnerabilityContentForSCAPrompt(status, vulnerabilities, prompt);
        }
        prompt.append("### 🛠️ Remediation Guidance\n\n")
                .append("Offer actionable advice:\n")
                .append("- Whether to remove, upgrade, or replace the package\n")
                .append("- If malicious: clearly emphasize permanent removal\n")
                .append("- Recommend safer, verified alternatives if available\n")
                .append("- Suggest preventative measures:\n")
                .append("  - Use SCA in CI/CD\n")
                .append("  - Prefer signed packages\n")
                .append("  - Pin versions to prevent shadow updates\n\n");

        prompt.append("### ✅ Summary Section\n\n")
                .append("Conclude with:\n")
                .append("- Overall risk explanation\n")
                .append("- Immediate remediation steps\n")
                .append("- Whether this specific version is linked to online reports\n")
                .append("- If not, reference Checkmarx attribution (per above rules)\n")
                .append("- Never mention competitor vendors or tools\n\n");

        prompt.append("### ✏️ Output Formatting\n\n")
                .append("- Use Markdown: `##`, `- `, `**bold**`, `code`\n")
                .append("- Developer-friendly tone, informative, concise\n")
                .append("- No speculation — use only trusted, verified sources\n");

        return prompt.toString();
    }

    /**
     * Builds a prompt for explaining malicious packages.
     *
     * @param version the version of the package
     * @param prompt  the prompt builder
     */
    private static void buildMaliciousContentForSCAPrompt(String version, StringBuilder prompt) {
        prompt.append("\n\n")
                .append("### 🧨 Malicious Package Detected\n\n")
                .append("This package has been flagged as **malicious**.\n\n")
                .append("**⚠️ Never install or use this package under any circumstances.**\n\n")
                .append("#### 🔎 Web Investigation:\n\n")
                .append("- Search the web for trusted community or vendor reports about malicious activity involving this package.\n")
                .append("- If information exists about other versions but **not** version `").append(version).append("`, explicitly say:\n\n")
                .append("> _“This specific version (`").append(version).append("`) was identified as malicious by Checkmarx Security researchers.”_\n\n")
                .append("- If **no credible external information is found at all**, state:\n\n")
                .append("> _“This package was identified as malicious by Checkmarx Security researchers based on internal threat intelligence and behavioral analysis.”_\n\n")
                .append("Then explain:\n")
                .append("- What types of malicious behavior these packages typically include (e.g., data exfiltration, postinstall backdoors)\n")
                .append("- Indicators of compromise developers should look for (e.g., suspicious scripts, obfuscation, DNS calls)\n\n")
                .append("**Recommended Actions:**\n")
                .append("- ✅ Immediately remove from all codebases and pipelines\n")
                .append("- ❌ Never reinstall or trust any version of this package\n")
                .append("- 🔁 Replace with a well-known, secure alternative\n")
                .append("- 🔒 Consider running a retrospective security scan if this was installed\n\n");
    }

    /**
     * Builds a prompt for explaining known vulnerabilities.
     *
     * @param status          the severity status of the package
     * @param vulnerabilities the list of vulnerabilities affecting the package
     * @param prompt          the prompt builder
     */
    private static void buildVulnerabilityContentForSCAPrompt(String status, List<Vulnerability> vulnerabilities, StringBuilder prompt) {
        prompt.append("### 🚨 Known Vulnerabilities\n\n")
                .append("Explain each known CVE affecting this package:\n");

        if (vulnerabilities != null && !vulnerabilities.isEmpty()) {
            for (int i = 0; i < vulnerabilities.size(); i++) {
                Vulnerability vuln = vulnerabilities.get(i);
                prompt.append("\n#### ").append(i + 1).append(". ").append(vuln.getCve()).append("\n")
                        .append("- **Severity:** ").append(vuln.getSeverity()).append("\n")
                        .append("- **Description:** ").append(vuln.getDescription()).append("\n");
            }
        } else {
            prompt.append("\n⚠️ No CVEs were provided. Please verify if this is expected for status `").append(status).append("`.\n");
        }
    }

    /**
     * Generates a detailed prompt for explaining a detected secret.
     *
     * @param title       the title of the secret
     * @param description the description of the secret
     * @param severity    the severity level of the secret vulnerability
     * @return the formatted Markdown prompt
     */
    public static String secretsExplanationPrompt(String title, String description, String severity) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the `").append(AGENT_NAME).append("`.\n\n")
                .append("A potential secret has been detected: **\"").append(title).append("\"**  \n")
                .append("Severity: **").append(severity).append("**\n\n");
        prompt.append("### ❗ Important Instruction:\n")
                .append("👉 **Do not change any code. Just explain the risk, validation level, and recommended actions.**\n\n");
        prompt.append("### 🔍 Secret Overview\n\n")
                .append("- **Secret Name:** `").append(title).append("`\n")
                .append("- **Severity Level:** `").append(severity).append("`\n")
                .append("- **Details:** ").append(description).append("\n\n");
        prompt.append("### 🧠 Risk Understanding Based on Severity\n\n")
                .append("- **Critical**:  \n")
                .append("  The secret was **validated as active**. It is likely in use and can be exploited immediately if exposed.\n\n")
                .append("- **High**:  \n")
                .append("  The validation status is **unknown**. The secret may or may not be valid. Proceed with caution and treat it as potentially live.\n\n")
                .append("- **Medium**:  \n")
                .append("  The secret was identified as **invalid** or **mock/test value**. While not active, it may confuse developers or be reused insecurely.\n\n");
        prompt.append("### 🔐 Why This Matters\n\n")
                .append("Hardcoded secrets pose a serious risk:\n")
                .append("- **Leakage** through public repositories or logs\n")
                .append("- **Unauthorized access** to APIs, cloud providers, or infrastructure\n")
                .append("- **Exploitation** via replay attacks, privilege escalation, or lateral movement\n\n");
        prompt.append("### ✅ Recommended Remediation Steps (for developer action)\n\n")
                .append("- Rotate the secret if it’s live (Critical/High)\n")
                .append("- Move secrets to environment variables or secret managers\n")
                .append("- Audit the commit history to ensure it hasn’t leaked publicly\n")
                .append("- Implement secret scanning in your CI/CD pipelines\n")
                .append("- Document safe handling procedures in your repo\n\n");
        prompt.append("### 📋 Next Steps Checklist (Markdown)\n\n")
                .append("```markdown\n")
                .append("### Next Steps:\n")
                .append("- [ ] Rotate the exposed secret if valid\n")
                .append("- [ ] Move secret to secure storage (.env or secret manager)\n")
                .append("- [ ] Clean secret from commit history if leaked\n")
                .append("- [ ] Annotate clearly if it's a fake or mock value\n")
                .append("- [ ] Implement CI/CD secret scanning and policies\n")
                .append("```\n\n");
        prompt.append("### ✏️ Output Format Guidelines\n\n")
                .append("- Use Markdown with clear sections\n")
                .append("- Do not attempt to edit or redact the code\n")
                .append("- Be factual, concise, and helpful\n")
                .append("- Assume this is shown to a developer unfamiliar with security tooling\n");
        return prompt.toString();
    }
}
