
package net.insertcreativity.util;

import java.lang.reflect.Array;

/**Implements the bare minimum of a first in, first out stack*/
public class MinimalStack<T>
{
	/**The class type of the elements being stored in this stack*/
	private final Class<?> type;
	/**Array containing the data elements in this stack*/
	private T[] elements;
	/**How much to grow the stack by one capacity has been reached*/
	private int growAmount;
	/**The current capacity of the stack*/
	private int capacity;
	/**How many elements are currently in the stack*/
	private volatile int length = 0;
	/**The current offset in the stack, denotes the first most added element left*/
	private int offset = 0;
	
	/**Creates a new stack that holds the specified type of data elements with an initially allocated size
	 * @param elementType The class type of data that this stack will be holding
	 * @param initialCapacity How much space to initially allocate in the stack
	 * @param incrementAmount How much additional space should be allocated when the stack hits capacity*/
	public MinimalStack(Class<?> elementType, int initialCapacity, int incrementAmount)
	{
		type = elementType;//set the stack's data element type
		capacity = initialCapacity;//set the current capacity to be the initial capacity
		growAmount = incrementAmount;//set the grow amount for the stack
		elements = (T[])Array.newInstance(type, capacity);//create the array for holding the stack elements
	}
	
	/**Adds an element to the top of the stack
	 * @param element The element to be added to the top of the stack*/
	public void push(T element)
	{
		if(length == elements.length){//if the stack has reached capacity
			T[] newElements = (T[])Array.newInstance(type, capacity + growAmount);//create a new and larger stack
			System.arraycopy(elements, 0, newElements, 0, offset);//copy all the preceding elements
			System.arraycopy(elements, offset, newElements, offset + growAmount, capacity - offset);//copy all the previous elements
			capacity += growAmount;//allocate extra space into the stack's capacity
			offset += growAmount;//move the offset forward by the grow
			elements = newElements;//replace the old elements with newElements
		}
		elements[(offset + length++) % capacity] = element;//add the element into the stack at the top, and increment the length
	}
	
	/**Removes and returns the earliest queued element from the stack
	 * @return The element at the bottom of the stack*/
	public T pop()
	{
		length--;//decrement the length, since one element is being removed
		T element = elements[offset];//retrieve the element that's being removed
		elements[offset++] = null;//remove the object's reference from the elements, and increment the offset
		if(offset == elements.length){//if the offset has reached the end of the array
			offset = 0;//wrap it back around to the front.
		}
		return element;//return the removed element
	}
	
	/**Removes all the elements currently in the stack*/
	public void clear()
	{
		for(int counter = 0; counter < capacity; counter++){//iterate through all the elements in the stack
			elements[counter] = null;//remove the element
		}
		length = 0;//reset the length back to 0
		offset = 0;//reset the offset back to 0
	}
	
	/**Retrieves the number of elements currently in the stack
	 * @return How many elements are in the stack*/
	public int length()
	{
		return length;
	}
}
