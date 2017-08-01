package tas.utils.format

class StringComplementor(requiredLen:Int, complementString:String) {

  if (requiredLen <= 0) throw new IllegalArgumentException("requiredLen can not be <= 0")
  if (complementString.length != 1) throw new IllegalArgumentException("complementString must have lenght 1 symbol")

  def apply(stringToComplement:String):String = {
    val len = stringToComplement.length

    (complementString * Math.max(requiredLen - len,
                                0)
       + stringToComplement)
  }
}
