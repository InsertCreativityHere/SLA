
package net.insertcreativity.sla;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Locale;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxHost;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWriteMode;
import com.dropbox.core.json.JsonReader.FileLoadException;
import net.insertcreativity.util.LogPrinter;
import net.insertcreativity.util.Util;

/**Class responsible for encapsulating all the file management for this node; initially creates all the
 * necessary files for the node, and ensures that the node and it's files stay up to date with the master
 * repository and SLA database, in addition to providing a means of communication between them and the node
 * and keeping this node synced with the master repository and SLA database*/
public final class FileManager
{
	/**Reference to the logger that the file manager outputs to*/
	private final LogPrinter log;
	/**Client used to communicate with the master database*/
	private DbxClient databaseClient;
	/**Class loader to dynamically load in new task classes*/
	private URLClassLoader classLoader;
	/**The server's name in ANDAC that this will upload to*/
	private final String serverName;
	/**Reference to the root directory that the node is running from*/
	private final File rootDir;
	/**Stores the last time that the task file was updated*/
	private long lastTaskUpdate = 0;
	
	/**Constructs a new file manager
	 * @param logger Object that all the file manager's activity should be logged to
	 * @param name Unique string identifier for this node
	 * @param dir Reference to the directory that this node is running in
	 * @param authCode Authorization code for accessing the master database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws FileLoadException If the database key file is malformed or invalid*/
	private FileManager(LogPrinter logger, String name, File dir, String authCode) throws IOException, DbxException, FileLoadException
	{
		log = logger;//store a reference to the logger this file manager should use
		rootDir = dir;//store a reference to the root directory for this node
		serverName = "/ANDAC/Servers/" + name;//store what the server's name is in ANDAC
		databaseClient = establishDatabaseConnection(name, authCode);//establish a connection with the master database
		File zipExe = new File(dir, "res\\7za.exe");//store a reference to the 7zip command line executable
		if(!zipExe.canExecute()){//if the 7zip command line doesn't exist or can't be executed
			log.log("Failed to locate 7zip executable");//log that the 7zip executable is missing or unusable
			Util.delete(zipExe);//delete anything that might be at it's location
			downloadFileFromDatabase(zipExe.getAbsolutePath(), "/ANDAC/7za.exe");//download a copy of the 7zip command line executable
			log.log("Successfully installed 7zip executable");//log that 7zip was installed successfully
		}
		File nirExe = new File(dir, "res\\nircmdc.exe");//store a reference to the NirSoft command line executable
		if(!nirExe.canExecute()){//if the NirSoft command line doesn't exist or can't be executed
			log.log("Failed to locate NirSoft executable");//log that the NirSoft command line is missing or unusable
			Util.delete(nirExe);//delete anything that might be at it's location
			downloadFileFromDatabase(nirExe.getAbsolutePath(), "/ANDAC/nircmdc.exe");//download a copy of the NirSoft command line executable
			log.log("Successfully installed NirSoft executable");//log that the NirSoft command line was installed successfully
		}
		if(!(new File(dir, "res\\Git\\git-bash.exe")).canExecute()){//if the Git bash doesn't exist or can't be executed
			log.log("Failed to locate a Git installation");//log that a Git installation is missing or unusable
			installGit(dir);//install Git from scratch
			log.log("Successfully installed Git");//log that Git was installed successfully
		}
		classLoader = new URLClassLoader(new URL[] {new File(rootDir, "res\\taskClasses").toURI().toURL()});//create the class loader for this node
		log.log("Successfully initialized the task class loader");//log that the class loader for the task classes was created successfully
		File taskClassesDir = new File(dir, "res\\taskClasses");//store a reference to the task classes directory
		if(!taskClassesDir.isDirectory()){//if there is no valid task class directory
			log.log("Failed to locate task class directory");//log that the task class directory couldn't be located
			Util.delete(taskClassesDir);//delete anything that might be at it's location
			if(!taskClassesDir.mkdirs()){//if the task classes directory couldn't be created
				throw new IOException("Failed to create the task classes directory");//except that the task class directory couldn'y be made
			}
			log.log("Successfully created task class directory");//log the successful creation of a new task class directory
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
	public static FileManager createServer(LogPrinter logger, String serverName, File serverDir, String authCode) throws IOException, DbxException, FileLoadException
	{
		logger.log("Initializing new server file manager...");//log the initialization of a new server file manager
		FileManager serverFileManager = new FileManager(logger, serverName, serverDir, authCode);//create a new file manager for the server
		serverName = serverFileManager.serverName;//store what the server's name is in ANDAC
		if(serverFileManager.databaseClient.getMetadata(serverName) == null){//if this server doesn't have a folder in ANDAC
			serverFileManager.databaseClient.createFolder(serverName);//create a folder for this server in ANDAC
			logger.log("Successfully created root directory for " + serverName);//log the successful creation of a new root directory
		}
		if(serverFileManager.databaseClient.getMetadata(serverName + "/results") == null){//if this server doesn't have a results folder in ANDAC
			serverFileManager.databaseClient.createFolder(serverName + "/results");//create a results folder for this server in ANDAC
			logger.log("Successfully created a new results directory");//log the successful creation of a new results directory
		}
		if(serverFileManager.databaseClient.getMetadata(serverName + "/tasks.txt") == null){//if this server has no task file in ANDAC
			logger.log("Failed to locate tasks.txt");//log that the task file couldn't be found
			File taskFile = new File(serverDir, "tasks.txt");//store a reference to the server's task file
			taskFile.createNewFile();//create the task file locally
			serverFileManager.uploadFileToDatabase(taskFile.getAbsolutePath(), serverName + "/tasks.txt");//upload the task file to ANDAC
			taskFile.delete();//delete the local copy of the task file
			logger.log("Successfully created tasks.txt");//log that the task file was created successfully
		}
		File logFile = new File(serverDir, "log.txt");//store a reference to the server's log file
		if(serverFileManager.databaseClient.getMetadata(serverName + "/log.txt") != null){//if this server already has a log file in ANDAC
			serverFileManager.downloadFileFromDatabase(logFile.getAbsolutePath(), serverName + "/log.txt");//download the log file
			logger.log("Successfully loaded log.txt");//log that the log file was downloaded successfully
		} else{//if there is no log file for this server in ANDAC
			logger.log("Failed to locate log.txt");//log that the log file couldn't be found
			logFile.createNewFile();//create a new log file for the server
			serverFileManager.uploadFileToDatabase(logFile.getAbsolutePath(), serverName + "/log.txt");//upload the log file to ANDAC
			logger.log("Successfully created log.txt");//log the successful creation of a new log file
		}
		File statusFile = new File(serverDir, "status.txt");//store a reference to the server's status file
		if(serverFileManager.databaseClient.getMetadata(serverName + "/status.txt") != null){//if this server already has a status file in ANDAC
			serverFileManager.downloadFileFromDatabase(statusFile.getAbsolutePath(), serverName + "/status.txt");//download the status file
			logger.log("Successfully loaded status.txt");//log that the status file was downloaded successfully
		} else{//if there is no status file for this server in ANDAC
			logger.log("Failed to locate status.txt...");//log that the status file couldn't be found
			statusFile.createNewFile();//create a new status file for the server
			serverFileManager.uploadFileToDatabase(statusFile.getAbsolutePath(), serverName + "/status.txt");//upload the status file to ANDAC
			logger.log("Successfully created status.txt");//log the successful creation of a new status file
		}
		File lockFile = new File(serverDir, "L.lock");//store a reference to the lock file for this server
		if(!lockFile.canRead()){//if the lock file is unreadable
			logger.log("Failed to locate L.lock");//log that the lock file couldn't be found
			if(lockFile.createNewFile()){//if the lock file was successfully created
				logger.log("Successfully created L.lock");//log that the lock file was created successfully
			} else{//if the lock file couldn't be successfully created
				throw new IOException("Failed to create lock file: L.lock");//except that the lock file couldn't be created
			}
		}
		logger.log("Successfully created new server file manager on " + serverDir.getAbsolutePath());//log the successful creation of a new server file manager
		return serverFileManager;//return the server's file manager
	}
	
	/**Creates a new file manager for a client by setting up a local file environment, establishing a
	 * connection to the master repository and the master database
	 * @param logger Object that all the file manager's activity should be logged to
	 * @param serverName Unique string identifier for this client's server
	 * @param clientDir Reference to the directory that the client is running in
	 * @param authCode Authorization code for accessing the master database
	 * @throws IOException If there's an issue creating the local file environment
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws FileLoadException If the database key file is malformed or invalid*/
	public static FileManager createClient(LogPrinter logger, String serverName, File clientDir, String authCode) throws IOException, DbxException, FileLoadException
	{
		logger.log("Initializing new client file manager");//log the initialization of a new client file manager
		FileManager clientFileManager = new FileManager(logger, serverName, clientDir, authCode);//create a new file manager for the client
		if(clientFileManager.databaseClient.getMetadata(serverName) == null){//if this client's server doesn't have a folder in ANDAC
			throw new DbxException("This client's server is unregistered in ANDAC");//except that this client's server isn't valid
		}
		logger.log("Successfully created new client file manager on " + clientDir.getAbsolutePath());//log the successful creation of a new client file manager
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
		log.log("Establishinc connection with master database");//log that the connection is being established
		DbxClient dbxClient = new DbxClient(new DbxRequestConfig(name, Locale.getDefault().toString()), authCode, DbxHost.Default);//create a client to communicate with the master database
		log.log("Connection successfully established with master database");//log that the connection was created successfully
		return dbxClient;//return the client
	}
	
	/**Installs Git into the node's res directory, removing any previous installations
	 * @param dir Reference to the directory this node is running in
	 * @throws IOException If there was an issue deleting the previous Git installation or creating this one
	 * @throws DbxException If there was an issue downloading Git from the database*/
	private void installGit(File dir) throws IOException, DbxException
	{
		File gitDir = new File(dir, "res\\Git");//create a reference to the Git directory
		if(gitDir.exists() && !Util.delete(gitDir)){//if the Git directory already exists
			if(Util.delete(gitDir)){//if the Git directory could be deleted
				log.log("Removed previous Git installation");//log that the previous Git directory was deleted
			} else{//if the Git directory couldn't be deleted
				throw new IOException("Failed to delete previous Git installation");//except that the previous installation couldn't be deleted
			}
		}
		File zipFile = new File(gitDir, "Git.zip");//store a reference to the zip file containing Git
		log.log("Downloading '/ANDAC/Git.zip'...");//log that the Git download has begun
		downloadFileFromDatabase(zipFile.getAbsolutePath(), "/ANDAC/Git.zip");//download Git from ANDAC
		log.log("Successfully downloaded '/ANDAC/Git.zip");//log that Git has been downloaded
		ProcessBuilder unzip = new ProcessBuilder(dir.getAbsolutePath() + "\\res\\7za.exe", "x", "Git.zip", "-y");//create a process for unzipping Git
		log.log("Starting Git instllation...");//log that the Git installation process is beginning
		Process process = unzip.directory(gitDir).start();//start the process
		InputStream inputstream = process.getInputStream();//retrieve the processes input stream
		int next = inputstream.read();//store the bytes being read off the process input stream
		while(next != -1){//so long as there's more bytes to read off
			log.write(inputstream.read());//read the next byte and write it to the log
		}
		try{//try to wait until the process is completely finished
			process.waitFor();//set the process directory to be the Git directory, then starts the process and waits until it's done
		} catch(InterruptedException interruptedException){//if the process is interrupted
			throw new IOException("Unzipping was interrupted");//except that the unzipping was interrupted
		}
		log.log("Deleting temporary installation files...");//log the deletion of the leftover zip file
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
		log.log("Downloading class: " + className);//log that the class's download has begun
		try{//try to load in the requested class file
			if(databaseClient.getMetadata(serverName + "/taskClasses/" + className) != null){//if the task class is in the database
				downloadFileFromDatabase(taskClassFile.getAbsolutePath(), serverName + "/taskClasses/" + className);//download the class file from the database
				log.log("Loading in class file to JVM...");//log that the class is being loading
				classLoader.loadClass(className);//load the class into the node
			} else{//if the task class isn't listed in the database
				throw new IOException("Invalid task class requested");//except that the task class wasn't valid
			}
		} catch(DbxException dbxException){//if there's an issue downloading the class file
			throw new IOException("Failed to download class", dbxException);//except that the class couldn't be downloaded
		} catch(ClassNotFoundException classNotFoundException){//if the class couldn't be found
			throw new IOException("Failed to load class", classNotFoundException);//except that the class couldn't be loaded
		}
		log.log("Successfully loaded class");//log that the class was successfully loaded
	}
	
	/**Uploads a result file from the server into the ANDAC database, deleting the file after it's been uploaded successfully
	 * @param result File reference to the result file to be uploaded into the database
	 * @throws IOException If the upload fails*/
	public void uploadResult(File result) throws IOException
	{
		log.log("Uploading result: " + result.getName());//log that the upload has begun
		try{//try to upload the result into the master database
			uploadFileToDatabase(result.getAbsolutePath(), serverName + "/results/" + result.getName());//upload the result file into the ANDAC database
		} catch(DbxException dbxException){//if the result couldn't be uploaded into the database
			throw new IOException("Failed to upload result", dbxException);//except that the upload failed
		}
		log.log("Deleting local result files...");//log that the result's local files are being deleted
		result.delete();//delete the result file from the local environment
		log.log("Upload Complete");//log that the upload completed successfully
	}
	
	/**Checks this server's task list in ANDAC, downloading a copy of all the tasks left, before clearing the list
	 * @return A list containing all of the tasks left for the server in the ANDAC*/
	public ArrayList<String> loadTasks()
	{
		ArrayList<String> tasks = new ArrayList<String>();//create a list for holding the tasks in
		try{//try to load the list of tasks left for the server to complete
			long lastModified = databaseClient.getMetadata(serverName + "").asFile().lastModified.getTime();//get when the tasks file was last modified
			if(lastModified != lastTaskUpdate){//if the task file has been updated since the last check
				//TODO sync up the tasks list here
			}
		} catch(DbxException dbxException){//if the tasks file couldn't be loaded successfully 
			dbxException.printStackTrace();//print the exception
		}
		return tasks;//return the list of tasks
	}
}
