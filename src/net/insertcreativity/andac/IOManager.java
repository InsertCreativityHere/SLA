
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
import java.util.ArrayList;
import java.util.Locale;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxHost;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWriteMode;
import net.insertcreativity.util.LogPrinter;
import net.insertcreativity.util.Util;

/**Class responsible for encapsulating all the input/output and file management need for a server of client.
 * It creates and updates all the files necessary for it's operation and provides a means of communication
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
	/**The name of the server that this io manager should reference in ANDAC (null if this is a client)*/
	private final String serverName;
	/**Flag for whether or not the host computer's networks are currently enabled*/
	private boolean networkEnabled = true;
	
	/**Constructs a new io manager, which constructs and downloads all the necessary files an programs for the
	 * client or server to function properly, in addition to establishing external connections to the master
	 * database and repository.
	 * @param logPrinter The log that this io manager should log all it's activity to
	 * @param directory The base directory that the io manager constructs it's files in
	 * @param name The name of the server that this io manager should reference in ANDAC (null if this is a client)
	 * @param authCode The authorization code for accessing the master database
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws IOException if there's an issue constructing the local file environment*/
	private IOManager(LogPrinter logPrinter, File directory, String name, String authCode) throws DbxException, IOException
	{
		log = logPrinter;//set this io manager's log
		baseDirectory = directory;//set the base directory that this io manager should manage
		serverName = "/ANDAC/" + name;//set the name that this file io should use in ANDAC
		File bin = new File(baseDirectory, "bin");//create a reference to the bin folder
		if(bin.mkdirs()){//if the bin directory was created
			log.log("Created the bin directory");//log that the bin directory was created
		}
		database = establishDatabaseConnection(name, authCode);//establish a connection to the master database
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
	static IOManager createServerFileManager(LogPrinter logPrinter, File serverDirectory, String name, String authCode) throws IOException, DbxException
	{
		logPrinter.log("Initializing server IO manager...");//log that a new server io manager is being initialized
		IOManager fileManager = new IOManager(logPrinter, serverDirectory, name, authCode);//create a new io manager for the server
		logPrinter.log("Successfully created new client IO manager at: " + serverDirectory.getAbsolutePath());//log that the server io manager was created successfully
		if(fileManager.database.createFolder("/ANDAC/" + name) != null){//if this server's folder in ANDAC was just created
			logPrinter.log("Added server to ANDAC database");//log that the server has been added into ANDAC
		}
		if(fileManager.database.createFolder("/ANDAC/" + name + "/Results") != null){//if the server's results folder was just created
			logPrinter.log("Created results folder for server in ANDAC");//log that the server's results folder was just created
		}
		File logFile = new File(serverDirectory, "log.txt");//create a reference to the server's log file
		if(fileManager.database.getMetadata("/ANDAC/" + name + "/log.txt") == null){//if this server doesn't have a log file in ANDAC
			logFile.createNewFile();//create a new log file for the server
			fileManager.uploadFile("/ANDAC/" + name + "/log.txt", logFile.getAbsolutePath());//upload the new log file to ANDAC
			logPrinter.log("Successfully created new log file for " + name);//log that the server's log file was created
		} else{//if this server has a log file in ANDAC
			fileManager.downloadFile("/ANDAC/" + name + "/log.txt", logFile.getAbsolutePath());//download the server's log file
			logPrinter.log("Retrieved server log file from ANDAC");//log that the server's log file has been successfully downloaded
		}
		if(fileManager.database.getMetadata("/ANDAC/" + name + "/status.txt") == null){//if this server doesn't have a status file in ANDAC
			fileManager.uploadData("/ANDAC/" + name + "/status.txt", new byte[] {});//upload an empty status file to ANDAC
			logPrinter.log("Successfully created new status file for " + name);//log that the server's status file was created
		}
		return fileManager;//return the new io manager created for the server
	}
	
	/**Creates a new io manager to set up and manage the files necessary for the client to function, as well
	 * as establishing a connection to the master database and repository for downloading data and updating itself
	 * @param logPrinter The log that the io manager should log all it's activity to
	 * @param clientDirectory File object for the directory that the client is running from
	 * @param authCode The authorization code for accessing the master database
	 * @throws DbxException If there's an issue communicating with the master database
	 * @throws IOException if there's an issue constructing the local file environment*/
	static IOManager createClientFileManager(LogPrinter logPrinter, File clientDirectory, String authCode) throws IOException, DbxException
	{
		logPrinter.log("Initializing client IO manager...");//log that a new client io manager is being initialized
		IOManager fileManager = new IOManager(logPrinter, clientDirectory, null, authCode);//create a new io manager for the client
		logPrinter.log("Successfully created new client IO manager at: " + clientDirectory.getAbsolutePath());//log that the client io manager was created successfully
		return fileManager;//return the new io manager created for the client
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
		return database.uploadFile(remotePath, DbxWriteMode.add(), -1, new FileInputStream(new File(localPath)));//make a stream for the file and upload it
	}
	
	/**Uploads byte data to the master database stored as a file of the specified name
	 * @param remotePath The path to give this data's file in the master database
	 * @param data The data to upload, stored as an array of bytes
	 * @return Meta-data about the upload as a DbxEntry
	 * @throws IOException If the data can't be read properly
	 * @throws DbxException If the upload encounters a problem*/
	private DbxEntry uploadData(String remotePath, byte[] data) throws IOException, DbxException
	{
		return database.uploadFile(remotePath, DbxWriteMode.add(), -1, new ByteArrayInputStream(data));//wrap the data in an input stream and upload it
	}
	
	/**Updates the server's log and status files in ANDAC
	 * @param status The formatted status string of this server to be uploaded
	 * @throws IOException If the data can't be written properly
	 * @throws DbxException If the download encounters a problem*/
	void updateANDAC(String status) throws IOException, DbxException
	{
		uploadData(serverName + "/status.txt", status.getBytes(log.encoding));//upload the status string to this server's status file
		synchronized(log){//lock log
			uploadFile(serverName + "/log.txt", new File(baseDirectory, "log.txt").getAbsolutePath());//upload this server's log file to it's ANDAC entry
		}//release log
	}
	
	/**Upload the results of task into this server's ANDAC entry under the 'Results' directory
	 * @param name The file name that this task should be uploaded with
	 * @param results The results of processing the task, stored as an array of serializable objects
	 * @throws IOException If the results couldn't be serialized properly
	 * @throws DbxException If the results couldn't be uploaded properly*/
	void uploadResult(String name, Serializable[] results) throws IOException, DbxException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();//create a byte array stream for writing all the results into as bytes
		try(ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)){//create an object output stream for writing the results to
			for(Serializable result : results){//iterate through all the results
				objectOutputStream.writeObject(result);//write the result into the output stream
			}
		}
		uploadData(serverName + "/Results/" + name, byteArrayOutputStream.toByteArray());//upload the byte array of results into a results file in ANDAC
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
		File localFile = new File(localPath);//create a reference to the download location
		localFile.getParentFile().mkdirs();//attempt to create any parent folders that this file should be inside
		Util.delete(localFile);//delete anything at the local file's location
		localFile.createNewFile();//create the local file
		try(FileOutputStream fileOutputStream = new FileOutputStream(localFile)){//create a stream for writing to the local file
			if(database.getFile(remotePath, null, fileOutputStream) == null){//if the remote file didn't exist in the master database
				throw new FileNotFoundException(remotePath + " could not be located in the master database");//except that the file couldn't be found
			}
			return localFile;//return the reference to the local file
		}
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
		log.log("Downloading " + name + "...");//log that the file download is beginning
		File zip = new File(localFile, name);//create a reference to the zip file
		downloadFile(zip.getAbsolutePath(), remotePath);//download the zip file
		log.log("Successfully downloaded " + name);//log that the zip file was downloaded
		ProcessBuilder decompress = new ProcessBuilder(new File(baseDirectory, "bin\\7za.exe").getAbsolutePath(), "x", localFile.getAbsolutePath(), "-y");//create a process to decompress the file
		decompress.redirectErrorStream(true);//merge the process's error stream into it's output stream
		log.log("Starting " + name + " decompression");//log that the decompression portion is beginning
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
		DbxEntry metadata = database.getMetadata(remotePath);//retrieve the file's meta-data
		if(metadata == null){//if the specified file doesn't exist in the master database
			throw new FileNotFoundException(remotePath + " could not be located in the master database");//except that the file couldn't be found
		}
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int)metadata.asFile().numBytes);//allocate a new byte output stream the same size as the file
		database.getFile(remotePath, null, byteArrayOutputStream);//download the file into the byte output stream
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
		String filePath = classPath.replace('.', '/') + ".class";//get the file formatted class path
		downloadFile(baseDirectory + "\\tasks\\" + filePath.replace('/', '\\'), "/ANDAC/taskClasses/" + filePath);//download the class file
		try{//try to load in the class
			return classLoader.loadClass(classPath);//load the class's file into the JVM, and return a reference to it's object representation
		} catch(ClassNotFoundException classNotFoundException){//if the class couldn't be loaded properly
			throw new IOException("Failed to load in class", classNotFoundException);//except that the class wasn't loaded correctly
		}
	}
	
	/**Checks ANDAC to see if any new tasks are queued for this server, downloading any that are
	 * @return An array of strings containing the tasks to be executed by the server
	 * @throws IOException If the data couldn't be retrieved properly
	 * @throws DbxException If the download encountered a problem*/
	String[] fetchTasks() throws IOException, DbxException
	{
		if(database.getMetadata(serverName + "/tasks.txt") != null){//if this server has tasks queued from ANDAC
			try{//wrapper to ensure the lock file is deleted
				uploadData(serverName + "/LOCK", new byte[] {});//lock tasks.txt
				String[] tasks = new String(downloadData("/ANDAC/" + serverName + "/tasks.txt"), log.encoding).split("\n");//download and store all the new tasks
				log.log(tasks.length + " new tasks downloaded");//log the number of new tasks downloaded
				return tasks;//return the tasks
			} finally{//ensure the lock file is deleted
				database.delete(serverName + "/LOCK");//delete the lock file
			}//release tasks.txt
		}
		return new String[] {};//return an empty array of tasks
	}
	
	//TODO
	private boolean setNetworkState(boolean state) throws IOException
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
		return success;//return whether all the processes were successful
	}
}