package security.fortress

import java.security.AccessControlContext
import java.util
import java.util.WeakHashMap

final class SecurityUtils {

}

object SecurityUtils {

  // A map used to store inherited contexts.<br>
  // A thread is used as a key for the map and AccessControlContext
  // passed to the putContext is used as a value.
  private final val ACC_CACHE: WeakHashMap[Thread, AccessControlContext] =
    new util.WeakHashMap[Thread, AccessControlContext]()

  def putContext(thread: Thread, context: AccessControlContext) = {
    if(thread == null)
      throw new NullPointerException("thread can not be null")
    ACC_CACHE.synchronized{
      if(ACC_CACHE.containsKey(thread))
        throw new SecurityException("You can not modify this map")
      if(context == null) {
        // this only allowed once - for the very first thread
        if(ACC_CACHE.containsValue(null))
          throw new Error("null context may be stored only once")
      }
      ACC_CACHE.put(thread, context)
    }
  }

  def getContext(thread: Thread): AccessControlContext = {
    // ~fixme: see 'fixme' at the top of the file
    /*
     Class cl = VMStack.getCallerClass(0);
     if (cl != AccessController.class) {
     throw new SecurityException("You ["+cl+"] do not have access to this resource.");
     }
     */
    ACC_CACHE.synchronized{
      ACC_CACHE.get(thread)
    }
  }

}
