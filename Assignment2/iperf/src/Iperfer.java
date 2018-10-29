import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Iperfer {
    private static void client(String hostname, int port, long time) {
        long numBytes = 0;
        long totalTime = 0;
        try (
                Socket socket = new Socket(hostname, port);
                OutputStream out = socket.getOutputStream()
        ) {
            byte[] chunk = new byte[1000];
            Arrays.fill(chunk, (byte) 0);
            time = (long) (time * 1e9);
            long startTime = System.nanoTime();
            while (totalTime < time) {
                out.write(chunk, 0, 1000);
                out.flush();
                numBytes += 1000;
                totalTime = System.nanoTime() - startTime;
            }
        } catch (IOException e) {
            System.err.println(String.format("Error: " + e.getMessage()));
            System.exit(1);
        }
        System.out.println("Total Time: " + String.valueOf(totalTime / 1e9));
        long sentKB = (long) (numBytes / 1e3);
        double rate = 1.0 * numBytes * 8 / (totalTime / 1e3);
        System.out.println(String.format("sent=%d KB rate=%f Mbps", sentKB, rate));
    }

    private static void server(Integer port) {
        long totalTime = 0;
        long numBytes = 0;
        try (
                ServerSocket socket = new ServerSocket(port);
                Socket clientSocket = socket.accept();
                DataInputStream in = new DataInputStream(clientSocket.getInputStream())
        ) {
            byte [] inputData = new byte[1000];
            int len = in.read(inputData,0, 1000);
            long startTime = System.nanoTime();
            while (len != -1) {
                numBytes += len;
                totalTime = System.nanoTime() - startTime;
                len = in.read(inputData, 0, 1000);
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("Total Time: " + String.valueOf(totalTime / 1e9));
        long receiveKB = (long) (numBytes / 1e3);
        double rate = numBytes * 8 / (totalTime / 1e3);
        System.out.println(String.format("received=%d KB rate=%f Mbps", receiveKB, rate));
    }


    public static void main(String[] args) {
        // write your code here
        ArgParser parser = new ArgParser();
        parser.parse(args);
        if (parser.isClient)
            client(parser.hostname, parser.port, parser.time);
        else {
            server(parser.port);
        }
    }
}
