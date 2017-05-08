
package net.insertcreativity.sla;

import java.io.Closeable;
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
public final class FileManager implements Runnable, Closeable
{
	/**Reference to the logger that the file manager outputs to*/
	private final Logger log;
	/**Client used to communicate with the master database*/
	private DbxClient databaseClient;
	/**Class loader to dynamically load in new task classes*/
	private URLClassLoader classLoader;
	/**The server's name in ANDAC*/
	private final String serverName;
	/**Reference to the root directory that the node is running from*/
	private final File rootDir;
	/**Flag for whether the file manager should keep running*/
	private boolean keepRunning = false;
	
	/**Constructs a new file manager
	 * @param logger Object that all the file manager's activity should be logged to
	 * @param name Unique string identifier for this node
	 * @param dir Reference to the directory that this node is running in
	 * @param authCode Authorization code for accessing the master database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws FileLoadException If the database key file is malformed or invalid*/
	private FileManager(Logger logger, String name, File dir, String authCode) throws IOException, DbxException, FileLoadException
	{
		log = logger;//store a reference to the logger this file manager should use
		rootDir = dir;//store a reference to the root directory for this node
		serverName = "/ANDAC/Servers/" + name;//store what the server's name is in ANDAC
		databaseClient = establishDatabaseConnection(name, authCode);//establish a connection with the master database
		File zipExe = new File(dir, "res\\7za.exe");//store a reference to the 7zip command line executable
		if(!zipExe.canExecute()){//if the 7zip command line doesn't exist or can't be executed
			Util.delete(zipExe);//delete anything that might be at it's location
			downloadFileFromDatabase(zipExe.getAbsolutePath(), "/ANDAC/7za.exe");//download a copy of the 7zip command line executable
		}
		File nirExe = new File(dir, "res\\nircmdc.exe");//store a reference to the NirSoft command line executable
		if(!nirExe.canExecute()){//if the NirSoft command line doesn't exist or can't be executed
			Util.delete(nirExe);//delete anything that might be at it's location
			downloadFileFromDatabase(nirExe.getAbsolutePath(), "/ANDAC/nircmdc.exe");//download a copy of the NirSoft command line executable
		}
		if(!(new File(dir, "res\\Git\\git-bash.exe")).canExecute()){//if the Git bash doesn't exist or can't be executed
			installGit(dir);//install Git from scratch
		}
		classLoader = new URLClassLoader(new URL[] {new File(rootDir, "res\\taskClasses").toURI().toURL()});//create the class loader for this node
		File taskClassesDir = new File(dir, "res\\taskClasses");//store a reference to the task classes directory
		if(!taskClassesDir.isDirectory()){//if there is no valid task class directory
			Util.delete(taskClassesDir);//delete anything that might be at it's location
			if(!taskClassesDir.mkdirs()){//if the task classes directory couldn't be created
				throw new IOException("Failed to create the task classes directory");//except that the task class directory couldn'y be made
			}
		}
	}
	
