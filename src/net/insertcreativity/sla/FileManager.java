
package net.insertcreativity.sla;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxHost;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWriteMode;
import com.dropbox.core.json.JsonReader.FileLoadException;
import net.insertcreativity.util.Util;

/**Class responsible for encapsulating all the file management for this node; initially creates all the
 * necessary files for the node, and ensures that the node and it's files stay up to date with the master
 * repository and SLA database, in addition to providing a means of communication between them and the node
 * and keeping this node synced with the master repository and SLA database*/
public class FileManager implements Runnable
{
	/**Client used to communicate with the SLA database*/
	private DbxClient databaseClient;
	/**Class loader to dynamically load in new task classes*/
	private URLClassLoader classLoader;
	/**Reference to the node that this is managing files for*/
	private final Node master;
	/**Reference to the root directory that the node is running from*/
	private final File rootDir;
	/**The server's name in ANDAC*/
	private final String serverName;
	
	/**Constructs a new file manager
	 * @param name Unique string identifier for this node
	 * @param dir Reference to the directory that this node is running in
	 * @param authCode Authorization code for accessing the SLA database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the SLA database
	 * @throws FileLoadException If the SLA key file is malformed or invalid*/
	private FileManager(Node node, String name, File dir, String authCode) throws IOException, DbxException, FileLoadException
	{
		rootDir = dir;//store a reference to the root directory for this node
		master = node;//store a reference to the node that created this file manager
		serverName = "/ANDAC/Servers/" + name;//store the server's name in ANDAC
		databaseClient = establishDatabaseConnection(name, authCode);//establish a connection with the SLA database
		if(!(new File(dir, "bin\\Git\\git-bash.exe")).canExecute()){//if the Git bash doesn't exist or can't be executed
			installGit(dir);//install Git from scratch
		}
		classLoader = new URLClassLoader(new URL[] {new File(rootDir, "bin\\taskClasses").toURI().toURL()});//create the class loader for this node
		File taskClassesDir = new File(dir, "bin\\taskClasses");//store a reference to the task classes directory
		if(!taskClassesDir.isDirectory()){//if there is no valid task class directory
			taskClassesDir.delete();//delete anything that might be at it's location
			if(!taskClassesDir.mkdirs()){//if the task classes directory couldn't be created
				throw new IOException("Failed to create the task classes directory");//except that the task class directory couldn'y be made
			}
		}
	}
	
	/**Creates a new file manager for a server by setting up the local file environment and establishing
	 * connections to the master repository and SLA database
	 * @param server Reference to the server that this will manage files for
	 * @param serverName Unique string identifier for this server
	 * @param serverDir Reference to the directory that the server is running in
	 * @param authCode Authorization code for accessing the SLA database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the SLA database
	 * @throws FileLoadException If the SLA key file is malformed or invalid*/
	public static FileManager createServer(Server server, String serverName, File serverDir, String authCode) throws IOException, DbxException, FileLoadException
	{
		FileManager serverFileManager = new FileManager(server, serverName, serverDir, authCode);//create a new file manager for the server
		serverName = serverFileManager.serverName;//update the server's name to what it is in ANDAC
		if(serverFileManager.databaseClient.getMetadata(serverName) == null){//if this server doesn't have a folder in ANDAC
			serverFileManager.databaseClient.createFolder(serverName);//create a folder for this server in ANDAC
		}
		if(serverFileManager.databaseClient.getMetadata(serverName + "/tasks") == null){//if this server doesn't have a task folder in ANDAC
			serverFileManager.databaseClient.createFolder(serverName + "/tasks");//create a task folder for this server in ANDAC
		}
		File logFile = new File(serverDir, "log.txt");//store a reference to the server's log file
		if(serverFileManager.databaseClient.getMetadata(serverName + "/log.txt") != null){//if this server already has a log file in ANDAC
			serverFileManager.downloadFileFromDatabase(logFile.getAbsolutePath(), serverName + "/log.txt");//download the log file
		} else{//if there is no log file for this server in ANDAC
			logFile.createNewFile();//create a new log file for the server
			serverFileManager.uploadFileToDatabase(logFile.getAbsolutePath(), serverName + "/log.txt");//upload the log file to ANDAC
		}
		File statusFile = new File(serverDir, "status.txt");//store a reference to the server's status file
		if(serverFileManager.databaseClient.getMetadata(serverName + "/status.txt") != null){//if this server already has a status file in ANDAC
			serverFileManager.downloadFileFromDatabase(statusFile.getAbsolutePath(), serverName + "/status.txt");//download the status file
		} else{//if there is no status file for this server in ANDAC
			statusFile.createNewFile();//create a new status file for the server
			serverFileManager.uploadFileToDatabase(statusFile.getAbsolutePath(), serverName + "/status.txt");//upload the status file to ANDAC
		}
		return serverFileManager;//return the server's file manager
	}
	
	/**Creates a new file manager for a client by setting up a local file environment, establishing a
	 * connection to the master repository and the SLA database
	 * @param client Reference to the client that this will manage files for
	 * @param clientName Unique string identifier for this client
	 * @param clientDir Reference to the directory that the client is running in
	 * @param authCode Authorization code for accessing the SLA database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the SLA database
	 * @throws FileLoadException If the SLA key file is malformed or invalid*/
	public static FileManager createClient(Client client, String clientName, File clientDir, String authCode) throws IOException, DbxException, FileLoadException
	{
		FileManager clientFileManager = new FileManager(client, clientName, clientDir, authCode);//create a new file manager for the client
		return clientFileManager;//return the client's file manager
	}
	
