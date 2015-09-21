package socs.network.node;

import socs.network.message.SOSPFPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Created by justindomingue on 15-09-21.
 */
public class ClientHandler implements Runnable {

    private Socket serviceSocket;
    private RouterDescription rd;
    private short portNumber;

    public ClientHandler(Socket serviceSocket, RouterDescription rd, short portNubmer) {
        this.serviceSocket = serviceSocket;
        this.rd = rd;
        this.portNumber = portNubmer;
    }

    public void run() {
        Router.ports[portNumber] = null;

        try {
            // create output stream
            ObjectOutputStream output = new ObjectOutputStream(serviceSocket.getOutputStream());

            // create input stream
            ObjectInputStream input = new ObjectInputStream(serviceSocket.getInputStream());

            // now write to the output stream
            SOSPFPacket answerPacket = new SOSPFPacket();
            answerPacket.srcIP = rd.simulatedIPAddress;
            answerPacket.srcProcessIP = rd.processIPAddress;
            answerPacket.srcProcessPort = rd.processPortNumber;
            answerPacket.dstIP = serviceSocket.getRemoteSocketAddress().toString();
            answerPacket.sospfType = 0;
            answerPacket.routerID = rd.simulatedIPAddress;
            answerPacket.neighborID = rd.simulatedIPAddress;


            output.writeObject(answerPacket);

            // read until receive acknowledgment
            SOSPFPacket responsePacket;

            while (true) {
                responsePacket = (SOSPFPacket) input.readObject();

                // if HELLO
                if (responsePacket.sospfType == 0) {

                    RouterDescription remoteRouter = new RouterDescription();
                    remoteRouter.simulatedIPAddress = responsePacket.srcIP;
                    remoteRouter.processIPAddress = responsePacket.srcProcessIP;
                    remoteRouter.processPortNumber = responsePacket.srcProcessPort;
                    remoteRouter.status = RouterStatus.TWO_WAY;

                    // Add link to local ports
                    Link link = new Link(rd, remoteRouter, (short) 1);
                    Router.ports[portNumber] = link;

                    // Acknowledge
                    System.out.println("received HELLO from " + link.router2.simulatedIPAddress + ";");
                    System.out.println("set " + link.router2.simulatedIPAddress + " state to " + link.router2.status + ";");

                    // Answer with HELLO
                    output.writeObject(answerPacket);
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
        }
    }
}
