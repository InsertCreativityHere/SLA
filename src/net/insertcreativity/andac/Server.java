
package net.insertcreativity.andac;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import net.insertcreativity.util.LogPrinter;

public class Server
{
	/**Map of all the clients connected to this server indexed by their names, also it's own lock*/
	private final HashMap<String, ClientManager> clientManagers;
	/**Reference to the server manager for this server*/
	private final ServerManager serverManager;
	/**Reference to this GUI manager for this server*/
	private GuiManager guiManager;
	/**Reference to the logger for this server*/
	private final LogPrinter log;
	/**The name for this server to use in ANDAC*/
	private final String serverName;
	
	/**Wrapper for the server manager's implementation; necessary because the server manager impl might construct a new copy of
	 * itself as a form of self repair, in which case any references will remain to the old broken copy. This wrapper ensures
	 * that a reference is always kept to the newest server manager implementation*/
	private class ServerManager implements Closeable
	{
		/**Reference to the underlying server manager implementation, also it's own lock*/
		private ServerManagerImpl serverManagerImpl;
		
		/**Creates a new server manager on the specified ports
		 * @param inputPort The port number to establish the server's input socket on
		 * @param outputPort The port number to establish the server's output socket on
		 * @throws IOException If the sockets couldn't be correctly established*/
		private ServerManager(int inputPort, int outputPort) throws IOException
		{
			serverManagerImpl = new ServerManagerImpl(inputPort, outputPort);//create the underlying server manager implementation
		}
		
		/**Starts the underlying server manager implementation's thread*/
		private void runServer()
		{
			serverManagerImpl.start();//start the underlying server manager thread
		}
		
		/**Closes the underlying server manager implementation
		 * @throws IOException If the underlying server manager implementation couldn't be closed*/
		public void close() throws IOException
		{
			synchronized(serverManagerImpl){//lock serverManagerImpl
				serverManagerImpl.close();//close the underlying server manager thread
			}//release serverManagerImpl
		}
		
		/**Class for encapsulating all the functionality of this server, listens for requested connections, and creates new
		 * ClientManagers for handling them. Terminates when the serverInput socket is closed, and keepRunning is false,
		 * otherwise will attempt a self-repair.*/
		private class ServerManagerImpl extends Thread implements Closeable
		{
			/**The input server socket that is used to establish input connections with new clients*/
			private final ServerSocket serverInput;
			/**The output server socket that is used to establish output connections with new clients*/
			private final ServerSocket serverOutput;
			/**The parent server manager that created this one as an attempt at self-repair*/
			private volatile ServerManagerImpl parent = null;
			/**Flag for whether or not the server manager should continue running*/
			private volatile boolean keepRunning = true;
			
