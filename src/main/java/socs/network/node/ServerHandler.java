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
    private RouterDescription rd;
    private short portNumber;

    public ServerHandler(Socket clientSocket, RouterDescription rd, short portNubmer) {
        this.clientSocket = clientSocket;
        this.rd = rd;
        this.portNumber = portNubmer;
    }

    public void run() {
        Link link = Router.ports[portNumber];
        try {
        // create output stream
        ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());

        // create input stream
        ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());


        SOSPFPacket responsePacket;
        while (true) {
            responsePacket = (SOSPFPacket)input.readObject();

            if (responsePacket.sospfType == 0) {
                // first hello
                if (link.router2.status == null) {
                    link.router2.status = RouterStatus.INIT;

                    // Answer with HELLO
                    SOSPFPacket answerPacket = new SOSPFPacket();

                    answerPacket.srcIP = rd.simulatedIPAddress;
                    answerPacket.srcProcessIP = rd.processIPAddress;
                    answerPacket.srcProcessPort = rd.processPortNumber;
                    answerPacket.dstIP = clientSocket.getRemoteSocketAddress().toString();
                    answerPacket.sospfType = 0;
                    answerPacket.routerID = rd.simulatedIPAddress;
                    answerPacket.neighborID = rd.simulatedIPAddress;

                    output.writeObject(answerPacket);

                // second hello
                } else {
                    link.router2.status = RouterStatus.TWO_WAY;
                }

                // Send HELLO
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
