<img src="https://raw.githubusercontent.com/Checkmarx/ci-cd-integrations/main/.images/PluginBanner.jpg">
<br />
<div align="center">

[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![Install][install-shield]][install-url]
[![License][license-shield]][license-url]

</div>
<br />
<p align="center">
<a href="https://github.com/Checkmarx/ast-jetbrains-plugin">
<img src="https://raw.githubusercontent.com/Checkmarx/ci-cd-integrations/main/.images/cx_x_icon.png" alt="Logo" width="80" height="80" />
</a>
<h3 align="center">CHECKMARX-ONE-JETBRAINS-PLUGIN</h3>
<p align="center">
<a href="https://docs.checkmarx.com/en/34965-68734-installing-and-setting-up-the-checkmarx-one-jetbrains-pluging.html"><strong>Explore the docs »</strong></a>
<br />
<a href="https://plugins.jetbrains.com/plugin/17672-checkmarx-ast"><strong>Marketplace »</strong></a>
</p>
</p>

<p  align="center">
The Checkmarx One JetBrains plugin enables you to import results from a Checkmarx One scan directly into your IDE and run new scans from the IDE.
</p>
<br  />
<p  align="center">
<a  href="https://github.com/Checkmarx/ast-jetbrains-plugin/issues/new">Report Bug</a>
·
<a  href="https://github.com/Checkmarx/ast-jetbrains-plugin/issues/new">Request Feature</a>
</p>
<br>

<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#Overview">Overview</a></li>
    <li><a href="#key-features">Key Features</a></li>
    <li><a href="#prerequisites">Prerequisites</a></li>
    <li><a href="#Initial-Setup">Initial Setup</a></li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#Contribution">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
    <li><a href="#feedback">Feedback</a></li>
  </ol>
</details>




# Overview

The **​Checkmarx Developer Assist** plugin provides an agentic AI tool that delivers real-time context-aware prevention, remediation, and guidance to developers inside the IDE.

> This plugin provides easy integration with JetBrains IDEs. It is officially supported for IntelliJ IDEA. It may work effectively for other JetBrains IDEs such as Rider, WebStorm, RubyMine, PyCharm, MPS, etc. However, Checkmarx does not guarantee full functionality and stability for these IDEs.

## Key Features
- Access the full power of Checkmarx One (SAST, SCA, IaC Security, API Security, Container Security) directly from your IDE.
- ASCA, a lightweight realtime source code scanner, enables developers to identify secure coding best practice violations in the file that they are working on **as they code**.
- Rescan an existing branch from your IDE or create a new branch in Checkmarx One for the local branch in your workspace.
- Provides actionable results including remediation recommendations. Navigate from results panel directly to the highlighted vulnerable code in the editor and get right down to work on the remediation.
- Connect to Checkmarx Developer Assist via API Key
- Apply Auto Remediation to automatically remediate open source vulnerabilities, by updating to a non-vulnerable package version.
#### **Checkmarx One Developer Assist – AI guided remediation**
- An advanced security agent that delivers real-time context-aware prevention, remediation, and guidance to developers from the IDE.
- <b>OSS</b> Realtime scanner identifies risks in open source packages used in your project.
- <b>Secrets</b> Realtime scanner identifies risks in detected secrets used in your project.
- <b>Container</b> Realtime scanner identifies risks in container images used in your project.
- <b>IAC</b> Realtime scanner identifies infrastructure configuration risks in your project.
- <b>ASCA</b> Realtime scanner identifies insecure patterns in your project source code.
- MCP-based agentic AI remediation.
- AI powered explanation of risk details


## Prerequisites

- You are running IntelliJ version 2022.2+
> Early versions of our plugin (2.0.16 and below) support JetBrains version 2021.1+ as well.
> If you are using a JetBrains IDE other than IntelliJ (**Note:** these are not officially supported), make sure that you are using a version based on IntelliJ version 2022.2+.
- You have access to Checkmarx Developer Assist via:
- an **API key** (see [Generating an API Key](https://checkmarx.com/resource/documents/en/34965-68618-generating-an-api-key.html))
- The **Checkmarx MCP** must be activated for your tenant account in the Checkmarx One UI under **Settings → Plugins**. This must be done by an account admin.
- You must have **GitHub Copilot Chat  (AI Agent)** installed

## Initial Setup
- Verify that all prerequisites are in place.
- Install the **Checkmarx Developer Assist** plugin and configure the settings as
  described [here](https://docs.checkmarx.com/en/34965-68734-installing-and-setting-up-the-checkmarx-one-jetbrains-pluging-68734.html#UUID-8d3bdd51-782c-2816-65e2-38d7529651c8_section-idm449017032697283334758018635).
> Note: To use Dev Assist, you may need to **Start** the Checkmarx MCP server.
**GIF – Installing and Setting Up the Plugin**
![Installing and Setting Up the Plugin](https://raw.githubusercontent.com/Checkmarx/ci-cd-integrations/main/.images/JetBrains_Installation_And_Initial_Setup.gif "Installing and Setting Up the Plugin")

## Usage
* To see how you can use our tool, please refer to the [Documentation](https://docs.checkmarx.com/en/34965-68736-using-the-checkmarx-one-jetbrains-plugin.html#UUID-54985b7e-78ae-a5e7-4afc-0195ed2c18b3)
* Learn about using Dev Assist [here](https://docs.checkmarx.com/en/34965-405960-checkmarx-one-developer-assist.html)


## Feedback
We’d love to hear your feedback! If you come across a bug or have a feature request, please let us know by submitting an issue in [GitHub Issues](https://github.com/Checkmarx/ast-jetbrains-plugin/issues).


## Contribution

We appreciate feedback and contribution to the JETBRAINS PLUGIN! Before you get started, please see the following:

- [Checkmarx contribution guidelines](docs/contributing.md)
- [Checkmarx Code of Conduct](docs/code_of_conduct.md)

<!-- LICENSE -->
## License
Distributed under the [Apache 2.0](LICENSE). See `LICENSE` for more information.


<!-- CONTACT -->
## Contact

Checkmarx - Checkmarx One Integrations Team

Project Link: [https://github.com/Checkmarx/ast-jetbrains-plugin](https://github.com/Checkmarx/ast-jetbrains-plugin)

Find more integrations from our team [here](https://github.com/Checkmarx/ci-cd-integrations#checkmarx-ast-integrations)


© 2025 Checkmarx Ltd. All Rights Reserved.

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