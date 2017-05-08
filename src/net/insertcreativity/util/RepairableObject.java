
package net.insertcreativity.util;

import java.lang.reflect.Constructor;

/**Wrapper class that creates an object from scratch and stores it's constructor data, allowing the
 * object to be repaired without loosing the reference to it*/
public class RepairableObject<T>
{
	/**List of arguments that satisfy the underlying object's constructor*/
	private final Object[] parameters;
	/**Reference to the object's constructor*/
	private final Constructor<T> constructor;
	/**Reference to the underlying output-stream*/
	protected T object;
	
	/**Creates a new reparable object, by invoking the object's constructor with the provided arguments
	 * @param type The class type of the object that this will wrap
	 * @param args An array containing the arguments to be passed into the object's constructor
	 * @throws ReflectiveOperationException If the object cannot be created with the provided arguments
	 * @throws Exception Any exceptions thrown by the object's constructor will be propagated
	 *                   its up to the caller to know what exceptions to catch from this*/
	public RepairableObject(Class<T> type, Object[] args) throws ReflectiveOperationException
	{
		parameters = args;//store the arguments as the constructor's parameters
		Class<?>[] types = new Class[args.length];//allocate an array for all the argument's types
		for(int counter = 0; counter < args.length; counter++){//iterate through the arguments
			types[counter] = args[counter].getClass();//store the argument's type
		}
		constructor = type.getConstructor(types);//retrieve the corresponding constructor
		object = constructor.newInstance(args);//create a new instance of the object
	}
	
	/**attempts to repair the underling object by reconstructing it
	 * @throws ReflectiveOperationException If the object cannot be reconstructed
	 * @throws Exception Any exceptions thrown by the object's constructor will be propagated
	 *                   its up to the caller to know what exceptions to catch from this*/
	public void repair() throws ReflectiveOperationException, Exception
	{
		object = constructor.newInstance(parameters);//reconstruct the underlying object
	}
}
