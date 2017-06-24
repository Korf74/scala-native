package java.lang

trait RuntimePermissionCollection {

  val ACCESS_DECLARED_MEMBERS_PERMISSION = new RuntimePermission("accessDeclaredMembers")

  val CREATE_SECURITY_MANAGER_PERMISSION = new RuntimePermission("createSecurityManager")

  val CREATE_CLASS_LOADER_PERMISSION = new RuntimePermission("createClassLoader")

  val EXIT_VM_PERMISSION = new RuntimePermission("exitVM")

  val GET_CLASS_LOADER_PERMISSION = new RuntimePermission("getClassLoader")

  val GET_PROTECTION_DOMAIN_PERMISSION = new RuntimePermission("getProtectionDomain")

  val GET_STACK_TRACE_PERMISSION = new RuntimePermission("getStackTrace")

  val GETENV_PERMISSION = new RuntimePermission("getenv.*")

  val MODIFY_THREAD_GROUP_PERMISSION = new RuntimePermission("modifyThreadGroup")

  val MODIFY_THREAD_PERMISSION = new RuntimePermission("modifyThread")

  val QUEUE_PRINT_JOB_PERMISSION = new RuntimePermission("queuePrintJob")

  val READ_FILE_DESCRIPTOR_PERMISSION = new RuntimePermission("readFileDescriptor")

  val SET_CONTEXT_CLASS_LOADER_PERMISSION = new RuntimePermission("setContextClassLoader")

  val SET_DEFAULT_UNCAUGHT_EXCEPTION_HANDLER_PERMISSION = new RuntimePermission("setDefaultUncaughtExceptionHandler")

  val SET_FACTORY_PERMISSION = new RuntimePermission("setFactory")

  val SET_IO_PERMISSION = new RuntimePermission("setIO")

  val SET_SECURITY_MANAGER_PERMISSION = new RuntimePermission("setSecurityManager")

  val SHUTDOWN_HOOKS_PERMISSION = new RuntimePermission("shutdownHooks")

  val STOP_THREAD_PERMISSION = new RuntimePermission("stopThread")

  val WRITE_FILE_DESCRIPTOR_PERMISSION = new RuntimePermission("writeFileDescriptor")

}
