
package net.insertcreativity.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

/**Class that encapsulates logging capabilities, which has a list of subscriber output-streams, which the
 * log forwards all it's data to. In the case a subscriber malfunctions, the log writer notifies on the output stream
 * and then removes it from the subscriber list*/
public class LogWriter extends OutputStream
{
	/**Encoding that the log should output it's characters with*/
	public final Charset encoding;
	/**Buffer for storing data written to the log before outputting it*/
	private final byte[] logBuffer;
	/**Index for storing the current location in the buffer*/
	private int bufferIndex = 0;
	/**Map containing all the output-streams registered to this log, keyed by their names*/
	private final HashMap<String, OutputStream> outputs  = new HashMap<String, OutputStream>();
	/**List for storing exceptions subscribers raise while being flushed to*/
	private final ArrayList<IOException> exceptions = new ArrayList<IOException>();

	/**Create a new log with no outputs that uses UTF-8 encoding and has a buffer size of 8192*/
	public LogWriter()
	{
		logBuffer = new byte[8192];//allocate 8192 bytes to the log buffer
		encoding = StandardCharsets.UTF_8;//encode characters with the UTF8 standard
	}
	
	/**Create a new log with no outputs that uses UTF-8 encoding
	 * @param bufferSize How many bytes long the log's buffer should be*/
	public LogWriter(int bufferSize)
	{
		logBuffer = new byte[bufferSize];//allocate the specified number of bytes to the log buffer
		encoding = StandardCharsets.UTF_8;//encode characters with the UTF8 standard
	}
	
	/**Create a new log with no outputs and a buffer size of 8192
	 * @param charset Charset that the log should use to encode it's characters*/
	public LogWriter(Charset charset)
	{
		logBuffer = new byte[8192];//allocate 8192 bytes to the log buffer
		encoding = charset;//encode characters with the specified standard
	}
	
	/**Create a new log with no outputs and the specified settings
	 * @param bufferSize How many bytes long the log's buffer should be
	 * @param charset Charset that the log should use to encode it's characters*/
	public LogWriter(int bufferSize, Charset charset)
	{
		logBuffer = new byte[bufferSize];//allocate the specified number of bytes to the log buffer
		encoding = charset;//encode characters with the specified standard
	}
	
	/**Subscribe an output-stream to this log so that it'll receive the log's output
	 * @param name String identifier for the output-stream
	 * @param output The output-stream to subscribe
	 * @return The output-stream previously subscribed under the provided name, or null if the name is new*/
	public OutputStream addOutput(String name, OutputStream output)
	{
		flush();//flush the log writer to ensure there's no data gets carried over to this output
		return outputs.put(name, output);//add the provided output and it's name into the outputs map
	}
	
	/**Unsubscribes an output-stream from this log
	 * @param name The identifier for the output-stream to be unsubscribed
	 * @return The output-stream that was removed, or null if there was no output with the specified name*/
	public OutputStream removeOutput(String name)
	{
		OutputStream outputStream =  outputs.remove(name);//remove the output stream from the map
		if(outputStream != null){//if there was an output stream with the specified name
			try{//try to flush the log to this output before removing it
				outputStream.write(logBuffer, 0, bufferIndex);//write the log buffer to the output stream
				outputStream.flush();//flush the output stream
			} catch(IOException ioException){//if the log buffer couldn't be written and flushed to the output
				log("Failed to properly remove " +  name);//log that the output stream, wasn't removed properly
				ioException.printStackTrace();//print the exception out
			}
		}
		return outputStream;//return the result of removing the name
	}
	
	/**Logs a string with a date-stamp
	 * @param s The string to be logged*/
	public void log(String s)
	{
		write(("<" + System.currentTimeMillis() + ">" + s + "\n").getBytes(encoding));//write the string with a date-stamp
	}
	
	/**Writes a single byte into the log
	 * @param b An integer whose first 8 bits will be written as a byte to the log*/
	public void write(int b)
	{
		logBuffer[bufferIndex++] = (byte)b;//write the byte into the log buffer
		if(bufferIndex == logBuffer.length){//if the log buffer is full
			flush();//flush the log
		}
	}
	
