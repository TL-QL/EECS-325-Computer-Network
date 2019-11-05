import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;

class p2p{
  
  // Method to find specific file
  private static void findFile(String fileName, ArrayList<Socket> neighborSet, int queryID) throws Exception{
    //ArrayList<FutureTask<String>> responses = new ArrayList<FutureTask<String>>();
    String myResponse = "";
    boolean gotResponse = false;
    // Send query message to neighbors
    for(int i = 0;i < neighborSet.size() && (!gotResponse);i++){ 
      Task task = new Task(neighborSet.get(i), fileName, queryID);
      FutureTask<String> response = new FutureTask<String>(task);
      Thread thread = new Thread(response);
      thread.start();
      //responses.add(response);

      try{
          // Wait at most 60s for the response
          myResponse = response.get(60, TimeUnit.SECONDS);
          if(myResponse != null){
            gotResponse = true;
          }
      }
      catch(Exception e){
        // if timeout, cancel the task
        response.cancel(true);
      }
    } 
    
    // // Get response from neighbors
    // for(int i = 0;i < responses.size();i++){
    //   try{
    //     if(gotResponse) responses.get(i).cancel(true);
    //     else{
    //       // Wait at most 30s for the response
    //       String myReturn = (responses.get(i)).get(30, TimeUnit.SECONDS);
    //       if(myReturn != null){
    //         gotResponse = true;
    //         myResponse = myReturn;
    //       }
    //     }
    //   }
    //   catch(Exception e){
    //     // if timeout, cancel the task
    //     responses.get(i).cancel(true);
    //   }
    // }
    
    if(myResponse.isEmpty()) System.out.println("Nobody connected has the file: "+ fileName);
    else{
      // If find the file, setup a TCP to download it
      System.out.println("I find the file: "+ fileName+"!");
      String[] extractResponse = myResponse.split("[:;]");
      Thread thread2 = new Thread(new myTCP(extractResponse[2], Integer.parseInt(extractResponse[3]), fileName));
      thread2.start(); 
    }
  }
  
  // Method to close all connections with its neighbors
  private static void closeAllConnections(ArrayList<Socket> neighborSet) throws Exception{
    for(int i = 0;i < neighborSet.size();i++){
      (neighborSet.get(i)).close();
    }
    neighborSet.clear();
  }
  
  // Method to connect to all neighbors assigned
  private static void connectNeighbors(ArrayList<Socket> neighborSet) throws Exception{
    ArrayList<String[]> neighborsList = new ArrayList<String[]>();
    // Read config_neighbors.txt
    BufferedReader reader = new BufferedReader(new FileReader("config_neighbors.txt"));
    String neighbor = reader.readLine();
    while(neighbor != null){
      String neighbor_split[] = neighbor.split(" ");
      neighborsList.add(neighbor_split);
      neighbor = reader.readLine();
    }
    reader.close();
    
    if(neighborsList.size() <= 0) System.out.println("I am lonely! Connection Failed!");
    else{
      for(int i = 0;i < neighborsList.size();i++){
        System.out.println("I am trying to connect to another neighbor peer! Host: "+neighborsList.get(i)[0]+".");
        
        // Create client socket and connect to neighbors
        Socket clientSocket = new Socket(InetAddress.getByName(neighborsList.get(i)[0]).getHostAddress(), Integer.parseInt(neighborsList.get(i)[1]));
        
        System.out.println("Successfully connected to "+ neighborsList.get(i)[0]+"!");
        
        // Set Timeout for each clientSocket
        clientSocket.setSoTimeout(180000);
        
        neighborSet.add(clientSocket);
        // Create a Timer
        Timer t = new Timer();
        // Send HeartBeat for every 60000ms
        HeartBeat hb = new HeartBeat(clientSocket, neighborSet, t);
        t.schedule(hb, 300000, 300000);
        
      }
    }
  }
  
  // Method to exit
  private static void terminate(ArrayList<Socket> neighborSet) throws Exception{
    closeAllConnections(neighborSet);
    System.exit(0);
  }
  /******************************************************
    Private helper class
    *******************************************************/
  // Inner class as server for queries & responses
  private static class Welcome implements Runnable{
    // Store port# for query & response port
    private int queryResponsePort;
    // Store port# for file transfer port
    private int fileTransPort;
    // Store local host name
    private String hostName;
    // Store all neighbor sockets
    private ArrayList<Socket> neighborSet;
    // Store query ID which has already received
    private HashSet<Integer> queryIDs;
    
