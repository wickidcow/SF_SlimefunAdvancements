<div align="center">

# 🏆📜 SlimefunAdvancements — Slimefun Legacy

**Configurable Minecraft advancements for Slimefun progression and addon content.**

![Slimefun Legacy](https://img.shields.io/badge/Slimefun-Legacy-6bd425?style=for-the-badge)
![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)
![Maintained for AlbionMC.com](https://img.shields.io/badge/Maintained%20for-albionmc.com-7b68ee?style=for-the-badge)

</div>

> [!IMPORTANT]
> This is an **unofficial Slimefun Legacy maintenance fork** of SlimefunAdvancements, developed for use on **albionmc.com** while preserving the original project, English presentation, and community compatibility work.

## 🏆 What does SlimefunAdvancements do?

SlimefunAdvancements adds a **configurable advancement system** for Slimefun. Server owners can define advancement trees and criteria around Slimefun progression, and compatible addons can provide their own advancement/group definitions for import.

Core features include:

- configurable advancement definitions in `advancements.yml`;
- advancement groups in `groups.yml`;
- import support for addon-provided `sfadvancements.yml` and `sfagroups.yml` files;
- custom criteria/API support documented in `api.md`;
- commands for managing and importing advancement content;
- compatibility maintenance for modern vanilla advancement behavior and Slimefun guide integrations.

Main command:

```text
/sfadvancements
```

Alias:

```text
/sfa
```

Command permissions use the `sfa.command.<command>` pattern documented in `plugin.yml`.

## 🧪 Slimefun Legacy maintenance

This fork preserves the original English names/messages and intended gameplay while carrying forward useful compatibility fixes from later community forks. Modern advancement injection must respect the server thread model, and the maintained build keeps that safety boundary in mind.

Exact external dependency requirements are determined by the current build and `plugin.yml`; server owners should review the startup log after upgrades and keep complete backups before replacing production builds.

## ❤️ Credits & project lineage

- **char3210** — original creator and maintainer of **SlimefunAdvancements**.
- **char3210/SlimefunAdvancements** — original source project and advancement-system foundation.
- **SlimefunGuguProject/SlimefunAdvancements** — immediate upstream fork from which this repository was created and a source of later Minecraft/Slimefun compatibility work.
- **ybw0014 and community maintainers** — later build/compatibility work in the extended SlimefunAdvancements family where applicable.
- **Slimefun developers and contributors** — for the platform and addon APIs.
- **wickidcow / Slimefun Legacy** — current English-first compatibility and preservation work for modern servers and albionmc.com.

This fork intentionally keeps both the original author and later fork lineage visible. It does not claim original authorship.

## 📜 GNU General Public License v3.0

SlimefunAdvancements is licensed under the **GNU General Public License v3.0 (GPLv3)**. See `LICENSE` for the complete terms.

If you distribute the plugin or a modified GPL-covered version, comply with GPLv3, including preserving applicable notices, identifying modified versions, licensing covered modified source under GPLv3, and making the required Corresponding Source available when distributing object code.

The software is provided **without warranty** as described by GPLv3.

## ⚖️ Independence & trademark notice

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

SlimefunAdvancements, Slimefun Legacy, and this maintenance fork are independent community projects. They are not sponsored, endorsed, approved, or operated by Mojang Studios or Microsoft. Minecraft-related names, brands, and assets remain the property of their respective rights holders.

This repository is also not represented as an official release of char3210, SlimefunGuguProject, ybw0014, or the original Slimefun team unless explicitly stated by those parties.

---

<div align="center">

**🏆 Give Slimefun progression the advancement tree it deserves. 📜**

</div>
