
package net.insertcreativity.sla;

import java.awt.BorderLayout;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.insertcreativity.util.LogPrinter;

public class Server implements Closeable
{
	/**Map of all the clients connected to this server indexed by their names, also it's own lock*/
	private final HashMap<String, ClientManager> clientManagers;
	/**Reference to the server manager for this server*/
	private final ServerManager serverManager;
	/**Reference to this server's GUI manager*/
	private GuiManager guiManager;
	/**Reference to the logger used by this server*/
	private final LogPrinter log;
	
	/**Creates a new server on the specified ports, by the time the constructor finished all it's threads and members will
	 * be initialized and running
	 * @param logPrinter Log object that this server should log it's activity to, null if logging is disabled
	 * @param inputPort The port that this server should establish input connections through
	 * @param ouptutPort The port that this server should establish output connections through
	 * @throws IOException If the server couldn't be constructed correctly*/
	public Server(LogPrinter logPrinter, int inputPort, int outputPort) throws IOException
	{
		log = logPrinter;//set this server's log to the one provided
		log.log("Beginning server initialization...");//log that the server is starting up
		serverManager = new ServerManager(inputPort, outputPort);//create a new server manager for this server
		clientManagers = new HashMap<String, ClientManager>();//initialize the client manager map
		//TODO create the guiManger here
		serverManager.runServer();//start the server manager thread
		log.log("Server initialization completed");//log that the server has initialized completely
	}
	
	/**Creates a new server with the specified details
	 * @param logPath Path for the file this server should log it's activity to, null if logging is disabled
	 * @param inputPort The port that this server should establish input connections through
	 * @param ouptutPort The port that this server should establish output connections through
	 * @return A new server with all it's threads initialized and running already
	 * @throws IOException If the server couldn't be constructed correctly*/
	public static Server openServer(String logPath, int inputPort, int outputPort) throws IOException
	{
		LogPrinter logPrinter = new LogPrinter();//create a new log printer
		logPrinter.addOutput("logFile", new FileOutputStream(logPath, true));//add the log file as an output to the log printer
		try{//try to create and return a new server
			return new Server(logPrinter, inputPort, outputPort);//create the new server
		} catch(Exception exception){//if the server couldn't be created
			logPrinter.close();//close the log printer
			throw exception;//propagate the exception
		}
	}
	
	/**Creates a new server through the server creation dialogue where the user inputs the server details through a GUI
	 * @return A new server with all it's threads initialized and running already, or null if the user cancels the server creation*/
	public static Server openServer()
	{
		JPanel userPanel = new JPanel(new BorderLayout());//create a panel for interfacing with the user
			JPanel inputPanel = new JPanel();//create a panel for obtaining the input port number
				inputPanel.add(new JLabel("Input Port:"));//create a label asking for the input port number, and place it towards the west side of the panel
					JTextField inputPort = new JTextField(6);//create a text field for the user to enter the input port number into
				inputPanel.add(inputPort);//place the input port number text field towards the east of the panel
		userPanel.add(inputPanel, BorderLayout.WEST);//place the input port panel to the west side of the server creation panel
			JPanel outputPanel = new JPanel();//create a panel for obtaining the output port number
				outputPanel.add(new JLabel("Output Port:"));//create a label asking for the output port number, and place it towards the west side of the panel
					JTextField outputPort = new JTextField(6);//create a text field for the user to enter the output port number into
				outputPanel.add(outputPort);//place the output port number text field towards the east of the panel
		userPanel.add(outputPanel, BorderLayout.EAST);//place the output port panel to the east side of the server creation panel
			JPanel logPanel = new JPanel();//create a panel for obtaining a log file for the server
				logPanel.add(new JLabel("Log File:"));//create a label asking for the log file path, and place it towards the west side of the panel
					JTextField logFile = new JTextField(22);//create a text field for the user to enter the log file path into
				logPanel.add(logFile);//place the log file path text field towards the east of the panel
		userPanel.add(logPanel, BorderLayout.SOUTH);//place the log file panel towards the bottom of the server creation panel
		while(JOptionPane.showOptionDialog(null, userPanel, "Open a Server", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[] {"Start Server"}, null) == 0){//while the user keeps pressing the 'Start Server' button
			try{//try to create a new server with the user specified information
				return openServer(logFile.getText(), Integer.parseInt(inputPort.getText()), Integer.parseInt(outputPort.getText()));//open a new server and attempt to return it
			} catch(Exception exception){//if the server coudln't be created
				JOptionPane.showInternalMessageDialog(userPanel, exception, "Error", JOptionPane.ERROR_MESSAGE);//inform the user of the error
			}
		}
		return null;//return null if the user cancelled the server's creation
	}

	/**Closes the server, first closing it's server sockets, followed by terminating all it's client connections, before closing
	 * down the GUI and resources used by the server*/
	public void close()
	{
		log.log("Beginning server shutdown sequence");//log that the server shutdown has begun
		try{//try to close the server manager and the server sockets
			serverManager.close();//close the server manager
		} catch(Exception exception){//if closing the server manager caused an exception
			log.log("Failed to close server manager");//log that the server manager couldn't be closed without an exception
			exception.printStackTrace(log);//log the exception
		}
		synchronized(clientManagers){//lock clientManagers
			for(HashMap.Entry<String, ClientManager> client : clientManagers.entrySet()){//iterate through all the client managers
				try{//try to close this client manager
					client.getValue().close();//close the client manager
				} catch(Exception exception){//if closing the client manager caused an exception
					log.log("Failed to close client manager: " + client.getKey());//log that the client manager couldn't be closed without an exception
					exception.printStackTrace(log);//log the exception
				}
			}
		}//release clientManagers
		guiManager.close();//close the guiManager
		log.log("Server shutdown complete");//log that the server has shutdown
		log.close();//close the log
	}
	
	/**Immediately terminates the host computer using NTSystemShutdown. This method can cause system instability and should hence
	 * be used sparingly in only cases of emergency*/
	public void rapidShutdown()
	{
		//TODO
	}
	
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
	
	private class ClientManager implements Runnable, Closeable
	{
		
	}
	
	private class GuiManager implements Runnable, Closeable
	{
		
	}
}
