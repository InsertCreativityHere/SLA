
package net.insertcreativity.andac;

import java.awt.BorderLayout;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.insertcreativity.util.LogPrinter;
import net.insertcreativity.util.MinimalStack;

public class Server implements Closeable//TODO http://www.techrepublic.com/article/protect-your-network-traffic-using-javas-encryption-features/ USE ENCRYPTION
{
	/**Map of all the clients connected to this server keyed by name, also it's own lock*/
	private final HashMap<String, ClientManager> clients;
	/**Reference to the server manager used by this server*/
	private final ServerManager serverManager;
	/**Reference to this GUI manager used by this server*/
	private final GuiManager guiManager;
	/**Reference to the log this server should write all it's activity to*/
	private final LogPrinter log;
	/**Stack of all the tSasks currently assigned to this server*/
	private final MinimalStack<String> tasks;
	/**The name of this server*/
	private final String serverName;
	/**Flag for whether or not the server is currently cloaked*/
	private boolean isCloaked;

	/**Creates a new server on the specified ports, and starts all it's relevant threads
	 * @param logPrinter Log printer that this server should log it's activity to, null if logging is disabled
	 * @param name The name of this server
	 * @param inputPort The port that this server should establish input connections through
	 * @param ouptutPort The port that this server should establish output connections through
	 * @throws IOException If the server couldn't be constructed correctly*/
	public Server(LogPrinter logPrinter, String name, int inputPort, int outputPort) throws IOException
	{
		//TODO
	}

	/**Creates a new server with the specified details
	 * @param logPath Path for the file this server should log it's activity to, null if logging is disabled
	 * @param name The name of this server
	 * @param inputPort The port that this server should establish input connections through
	 * @param ouptutPort The port that this server should establish output connections through
	 * @return A new server with all it's threads initialized and running already
	 * @throws IOException If the server couldn't be constructed correctly*/
	public static Server openServer(String logPath, String name, int inputPort, int outputPort) throws IOException
	{
		LogPrinter logPrinter = new LogPrinter();//create a new log printer
		if(logPath != null){//if logging should be enabled for this server
			logPrinter.addOutput("logFile", new FileOutputStream(logPath, true));//add the log file as an output to the log printer
		}
		logPrinter.addOutput("SysOut", System.out);//add the system's output stream as an output to the log printer
		try{//try to create and return a new server
			return new Server(logPrinter, name, inputPort, outputPort);//create the new server
		} catch(Exception exception){//if the server couldn't be created
			logPrinter.close();//close the log printer
			throw exception;//propagate the exception
		}
	}

	/**Creates a new server through the server creation dialogue where the user inputs the server details through a GUI
	 * @return A new server with all it's threads initialized and running already, or null if the user cancels the server creation*/
	public static Server openServer()
	{
		JPanel serverPanel = new JPanel();//create a new panel for displaying everything in
		serverPanel.setLayout(new BoxLayout(serverPanel, BoxLayout.Y_AXIS));//arrange the panel stacked vertically
		JPanel upperPanel = new JPanel();//create a new panel for grouping all the elements on the top together
		JPanel inputPanel = new JPanel();//create a panel for getting the input port
		inputPanel.add(new JLabel("Input Port:"));//create a label prompting the user for the input port
		JTextField inputField = new JTextField(6);//create a text field for the user to enter the input port into
		inputPanel.add(inputField);//add the text field in the input panel
		upperPanel.add(inputPanel, BorderLayout.WEST);//place the input panel in the left side of the upper panel
		JPanel outputPanel = new JPanel();//create a panel for getting the output port
		outputPanel.add(new JLabel("Output Port:"));//create a label prompting the user for the output port
		JTextField outputField = new JTextField(6);//create a text field for the user to enter the output port into
		outputPanel.add(outputField);//add the text field in the output panel
		upperPanel.add(outputPanel, BorderLayout.NORTH);//place the input panel in the middle of the upper panel
		JPanel namePanel = new JPanel();//create a panel for getting the server's name
		namePanel.add(new JLabel("Server Name:"));//create a label prompting the user for the server's name
		JTextField nameField = new JTextField(20);//create a text field for the user to enter the server's name into
		namePanel.add(nameField);//add the text field in the name panel
		upperPanel.add(namePanel, BorderLayout.EAST);//place the name panel in the right side of the upper panel
		serverPanel.add(upperPanel);//place the upper panel in the top part of the server panel
		JPanel logPanel = new JPanel();//create a panel for getting the log path
		logPanel.add(new JLabel("Log File:"));//create a label prompting the user for the log path
		JTextField logField = new JTextField(50);//create a text field for the user to enter the log path into
		logPanel.add(logField);//place the text field in the log panel
		serverPanel.add(logPanel);//place the log panel in the bottom part of the server panel
		while(JOptionPane.showOptionDialog(null, serverPanel, "Open a Server", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[] {"Start Server"}, null) == 0){//while the user keeps pressing the 'Start Server' button
			try{//try to create a new server with the user specified information
				return openServer(logField.getText(), nameField.getText(), Integer.parseInt(inputField.getText()), Integer.parseInt(outputField.getText()));//open a new server and attempt to return it
			} catch(Exception exception){//if the server coudln't be created
				JOptionPane.showInternalMessageDialog(serverPanel, exception, "Error", JOptionPane.ERROR_MESSAGE);//inform the user of the error
			}
		}
		return null;//return null if the user cancelled the server's creation
	}

