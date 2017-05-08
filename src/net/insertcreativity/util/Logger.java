
package net.insertcreativity.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;

/**Class that encapsulates all the logging capabilities of the server*/
public class Logger
{
	/**Lock for ensuring synchronous access to the logs*/
	private final Object LOG_LOCK = new Object();
	/**Map containing all the output-streams registered to this logger, keyed by their names*/
	private final HashMap<String, OutputStream> outputs  = new HashMap<String, OutputStream>();
	
	/**Adds an output stream into the streams that this log will output to
	 * @param name String identifier for the output-stream
	 * @param output A supplier that yields an output-stream when called*/
	public void addOutput(String name, OutputStream output)
	{
		synchronized(LOG_LOCK){//sync outputs
			outputs.put(name, output);//add the provided output and it's name into the outputs map
		}
	}
	
	/**Removes an output stream from the list of log outputs, and returns it, unclosed
	 * @param name String identifier for the output-stream to be removed
	 * @return the output-stream that was removed, or null if there was no stream with the specified name*/
	public OutputStream removeOutput(String name)
	{
		synchronized(LOG_LOCK){//sync outputs
			return outputs.remove(name);//return the result of removing the output stream from the outputs map
		}
	}
	
	/**Logs a string with a date-stamp
	 * @param s The string to be logged*/
	public final void log(String s)
	{
		logRaw("<" + System.currentTimeMillis() + ">" + s);//log the string with a date-stamp
	}
	
	/**Logs a throwable, first printing the message to the user with a date-stamp, and then logging the throwable's
	 * stack trace elements
	 * @param throwable The throwable to be logged*/
	public final void log(Throwable throwable)
	{
		log(throwable.toString() + '\n');//log the exception's message with a date-stamp
		logRaw(getThrowableString(throwable));//log the throwable's stack trace
	}
	
	/**Constructs a string representation of a throwable's full stack trace
	 * @param throwable The throwable who's stack trace should be retrieved
	 * @return A string containing the full stack trace and message of the throwable*/
	private final String getThrowableString(Throwable throwable)
	{
		Throwable cause = throwable.getCause();//get the cause of the throwable
		String causeString = "";//create a string for storing the cause's error message
		if(cause != null){//if the original throwable did have a cause
			causeString = "Caused by:" + cause.toString() + '\n' + getThrowableString(cause);//store the cause's string
		}
		int total = causeString.length();//create a variable for storing the total number of characters
		StackTraceElement[] stackTraceElements = throwable.getStackTrace();//get all the elements of the stack trace
		String[] stackElementStrings = new String[stackTraceElements.length];//create an array for storing all the stack trace strings
		for(int counter = 0; counter < stackTraceElements.length; counter++){//iterate through all the stack trace elements
			stackElementStrings[counter] = "\tat " + stackTraceElements[counter].toString() + '\n';//store the stack element's string value
			total += stackElementStrings[counter].length();//add the number of chars in the string to the total
		}
		char[] characters = new char[total];//create a char array for holding all the string's data
		int offsetValue = 0;//create a variable for keeping track of the current offset in the character array
		for(String stackElementString : stackElementStrings){//iterate through all the stack element's strings
			stackElementString.getChars(0, stackElementString.length(), characters, offsetValue);//copy the stack element's string into the array
			offsetValue += stackElementString.length();//add the stack trace's length to the total character count
		}
		causeString.getChars(0, causeString.length(), characters, offsetValue);//copy in the cause
		return new String(characters);//return a new string made of the character array
	}
	
	/**Logs a string exactly as it's given
	 * @param s The string to log*/
	public void logRaw(String s)
	{
		synchronized(LOG_LOCK){//sync outputs
			for(Iterator<HashMap.Entry<String, OutputStream>> iterator = outputs.entrySet().iterator(); iterator.hasNext();){//iterate through all the outputs
				HashMap.Entry<String, OutputStream> entry = iterator.next();//retrieve the next output entry
				try{//try to write the string to the output stream
					entry.getValue().write(s.getBytes(StandardCharsets.UTF_8));//encode the string as bytes and write them to the output
				} catch(IOException ioException){//if the string couldn't be written successfully
					iterator.remove();//remove the output from the output map
					log(entry.getKey() + " was removed from the logger due to a malfunction");//log that the output was removed for malfunctioning
					log(ioException);//log the exception thrown from the output
					try{//try to close the output before discarding it
						entry.getValue().close();//close the output
					} catch(Exception exception){//if the output couldn't be closed
						log("Failed to close output: " + entry.getKey());//log that the output couldn't be closed properly
					}
				}
			}
		}
	}
}
