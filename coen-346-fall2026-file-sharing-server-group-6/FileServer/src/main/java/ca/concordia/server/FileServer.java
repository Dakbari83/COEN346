package ca.concordia.server;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import ca.concordia.filesystem.FileSystemManager;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        FileSystemManager fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.fsManager = fsManager;
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ", 3);
                        String command = parts[0].toUpperCase();

                try {
                    switch (command) {
                        case "CREATE":
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            fsManager.createFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' created.");
                            break;
                        case "WRITE":
                            if (parts.length < 3) {
                                writer.println("ERROR: missing filename or content");
                                break;
                            }
                            fsManager.writeFile(parts[1], parts[2].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            writer.println("SUCCESS: File '" + parts[1] + "' written.");
                            break;
                        case "DELETE":
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            fsManager.deleteFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                            break;
                        case "QUIT":
                            writer.println("SUCCESS: Disconnecting.");
                            return;
                        default:
                            writer.println("ERROR: Unknown command.");
                            break;
                    }
                } catch (Exception e) {
                    writer.println(e.getMessage());
                }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}
