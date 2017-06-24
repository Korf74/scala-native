package java.lang

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Member
// import VMHelper

private final class VMClassRegistry {

}

object VMClassRegistry {

  // native defs
  def getSimpleName(clazz: Class[_]): String = ???

  def getEnclosingClass(clazz: Class[_]): Class = ???

  def getEnclosingMemeber(clazz: Class[_]): Member = ???

  def loadBootstrapClass(name: String): Class[_] = ???

  def getClassNative(obj: Object): Class[_ <: Object] = ???

  // not native
  def getClass(obj: Object): Class[_ <: Object] = {
    if(VMHelper.isVMMagicPackageSupported())
      return VMHelper.getManagerClass(obj).toObjectReference().toObject().asInstanceOf[Class[_ <: Object]]
    getClassNative(obj)
  }

  // not native
  def getClassLoader(clazz: Class[_]): ClassLoader = {
    if(clazz != null) {
      assert(getClassLoader(clazz) == clazz.definingLoader)
      return clazz.definingLoader
    }
    Thread.currentThread().getContextClassLoader
  }

  def getClassLoader0(clazz: Class[_]): ClassLoader = ???

  //rest not needed



}
