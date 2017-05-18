
package net.insertcreativity.andac;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxHost;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWriteMode;
import net.insertcreativity.util.LogPrinter;
import net.insertcreativity.util.Util;

/**Class responsible for encapsulating all the input/output and file management needs of a server or client.
 * It creates and maintains all the files necessary for it's operation and provides a means of communication
 * to the master repository and database, which allows outside control and management of the server or client,
 * in addition to facilitating communication between servers and clients in a cloaked manner*/
public class IOManager
{
	/**Client used to communicate with the master database*/
	private final DbxClient database;
	/**Class loader for dynamically loading in task classes*/
	private final URLClassLoader classLoader;
	/**Reference to the log printer that the io manager should log it's activity to*/
	private final LogPrinter log;
	/**Reference to the base directory that the io manager manages*/
	private final File baseDirectory;
	/**The ANDAC name of this server or client (clients are prefixed by '[server]/')*/
	private final String remoteName;
	/**The ANDAC name of the server to reference in ANDAC (same as remoteName for servers)*/
	private final String serverName;
	/**Flag for whether or not the host computer's networks are currently enabled*/
	private boolean networkEnabled = true;

	/**Constructs a new io manager, which constructs and downloads all the necessary files an programs for the
	 * client or server to function properly, in addition to establishing external connections to the master
	 * database and repository.
	 * @param logPrinter The log that this io manager should log all it's activity to
	 * @param directory The base directory that the io manager should construct it's files in
	 * @param name The ANDAC name this io manager should reference (clients must be prefixed by '[server]/')
	 * @param name The name of the server that this io manager should reference in ANDAC (null if this is a client)
	 * @param authCode The authorization code for accessing the master database
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws IOException if there's an issue constructing the local file environment*/
	private IOManager(LogPrinter logPrinter, File directory, String name, String authCode) throws DbxException, IOException
	{
		log = logPrinter;//set this io manager's log
		baseDirectory = directory;//set the base directory that this io manager should manage
		int slashIndex = name.indexOf('/');//store the index of a slash in the name
		database = establishDatabaseConnection(name, authCode);//establish a connection to the master database
		if(slashIndex != -1){//if the name contains a slash in it (this is a client)
			remoteName = "/ANDAC/" + name.substring(0, slashIndex) + "/Clients/" + name.substring(slashIndex + 1);//set this client's full ANDAC name
			serverName = remoteName.substring(0, slashIndex + 7);//set the full ANDAC name for this client's server
			if(database.getMetadata(serverName) == null){//if this client's server isn't registered in ANDAC
				throw new IOException("The client's server is unregistered in ANDAC");//except that this client's server is unregistered
			}
		} else{//if the name has no slashes in it (this is a server)
			remoteName = "/ANDAC/" + name;//set this server's full ANDAC name
			serverName = remoteName;//set this server's name to it's remote name
		}
		File bin = new File(baseDirectory, "bin");//create a reference to the bin folder
		if(bin.mkdirs()){//if the bin directory was created
			log.log("Created the bin directory");//log that the bin directory was created
		}
		File zipExe = new File(bin, "7za.exe");//create a reference to the 7zip executable file
		if(!zipExe.canExecute()){//if the 7zip executable cannot be located or run
			log.log("7zip executable is missing or inaccessable");//log that the 7zip executable couldn't be executed
			downloadFile(zipExe.getAbsolutePath(), "/ANDAC/7za.exe");//download a copy of the 7zip executable
			log.log("Successfully downloaded 7zip executable");//log that the 7zip executable was successfully downloaded
		}
		File nirExe = new File(bin, "nircmdc.exe");//create a reference to the NirSoft command line
		if(!nirExe.canExecute()){//if the NirSoft command line cannot be located or run
			log.log("NirSoft command line is missing or inaccessable");//log that that the NirSoft command line couldn't be executed
			downloadFile(nirExe.getAbsolutePath(), "/ANDAC/nircmdc.exe");//download a copy of the NirSoft command line
			log.log("Successfully downloaded NirSoft command line");//log that the NirSoft command line was successfully downloaded
		}
		File gitDir = new File(bin, "Git");//create a reference to the Git directory
		if(!(new File(gitDir, "git-bash.exe").canExecute())){//if the Git bash cannot be located or run
			log.log("Git directory is missing or damaged");//log that the Git bash couldn't be executed
			downloadCompressed(gitDir.getAbsolutePath(), "/ANDAC/Git.zip");//download and decompress a copy of Git
		}
		File taskDir = new File(bin, "tasks");//create a reference to the task class directory
		if(taskDir.mkdir()){//if the task directory was created
			log.log("Created the task directory");//log that the task directory was created
		}
		classLoader = new URLClassLoader(new URL[] {taskDir.toURI().toURL()});//create the task class loader
		log.log("Successfully created the task class loader at " + taskDir.getAbsolutePath());//log that the task class loader was created successfully
	}

