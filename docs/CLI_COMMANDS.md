# Blender `--command` Reference

Blender's `--command` (or `-c`) option runs a registered command in
background mode. The command consumes every argument after its identifier.

```sh
blender --command <command> [command arguments...]
blender -c <command> [command arguments...]
```

Use the installed Blender executable to discover the authoritative command
list and the options for a specific command:

```sh
blender --command help
blender --command <command> --help
```

The available command identifiers are not a fixed API. Blender builds,
enabled extensions, and startup scripts can register additional commands, so
`blender --command help` is the complete list for the Blender installation
being used.

## Documented built-in commands

The Blender 5.2 documentation identifies the following command IDs:

| Command            | Purpose                                                  |
|--------------------|----------------------------------------------------------|
| `help`             | List the registered commands.                            |
| `extension`        | Manage, package, validate, and index Blender Extensions. |
| `maketx`           | Generate Cycles `.tx` texture files.                     |
| `keyconfig_export` | Export a key configuration.                              |
| `sysinfo`          | Print Blender and system information.                    |

Commands beyond this list may be present in a particular installation.

## `extension` command

```text
blender --command extension <subcommand> [arguments...]
```

### Package management

```text
list [-s|--sync]
sync
update [-s|--sync]
install [-s|--sync] [-e|--enable] [--no-prefs] PACKAGES
install-file -r|--repo REPO [-e|--enable] [--no-prefs] FILE
remove [--no-prefs] PACKAGES
```

- `PACKAGES` is a comma-separated list with no spaces.
- `--no-prefs` makes user preferences read-only for commands that would
  normally update them.

### Repository management

```text
repo-list
repo-add [--name NAME] [--directory DIRECTORY] [--url URL]
         [--access-token ACCESS_TOKEN] [--source SOURCE]
         [--cache BOOLEAN] [--clear-all] [--no-prefs] ID
repo-remove [--no-prefs] ID
```

Avoid placing an access token directly in a shell command where it may be
saved in shell history or exposed in process listings.

### Extension creation

```text
build [--source-dir DIR] [--output-dir DIR] [--output-filepath FILE]
      [--valid-tags FILE] [--split-platforms] [--verbose]
validate [--valid-tags FILE] [SOURCE_PATH]
server-generate --repo-dir DIR [--repo-config FILE] [--html]
                [--html-template FILE]
```

`--source-dir` defaults to the current directory. `validate` accepts either a
source directory or an extension archive and defaults to the current directory.

## Examples for Sakura Rig Utilities

Run these from the repository root:

```sh
# Validate the extension source package.
blender --command extension validate Extension/src

# Create a package in a temporary output directory.
blender --command extension build --source-dir Extension/src --output-dir /tmp

# Ask the installed Blender for the exact extension command syntax.
blender --command extension --help
blender --command extension build --help
```

## Registering an extension-defined command

Extensions can add commands dynamically with
`bpy.utils.register_cli_command(id, execute)`. The identifier must pass
Python's `str.isidentifier()` check, and the callback receives all arguments
following the identifier as `list[str]`. It returns `0` on success and a
non-zero exit code on failure. Retain the returned handle and unregister it
during the extension lifecycle with `bpy.utils.unregister_cli_command()`.

`rig_manager/PT_Rig_Manager.py` currently registers a 3D View sidebar panel;
it does not register a CLI command.

## Source material

- Blender 5.2 Manual: `advanced/command_line/arguments`
- Blender 5.2 Manual: `advanced/command_line/extension_arguments`
- Blender 5.2 Python API: `bpy.utils.register_cli_command`
