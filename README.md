# ServerPingFixer

ServerPingFixer resolves an issue where the initial server ping fails when a Minecraft server is protected by **GCORE**.

## Problem

When a server is behind GCORE protection, the first status ping sent by the Minecraft client may fail. As a result, the server does not appear correctly in the multiplayer server list until it is refreshed.

## Solution

ServerPingFixer modifies the ping sequence:

1. Sends a normal status ping
2. Sends a second normal status ping
3. Sends the legacy ping afterwards