	/**Creates a new io manager to set up and manage the files necessary for the server to function, both locally
	 * and in the server's ANDAC entry, as well as establishing a connection to the master database and repository
	 * for transmitting results and data remotely
	 * @param logPrinter The log that the io manager should log all it's activity to
	 * @param clientDirectory File object for the directory that the client is running from
	 * @param name The name of the server that this io manager should reference in ANDAC
	 * @param authCode The authorization code for accessing the master database
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws IOException if there's an issue constructing the local file environment*/
	static IOManager createServerIOManager(LogPrinter logPrinter, File serverDirectory, String serverName, String authCode) throws IOException, DbxException
	{
		logPrinter.log("Initializing server IO manager...");//log that a new server io manager is being initialized
		IOManager ioManager = new IOManager(logPrinter, serverDirectory, serverName, authCode);//create a new io manager for the server
		if(ioManager.database.createFolder(ioManager.remoteName) != null){//if this server's folder in ANDAC was just created
			logPrinter.log("Added server to ANDAC database");//log that the server has been added into ANDAC
		}
		if(ioManager.database.createFolder(ioManager.remoteName + "/Results") != null){//if the server's results folder was just created
			logPrinter.log("Created results folder for server in ANDAC");//log that the server's results folder was just created
		}
		if(ioManager.database.createFolder(ioManager.remoteName + "/Clients") != null){//if the server's clients folder was just created
			logPrinter.log("Created results folder for server in ANDAC");//log that the server's clients folder was just created
		}
		File logFile = new File(serverDirectory, "log.dat");//create a reference to the server's log file
		if(ioManager.database.getMetadata(ioManager.remoteName + "/log.dat") == null){//if this server doesn't have a log file in ANDAC
			logFile.createNewFile();//create a new log file for the server
			ioManager.uploadFile(ioManager.remoteName + "/log.dat", logFile.getAbsolutePath());//upload the new log file to ANDAC
			logPrinter.log("Successfully created new log file for " + serverName);//log that the server's log file was created
		} else{//if this server has a log file in ANDAC
			ioManager.downloadFile(ioManager.remoteName + "/log.dat", logFile.getAbsolutePath());//download the server's log file
			logPrinter.log("Retrieved server log file from ANDAC");//log that the server's log file has been successfully downloaded
		}
		if(ioManager.database.getMetadata(ioManager.remoteName + "/status.dat") == null){//if this server doesn't have a status file in ANDAC
			ioManager.uploadData(ioManager.remoteName + "/status.dat", new byte[] {});//upload an empty status file to ANDAC
			logPrinter.log("Successfully created new status file for " + serverName);//log that the server's status file was created
		}
		logPrinter.log("Successfully created new server IO manager at: " + serverDirectory.getAbsolutePath());//log that the server io manager was created successfully
		return ioManager;//return the new io manager created for the server
	}

