
package net.insertcreativity.sla;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;

import net.insertcreativity.util.LogWriter;

public class Server implements Closeable
{
	/**Reference to the logger used by this server*/
	private final LogWriter log;
	/**Flag for whether the server should continue running*/
	private volatile boolean keepRunning = true;
	
	/**Class for encapsulating all the functionality of this server, listens for requested connections, and creates new
	 * ClientManagers for handling them. Terminates when the serverInput socket is closed, and keepRunning is false,
	 * otherwise will attempt a self-repair.*/
	private class ServerManager extends Thread implements Closable
	{
		/**The input server socket that is used to establish input connections with new clients*/
		private final ServerSocket serverInput;
		/**The output server socket that is used to establish output connections with new clients*/
		private final ServerSocket serverOutput;
		/**The parent server manager that created this one as an attempt at self-repair*/
		private volatile ServerManager parent = null;
		
		/**Creates a new server manager on the specified ports
		 * @param inputPort The port number to establish the server's input socket on
		 * @param outputPort The port number to establish the server's output socket on
		 * @throws IOException If either of the sockets couldn't be correctly established*/
		private ServerManager(int inputPort, int outputPort) throws IOException
		{
			ServerSocket tempInput = null;
			ServerSocket tempOutput = null;
			log.log("Creating new server manager on Input:" + inputPort + " Ouput:" + outputPort);//log the creation of a new server manager
			try{//try to establish the server's sockets
				tempInput = new ServerSocket(inputPort);//create a new server input socket
				try{//try to establish the server's output socket
					tempOutput = new ServerSocket(outputPort);//create a new server output socket
					tempOutput.setSoTimeout(1000);//set the server output socket to wait 1 second for replies
				} catch(IOException ioException){
					
				}
			} catch(IOException ioException){
				
			}
			
			
			try{//try to establish the server's sockets
				tempInput = new ServerSocket(inputPort);//create a new server input socket
				tempOutput = new ServerSocket(outputPort);//create a new server output socket
				tempOutput.setSoTimeout(1000);//set the server output socket to wait 1 second for replies
			} catch(IOException ioException){//if either of the socket's couldn't be created correctly
				try{//to close the server input socket
					tempInput.close();//close the server input socket
				} catch(Exception exception){}//ignore any exceptions
				try{//to close the server input socket
					tempOutput.close();//close the server input socket
				} catch(Exception exception){}//ignore any exceptions
				throw ioException;//propagate the exception through to the caller
			}
			serverInput = tempInput;//set the server's input socket
			serverOutput = tempOutput;//set the server's output socket
		}
	}
}
