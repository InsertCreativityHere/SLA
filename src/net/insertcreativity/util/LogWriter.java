
package net.insertcreativity.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**Class that encapsulates logging capabilities, which has a list of subscriber output-streams, which the
 * log forwards all it's data to*/
public class LogWriter extends OutputStream
{
	/**Encoding that the log should output it's characters with*/
	public final Charset encoding;
	/**Buffer for storing data written to the log before outputting it*/
	private final byte[] logBuffer;
	/**Index for storing the current location in the buffer*/
	private int bufferIndex = 0;
	/**Lock for ensuring synchronous access to the log outputs*/
	private final Object OUT_LOCK = new Object();
	/**Map containing all the output-streams registered to this log, keyed by their names*/
	private final HashMap<String, OutputStream> outputs  = new HashMap<String, OutputStream>();

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
		synchronized(OUT_LOCK){//sync outputs
			return outputs.put(name, output);//add the provided output and it's name into the outputs map
		}
	}
	
	/**Unsubscribes an output-stream from this log
	 * @param name The identifier for the output-stream to be unsubscribed
	 * @return The output-stream that was removed, or null if there was no output with the specified name*/
	public OutputStream removeOutput(String name)
	{
		synchronized(OUT_LOCK){//sync outputs
			return outputs.remove(name);//return the result of removing the output stream from the outputs map
		}
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
		} else{//if there is sufficient space in the log buffer
			System.arraycopy(b, 0, logBuffer, bufferIndex, b.length);//copy the bytes into the log buffer
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
		} else{//if there is sufficient space in the log buffer
			System.arraycopy(b, off, logBuffer, bufferIndex, len);//copy the specified bytes into the log buffer
		}
	}

	/**Flushes the log buffer, writing all it's data into the subscriber outputs before flushing them*/
	public void flush()
	{
		synchronized(OUT_LOCK){//sync outputs
			for(OutputStream output : outputs.values()){//iterate through all the outputs subscribed to this log
				try{//try to log to the output-stream
					output.write(logBuffer, 0, bufferIndex);//write the log buffer to the output-stream
					output.flush();//flush the output-stream
				} catch(IOException ioException){
					//TODO
				} finally{
					bufferIndex = 0;//reset the log buffer
				}
			}
		}
	}
	
	/**Closes the log and any subscribed streams*/
	public void close()
	{
		synchronized(OUT_LOCK){//sync outputs
			for(OutputStream output : outputs.values()){//iterate through all the outputs subscribed to this log
				try{//try to close the current output-stream
					output.write(logBuffer, 0, bufferIndex);//write the log buffer to the output-stream
					output.flush();//flush the output-stream
					output.close();//close the output-stream
				} catch(IOException ioException){
					//TODO
				} finally{
					bufferIndex = 0;//reset the buffer index
					outputs.clear();//remove all the subscribed output-streams
				}
			}
		}
	}
}