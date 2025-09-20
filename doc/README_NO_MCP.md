# VS Code Profile: No MCP (Disable Model Context Protocol)

This repository includes a VS Code profile to disable MCP-related features globally when using this workspace.

## Profile file

- Path: `.vscode/profiles/no-mcp.code-profile`

## What it does

- Disables MCP servers for multiple extensions:
  - `mcpServers: {}`
  - `modelContextProtocol.enabled: false`
  - `copilot.mcp.enabled: false`
  - `continue.mcpServers: {}`
  - `continue.enableMcp: false`
- Reduces chat/AI prompts noise (`chat.commandCenter.enabled: false`)
- Adds unwanted recommendations for MCP-related extensions so they are not suggested.

## How to import and use

1. Open Command Palette in VS Code: `Ctrl+Shift+P` (Windows)
2. Run: `Profiles: Import Profile...`
3. Choose `Import from file...`
4. Select the file: `.vscode/profiles/no-mcp.code-profile`
5. After import, VS Code will offer to switch to the profile. Accept it.
6. (Optional) Set as default:
   - Command Palette → `Profiles: Manage Profiles`
   - Click the three dots on `No MCP` → `Set as Default Profile`

## Manual settings (alternative)

If you prefer manual settings without using the profile, add these keys to your user or workspace settings:

```json
{
  "mcpServers": {},
  "modelContextProtocol.enabled": false,
  "copilot.mcp.enabled": false,
  "continue.mcpServers": {},
  "continue.enableMcp": false
}
```

## Reverting

- To re-enable MCP, import/switch back to a profile that enables it, or set the above keys to their defaults (`true`/remove overrides).
