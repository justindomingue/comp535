package socs.network.node;

import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Created by justindomingue on 15-09-21.
 */
public class ServerHandler implements Runnable {
    private Socket clientSocket;
    private Link link;
    private Router main;

    public ServerHandler(Socket clientSocket, Link link, Router main) {
        this.clientSocket = clientSocket;
        this.link = link;
        this.main = main;
    }

    public void run() {
        RouterDescription rd = link.router1;

        try {
            ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());

            // Send first HELLO
            SOSPFPacket answerPacket = new SOSPFPacket();
            answerPacket.srcIP = rd.simulatedIPAddress;
            answerPacket.srcProcessIP = rd.processIPAddress;
            answerPacket.srcProcessPort = rd.processPortNumber;
            answerPacket.dstIP = clientSocket.getRemoteSocketAddress().toString();
            answerPacket.sospfType = 0;
            answerPacket.routerID = rd.simulatedIPAddress;
            answerPacket.neighborID = rd.simulatedIPAddress;

            output.writeObject(answerPacket);

            SOSPFPacket responsePacket;

            while (true) {
                // Wait for answer
                responsePacket = (SOSPFPacket) input.readObject();

                // if HELLO
                if (responsePacket.sospfType == 0) {
                    link.router2.status = RouterStatus.TWO_WAY;

                    // Acknowledge
                    System.out.println("received HELLO from " + link.router2.simulatedIPAddress + ";");
                    System.out.println("set " + link.router2.simulatedIPAddress + " state to " + link.router2.status + ";");

                    // Answer with HELLO
                    output.writeObject(answerPacket);

                    main.addToLSD(link);
                    main.makeUpdateListener(clientSocket);
                    main.sendUpdate(clientSocket);

                    return;

                } else if (responsePacket.sospfType == 1) {
                    System.out.println("Received update.");
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
        }
    }
}