	/**Writes an array of bytes into the log
	 * @param b An array of bytes to write into the log*/
	public void write(byte[] b)
	{
		int spaceLeft = logBuffer.length - bufferIndex;//calculate how many bytes are left in the log buffer
		if(spaceLeft <= b.length){//if there isn't enough space in the log buffer
			System.arraycopy(b, 0, logBuffer, bufferIndex, spaceLeft);//copy in as many bytes as possible to the log buffer
			flush();//flush the log buffer
			System.arraycopy(b, spaceLeft, logBuffer, bufferIndex, (b.length - spaceLeft));//copy in the remaining bytes
			bufferIndex += (b.length - spaceLeft);//increment the buffer index by how many bytes were added
		} else{//if there is sufficient space in the log buffer
			System.arraycopy(b, 0, logBuffer, bufferIndex, b.length);//copy the bytes into the log buffer
			bufferIndex += b.length;//increment the buffer index by how many bytes were added
		}
	}
	
	/**Writes a specific section of a byte array into the log
	 * @param b An array of bytes to write into the log
	 * @param off The starting offset to write bytes from b
	 * @param len How many bytes should be written from b*/
	public void write(byte[] b, int off, int len)
	{
		int spaceLeft = logBuffer.length - bufferIndex;//calculate how many bytes are left in the log buffer
		if(spaceLeft <= len){//if there isn't enough space in the log buffer
			System.arraycopy(b, off, logBuffer, bufferIndex, spaceLeft);//copy in as many bytes as possible to the log buffer
			flush();//flush the log buffer
			System.arraycopy(b, off + spaceLeft, logBuffer, bufferIndex, (len - spaceLeft));//copy in the remaining bytes
			bufferIndex += (len - spaceLeft);//increment the buffer index by how many bytes were added
		} else{//if there is sufficient space in the log buffer
			System.arraycopy(b, off, logBuffer, bufferIndex, len);//copy the specified bytes into the log buffer
			bufferIndex += len;//increment the buffer index by how many bytes were added
		}
	}

	/**Flushes the log buffer, writing all it's data into the subscriber outputs before flushing them*/
	public void flush()
	{
		try{//wrapper to ensure that the buffer index gets reset
			for(HashMap.Entry<String, OutputStream> output : outputs.entrySet()){//iterate through all the subscribed outputs 
				try{//try to write the log buffer to the output stream
					output.getValue().write(logBuffer, 0, bufferIndex);//write the log buffer to the output stream
					output.getValue().flush();//flush the output-stream
				} catch(IOException ioException){//if the log buffer couldn't be written
					exceptions.add(new IOException("Failed to properly flush to " + output.getKey(), ioException));//store the exception
					outputs.remove(output.getKey());//unsubscribe the output stream
					//TODO what to do now? notify the output stream? close it?
				}
			}
		} finally{//ensures that the buffer index gets reset
			bufferIndex = 0;//reset the buffer index
			for(IOException ioException : exceptions){//iterate through the exceptions
				ioException.printStackTrace();//print out the exception
			}
			exceptions.clear();//clear the exception list
		}
	}
	
	/**Closes the log and any subscribed streams*/
	public void close()
	{
		try{//wrapper to ensure the buffer index and output map get reset
			for(OutputStream output : outputs.values()){//iterate through all the subscribed outputs
				try{//try to write the log buffer to the output-stream and then close it
					//try to write the log buffer to the output-stream
					output.write(logBuffer, 0, bufferIndex);//write the log buffer to the output-stream
					output.flush();//flush the output-stream
					output.close();//close the output-stream
				} catch(IOException ioException){//if the log buffer couldn't be written, or the output couldn't be closed
					ioException.printStackTrace();//print the exception
				}
			}
		} finally{//ensures that the buffer index and output map get reset
			bufferIndex = 0;//reset the buffer index
			outputs.clear();//clear the output map
		}
	}
}