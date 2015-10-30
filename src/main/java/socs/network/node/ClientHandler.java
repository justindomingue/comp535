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
    private Link link;

    public ClientHandler(Socket serviceSocket, Link link) {
        this.serviceSocket = serviceSocket;
        this.link = link;
    }

    public void run() {
        try {
            ObjectOutputStream output = new ObjectOutputStream(serviceSocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(serviceSocket.getInputStream());

            RouterDescription rd = link.router1;

            SOSPFPacket responsePacket;
            while (true) {
                responsePacket = (SOSPFPacket)input.readObject();

                if (responsePacket.sospfType == 0) {
                    // first hello
                    if (link.router2 == null) {
                        RouterDescription remote = new RouterDescription();
                        remote.processIPAddress = responsePacket.srcProcessIP;
                        remote.processPortNumber = responsePacket.srcProcessPort;
                        remote.simulatedIPAddress = responsePacket.srcIP;
                        remote.status = RouterStatus.INIT;
                        link.router2 = remote;

                        // Answer with HELLO
                        SOSPFPacket answerPacket = new SOSPFPacket();

                        answerPacket.srcIP = rd.simulatedIPAddress;
                        answerPacket.srcProcessIP = rd.processIPAddress;
                        answerPacket.srcProcessPort = rd.processPortNumber;
                        answerPacket.dstIP = serviceSocket.getRemoteSocketAddress().toString();
                        answerPacket.sospfType = 0;
                        answerPacket.routerID = rd.simulatedIPAddress;
                        answerPacket.neighborID = rd.simulatedIPAddress;

                        output.writeObject(answerPacket);

                        // second hello
                    } else {
                        link.router2.status = RouterStatus.TWO_WAY;
                    }

                    System.out.println("received HELLO from " + link.router2.simulatedIPAddress + ";");
                    System.out.println("set " + link.router2.simulatedIPAddress + " state to " + link.router2.status + ";");
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
        }
    }
}