	/**Creates a new file manager for a server by setting up the local file environment and establishing
	 * connections to the master repository and master database
	 * @param logger Object that all the file manager's activity should be logged to
	 * @param serverName Unique string identifier for this server
	 * @param serverDir Reference to the directory that the server is running in
	 * @param authCode Authorization code for accessing the master database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws FileLoadException If the database key file is malformed or invalid*/
	public static FileManager createServer(Logger logger, String serverName, File serverDir, String authCode) throws IOException, DbxException, FileLoadException
	{
		FileManager serverFileManager = new FileManager(logger, serverName, serverDir, authCode);//create a new file manager for the server
		serverName = serverFileManager.serverName;//store what the server's name is in ANDAC
		if(serverFileManager.databaseClient.getMetadata(serverName) == null){//if this server doesn't have a folder in ANDAC
			serverFileManager.databaseClient.createFolder(serverName);//create a folder for this server in ANDAC
		}
		if(serverFileManager.databaseClient.getMetadata(serverName + "/tasks") == null){//if this server doesn't have a tasks folder in ANDAC
			serverFileManager.databaseClient.createFolder(serverName + "/tasks");//create a tasks folder for this server in ANDAC
		}
		if(serverFileManager.databaseClient.getMetadata(serverName + "/results") == null){//if this server doesn't have a results folder in ANDAC
			serverFileManager.databaseClient.createFolder(serverName + "/results");//create a results folder for this server in ANDAC
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
		serverFileManager.keepRunning = true;//set that the file manager should be running
		new Thread(serverFileManager).start();//start the file manager thread for this server
		return serverFileManager;//return the server's file manager
	}
	
	/**Creates a new file manager for a client by setting up a local file environment, establishing a
	 * connection to the master repository and the master database
	 * @param logger Object that all the file manager's activity should be logged to
	 * @param clientName Unique string identifier for this client
	 * @param clientDir Reference to the directory that the client is running in
	 * @param authCode Authorization code for accessing the master database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws FileLoadException If the database key file is malformed or invalid*/
	public static FileManager createClient(Logger logger, String clientName, File clientDir, String authCode) throws IOException, DbxException, FileLoadException
	{
		FileManager clientFileManager = new FileManager(logger, clientName, clientDir, authCode);//create a new file manager for the client
		return clientFileManager;//return the client's file manager
	}
	
	/**Establishes a connection to the master database, returning a client that can be used to access it directly
	 * @param name Unique string identifier for this node
	 * @param authCode Code passed to the API to obtain authorized access to the master database
	 * @return A client object that can be used to make API calls to the master database
	 * @throws DbxException If an error occurs in establishing the connection with Dropbox
	 * @throws FileLoadException If the provided key file is malformed*/
	private DbxClient establishDatabaseConnection(String name, String authCode) throws DbxException, FileLoadException
	{
		log.log("Establishinc connection with master database", true);//log that the connection is being established
		DbxRequestConfig dbxRequestConfig = new DbxRequestConfig(name, Locale.getDefault().toString());//store the request configuration to use with the Dropbox SLA database
		log.log("Connection successfully established with master database", true);//log that the connection was created successfully
		return new DbxClient(dbxRequestConfig, authCode, DbxHost.Default);//return the Dropbox client
	}
	
	/**Installs Git into the node's res directory, removing any previous installations
	 * @param dir Reference to the directory this node is running in
	 * @throws IOException If there was an issue deleting the previous Git installation or creating this one
	 * @throws DbxException If there was an issue downloading Git from the database*/
	private void installGit(File dir) throws IOException, DbxException
	{
		File gitDir = new File(dir, "res\\Git");//create a reference to the git directory
		if(gitDir.exists() && !Util.delete(gitDir)){//if the Git directory already exists and couldn't be deleted
			throw new IOException("Failed to delete previous git installation");//except that the previous installation couldn't be deleted
		}
		File zipFile = new File(gitDir, "Git.zip");//store a reference to the zip file containing Git
		downloadFileFromDatabase(zipFile.getAbsolutePath(), "/ANDAC/Git.zip");//download Git from ANDAC
		ProcessBuilder unzip = new ProcessBuilder(dir.getAbsolutePath() + "\\res\\7za.exe", "x", "Git.zip", "-y");//create a process for unzipping Git
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
	
	/**Uploads a file to the master database
	 * @param localPath The location of the file to be uploaded
	 * @param remotePath The location that the file should be uploaded to in the master database
	 * @return Meta-data from the download process
	 * @throws IOException If there was an issue reading from the local file
	 * @throws DbxException If there was an issue uploading the file to the master database*/
	private DbxEntry uploadFileToDatabase(String localPath, String remotePath) throws IOException, DbxException
	{
		try(InputStream inputStream = new FileInputStream(new File(localPath))){//try to create an input stream for the uploaded file
			DbxEntry metadata = databaseClient.uploadFile(remotePath, DbxWriteMode.add(), -1, inputStream);//upload the file to the master database
			return metadata;//return the download's meta-data
		}
	}
	
	/**Downloads a file from the master database
	 * @param localPath The location that the file should be downloaded to
	 * @param remotePath the location of the file inside the master database
	 * @return Meta-data from the download process
	 * @throws IOException If there was an issue writing to the local file
	 * @throws DbxException If there was an issue downloading from the master database*/
	private DbxEntry downloadFileFromDatabase(String localPath, String remotePath) throws IOException, DbxException
	{
		File localFile = new File(localPath);//store the local file
		if(!localFile.getParentFile().isDirectory() && !localFile.getParentFile().mkdirs()){//if any of the parent directories don't exist and couldn't be created
			throw new IOException("Failed to create parents for " + localFile.getAbsolutePath());//except that the parents couldn't be made
		}
		try(OutputStream outputStream = new FileOutputStream(localFile)){//try to create an output stream for the download file
			DbxEntry metadata = databaseClient.getFile(remotePath, null, outputStream);//download the file from the master database
			return metadata;//return the download's meta-data
		}
	}
	
	/**Loads in a task class to the node's file environment
	 * @param className The name of the class to be loaded
	 * @throws IOException If the class couldn't be loaded*/
	public void loadClass(String className) throws IOException
	{
		className = className + (className.endsWith(".class")? "" : ".class");//ensure the class has the proper class ending
		File taskClassFile = new File(rootDir, "res\\taskClasses\\" + className);//store a reference to the desired class
		Util.delete(taskClassFile);//delete anything at the class's location
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
	
	/**Uploads a result file from the server into the ANDAC database, deleting the file after it's been uploaded successfully
	 * @param result File reference to the result file to be uploaded into the database
	 * @throws IOException If the upload fails*/
	public void uploadResult(File result) throws IOException
	{
		try{//try to upload the result into the master database
			uploadFileToDatabase(result.getAbsolutePath(), serverName + "/results/" + result.getName());//upload the result file into the ANDAC database
		} catch(DbxException dbxException){//if the result couldn't be uploaded into the database
			throw new IOException("Failed to upload result", dbxException);//except that the upload failed
		}
		result.delete();//delete the result file from the local environment
	}
	
	/**Periodically checks the ANDAC tasks folder for this server, ensuring that it stays up to date and processes them*/
	public void run()
	{//TODO write the thread already
		log.log("Starting file manager thread", true);//log that the file manager thread is being started
		long lastModified = 0;//stores the timestamp for when the tasks file was last updated
		File LOCK = new File(rootDir, "LOCKA.txt");//store a reference to the lock file for this file manager
		try{//try to ensure the LOCK file is usable
			if(!LOCK.exists() && !LOCK.createNewFile()){//if the LOCK file doesn't exist and can't be created
				
			}
		} catch(IOException ioException){//if the LOCK file couldn't be created or accessed successfully
			//TODO put more log calls everywhere on this
		}
		while(keepRunning){//while the file manager should keep running
			
		}
	}
	
	/**Closes the file manager, releasing any resources and signaling it's threads to stop*/
	public void close()
	{
		log.log("Closing file manager", true);//log that the file manager is being closed
		keepRunning = false;//set that the file manager should stop running
	}
	
	public static void main(String args[]) throws Exception
	{
		FileManager fileManager = FileManager.createServer(null, "Testing2", new File("C:\\Users\\ahenriksen2015\\downloads\\testing"), "zGw7BngehFAAAAAAAAAAEorYRDKDnxFue0w4cQkTN0EO2iorC-uDLwyQyuZV25SS");
	}
}
