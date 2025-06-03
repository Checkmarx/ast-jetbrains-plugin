<img src="/banner.png">
<br />
<div align="center">

[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![Install][install-shield]][install-url]
[![APACHE License][license-shield]][license-url]

</div>
<br />

<p align="center">
  <a href="https://github.com/Checkmarx/ast-jetbrains-plugin">
    <img src="/logo.png" alt="Logo" width="80" height="80" />
  </a>

<h3 align="center">CHECKMARX-ONE-JETBRAINS-PLUGIN</h3>

<p align="center">
    The Checkmarx One JetBrains plugin enables you to import results from a Checkmarx One scan directly into your IDE.
    <br />
    <a href="https://checkmarx.com/resource/documents/en/34965-68733-checkmarx-one-jetbrains-plugin.html"><strong>Explore the docs »</strong></a>
    <br />
    <a href="https://plugins.jetbrains.com/plugin/17672-checkmarx-ast"><strong>Marketplace »</strong></a>
    <br />
    <br />
    <a href="https://github.com/Checkmarx/ast-jetbrains-plugin/issues/new">Report Bug</a>
    ·
    <a href="https://github.com/Checkmarx/ast-jetbrains-plugin/issues/new">Request Feature</a>
  </p>
</p>



<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#setting-up">Setting Up</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>



<!-- ABOUT THE PROJECT -->
## About The Project

Checkmarx continues to spearhead the shift-left approach to AppSec by bringing our powerful AppSec tools into your IDE. This empowers developers to identify vulnerabilities and remediate them **as they code**. The Checkmarx One (AST) JetBrains plugin integrates seamlessly into your IDE, enabling you to access the full functionality of your Checkmarx One account (SAST, SCA, IaC Security) directly from your IDE.

You can run new scans, or import results from scans run in your Checkmarx One account. Checkmarx provides detailed info about each vulnerability, including remediation recommendations and examples of effective remediation. The plugin enables you to navigate from a vulnerability to the relevant source code, so that you can easily zero-in on the problematic code and start working on remediation.

This plugin provides easy integration with JetBrains and is compatible with all JetBrains IDE products (e.g., IntelliJ IDEA, PyCharm, WebStorm etc.).

<!-- GETTING STARTED -->
## Getting Started

### Prerequisites

-   Access the full power of Checkmarx One (SAST, SCA, and IaC Security) directly from your IDE.

-   Run a new scan from your IDE even before committing the code, or import scan results from your Checkmarx One account.
-   Rescan an existing branch from your IDE or create a new branch in Checkmarx One for the local branch in your workspace.

-   Provides actionable results including remediation recommendations. Navigate from results panel directly to the highlighted vulnerable code in the editor and get right down to work on the remediation.

-   Group and filter results

-   Triage results (by adjusting the severity and state and adding comments) directly from the JetBrains console (currently supported for SAST and IaC Security)

-   Apply Auto Remediation to automatically remediate open source vulnerabilities, by updating to a non-vulnerable package version.

-   Links to Codebashing lessons

-   AI Secure Coding Assistant (ASCA) - A lightweight scan engine that runs in the background while you work, enabling developers to identify and remediate secure coding best practice violations  **as they code**.

### Prerequisites

-   You are running IntelliJ version 2022.2+ or another JetBrains IDE that is based on a supported version of IntelliJ.

-   You have an **API key** for your Checkmarx One account. To create an
    API key, see [Generating an API Key](https://checkmarx.com/resource/documents/en/34965-68618-generating-an-api-key.html).
> The following are the minimum required  [roles](https://docs.checkmarx.com/en/34965-68603-managing-roles.html "Managing Roles")  for running an end-to-end flow of scanning a project and viewing results via the CLI or plugins:
> -   CxOne composite role  `ast-scanner`
> -   CxOne role  `view-policy-management`
> -   IAM role  `default-roles`

### Setting Up


-   Verify that all prerequisites are in place.

-   Install the **Checkmarx One** plugin and configure the settings as
    described [here](https://checkmarx.com/resource/documents/en/34965-68734-installing-and-setting-up-the-checkmarx-one-jetbrains-plugin.html).



## Usage

To see how you can use our tool, please refer to the [Documentation](https://checkmarx.com/resource/documents/en/34965-68733-checkmarx-one-jetbrains-plugin.html)


## Contribution

We appreciate feedback and contribution to the JETBRAINS PLUGIN! Before you get started, please see the following:

- [Checkmarx contribution guidelines](docs/contributing.md)
- [Checkmarx Code of Conduct](docs/code_of_conduct.md)

<!-- LICENSE -->
## License
Distributed under the [Apache 2.0](LICENSE). See `LICENSE` for more information.

<!-- FEEDBACK -->
## Feedback
We’d love to hear your feedback! If you come across a bug or have a feature request, please let us know by submitting an issue in [GitHub Issues](https://github.com/Checkmarx/ast-jetbrains-plugin/issues).

<!-- CONTACT -->
## Contact

Checkmarx - Checkmarx One Integrations Team

Project Link: [https://github.com/Checkmarx/ast-jetbrains-plugin](https://github.com/Checkmarx/ast-jetbrains-plugin)

Find more integrations from our team [here](https://github.com/Checkmarx/ci-cd-integrations#checkmarx-ast-integrations)

© 2022 Checkmarx Ltd. All Rights Reserved.

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[contributors-shield]: https://img.shields.io/github/contributors/Checkmarx/ast-jetbrains-plugin.svg
[contributors-url]: https://github.com/Checkmarx/ast-jetbrains-plugin/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/Checkmarx/ast-jetbrains-plugin.svg
[forks-url]: https://github.com/Checkmarx/ast-jetbrains-plugin/network/members
[stars-shield]: https://img.shields.io/github/stars/Checkmarx/ast-jetbrains-plugin.svg
[stars-url]: https://github.com/Checkmarx/ast-jetbrains-plugin/stargazers
[issues-shield]: https://img.shields.io/github/issues/Checkmarx/ast-jetbrains-plugin.svg
[issues-url]: https://github.com/Checkmarx/ast-jetbrains-plugin/issues
[license-shield]: https://img.shields.io/github/license/Checkmarx/ast-jetbrains-plugin.svg
[license-url]: https://github.com/Checkmarx/ast-jetbrains-plugin/blob/main/LICENSE
[install-shield]: https://img.shields.io/jetbrains/plugin/d/17672-checkmarx-ast
[install-url]: https://plugins.jetbrains.com/plugin/17672-checkmarx-ast
