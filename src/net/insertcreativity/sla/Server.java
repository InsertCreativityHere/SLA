
package net.insertcreativity.sla;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxAuthInfo;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxHost;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;
import com.dropbox.core.json.JsonReader.FileLoadException;

import net.insertcreativity.util.Util;

public class Server
{
	
	
	/**Class responsible for encapsulating the server's file architecture; creating the server's file environment
	 * upon execution, and keeping the server's local files synced with the master repository and SLA database*/
	private class FileManager
	{
		/**Client used to communicate with the SLA database*/
		private DbxClient databaseClient;
		
		/**Creates a new file manager, which sets up the local file environment, and establishes connections
		 * to both the master repository and SLA database
		 * @param serverDir The directory that the server is running from
		 * @param authCode authorization code for the SLA database
		 * @throws DbxException If there was an error communicating with the SLA database
		 * @throws FileLoadException If the Dropbox key is malformed
		 * @throws IOException If the file manager is unable to create or access a file in the local file architecture
		 * @throws IOException If the local server files can't be correctly created or accessed*/
		private FileManager(String serverName, File serverDir, String authCode) throws DbxException, FileLoadException, IOException
		{
			databaseClient = establishDatabaseConnection(serverName, serverDir, authCode);//establish a connection with the SLA database
			if(databaseClient.getMetadata("/ANDAC/" + serverName) == null)//if this server doesn't have a folder in ANDAC
			{
				databaseClient.createFolder("/ANDAC/" + serverName);//create a folder for this server in the ANDAC
			}
			if(!(new File(serverDir, "bin\\Git\\git-bash.exe")).canExecute())//if the Git bash doesn't exist or can't be executed
			{
				installGit(serverDir);//install Git from scratch
			}
		}
		
		/**Establishes a connection to the SLA database, returning a client that can be used to access it directly
		 * @param serverName String identifier for this server
		 * @param serverDir File reference to the root directory the server is using
		 * @param authCode Code passed to the API to obtain authorized access to the SLA database
		 * @return A client object that can be used to make API calls to the SLA database
		 * @throws DbxException If an error occurs in establishing the connection with Dropbox
		 * @throws FileLoadException If the provided key file is malformed*/
		private DbxClient establishDatabaseConnection(String serverName, File serverDir, String authCode) throws DbxException, FileLoadException
		{
			DbxRequestConfig dbxRequestConfig = new DbxRequestConfig("SLA-" + serverName, Locale.getDefault().toString());//store the request configuration to use with the Dropbox SLA database
			return new DbxClient(dbxRequestConfig, authCode, DbxHost.Default);//return the Dropbox client
		}
		
		/**Uploads a file to the SLA database
		 * @param localPath The path location of the file to be uploaded on the local computer
		 * @param remotePath The path that the file should be uploaded to on the SLA database
		 * @throws IOException If there was an issue reading from the local file
		 * @throws DbxException If there was an issue uploading the file to the SLA database*/
		private void uploadFileToDatabase(String localPath, String remotePath) throws IOException, DbxException
		{
			try(InputStream inputStream = new FileInputStream(new File(localPath)))//try to create an input stream for the uploaded file
			{
				databaseClient.uploadFile(remotePath, DbxWriteMode.add(), -1, inputStream);//upload the file to the database
			}
		}
		
		/**Downloads a file from the SLA database
		 * @param localPath The path that the file should be downloaded to on the local computer
		 * @param remotePath the path location of the file inside the SLA database
		 * @return File reference to the downloaded file
		 * @throws IOException If there was an issue writing to the local file
		 * @throws DbxException If there was an issue downloading from the SLA database*/
		private DbxEntry downloadFileFromDatabase(String localPath, String remotePath) throws IOException, DbxException
		{
			File localFile = new File(localPath);//store the local file
			boolean thing;
			if(!localFile.getParentFile().isDirectory() && !(thing=localFile.getParentFile().mkdirs()))//if any of the parent directories don't exist and couldn't be created
			{
				System.out.print(thing);
				System.out.println(localFile.getParentFile().mkdirs());
				throw new IOException("Failed to create parents for " + localFile.getAbsolutePath());//except that the parent's couldn't be made
			}
			try(OutputStream outputStream = new FileOutputStream(localFile))//try to create an output stream for the download file
			{
				DbxEntry metadata = databaseClient.getFile(remotePath, null, outputStream);//download the file from the database
				return metadata.asFile();//return the file that was downloaded
			}
		}
		
