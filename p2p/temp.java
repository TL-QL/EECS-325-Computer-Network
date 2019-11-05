import misc.Constants;
import misc.Messages;
import net.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class p2p {

    private static void closeAllNeighbors(Set<Socket> neighbors) throws IOException {
        synchronized (neighbors) {
            for (Socket socket : neighbors) {
                socket.close();
            }
            neighbors.clear();
        }
    }

    private static void connectToPeer(String ipAddress, String portNumber, Set<Socket> neighbors) {
        int port = Integer.parseInt(portNumber.trim());
        System.out.println(String.format(Messages.connectionAttempted, ipAddress, port));
        try {
            Socket clientSocket = new Socket(ipAddress, port);
            clientSocket.setSoTimeout(Constants.timeout);
            System.out.println(String.format(Messages.connectionSuccessful, ipAddress, port));
            neighbors.add(clientSocket);
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(new Heartbeat(clientSocket, neighbors, timer), Constants.heartbeat, Constants.heartbeat);
        } catch (Exception e) {
            System.out.println(String.format(Messages.connectionFailed, ipAddress, port));
        }
    }

    private static void connect(String ipAddress, String portNumber, Set<Socket> neighbors) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<String[]>> future = executor.submit(new UDPDiscoveryClient(ipAddress, portNumber));
        executor.shutdown();
        try {
            List<String[]> peers = future.get();
            if (peers.size() == 0) {
                System.out.println("\nConnection Failed");
                return;
            }
            connectToPeer(peers.get(0)[1], peers.get(0)[2], neighbors);
            if (peers.size() >= 2) {
                connectToPeer(peers.get(1)[1], peers.get(1)[2], neighbors);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }


    private static void downloadFile(String fileIP, int filePort, String fileName) {
        (new Thread(new AdHocTCP(fileIP, filePort, fileName))).start();
    }

    private static void getFile(String fileName, Set<Socket> neighbors, int query) {
        String response = QueryFlood.fetchFileLocation(fileName, neighbors, query);
        if (response == null) {
            System.out.println(String.format(Messages.queryResponseNegative, fileName));
            return;
        }
        String[] responses = response.split("[(;:)]");
        downloadFile(responses[3], Integer.parseInt(responses[4]), fileName);
    }


    public static void main(String[] args) {
        Set<Socket> neighbors = Collections.synchronizedSet(new HashSet<Socket>());

        System.out.println(Messages.peerStarted);
        (new Thread(new WelcomeSocket(neighbors))).start(); // Start welcome socket.
        (new Thread(new UDPDiscoveryServer(neighbors))).start();
        int queryCount = 0;

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.println(Messages.commandPrompt);
            try {
                String[] command = reader.readLine().split(" ");
                switch (command[0].toLowerCase()) {

                    case Messages.connect:
                        if (command.length != 2) {
                            System.out.println(Messages.commandNotFound);
                            System.out.println(Messages.connectUsage);
                        } else {
                            connect(command[1], command[2], neighbors);
                        }
                        break;

                    case Messages.get:
                        if (command.length != 2) {
                            System.out.println(Messages.commandNotFound);
                            System.out.println(Messages.getUsage);
                        } else {
                            getFile(command[1], neighbors, queryCount++);
                        }
                        break;

                    case Messages.leave:
                        closeAllNeighbors(neighbors);
                        break;

                    case Messages.exit:
                        closeAllNeighbors(neighbors);
                        System.exit(0);

                    case Messages.help:
                        System.out.println(Messages.options);
                        break;

                    default:
                        System.out.println(Messages.commandNotFound);
                        System.out.println(Messages.options);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

}