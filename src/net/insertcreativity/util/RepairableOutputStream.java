
package net.insertcreativity.util;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;

/**Wrapper class that creates an output-stream from scratch and stores it's constructor data, allowing the
 * output-stream to be repaired later on without loosing the reference to it*/
public class RepairableOutputStream<T extends OutputStream> extends OutputStream
{
	/**List of arguments that satisfy the output-stream's constructor*/
	private final Object[] parameters;
	/**Reference to the output-stream's constructor*/
	private final Constructor<T> constructor;
	/**Reference to the underlying output-stream*/
	protected T output;
	
	/**Create a new output stream wrapped as a reparable object, by invoking it's constructor with the provided arguments
	 * @param type The class type for the output-stream that this is wrapping
	 * @param args An array containing the arguments to be passed into the output-stream's constructor
	 * @throws ReflectiveOperationException If the output-stream cannot be created with the provided arguments
	 * @throws Exception Any exceptions thrown by the output-stream's constructor will be propagated
	 *                   its up to the caller to know what exceptions to catch from this*/
	public RepairableOutputStream(Class<T> type, Object[] args) throws ReflectiveOperationException
	{
		parameters = args;//store the arguments as the constructor's parameters
		Class<?>[] types = new Class[args.length];//allocate an array for all the argument's types
		for(int counter = 0; counter < args.length; counter++){//iterate through the arguments
			types[counter] = args[counter].getClass();//store the argument's type
		}
		constructor = type.getConstructor(types);//retrieve the corresponding constructor
		output = constructor.newInstance(args);//create a new instance of the object
	}
	
	/**attempts to repair the underling output-stream by reconstructing it
	 * @throws ReflectiveOperationException If the output-stream cannot be reconstructed
	 * @throws IOException if the previous output-stream couldn't be closed
	 * @throws Exception Any exceptions thrown by the output-stream's constructor will be propagated
	 *                   its up to the caller to know what exceptions to catch from this*/
	public void repair() throws ReflectiveOperationException, IOException
	{
		try{//try to close the output before discarding it
			output.close();//close the old output
		} finally{//ensure that the new output-stream is created
			output = constructor.newInstance(parameters);//reconstruct the underlying output-stream
		}
	}
	
	/**Write a single byte to the underlying output-stream
	 * @param b An integer whose first 8 bits are typically taken as a byte and output
	 * @throws IOException If the underlying output-stream failed and couldn't be successfully repaired*/
	public void write(int b) throws IOException
	{
		try{//try to write the byte
			output.write(b);//have the underlying output-stream write the byte
		} catch(Exception exception){//if the byte couldn't be written
			try{//try to repair the output-stream
				repair();//repair the output-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			output.write(b);//have the new output-stream write the byte
		}
	}
	
	/**Write an entire array of bytes to the underlying output-stream
	 * @param b An array of bytes to be output
	 * @throws IOException If the underlying output-stream failed and couldn't be successfully repaired*/
	public void write(byte[] b) throws IOException
	{
		try{//try to write the bytes
			output.write(b);//have the underlying output-stream write the bytes
		} catch(Exception exception){//if the bytes couldn't be written
			try{//try to repair the output-stream
				repair();//repair the output-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			output.write(b);//have the new output-stream write the bytes
		}
	}
	
	/**Write a section of bytes len long from the specified byte array starting at an offset of off to the underlying
	 * output-stream
	 * @param b An array of bytes to output from
	 * @param off the starting offset in b
	 * @param len how many bytes to output from b
	 * @throws IOException If the underlying output-stream failed and couldn't be successfully repaired*/
	public void write(byte[] b, int off, int len) throws IOException
	{
		try{//try to write the specified section of bytes
			output.write(b, off, len);//have the underlying output-stream write the specified section of bytes
		} catch(Exception exception){//if the specified section of bytes couldn't be written
			try{//try to repair the output-stream
				repair();//repair the output-stream
			} catch(Exception exception1){//if the repair failed
				throw new IOException("Repair failure", exception1);//except the repair's failure
			}
			output.write(b, off, len);//have the new output-stream write the specified bytes
		}
	}
	
	public void flush()
	{
		try{//try to flush out the output-stream
			output.flush();//flush the underlying output-stream
		} catch(Exception exception){//if the output-stream couldn't be flushed
			//TODO
		}
	}
	
	public void close()
	{
		try{//try to close the output-stream
			output.close();//close the underlying output-stream
		} catch(Exception exception){//if the output-stream couldn't be closed
			//TODO
		}
	}
}
