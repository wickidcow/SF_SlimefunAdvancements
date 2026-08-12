# SlimefunAdvancements

A configurable advancement system for Slimefun.

This fork keeps the original SlimefunAdvancements gameplay and English presentation while carrying forward newer Minecraft, Paper, Slimefun, vanilla-advancement, and JustEnoughGuide compatibility work.

## Credits

SlimefunAdvancements was originally created and maintained by **char3210**. This repository uses the original English project as the reference for names, messages, and intended behavior while preserving newer compatibility fixes from later community development.

Original project: `char3210/SlimefunAdvancements`

## Requirements

- Java 16 or newer for the current project baseline
- A compatible Slimefun installation
- GuizhanLibPlugin, as required by the current compatibility code

The vanilla advancement injection logic must run on the server main thread.

## Commands and permissions

Main command: `/sfadvancements` (alias: `/sfa`)

Permissions use the format:

` sfa.command.<command name> `

Available command permissions are documented in `plugin.yml`.

## Configuration

Default advancement definitions are stored in `advancements.yml`, with advancement groups in `groups.yml`.

Other addons may provide `sfadvancements.yml` and `sfagroups.yml` files that can be imported with `/sfa import <plugin>`.

## Developer API

See [api.md](./api.md) for the custom criteria/API documentation.

## Builds

GitHub Actions builds the versioned plugin JAR from this repository. Release/build naming for this fork uses:

`SF_SlimefunAdvancements1.x.x.jar`