		/**Installs git into the server's bin directory, removing any previous installations
		 * @throws IOException If there was an issue deleting the previous Git installation or creating this one
		 * @throws DbxException If there was an issue downloading Git from the database*/
		private void installGit(File serverDir) throws IOException, DbxException
		{
			File gitDir = new File(serverDir, "bin\\Git");//create a reference to the git directory
			if(gitDir.exists() && !Util.delete(gitDir))//if the Git directory already exists and couldn't be deleted
			{
				throw new IOException("Failed to delete previous git installation");//except that the previous installation couldn't be deleted
			}
			File zipFile = new File(gitDir, "Git.zip");//store a reference to the zip file containing Git
			try{
				Thread.sleep(1000);
			} catch(Exception e){}
			downloadFileFromDatabase(zipFile.getAbsolutePath(), "/SLA/Git.zip");//download Git from the database
			ProcessBuilder unzip = new ProcessBuilder(serverDir.getAbsolutePath() + "\\bin\\7za920\\7za.exe", "x", "Git.zip", "-y");//create a process for unzipping Git
			Process process = unzip.directory(gitDir).start();//start the process
			InputStream inputstream = process.getInputStream();//retrieve the processes input stream
			int next = inputstream.read();//store the bytes being read off the process input stream
			while(next != -1)//so long as there's more bytes to read off
			{
				next = inputstream.read();//read the next byte
			}
			try{//try to wait until the process is completely finished
				process.waitFor();//set the process directory to be the Git directory, then starts the process and waits until it's done
			} catch(InterruptedException interruptedException)//if the process is interrupted
			{
				throw new IOException("Unzipping was interrupted");//except that the unzipping was interrupted
			}
			zipFile.delete();//attempt to delete the leftover zip file
		}
		
		
		
		
		
		
		/**Creates any files missing from the local file architecture
		 * @param serverDir The root directory for the server's files
		 * @throws IOException If any files cannot be created or accessed correctly*/
		private void createLocalFileArchitecture(File serverDir)
		{
			File taskDir = new File(serverDir, "taskClasses");//create a file reference for the task directory
			if(!taskDir.isDirectory() && !taskDir.mkdir()){//if the task directory doesn't exist and couldn't be created
				//throw new IOException("Failed to create the task directory");//inform the program of the failure
			}
			File gitExe = new File(serverDir, "bin\\Git\\git-bash.exe");
		}
	}

	
	/**Class responsible for encapsulating the server's file architecture, automatically creating the proper server
	 * environment if one is not found at the provided server directory. In addition it keeps the server directory
	 * synced with the SLA master repository and database, updating files and source, retrieving tasks and commands
	 * for the server along with uploading results and information from the server*/
	private class Fil3eManager implements Runnable
	{
		/**Creates a new file manager that manages the server running in the specified directory, checking all the files
		 * in the directory, and creating any missing or updating any old files.
		 * @param serverDir File object denoting the directory that the server is running from
		 * @throws IOException If files in the server directory couldn't be created or accessed correctly*/
		private Fil3eManager(File serverDir) throws IOException
		{

			
			createServerDirectory(serverDir);//create the server directory
			
			
			File gitExe = new File(serverDir, "bin\\Git\\git-bash.exe");//store a reference to the git bash executable
			if(!gitExe.canExecute())//if the server can't execute the git bash
			{
			}
			
			File GitDir = new File(serverDir, "bin\\Git");
			if(!GitDir.isDirectory()){//if the git directory isn't already an existent directory
				if(!GitDir.mkdirs()){//attempt to create the git directory, throwing an exception if it failed
					throw new IOException("Failed to create git directory");//throw an exception to signal failure
				}
			}
			
		}
		
		private void createServerDirectory(File serverDir) throws IOException
		{
			String[] dirElements;//array containing all the elements of the server directory from the master repository
			try(BufferedReader dirListStream = new BufferedReader(new InputStreamReader(new URL("https://raw.githubusercontent.com/insertcreativityhere/SLA/master/serverDir.dat").openStream())))
			{
				dirElements = new String[Integer.parseInt(dirListStream.readLine())];//load how many elements there are
				for(int counter = 0; counter < dirElements.length; counter++){//load all the directory elements
					dirElements[counter] = dirListStream.readLine();//load the next directory element
				}
			}
			
		}
		
		//new DbxWebAuthNoRedirect(dbxRequestConfig, DbxAppInfo.Reader.readFromFile(new File(serverDir, "res\\dropbox.key"))).finish(<<<...>>>)
		
		public void run()
		{
		}
	}
	
	public static void main(String args[]) throws Exception
	{
		FileManager fileManager = (new Server()).new FileManager("Testing", new File("C:\\Users\\ahenriksen2015\\Downloads\\testing\\"), "zGw7BngehFAAAAAAAAAAEorYRDKDnxFue0w4cQkTN0EO2iorC-uDLwyQyuZV25SS");
	}
}
