
package net.insertcreativity.util;

import java.io.File;

/**Utility class with myriad helper functions*/
public final class Util
{
	/**Deletes the specified file, also deleting any files inside in the case it's a directory
	 * @param file The file to be deleted*/
	public static final boolean delete(File file)
	{
		boolean success = true;
		File[] children = file.listFiles();//get a list of all the children in this file
		if(children != null){//if this is a non-empty directory
			for(File child : children){//iterate through all the children in this directory
				success &= delete(child);//attempt to delete the child, storing whether it was successful
			}
		}
		if(success){//if the file doesn't have any children
			return file.delete();//return whether the file was deleted
		}
		return false;//return false if at least one child couldn't be deleted from this file
	}
}
