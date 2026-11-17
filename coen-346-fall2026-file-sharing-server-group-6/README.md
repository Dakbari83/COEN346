# COEN 346 – Programming Assignment 2: A File Sharing Server

This repository contains the starter code for the assignment. Read the handout on Moodle before you start.

## Contents

- `FileServer/` – the server. **This is the only project you modify.**
- `FileClient/` – a console client (`Main`) and a stress test (`StressTestClient`) for testing your server. Do not modify them.
- `Sample session.txt` – the exact replies the server is expected to send, and how to use the stress test.

## Requirements

- JDK 21 or later
- IntelliJ IDEA (recommended) or Maven

## Running the projects

1. In IntelliJ, open `FileServer` and `FileClient` as two separate projects (File → Open, select the folder, and trust the Maven project).
2. Run `ca.concordia.Main` in **FileServer**. The server listens on port 12345.
3. Run `ca.concordia.Main` in **FileClient**, then type commands such as `CREATE notes` or `LIST`. Type `exit` to disconnect.
4. To try several clients at once, run more than one instance of the client (in IntelliJ: Run → Edit Configurations → Modify options → Allow multiple instances).
5. Once CREATE, WRITE, READ and LIST work, run `ca.concordia.StressTestClient` in **FileClient** to test your server with many clients at the same time.

If port 12345 is already in use on your machine, change it in both `FileServer/.../Main.java` and the clients.

## Working as a pair

Both partners must commit their own work to this repository. Commit often, with clear messages.
