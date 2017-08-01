package tas.utils.files.lined

class FileContents(val lines:List[String]) {

  private def isEmpty(line:String) = line.trim.isEmpty

  private def removeComment(line:String) = line.replaceFirst("#.*", "")

  lazy val significantLines = lines.map(removeComment).filterNot(isEmpty)
}
