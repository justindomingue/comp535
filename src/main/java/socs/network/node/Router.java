package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;


public class Router {

    protected LinkStateDatabase lsd;

    RouterDescription rd = new RouterDescription();

    //assuming that all routers are with 4 ports
    static Link[] ports = new Link[4];
    private List<Socket> sockets = new ArrayList<Socket>();

    // Client will set to true when it receives an LSAUPDATE
    // Server monitors this variable and broadcasts the update when it changes to true
    static boolean doSendUpdate = false;
    private int numServers = 0;

    private ServerSocket server;
    private boolean serverIsRunning = false;

    public Router(Configuration config) {
        rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        lsd = new LinkStateDatabase(rd);

        System.out.println("Initialized router with IP " + rd.simulatedIPAddress);

        //todo tmp
        Random rn = new Random();
        int n = rn.nextInt(1000) + 5000;

        try {
            // open a server socket
            server = new ServerSocket(n);
            System.out.println("Real IP " + server.getLocalSocketAddress() + " real port: " + server.getLocalPort());

            rd.processIPAddress = server.getLocalSocketAddress().toString();
            rd.processPortNumber = (short)server.getLocalPort();

            final Router main = this;

            new Thread(new Runnable() {
                public void run() {
                    serverIsRunning = true;

                    try {
                        while(true) {
                            // listen for and accept connections from clients
                            Socket serviceSocket = server.accept();

                            // Check for an available port
                            short portIndex = -1;
                            for (short i = 0; i < 4; i++) {
                                if (ports[i] == null) {
                                    portIndex = i;
                                    break;
                                }
                            }

                            if (portIndex == -1) {
                                System.out.println("No more ports available.");
                                continue;
                            }

                            // Add link server-null - null will be filled upon connection
                            Router.ports[portIndex] = new Link(rd, null, (short)1);

                            // span thread
                            new Thread(new ClientHandler(serviceSocket, Router.ports[portIndex], main)).start();
                        }
                    } catch (IOException e) {
                        System.out.println(e);
                    }
                }
            }).start();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    /**
     * output the shortest path to the given destination ip
     * <p/>
     * format: source ip address  -> ip address -> ... -> destination ip
     *
     * @param destinationIP the ip adderss of the destination simulated router
     */
    private void processDetect(String destinationIP) {

    }

    /**
     * disconnect with the router identified by the given destination ip address
     * Notice: this command should trigger the synchronization of database
     *
     * @param portNumber the port number which the link attaches at
     */
    private void processDisconnect(short portNumber) {

    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to identify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * NOTE: this command should not trigger link database synchronization
     */
    private void processAttach(String processIP, short processPort,
                               String simulatedIP, short weight) {
        boolean alreadyAttached = false;

        // Check for an available port
        short portIndex = -1;
        for (short i = 0; i < 4; i++)
        {
            if (ports[i] == null) {
                portIndex = i;
            } else if (ports[i].router2 != null && ports[i].router2.simulatedIPAddress.equals(simulatedIP)) {
                alreadyAttached = true;
            }
        }

        if (alreadyAttached) {
            System.out.println("Already attached.");
            return;
        }
        if (portIndex == -1) {
            System.out.println("No more ports available.");
            return;
        }

        // Create router description for remote router
        RouterDescription remote = new RouterDescription();
        remote.processIPAddress = processIP;
        remote.processPortNumber = processPort;
        remote.simulatedIPAddress = simulatedIP;
        remote.status = RouterStatus.INIT;

        // Add new link between current router/remote router with weight
        ports[portIndex] = new Link(rd, remote, weight);
    }

    /**
     * broadcast Hello to neighbors
     */
    private void processStart() {
        for (Link link: ports) {
            if (link == null) continue;

            Socket clientSocket;
            try {
                clientSocket = new Socket(link.router2.processIPAddress,link.router2.processPortNumber);

                new Thread(new ServerHandler(clientSocket, link, this)).start();

            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    /**
     * attach the link to the remote router, which is identified by the given simulated ip;
     * to establish the connection via socket, you need to indentify the process IP and process Port;
     * additionally, weight is the cost to transmitting data through the link
     * <p/>
     * This command does trigger the link database synchronization
     */
    private void processConnect(String processIP, short processPort,
                                String simulatedIP, short weight) {

    }

    /**
     * output the neighbors of the routers
     */
    private void processNeighbors() {
        System.out.println("Neighbors of " + this.rd.simulatedIPAddress + ":");

        for (Link link : this.ports) {
            if (link != null) {
                System.out.println(link.router2.simulatedIPAddress);
            }
        }
    }

    /**
     * disconnect with all neighbors and quit the program
     */
    private void processQuit() {

    }

    /**
     * called once handshake for `socket` connection is done.
     * will create two threads:
     *      1) listens for LSA Update
     *      2) writes LSA Update
     * @param socket
     */
    public void makeUpdateListener(final Socket socket) {
        Runnable listener = new Runnable() {
            public void run() {
                System.out.println("Listening");
                try {
                    Socket clientSocket = socket;
                    ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());

                    SOSPFPacket responsePacket;
                    while (true) {
                        responsePacket = (SOSPFPacket) input.readObject();
                        System.out.println("Listener received an update.");

                        boolean dbChanged = decodeLSA(responsePacket.lsaArray);
                        if (dbChanged) {
                            System.out.println("DB has changed.");
                        }
                        sendUpdate(socket); // send update to all but this socket
                    }

                } catch (IOException e) { System.out.println(e);
                } catch (ClassNotFoundException e) { System.out.println(e);
                }
            }
        };

        (new Thread(listener)).start();

        sockets.add(socket);
    }

    public void sendUpdate(Socket from) {

        SOSPFPacket answerPacket = new SOSPFPacket();
        answerPacket.srcIP = rd.simulatedIPAddress;
        answerPacket.srcProcessIP = rd.processIPAddress;
        answerPacket.srcProcessPort = rd.processPortNumber;
        answerPacket.sospfType = 1;
        answerPacket.routerID = rd.simulatedIPAddress;
        answerPacket.neighborID = rd.simulatedIPAddress;
        answerPacket.lsaArray = encodeLSA();

        for (Socket socket : sockets) {
            if (socket == from) continue;

            System.out.println("Sending update");

            try {
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());

                answerPacket.dstIP = socket.getRemoteSocketAddress().toString();

                output.writeObject(answerPacket);
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    public void addToLSD(Link link) {
//        LSA old = lsd._store.get(rd.simulatedIPAddress);
//
//        if (old == null) {
//            LSA lsa = new LSA();
//        }
//        lsa.linkStateID = rd.simulatedIPAddress;
//        lsa.lsaSeqNumber = Integer.MIN_VALUE;
//        lsd._store.put(rd.simulatedIPAddress, lsa);
    }
    private  boolean decodeLSA(Vector<LSA> lsas) {
        boolean dbUpdated = false;

        for (LSA lsa : lsas) {
            LSA old = lsd._store.get(lsa.linkStateID);
            if (old == null || old.lsaSeqNumber < lsa.lsaSeqNumber) {
                lsd._store.put(lsa.linkStateID, lsa);
                dbUpdated = true;
            }
        }

        return dbUpdated;
    }

    private Vector<LSA> encodeLSA() {
        Vector<LSA> v = new Vector<LSA>();

        for (LSA lsa : this.lsd._store.values()) {
            v.add(lsa);
        }

        return v;
    }

    public void terminal() {
        try {
            InputStreamReader isReader = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(isReader);
            System.out.print(">> ");
            String command = br.readLine();
            while (true) {
                if (command.startsWith("detect ")) {
                    String[] cmdLine = command.split(" ");
                    processDetect(cmdLine[1]);
                } else if (command.startsWith("disconnect ")) {
                    String[] cmdLine = command.split(" ");
                    processDisconnect(Short.parseShort(cmdLine[1]));
                } else if (command.startsWith("quit")) {
                    processQuit();
                } else if (command.startsWith("attach ")) {
                    String[] cmdLine = command.split(" ");
                    processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));
                } else if (command.equals("start")) {
                    processStart();
                } else if (command.equals("connect ")) {
                    String[] cmdLine = command.split(" ");
                    processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                            cmdLine[3], Short.parseShort(cmdLine[4]));
                } else if (command.equals("neighbors")) {
                    //output neighbors
                    processNeighbors();
                } else {
                    //invalid command
                    break;
                }
                System.out.println(">> ");
                command = br.readLine();
            }
            isReader.close();
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