	/**Creates a new io manager to set up and manage the files necessary for the client to function, as well
	 * as establishing a connection to the master database and repository for downloading data and updating itself
	 * @param logPrinter The log that the io manager should log all it's activity to
	 * @param clientDirectory File object for the directory that the client is running from
	 * @param name The ANDAC name this io manager should reference (must be prefixed by '[server]/')
	 * @param authCode The authorization code for accessing the master database
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws IOException if there's an issue constructing the local file environment*/
	static IOManager createClientIOManager(LogPrinter logPrinter, File clientDirectory, String clientName, String authCode) throws IOException, DbxException
	{
		logPrinter.log("Initializing client IO manager...");//log that a new client io manager is being initialized
		IOManager ioManager = new IOManager(logPrinter, clientDirectory, null, authCode);//create a new io manager for the client
		logPrinter.log("Successfully created new client IO manager at: " + clientDirectory.getAbsolutePath());//log that the client io manager was created successfully
		return ioManager;//return the new io manager created for the client
	}

	/**Creates an entry in ANDAC for this client, and additional files in it's local architecture so it can
	 * communicate in a cloaked fashion
	 * @throws IOException If there's an issues creating the local files
	 * @throws DbxException If there's an issue creating the remote files in ANDAC*/
	void setClientCloaked() throws IOException, DbxException
	{
		log.log("Cloaking this client connection");//log that this client is having it's connections cloaked
		File logFile = new File(baseDirectory, "log.dat");//create a reference to the client's log file
		logFile.createNewFile();//create a new log file for the client
		uploadFile(remoteName + "/log.dat", logFile.getAbsolutePath());//upload the new log file to ANDAC
		log.log("Successfully created new log file for " + serverName);//log that the client's log file was created
		uploadData(remoteName + "/status.dat", new byte[] {});//upload an empty status file to ANDAC
		log.log("Successfully created new status file for " + serverName);//log that the client's status file was created
		log.log("Cloaking complete");//log that the client was cloaked successfully
	}

	/**Removes this client's entry in ANDAC, and it's local files as well, storing all their relevant content in a
	 * string array, as these are unnecessary for an uncloaked client
	 * @return An array containing this client's status string ([0]), leftover log data ([1]), and tasks strings in that order ([2~])
	 * @throws IOException If there's an issue removing the local files
	 * @throws DbxException If there's an issue removing the remote ANDAC files*/
	String[] setClientUncloaked() throws IOException, DbxException
	{
		log.log("Uncloaking this client connection");//log that this client is having it's connections uncloaked
		String[] tasks = fetchTasks();//fetch all the leftover tasks for this client
		String[] remoteData = new String[tasks.length + 2];//create an array for holding all the client's remote data in
		System.arraycopy(tasks, 0, remoteData, 2, tasks.length);//copy the leftover tasks into the remote data array
		if(database.getMetadata(remoteName + "/status.dat") != null){//if this client has a status file in ANDAC
			remoteData[0] = new String(downloadData(remoteName + "/status.dat"), log.encoding);//download the client's status file into the remote data array
			log.log("Successfully retrieved remote status data");//log that the client's status data was downloaded successfully
		}
		updateANDAC("");//flush the leftover's of this client's log into ANDAC
		Util.delete(new File(baseDirectory, "log.dat"));//delete the client's log file
		if(database.getMetadata(remoteName + "/log.dat") != null){//if this client has a log file in ANDAC
			remoteData[1] = new String(downloadData(remoteName + "/log.dat"), log.encoding);//download the client's log into the remote data array
			log.log("Successfully retrieved remote log data");//log that the client's log data was downloaded successfully
		}
		database.delete(remoteName);//delete this client's ANDAC entry
		log.log("Uncloaking complete");//log that the client was uncloaked successfully
		return remoteData;//return all this client's remoteData
	}

