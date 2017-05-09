
package net.insertcreativity.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;

public class RepairableInputStream<T extends InputStream> extends InputStream
{
	/**List of arguments that satisfy the input-stream's constructor*/
	private final Object[] parameters;
	/**Reference to the input-stream's constructor*/
	private final Constructor<T> constructor;
	/**Reference to the underlying input-stream*/
	private T input;
	
	/**Create a new input-stream wrapped as a repairable object, by invoking it's constructor with the provided arguments
	 * @param type The class type for the input-stream that this is wrapping
	 * @param args An array containing the arguments to be passed into the input-stream's constructor
	 * @throws ReflectiveOperationException If the input-stream cannot be created with the provided arguments
	 * @throws Exception Any exceptions thrown by the output-stream's constructor will be propagated;
	 *                   it's up to the caller to know what exceptions to catch from this*/
	public RepairableInputStream(Class<T> type, Object[] args) throws ReflectiveOperationException
	{
		parameters = args;//store the arguments as the constructor's parameters
		Class<?>[] types = new Class<?>[args.length];//allocate an array for all the argument's types
		for(int counter = 0; counter < args.length; counter++){//iterate through the arguments
			types[counter] = args[counter].getClass();//store the argument's type
		}
		constructor = type.getConstructor(types);//retrieve the corresponding constructor
		input = constructor.newInstance(args);//create a new instance of the input-stream
	}
	
	/**Attempts to repair the underlying input-stream by reconstructing it
	 * @throws ReflectiveOperationException If the input-stream cannot be reconstructed
	 * @throws IOException If the old input-stream couldn't be closed
	 * @throws Exception Any exceptions thrown by the input-stream's constructor will be propagated;
	 *                   its up to the caller to know what exceptions to catch from this*/
	public void repair() throws ReflectiveOperationException
	{
		try{//try to close the input-stream before discarding it
			input.close();//close the old input-stream
		} catch(IOException ioException){//if the old input-stream couldn't be closed
			ioException.printStackTrace();//print the exception
		} finally{//ensure that the new input-stream is created
			input = constructor.newInstance(parameters);//reconstruct the underlying input-stream
		}
	}
	
	/**Reads the next byte from the underlying input-stream
	 * @return An int ranging from 0 to 255 representing the value of the byte read, or -1
	 *         if there is no more data to read
	 * @throws IOException If the underlying input-stream failed and couldn't be successfully repaired*/
	public int read() throws IOException
	{
		try{//try to read the next byte
			return input.read();//read from the underlying input-stream
		} catch(Exception exception){//if the underlying input-stream couldn't be read from
			try{//try to repair the input-stream
				repair();//repair the input-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			return input.read();//read from the new input-stream
		}
	}
	
	/**Reads 'b.length' many bytes from the underlying input-stream into the byte array
	 * @param b An array of bytes to read into
	 * @return The actual number of bytes read into the byte array, or -1 if there is no more data to read
	 * @throws IOException If the underlying input-stream failed and couldn't be successfully repaired*/
	public int read(byte[] b) throws IOException
	{
		try{//try to read into the byte array
			return input.read(b);//have the underlying input-stream read into the byte array
		} catch(Exception exception){//if the byte's couldn't be read into the byte array
			try{//try to repair the input-stream
				repair();//repair the input-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			return input.read(b);//have the new input-stream read into the byte array
		}
	}

	/**Reads 'len' many bytes from the underlying input-stream into a section of a byte array starting at an
	 * offset of off
	 * @param b An array of bytes to read into
	 * @param off The starting offset in b
	 * @param len How many bytes to input
	 * @return The actual number of bytes read into the byte array, or -1 if there is no more data to read
	 * @throws IOException If the underlying input-stream failed and couldn't be successfully repaired*/
	public int read(byte[] b, int off, int len) throws IOException
	{
		try{//try to read into the specified section of bytes
			return input.read(b, off, len);//have the underlying input-stream read in the bytes
		} catch(Exception exception){//if the byte's couldn't be read into the byte array
			try{//try to repair the input-stream
				repair();//repair the input-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			return input.read(b, off, len);//have the new input-stream read in the bytes
		}
	}
	
	/**Skips over up to the specified number of bytes in the stream
	 * @return How many bytes were actually skipped in the stream
	 * @throws IOException If the underlying input-stream failed and couldn't be successfully repaired*/
	public long skip(long n) throws IOException
	{
		try{//try to skip in the input-stream
			return input.skip(n);//skip over 'n' bytes in the input-stream
		} catch(Exception exception){//if the bytes couldn't be skipped over
			try{//try to repair the input-stream
				repair();//repair the input-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			return input.skip(n);//skip over 'n' bytes in the input-stream
		}
	}
	
	/**Returns an estimate of how many bytes are left to be read when this is called
	 * @throws IOException If the underlying input-stream failed and couldn't be successfully repaired*/
	public int avaiable() throws IOException
	{
		try{//try to retrieve the available number of bytes
			return input.available();//retrieve the available number of bytes from the underlying input-stream
		} catch(Exception exception){//if the available number of bytes couldn't be retrieved
			try{//try to repair the input-stream
				repair();//repair the input-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			return input.available();//retrieve the available number of bytes from the new input-stream
		}
	}
	
	/**Closes the underlying input-stream, releasing any resources it might of held
	 * @throws IOException If the underlying input-stream couldn't be closed successfully*/
	public void close() throws IOException
	{
		input.close();//close the input-stream
	}
	
	/**Marks the current position in the underlying input-stream*/
	public void mark(int readlimit)
	{
		input.mark(readlimit);//mark the input-stream
	}
	
	/**Repositions the stream to it's last marked location
	 * @throws IOException If the underlying input-stream couldn't be closed successfully*/
	public void reset() throws IOException
	{
		try{//try to retrieve the available number of bytes
			input.available();//retrieve the available number of bytes from the underlying input-stream
		} catch(Exception exception){//if the available number of bytes couldn't be retrieved
			if(!markSupported() && (exception instanceof IOException)){//if marking isn't supports and an IOException was thrown
				throw exception;//re-throw the exception 
			}
			try{//try to repair the input-stream
				repair();//repair the input-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			input.available();//retrieve the available number of bytes from the new input-stream
		}
	}
	
	/**Returns whether or not the underlying input-stream supports marking and resetting
	 * @return True if the mark and reset methods are supported, false if not*/
	public boolean markSupported()
	{
		return input.markSupported();//return whether the underlying input-stream supports marking
	}
	
	/**Returns a reference to the underlying input-stream; Since the stream may
	 * change due to repairs, this reference should never be stored, and instead whenever access to
	 * the input-stream is needed, 'get' should be invoked to ensure the most current copy is used
	 * @return A reference to the underlying input-stream*/
	public T get()
	{
		return input;//return a reference to the input-stream
	}
}
