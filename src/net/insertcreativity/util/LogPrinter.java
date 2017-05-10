
package net.insertcreativity.util;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**Extension of LogWriter with the capabilities of a PrintStream, and synchronous logging*/
public class LogPrinter extends PrintStream
{
	
	/**Reference to the underlying log writer*/
	private final LogWriter log;
	
	/**Create a new log printer that uses UTF-8 encoding and has a buffer size of 8192 with auto flushing
	 * @throws UnsupportedEncodingException If UTF_8 isn't supported*/
	public LogPrinter() throws UnsupportedEncodingException
	{
		this(makeLogWriter(8192, StandardCharsets.UTF_8), true);//make the underlying log writer and pass it to the underlying print stream
	}
	
	/**Create a new log printer that uses UTF-8 encoding
	 * @param bufferSize How many bytes long the log's buffer should be
	 * @param autoFlush Flag for whether the log should flush whenever an array or newline is written
	 * @throws UnsupportedEncodingException If UTF_8 isn't supported*/
	public LogPrinter(int bufferSize, boolean autoFlush) throws UnsupportedEncodingException
	{
		this(makeLogWriter(bufferSize, StandardCharsets.UTF_8), autoFlush);//make the underlying log writer and pass it to the underlying print stream
	}
	
	/**Creates a new log printer
	 * @param bufferSize How many bytes long the log's buffer should be
	 * @param autoFlush Flag for whether the log should flush whenever an array or newline is written
	 * @param encoding Charset that the log should use to encode it's characters
	 * @throws UnsupportedEncodingException If the provided encoding isn't supported*/
	public LogPrinter(int bufferSize, boolean autoFlush, Charset encoding) throws UnsupportedEncodingException
	{
		this(makeLogWriter(bufferSize, encoding), autoFlush);//make the underlying log writer and pass it to the underlying print stream
	}
	
	/**Method for in-lining the creation of the underlying log writer
	 * @param bufferSize How many bytes long the underlying log writer's buffer should be
	 * @param encoding Charset that the underlying log writer should use to encode it's characters*/
	private static LogWriter makeLogWriter(int bufferSize, Charset encoding)
	{
		return new LogWriter(bufferSize, encoding);//create a new log writer with the specified buffer and encoding
	}
	
	/**Creates a new log printer using the underlying log writer
	 * @param logWriter The log writer that will underlie this log printer
	 * @param autoFlush Flag for whether the log should flush whenever an array or newline is written
	 * @throws UnsupportedEncodingException If the encoding used in the log writer isn't supported*/
	private LogPrinter(LogWriter logWriter, boolean autoFlush) throws UnsupportedEncodingException
	{
		super(logWriter, autoFlush, logWriter.encoding.name());//initialize the underlying print stream
		log = logWriter;//set the underlying log writer for this log printer
	}
	
	/**Subscribe an output-stream to this log so that it'll receive the log's output
	 * @param name String identifier for the output-stream
	 * @param output The output-stream to subscribe
	 * @return The output-stream previously subscribed under the provided name, or null if the name is new*/
	public OutputStream addOutput(String name, OutputStream output)
	{
		return log.addOutput('O' + name, output);//return the result from the underlying log writer
	}
	
	/**Unsubscribes an output-stream from this log
	 * @param name The identifier for the output-stream to be unsubscribed
	 * @return The output-stream that was removed, or null if there was no output with the specified name*/
	public OutputStream removeOutput(String name)
	{
		return log.removeOutput('O' + name);//return the result from the underlying log writer
	}
	
	/**Logs a string with a date-stamp
	 * @param s The string to be logged*/
	public void log(String s)
	{
		synchronized(this){//synchronize on the log printer
			log.log(s);//log the string to the underling log writer
		}
	}
	
	/**Captures the system's output stream, routing all it's output through the log printer first.
	 * Note that this does not subscribe the system output stream, closing the log printer will only
	 * reset the system output stream to how it was originally, it won't close the stream*/
	public void captureOut()
	{
		log.addOutput("SysOut", System.out);//add the system output stream to the log outputs
		System.setOut(this);//replace the system output stream with this log printer
	}
	
	/**Captures the system's error stream, routing all it's output through the log printer first.
	 * Note that this does not subscribe the system error stream, closing the log printer will only
	 * reset the system error stream to how it was originally, it won't close the stream*/
	public void captureErr()
	{
		log.addOutput("SysErr", System.err);//add the system error stream to the log outputs
		System.setErr(this);//replace the system error stream with this log printer
	}
	
	/**Close the underlying print stream, releasing any captured outputs first*/
	public void close()
	{
		PrintStream outputStream = (PrintStream)log.removeOutput("SysOut");//remove the system output stream if it's captured
		if(outputStream != null){//if there was a captured system output stream
			System.setOut(outputStream);//set the system output stream back to the original
		}
		PrintStream errorStream = (PrintStream)log.removeOutput("SysErr");//remove the system error stream if it's captured
		if(errorStream != null){//if there was a captured system error stream
			System.setErr(errorStream);//set the system error stream back to the original
		}
		super.close();//close the underlying print stream
	}
}