	/**Establishes a connection to the SLA database, returning a client that can be used to access it directly
	 * @param name Unique string identifier for this node
	 * @param authCode Code passed to the API to obtain authorized access to the SLA database
	 * @return A client object that can be used to make API calls to the SLA database
	 * @throws DbxException If an error occurs in establishing the connection with Dropbox
	 * @throws FileLoadException If the provided key file is malformed*/
	private static DbxClient establishDatabaseConnection(String name, String authCode) throws DbxException, FileLoadException
	{
		DbxRequestConfig dbxRequestConfig = new DbxRequestConfig(name, Locale.getDefault().toString());//store the request configuration to use with the Dropbox SLA database
		return new DbxClient(dbxRequestConfig, authCode, DbxHost.Default);//return the Dropbox client
	}
	
	/**Installs Git into the node's bin directory, removing any previous installations
	 * @param dir Reference to the directory this node is running in
	 * @throws IOException If there was an issue deleting the previous Git installation or creating this one
	 * @throws DbxException If there was an issue downloading Git from the database*/
	private void installGit(File dir) throws IOException, DbxException
	{
		File gitDir = new File(dir, "bin\\Git");//create a reference to the git directory
		if(gitDir.exists() && !Util.delete(gitDir)){//if the Git directory already exists and couldn't be deleted
			throw new IOException("Failed to delete previous git installation");//except that the previous installation couldn't be deleted
		}
		File zipFile = new File(gitDir, "Git.zip");//store a reference to the zip file containing Git
		downloadFileFromDatabase(zipFile.getAbsolutePath(), "/SLA/Git.zip");//download Git from the database
		ProcessBuilder unzip = new ProcessBuilder(dir.getAbsolutePath() + "\\bin\\7za920\\7za.exe", "x", "Git.zip", "-y");//create a process for unzipping Git
		Process process = unzip.directory(gitDir).start();//start the process
		InputStream inputstream = process.getInputStream();//retrieve the processes input stream
		int next = inputstream.read();//store the bytes being read off the process input stream
		while(next != -1){//so long as there's more bytes to read off
			next = inputstream.read();//read the next byte
		}
		try{//try to wait until the process is completely finished
			process.waitFor();//set the process directory to be the Git directory, then starts the process and waits until it's done
		} catch(InterruptedException interruptedException){//if the process is interrupted
			throw new IOException("Unzipping was interrupted");//except that the unzipping was interrupted
		}
		zipFile.delete();//attempt to delete the leftover zip file
	}
	
	/**Uploads a file to the SLA database
	 * @param localPath The location of the file to be uploaded
	 * @param remotePath The location that the file should be uploaded to in the SLA database
	 * @return Meta-data from the download process
	 * @throws IOException If there was an issue reading from the local file
	 * @throws DbxException If there was an issue uploading the file to the SLA database*/
	private DbxEntry uploadFileToDatabase(String localPath, String remotePath) throws IOException, DbxException
	{
		try(InputStream inputStream = new FileInputStream(new File(localPath))){//try to create an input stream for the uploaded file
			DbxEntry metadata = databaseClient.uploadFile(remotePath, DbxWriteMode.add(), -1, inputStream);//upload the file to the SLA database
			return metadata;//return the download's meta-data
		}
	}
	
	/**Downloads a file from the SLA database
	 * @param localPath The location that the file should be downloaded to
	 * @param remotePath the location of the file inside the SLA database
	 * @return Meta-data from the download process
	 * @throws IOException If there was an issue writing to the local file
	 * @throws DbxException If there was an issue downloading from the SLA database*/
	private DbxEntry downloadFileFromDatabase(String localPath, String remotePath) throws IOException, DbxException
	{
		File localFile = new File(localPath);//store the local file
		if(!localFile.getParentFile().isDirectory() && !localFile.getParentFile().mkdirs()){//if any of the parent directories don't exist and couldn't be created
			throw new IOException("Failed to create parents for " + localFile.getAbsolutePath());//except that the parents couldn't be made
		}
		try(OutputStream outputStream = new FileOutputStream(localFile)){//try to create an output stream for the download file
			DbxEntry metadata = databaseClient.getFile(remotePath, null, outputStream);//download the file from the database
			return metadata;//return the download's meta-data
		}
	}
	
	/**Loads in a task class to the node's file environment
	 * @param className The name of the class to be loaded
	 * @throws IOException If the class couldn't be loaded*/
	protected void loadClass(String className) throws IOException
	{
		className = className + (className.endsWith(".class")? "" : ".class");//ensure the class has the proper class ending
		File taskClassFile = new File(rootDir, "bin\\taskClasses\\" + className);//store a reference to the desired class
		taskClassFile.delete();//delete anything at the class's location
		try{//try to load in the requested class file
			if(databaseClient.getMetadata(serverName + "/taskClasses/" + className) != null){//if the task class is in the database
				downloadFileFromDatabase(taskClassFile.getAbsolutePath(), serverName + "/taskClasses/" + className);//download the class file from the database
				classLoader.loadClass(className);//load the class into the node
			} else{//if the task class isn't listed in the database
				throw new IOException("Invalid task class requested");//except that the task class wasn't valid
			}
		} catch(DbxException dbxException){//if there's an issue downloading the class file
			throw new IOException("Failed to download class", dbxException);//except that the class couldn't be downloaded
		} catch(ClassNotFoundException classNotFoundException){//if the class couldn't be found
			throw new IOException("Failed to load class", classNotFoundException);//except that the class couldn't be loaded
		}
	}
	
	public void run()
	{
		
	}
	
	public static void main(String args[]) throws Exception
	{
		FileManager fileManager = new FileManager("Testing", new File("C:\\Users\\ahenriksen2015\\Desktop\\testing\\"), "zGw7BngehFAAAAAAAAAAEorYRDKDnxFue0w4cQkTN0EO2iorC-uDLwyQyuZV25SS");
	}
}
