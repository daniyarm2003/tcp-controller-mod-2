# tcp-controller-mod-2

This is a Java application, which modifies Minecraft with the use of the Forge Mod Loader, to allow clients such as Discord bots, Twitch bots, and more to interact with the running Minecraft world, by accepting TCP messages

- This mod creates a TCP server, which listens for incoming connections and messages
- Messages received are unmarshalled, and the mod performs actions specified by the messages
- The server supports connections from multiple clients simultaneously