    // Initialize Welcome
    public Welcome(int queryResponsePort, int fileTransPort, String hostName, ArrayList<Socket> neighborSet, HashSet<Integer> queryIDs){
      this.queryResponsePort = queryResponsePort;
      this.fileTransPort = fileTransPort;
      this.hostName = hostName;
      this.neighborSet = neighborSet;
      this.queryIDs = queryIDs;
    }
    
    public void run(){ 
      try{
        // Creat sockets for queryResponse
        ServerSocket welcomeSocket = new ServerSocket(queryResponsePort);
        //ServerSocket fileTransSocket = new ServerSocket(fileTransPort);
        // Read file names of all shared files
        ArrayList<String> files = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new FileReader("config_sharing.txt"));
        String file = reader.readLine();
        while(file != null){
          files.add(file.trim());
          //System.out.println(file);
          file = reader.readLine();
        }
        
        reader.close();
        while(true){
          // Wait on a welcome socket for contact by neighbor peers
          Socket connectionSocket1 = welcomeSocket.accept();
          System.out.println("Accepted a connection initiated from another!");
          
          // Create a new thread for neighbors and start it
          Thread neighborThread = new Thread(new Neighbor(hostName, fileTransPort, connectionSocket1, neighborSet, files, queryIDs));
          //Thread neighborThread = new Thread(new Neighbor(hostName, connectionSocket1, neighborSet, files, queryIDs));
          neighborThread.start();
        }
      }
      catch(Exception e){
        System.out.println(e);
      }
    }
  }
  
  // Inner class to communicate with neighbors: query and heartbeat
  private static class Neighbor implements Runnable{
    // Store local host name
    private String hostName;
    // Store file trans port
    private int fileTransPort;
    // Store connectionSocket
    private Socket connectionSocket;
    // Store sockets for all neighbors
    private ArrayList<Socket> neighborSet;
    // Store names of files shared
    private ArrayList<String> files;
    // Store query ID which has already received
    private HashSet<Integer> queryIDs;
    
    // Initialize Neighbor
    public Neighbor(String hostName, int fileTransPort, Socket connectionSocket, ArrayList<Socket> neighborSet, ArrayList<String> files, HashSet<Integer> queryIDs){
      this.hostName = hostName;
      this.fileTransPort = fileTransPort;
      this.connectionSocket = connectionSocket;
      this.neighborSet = neighborSet;
      this.files = files;
      this.queryIDs = queryIDs;
    }
    
    public void run(){
      try{
        // Create input stream from socket
        BufferedReader inFromClient = 
          new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        // Create output stream from socket
        DataOutputStream outToClient = 
          new DataOutputStream(connectionSocket.getOutputStream());
        
        while(true){
          // Read messages from socket
          String clientSentence = inFromClient.readLine();
          // If there is any message from clients
          if(clientSentence != null){
            String[] request = clientSentence.split("[:;]");
            // If the request is a query message
            if(request[0].equals("Q")){
              if(queryIDs.contains(Integer.parseInt(request[1])));
              else{
                System.out.println("Received the query!");
                queryIDs.add(Integer.parseInt(request[1]));
                boolean hasFound = false;
                for(int j = 0;j < files.size() && (!hasFound);j++){
                  if(files.get(j).equals(request[2])) hasFound = true;
                }
                // If the peer has the requsted file, responds with its own IP
                if(hasFound){
                  System.out.println("I have the file: "+ request[2]+"!");
                  String peerIP = InetAddress.getByName(hostName).getHostAddress();
                  outToClient.writeBytes("R:"+request[1]+";"+peerIP+":"+ fileTransPort +";"+request[2]+"\n");
                  outToClient.flush();
                }
                // If the peer does not have the requested file, 
                // sends requests to its neighbors
                else{
                  String myResponse = "";
                  boolean gotResponse = false;
                  // Send query message to neighbors
                  for(int i = 0;i < neighborSet.size() && (!gotResponse);i++){ 
                    Task task = new Task(neighborSet.get(i), request[2], Integer.parseInt(request[1]));
                    FutureTask<String> response = new FutureTask<String>(task);
                    Thread thread = new Thread(response);
                    thread.start();
                    //responses.add(response);
              
                    try{
                        // Wait at most 30s for the response
                        myResponse = response.get(30, TimeUnit.SECONDS);
                        if(myResponse != null){
                          gotResponse = true;
                        }
                    }
                    catch(Exception e){
                      // if timeout, cancel the task
                      response.cancel(true);
                    }
                  } 
                  
                  if(myResponse.isEmpty()) System.out.println("Nobody connected has the file "+ request[2]+"!");
                  else{
                    // If someone else has the file, send response with the port number and IP of the peer who has the file
                    System.out.println("Someone else connected has the file!");
                    String[] extractResponse = myResponse.split("[:;]");
                    outToClient.writeBytes("R:"+request[1]+";"+extractResponse[2]+":"+ extractResponse[3] +";"+request[2]+"\n");
                    outToClient.flush();
                  }
                }
              }
            }
            // If request is a heart beat
            else if(request[0].equals("BiuBiuBiu")){
              try{
                // Respond to the heartbeat
                outToClient.writeBytes("BiuBiuBiu"+"\n");
                outToClient.flush();
              }
              catch(IOException e){
                System.out.println(e);
              }
            }
            else{
              System.out.println("Illegal command!");
            }
          }
        }
      }
      catch(Exception e1){
        System.out.println(e1);
      }
    }
  }
  
  // Inner class as server for file transfer
  private static class FileWelcome implements Runnable{
    // Store port# for file transfer port
    private int fileTransPort;
    // Store local host name
    private String hostName;
    
    public FileWelcome(int fileTransPort, String hostName){
      this.fileTransPort = fileTransPort;
      this.hostName = hostName;
    }
    
    // Create serversocket and wait for connection
    public void run(){
      try{
        ServerSocket fileTransSocket = new ServerSocket(fileTransPort);
        while(true){
          Socket connectionSocket = fileTransSocket.accept();
          Thread myThread = new Thread(new FileThread(hostName, connectionSocket));
          myThread.start();
        }
      }
      
      catch(Exception e){
        System.out.println(e);
      }
    }
  }
  
  // Inner class to communicate with neighbors: file transfer
  private static class FileThread implements Runnable{
    private String hostName;
    private Socket connectionSocket;
    
    public FileThread(String hostName, Socket connectionSocket){
      this.hostName = hostName;
      this.connectionSocket = connectionSocket;
    }
    
    public void run(){
      try{
        // Create input stream from socket
        BufferedReader inFromClient = 
          new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        // Create output stream from socket
        DataOutputStream outToClient = 
          new DataOutputStream(connectionSocket.getOutputStream());
        
        String clientSentence = inFromClient.readLine();
        if(clientSentence != null){
          String[] request = clientSentence.split("[:;]");
          // If it is a file transfer request
          if(request[0].equals("T")){
            System.out.println("Received a file transfer request!");
            
            // Create a reader to read the context of the file
            BufferedReader inFromFile = new BufferedReader(new InputStreamReader(new FileInputStream(new File("shared/"+request[1]))));
            String row = inFromFile.readLine();
            
            // Send the requested file
            while(row != null){
              outToClient.writeBytes(row+"\n");
              outToClient.flush();
              row = inFromFile.readLine();
            }
            
            // Finished transmission
            inFromFile.close();
            outToClient.flush();
            inFromClient.close();
            outToClient.close();
            connectionSocket.close();
            System.out.println("Completed the file transmission to the requesting peer!");
          }
          else{
            System.out.println("Illegal command!");
          }
        }
      }
      catch(Exception e){
        System.out.println(e);
      }
    }
  }
  
  // Periodically send HeartBeat to neighbors
  private static class HeartBeat extends TimerTask{
    // Store clientSocket
    private Socket clientSocket;
    // Store all neighbor sockets
    private ArrayList<Socket> neighborSet;
    // Store a Timer
    private Timer t;
    
    // Initialize a HeartBeat
    public HeartBeat(Socket clientSocket, ArrayList<Socket> neighborSet, Timer t){
      this.clientSocket = clientSocket;
      this.neighborSet = neighborSet;
      this.t = t;
    }
    
    public void run(){
      
      synchronized(clientSocket){
        // Send heartbeat and wait for response
        try{
          DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
          BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
          
          System.out.println("Sending a heart beat to a neighbor...");
          
          outToServer.writeBytes("BiuBiuBiu"+"\n");
          outToServer.flush();
          String response = inFromServer.readLine();
          if(response.equals("BiuBiuBiu")) {
            
            System.out.println("Received a heart beat echo from a neighbor!");
          }
        }
        // If timeout, close the connection
        catch(SocketTimeoutException e){
          System.out.println("Timeout! Closing the connection!");
          t.cancel();
          try{
            clientSocket.close();
            neighborSet.remove(clientSocket);
          }
          catch(Exception e1){
            System.out.println(e1);
          }
        }
        catch(Exception e){
          System.out.println(e);
        }
      }
      
    }
  }
  
  // Inner class to send query message to neighbors
  private static class Task implements Callable<String>{
    // Store a neighbor socket
    private Socket neighbor;
    // Store requested file name
    private String file;
    // Store query ID
    private int queryID;
    
    // Initiate Task
    public Task(Socket neighbor, String file, int queryID){
      this.neighbor = neighbor;
      this.file = file;
      this.queryID = queryID;
    }
    
    // Send query message to neighbors
    public String call() throws Exception {
      
      //synchronized(neighbor){
        System.out.println("Query for a neighbor...");
        
        DataOutputStream outToServer = new DataOutputStream(neighbor.getOutputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(neighbor.getInputStream()));
        
        outToServer.writeBytes("Q:"+ queryID+";"+file+"\n");
        outToServer.flush();
        String response = inFromServer.readLine();
        return response;
      //}
      
    }
  }
  
  // Inner class for Ad hoc TCP for file transfer
  private static class myTCP implements Runnable{
    // Store the IP where has the requested file
    private String iP;
    // Store the file transfer port
    private int port;
    // Store the requested file name
    private String file;
    
    // Initiate myTCP
    public myTCP(String iP, int port, String file){
      this.iP = iP;
      this.port = port;
      this.file = file;
    }
    
    // Request a file transfer
    public void run(){
      try{
        System.out.println("Requesting a file transfer from a neighbor...");
        
        // Create client socket and input/output stream for file transfer
        Socket clientFileSocket = new Socket(iP, port);
        clientFileSocket.setSoTimeout(100000);
        DataOutputStream query = new DataOutputStream(clientFileSocket.getOutputStream());
        BufferedReader queryHit = new BufferedReader(new InputStreamReader(clientFileSocket.getInputStream()));
        // request file
        query.writeBytes("T:"+file+"\n");
        query.flush();
        
        BufferedWriter outToFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File("obtained/"+file))));
        String context = queryHit.readLine();
        
        // save file to /obtain
        while(context != null){
          outToFile.write(context + "\n");
          outToFile.flush();
          context = queryHit.readLine();
        }
        outToFile.close();
        
        System.out.println("Successful transfer!");
        
        clientFileSocket.close();
      }
      catch(Exception e){
        System.out.println(e);
      }
    }
  }
  
  public static void main(String[] args) throws Exception{
    // The peer started. 
    System.out.println("I am starting!");
    
    // An ArrayList used to store all sockets for neighbors.
    ArrayList<Socket> neighborSet = new ArrayList<Socket>();
    // Store query ID which has already received
    HashSet<Integer> queryIDs = new HashSet<Integer>();
    
    // Read config_peer.txt file.
    // Get port# for query&response and file transfer and hostName
    Scanner scanner = new Scanner(new File("config_peer.txt"));
    int queryResponsePort = scanner.nextInt();
    int fileTransPort = scanner.nextInt();
    String hostName = scanner.next();
    scanner.close();

    // queryID
    int queryID = queryResponsePort*100;
    
    // Create a new thread for welcomesockets and start it.
    Thread welcomeThread = new Thread(new Welcome(queryResponsePort, fileTransPort, hostName, neighborSet, queryIDs));
    welcomeThread.start();
    
    Thread fileWelcomeThread = new Thread(new FileWelcome(fileTransPort, hostName));
    fileWelcomeThread.start();
    
    // Read commands from System.in.
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    
    while(true){
      String command = reader.readLine();
      String[] commands = command.split(" ");
      
      if(commands.length <= 0) System.out.println("Illegal command!");
      // Command = Get <filename>
      else if(commands[0].equals("Get")){
        if(commands.length != 2) System.out.println("Illegal command! Missing filename!");
        else{
          queryIDs.add(queryID);
          findFile(commands[1], neighborSet, queryID++);
        }
      }
      // Command = Leave
      else if(commands[0].equals("Leave")){
        if(commands.length != 1) System.out.println("Illegal command!");
        else
          closeAllConnections(neighborSet);
      }
      // Command = Connect
      else if(commands[0].equals("Connect")){
        if(commands.length != 1) System.out.println("Illegal command!");
        else
          connectNeighbors(neighborSet);
      }
      // Command = Exit
      else if(commands[0].equals("Exit")){
        if(commands.length != 1) System.out.println("Illegal command!");
        else
          terminate(neighborSet);
      }
      else
        System.out.println("Illegal command!");
    }
  }
}