	/**Establishes a connection to the master database, returning a client that can be used to access it directly
	 * @param name Unique string identifier for this node
	 * @param authCode Code passed to the API to obtain authorized access to the master database
	 * @return A client object that can be used to make API calls to the master database
	 * @throws DbxException If an error occurs in establishing the connection with the master database*/
	private DbxClient establishDatabaseConnection(String name, String authCode) throws DbxException
	{
		log.log("Establishing connection with master database");//log that the connection is being established
		DbxClient dbxClient = new DbxClient(new DbxRequestConfig(name, Locale.getDefault().toString()), authCode, DbxHost.Default);//create a client to communicate with the master database
		log.log("Connection successfully established with master database");//log that the connection was created successfully
		return dbxClient;//return the client
	}

	/**Uploads a file to the master database
	 * @param remotePath The path to upload this file to in the master database
	 * @param localPath The path for the file to be uploaded
	 * @return Meta-data about the upload as a DbxEntry
	 * @throws FileNotFoundException If the provided file couldn't be accessed
	 * @throws IOException If the file couldn't be read from properly
	 * @throws DbxException If the upload encounters a problem*/
	private DbxEntry uploadFile(String remotePath, String localPath) throws FileNotFoundException, IOException, DbxException
	{
		log.log("Uploading " + localPath + " -> " + remotePath);//log that a file is being uploaded
		DbxEntry dbxEntry = database.uploadFile(remotePath, DbxWriteMode.add(), -1, new FileInputStream(new File(localPath)));//make a stream for the file and upload it
		log.log("Successfully uploaded " + localPath + " -> " + remotePath);//log that the file was uploaded successfully
		return dbxEntry;//return the upload's meta-data
	}

	/**Uploads byte data to the master database stored as a file of the specified name
	 * @param remotePath The path to give this data's file in the master database
	 * @param data The data to upload, stored as an array of bytes
	 * @return Meta-data about the upload as a DbxEntry
	 * @throws IOException If the data couldn't be written properly
	 * @throws DbxException If the upload encounters a problem*/
	private DbxEntry uploadData(String remotePath, byte[] data) throws IOException, DbxException
	{
		log.log("Uploading byte to " + remotePath);//log that data is being uploaded
		DbxEntry dbxEntry = database.uploadFile(remotePath, DbxWriteMode.add(), -1, new ByteArrayInputStream(data));//wrap the data in an input stream and upload it
		log.log("Successfully uploaded bytes to " + remotePath);//log that the data was uploaded successfully
		return dbxEntry;//return the upload's meta-data
	}

	/**Updates the server's log and status files in ANDAC
	 * @param status The formatted status string of this server to be uploaded
	 * @throws IOException If the data couldn't be written properly
	 * @throws DbxException If the upload encounters a problem*/
	void updateANDAC(String status) throws IOException, DbxException
	{
		log.log("Updating ANDAC log and status files...");//log that the log and status files are being updated in ANDAC
		uploadData(remoteName + "/status.dat", status.getBytes(log.encoding));//upload the status string to this server's status file
		synchronized(log){//lock log
			uploadFile(remoteName + "/log.dat", new File(baseDirectory, "log.dat").getAbsolutePath());//upload this server's log file to it's ANDAC entry
		}//release log
		log.log("Updated ANDAC log and status files");//log that the log and status files were updated in ANDAC
	}