	/**Wrapper class for the server manager. This is necessary because the underlying server manager might construct a copy
	 * of itself as a form of self-repair, in which case any remaining references will be to the old copy. This wrapper
	 * ensures that it will always be a reference to the most recent server manager*/
	private class ServerManager implements Closeable
	{
		/**Reference to the underlying server manager implementation*/
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
			serverManagerImpl.close();//close the underlying server manager thread
		}


		/**Class for establishing connections being established with this server, first listening for requested connections,
		 * and passing them alone to their respective client managers.*/
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

			/**Runs the server manager thread, which listens for incoming connections, determines which are valid, and links them with
			 * a corresponding manager into this server*/
			public void run()
			{
				try{//wrapper for the server manager loop
					while(keepRunning){//while the server manager should continue running
						Socket inputSocket = serverInput.accept();//block until a connection is available and accept it




						try{//try to close the input socket
							inputSocket.close();//close the input socket
						} catch(IOException ioException){

						}



						try{//try to listen for a connection and accept it if valid
							Socket inputSocket = serverInput.accept();//block until a connection is available and accept it

						}





						Socket inputSocket = null;//variable for holding the inputSocket if it needs to be closed in the finally block
						Socket outputSocket = null;//variable for holding the outputSocket if it needs to be closed in the finally block
						boolean dropConnection = false;//flag for whether the connection should be dropped in the finally block
						try{//try to listen for a new client connection and attempt to accept it

						} finally{//ensure the connection gets dropped if it should be
							if(dropConnection){//if the connection should be dropped
								try{//try to close the input socket
									inputSocket.close();
								} catch(Exception exception){

								}
								try{
									outputSocket.close();
								} catch(Exception exception){

								}
							}
						}



						input






						try{//try to listen for a new client connection and attempt to accept it
							inputSocket = serverInput.accept();//block until a connection is available and accept it
							log.log("New connection [" + inputSocket.getRemoteSocketAddress().toString() + "]");//log the new connection
							synchronized(clients){//lock clients
								try{//try to read in the client's name
									inputStream = inputSocket.getInputStream();//get the input socket's input stream
									byte[] nameBytes = new byte[inputStream.read()];//allocate bytes for the client's name
									inputStream.read(nameBytes);//read in the client's name
								} catch(IOException ioException){//if the client's name couldn't be read

								}

								//TODO also make the streams into secure streams or something...
								//TODO get the 'name' here or something, also 'outputSocket'



								if(clients.containsKey(name)){//if this is an existent connection
									clients.get(name).reestablishConnection(inputSocket, outputSocket);//reestablish the client's connection
									log.log("Successfully re-established connection [" + name + ":" + inputSocket.getRemoteSocketAddress() + "]");//log the connection succeeded
								} else{//if this is a new connection
									clientManager = new ClientManager(name, inputSocket, outputSocket);//create a new client manager
									log.log("Successfully established connection [" + name + ":" + inputSocket.getRemoteSocketAddress() + "]");//log the connection succeeded
									clients.put(name, clientManager);//add the new client manager to the client map
									new Thread(clientManager).start();//start the client manager's thread
									//TODO have the client manager log 'client manager made instead of being here'
								}
							}//release clients
						} catch(SocketTimeoutException socketTimeoutException){//if the client took too long (>10s) to connect
							log.log("Connection timed out [" + inputSocket.getRemoteSocketAddress().toString() + "]");//log that the connection timed out
						} catch(ConnectException connectException){//TODO make sure client manager throws this for all it's own things
							log.log("Connection failed [" + inputSocket.getRemoteSocketAddress().toString() + "]");//log that the connection failed
							connectException.printStackTrace(log);//log the exception
						} finally{//ensure all the loop's references get cleared
							clientManager = null;
							inputSocket = null;
							outputSocket = null;
							inputStream = null;
							name = null;
						}











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
							Server.this.close();//close the rest of the server down
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

	//TODO
	private class ServerManger implements Runnable, Closeable
	{
		private class ServerManagerImpl implements Closeable
		{

		}
	}

	//TODO
	private class ClientManager implements Runnable, Closeable
	{

	}

	//TODO
	private class GuiManager implements Runnable, Closeable
	{

	}
}
