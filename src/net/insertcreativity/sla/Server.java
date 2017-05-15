
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
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.insertcreativity.util.LogPrinter;

public class Server implements Closeable
{

	
	/**Creates a new server on the specified ports, by the time the constructor finished all it's threads and members will
	 * be initialized and running
	 * @param logPrinter Log object that this server should log it's activity to, null if logging is disabled
	 * @param name The name of this server
	 * @param inputPort The port that this server should establish input connections through
	 * @param ouptutPort The port that this server should establish output connections through
	 * @throws IOException If the server couldn't be constructed correctly*/
	public Server(LogPrinter logPrinter, String name, int inputPort, int outputPort) throws IOException
	{
		ID = name;//set this server's ID
		log = logPrinter;//set this server's log to the one provided
		log.log("Beginning server initialization [" + ID + "]");//log that the server is starting up
		serverManager = new ServerManager(inputPort, outputPort);//create a new server manager for this server
		clientManagers = new HashMap<String, ClientManager>();//initialize the client manager map
		//TODO create the guiManger here
		serverManager.runServer();//start the server manager thread
		log.log("Server initialization completed [" + ID + "]");//log that the server has initialized completely
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
		logPrinter.addOutput("logFile", new FileOutputStream(logPath, true));//add the log file as an output to the log printer
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
	
	/**Class that manages a single client connection, responsible for transferring data and commands between the client and server*/
	private class ClientManager implements Runnable, Closeable
	{
		private ClientManager(Socket inputSocket, Socket ouputSocket)
		{
			
		}
	}
	
	private class GuiManager implements Runnable, Closeable
	{
		
	}
}