	/**Upload the results of task into this server's ANDAC entry under the 'Results' directory
	 * @param name The file name that this task should be uploaded with
	 * @param results The results of processing the task, stored as an array of serializable objects
	 * @throws IOException If the results couldn't be serialized properly
	 * @throws DbxException If the results couldn't be uploaded properly*/
	void uploadResult(String name, Serializable[] results) throws IOException, DbxException
	{
		log.log("Uploading results for: " + name);//log that results are being uploaded
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();//create a byte array stream for writing all the results into as bytes
		try(ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)){//create an object output stream for writing the results to
			for(Serializable result : results){//iterate through all the results
				objectOutputStream.writeObject(result);//write the result into the output stream
			}
		}
		uploadData(serverName + "/Results/" + name + ".dat", byteArrayOutputStream.toByteArray());//upload the byte array of results into a results file in ANDAC
		log.log("Succesfully uploaded results for: " + name);//log that the results were uploaded successfully
	}

	/**Uploads new tasks to a cloaked client in ANDAC
	 * (this operation is blocking, and may take time if it happens to check while the tasks file is being modified)
	 * @param destination The ANDAC name of the client to send the tasks to
	 * @param tasks A string containing all the tasks to be sent
	 * @throws IOException If the data couldn't be written properly
	 * @throws DbxException If the data couldn't be uploaded properly*/
	void uploadTasks(String destination, String tasks) throws IOException, DbxException
	{
		log.log("Uploading new tasks to " + destination);//log how many tasks are being sent and where to
		while(database.getMetadata(destination + "/client.lock") != null){//while the client has the tasks file locked
			try{//try to sleep
				Thread.sleep(500);//sleep for 0.5 seconds
			} catch(InterruptedException interruptedException){}//ignore any interruptions
		}
		try{//wrapper to ensure the lock file is deleted
			uploadData(destination + "/server.lock", new byte[] {});//lock tasks.dat
			if(database.getMetadata(destination + "/tasks.dat") != null){//if there are leftover tasks
				tasks = new String(downloadData(destination + "/tasks.dat"), log.encoding) + "#next#" + tasks;//copy the old tasks over
			}
			uploadData(destination + "/tasks.dat", tasks.getBytes(log.encoding));//upload all the tasks
		} finally{//ensure the lock file is deleted
			database.delete(destination + "/server.lock");//release tasks.dat
		}
		log.log("Successfully sent tasks to " + destination);//log that the tasks were sent successfully
	}

	/**Downloads a file from the master database
	 * @param remotePath The path of the file to download in the master database
	 * @param localPath The path to download the file to locally
	 * @return A reference to the downloaded file
	 * @throws FileNotFoundException If the specified file couldn't be located in the master database
	 * @throws IOException If the data couldn't be written properly
	 * @throws DbxException If the download encountered a problem*/
	File downloadFile(String remotePath, String localPath) throws FileNotFoundException, IOException, DbxException
	{
		log.log("Downloading " + localPath + " <- " + remotePath);//log that a file is being downloaded
		File localFile = new File(localPath);//create a reference to the download location
		localFile.getParentFile().mkdirs();//attempt to create any parent folders that this file should be inside
		Util.delete(localFile);//delete anything at the local file's location
		localFile.createNewFile();//create the local file
		try(FileOutputStream fileOutputStream = new FileOutputStream(localFile)){//create a stream for writing to the local file
			if(database.getFile(remotePath, null, fileOutputStream) == null){//if the remote file didn't exist in the master database
				throw new FileNotFoundException(remotePath + " could not be located in the master database");//except that the file couldn't be found
			}
		}
		log.log("Successfully downloaded " + localPath + " <- " + remotePath);//log that the file was downloaded successfully
		return localFile;//return the reference to the local file
	}

	/**Downloads and decompresses a zip file from the master database
	 * @param remotePath The path of the zip file to download in the master database
	 * @param localPath The path to unzip the file to locally
	 * @return A reference to the decompressed base file or folder
	 * @throws FileNotFoundException If the specified file couldn't be located in the master database
	 * @throws IOException If the data couldn't be written or unzipped properly
	 * @throws DbxException If the download encountered a problem*/
	File downloadCompressed(String remotePath, String localPath) throws FileNotFoundException, IOException, DbxException
	{
		File localFile = new File(localPath);//create a reference to the local file location
		Util.delete(localFile);//delete anything that might be in it's place
		String name = remotePath.substring(remotePath.lastIndexOf('/'));//get the name of the file for logging purposes
		File zip = new File(localFile, name);//create a reference to the zip file
		downloadFile(zip.getAbsolutePath(), remotePath);//download the zip file
		ProcessBuilder decompress = new ProcessBuilder(new File(baseDirectory, "bin\\7za.exe").getAbsolutePath(), "x", localFile.getAbsolutePath(), "-y");//create a process to decompress the file
		decompress.redirectErrorStream(true);//merge the process's error stream into it's output stream
		log.log("Decompressing " + name + "...");//log that the decompression portion is beginning
		Process process = decompress.directory(zip.getParentFile()).start();//start the process in the parent directory
		InputStream inputStream = process.getInputStream();//retrieve the process's output stream
		int next;//create a variable for storing the bytes read off the input stream
		while((next = inputStream.read()) != -1){//while the end of the stream has not been reached
			log.write(next);//write the next byte to the log
		}
		try{//try to wait until the decompression is complete
			log.log("Decompression finished: " + process.waitFor());//log the exit code from the decompression process
		} catch(InterruptedException interruptedException){}//ignore any exceptions from being interrupted
		log.log("Deleting temporary files...");//log that the temporary download files are being deleted
		Util.delete(zip);//delete the leftover zip file
		log.log("Successsfully downloaded and decompressed " + name);//log that the file was downloaded and decompressed successfully
		return localFile;//return a reference to the decompressed file
	}

	/**Downloads the byte data of a file stored in the master database
	 * @param remotePath The path of the file to download from the master database
	 * @return A byte array containing the file's data
	 * @throws FileNotFoundException If the specified file couldn't be located in the master database
	 * @throws IOException If the data couldn't be retrieved properly
	 * @throws DbxException If the download encountered a problem*/
	byte[] downloadData(String remotePath) throws FileNotFoundException, IOException, DbxException
	{
		log.log("Downloading bytes from " + remotePath);//log that data is being downloaded
		DbxEntry metadata = database.getMetadata(remotePath);//retrieve the file's meta-data
		if(metadata == null){//if the specified file doesn't exist in the master database
			throw new FileNotFoundException(remotePath + " could not be located in the master database");//except that the file couldn't be found
		}
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int)metadata.asFile().numBytes);//allocate a new byte output stream the same size as the file
		database.getFile(remotePath, null, byteArrayOutputStream);//download the file into the byte output stream
		log.log("Successfully downloaded bytes from " + remotePath);//log that the data was downloaded successfully
		return byteArrayOutputStream.toByteArray();//return the byte array generated from the byte output stream
	}

	/**Downloads a class and automatically loads it into the JVM class-path
	 * @param classPath The binary name of the class delimited by '.'as you would see in an import statement
	 * @return Class object representing the newly loaded class
	 * @throws FileNotFoundException If the class file wasn't listed in ANDAC
	 * @throws IOException If the class file couldn't be written properly
	 * @throws DbxException If the class file couldn't be downloaded properly*/
	Class<?> downloadClass(String classPath) throws FileNotFoundException, IOException, DbxException
	{
		log.log("loading class: " + classPath);//log the class that is being downloaded
		String filePath = classPath.replace('.', '/') + ".class";//get the file formatted class path
		downloadFile(baseDirectory + "\\tasks\\" + filePath.replace('/', '\\'), "/ANDAC/taskClasses/" + filePath);//download the class file
		try{//try to load in the class
			Class<?> clazz = classLoader.loadClass(classPath);//load the class's file into the JVM
			log.log("Successfully loaded in class: " + classPath);//log that the class was loaded successfully
			return clazz;//return a reference to the class's object representation
		} catch(ClassNotFoundException classNotFoundException){//if the class couldn't be loaded properly
			throw new IOException("Failed to load in class", classNotFoundException);//except that the class wasn't loaded correctly
		}
	}

	/**Checks ANDAC to see if any new tasks are queued for this server, downloading any that are
	 * (this operation is blocking, and may take time if it happens to check while the tasks file is being modified)
	 * @return An array of strings containing the tasks to be executed by the server
	 * @throws IOException If the data couldn't be retrieved properly
	 * @throws DbxException If the download encountered a problem*/
	String[] fetchTasks() throws IOException, DbxException
	{
		log.log("Fetching tasks...");//log that the tasks are being fetched
		while(database.getMetadata(remoteName + "/server.lock") != null){//while the server has the tasks file locked
			try{//try to sleep
				Thread.sleep(500);//sleep for 0.5 seconds
			} catch(InterruptedException interruptedException){}//ignore any interruptions
		}
		try{//wrapper to ensure the lock file gets deleted
			uploadData(remoteName + "/client.lock", new byte[] {});//lock tasks.dat
			while(database.getMetadata(remoteName + "/server.lock") != null){//while the server has the tasks file locked
				try{//try to sleep
					Thread.sleep(500);//sleep for 0.5 seconds
				} catch(InterruptedException interruptedException){}//ignore any interruptions
			}
			if(database.getMetadata(remoteName + "/tasks.dat") == null){//if there are no new tasks to download
				log.log("No new tasks found");//log that no new tasks were found
				return new String[] {};//return an empty array of tasks
			} else{//if there are new tasks to download
				String[] tasks = new String(downloadData(remoteName + "/tasks.dat"), log.encoding).split("\n");//download and store all the new tasks
				database.delete(remoteName + "/tasks.dat");//delete the tasks now that they've been received
				log.log(tasks.length + " new tasks downloaded");//log the number of new tasks downloaded
				return tasks;//return the new tasks
			}
		} finally{//ensure the lock file is deleted
			database.delete(remoteName + "/client.lock");//release tasks.dat
		}
	}

	/**Sets the state of the host computer's physical network adapters to either disabled or enabled
	 * @param state True if the networks should be enabled, false if they should be disabled
	 * @returns Boolean indicating whether the operation succeeded on all of them
	 * @throws IOException If an IO exception occurs during the process of setting the states*/
	boolean setNetworkState(boolean state) throws IOException
	{
		if(networkEnabled == state){//if the network is already in the specified state
			return true;//return that the state was correctly set and do nothing
		}
		String stateString = (state? "enable":"disable");//store the string version of the new network state
		log.log("Setting network state: " + stateString + "d");//log the new network state
		ProcessBuilder setNetworkState = new ProcessBuilder("wmic", "path", "win32_networkadapter", "where", "physicalAdapter=True", "call", stateString);//create a process to set the network state
		setNetworkState.redirectErrorStream(true);//merge the process's error stream with it's output stream
		Process process = setNetworkState.directory(new File(System.getenv("SystemRoot") + "\\System32")).start();//set the process from the system directory
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));//retrieve the process's output stream and wrap it in a reader
		String line;//create a variable for storing the line read by the buffered reader
		boolean success = true;//create a variable for storing whether all the processes were successful
		while((line = bufferedReader.readLine()) != null){//while the end of stream has not been reached
			if(line.length() != 0){//if the line isn't empty
				line = line.trim();//remove any unnecessary whitespace
				log.println(line);//write the line into the log
				int returnIndex = line.toLowerCase().indexOf("returnvalue");//get the index where 'ReturnValue' occurs
				if(returnIndex > -1){//if the line contains the return value code
					int equalsIndex = line.indexOf('=', returnIndex);//get the index of the next equals sign after 'ReturnValue'
					success &= line.substring(equalsIndex, line.indexOf(';', equalsIndex)).trim().equals("0");//and whether the return value was 0 with whether the process is successful
				}
			}
		}
		try{//try to wait for the process to fully complete
			process.waitFor();//block until the process completes
		} catch(InterruptedException interruptedException){}//ignore any interruptions
		if(success){//if the network state was set successfully
			log.log("Successfully set network state: " + stateString + "d");//log that the network state was set successfully
		} else{//if the network state couldn't be set
			log.log("Failed to set the network state: " + stateString + "d");//log that the network state wasn't set successfully
		}
		return success;//return whether all the processes were successful
	}
}