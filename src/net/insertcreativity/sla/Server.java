
package net.insertcreativity.sla;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;

import net.insertcreativity.util.LogPrinter;

public class Server implements Closeable
{
	/**Reference to the logger used by this server*/
	private final LogPrinter log;
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
		 * @throws IOException If the sockets couldn't be correctly established*/
		private ServerManager(int inputPort, int outputPort) throws IOException
		{
			ServerSocket tempInput = null;
			ServerSocket tempOutput = null;
			log.log("Creating new server manager on Input:" + inputPort + " Ouput:" + outputPort);//log the creation of a new server manager
			try{//try to establish the server's sockets
				tempInput = new ServerSocket(inputPort);//create a new server input socket
				log.log("Established the server input socket");//log the creation of the input socket
				try{//try to establish the server's output socket
					tempOutput = new ServerSocket(outputPort);//create a new server output socket
					tempOutput.setSoTimeout(1000);//set the server output socket to wait 1 second for replies
					log.log("Established the server output socket");//log the creation of the output socket
				} catch(IOException ioException){//if the server's output socket couldn't be established successfully
					try{//try to close the server's output socket
						tempOutput.close();//close the output socket
					} catch(NullPointerException nullPointerException){//if the output socket hadn't been fully initialized yet
					} catch(IOException ioException1){//if the output socket couldn't be closed
						log.log("Failed to close output socket");//log that the output socket couldn't be closed
						ioException1.printStackTrace(log);//log the exception
					}
					throw ioException;//propagate the exception
				}
			} catch(IOException ioException){//if the server's sockets couldn't be established successfully
				try{//try to close the server's input socket
					tempInput.close();//close the input socket
				} catch(NullPointerException nullPointerException){//if the input socket hadn't been fully initialized yet
				} catch(IOException ioException1){//if the input socket couldn't be closed
					log.log("Failed to close input socket");//log that the input socket couldn't be closed
					ioException1.printStackTrace(log);//log the exception
				}
				throw ioException;//propagate the exception
			}
			serverInput = tempInput;//set the server's input socket
			serverOutput = tempOutput;//set the server's output socket
			log.log("Successfully created server manager");//log the successful creation of the server manager
		}
	}
}