			/**Creates a new server manager on the specified ports
			 * @param inputPort The port number to establish the server's input socket on
			 * @param outputPort The port number to establish the server's output socket on
			 * @throws IOException If the sockets couldn't be correctly established*/
			private ServerManagerImpl(int inputPort, int outputPort) throws IOException
			{
				ServerSocket tempInput = null;
				ServerSocket tempOutput = null;
				log.log("Creating new server manager on Input:" + inputPort + " Ouput:" + outputPort);//log the creation of a new server manager
				try{//try to establish the server's sockets
					tempInput = new ServerSocket(inputPort);//create a new server input socket
					log.log("Established the server input socket");//log the creation of the input socket
					try{//try to establish the server's output socket
						tempOutput = new ServerSocket(outputPort);//create a new server output socket
						tempOutput.setSoTimeout(10000);//set the server output socket to wait 1 second for replies
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
			
			/**Runs the server manager thread, which listens for client connections, determines which are valid, and creates
			 * client managers for the clients who get accepted*/
			public void run()
			{
				try{//wrapper for the server manager loop
					ClientManager clientManager = null;
					Socket inputSocket = null;
					while(keepRunning){//while the server manager should continue running
						try{//try to listen for a new client connection and attempt to accept it
							inputSocket = serverInput.accept();//block until a connection is available and accept it
							log.log("New connection [" + inputSocket.getRemoteSocketAddress().toString() + "]");//log the new connection
							clientManager = new ClientManager(inputSocket, serverOutput.accept());//create a new client manager
						} catch(SocketTimeoutException socketTimeoutException){//if the client took too long (>10s) to connect
							log.log("Connection timed out [" + inputSocket.getRemoteSocketAddress().toString() + "]");//log that the connection timed out
							continue;//continue to wait for another connection
						} catch(ConnectException connectException){//TODO make sure client manager throws this for all it's own things
							log.log("Connection failed [" + inputSocket.getRemoteSocketAddress().toString() + "]");//log that the connection failed
							connectException.printStackTrace(log);//log the exception
						} finally{//ensure the input socket gets cleared
							inputSocket = null;//clear the input socket's reference
						}
						synchronized(clientManagers){//lock clientManagers
							clientManagers.put(clientManager.clientName, clientManager);//add the new client manager to the map
						}//release clientManagers
						new Thread(clientManager).start();//start the client manager's thread
						log.log("Successfully established connection [" + inputSocket.getRemoteSocketAddress() + "]");//log the connection succeeded
					}
				} catch(Exception exception){
					if(!(exception instanceof SocketException) || (keepRunning)){//if this isn't an expected socket closure exception
						log.log("Fatal exception occurred in server manager thread");//log that an unknown fatal exception occurred
						exception.printStackTrace(log);//log the exception
					}
				} finally{//attempt to repair the server manager if necessary, and close leftover sockets
					try{//try to close the server output socket
						serverOutput.close();//close the server output socket
					} catch(Exception exception){//if the server output socket couldn't be closed
						log.log("Failed to finalize server output socket");//log that the output socket couldn't be closed
						exception.printStackTrace(log);//log the exception
					}
					synchronized(this){//lock serverInput, keepRunning, parent
						if(!serverInput.isClosed()){//if the server input socket isn't already closed
							try{//try to close the server input socket
								serverInput.close();//close the server input socket
							} catch(Exception exception){//if the server input socket couldn't be closed
								log.log("Failed to finalize server input socket");//log that the input socket couldn't be closed
								exception.printStackTrace(log);//log the exception
							}
						}//release serverInput, keepRunning, parent
						if(keepRunning){//if the server manager thread should still be running
							if(parent != null){//if this server manager was a reconstruction attempt
								parent.interrupt();//interrupt the parent to inform it that the reconstruction failed
							} else{//if this server manager should attempt a reconstruction
								log.log("Unexpected termination of server manager thread... Attempting repair");//log that the repair process is starting
								try{//try to reconstruct the server manager thread to replace this one
									ServerManagerImpl child = new ServerManagerImpl(serverInput.getLocalPort(), serverOutput.getLocalPort());//create a new server manager
									child.parent = this;//set this server manager as it's parent
									serverManagerImpl = child;//update the server manager implementation reference to the child
									child.start();//start the new server manager thread
									Thread.sleep(60000);//TODO test the new child somehow here (also apparently returning from finally is bad...)
									child.parent = null;//remove the new server manager's parent so its no longer marked as a reconstruction
									return;//close this server manager since the repair was successful
								} catch(InterruptedException interruptedException){//if the reconstruction self-reported it's failure
									log.log("Server manager reconstruction failed");//log that the server manager reconstruction failed
								} catch(Exception exception){//if the reconstruction failed for an unknown reason
									log.log("Server manager reconstruction failed exceptionally");//log that the server manager reconstruction failed
									exception.printStackTrace(log);//log the exception
								}
							}
						}
						log.log("Server manager thread terminated");//log that the server manager thread has terminated
					}//release serverInput, keepRunning, parent
				}
			}
			
			/**Closes the server manager by closing it's underlying sockets
			 * @throws IOException if the server's sockets couldn't be closed*/
			public void close() throws IOException
			{
				synchronized(this){//lock serverInput, keepRunning, parent
					keepRunning = false;//set that the server manager should stop running
					if(serverInput.isClosed()){//if the server input socket is already closed
						log.log("Server input socket already closed");//log that the input socket is already closed
					} else{//if the server input socket is still open
						serverInput.close();//close the server input socket
						log.log("Closed the server input socket");//log that the server input socket was closed
					}
				}//release serverInput, keepRunning, parent
			}
		}
	}
}
